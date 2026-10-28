package com.opson.features.today.impl

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
import com.opson.libraries.nutrition.NumbersChange
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
import opson.libraries.resources.generated.resources.today_toast_cleared
import opson.libraries.resources.generated.resources.today_toast_copied_foods_other
import opson.libraries.resources.generated.resources.today_toast_copied_previous
import opson.libraries.resources.generated.resources.today_toast_copied_tomorrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.EnergyExpended
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * [TodayViewModel], one test per action: what it writes, what it sends, and the outline on a
 * meal that just changed. Which cards exist is [TodayStateBuilderTest]'s.
 */
@OptIn(ExperimentalCoroutinesApi::class)
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

    private val weights = FakeWeightRepository()
    private val cardView = TodayCardViewStore(FakeCacheFactory())

    private fun vm(
        health: FakeHealthRepository = FakeHealthRepository(),
        healthOn: Boolean = false,
        summaryOn: Boolean = false,
        selectOn: Boolean = false,
        foodActionsOn: Boolean = false,
        healthRepository: HealthRepository = health,
        goalsAndEnergyOn: Boolean = false,
        burnedRepository: FakeBurnedRepository = FakeBurnedRepository(),
    ) = TodayViewModel(
        diary = diary,
        settingsRepository = settings,
        policySource = policySource,
        selectedDay = selectedDay,
        calendar = FakeCalendar(),
        recentLogEvents = recentLogEvents,
        clock = clock,
        health = healthRepository,
        healthFlag = healthFlag(healthOn),
        fasting = fasting,
        fastingRelease = FastingRelease { fastingReleased },
        ticker = MinuteTicker { flowOf(clock.now) },
        editor = diary,
        weights = weights,
        summaryEnabled = summaryFlag(summaryOn),
        selectEnabled = selectFlag(selectOn),
        foodActionsEnabled = foodActionsFlag(foodActionsOn),
        goalsAndEnergy = goalsAndEnergyFlag(goalsAndEnergyOn),
        burnedRepository = burnedRepository,
        cardView = cardView,
    )

    private fun TestScope.events(vm: TodayViewModel): List<TodayEvent> {
        val events = mutableListOf<TodayEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    /** The day waits for the health store's first reading; until then the list is the spinner, not a blank page. */
    @Test
    fun healthStoreStillAnswering_keepsTodayLoading_untilItDoes() = runUnitTest {
        val health = FakeHealthRepository(connected = true, energy = TodayFixtures.expended)
        val gate = MutableStateFlow(false)
        val slow = object : HealthRepository by health {
            override fun energy(day: LocalDate, timeZone: TimeZone): Flow<EnergyExpended?> =
                gate.filter { it }.flatMapLatest { health.energy(day, timeZone) }
        }
        val vm = vm(healthOn = true, healthRepository = slow)

        assertTrue(vm.state.loading)
        assertEquals(listOf<TodayRow>(TodayRow.Loading), todayRows(vm.state))

        gate.value = true
        assertFalse(vm.state.loading)
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
    fun healthFlagOn_exerciseInTheRange_movesTodaysRange() = runUnitTest {
        settings.state.value = TodayFixtures.exerciseAll
        val vm = vm(FakeHealthRepository(connected = true, energy = TodayFixtures.expended), healthOn = true)

        assertEquals("2,310 to 2,710", (vm.state.summary as TodaySummary.Energy).range.text())
    }

    @Test
    fun healthFlagOff_exerciseInTheRange_keepsTheBase() = runUnitTest {
        settings.state.value = TodayFixtures.exerciseAll
        val vm = vm(FakeHealthRepository(connected = true, energy = TodayFixtures.expended), healthOn = false)

        assertEquals("1,900 to 2,300", (vm.state.summary as TodaySummary.Energy).range.text())
    }

    @Test
    fun notConnected_exerciseInTheRange_keepsTheBase() = runUnitTest {
        settings.state.value = TodayFixtures.exerciseAll
        val vm = vm(FakeHealthRepository(connected = false, energy = TodayFixtures.expended), healthOn = true)

        assertEquals("1,900 to 2,300", (vm.state.summary as TodaySummary.Energy).range.text())
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
    fun theDaysTitleOpensTheCalendar() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(TodayAction.DayLabelTapped)

        assertEquals(listOf<TodayEvent>(TodayEvent.OpenCalendar(today)), events)
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

        vm.takeAction(TodayAction.CloseDayButtonTapped)
        selectedDay.select(tomorrow)
        vm.takeAction(TodayAction.CloseDayButtonTapped)

        assertEquals(listOf<TodayEvent>(TodayEvent.OpenDayClose(today)), events)
    }

    @Test
    fun mealTapsOpenTheLoggerOrTheMealSheet() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(TodayAction.MealTileTapped(MealSlot.Lunch))
        vm.takeAction(TodayAction.PlanItTapped(MealSlot.Snack))
        vm.takeAction(TodayAction.MealPlusTapped(MealSlot.Breakfast))

        assertEquals(
            listOf(
                TodayEvent.OpenLogger(today, MealSlot.Lunch),
                TodayEvent.OpenLogger(today, MealSlot.Snack),
                TodayEvent.OpenMealSheet(today, MealSlot.Breakfast),
            ),
            events,
        )
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
    fun theTimerBannersSwitch_opensThePauseForIt() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(TodayAction.TimedSwitchTapped(PresetId.Full))

        assertEquals(listOf<TodayEvent>(TodayEvent.OpenPause(NumbersChange.PickPreset(PresetId.Full))), events)
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
        vm.takeAction(TodayAction.NotesMenuTapped)
        vm.takeAction(TodayAction.DayNoteTapped)
        vm.takeAction(TodayAction.EditCardsTapped)

        assertEquals(
            listOf(
                TodayEvent.OpenDayClose(today),
                TodayEvent.OpenNutrients(today),
                TodayEvent.OpenNotes,
                TodayEvent.OpenDayNote(today),
                TodayEvent.OpenTodayCards,
            ),
            events,
        )
    }

    @Test
    fun aNewPolicyRebuildsTheCards() = runUnitTest {
        val vm = vm()

        policySource.policy.value = policy(PresetId.NoNumbers)

        assertNull(vm.state.summary, "No numbers' meal timeline stands in for the card")
        assertNotNull(vm.state.mealTiles)
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
                val shows = !hard && (preset == PresetId.Full || preset == PresetId.Full)
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

    // Design sync 2: the summary card (release.todaySummary)

    @Test
    fun summaryFlagOff_weighInsAreNeverRead() = runUnitTest {
        vm(summaryOn = false)
        assertEquals(0, weights.reads)

        vm(summaryOn = true)
        assertEquals(1, weights.reads)
    }

    @Test
    fun thePageLeftOpenAndTheCollapseAreSavedForNextTime() = runUnitTest {
        val vm = vm(summaryOn = true)

        vm.takeAction(TodayAction.SummaryPageShown(1))
        assertEquals(1, vm.state.summaryPage)
        assertEquals(1, vm(summaryOn = true).state.summaryPage, "the next Today opens on it")

        vm.takeAction(TodayAction.CollapseTapped)
        assertTrue(vm.state.collapsed)

        vm.takeAction(TodayAction.ExpandTapped)
        assertFalse(vm.state.collapsed)
    }

    /**
     * Swiping the card or collapsing it is view state. Saved in Settings, each swipe queued the whole
     * settings object, and a phone that had not pulled yet pushed back the mode another phone had
     * just left.
     */
    @Test
    fun swipingOrCollapsingTheCard_neverWritesSettings() = runUnitTest {
        val vm = vm(summaryOn = true)
        val before = settings.state.value

        vm.takeAction(TodayAction.SummaryPageShown(1))
        vm.takeAction(TodayAction.CollapseTapped)
        vm.takeAction(TodayAction.ExpandTapped)

        assertEquals(before, settings.state.value)
    }

    /** A choice made before the card's view moved to the phone still holds until it is changed here. */
    @Test
    fun aPageSavedInSettingsBeforeTheMove_stillOpens() = runUnitTest {
        settings.state.value = Settings(todaySummaryPage = 1, todaySummaryCollapsed = true)
        val vm = vm(summaryOn = true)
        assertEquals(1, vm.state.summaryPage)
        assertTrue(vm.state.collapsed)

        vm.takeAction(TodayAction.SummaryPageShown(0))
        vm.takeAction(TodayAction.ExpandTapped)

        assertEquals(0, vm.state.summaryPage)
        assertFalse(vm.state.collapsed)
    }

    /** The card's view is the signed-in person's. After a user change the next account opens on its own choice. */
    @Test
    fun aUserChange_dropsTheCardViewLeftByThePreviousAccount() = runUnitTest {
        val vm = vm(summaryOn = true)
        vm.takeAction(TodayAction.SummaryPageShown(1))
        vm.takeAction(TodayAction.CollapseTapped)

        cardView.clear(previousUserId = "previous")
        settings.state.value = Settings(todaySummaryPage = 0, todaySummaryCollapsed = false)

        assertEquals(0, vm.state.summaryPage)
        assertFalse(vm.state.collapsed)
        assertEquals(0, vm(summaryOn = true).state.summaryPage, "a fresh Today opens on the new account's page")
    }

    @Test
    fun aLongPressOnTheCardOpensTheCardsSheet_onlyWithTheFlag() = runUnitTest {
        val off = vm(summaryOn = false)
        val offEvents = events(off)
        off.takeAction(TodayAction.SummaryLongPressed)
        assertTrue(offEvents.isEmpty())

        val on = vm(summaryOn = true)
        val events = events(on)
        on.takeAction(TodayAction.SummaryLongPressed)
        on.takeAction(TodayAction.WeightTapped)
        assertEquals(listOf(TodayEvent.OpenTodayCards, TodayEvent.OpenWeight), events)
    }

    // Select foods, drag them, swipe to delete (release.todaySelect), and a food's ⋮ (release.foodActions)

    private val oats = "oats-$today"
    private val banana = "banana-$today"
    private val coffee = "coffee-$today"
    private val adobo = "adobo-$today"

    @Test
    fun selectFlagOff_aLongPressSelectsNothing() = runUnitTest {
        val vm = vm(selectOn = false)

        vm.takeAction(TodayAction.FoodLongPressed(oats))
        vm.takeAction(TodayAction.RowSwipedOpen(oats))

        assertNull(vm.state.selection)
        assertNull(vm.state.swipe.open)
    }

    @Test
    fun aLongPressStartsSelecting_thenFoodsAndMealHeadersToggle() = runUnitTest {
        val vm = vm(selectOn = true)

        vm.takeAction(TodayAction.FoodLongPressed(oats))
        assertEquals(setOf(oats), vm.state.selection?.ids)

        vm.takeAction(TodayAction.FoodToggled(adobo))
        vm.takeAction(TodayAction.FoodToggled(oats))
        assertEquals(setOf(adobo), vm.state.selection?.ids)

        vm.takeAction(TodayAction.MealHeaderToggled(MealSlot.Breakfast))
        assertEquals(setOf(adobo, oats, banana, coffee), vm.state.selection?.ids)
        vm.takeAction(TodayAction.MealHeaderToggled(MealSlot.Breakfast))
        assertEquals(setOf(adobo), vm.state.selection?.ids, "a meal already picked whole is unpicked")

        vm.takeAction(TodayAction.SelectAllTapped)
        assertEquals(TodayFixtures.day().allItems.size, vm.state.selection?.count)

        vm.takeAction(TodayAction.SelectionClosed)
        assertNull(vm.state.selection)
    }

    @Test
    fun copyToADayAndMeal_copiesThePicks() = runUnitTest {
        val vm = vm(selectOn = true)
        val events = events(vm)
        vm.takeAction(TodayAction.FoodLongPressed(oats))
        vm.takeAction(TodayAction.FoodToggled(banana))

        vm.takeAction(TodayAction.CopyToTapped)
        assertEquals(SelectStep.CopyTo(CopyDay.Today, MealSlot.Breakfast), vm.state.selection?.step, "starts on the first pick's meal")
        vm.takeAction(TodayAction.CopyDayPicked(CopyDay.Tomorrow))
        vm.takeAction(TodayAction.CopySlotPicked(MealSlot.Snack))
        vm.takeAction(TodayAction.CopyConfirmed)

        assertEquals(listOf(Triple(listOf(oats, banana), tomorrow, MealSlot.Snack)), diary.copiedItems)
        assertNull(vm.state.selection)
        val toast = (events.single() as TodayEvent.ShowToast).toast
        assertEquals(Res.string.today_toast_copied_foods_other, toast.message)
    }

    @Test
    fun moveFromTheBottomBar_movesThePicks_keepsTheirTimes_andOffersUndo() = runUnitTest {
        val vm = vm(selectOn = true)
        val events = events(vm)
        vm.takeAction(TodayAction.FoodLongPressed(adobo))

        vm.takeAction(TodayAction.MoveTapped)
        vm.takeAction(TodayAction.MoveConfirmed)
        assertTrue(diary.moved.isEmpty(), "nothing picked to move to yet")

        vm.takeAction(TodayAction.MoveSlotPicked(MealSlot.Dinner))
        vm.takeAction(TodayAction.MoveConfirmed)
        assertEquals(listOf(listOf(adobo) to MealSlot.Dinner), diary.moved)
        assertTrue(diary.updated.isEmpty(), "a move never rewrites the entry, so its time stays")
        assertNull(vm.state.selection)
        assertEquals(MovedNotice(1, "Chicken adobo", MealSlot.Dinner), vm.state.moved)
        assertTrue(events.isEmpty(), "the Undo bar says it, not a toast")
    }

    @Test
    fun aDrop_movesEveryPick_andUndoPutsEachBackInItsOwnMeal() = runUnitTest {
        val vm = vm(selectOn = true)
        vm.takeAction(TodayAction.FoodLongPressed(oats))
        vm.takeAction(TodayAction.FoodToggled(adobo))

        vm.takeAction(TodayAction.FoodsDropped(MealSlot.Dinner))
        assertEquals(listOf(listOf(oats, adobo) to MealSlot.Dinner), diary.moved)
        assertEquals(MovedNotice(2, null, MealSlot.Dinner), vm.state.moved)
        assertNull(vm.state.selection)

        vm.takeAction(TodayAction.MovedUndoTapped)
        assertEquals(
            listOf(listOf(oats, adobo) to MealSlot.Dinner, listOf(oats) to MealSlot.Breakfast, listOf(adobo) to MealSlot.Lunch),
            diary.moved,
        )
        assertNull(vm.state.moved)
    }

    @Test
    fun aDrop_leavesFoodsAlreadyInThatMeal_andOnTheirOwnMealMovesNothing() = runUnitTest {
        val vm = vm(selectOn = true)
        vm.takeAction(TodayAction.FoodLongPressed(oats))
        vm.takeAction(TodayAction.FoodToggled(banana))

        vm.takeAction(TodayAction.FoodsDropped(MealSlot.Breakfast))
        assertTrue(diary.moved.isEmpty())
        assertEquals(setOf(oats, banana), vm.state.selection?.ids, "still selecting")

        vm.takeAction(TodayAction.FoodToggled(adobo))
        vm.takeAction(TodayAction.FoodsDropped(MealSlot.Lunch))
        assertEquals(listOf(listOf(oats, banana) to MealSlot.Lunch), diary.moved, "the adobo is already at lunch")
    }

    @Test
    fun theMovesUndo_closesAfterSixSeconds() = runUnitTest {
        val vm = vm(selectOn = true)
        vm.takeAction(TodayAction.FoodLongPressed(adobo))
        vm.takeAction(TodayAction.FoodsDropped(MealSlot.Snack))

        advanceTimeBy(LoggedToMeal.UNDO_WINDOW.inWholeMilliseconds + 1)
        runCurrent()
        assertNull(vm.state.moved)
        assertEquals(1, diary.moved.size, "nothing moved back")
    }

    @Test
    fun grabbingAnUnpickedRowsHandle_picksIt() = runUnitTest {
        val vm = vm(selectOn = true)
        vm.takeAction(TodayAction.FoodLongPressed(oats))

        vm.takeAction(TodayAction.DragStarted(adobo))
        assertEquals(setOf(oats, adobo), vm.state.selection?.ids)

        vm.takeAction(TodayAction.DragStarted(oats))
        assertEquals(setOf(oats, adobo), vm.state.selection?.ids, "a picked row stays picked")
    }

    @Test
    fun aRowsMoveAction_picksItAndOpensMove() = runUnitTest {
        val vm = vm(selectOn = true)
        vm.takeAction(TodayAction.FoodLongPressed(oats))

        vm.takeAction(TodayAction.MoveRowRequested(adobo))

        assertEquals(setOf(oats, adobo), vm.state.selection?.ids)
        assertEquals(SelectStep.MoveTo(), vm.state.selection?.step)
    }

    @Test
    fun selectFlagOff_aDropMovesNothing() = runUnitTest {
        val vm = vm(selectOn = false)

        vm.takeAction(TodayAction.FoodLongPressed(oats))
        vm.takeAction(TodayAction.FoodsDropped(MealSlot.Dinner))

        assertTrue(diary.moved.isEmpty())
        assertNull(vm.state.moved)
    }

    @Test
    fun aFoodsMenu_opensItsActions_onlyWithTheFoodActionsFlag() = runUnitTest {
        val off = vm()
        val offEvents = events(off)
        off.takeAction(TodayAction.ItemMenuTapped(banana))
        assertTrue(offEvents.isEmpty())

        val on = vm(foodActionsOn = true)
        val onEvents = events(on)
        on.takeAction(TodayAction.ItemMenuTapped(banana))
        assertEquals(listOf<TodayEvent>(TodayEvent.OpenItemSheet(today, banana)), onEvents)
    }

    @Test
    fun moveToOnUncategorized_picksItsFoodsAndOpensMove() = runUnitTest {
        val vm = vm(selectOn = true)

        vm.takeAction(TodayAction.MoveUncategorizedTapped(listOf(oats, banana)))

        assertEquals(setOf(oats, banana), vm.state.selection?.ids)
        assertEquals(SelectStep.MoveTo(), vm.state.selection?.step)
    }

    @Test
    fun removingThePicksAsksOnce_thenOffersUndoForSixSeconds() = runUnitTest {
        val vm = vm(selectOn = true)
        vm.takeAction(TodayAction.FoodLongPressed(oats))
        vm.takeAction(TodayAction.FoodToggled(banana))

        vm.takeAction(TodayAction.RemoveSelectedTapped)
        assertEquals(SelectStep.ConfirmRemove, vm.state.selection?.step)
        assertTrue(diary.removed.isEmpty(), "nothing goes until Remove")
        vm.takeAction(TodayAction.SelectStepBack)
        assertEquals(SelectStep.Actions, vm.state.selection?.step, "Keep goes back")

        vm.takeAction(TodayAction.RemoveSelectedTapped)
        vm.takeAction(TodayAction.RemoveSelectedConfirmed)
        assertEquals(listOf(oats, banana), diary.removed)
        assertNull(vm.state.selection)
        assertEquals(RemovedNotice(2, null, null), vm.state.removed)

        advanceTimeBy(LoggedToMeal.UNDO_WINDOW.inWholeMilliseconds + 1)
        runCurrent()
        assertNull(vm.state.removed)
        assertTrue(diary.restored.isEmpty())
    }

    @Test
    fun swipingOpensOneRowAtATime_andScrollingClosesIt() = runUnitTest {
        val vm = vm(selectOn = true)

        vm.takeAction(TodayAction.RowSwipedOpen(oats))
        vm.takeAction(TodayAction.RowSwipedOpen(banana))
        assertEquals(SwipeState(open = banana), vm.state.swipe)

        vm.takeAction(TodayAction.ListScrolled)
        assertEquals(SwipeState(), vm.state.swipe)

        vm.takeAction(TodayAction.RowSwipedOpen(banana))
        vm.takeAction(TodayAction.RowClosed)
        assertEquals(SwipeState(), vm.state.swipe)
    }

    @Test
    fun aTapOnAnOpenRowClosesIt_ratherThanOpeningTheItem() = runUnitTest {
        val vm = vm(selectOn = true)
        val events = events(vm)

        vm.takeAction(TodayAction.RowSwipedOpen(banana))
        vm.takeAction(TodayAction.ItemTapped(banana))
        assertEquals(SwipeState(), vm.state.swipe)
        assertTrue(events.isEmpty())

        vm.takeAction(TodayAction.ItemTapped(banana))
        assertEquals(listOf<TodayEvent>(TodayEvent.OpenItemSheet(today, banana)), events)
    }

    @Test
    fun deleteAsksInPlace_keepPutsItBack_removeTakesItWithUndo() = runUnitTest {
        val vm = vm(selectOn = true)

        vm.takeAction(TodayAction.DeleteRequested(banana))
        assertEquals(SwipeState(confirming = banana), vm.state.swipe)
        vm.takeAction(TodayAction.RowRemoveKept)
        assertEquals(SwipeState(), vm.state.swipe)
        assertTrue(diary.removed.isEmpty())

        vm.takeAction(TodayAction.DeleteRequested(banana))
        vm.takeAction(TodayAction.RowRemoveConfirmed(banana))
        assertEquals(listOf(banana), diary.removed)
        assertEquals(RemovedNotice(1, "Banana", MealSlot.Breakfast), vm.state.removed)

        vm.takeAction(TodayAction.RemovedUndoTapped)
        assertEquals(listOf(listOf(banana)), diary.restored)
        assertNull(vm.state.removed)
    }

    @Test
    fun anotherDay_dropsTheSelection() = runUnitTest {
        val vm = vm(selectOn = true)
        vm.takeAction(TodayAction.FoodLongPressed(oats))

        vm.takeAction(TodayAction.PreviousDayTapped)

        assertNull(vm.state.selection)
    }

    // --- design sync 4's summary card ---

    @Test
    fun mainNumberToggled_remembersTheSwitchForTheGoalStyle() = runUnitTest {
        val vm = vm(summaryOn = true, goalsAndEnergyOn = true, burnedRepository = FakeBurnedRepository(TodayFixtures.burned))
        assertEquals(MainKind.ToRange, (vm.state.summary as TodaySummary.Energy).card?.main?.kind)

        vm.takeAction(TodayAction.MainNumberToggled)

        assertEquals(mapOf("range" to "balance"), settings.state.value.todayMainNumberByStyle)
        assertEquals(MainKind.Deficit, (vm.state.summary as TodaySummary.Energy).card?.main?.kind)
    }

    @Test
    fun withTheFlagOff_burnedIsNotRead_andTheToggleDoesNothing() = runUnitTest {
        val vm = vm(summaryOn = true, burnedRepository = FakeBurnedRepository(TodayFixtures.burned))
        assertNull((vm.state.summary as TodaySummary.Energy).card)
        vm.takeAction(TodayAction.MainNumberToggled)
        assertEquals(emptyMap(), settings.state.value.todayMainNumberByStyle)
    }

    @Test
    fun energyTapped_opensEnergyToday() = runUnitTest {
        val vm = vm(summaryOn = true, goalsAndEnergyOn = true)
        val events = events(vm)
        vm.takeAction(TodayAction.EnergyTapped)
        assertEquals(listOf<TodayEvent>(TodayEvent.OpenEnergyToday), events)
    }
}
