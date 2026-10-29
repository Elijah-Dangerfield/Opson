package com.opson.libraries.diary.impl

import com.opson.libraries.diary.DayMark
import com.opson.libraries.diary.DayReflection
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.RepeatDays
import com.opson.libraries.diary.RepeatPlan
import com.opson.libraries.diary.RepeatsRelease
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SyncEntity
import com.opson.libraries.diary.impl.wire.LogEntryRowDto
import com.opson.libraries.diary.impl.wire.WireJson
import com.opson.libraries.diary.repeatEntryId
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.opson.storage.db.ClearableDao
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Repeats over the in-memory database: that a rule logs today once and only once, through the
 * same rows and outbox a logged food uses, what it skips, and that two phones holding one rule
 * write one set of rows. Also that a sign-out keeps them for the same account and another
 * account's sign-in deletes them ([RepeatOwner]); the dump is stood in for by [Phone.dump], the
 * real one is `UserScopedDaoCleaner`. Times are UTC ([FakeCalendar]); the fixtures' today is
 * Sunday Oct 25.
 */
class RepeatRepositoryImplTest : CoroutineTest() {
    private val db = FakeDiaryDb()
    private val clock = FakeClock(Instant.parse("2026-10-25T07:30:00Z"))
    private val calendar = FakeCalendar()
    private val settings = FakeSettingsRepository(Settings())
    private var released = true
    private val phone = Phone(db, clock, calendar, settings) { released }
    private val repeats = phone.repeats
    private val diary = phone.diary

    private val today = LocalDate(2026, 10, 25)
    private val porridge = LogDraft(name = "Oat porridge", precision = Precision.Estimate, foodId = "food-porridge", amountText = "1 bowl", kcal = 300.0)
    private val coffee = LogDraft(name = "Coffee with milk", precision = Precision.Estimate, amountText = "1 mug", kcal = 40.0)

    @Test
    fun aRuleThatFallsOnTodayLogsItsFoodsWhenSaved_inItsMeal_atTheMealsTime() = runUnitTest {
        val saved = repeats.create(plan(listOf(porridge, coffee)))

        assertTrue(saved.loggedToday)
        val breakfast = diary.observeDay(today).first().items(MealSlot.Breakfast)
        assertEquals(listOf("Oat porridge", "Coffee with milk"), breakfast.map { it.name })
        assertEquals(listOf(repeatEntryId(saved.rule.id, today, 0), repeatEntryId(saved.rule.id, today, 1)), breakfast.map { it.id })
        assertTrue(breakfast.all { it.repeatRuleId == saved.rule.id }, "Today can mark them as repeats")
        assertEquals(Instant.parse("2026-10-25T08:00:00Z"), breakfast.first().loggedAt, "the meal's time, not the moment the app opened")
        assertEquals(LogMethod.Copy, breakfast.first().method)
    }

    @Test
    fun theFoodsGoUpThroughTheDiaryOutbox_stampedAtTheStartOfTheDay() = runUnitTest {
        val saved = repeats.create(plan(listOf(porridge)))

        val id = repeatEntryId(saved.rule.id, today, 0)
        val mutation = db.outbox.rows.single()
        assertEquals(SyncEntity.LogEntry.wire, mutation.entity)
        assertEquals(id, mutation.entityId)
        assertEquals("2026-10-25", mutation.day)
        assertNull(mutation.basePayload)
        val row = WireJson.decodeFromString(LogEntryRowDto.serializer(), mutation.payload)
        assertEquals("breakfast", row.slot)
        assertEquals(300.0, row.kcal)
        assertEquals("copy", row.method, "a method every server build takes")
        assertEquals(Instant.parse("2026-10-25T00:00:00Z").toEpochMilliseconds(), db.entries.state.value.getValue(id).clientUpdatedAtEpochMs)
        assertEquals(emptyList(), db.writesOutsideTransaction)
    }

    @Test
    fun loggingAgainTheSameDayWritesNothing() = runUnitTest {
        repeats.create(plan(listOf(porridge)))
        val outbox = db.outbox.rows.toList()

        phone.materializer.logDue()
        phone.materializer.logDue()

        assertEquals(1, db.entries.rows.size)
        assertEquals(outbox, db.outbox.rows.toList())
    }

