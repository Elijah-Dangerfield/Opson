package com.opson.libraries.diary.impl

import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.CheckInMood
import com.opson.libraries.diary.Energy
import com.opson.libraries.diary.HungerLevel
import com.opson.libraries.diary.MoodLevel
import com.opson.libraries.diary.Sleep
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.DayReflection
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.MealFeeling
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
import kotlin.time.Duration.Companion.minutes

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
    private val fastEnder = FakeFastEnder()
    private val repo = DiaryRepositoryImpl(db.tables, DiaryWriter(db.tables, clock, scheduler), settings, calendar, fastEnder)

    private val today = LocalDate(2026, 10, 25)
    private val yesterday = LocalDate(2026, 10, 24)

    @Test
    fun addItemsWritesTheRowsAndTheirOutboxMutationsInOneTransaction() = runUnitTest {
        val logged = repo.addItems(
            today,
            MealSlot.Lunch,
            listOf(draft("Jasmine rice", kcal = 206.0), draft("Apple", kcal = null)),
            LogMethod.Search,
        ).written()

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

        val result = runCatching { repo.addItems(today, MealSlot.Dinner, listOf(draft("Soup")), LogMethod.Search).written() }

        assertTrue(result.isFailure)
        assertTrue(db.entries.rows.isEmpty(), "the row must not outlive its mutation")
        assertTrue(db.outbox.rows.isEmpty())
        assertEquals(0, scheduler.requests)
    }

    @Test
    fun aSecondChangeToARowReplacesItsWaitingMutationUnderANewId() = runUnitTest {
        val item = repo.addItems(today, MealSlot.Lunch, listOf(draft("Toast")), LogMethod.Search).written().single()
        val first = db.outbox.rows.single()

        repo.moveItem(item.id, MealSlot.Breakfast)

        val second = db.outbox.rows.single()
        assertNotEquals(first.id, second.id, "the server's ack for the first id must not clear the second")
        assertEquals("breakfast", decode(second.payload).slot)
        assertNull(second.basePayload, "the row was new, so a rejection removes it")
    }

    @Test
    fun editsInTheSameMillisecondStillMoveTheTimestampForward() = runUnitTest {
        val item = repo.addItems(today, MealSlot.Lunch, listOf(draft("Toast")), LogMethod.Search).written().single()
        val before = db.entries.state.value.getValue(item.id).clientUpdatedAtEpochMs

        repo.moveItem(item.id, MealSlot.Snack)

        val after = db.entries.state.value.getValue(item.id).clientUpdatedAtEpochMs
        assertTrue(after > before, "the server keeps only strictly newer writes")
    }

    @Test
    fun removingAnItemWritesATombstoneThatSyncs() = runUnitTest {
        val item = repo.addItems(today, MealSlot.Lunch, listOf(draft("Toast")), LogMethod.Search).written().single()

        repo.removeItem(item.id)

        assertTrue(db.entries.state.value.getValue(item.id).deleted)
        assertTrue(decode(db.outbox.rows.single().payload).deleted)
        assertTrue(repo.observeDay(today).first().allItems.isEmpty())
    }

    @Test
    fun withMealGroupsOffItemsLandInUncategorized() = runUnitTest {
        repo.setMealGroups(today, false)
        val forDay = repo.addItems(today, MealSlot.Lunch, listOf(draft("Toast")), LogMethod.Search).written().single()
        assertEquals(MealSlot.Uncategorized, forDay.slot)

        settings.state.value = Settings(mealGroups = false)
        val fromSettings = repo.addItems(yesterday, MealSlot.Dinner, listOf(draft("Soup")), LogMethod.Search).written().single()
        assertEquals(MealSlot.Uncategorized, fromSettings.slot)
    }

    @Test
    fun copyDayAppendsCopiesWithNewIdsAndLeavesTheSourceAlone() = runUnitTest {
        val source = repo.addItems(yesterday, MealSlot.Breakfast, listOf(draft("Oats", kcal = 300.0)), LogMethod.Search).written() +
            repo.addItems(yesterday, MealSlot.Dinner, listOf(draft("Adobo", kcal = 310.0)), LogMethod.Search).written()
        repo.addItems(today, MealSlot.Lunch, listOf(draft("Salad")), LogMethod.Search).written()

        val copies = repo.copyDay(yesterday, today).written()

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
        repo.addItems(yesterday, MealSlot.Breakfast, listOf(draft("Oats")), LogMethod.Search).written()
        repo.addItems(yesterday, MealSlot.Dinner, listOf(draft("Adobo"), draft("Rice")), LogMethod.Search).written()

        repo.copySlot(yesterday, today, MealSlot.Dinner).written()

        val day = repo.observeDay(today).first()
        assertEquals(listOf("Adobo", "Rice"), day.items(MealSlot.Dinner).map { it.name })
        assertTrue(day.items(MealSlot.Breakfast).isEmpty())
    }

    @Test
    fun clearDayRemovesItemsAndAteMarksButKeepsWaterAndTheNote() = runUnitTest {
        repo.addItems(today, MealSlot.Lunch, listOf(draft("Toast")), LogMethod.Search).written()
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
        repo.addItems(LocalDate(2026, 10, 3), MealSlot.Lunch, listOf(draft("Toast")), LogMethod.Search).written()
        repo.setAteNoDetails(LocalDate(2026, 10, 9), MealSlot.Dinner, true)
        repo.addWater(LocalDate(2026, 10, 12), 250)
        repo.addItems(LocalDate(2026, 11, 1), MealSlot.Lunch, listOf(draft("Toast")), LogMethod.Search).written()

        val dots = repo.observeLoggedDates(2026, 10).first()

        assertEquals(setOf(LocalDate(2026, 10, 3), LocalDate(2026, 10, 9)), dots)
    }

    @Test
    fun usualAtRanksFoodsByHowOftenTheyWereLoggedInThatSlot() = runUnitTest {
        repeat(3) { i ->
            repo.addItems(LocalDate(2026, 10, 20 + i), MealSlot.Breakfast, listOf(draft("Oats")), LogMethod.Search).written()
        }
        repo.addItems(LocalDate(2026, 10, 24), MealSlot.Breakfast, listOf(draft("Eggs")), LogMethod.Search).written()
        repo.addItems(LocalDate(2026, 10, 24), MealSlot.Dinner, listOf(draft("Adobo")), LogMethod.Search).written()
        repo.addItems(LocalDate(2026, 8, 1), MealSlot.Breakfast, listOf(draft("Pancakes")), LogMethod.Search).written()

        val usual = repo.usualAt(MealSlot.Breakfast).first()

        assertEquals(listOf("Oats", "Eggs"), usual.map { it.name })
    }

    /** The Foods tab drops what has no food behind it, so counting those left Recent short of six. */
    @Test
    fun recentFoodsSkipsQuickNumbersAndNameOnlyLines() = runUnitTest {
        val foods = listOf("Oats", "Eggs", "Rice", "Adobo", "Banana", "Yogurt")
        foods.forEach { name ->
            repo.addItems(today, MealSlot.Lunch, listOf(draft(name).copy(foodId = "f-$name")), LogMethod.Search).written()
            clock.now += 1.minutes
            repo.addItems(today, MealSlot.Lunch, listOf(draft("Quick number", kcal = 300.0)), LogMethod.QuickNumber).written()
            clock.now += 1.minutes
            repo.addItems(today, MealSlot.Lunch, listOf(draft("Something at the market")), LogMethod.Search).written()
            clock.now += 1.minutes
        }

        val recent = repo.recentFoods(limit = 6).first()

        assertEquals(foods.reversed(), recent.map { it.name })
    }

    @Test
    fun aFoodTakenOffRecentStaysOffUntilItIsLoggedAgain() = runUnitTest {
        repo.addItems(today, MealSlot.Lunch, listOf(draft("Oats").copy(foodId = "oats")), LogMethod.Search)
        clock.now += 1.minutes
        repo.addItems(today, MealSlot.Lunch, listOf(draft("Eggs").copy(foodId = "eggs")), LogMethod.Search)
        clock.now += 1.minutes
        settings.update { it.hideFromRecent("f:oats", clock.now.toEpochMilliseconds()) }

        assertEquals(listOf("Eggs"), repo.recentFoods(limit = 6).first().map { it.name })

        clock.now += 1.minutes
        repo.addItems(today, MealSlot.Dinner, listOf(draft("Oats").copy(foodId = "oats")), LogMethod.Search)
        assertEquals(listOf("Oats", "Eggs"), repo.recentFoods(limit = 6).first().map { it.name })
    }

    @Test
    fun frequentFoodsRanksAcrossEverySlotWithinTheWindow() = runUnitTest {
        repo.addItems(LocalDate(2026, 10, 20), MealSlot.Breakfast, listOf(draft("Banana")), LogMethod.Search).written()
        repo.addItems(LocalDate(2026, 10, 21), MealSlot.Snack, listOf(draft("Banana")), LogMethod.Search).written()
        repo.addItems(LocalDate(2026, 10, 22), MealSlot.Dinner, listOf(draft("Banana")), LogMethod.Search).written()
        repo.addItems(LocalDate(2026, 10, 23), MealSlot.Lunch, listOf(draft("Rice"), draft("Rice")), LogMethod.Search).written()
        repo.addItems(LocalDate(2026, 10, 24), MealSlot.Lunch, listOf(draft("Adobo")), LogMethod.Search).written()
        repo.addItems(LocalDate(2026, 8, 1), MealSlot.Lunch, List(4) { draft("Old soup") }, LogMethod.Search).written()

        val favorites = repo.frequentFoods(limit = 3).first()

        assertEquals(listOf("Banana", "Rice", "Adobo"), favorites.map { it.name })
    }

    @Test
    fun observeDaysIncludesEveryDateInTheRange() = runUnitTest {
        repo.addItems(LocalDate(2026, 10, 23), MealSlot.Lunch, listOf(draft("Toast", kcal = 100.0)), LogMethod.Search).written()

        val days = repo.observeDays(LocalDate(2026, 10, 22), LocalDate(2026, 10, 24)).first()

        assertEquals(listOf(22, 23, 24), days.map { it.date.day })
        assertEquals(100.0, days[1].totals().kcal.value)
        assertEquals(0.0, days[0].totals().kcal.value, "an empty day really is zero")
    }

    @Test
    fun untrackedAndTheDayFeelingSyncOnTheDayRecord() = runUnitTest {
        repo.setUntracked(today, true)
        repo.closeDay(today, DayReflection(feeling = DayFeeling.Steady))

        val day = repo.observeDay(today).first()
        assertTrue(day.untracked)
        assertEquals(DayFeeling.Steady, day.feeling)
        val row = WireJson.decodeFromString(DayRecordRowDto.serializer(), db.outbox.rows.single().payload)
        assertTrue(row.untracked)
        assertEquals("steady", row.feeling)

        repo.setUntracked(today, false)
        repo.closeDay(today, DayReflection(feeling = null))
        val cleared = repo.observeDay(today).first()
        assertEquals(false, cleared.untracked)
        assertNull(cleared.feeling)
    }

    @Test
    fun removedItemsComeBackOnRestore_andBothSync() = runUnitTest {
        val items = repo.addItems(today, MealSlot.Breakfast, listOf(draft("Oats"), draft("Banana"), draft("Coffee")), LogMethod.Search).written()
        val picked = items.take(2).map { it.id }

        repo.removeItems(picked)
        assertEquals(listOf("Coffee"), repo.observeDay(today).first().allItems.map { it.name })

        repo.restoreItems(picked)
        assertEquals(listOf("Oats", "Banana", "Coffee"), repo.observeDay(today).first().allItems.map { it.name })
        assertTrue(picked.none { decode(db.outbox.rows.single { row -> row.entityId == it }.payload).deleted }, "the restore is what syncs")
    }

    @Test
    fun movingAndCopyingSeveralItems() = runUnitTest {
        val items = repo.addItems(today, MealSlot.Uncategorized, listOf(draft("Granola"), draft("Sparkling water")), LogMethod.Search).written()

        repo.moveItems(items.map { it.id }, MealSlot.Snack)
        assertEquals(listOf("Granola", "Sparkling water"), repo.observeDay(today).first().items(MealSlot.Snack).map { it.name })

        val copies = repo.copyItems(items.map { it.id }, yesterday, MealSlot.Dinner).written()
        assertEquals(listOf("Granola", "Sparkling water"), repo.observeDay(yesterday).first().items(MealSlot.Dinner).map { it.name })
        assertTrue(copies.all { it.method == LogMethod.Copy })
    }

    @Test
    fun theCheckInSyncsOnTheDayRecord_andEmptyClearsIt() = runUnitTest {
        val checkIn = CheckIn(
            hunger = HungerLevel.of(4),
            sleep = Sleep.Great,
            mood = MoodLevel.Ok,
            moods = setOf(CheckInMood.Social, CheckInMood.Calm),
        )

        repo.setCheckIn(today, checkIn)

        assertEquals(checkIn, repo.observeDay(today).first().checkIn)
        val row = WireJson.decodeFromString(DayRecordRowDto.serializer(), db.outbox.rows.single().payload)
        assertEquals(
            """{"hunger":"steady","hungerLevel":4,"sleep":"great","mood":"ok","moods":["calm","social"]}""",
            row.checkIn,
            "the nearest old hunger word rides along for a 0.2 phone",
        )

        repo.setCheckIn(today, CheckIn())
        assertNull(repo.observeDay(today).first().checkIn)
    }

    @Test
    fun theCheckInIsItsOwnWrite_closingKeepsIt_andAClosedDayStillTakesOne() = runUnitTest {
        repo.setCheckIn(today, CheckIn(energy = Energy.Good))
        repo.closeDay(today, DayReflection(note = "Done"))
        assertEquals(CheckIn(energy = Energy.Good), repo.observeDay(today).first().checkIn, "closing leaves the check-in alone")

        repo.setCheckIn(today, CheckIn(energy = Energy.Low))

        val day = repo.observeDay(today).first()
        assertEquals(CheckIn(energy = Energy.Low), day.checkIn)
        assertTrue(day.closedByUser, "and the day stays closed")
    }

    @Test
    fun aCheckInFromBeforeTheScale_readsItsHungerWordAsAStep_andKeepsTheRest() {
        val old = """{"hunger":"often","energy":"low","sleep":"poor","moods":["tired"]}""".toCheckIn()
        assertEquals(
            CheckIn(hunger = HungerLevel.of(3), energy = Energy.Low, sleep = Sleep.Poor, moods = setOf(CheckInMood.Tired)),
            old,
        )
        assertEquals(listOf(6, 5, 4, 3, 2), listOf("rarely", "some", "steady", "often", "very").map { """{"hunger":"$it"}""".toCheckIn()?.hunger?.value })
        assertEquals(HungerLevel.of(8), """{"hunger":"very","hungerLevel":8}""".toCheckIn()?.hunger, "the scale wins over the old word")
        assertNull("""{"hungerLevel":11,"energy":"sideways"}""".toCheckIn(), "off the scale and unknown words read as unanswered")
        assertNull("not json".toCheckIn())
    }

    @Test
    fun closingSavesTheReflectionWithTheClose_inOneMutation_andKeepsTheFirstCloseTime() = runUnitTest {
        repo.closeDay(today, DayReflection(note = "  Dinner at Lola's  ", feeling = DayFeeling.Good))
        val firstClose = repo.observeDay(today).first().closedAt
        clock.now += 1.minutes

        repo.closeDay(today, DayReflection(note = "Dinner at Lola's, a lazy Sunday", feeling = DayFeeling.Good))

        val day = repo.observeDay(today).first()
        assertEquals("Dinner at Lola's, a lazy Sunday", day.note)
        assertEquals(firstClose, day.closedAt, "closing again updates the note, not when the day was closed")
        assertEquals(1, db.outbox.rows.size, "one day record waiting, carrying the close and the note together")
    }

    @Test
    fun aClosedDayRefusesEveryWriteToItsLog_andWritesNothing() = runUnitTest {
        val oats = repo.addItems(today, MealSlot.Breakfast, listOf(draft("Oats")), LogMethod.Search).written().single()
        repo.addItems(yesterday, MealSlot.Dinner, listOf(draft("Adobo")), LogMethod.Search).written()
        repo.closeDay(today, DayReflection())
        val outboxBefore = db.outbox.rows.toList()
        val dayBefore = repo.observeDay(today).first()
        val fastsEndedBefore = fastEnder.foodLogged
        val closed = DayWrite.DayClosed(today)

        assertEquals(closed, repo.addItems(today, MealSlot.Lunch, listOf(draft("Rice")), LogMethod.Search))
        assertEquals(closed, repo.copyDay(from = yesterday, to = today))
        assertEquals(closed, repo.copySlot(fromDay = yesterday, toDay = today, slot = MealSlot.Dinner))
        assertEquals(closed, repo.addWater(today, 250))
        assertEquals(closed, repo.removeWater(today, 250))
        assertEquals(closed, repo.setWater(today, 1_000))
        assertEquals(closed, repo.setDayNote(today, "A note"))
        assertEquals(closed, repo.setAteNoDetails(today, MealSlot.Lunch, ate = true))
        assertEquals(closed, repo.setFeeling(today, MealSlot.Breakfast, MealFeeling(note = "Quick one")))
        assertEquals(closed, repo.updateItem(oats.copy(name = "Overnight oats")))
        assertEquals(closed, repo.removeItem(oats.id))
        assertEquals(closed, repo.moveItem(oats.id, MealSlot.Snack))
        assertEquals(closed, repo.clearDay(today))

        assertEquals(outboxBefore, db.outbox.rows.toList(), "nothing queued")
        assertEquals(dayBefore, repo.observeDay(today).first(), "nothing changed")
        assertEquals(fastsEndedBefore, fastEnder.foodLogged, "food a closed day refused ends no fast")
    }

    @Test
    fun aBatchThatTouchesAClosedDayWritesNothing_evenForItsOpenDays() = runUnitTest {
        val open = repo.addItems(yesterday, MealSlot.Lunch, listOf(draft("Toast")), LogMethod.Search).written().single()
        val shut = repo.addItems(today, MealSlot.Lunch, listOf(draft("Soup")), LogMethod.Search).written().single()
        repo.closeDay(today, DayReflection())
        val outboxBefore = db.outbox.rows.toList()
        val closed = DayWrite.DayClosed(today)

        assertEquals(closed, repo.removeItems(listOf(open.id, shut.id)))
        assertEquals(closed, repo.moveItems(listOf(open.id, shut.id), MealSlot.Dinner))
        assertEquals(closed, repo.copyItems(listOf(open.id), toDay = today, slot = MealSlot.Dinner))

        assertEquals(outboxBefore, db.outbox.rows.toList())
        assertEquals(listOf("Toast"), repo.observeDay(yesterday).first().items(MealSlot.Lunch).map { it.name })
    }

    @Test
    fun aClosedDayStillCopiesOut_marksUntracked_andReopens() = runUnitTest {
        val soup = repo.addItems(today, MealSlot.Lunch, listOf(draft("Soup")), LogMethod.Search).written().single()
        repo.closeDay(today, DayReflection())

        val copied = repo.copyItems(listOf(soup.id), toDay = yesterday, slot = MealSlot.Dinner)
        repo.setUntracked(today, true)

        assertEquals(listOf("Soup"), copied.written().map { it.name }, "copying from a closed day to an open one is fine")
        assertTrue(repo.observeDay(today).first().untracked)

        repo.reopenDay(today)

        assertTrue(repo.addWater(today, 250) is DayWrite.Written)
        assertEquals(250, repo.observeDay(today).first().waterMl)
        assertEquals(false, repo.observeDay(today).first().closedByUser)
    }

    @Test
    fun aPastDayNobodyClosedStaysOpenToLogging() = runUnitTest {
        val logged = repo.addItems(LocalDate(2026, 10, 1), MealSlot.Dinner, listOf(draft("Pancit")), LogMethod.Search)

        assertTrue(logged is DayWrite.Written)
    }

    private fun payloadFor(id: String): LogEntryRowDto = decode(db.outbox.rows.single { it.entityId == id }.payload)

    private fun decode(payload: String) = WireJson.decodeFromString(LogEntryRowDto.serializer(), payload)

    private fun draft(name: String, kcal: Double? = 100.0) = LogDraft(name = name, precision = Precision.Estimate, kcal = kcal)
}

class FakeSettingsRepository(initial: Settings = Settings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: StateFlow<Settings> = state
    val known = MutableStateFlow(true)
    override val isKnown: StateFlow<Boolean> = known

    override suspend fun update(stampNumbersChange: Boolean, transform: (Settings) -> Settings): Settings =
        transform(state.value).also { state.value = it }
}
