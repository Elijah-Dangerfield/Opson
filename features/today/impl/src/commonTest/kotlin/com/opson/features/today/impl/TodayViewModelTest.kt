package com.opson.features.today.impl

import com.opson.features.logger.LogTab
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_toast_added_to
import opson.libraries.resources.generated.resources.today_toast_cleared
import opson.libraries.resources.generated.resources.today_toast_copied_previous
import opson.libraries.resources.generated.resources.today_toast_copied_tomorrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    private fun vm() = TodayViewModel(
        diary = diary,
        settingsRepository = settings,
        policySource = policySource,
        selectedDay = selectedDay,
        calendar = FakeCalendar(),
    )

    private fun TestScope.events(vm: TodayViewModel): List<TodayEvent> {
        val events = mutableListOf<TodayEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
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
}