    @Test
    fun aRemovedEntryStaysRemovedThatDay_andTheRuleStays() = runUnitTest {
        val saved = repeats.create(plan(listOf(porridge)))
        diary.removeItem(repeatEntryId(saved.rule.id, today, 0))

        phone.materializer.logDue()

        assertTrue(diary.observeDay(today).first().allItems.isEmpty())
        assertNotNull(repeats.get(saved.rule.id))
    }

    @Test
    fun aRuleHasHadTodayOnceItLogged_evenWithItsFoodRemoved() = runUnitTest {
        val saved = repeats.create(plan(listOf(porridge)))
        val other = repeats.create(plan(listOf(coffee), start = LocalDate(2026, 10, 26)))
        diary.removeItem(repeatEntryId(saved.rule.id, today, 0))

        assertTrue(repeats.observeHasRun(saved.rule.id, today).first())
        assertFalse(repeats.observeHasRun(other.rule.id, today).first(), "starts tomorrow")
    }

    @Test
    fun theNextDayLogsAgain_withItsOwnIds() = runUnitTest {
        val saved = repeats.create(plan(listOf(porridge)))
        calendar.today = LocalDate(2026, 10, 26)
        clock.now = Instant.parse("2026-10-26T06:00:00Z")

        phone.materializer.logDue()

        val monday = diary.observeDay(LocalDate(2026, 10, 26)).first().items(MealSlot.Breakfast)
        assertEquals(listOf(repeatEntryId(saved.rule.id, LocalDate(2026, 10, 26), 0)), monday.map { it.id })
        assertEquals(1, diary.observeDay(today).first().allItems.size, "Sunday is left as it was")
    }

    @Test
    fun aDayTheAppWasNotOpenedIsNotFilledIn() = runUnitTest {
        repeats.create(plan(listOf(porridge)))
        calendar.today = LocalDate(2026, 10, 28)
        clock.now = Instant.parse("2026-10-28T06:00:00Z")

        phone.materializer.logDue()

        val logged = db.entries.rows.map { it.day }.sorted()
        assertEquals(listOf("2026-10-25", "2026-10-28"), logged, "Monday and Tuesday stay empty")
    }

    @Test
    fun onlyTheDaysPicked_andNothingBeforeTheStartOrAfterTheEnd() = runUnitTest {
        val weekdays = repeats.create(plan(listOf(porridge), days = RepeatDays.Weekdays))
        val later = repeats.create(plan(listOf(coffee), start = LocalDate(2026, 10, 26)))
        val over = repeats.create(plan(listOf(coffee), start = LocalDate(2026, 10, 1), end = LocalDate(2026, 10, 24)))

        assertFalse(weekdays.loggedToday, "Sunday is not a weekday")
        assertFalse(later.loggedToday)
        assertFalse(over.loggedToday)
        assertTrue(db.entries.rows.isEmpty())
    }

    @Test
    fun aPausedRuleLogsNothing_untilItIsResumed() = runUnitTest {
        val saved = repeats.create(plan(listOf(porridge), days = setOf(DayOfWeek.MONDAY)))
        repeats.setPaused(saved.rule.id, true)
        calendar.today = LocalDate(2026, 10, 26)

        phone.materializer.logDue()
        assertTrue(db.entries.rows.isEmpty())

        val resumed = repeats.setPaused(saved.rule.id, false)
        assertEquals(true, resumed?.loggedToday)
        assertEquals(1, db.entries.rows.size)
    }

    @Test
    fun aClosedDayLogsNothing_andReopeningItLetsTheNextRunLog() = runUnitTest {
        diary.closeDay(today, DayReflection())
        repeats.create(plan(listOf(porridge)))
        assertTrue(db.entries.rows.isEmpty())

        diary.reopenDay(today)
        phone.materializer.logDue()

        assertEquals(1, db.entries.rows.size)
    }

    @Test
    fun anUntrackedDayLogsNothing() = runUnitTest {
        diary.setUntracked(today, true)

        val saved = repeats.create(plan(listOf(porridge)))

        assertFalse(saved.loggedToday)
        assertTrue(db.entries.rows.isEmpty())
    }

    @Test
    fun withMealGroupsOffTheFoodsGoToAllFood() = runUnitTest {
        settings.state.value = Settings(mealGroups = false)

        repeats.create(plan(listOf(porridge)))

        assertEquals(listOf("Oat porridge"), diary.observeDay(today).first().items(MealSlot.Uncategorized).map { it.name })
    }

