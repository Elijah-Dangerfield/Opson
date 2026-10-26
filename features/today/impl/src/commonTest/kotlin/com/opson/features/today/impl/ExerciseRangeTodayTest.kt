package com.opson.features.today.impl

import com.opson.features.today.impl.dayclose.RangeLine
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EnergyRange
import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Exercise in today's range (0.2): [todayRange] and where Today and Day close show it. The base
 * is 1,900 to 2,300; the fixture's 410 active (260 movement, 150 workouts) adds 205 for Some of
 * it and 410 for All of it.
 */
class ExerciseRangeTodayTest {

    private val base = EnergyRange(2100, 1900, 2300, 0)
    private val today = TodayFixtures.day()

    @Test
    fun notConnected_orFlagOff_isTheBase() {
        assertEquals(base, todayRange(TodayFixtures.exerciseAll, today, TodayFixtures.today, expended = null))
    }

    @Test
    fun unanswered_isTheBase() {
        assertEquals(base, todayRange(Settings(), today, TodayFixtures.today, TodayFixtures.expended))
    }

    @Test
    fun someOfIt_addsHalf() {
        assertEquals(EnergyRange(2305, 2105, 2505, 205), todayRange(TodayFixtures.exerciseSome, today, TodayFixtures.today, TodayFixtures.expended))
    }

    @Test
    fun allOfIt_addsAllTheActiveEnergy() {
        assertEquals(EnergyRange(2510, 2310, 2710, 410), todayRange(TodayFixtures.exerciseAll, today, TodayFixtures.today, TodayFixtures.expended))
    }

    @Test
    fun aPastDay_isTheBase() {
        val past = TodayFixtures.day(TodayFixtures.today.minus(DatePeriod(days = 1)))
        assertEquals(base, todayRange(TodayFixtures.exerciseAll, past, TodayFixtures.today, TodayFixtures.expended))
    }

    @Test
    fun aDayLeftOutOfTheMath_isTheBase() {
        assertEquals(base, todayRange(TodayFixtures.exerciseAll, TodayFixtures.untrackedDay(), TodayFixtures.today, TodayFixtures.expended))
    }

    @Test
    fun numbersHaveBeenHard_isTheBase_whateverIsStored() {
        ExerciseAdjustment.entries.forEach { choice ->
            val settings = Settings(exerciseRange = choice, onboarding = OnboardingAnswers(hard = true))
            assertEquals(base, todayRange(settings, today, TodayFixtures.today, TodayFixtures.expended), choice.name)
        }
    }

    @Test
    fun aReadingThatMakesNoSense_addsNothing_andNeverLowers() {
        listOf(
            EnergyExpended(restingKcal = 1500.0, activeKcal = Double.NaN, workoutKcal = Double.NaN),
            EnergyExpended(restingKcal = null, activeKcal = Double.POSITIVE_INFINITY, workoutKcal = null),
            EnergyExpended(restingKcal = 1500.0, activeKcal = -800.0, workoutKcal = -100.0),
            EnergyExpended(restingKcal = 1500.0, activeKcal = null, workoutKcal = null),
        ).forEach { energy ->
            val range = todayRange(TodayFixtures.exerciseAll, today, TodayFixtures.today, energy)
            assertEquals(base, range, "$energy")
        }
    }

    @Test
    fun todaysSummary_showsTheMovedRange() {
        val state = TodayFixtures.todayState(policy(PresetId.Full), settings = TodayFixtures.exerciseSome, expended = TodayFixtures.expended)
        val energy = assertIs<TodaySummary.Energy>(state.summary)
        assertEquals("2,105 to 2,505", energy.range.text())
    }

    @Test
    fun todaysSummary_withoutEnergy_showsTheBase() {
        val state = TodayFixtures.todayState(policy(PresetId.Full), settings = TodayFixtures.exerciseAll)
        assertEquals("1,900 to 2,300", assertIs<TodaySummary.Energy>(state.summary).range.text())
    }

    @Test
    fun energyOutHidden_theRangeStillMoves_butNoEnergyOutAmountShows() {
        val state = TodayFixtures.todayState(
            policy(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Hide)),
            settings = TodayFixtures.exerciseAll,
            expended = TodayFixtures.expended,
        )
        val energy = assertIs<TodaySummary.Energy>(state.summary)
        assertEquals("2,310 to 2,710", energy.range.text())
        assertEquals(Display.Hidden, energy.expended)
    }

    @Test
    fun blind_hasNoRangeToShow() {
        listOf(PresetId.Blind, PresetId.Rhythm, PresetId.Mindful).forEach { preset ->
            val state = TodayFixtures.todayState(policy(preset), settings = TodayFixtures.exerciseAll, expended = TodayFixtures.expended)
            assertEquals(null, (state.summary as? TodaySummary.Energy)?.range?.text(), "$preset")
        }
    }

    @Test
    fun dayClose_usesTheSameRange() {
        val state = TodayFixtures.dayCloseState(policy(PresetId.Full), settings = TodayFixtures.exerciseAll, expended = TodayFixtures.expended)
        val totals = assertNotNull(state.totals)
        // 2,058 eaten, under the moved range.
        assertEquals("2,310 to 2,710", assertIs<RangeLine.Below>(totals.rangeLine).range.text())
    }

    @Test
    fun dayClose_aPastDay_isTheBase() {
        val past = TodayFixtures.today.minus(DatePeriod(days = 1))
        val state = TodayFixtures.dayCloseState(policy(PresetId.Full), settings = TodayFixtures.exerciseAll, expended = TodayFixtures.expended, date = past)
        assertEquals("1,900 to 2,300", assertIs<RangeLine.Inside>(assertNotNull(state.totals).rangeLine).range.text())
    }
}
