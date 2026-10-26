package com.opson.libraries.diary.impl

import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.SyncEntity
import com.opson.libraries.diary.impl.wire.DayRecordRowDto
import com.opson.libraries.diary.impl.wire.LogEntryRowDto
import com.opson.libraries.diary.impl.wire.WireJson
import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [DiaryRepositoryImpl] over an in-memory database: that each write lands
 * with its outbox mutation in one transaction, how waiting mutations coalesce,
 * and the day operations the Today screen leans on (copy, clear, water, meal
 * groups, calendar dots, usual picks).
 *
 * Not covered here: SQL (the fake DAOs filter in Kotlin; the queries are
 * exercised on device), and what happens to the outbox afterwards, which is
 * [DiarySyncerTest].
 */
class DiaryRepositoryImplTest : CoroutineTest() {
    private val db = FakeDiaryDb()
    private val clock = FakeClock()
    private val scheduler = FakeScheduler()
    private val settings = FakeSettingsRepository()
    private val calendar = FakeCalendar()
    private val repo = DiaryRepositoryImpl(db.tables, DiaryWriter(db.tables, clock, scheduler), settings, calendar)

    private val today = LocalDate(2026, 10, 25)
    private val yesterday = LocalDate(2026, 10, 24)

    @Test
    fun addItemsWritesTheRowsAndTheirOutboxMutationsInOneTransaction() = runUnitTest {
        val logged = repo.addItems(
            today,
            MealSlot.Lunch,
            listOf(draft("Jasmine rice", kcal = 206.0), draft("Apple", kcal = null)),
            LogMethod.Search,
        )

        assertEquals(2, db.entries.rows.size)
        assertEquals(2, db.outbox.rows.size)
        assertEquals(emptyList(), db.writesOutsideTransaction)
        assertEquals(1, db.transactionsCommitted)
        assertEquals(1, scheduler.requests)

        val rice = payloadFor(logged[0].id)
        assertEquals("lunch", rice.slot)
        assertEquals("search", rice.method)
        assertEquals(206.0, rice.kcal)
        assertEquals("2026-10-25", rice.day)
        assertNull(payloadFor(logged[1].id).kcal, "an unknown amount goes up as unknown, not zero")
        assertTrue(db.outbox.rows.all { it.entity == SyncEntity.LogEntry.wire && it.basePayload == null })
    }

    @Test
    fun aFailedOutboxWriteRollsTheRowBack() = runUnitTest {
        db.failNextOutboxInsert = true

        val result = runCatching { repo.addItems(today, MealSlot.Dinner, listOf(draft("Soup")), LogMethod.Search) }

        assertTrue(result.isFailure)
        assertTrue(db.entries.rows.isEmpty(), "the row must not outlive its mutation")
        assertTrue(db.outbox.rows.isEmpty())
        assertEquals(0, scheduler.requests)
    }

    @Test
    fun aSecondChangeToARowReplacesItsWaitingMutationUnderANewId() = runUnitTest {
        val item = repo.addItems(today, MealSlot.Lunch, listOf(draft("Toast")), LogMethod.Search).single()
        val first = db.outbox.rows.single()

        repo.moveItem(item.id, MealSlot.Breakfast)

        val second = db.outbox.rows.single()
        assertNotEquals(first.id, second.id, "the server's ack for the first id must not clear the second")
        assertEquals("breakfast", decode(second.payload).slot)
        assertNull(second.basePayload, "the row was new, so a rejection removes it")
    }

    @Test
    fun editsInTheSameMillisecondStillMoveTheTimestampForward() = runUnitTest {
        val item = repo.addItems(today, MealSlot.Lunch, listOf(draft("Toast")), LogMethod.Search).single()
        val before = db.entries.state.value.getValue(item.id).clientUpdatedAtEpochMs

        repo.moveItem(item.id, MealSlot.Snack)

        val after = db.entries.state.value.getValue(item.id).clientUpdatedAtEpochMs
        assertTrue(after > before, "the server keeps only strictly newer writes")
    }

    @Test
    fun removingAnItemWritesATombstoneThatSyncs() = runUnitTest {
        val item = repo.addItems(today, MealSlot.Lunch, listOf(draft("Toast")), LogMethod.Search).single()

        repo.removeItem(item.id)

        assertTrue(db.entries.state.value.getValue(item.id).deleted)
        assertTrue(decode(db.outbox.rows.single().payload).deleted)
        assertTrue(repo.observeDay(today).first().allItems.isEmpty())
    }