    @Test
    fun theMealsOwnTimeFromSettings() = runUnitTest {
        settings.state.value = Settings(mealTimes = Settings().mealTimes.with(MealSlot.Dinner, LocalTime(18, 15)))

        repeats.create(plan(listOf(porridge), slot = MealSlot.Dinner))

        assertEquals(Instant.parse("2026-10-25T18:15:00Z"), diary.observeDay(today).first().items(MealSlot.Dinner).single().loggedAt)
    }

    @Test
    fun nothingWhileTheFlagIsOff_orBeforeTheUsersSettingsAreKnown() = runUnitTest {
        released = false
        repeats.create(plan(listOf(porridge)))
        assertTrue(db.entries.rows.isEmpty())

        released = true
        settings.known.value = false
        phone.materializer.logDue()
        assertTrue(db.entries.rows.isEmpty())

        settings.known.value = true
        phone.materializer.logDue()
        assertEquals(1, db.entries.rows.size)
    }

    @Test
    fun aSavedMealLogsItsCurrentFoods_andTheSnapshotOnceItIsGone() = runUnitTest {
        val meal = phone.savedMeals.create("Usual breakfast", MealSlot.Breakfast, listOf(porridge, coffee))
        val saved = repeats.create(plan(listOf(porridge), savedMealId = meal.id, name = meal.name))

        assertEquals(listOf("Oat porridge", "Coffee with milk"), diary.observeDay(today).first().allItems.map { it.name })
        assertEquals(LogMethod.Saved, diary.observeDay(today).first().allItems.first().method)

        phone.savedMeals.delete(meal.id)
        calendar.today = LocalDate(2026, 10, 26)
        phone.materializer.logDue()

        val monday = diary.observeDay(LocalDate(2026, 10, 26)).first().allItems
        assertEquals(listOf("Oat porridge"), monday.map { it.name })
        assertEquals(saved.rule.id, monday.single().repeatRuleId)
    }

    @Test
    fun anEntryAlreadyLoggedTodayCountsAsTodays() = runUnitTest {
        val logged = diary.addItems(today, MealSlot.Breakfast, listOf(porridge), LogMethod.Search).written().single()

        val saved = repeats.create(plan(listOf(porridge)), loggedToday = listOf(logged.id))

        assertFalse(saved.loggedToday)
        val items = diary.observeDay(today).first().allItems
        assertEquals(listOf(logged.id), items.map { it.id })
        assertEquals(saved.rule.id, items.single().repeatRuleId)
    }

    @Test
    fun anEntryFromAnotherDayDoesNotCountAsTodays() = runUnitTest {
        val yesterday = diary.addItems(LocalDate(2026, 10, 24), MealSlot.Breakfast, listOf(porridge), LogMethod.Search).written().single()

        val saved = repeats.create(plan(listOf(porridge)), loggedToday = listOf(yesterday.id))

        assertTrue(saved.loggedToday)
        assertNull(diary.observeDay(LocalDate(2026, 10, 24)).first().allItems.single().repeatRuleId)
    }

    @Test
    fun anEditStartsWithTheNextDay_andTodaysFoodsStay() = runUnitTest {
        val saved = repeats.create(plan(listOf(porridge)))

        val edited = repeats.update(saved.rule.id, plan(listOf(coffee)), paused = false)

        assertEquals(false, edited?.loggedToday)
        assertEquals(listOf("Oat porridge"), diary.observeDay(today).first().allItems.map { it.name })
        assertEquals(listOf(coffee), repeats.get(saved.rule.id)?.plan?.items)
    }

    @Test
    fun deletingARuleKeepsWhatItLogged_asOrdinaryEntries() = runUnitTest {
        val saved = repeats.create(plan(listOf(porridge)))

        repeats.delete(saved.rule.id)

        assertNull(repeats.get(saved.rule.id))
        assertTrue(repeats.observeAll().first().isEmpty())
        val entry = diary.observeDay(today).first().allItems.single()
        assertNull(entry.repeatRuleId)
        assertTrue(db.repeats.rows.single().deleted, "a tombstone, so the delete can sync once rules do")
    }

