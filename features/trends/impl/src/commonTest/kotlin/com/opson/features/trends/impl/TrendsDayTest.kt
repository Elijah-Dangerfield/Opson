package com.opson.features.trends.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The calendar's day sheet: it agrees with the calendar's cell, shows eaten, burned, the
 * difference and the plan only where the mode shows them in full, and opens the day on Today.
 */
class TrendsDayTest : CoroutineTest() {

    private val inputs = TrendsFixtures.deficitInputs()
    private val thursday = LocalDate(2026, 10, 22)
    private val monday = LocalDate(2026, 10, 19)

    private fun build(date: LocalDate, policy: com.opson.libraries.nutrition.VisibilityPolicy = TrendsFixtures.policy(), settings: Settings = inputs.settings) =
        TrendsDayStateBuilder.build(
            inputs.days.firstOrNull { it.date == date } ?: Day.empty(date),
            inputs.today,
            settings,
            policy,
            inputs.burnedByDay?.get(date),
        )

    @Test
    fun aDay_saysWhatItsCellSays() {
        val calendar = TrendsStateBuilder.build(inputs, TrendsFixtures.policy()).calendar!!
        calendar.shown.cells.filterNotNull().filter { it.date <= inputs.today }.forEach { cell ->
            assertEquals(cell.standing, build(cell.date).standing, "${cell.date}")
            assertEquals(cell.tone, build(cell.date).tone, "${cell.date}")
        }
    }

    @Test
    fun aLoggedDay_inAllNumbers_hasEatenBurnedTheDifferenceAndThePlan() {
        val state = build(monday)
        val eaten = inputs.days.single { it.date == monday }.totals().kcal.known
        val burned = inputs.burnedByDay!!.getValue(monday).totalKcal
        assertIs<Display.Shown>(state.eaten)
        assertIs<Display.Shown>(state.burned)
        assertEquals(RunningTotals.side(burned - eaten), state.side)
        assertIs<Display.Shown>(state.difference)
        assertEquals(true, state.goalAbout, "a deficit's goal is about a number")
        assertEquals(true, state.canOpen)
    }

    @Test
    fun anEmptyDay_isNotLogged_withNothingEatenOrBurned() {
        val state = build(thursday)
        assertEquals(PlanStanding.NotLogged, state.standing)
        assertEquals(Display.Hidden, state.eaten)
        assertEquals(Display.Hidden, state.difference)
    }

    @Test
    fun hiddenModes_andNumbersHaveBeenHard_sayOnlyLoggedOrNot() {
        listOf(
            build(monday, TrendsFixtures.policy(PresetId.NoNumbers)),
            build(monday, TrendsFixtures.policy(PresetId.MacrosWithGrams)),
            build(monday, settings = inputs.settings.copy(onboarding = OnboardingAnswers(hard = true))),
            build(monday, TrendsFixtures.policy(overrides = mapOf(Metric.EnergyOut to Visibility.Hide))),
        ).forEach { state ->
            assertEquals(CalendarMode.LoggedOnly, state.mode)
            assertEquals(PlanStanding.Logged, state.standing)
            listOf(state.eaten, state.burned, state.difference, state.goal).forEach { assertEquals(Display.Hidden, it) }
        }
    }

    @Test
    fun openDayTapped_movesTodayToTheDay_andOpensIt() = runUnitTest {
        val selected = RecordingSelectedDay()
        val vm = TrendsDayViewModel(
            thursday, FakeDiaryRepository(inputs.days), FakeSettingsRepository(inputs.settings), FakePolicySource(),
            goalsAndEnergyFlag(on = true), FakeBurnedRepository(inputs.burnedByDay!!.getValue(thursday)), selected,
            TrendsFixtures.clock, TrendsFixtures.timeZone,
        )
        val events = mutableListOf<TrendsDayEvent>()
        backgroundScope.launch { vm.eventFlow.collect { events += it } }
        assertEquals(PlanStanding.NotLogged, vm.state.standing)
        vm.takeAction(TrendsDayAction.OpenDayTapped)
        assertEquals(thursday, selected.date.value)
        assertEquals(listOf<TrendsDayEvent>(TrendsDayEvent.OpenToday), events)
        vm.takeAction(TrendsDayAction.CloseTapped)
        assertTrue(TrendsDayEvent.Dismiss in events)
    }

    private class RecordingSelectedDay : com.opson.libraries.diary.SelectedDay {
        private val current = MutableStateFlow(TrendsFixtures.today)
        override val date: StateFlow<LocalDate> = current
        override fun select(day: LocalDate) {
            current.value = day
        }
    }
}
