package com.opson.features.today.impl

import com.opson.features.logger.LogTab
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.Fast
import com.opson.libraries.diary.FastingRelease
import com.opson.libraries.diary.FastingStatus
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MinuteTicker
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_toast_added_to
import opson.libraries.resources.generated.resources.today_toast_cleared
import opson.libraries.resources.generated.resources.today_toast_copied_previous
import opson.libraries.resources.generated.resources.today_toast_copied_tomorrow
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.reminders.RemindersEnabled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * [TodayViewModel], one test per action: what it writes, what it sends, and the outline on a
 * meal that just changed. Which cards exist is [TodayStateBuilderTest]'s.
 */
class TodayViewModelTest : CoroutineTest() {
    private val today = TodayFixtures.today
    private val yesterday = today.minus(DatePeriod(days = 1))
    private val tomorrow = today.plus(DatePeriod(days = 1))
    private val diary = FakeDiaryRepository(listOf(TodayFixtures.day(), TodayFixtures.yesterday()))
    private val settings = FakeSettingsRepository()
    private val policySource = FakePolicySource()
    private val selectedDay = FakeSelectedDay()
    private val recentLogEvents = FakeRecentLogEvents()
    private val clock = FakeClock()
    private val fasting = FakeFastingRepository()
    private var fastingReleased = true

    private fun vm(health: FakeHealthRepository = FakeHealthRepository(), healthOn: Boolean = false) = TodayViewModel(
        diary = diary,
        settingsRepository = settings,
        policySource = policySource,
        selectedDay = selectedDay,
        calendar = FakeCalendar(),
        recentLogEvents = recentLogEvents,
        clock = clock,
        health = health,
        healthFlag = healthFlag(healthOn),
        fasting = fasting,
        fastingRelease = FastingRelease { fastingReleased },
        ticker = MinuteTicker { flowOf(clock.now) },
        remindersEnabled = RemindersEnabled(
            object : AppConfigMap() {
                override val map: Map<String, *> = mapOf("release" to mapOf("reminders" to true))
            },
        ),
    )

    private fun TestScope.events(vm: TodayViewModel): List<TodayEvent> {
        val events = mutableListOf<TodayEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    @Test
    fun healthFlagOff_neverReadsEnergy_andShowsNoExpendedLine() = runUnitTest {
        val health = FakeHealthRepository(connected = true, energy = TodayFixtures.expended)
        val vm = vm(health, healthOn = false)

        assertTrue(health.energyDays.isEmpty())
        assertEquals(Display.Hidden, (vm.state.summary as TodaySummary.Energy).expended)
    }

    @Test
    fun healthFlagOn_readsTodaysEnergy_intoTheSummary() = runUnitTest {
        val health = FakeHealthRepository(connected = true, energy = TodayFixtures.expended)
        val vm = vm(health, healthOn = true)

        assertEquals(listOf(today), health.energyDays)
        assertEquals(Display.Shown("1,990 kcal"), (vm.state.summary as TodaySummary.Energy).expended)
    }

    @Test
    fun healthFlagOn_anotherDay_readsNothing() = runUnitTest {
        val health = FakeHealthRepository(connected = true, energy = TodayFixtures.expended)
        val vm = vm(health, healthOn = true)

        vm.takeAction(TodayAction.PreviousDayTapped)

        assertEquals(listOf(today), health.energyDays)
        assertEquals(Display.Hidden, (vm.state.summary as TodaySummary.Energy).expended)
    }

    @Test
    fun healthFlagOn_newEnergyArrives_andTheLineFollows() = runUnitTest {
        val health = FakeHealthRepository(connected = true, energy = TodayFixtures.expended)
        val vm = vm(health, healthOn = true)

        health.energyFlow.value = TodayFixtures.expended.copy(activeKcal = 500.0)

        assertEquals(Display.Shown("2,080 kcal"), (vm.state.summary as TodaySummary.Energy).expended)
    }

    @Test
    fun loadsTheSelectedDay() = runUnitTest {
        val vm = vm()

        assertEquals(false, vm.state.loading)
        assertEquals(today, vm.state.day)
    }

    @Test
    fun previousAndNextMoveTheSelectedDayUpToOneAhead() = runUnitTest {
        val vm = vm()

        vm.takeAction(TodayAction.PreviousDayTapped)
        assertEquals(yesterday, selectedDay.date.value)
        assertEquals(yesterday, vm.state.day)

        vm.takeAction(TodayAction.NextDayTapped)
        vm.takeAction(TodayAction.NextDayTapped)
        assertEquals(tomorrow, selectedDay.date.value)
        vm.takeAction(TodayAction.NextDayTapped)
        assertEquals(tomorrow, selectedDay.date.value, "planning goes one day ahead, no further")
    }

    @Test
    fun theDayLabelAndCalendarOpenTheCalendar() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(TodayAction.DayLabelTapped)
        vm.takeAction(TodayAction.CalendarTapped)

        assertEquals(listOf<TodayEvent>(TodayEvent.OpenCalendar(today), TodayEvent.OpenCalendar(today)), events)
    }

