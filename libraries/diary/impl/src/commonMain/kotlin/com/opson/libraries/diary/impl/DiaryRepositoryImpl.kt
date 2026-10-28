package com.opson.libraries.diary.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.DayReflection
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.DiaryItemEditor
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.SyncEntity
import com.opson.libraries.diary.db.DayRecordEntity
import com.opson.libraries.diary.db.LogEntryEntity
import com.opson.libraries.diary.db.MealFeelingEntity
import com.opson.libraries.diary.impl.wire.DayRecordRowDto
import com.opson.libraries.diary.impl.wire.LogEntryRowDto
import com.opson.libraries.diary.impl.wire.MealFeelingRowDto
import com.opson.libraries.diary.impl.wire.WireJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Instant

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = DiaryRepository::class)
@ContributesBinding(AppScope::class, boundType = DiaryItemEditor::class)
@Inject
class DiaryRepositoryImpl(
    private val tables: DiaryTables,
    private val writer: DiaryWriter,
    private val settingsRepository: SettingsRepository,
    private val calendar: LocalCalendar,
    private val fastEnder: FastEnder,
) : DiaryRepository, DiaryItemEditor {

    private val logger = KLog.withTag("Diary")

    override fun observeDay(date: LocalDate): Flow<Day> {
        val key = date.toString()
        return combine(
            tables.entries.observeDay(key),
            tables.days.observe(key),
            tables.feelings.observeDay(key),
        ) { entries, record, feelings -> buildDay(date, entries, record, feelings) }
    }

    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Day>> {
        require(from <= to) { "from must not be after to" }
        val fromKey = from.toString()
        val toKey = to.toString()
        return combine(
            tables.entries.observeRange(fromKey, toKey),
            tables.days.observeRange(fromKey, toKey),
            tables.feelings.observeRange(fromKey, toKey),
        ) { entries, records, feelings ->
            val entriesByDay = entries.groupBy { it.day }
            val recordsByDay = records.associateBy { it.day }
            val feelingsByDay = feelings.groupBy { it.day }
            generateSequence(from) { it.plus(1, DateTimeUnit.DAY) }
                .takeWhile { it <= to }
                .map { date ->
                    val key = date.toString()
                    buildDay(date, entriesByDay[key].orEmpty(), recordsByDay[key], feelingsByDay[key].orEmpty())
                }
                .toList()
        }
    }

    override fun observeLoggedDates(year: Int, month: Int): Flow<Set<LocalDate>> {
        val first = LocalDate(year, month, 1)
        val last = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        return combine(
            tables.entries.observeDaysWithEntries(first.toString(), last.toString()),
            tables.days.observeRange(first.toString(), last.toString()),
        ) { withEntries, records ->
            val withAte = records.filter { it.ateNoDetailsSlots().isNotEmpty() }.map { it.day }
            (withEntries + withAte).map(LocalDate::parse).toSet()
        }
    }

    override suspend fun addItems(
        day: LocalDate,
        slot: MealSlot,
        items: List<LogDraft>,
        method: LogMethod,
    ): DayWrite<List<LogItem>> {
        if (items.isEmpty()) return DayWrite.Written(emptyList())
        return logFood(day) { insertDrafts(day, items.map { slot to it }, method) }
    }

    override suspend fun updateItem(item: LogItem): DayWrite<Unit> = writer.write {
        val existing = tables.entries.get(item.id)?.takeUnless { it.deleted } ?: return@write written
        closedAmong(listOf(existing.day, item.day.toString()))?.let { return@write it }
        val next = item.sanitized().toEntity(stamp(existing.clientUpdatedAtEpochMs))
        if (next.copy(clientUpdatedAtEpochMs = existing.clientUpdatedAtEpochMs) != existing) putEntry(next, base = existing)
        written
    }

    override suspend fun removeItem(id: String): DayWrite<Unit> = removeItems(listOf(id))

    override suspend fun moveItem(id: String, slot: MealSlot): DayWrite<Unit> = moveItems(listOf(id), slot)

    override suspend fun removeItems(ids: List<String>): DayWrite<Unit> = writer.write {
        val live = ids.mapNotNull { id -> tables.entries.get(id)?.takeUnless { it.deleted } }
        closedAmong(live.map { it.day })?.let { return@write it }
        live.forEach { existing ->
            putEntry(existing.copy(deleted = true, clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs)), existing)
        }
        written
    }

    override suspend fun restoreItems(ids: List<String>): DayWrite<Unit> = writer.write {
        val removed = ids.mapNotNull { id -> tables.entries.get(id)?.takeIf { it.deleted } }
        closedAmong(removed.map { it.day })?.let { return@write it }
        removed.forEach { existing ->
            putEntry(existing.copy(deleted = false, clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs)), existing)
        }
        written
    }

    override suspend fun moveItems(ids: List<String>, slot: MealSlot): DayWrite<Unit> = writer.write {
        val moving = ids.mapNotNull { id -> tables.entries.get(id)?.takeUnless { it.deleted } }.filter { it.slot != slot.wire }
        closedAmong(moving.map { it.day })?.let { return@write it }
        moving.forEach { existing ->
            putEntry(existing.copy(slot = slot.wire, clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs)), existing)
        }
        written
    }

    override suspend fun copyItems(ids: List<String>, toDay: LocalDate, slot: MealSlot): DayWrite<List<LogItem>> {
        val source = ids.mapNotNull { id -> tables.entries.get(id)?.takeUnless { it.deleted }?.toDomain() }
        return logFood(toDay, ate = source.isNotEmpty()) { insertDrafts(toDay, source.map { slot to it.toDraft() }, LogMethod.Copy) }
    }

    override suspend fun copyDay(from: LocalDate, to: LocalDate): DayWrite<List<LogItem>> {
        val source = tables.entries.getDay(from.toString()).mapNotNull { it.toDomain() }
        return logFood(to, ate = source.isNotEmpty()) { insertDrafts(to, source.map { it.slot to it.toDraft() }, LogMethod.Copy) }
    }

    override suspend fun copySlot(fromDay: LocalDate, toDay: LocalDate, slot: MealSlot): DayWrite<List<LogItem>> {
        val source = tables.entries.getDay(fromDay.toString()).mapNotNull { it.toDomain() }.filter { it.slot == slot }
        return logFood(toDay, ate = source.isNotEmpty()) { insertDrafts(toDay, source.map { slot to it.toDraft() }, LogMethod.Copy) }
    }

    override suspend fun clearDay(day: LocalDate): DayWrite<Unit> = writer.write {
        closedDay(day)?.let { return@write it }
        tables.entries.getDay(day.toString()).forEach { existing ->
            putEntry(existing.copy(deleted = true, clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs)), existing)
        }
        changeDay(day) { it.copy(ateNoDetailsJson = emptySet<MealSlot>().toAteJson()) }
        written
    }

    override suspend fun addWater(day: LocalDate, ml: Int): DayWrite<Unit> = changeOpenDay(day) {
        it.copy(waterMl = clampWater((it.waterMl ?: 0) + ml.coerceAtLeast(0)))
    }

    override suspend fun removeWater(day: LocalDate, ml: Int): DayWrite<Unit> = changeOpenDay(day) {
        it.copy(waterMl = clampWater((it.waterMl ?: 0) - ml.coerceAtLeast(0)))
    }

    override suspend fun setWater(day: LocalDate, ml: Int): DayWrite<Unit> = changeOpenDay(day) { it.copy(waterMl = clampWater(ml)) }

    override suspend fun closeDay(day: LocalDate, reflection: DayReflection) = writer.write {
        changeDay(day) {
            it.copy(
                closedAtEpochMs = it.closedAtEpochMs ?: nowEpochMs,
                note = reflection.note.cleanNote(),
                feeling = reflection.feeling?.wire,
                checkIn = reflection.checkIn.toCheckInJson(),
            )
        }
    }

    override suspend fun reopenDay(day: LocalDate) = writer.write {
        changeDay(day) { it.copy(closedAtEpochMs = null) }
    }

    override suspend fun setDayNote(day: LocalDate, note: String?): DayWrite<Unit> = changeOpenDay(day) { it.copy(note = note.cleanNote()) }

    override suspend fun setUntracked(day: LocalDate, untracked: Boolean) = writer.write {
        changeDay(day) { it.copy(untracked = untracked) }
    }

    override suspend fun setAteNoDetails(day: LocalDate, slot: MealSlot, ate: Boolean): DayWrite<Unit> {
        val mark: DiaryWriter.WriteScope.(DayRecordEntity) -> DayRecordEntity = { record ->
            val slots = record.ateNoDetailsSlots()
            record.copy(ateNoDetailsJson = (if (ate) slots + slot else slots - slot).toAteJson())
        }
        return if (ate) logFood(day) { changeDay(day, mark) } else changeOpenDay(day, mark)
    }

    /**
     * Writes food to [day] through [block] and ends a running fast when [ate], unless the day is
     * closed. Checked before the fast is ended, so food a closed day refuses ends nothing, and
     * again inside the write, where the answer cannot change before the rows land.
     */
    private suspend fun <T> logFood(
        day: LocalDate,
        ate: Boolean = true,
        block: suspend DiaryWriter.WriteScope.() -> T,
    ): DayWrite<T> {
        closedDay(day)?.let { return it }
        if (ate) foodLoggedOn(day)
        return writer.write { closedDay(day) ?: DayWrite.Written(block()) }
    }

    /** Changes [day]'s record unless the day is closed. */
    private suspend fun changeOpenDay(
        day: LocalDate,
        change: DiaryWriter.WriteScope.(DayRecordEntity) -> DayRecordEntity,
    ): DayWrite<Unit> = writer.write {
        closedDay(day) ?: run {
            changeDay(day, change)
            written
        }
    }

    private suspend fun closedDay(day: LocalDate): DayWrite.DayClosed? = tables.closedDay(day.toString())

    /** The first closed day among [days] (ISO dates), so a batch that touches one writes nothing. */
    private suspend fun closedAmong(days: Collection<String>): DayWrite.DayClosed? =
        days.toSet().firstNotNullOfOrNull { tables.closedDay(it) }

    /**
     * Logging food ends a fast. Only on today or earlier: planning tomorrow's meals is not eating
     * now. Fasting never gets in the way of logging, so a failure here is logged and dropped.
     *
     * Before the food is written: a fast that was due and never started (the app was closed when
     * the window shut) is written first, and it is only due while nothing was logged since the
     * close. After the write, the new food would be that something.
     */
    private suspend fun foodLoggedOn(day: LocalDate) {
        if (day > calendar.today()) return
        Catching { fastEnder.foodLogged() }.onFailure { logger.w(it) { "Could not end a fast on logging food" } }
    }

    override suspend fun setFeeling(day: LocalDate, slot: MealSlot, feeling: MealFeeling?): DayWrite<Unit> = writer.write {
        closedDay(day)?.let { return@write it }
        val key = day.toString()
        val existing = tables.feelings.get(key, slot.wire)
        val next: MealFeelingEntity = if (feeling == null || feeling.isEmpty) {
            if (existing == null || existing.deleted) return@write written
            existing.copy(deleted = true, clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs))
        } else {
            val candidate = feeling.copy(note = feeling.note?.take(MAX_NOTE))
                .toEntity(key, slot, stamp(existing?.clientUpdatedAtEpochMs))
            if (existing != null && candidate.copy(clientUpdatedAtEpochMs = existing.clientUpdatedAtEpochMs) == existing) {
                return@write written
            }
            candidate
        }
        tables.feelings.upsert(listOf(next))
        enqueue(
            SyncEntity.MealFeeling,
            feelingKey(key, slot.wire),
            WireJson.encodeToString(MealFeelingRowDto.serializer(), next.toRow()),
            existing?.let { WireJson.encodeToString(MealFeelingRowDto.serializer(), it.toRow()) },
            day = key,
        )
        written
    }

    override suspend fun setMealGroups(day: LocalDate, on: Boolean?) = writer.write {
        changeDay(day) { it.copy(mealGroups = on) }
    }

    // A food taken off Recent stays off until it is logged again, so the latest entry decides.
    override fun recentFoods(limit: Int): Flow<List<LogItem>> =
        combine(
            tables.entries.observeLatestFoods(RECENT_SCAN),
            settingsRepository.settings.distinctUntilChangedBy { it.hiddenRecentFoods },
        ) { rows, settings ->
            rows.mapNotNull { it.toDomain() }
                .distinctBy { it.foodKey() }
                .filterNot { settings.hidesFromRecent(it.foodKey(), it.loggedAt.toEpochMilliseconds()) }
                .take(limit)
        }

    override fun usualAt(slot: MealSlot, limit: Int, days: Int): Flow<List<LogItem>> = flow {
        val since = calendar.today().minus(days, DateTimeUnit.DAY)
        emitAll(tables.entries.observeSlotSince(slot.wire, since.toString()).map { it.mostFrequent(limit) })
    }

    override fun frequentFoods(limit: Int, days: Int): Flow<List<LogItem>> = flow {
        val since = calendar.today().minus(days, DateTimeUnit.DAY)
        emitAll(tables.entries.observeSince(since.toString()).map { it.mostFrequent(limit) })
    }

    /** Groups by food, most entries first (ties to the most recent), each as its latest entry. */
    private fun List<LogEntryEntity>.mostFrequent(limit: Int): List<LogItem> =
        mapNotNull { it.toDomain() }
            .groupBy { it.foodKey() }
            .values
            .sortedWith(
                compareByDescending<List<LogItem>> { it.size }
                    .thenByDescending { group -> group.maxOf { it.loggedAt } },
            )
            .map { group -> group.maxBy { it.loggedAt } }
            .take(limit)

    // ---------- writes ----------

    private suspend fun DiaryWriter.WriteScope.insertDrafts(
        day: LocalDate,
        drafts: List<Pair<MealSlot, LogDraft>>,
        method: LogMethod,
    ): List<LogItem> {
        if (drafts.isEmpty()) return emptyList()
        val record = tables.days.get(day.toString())?.takeUnless { it.deleted }
        val groupsOn = record?.mealGroups ?: settingsRepository.settings.value.mealGroups
        val logged = drafts.mapIndexed { index, (slot, draft) ->
            draft.toItem(
                id = newId(),
                day = day,
                slot = if (groupsOn) slot else MealSlot.Uncategorized,
                // One millisecond apart so a basket keeps its order on Today.
                loggedAt = Instant.fromEpochMilliseconds(nowEpochMs + index),
                method = draft.method ?: method,
            )
        }
        val entities = logged.map { it.toEntity(stamp(null)) }
        tables.entries.upsert(entities)
        entities.forEach { entity ->
            enqueue(SyncEntity.LogEntry, entity.id, entity.rowJson(), base = null, day = entity.day)
        }
        return logged
    }

    private suspend fun DiaryWriter.WriteScope.putEntry(next: LogEntryEntity, base: LogEntryEntity?) {
        tables.entries.upsert(listOf(next))
        enqueue(SyncEntity.LogEntry, next.id, next.rowJson(), base?.rowJson(), day = next.day)
    }

    private suspend fun DiaryWriter.WriteScope.changeDay(
        day: LocalDate,
        change: DiaryWriter.WriteScope.(DayRecordEntity) -> DayRecordEntity,
    ) {
        val key = day.toString()
        val existing = tables.days.get(key)
        val current = existing?.takeUnless { it.deleted } ?: emptyDayRecord(key)
        val changed = change(current)
        if (changed == current) return
        val next = changed.copy(deleted = false, clientUpdatedAtEpochMs = stamp(existing?.clientUpdatedAtEpochMs))
        tables.days.upsert(listOf(next))
        enqueue(
            SyncEntity.DayRecord,
            key,
            WireJson.encodeToString(DayRecordRowDto.serializer(), next.toRow()),
            existing?.let { WireJson.encodeToString(DayRecordRowDto.serializer(), it.toRow()) },
            day = key,
        )
    }

    private fun buildDay(
        date: LocalDate,
        entries: List<LogEntryEntity>,
        record: DayRecordEntity?,
        feelings: List<MealFeelingEntity>,
    ): Day {
        val items = entries.mapNotNull { it.toDomain() }
            .sortedWith(compareBy<LogItem> { it.loggedAt }.thenBy { it.id })
            .groupBy { it.slot }
        val live = record?.takeUnless { it.deleted }
        return Day(
            date = date,
            itemsBySlot = MealSlot.entries.associateWith { items[it].orEmpty() },
            waterMl = live?.waterMl ?: 0,
            closedAt = live?.closedAtEpochMs?.let(Instant::fromEpochMilliseconds),
            note = live?.note,
            ateNoDetails = live?.ateNoDetailsSlots().orEmpty(),
            mealGroups = live?.mealGroups,
            untracked = live?.untracked ?: false,
            feeling = DayFeeling.fromWire(live?.feeling),
            checkIn = live?.checkIn.toCheckIn(),
            feelings = feelings.filterNot { it.deleted }
                .mapNotNull { row -> MealSlot.fromWire(row.slot)?.let { it to row.toDomain() } }
                .toMap(),
        )
    }

    private companion object {
        const val RECENT_SCAN = 200
        const val MAX_NOTE = 2_000
        const val MAX_WATER_ML = 20_000

        val written: DayWrite<Unit> = DayWrite.Written(Unit)

        fun clampWater(ml: Int): Int = ml.coerceIn(0, MAX_WATER_ML)

        fun String?.cleanNote(): String? = this?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_NOTE)
    }
}