    @Test
    fun anEntryPulledFromAnotherPhoneIsKept_notWrittenAgain() = runUnitTest {
        val saved = repeats.create(plan(listOf(porridge), days = setOf(DayOfWeek.MONDAY)))
        val monday = LocalDate(2026, 10, 26)
        val id = repeatEntryId(saved.rule.id, monday, 0)
        val pulled = diary.addItems(monday, MealSlot.Breakfast, listOf(porridge), LogMethod.Copy).written().single()
            .copy(id = id, name = "Oat porridge, edited on the other phone")
        db.entries.put(id, pulled.toEntity(updatedAtEpochMs = 1))
        db.outbox.clear()
        calendar.today = monday

        phone.materializer.logDue()

        assertEquals("Oat porridge, edited on the other phone", db.entries.state.value.getValue(id).name)
        assertTrue(db.outbox.rows.isEmpty())
        assertEquals(saved.rule.id, diary.observeDay(monday).first().allItems.single { it.id == id }.repeatRuleId)
    }

    /** The cross-device guarantee: one rule on two phones is one set of rows on the server, which keeps one row per id. */
    @Test
    fun twoPhonesWithTheSameRuleWriteTheSameRows() = runUnitTest {
        val saved = repeats.create(plan(listOf(porridge, coffee)))
        val otherDb = FakeDiaryDb()
        otherDb.repeats.put(saved.rule.id, db.repeats.state.value.getValue(saved.rule.id))
        // The other phone opens later in the day: its rows must still match.
        val other = Phone(otherDb, FakeClock(Instant.parse("2026-10-25T13:10:00Z")), FakeCalendar(), FakeSettingsRepository()) { true }

        other.materializer.logDue()

        fun sent(of: FakeDiaryDb) = of.outbox.rows.map { it.entityId to it.payload }.toSet()
        assertEquals(2, sent(db).size)
        assertEquals(sent(db), sent(otherDb))
    }

    // Copy yesterday and copy to tomorrow, over a day a repeat logged

    @Test
    fun copyingADayOntoTheNext_leavesARepeatsFoodsToTheRepeat() = runUnitTest {
        val saved = repeats.create(plan(listOf(porridge)))
        diary.addItems(today, MealSlot.Lunch, listOf(coffee), LogMethod.Search)
        val tomorrow = LocalDate(2026, 10, 26)

        diary.copyDay(today, tomorrow)
        calendar.today = tomorrow
        clock.now = Instant.parse("2026-10-26T06:00:00Z")
        phone.materializer.logDue()

        val monday = diary.observeDay(tomorrow).first().allItems
        assertEquals(setOf("Oat porridge", "Coffee with milk"), monday.map { it.name }.toSet())
        assertEquals(2, monday.size, "one porridge, the repeat's")
        assertEquals(saved.rule.id, monday.single { it.name == "Oat porridge" }.repeatRuleId)
    }

    @Test
    fun copyYesterday_ontoADayTheRepeatAlreadyLogged_addsNoSecondCopy() = runUnitTest {
        repeats.create(plan(listOf(porridge)))
        val tomorrow = LocalDate(2026, 10, 26)
        calendar.today = tomorrow
        clock.now = Instant.parse("2026-10-26T06:00:00Z")
        phone.materializer.logDue()

        diary.copyDay(today, tomorrow)
        diary.copySlot(today, tomorrow, MealSlot.Breakfast)

        assertEquals(1, diary.observeDay(tomorrow).first().allItems.size)
    }

    @Test
    fun aRepeatThatSkipsTheDayCopiedOnto_isCopiedLikeAnyFood() = runUnitTest {
        repeats.create(plan(listOf(porridge), days = setOf(DayOfWeek.SUNDAY)))

        diary.copyDay(today, LocalDate(2026, 10, 26))

        assertEquals(listOf("Oat porridge"), diary.observeDay(LocalDate(2026, 10, 26)).first().allItems.map { it.name })
    }

    // Signing out, and who signs in next

    @Test
    fun signingOutAndBackIn_keepsTheRepeats() = runUnitTest {
        val saved = repeats.create(plan(listOf(porridge)))

        phone.user.user.value = ActiveUser.SignedOut
        phone.dump("user-a")
        assertTrue(repeats.observeAll().first().isEmpty(), "nobody signed in sees none")
        phone.user.user.value = ActiveUser.SignedIn("user-a")

        assertEquals(listOf(saved.rule.id), repeats.observeAll().first().map { it.id })
        assertNotNull(repeats.get(saved.rule.id))
    }