    @Test
    fun nutrientsOpensThisDaysNutrients() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(TodayAction.NutrientsTapped)

        assertEquals(listOf<TodayEvent>(TodayEvent.OpenNutrients(today)), events)
    }

    @Test
    fun closeTheDayOpensDayCloseButNotOnAPlannedDay() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(TodayAction.CloseDayCardTapped)
        vm.takeAction(TodayAction.RevealedTapped)
        selectedDay.select(tomorrow)
        vm.takeAction(TodayAction.CloseDayCardTapped)

        assertEquals(listOf<TodayEvent>(TodayEvent.OpenDayClose(today), TodayEvent.OpenDayClose(today)), events)
    }

    @Test
    fun mealTapsOpenTheLoggerOrTheMealSheet() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(TodayAction.MealTileTapped(MealSlot.Lunch))
        vm.takeAction(TodayAction.RhythmLogTapped(MealSlot.Dinner))
        vm.takeAction(TodayAction.PlanItTapped(MealSlot.Snack))
        vm.takeAction(TodayAction.MealPlusTapped(MealSlot.Breakfast))
        vm.takeAction(TodayAction.SavedMealsTapped(MealSlot.Dinner))
        vm.takeAction(TodayAction.FeelPromptTapped(MealSlot.Snack))

        assertEquals(
            listOf(
                TodayEvent.OpenLogger(today, MealSlot.Lunch),
                TodayEvent.OpenLogger(today, MealSlot.Dinner),
                TodayEvent.OpenLogger(today, MealSlot.Snack),
                TodayEvent.OpenMealSheet(today, MealSlot.Breakfast),
                TodayEvent.OpenLog(today, MealSlot.Dinner, LogTab.Saved),
                TodayEvent.OpenMealFeel(today, MealSlot.Snack),
            ),
            events,
        )
    }

    @Test
    fun copyingYesterdaysMealWritesThatSlot() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(TodayAction.CopyYesterdayMealTapped(MealSlot.Dinner))

        assertEquals(listOf(Triple(yesterday, today, MealSlot.Dinner)), diary.copiedSlots)
        val toast = (events.single() as TodayEvent.ShowToast).toast
        assertEquals(Res.string.today_toast_added_to, toast.message)
    }

    @Test
    fun foodLandingOnTheDayOutlinesItsMealForAMoment() = runUnitTest {
        val vm = vm()
        assertNull(vm.state.highlight)

        diary.days.value = diary.days.value + (today to TodayFixtures.day(dinner = true))
        assertEquals(MealSlot.Dinner, vm.state.highlight)

        advanceTimeBy(TodayViewModel.HIGHLIGHT_FOR.inWholeMilliseconds + 1)
        runCurrent()
        assertNull(vm.state.highlight)
    }

    @Test
    fun anAddFromTheLoggerOffersUndoForWhatIsLeftOfItsSixSeconds() = runUnitTest {
        val vm = vm()
        val event = TodayFixtures.added()
        clock.now = event.at + 2.seconds

        recentLogEvents.logged(event)

        assertEquals(AddedNotice(MealSlot.Dinner, count = 2), vm.state.added)
        assertEquals(MealSlot.Dinner, vm.state.highlight, "scrolled to and outlined")
        assertEquals(listOf(event), recentLogEvents.consumed, "shown once")
        assertNull(recentLogEvents.latest.value)

        advanceTimeBy(4.seconds.inWholeMilliseconds - 1)
        runCurrent()
        assertNotNull(vm.state.added, "four seconds were left, not six")
        advanceTimeBy(2)
        runCurrent()
        assertNull(vm.state.added)
    }

    @Test
    fun undoRemovesExactlyWhatTheLoggerAdded() = runUnitTest {
        val vm = vm()
        val event = TodayFixtures.added()
        clock.now = event.at
        recentLogEvents.logged(event)

        vm.takeAction(TodayAction.UndoTapped)

        assertEquals(listOf(event), recentLogEvents.undone)
        assertNull(vm.state.added)
        vm.takeAction(TodayAction.UndoTapped)
        assertEquals(1, recentLogEvents.undone.size, "a second tap has nothing left to undo")
    }

    @Test
    fun anAddWhoseWindowClosedShowsNothing() = runUnitTest {
        val vm = vm()
        val event = TodayFixtures.added()
        clock.now = event.at + LoggedToMeal.UNDO_WINDOW

        recentLogEvents.logged(event)

        assertNull(vm.state.added)
        assertEquals(listOf(event), recentLogEvents.consumed)
    }

    @Test
    fun anAddForAnotherDayWaitsForThatDay() = runUnitTest {
        val vm = vm()
        val event = TodayFixtures.added().copy(day = yesterday)
        clock.now = event.at

        recentLogEvents.logged(event)
        assertNull(vm.state.added)
        assertTrue(recentLogEvents.consumed.isEmpty())

        vm.takeAction(TodayAction.PreviousDayTapped)
        assertEquals(AddedNotice(MealSlot.Dinner, count = 2), vm.state.added)
    }

    @Test
    fun goingToAnotherDayDropsTheUndoBar() = runUnitTest {
        val vm = vm()
        val event = TodayFixtures.added()
        clock.now = event.at
        recentLogEvents.logged(event)

        vm.takeAction(TodayAction.PreviousDayTapped)

        assertNull(vm.state.added)
        assertTrue(recentLogEvents.undone.isEmpty())
    }

    @Test
    fun markAsUntrackedTogglesTheDayAndTrackItAgainUndoesIt() = runUnitTest {
        val vm = vm()

        vm.takeAction(TodayAction.DayMenuTapped)
        vm.takeAction(TodayAction.UntrackedMenuTapped)
        assertNull(vm.state.menu)
        assertEquals(listOf(today to true), diary.untracked)

        diary.days.value = diary.days.value + (today to TodayFixtures.untrackedDay())
        assertTrue(vm.state.untracked)
        vm.takeAction(TodayAction.UntrackedMenuTapped)
        vm.takeAction(TodayAction.TrackAgainTapped)
        assertEquals(listOf(today to true, today to false, today to false), diary.untracked)
    }

    @Test
    fun iTriedItOpensTheLoggerWithTheBridgeFoodAndPutsTheCardAway() = runUnitTest {
        explorerWithABridge()
        val vm = vm()
        val events = events(vm)
        assertEquals(FoodBridgeCard(from = "jasmine rice", to = "rice pudding", shared = "rice"), vm.state.bridge)

        vm.takeAction(TodayAction.BridgeTriedTapped)

        assertEquals(listOf<TodayEvent>(TodayEvent.OpenLoggerWithFood(today, "pudding", isCustom = false)), events)
        assertNull(vm.state.bridge)
    }

    @Test
    fun notTodayPutsTheCardAwayAndRecordsNothing() = runUnitTest {
        explorerWithABridge()
        val vm = vm()

        vm.takeAction(TodayAction.BridgeNotTodayTapped)

        assertNull(vm.state.bridge)
        assertEquals(0, diary.writes)
        vm.takeAction(TodayAction.NextDayTapped)
        vm.takeAction(TodayAction.PreviousDayTapped)
        assertNull(vm.state.bridge, "still today, still put away")
    }

    /** Jasmine rice most days, rice pudding once last week. */
    private fun explorerWithABridge() {
        val rice = TodayFixtures.day().items(MealSlot.Lunch).last()
        val pudding = rice.copy(id = "pudding-1", name = "Rice pudding", foodId = "pudding", day = today.minus(DatePeriod(days = 4)))
        diary.frequent.value = listOf(rice)
        diary.days.value = mapOf(today to TodayFixtures.day(), pudding.day to Day(pudding.day, itemsBySlot = mapOf(MealSlot.Snack to listOf(pudding))))
        policySource.policy.value = policy(PresetId.Explorer)
    }

    @Test
    fun movingToAnotherDayIsNotAnAddition() = runUnitTest {
        val vm = vm()

        vm.takeAction(TodayAction.PreviousDayTapped)

        assertNull(vm.state.highlight)
    }

    @Test
    fun itemAndWaterTapsOpenTheirSheets() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(TodayAction.ItemTapped("adobo-$today"))
        vm.takeAction(TodayAction.WaterPlusTapped)

        assertEquals(listOf(TodayEvent.OpenItemSheet(today, "adobo-$today"), TodayEvent.OpenWaterSheet(today)), events)
    }

    @Test
    fun alreadyAteMarksTheMeal() = runUnitTest {
        val vm = vm()

        vm.takeAction(TodayAction.AlreadyAteTapped(MealSlot.Dinner))

        assertEquals(listOf(today to MealSlot.Dinner), diary.ateNoDetails)
    }

    @Test
    fun theRhythmReminderLineOpensReminders_andTheFlagReachesTheState() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(TodayAction.RemindersTapped)

        assertEquals(listOf<TodayEvent>(TodayEvent.OpenReminders), events)
    }

    @Test
    fun theMenuOpensAndDismisses() = runUnitTest {
        val vm = vm()

        vm.takeAction(TodayAction.DayMenuTapped)
        assertNotNull(vm.state.menu)

        vm.takeAction(TodayAction.MenuDismissed)
        assertNull(vm.state.menu)
    }

    @Test
    fun clearingTakesTwoTapsAndADismissDisarms() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(TodayAction.DayMenuTapped)
        vm.takeAction(TodayAction.ClearDayTapped)
        assertEquals(true, vm.state.menu?.clearArmed)
        assertTrue(diary.cleared.isEmpty())

        vm.takeAction(TodayAction.MenuDismissed)
        vm.takeAction(TodayAction.DayMenuTapped)
        assertEquals(false, vm.state.menu?.clearArmed)

        vm.takeAction(TodayAction.ClearDayTapped)
        vm.takeAction(TodayAction.ClearDayTapped)
        assertEquals(listOf(today), diary.cleared)
        assertNull(vm.state.menu)
        assertEquals(Res.string.today_toast_cleared, (events.single() as TodayEvent.ShowToast).toast.message)
    }

    @Test
    fun copyYesterdayAndCopyToTomorrowAppendWholeDays() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(TodayAction.DayMenuTapped)
        vm.takeAction(TodayAction.CopyYesterdayTapped)
        vm.takeAction(TodayAction.DayMenuTapped)
        vm.takeAction(TodayAction.CopyToTomorrowTapped)

        assertEquals(listOf(yesterday to today, today to tomorrow), diary.copiedDays)
        assertEquals(
            listOf(Res.string.today_toast_copied_previous, Res.string.today_toast_copied_tomorrow),
            events.map { (it as TodayEvent.ShowToast).toast.message },
        )
        assertNull(vm.state.menu)
    }

    @Test
    fun aPlannedDayCannotBeCopiedFurtherAhead() = runUnitTest {
        selectedDay.select(tomorrow)
        val vm = vm()

        vm.takeAction(TodayAction.CopyToTomorrowTapped)

        assertTrue(diary.copiedDays.isEmpty())
    }

    @Test
    fun menuItemsOpenTheirScreens() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(TodayAction.DayCloseMenuTapped)
        vm.takeAction(TodayAction.DayNutrientsTapped)
        vm.takeAction(TodayAction.DayNoteTapped)
        vm.takeAction(TodayAction.EditCardsTapped)

        assertEquals(
            listOf(
                TodayEvent.OpenDayClose(today),
                TodayEvent.OpenNutrients(today),
                TodayEvent.OpenDayNote(today),
                TodayEvent.OpenTodayCards,
            ),
            events,
        )
    }

    @Test
    fun aNewPolicyRebuildsTheCards() = runUnitTest {
        val vm = vm()

        policySource.policy.value = policy(PresetId.Blind)

        assertTrue(vm.state.summary is TodaySummary.Counted)
    }

    @Test
    fun explorerCollectsFoodsOnlyInItsLayout() = runUnitTest {
        diary.frequent.value = TodayFixtures.day().allItems.take(2)
        policySource.policy.value = policy(PresetId.Explorer)
        settings.state.value = Settings()
        val vm = vm()

        assertEquals(listOf("Oat porridge with milk", "Banana"), vm.state.safeFoods)
    }

    @Test
    fun anEmptyDayLoadsToo() = runUnitTest {
        diary.days.value = emptyMap<kotlinx.datetime.LocalDate, Day>()
        val vm = vm()

        assertTrue(vm.state.meals.all { it is MealEntry.Collapsed })
    }

    // --- Fasting (0.2) ---

    @Test
    fun noFastingCardWhileTheTimerIsOff() = runUnitTest {
        val vm = vm()
        assertNull(vm.state.fasting)
        assertEquals(0, fasting.startDueCalls)
    }

    @Test
    fun theFastingCardShowsTodayWhileTheTimerIsOn_andStartsADueFast() = runUnitTest {
        settings.state.value = Settings(fasting = true)
        val vm = vm()

        assertEquals(FastingStatus.WindowOpen(until = LocalTime(19, 0)), vm.state.fasting)
        assertEquals(1, fasting.startDueCalls)

        vm.takeAction(TodayAction.PreviousDayTapped)
        assertNull(vm.state.fasting, "only on today")
    }

    @Test
    fun theFastingCardReadsTheStoredStart() = runUnitTest {
        settings.state.value = Settings(fasting = true)
        clock.now = Instant.parse("2026-10-25T21:30:00Z")
        fasting.latest.value = Fast(today, Instant.parse("2026-10-25T19:00:00Z"), goalHours = 14)
        val vm = vm()

        assertEquals(FastingStatus.Running(since = LocalTime(19, 0), elapsed = 150.minutes), vm.state.fasting)
    }

    @Test
    fun theFastingCardNeverShowsAfterTheStatementOrOutsideFullAndSprint() = runUnitTest {
        PresetId.entries.forEach { preset ->
            listOf(false, true).forEach { hard ->
                settings.state.value = Settings(
                    numbers = NumbersSettings(preset),
                    fasting = true,
                    onboarding = OnboardingAnswers(hard = hard),
                )
                val shows = !hard && (preset == PresetId.Full || preset == PresetId.Sprint)
                assertEquals(shows, vm().state.fasting != null, "$preset, hard=$hard")
            }
        }
    }

    @Test
    fun theFastingCardStaysOffWithTheFlagOffOrTheCardSwitchedOff() = runUnitTest {
        fastingReleased = false
        settings.state.value = Settings(fasting = true)
        assertNull(vm().state.fasting)

        fastingReleased = true
        settings.state.value = Settings(fasting = true, fastToday = false)
        assertNull(vm().state.fasting)
    }

    @Test
    fun theFastingCardOpensFasting_andEndFastEndsIt() = runUnitTest {
        settings.state.value = Settings(fasting = true)
        val vm = vm()
        val events = events(vm)

        vm.takeAction(TodayAction.FastingCardTapped)
        vm.takeAction(TodayAction.EndFastTapped)

        assertEquals(listOf<TodayEvent>(TodayEvent.OpenFasting), events)
        assertEquals(1, fasting.endCalls)
    }
}
