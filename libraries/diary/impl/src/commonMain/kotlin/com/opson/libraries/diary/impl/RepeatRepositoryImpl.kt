package com.opson.libraries.diary.impl

import com.opson.libraries.core.AutoInit
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.RepeatPlan
import com.opson.libraries.diary.RepeatRepository
import com.opson.libraries.diary.RepeatRule
import com.opson.libraries.diary.RepeatSaved
import com.opson.libraries.diary.RepeatsRelease
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.SyncEntity
import com.opson.libraries.diary.db.RepeatLogEntity
import com.opson.libraries.diary.db.RepeatRuleEntity
import com.opson.libraries.diary.repeatEntryId
import com.opson.libraries.diary.impl.wire.WireJson
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.opson.AppEvent
import com.opson.libraries.opson.AppEvents
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.serialization.builtins.ListSerializer
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Repeats on this phone (see [RepeatRepository]). A rule's own writes touch only the device-local
 * rule tables; every save then asks [RepeatMaterializer] to log today, so a rule that falls on
 * today shows its foods the moment it is saved. Only the signed-in account's ([RepeatOwner]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class RepeatRepositoryImpl(
    private val tables: DiaryTables,
    private val materializer: RepeatMaterializer,
    private val owner: RepeatOwner,
    private val calendar: LocalCalendar,
    private val clock: Clock,
) : RepeatRepository {

    private val logger = KLog.withTag("Repeats")

    override fun observeAll(): Flow<List<RepeatRule>> = owner.observe().flatMapLatest { userId ->
        if (userId == null) flowOf(emptyList()) else tables.repeats.observeAll().map { rows -> rows.mapNotNull { it.toDomain() } }
    }

    override suspend fun get(id: String): RepeatRule? {
        if (owner.current() == null) return null
        return tables.repeats.get(id)?.takeUnless { it.deleted }?.toDomain()
    }

    override fun observeHasRun(id: String, day: LocalDate): Flow<Boolean> =
        tables.repeatLogs.observeDay(day.toString()).map { rows -> rows.any { it.ruleId == id } }.distinctUntilChanged()

    override suspend fun create(plan: RepeatPlan, loggedToday: List<String>): RepeatSaved {
        // Settles the phone's repeats on the account saving this one.
        owner.current()
        val rule = RepeatRule(id = newId(), plan = plan.cleaned())
        val now = clock.now().toEpochMilliseconds()
        val today = calendar.today().toString()
        tables.transactor.inTransaction {
            tables.repeats.upsert(listOf(rule.toEntity(createdAtEpochMs = now, updatedAtEpochMs = now)))
            // Only live entries on today count: one removed, or on another day, leaves today to the rule.
            val counted = loggedToday.filter { id -> tables.entries.get(id)?.takeUnless { it.deleted }?.day == today }
            if (counted.isNotEmpty()) tables.repeatLogs.insert(counted.map { RepeatLogEntity(entryId = it, ruleId = rule.id, day = today) })
        }
        logger.logEvent("repeat.created", "days" to rule.plan.days.size, "meal" to (rule.plan.savedMealId != null), "items" to rule.plan.items.size)
        return saved(rule)
    }

    override suspend fun update(id: String, plan: RepeatPlan, paused: Boolean): RepeatSaved? =
        change(id) { it.copy(plan = plan.cleaned(), paused = paused) }

    override suspend fun setPaused(id: String, paused: Boolean): RepeatSaved? =
        change(id) { it.copy(paused = paused) }

    override suspend fun delete(id: String) {
        if (owner.current() == null) return
        tables.transactor.inTransaction {
            val existing = tables.repeats.get(id)?.takeUnless { it.deleted } ?: return@inTransaction
            val now = clock.now().toEpochMilliseconds()
            tables.repeats.upsert(listOf(existing.copy(deleted = true, clientUpdatedAtEpochMs = stamp(now, existing.clientUpdatedAtEpochMs))))
            // The entries stay in the log as ordinary ones; only the link to the rule goes.
            tables.repeatLogs.deleteForRule(id)
        }
    }

    private suspend fun change(id: String, transform: (RepeatRule) -> RepeatRule): RepeatSaved? {
        if (owner.current() == null) return null
        val changed = tables.transactor.inTransaction {
            val existing = tables.repeats.get(id)?.takeUnless { it.deleted } ?: return@inTransaction null
            val rule = existing.toDomain() ?: return@inTransaction null
            val next = transform(rule)
            if (next == rule) return@inTransaction rule
            val now = clock.now().toEpochMilliseconds()
            tables.repeats.upsert(listOf(next.toEntity(existing.createdAtEpochMs, stamp(now, existing.clientUpdatedAtEpochMs))))
            next
        } ?: return null
        return saved(changed)
    }

    private suspend fun saved(rule: RepeatRule): RepeatSaved {
        val logged = Catching { materializer.logDue() }.logOnFailure { "Logging repeats after a save failed" }.getOrNull()
        return RepeatSaved(rule, loggedToday = (logged?.get(rule.id) ?: 0) > 0)
    }
}

