package com.opson.libraries.diary.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayCheckIns
import com.opson.libraries.diary.DayFeeling
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
@ContributesBinding(AppScope::class, boundType = DayCheckIns::class)
@Inject
class DiaryRepositoryImpl(
    private val tables: DiaryTables,
    private val writer: DiaryWriter,
    private val settingsRepository: SettingsRepository,
    private val calendar: LocalCalendar,
    private val fastEnder: FastEnder,
) : DiaryRepository, DiaryItemEditor, DayCheckIns {

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
    ): List<LogItem> {
        if (items.isEmpty()) return emptyList()
        foodLoggedOn(day)
        return writer.write { insertDrafts(day, items.map { slot to it }, method) }
    }

    override suspend fun updateItem(item: LogItem) = writer.write {
        val existing = tables.entries.get(item.id)?.takeUnless { it.deleted } ?: return@write
        val next = item.sanitized().toEntity(stamp(existing.clientUpdatedAtEpochMs))
        if (next.copy(clientUpdatedAtEpochMs = existing.clientUpdatedAtEpochMs) == existing) return@write
        putEntry(next, base = existing)
    }

    override suspend fun removeItem(id: String) = writer.write {
        val existing = tables.entries.get(id)?.takeUnless { it.deleted } ?: return@write
        putEntry(existing.copy(deleted = true, clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs)), existing)
    }

    override suspend fun moveItem(id: String, slot: MealSlot) = writer.write {
        val existing = tables.entries.get(id)?.takeUnless { it.deleted } ?: return@write
        if (existing.slot == slot.wire) return@write
        putEntry(existing.copy(slot = slot.wire, clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs)), existing)
    }

    override suspend fun removeItems(ids: List<String>) = writer.write {
        ids.forEach { id ->
            val existing = tables.entries.get(id)?.takeUnless { it.deleted } ?: return@forEach
            putEntry(existing.copy(deleted = true, clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs)), existing)
        }
    }

    override suspend fun restoreItems(ids: List<String>) = writer.write {
        ids.forEach { id ->
            val existing = tables.entries.get(id)?.takeIf { it.deleted } ?: return@forEach
            putEntry(existing.copy(deleted = false, clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs)), existing)
        }
    }

    override suspend fun moveItems(ids: List<String>, slot: MealSlot) = writer.write {
        ids.forEach { id ->
            val existing = tables.entries.get(id)?.takeUnless { it.deleted } ?: return@forEach
            if (existing.slot == slot.wire) return@forEach
            putEntry(existing.copy(slot = slot.wire, clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs)), existing)
        }
    }

    override suspend fun copyItems(ids: List<String>, toDay: LocalDate, slot: MealSlot): List<LogItem> {
        val source = ids.mapNotNull { id -> tables.entries.get(id)?.takeUnless { it.deleted }?.toDomain() }
        if (source.isNotEmpty()) foodLoggedOn(toDay)
        return writer.write { insertDrafts(toDay, source.map { slot to it.toDraft() }, LogMethod.Copy) }
    }

    override suspend fun copyDay(from: LocalDate, to: LocalDate): List<LogItem> {
        val source = tables.entries.getDay(from.toString()).mapNotNull { it.toDomain() }
        if (source.isNotEmpty()) foodLoggedOn(to)
        return writer.write { insertDrafts(to, source.map { it.slot to it.toDraft() }, LogMethod.Copy) }
    }

    override suspend fun copySlot(fromDay: LocalDate, toDay: LocalDate, slot: MealSlot): List<LogItem> {
        val source = tables.entries.getDay(fromDay.toString()).mapNotNull { it.toDomain() }.filter { it.slot == slot }
        if (source.isNotEmpty()) foodLoggedOn(toDay)
        return writer.write { insertDrafts(toDay, source.map { slot to it.toDraft() }, LogMethod.Copy) }
    }

    override suspend fun clearDay(day: LocalDate) = writer.write {
        tables.entries.getDay(day.toString()).forEach { existing ->
            putEntry(existing.copy(deleted = true, clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs)), existing)
        }
        changeDay(day) { it.copy(ateNoDetailsJson = emptySet<MealSlot>().toAteJson()) }
    }

    override suspend fun addWater(day: LocalDate, ml: Int) = writer.write {
        changeDay(day) { it.copy(waterMl = clampWater((it.waterMl ?: 0) + ml.coerceAtLeast(0))) }
    }

    override suspend fun removeWater(day: LocalDate, ml: Int) = writer.write {
        changeDay(day) { it.copy(waterMl = clampWater((it.waterMl ?: 0) - ml.coerceAtLeast(0))) }
    }

    override suspend fun setWater(day: LocalDate, ml: Int) = writer.write {
        changeDay(day) { it.copy(waterMl = clampWater(ml)) }
    }

    override suspend fun closeDay(day: LocalDate) = writer.write {
        changeDay(day) { if (it.closedAtEpochMs != null) it else it.copy(closedAtEpochMs = nowEpochMs) }
    }

    override suspend fun reopenDay(day: LocalDate) = writer.write {
        changeDay(day) { it.copy(closedAtEpochMs = null) }
    }

    override suspend fun setDayNote(day: LocalDate, note: String?) = writer.write {
        changeDay(day) { it.copy(note = note?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_NOTE)) }
    }

    override suspend fun setUntracked(day: LocalDate, untracked: Boolean) = writer.write {
        changeDay(day) { it.copy(untracked = untracked) }
    }

    override suspend fun setDayFeeling(day: LocalDate, feeling: DayFeeling?) = writer.write {
        changeDay(day) { it.copy(feeling = feeling?.wire) }
    }

    override suspend fun setCheckIn(day: LocalDate, checkIn: CheckIn?) = writer.write {
        changeDay(day) { it.copy(checkIn = checkIn.toCheckInJson()) }
    }

    override suspend fun setAteNoDetails(day: LocalDate, slot: MealSlot, ate: Boolean) {
        writer.write {
            changeDay(day) { record ->
                val slots = record.ateNoDetailsSlots()
                record.copy(ateNoDetailsJson = (if (ate) slots + slot else slots - slot).toAteJson())
            }
        }
        if (ate) foodLoggedOn(day)
    }

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

    override suspend fun setFeeling(day: LocalDate, slot: MealSlot, feeling: MealFeeling?) = writer.write {
        val key = day.toString()
        val existing = tables.feelings.get(key, slot.wire)
        val next: MealFeelingEntity = if (feeling == null || feeling.isEmpty) {
            if (existing == null || existing.deleted) return@write
            existing.copy(deleted = true, clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs))
        } else {
            val candidate = feeling.copy(note = feeling.note?.take(MAX_NOTE))
                .toEntity(key, slot, stamp(existing?.clientUpdatedAtEpochMs))
            if (existing != null && candidate.copy(clientUpdatedAtEpochMs = existing.clientUpdatedAtEpochMs) == existing) {
                return@write
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
    }

    override suspend fun setMealGroups(day: LocalDate, on: Boolean?) = writer.write {
        changeDay(day) { it.copy(mealGroups = on) }
    }

    override fun recentFoods(limit: Int): Flow<List<LogItem>> =
        tables.entries.observeLatestFoods(RECENT_SCAN).map { rows ->
            rows.mapNotNull { it.toDomain() }.distinctBy { it.foodKey() }.take(limit)
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

        fun clampWater(ml: Int): Int = ml.coerceIn(0, MAX_WATER_ML)
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
