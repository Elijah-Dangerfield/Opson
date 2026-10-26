package com.opson.features.settings.impl.integrations

import com.opson.features.settings.impl.SettingsFixtures
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.PresetId
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The second pass of Integrations' state: "Should exercise change your range?" and the days left
 * out of the math, across the visibility branches. Neither carries an amount, so neither changes
 * with the mode; what changes them is the connection and the "numbers have been hard" answer.
 */
class ExerciseAndLeftOutStateTest {

    private val presets = listOf(PresetId.Full, PresetId.Blind, PresetId.Fuel, PresetId.Delayed, PresetId.Nutrient)
    private val today = IntegrationsFixtures.today

    @Test
    fun connected_asksTheQuestion_inEveryMode() {
        presets.forEach { preset ->
            val state = IntegrationsFixtures.state(SettingsFixtures.policy(preset))
            assertEquals(ExerciseQuestion(ExerciseAdjustment.Some), state.exercise, "$preset")
            assertEquals(listOf(ExerciseAdjustment.None, ExerciseAdjustment.Some, ExerciseAdjustment.All), state.exercise?.options)
        }
    }

    @Test
    fun unanswered_selectsNo() {
        val state = IntegrationsFixtures.state(SettingsFixtures.policy(PresetId.Full), settings = IntegrationsFixtures.settings.copy(exerciseRange = null))
        assertEquals(ExerciseQuestion(ExerciseAdjustment.None), state.exercise)
    }

    @Test
    fun notConnected_doesNotAsk() {
        listOf(HealthStatus.NotConnected, HealthStatus.NeedsInstall, HealthStatus.Unsupported).forEach { status ->
            assertNull(IntegrationsFixtures.state(SettingsFixtures.policy(PresetId.Full), status = status).exercise, "$status")
        }
    }

    @Test
    fun numbersHaveBeenHard_neverAsks_inAnyMode() {
        val hard = IntegrationsFixtures.settings.copy(onboarding = OnboardingAnswers(hard = true))
        presets.forEach { preset ->
            assertNull(IntegrationsFixtures.state(SettingsFixtures.policy(preset), settings = hard).exercise, "$preset")
        }
    }

    @Test
    fun leftOut_listsUntrackedDays_withTheirNotes_inEveryMode() {
        presets.forEach { preset ->
            val state = IntegrationsFixtures.state(SettingsFixtures.policy(preset))
            assertEquals(listOf(LeftOutDay(IntegrationsFixtures.familyVisit, "Sun, Oct 18", "Family visit")), state.leftOut.days, "$preset")
        }
    }

    @Test
    fun leftOut_isShownWhetherOrNotConnected() {
        val state = IntegrationsFixtures.state(SettingsFixtures.policy(PresetId.Full), status = HealthStatus.NotConnected)
        assertEquals(1, state.leftOut.days.size)
    }

    @Test
    fun leftOut_newestFirst_blankNotesDropped_onlyInTheWindow() {
        fun untracked(date: LocalDate, note: String? = null) = Day.empty(date).copy(untracked = true, note = note)
        val days = listOf(
            untracked(today.minus(DatePeriod(days = 10)), note = "  "),
            untracked(today, note = " Sick day "),
            untracked(LeaveOutWindow.start(today)),
            untracked(LeaveOutWindow.start(today).minus(DatePeriod(days = 1))),
            untracked(today.plus(DatePeriod(days = 1))),
            Day.empty(today.minus(DatePeriod(days = 2))),
        )
        val state = IntegrationsFixtures.state(SettingsFixtures.policy(PresetId.Full), days = days)
        assertEquals(
            listOf(
                LeftOutDay(today, "Sun, Oct 25", "Sick day"),
                LeftOutDay(today.minus(DatePeriod(days = 10)), "Thu, Oct 15", null),
                LeftOutDay(LeaveOutWindow.start(today), "Tue, Jul 28", null),
            ),
            state.leftOut.days,
        )
    }

    @Test
    fun leftOut_noneYet() {
        assertTrue(IntegrationsFixtures.state(SettingsFixtures.policy(PresetId.Full), days = emptyList()).leftOut.days.isEmpty())
    }

    @Test
    fun theWindow_isNinetyDays_endingToday() {
        assertEquals(LocalDate(2026, 7, 28), LeaveOutWindow.start(today))
        assertTrue(LeaveOutWindow.contains(today, today))
        assertTrue(LeaveOutWindow.contains(LocalDate(2026, 7, 28), today))
        assertEquals(false, LeaveOutWindow.contains(LocalDate(2026, 7, 27), today))
        assertEquals(false, LeaveOutWindow.contains(LocalDate(2026, 10, 26), today))
    }
}