/**
 * Logs today's foods for every repeat that falls on today and has not had it yet. Idempotent:
 * each rule's day is recorded in `repeat_logs` with the entries it wrote, and the entry ids are
 * [repeatEntryId], so neither a second run nor a second phone holding the same rule can log a
 * food twice. An entry the person removed keeps its record, and its tombstone keeps its id, so
 * it is not logged again that day.
 *
 * **Only today.** A day the app was not opened is left as it is. Filling it in later would put
 * food in the log that the person may not have eaten, on a day they were not looking at, and
 * Trends reads an empty day as a gap rather than a low one, which is the honest answer.
 *
 * **What is skipped.** A day closed with Close the day, or marked untracked, logs nothing; neither
 * is recorded, so reopening the day or tracking it again lets the next run log it. A paused rule,
 * one before its start or past its end, and one whose saved meal and snapshot are both empty log
 * nothing either.
 *
 * **The rows.** Each food is an ordinary entry, written with its outbox mutation like a logged
 * one, so it syncs, edits and deletes the same way. Its time is the meal's time from Settings
 * (`mealTimes`), so Today's meal header and the learned meal times read the meal rather than
 * the moment the app happened to open. Its `clientUpdatedAt` is the day's start, deterministic
 * like the id and older than anything the person can do to the entry that day, so on the server
 * their edit or removal always outranks a copy of the auto-logged row. Logging this way never
 * ends a fast: food that logged itself is not proof of eating now.
 */
@SingleIn(AppScope::class)
@Inject
class RepeatMaterializer(
    private val tables: DiaryTables,
    private val writer: DiaryWriter,
    private val settingsRepository: SettingsRepository,
    private val calendar: LocalCalendar,
    private val release: RepeatsRelease,
    private val owner: RepeatOwner,
) {
    private val logger = KLog.withTag("Repeats")
    private val mutex = Mutex()

    /** Entries written, by rule id. Empty with the flag off or before the person's settings are known. */
    suspend fun logDue(): Map<String, Int> = mutex.withLock {
        // The settings say where food goes (meal groups) and when (meal times); the defaults are not theirs.
        if (!release.isOn() || !settingsRepository.isKnown.value) return emptyMap()
        // Only the signed-in account's rules; another account's are gone before they are read.
        owner.current() ?: return emptyMap()
        val today = calendar.today()
        val zone = calendar.timeZone()
        val settings = settingsRepository.settings.value
        val written = writer.write { logDay(today, zone, settings) }
        val total = written.values.sum()
        if (total > 0) logger.logEvent("repeat.logged", "entries" to total, "rules" to written.count { it.value > 0 })
        written
    }

    private suspend fun DiaryWriter.WriteScope.logDay(day: LocalDate, zone: TimeZone, settings: Settings): Map<String, Int> {
        val key = day.toString()
        if (tables.closedDay(key) != null) return emptyMap()
        val record = tables.days.get(key)?.takeUnless { it.deleted }
        if (record?.untracked == true) return emptyMap()
        val groupsOn = record?.mealGroups ?: settings.mealGroups
        val dayStart = day.atStartOfDayIn(zone).toEpochMilliseconds()
        val written = mutableMapOf<String, Int>()
        for (rule in tables.repeats.getAll().mapNotNull { it.toDomain() }) {
            if (!rule.isDue(day) || tables.repeatLogs.countFor(rule.id, key) > 0) continue
            val items = itemsOf(rule.plan).filter { it.name.isNotBlank() }
            if (items.isEmpty()) continue
            val slot = if (groupsOn) rule.plan.slot else MealSlot.Uncategorized
            val at = day.atTime(settings.mealTimes.at(rule.plan.slot)).toInstant(zone).toEpochMilliseconds()
            val method = if (rule.plan.savedMealId != null) LogMethod.Saved else LogMethod.Copy
            var count = 0
            val logs = items.mapIndexed { index, draft ->
                val id = repeatEntryId(rule.id, day, index)
                // Already there, logged here before the ledger was written or pulled from another phone.
                if (tables.entries.get(id) == null) {
                    // One millisecond apart so a meal keeps its order on Today.
                    val entity = draft.toItem(id, day, slot, Instant.fromEpochMilliseconds(at + index), method).toEntity(dayStart)
                    tables.entries.upsert(listOf(entity))
                    enqueue(SyncEntity.LogEntry, id, entity.rowJson(), base = null, day = key)
                    count++
                }
                RepeatLogEntity(entryId = id, ruleId = rule.id, day = key)
            }
            tables.repeatLogs.insert(logs)
            written[rule.id] = count
        }
        return written
    }

    /** The saved meal's foods while it is there, else the snapshot the rule was saved with. */
    private suspend fun itemsOf(plan: RepeatPlan): List<LogDraft> {
        val meal = plan.savedMealId?.let { tables.savedMeals.get(it) }?.takeUnless { it.deleted }?.toDomain()
        return meal?.items?.takeIf { it.isNotEmpty() } ?: plan.items
    }
}