internal fun LogEntryEntity.rowJson(): String = WireJson.encodeToString(LogEntryRowDto.serializer(), toRow())

/** Repeats of the same food collapse on this: the catalog id, then the custom food, then the name. */
internal fun LogItem.foodKey(): String =
    foodId?.let { "f:$it" } ?: customFoodId?.let { "c:$it" } ?: "n:${name.trim().lowercase()}"

private fun LogDraft.toItem(id: String, day: LocalDate, slot: MealSlot, loggedAt: Instant, method: LogMethod): LogItem {
    require(name.isNotBlank()) { "A logged item needs a name" }
    return LogItem(
        id = id,
        day = day,
        slot = slot,
        loggedAt = loggedAt,
        name = name,
        precision = precision,
        method = method,
        foodId = foodId,
        customFoodId = customFoodId,
        amountText = amountText,
        quantity = quantity,
        unit = unit,
        grams = grams,
        kcal = kcal,
        protein = protein,
        carbs = carbs,
        fat = fat,
        fiber = fiber,
        micros = micros,
    ).sanitized()
}

/**
 * Trims a row to what the server accepts (`DiaryRules`), so a long name or a
 * stray negative never turns into a rejection that rolls the user's entry back.
 */
internal fun LogItem.sanitized(): LogItem = copy(
    name = name.trim().take(MAX_NAME),
    amountText = amountText?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_AMOUNT_TEXT),
    unit = unit?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_UNIT),
    quantity = quantity.cleanAmount(MAX_QUANTITY),
    grams = grams.cleanAmount(MAX_GRAMS),
    kcal = kcal.cleanAmount(MAX_NUTRIENT),
    protein = protein.cleanAmount(MAX_NUTRIENT),
    carbs = carbs.cleanAmount(MAX_NUTRIENT),
    fat = fat.cleanAmount(MAX_NUTRIENT),
    fiber = fiber.cleanAmount(MAX_NUTRIENT),
    micros = micros
        ?.filter { (key, value) -> key.isNotBlank() && key.length <= MAX_MICRO_KEY && value.cleanAmount(MAX_NUTRIENT) != null }
        ?.entries?.take(MAX_MICROS)?.associate { it.key to it.value }
        ?.takeIf { it.isNotEmpty() },
)

private fun Double?.cleanAmount(max: Double): Double? = this?.takeIf { it.isFinite() && it >= 0.0 && it <= max }

private const val MAX_NAME = 200
private const val MAX_AMOUNT_TEXT = 100
private const val MAX_UNIT = 32
private const val MAX_QUANTITY = 10_000.0
private const val MAX_GRAMS = 100_000.0
private const val MAX_NUTRIENT = 100_000.0
private const val MAX_MICROS = 100
private const val MAX_MICRO_KEY = 64