    @Test
    fun anotherAccountSigningIn_neitherSeesNorLogsThem_andTheyAreDeleted() = runUnitTest {
        val saved = repeats.create(plan(listOf(porridge)))
        phone.user.user.value = ActiveUser.SignedOut
        phone.dump("user-a")
        phone.user.user.value = ActiveUser.SignedIn("user-b")
        calendar.today = LocalDate(2026, 10, 26)
        clock.now = Instant.parse("2026-10-26T06:00:00Z")

        assertTrue(repeats.observeAll().first().isEmpty())
        assertNull(repeats.get(saved.rule.id))
        assertEquals(emptyMap(), phone.materializer.logDue())
        assertTrue(db.repeats.rows.isEmpty(), "gone from the phone, not just hidden")

        phone.user.user.value = ActiveUser.SignedIn("user-a")
        assertTrue(repeats.observeAll().first().isEmpty(), "and not back for the first account either")
    }

    @Test
    fun rulesFromBeforeAnOwner_areClaimedBySomeoneSignedIn_orGoAtAUserChange() = runUnitTest {
        val saved = repeats.create(plan(listOf(porridge)))
        val row = db.repeats.state.value.getValue(saved.rule.id)

        // A phone upgraded while signed in: its rules become that account's.
        val upgraded = Phone(FakeDiaryDb().also { it.repeats.put(row.id, row) }, clock, calendar, settings) { true }
        assertEquals(listOf(saved.rule.id), upgraded.repeats.observeAll().first().map { it.id })
        upgraded.dump("user-a")
        assertEquals(1, upgraded.repeats.observeAll().first().size)

        // Upgraded mid-expiry, and a user change before anyone signed in: whose they were is unknown.
        val held = FakeDiaryDb().also { it.repeats.put(row.id, row) }
        val expired = Phone(held, clock, calendar, settings, FakeActiveUser(ActiveUser.SignedOut)) { true }
        expired.dump("user-a")
        assertTrue(held.repeats.rows.isEmpty())
    }

    @Test
    fun foodsARepeatLogged_areNotShowingUp_forTheStreak_butAFoodLoggedByHandIs() = runUnitTest {
        repeats.create(plan(listOf(porridge)))
        val marks = DayMarksImpl(db.tables)
        assertTrue(marks.observe().first().isEmpty(), "a day only a repeat logged is not someone showing up")

        diary.addItems(today, MealSlot.Lunch, listOf(coffee), LogMethod.Search)
        assertEquals(listOf(DayMark(today, logged = true)), marks.observe().first())
    }

    private fun plan(
        items: List<LogDraft>,
        slot: MealSlot = MealSlot.Breakfast,
        days: Set<DayOfWeek> = RepeatDays.EveryDay,
        start: LocalDate = today,
        end: LocalDate? = null,
        savedMealId: String? = null,
        name: String = items.first().name,
    ) = RepeatPlan(name = name, items = items, slot = slot, days = days, startDay = start, endDay = end, savedMealId = savedMealId)

    /** One phone's diary, saved meals and repeats over [db], signed in as [user]. */
    private class Phone(
        private val db: FakeDiaryDb,
        clock: FakeClock,
        calendar: FakeCalendar,
        settings: FakeSettingsRepository,
        val user: FakeActiveUser = FakeActiveUser(),
        release: RepeatsRelease,
    ) {
        private val writer = DiaryWriter(db.tables, clock, FakeScheduler())
        val diary = DiaryRepositoryImpl(db.tables, writer, settings, calendar, FakeFastEnder())
        val savedMeals = SavedMealRepositoryImpl(db.tables, writer)
        val owner = RepeatOwner(db.tables, user, FakeCacheFactory())
        val materializer = RepeatMaterializer(db.tables, writer, settings, calendar, release, owner)
        val repeats = RepeatRepositoryImpl(db.tables, materializer, owner, calendar, clock)

        /** What a user change runs on this phone: every [ClearableDao] the tables hold, then the repeats' own clearer. */
        suspend fun dump(previousUserId: String) {
            val t = db.tables
            listOf<Any>(
                t.entries, t.days, t.feelings, t.weighIns, t.customFoods, t.savedMeals, t.fasts,
                t.exercises, t.outbox, t.syncState, t.repeats, t.repeatLogs,
            ).filterIsInstance<ClearableDao>().forEach { it.deleteAll() }
            owner.clear(previousUserId)
        }
    }
}