/**
 * When repeats log: once the person is signed in and their settings are known, then on every
 * foreground and at each local midnight while the app is open. [AutoInit] so today's foods are
 * there before Today first draws.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class RepeatScheduler(
    materializer: RepeatMaterializer,
    activeUser: ActiveUserSource,
    settingsRepository: SettingsRepository,
    appEvents: AppEvents,
    private val calendar: LocalCalendar,
    private val clock: Clock,
    appScope: AppCoroutineScope,
) : AutoInit {

    private val started = appScope.launch {
        combine(activeUser.user, settingsRepository.isKnown) { user, known -> user is ActiveUser.SignedIn && known }
            .distinctUntilChanged()
            .flatMapLatest { ready ->
                if (!ready) {
                    emptyFlow()
                } else {
                    merge(flowOf(Unit), appEvents.live().filterIsInstance<AppEvent.OnForeground>().map { }, midnights())
                }
            }
            .collect { Catching { materializer.logDue() }.logOnFailure { "Logging repeats failed" } }
    }

    private fun midnights(): Flow<Unit> = flow {
        while (true) {
            val next = calendar.today().plus(DatePeriod(days = 1)).atStartOfDayIn(calendar.timeZone())
            delay((next - clock.now()).coerceAtLeast(1.seconds))
            emit(Unit)
        }
    }
}

// ---------- mapping ----------

private val draftsSerializer = ListSerializer(LogDraft.serializer())

private const val MAX_REPEAT_NAME = 200

/** A name the list can show: the plan's, or its first food's. */
private fun RepeatPlan.cleaned(): RepeatPlan {
    val trimmed = name.trim().ifEmpty { items.firstOrNull()?.name?.trim().orEmpty() }
    return copy(name = trimmed.take(MAX_REPEAT_NAME))
}

internal fun RepeatRule.toEntity(createdAtEpochMs: Long, updatedAtEpochMs: Long): RepeatRuleEntity = RepeatRuleEntity(
    id = id,
    name = plan.name,
    itemsJson = WireJson.encodeToString(draftsSerializer, plan.items),
    savedMealId = plan.savedMealId,
    slot = plan.slot.wire,
    days = plan.days.toMask(),
    startDay = plan.startDay.toString(),
    endDay = plan.endDay?.toString(),
    paused = paused,
    createdAtEpochMs = createdAtEpochMs,
    clientUpdatedAtEpochMs = updatedAtEpochMs,
    deleted = false,
)

/** Null for a row this build cannot read (a slot it does not know), which then logs nothing. */
internal fun RepeatRuleEntity.toDomain(): RepeatRule? {
    val slot = MealSlot.fromWire(slot)?.takeIf { it.isMeal } ?: return null
    val start = Catching { LocalDate.parse(startDay) }.getOrNull() ?: return null
    return RepeatRule(
        id = id,
        plan = RepeatPlan(
            name = name,
            items = Catching { WireJson.decodeFromString(draftsSerializer, itemsJson) }.getOrDefault(emptyList()),
            slot = slot,
            days = days.toDays(),
            startDay = start,
            endDay = endDay?.let { Catching { LocalDate.parse(it) }.getOrNull() },
            savedMealId = savedMealId,
        ),
        paused = paused,
    )
}

internal fun Set<DayOfWeek>.toMask(): Int = fold(0) { mask, day -> mask or (1 shl (day.isoDayNumber - 1)) }

internal fun Int.toDays(): Set<DayOfWeek> = DayOfWeek.entries.filter { this and (1 shl (it.isoDayNumber - 1)) != 0 }.toSet()