    @Test
    fun withMealGroupsOffItemsLandInUncategorized() = runUnitTest {
        repo.setMealGroups(today, false)
        val forDay = repo.addItems(today, MealSlot.Lunch, listOf(draft("Toast")), LogMethod.Search).single()
        assertEquals(MealSlot.Uncategorized, forDay.slot)

        settings.state.value = Settings(mealGroups = false)
        val fromSettings = repo.addItems(yesterday, MealSlot.Dinner, listOf(draft("Soup")), LogMethod.Search).single()
        assertEquals(MealSlot.Uncategorized, fromSettings.slot)
    }

    @Test
    fun copyDayAppendsCopiesWithNewIdsAndLeavesTheSourceAlone() = runUnitTest {
        val source = repo.addItems(yesterday, MealSlot.Breakfast, listOf(draft("Oats", kcal = 300.0)), LogMethod.Search) +
            repo.addItems(yesterday, MealSlot.Dinner, listOf(draft("Adobo", kcal = 310.0)), LogMethod.Search)
        repo.addItems(today, MealSlot.Lunch, listOf(draft("Salad")), LogMethod.Search)

        val copies = repo.copyDay(yesterday, today)

        val day = repo.observeDay(today).first()
        assertEquals(3, day.allItems.size, "copies are appended to what the day already has")
        assertEquals(listOf("Oats"), day.items(MealSlot.Breakfast).map { it.name })
        assertEquals(listOf("Adobo"), day.items(MealSlot.Dinner).map { it.name })
        assertTrue(copies.all { it.method == LogMethod.Copy && it.day == today })
        assertTrue(copies.map { it.id }.none { id -> id in source.map { it.id } })
        assertEquals(2, repo.observeDay(yesterday).first().allItems.size)
    }

    @Test
    fun copySlotCopiesOnlyThatMeal() = runUnitTest {
        repo.addItems(yesterday, MealSlot.Breakfast, listOf(draft("Oats")), LogMethod.Search)
        repo.addItems(yesterday, MealSlot.Dinner, listOf(draft("Adobo"), draft("Rice")), LogMethod.Search)

        repo.copySlot(yesterday, today, MealSlot.Dinner)

        val day = repo.observeDay(today).first()
        assertEquals(listOf("Adobo", "Rice"), day.items(MealSlot.Dinner).map { it.name })
        assertTrue(day.items(MealSlot.Breakfast).isEmpty())
    }

    @Test
    fun clearDayRemovesItemsAndAteMarksButKeepsWaterAndTheNote() = runUnitTest {
        repo.addItems(today, MealSlot.Lunch, listOf(draft("Toast")), LogMethod.Search)
        repo.setAteNoDetails(today, MealSlot.Snack, true)
        repo.addWater(today, 500)
        repo.setDayNote(today, "Busy day")

        repo.clearDay(today)

        val day = repo.observeDay(today).first()
        assertTrue(day.allItems.isEmpty())
        assertTrue(day.ateNoDetails.isEmpty())
        assertEquals(500, day.waterMl)
        assertEquals("Busy day", day.note)
        val dayRow = WireJson.decodeFromString(
            DayRecordRowDto.serializer(),
            db.outbox.rows.single { it.entity == SyncEntity.DayRecord.wire }.payload,
        )
        assertEquals(emptyMap(), dayRow.ateNoDetails)
        assertTrue(db.outbox.rows.filter { it.entity == SyncEntity.LogEntry.wire }.all { decode(it.payload).deleted })
    }

    @Test
    fun waterNeverGoesBelowZero() = runUnitTest {
        repo.addWater(today, 250)
        repo.removeWater(today, 500)

        assertEquals(0, repo.observeDay(today).first().waterMl)
    }

    @Test
    fun aWriteThatChangesNothingQueuesNothing() = runUnitTest {
        repo.reopenDay(today)

        assertTrue(db.outbox.rows.isEmpty())
        assertEquals(0, scheduler.requests)
    }

    @Test
    fun calendarDotsMarkDaysWithItemsOrAteNoDetails() = runUnitTest {
        repo.addItems(LocalDate(2026, 10, 3), MealSlot.Lunch, listOf(draft("Toast")), LogMethod.Search)
        repo.setAteNoDetails(LocalDate(2026, 10, 9), MealSlot.Dinner, true)
        repo.addWater(LocalDate(2026, 10, 12), 250)
        repo.addItems(LocalDate(2026, 11, 1), MealSlot.Lunch, listOf(draft("Toast")), LogMethod.Search)

        val dots = repo.observeLoggedDates(2026, 10).first()

        assertEquals(setOf(LocalDate(2026, 10, 3), LocalDate(2026, 10, 9)), dots)
    }

    @Test
    fun usualAtRanksFoodsByHowOftenTheyWereLoggedInThatSlot() = runUnitTest {
        repeat(3) { i ->
            repo.addItems(LocalDate(2026, 10, 20 + i), MealSlot.Breakfast, listOf(draft("Oats")), LogMethod.Search)
        }
        repo.addItems(LocalDate(2026, 10, 24), MealSlot.Breakfast, listOf(draft("Eggs")), LogMethod.Search)
        repo.addItems(LocalDate(2026, 10, 24), MealSlot.Dinner, listOf(draft("Adobo")), LogMethod.Search)
        repo.addItems(LocalDate(2026, 8, 1), MealSlot.Breakfast, listOf(draft("Pancakes")), LogMethod.Search)

        val usual = repo.usualAt(MealSlot.Breakfast).first()

        assertEquals(listOf("Oats", "Eggs"), usual.map { it.name })
    }

    @Test
    fun frequentFoodsRanksAcrossEverySlotWithinTheWindow() = runUnitTest {
        repo.addItems(LocalDate(2026, 10, 20), MealSlot.Breakfast, listOf(draft("Banana")), LogMethod.Search)
        repo.addItems(LocalDate(2026, 10, 21), MealSlot.Snack, listOf(draft("Banana")), LogMethod.Search)
        repo.addItems(LocalDate(2026, 10, 22), MealSlot.Dinner, listOf(draft("Banana")), LogMethod.Search)
        repo.addItems(LocalDate(2026, 10, 23), MealSlot.Lunch, listOf(draft("Rice"), draft("Rice")), LogMethod.Search)
        repo.addItems(LocalDate(2026, 10, 24), MealSlot.Lunch, listOf(draft("Adobo")), LogMethod.Search)
        repo.addItems(LocalDate(2026, 8, 1), MealSlot.Lunch, List(4) { draft("Old soup") }, LogMethod.Search)

        val favorites = repo.frequentFoods(limit = 3).first()

        assertEquals(listOf("Banana", "Rice", "Adobo"), favorites.map { it.name })
    }

    @Test
    fun observeDaysIncludesEveryDateInTheRange() = runUnitTest {
        repo.addItems(LocalDate(2026, 10, 23), MealSlot.Lunch, listOf(draft("Toast", kcal = 100.0)), LogMethod.Search)

        val days = repo.observeDays(LocalDate(2026, 10, 22), LocalDate(2026, 10, 24)).first()

        assertEquals(listOf(22, 23, 24), days.map { it.date.day })
        assertEquals(100.0, days[1].totals().kcal.value)
        assertEquals(0.0, days[0].totals().kcal.value, "an empty day really is zero")
    }

    @Test
    fun untrackedAndTheDayFeelingSyncOnTheDayRecord() = runUnitTest {
        repo.setUntracked(today, true)
        repo.setDayFeeling(today, DayFeeling.Steady)

        val day = repo.observeDay(today).first()
        assertTrue(day.untracked)
        assertEquals(DayFeeling.Steady, day.feeling)
        val row = WireJson.decodeFromString(DayRecordRowDto.serializer(), db.outbox.rows.single().payload)
        assertTrue(row.untracked)
        assertEquals("steady", row.feeling)

        repo.setUntracked(today, false)
        repo.setDayFeeling(today, null)
        val cleared = repo.observeDay(today).first()
        assertEquals(false, cleared.untracked)
        assertNull(cleared.feeling)
    }

    private fun payloadFor(id: String): LogEntryRowDto = decode(db.outbox.rows.single { it.entityId == id }.payload)

    private fun decode(payload: String) = WireJson.decodeFromString(LogEntryRowDto.serializer(), payload)

    private fun draft(name: String, kcal: Double? = 100.0) = LogDraft(name = name, precision = Precision.Estimate, kcal = kcal)
}

class FakeSettingsRepository(initial: Settings = Settings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: StateFlow<Settings> = state
    override val isKnown: StateFlow<Boolean> = MutableStateFlow(true)

    override suspend fun update(stampNumbersChange: Boolean, transform: (Settings) -> Settings): Settings =
        transform(state.value).also { state.value = it }
}
