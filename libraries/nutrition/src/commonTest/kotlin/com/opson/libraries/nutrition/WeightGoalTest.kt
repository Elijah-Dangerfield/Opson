package com.opson.libraries.nutrition

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A weight goal's arithmetic: pace to energy by the 7,700 kcal a kilogram rule, the paces the
 * stepper offers (up to GoalPace's fastest, never under the low-goal line), the
 * forecast, the healthy-range note, the aim it writes to Targets, and how the app tells the goal
 * still sets that aim.
 */
class WeightGoalTest {

    private val start = LocalDate(2026, 10, 29)

    @Test
    fun aPace_isEnergyADay_byTheRuleOfThumb() {
        assertEquals(550, WeightGoals.kcalPerDay(0.5))
        assertEquals(1100, WeightGoals.kcalPerDay(1.0))
        // Half a pound a week is about 250 a day (3,500 a pound over seven days).
        assertEquals(250, WeightGoals.kcalPerDay(Units.lbToKg(0.5)))
        assertEquals(-550, WeightGoal(GoalDirection.Lose, kgPerWeek = 0.5).dailyKcal)
        assertEquals(550, WeightGoal(GoalDirection.Gain, kgPerWeek = 0.5).dailyKcal)
        assertEquals(0, WeightGoal(GoalDirection.Maintain, kgPerWeek = 0.5).dailyKcal)
    }

    @Test
    fun losing_goesUpToTwoPercentAWeek_whateverTheEnergy() {
        // 100 kg: 2% is 2 kg a week. The old 1,000 a day line no longer holds back a big body.
        assertEquals(2.0, WeightGoals.fastestKgPerWeek(GoalDirection.Lose, 100.0, burnKcal = null, sex = null), 1e-9)
        assertEquals(1.2, WeightGoals.fastestKgPerWeek(GoalDirection.Lose, 60.0, burnKcal = null, sex = null), 1e-9)
        assertEquals((1..12).map { it / 10.0 }, WeightGoals.rates(GoalDirection.Lose, WeightUnit.Kg, 60.0, null, null).map(::tenths))
    }

    @Test
    fun losing_neverPlansAnAimUnderTheLowGoalLine() {
        // A typical day of 1,500 for a woman leaves 300 over the 1,200 line: about 0.27 kg a week.
        val fastest = WeightGoals.fastestKgPerWeek(GoalDirection.Lose, 80.0, burnKcal = 1500, sex = BodySex.Female)
        assertEquals(300.0 * 7 / KCAL_PER_KG, fastest, 1e-9)
        val rates = WeightGoals.rates(GoalDirection.Lose, WeightUnit.Kg, 80.0, 1500, BodySex.Female)
        rates.forEach { rate ->
            val type = GoalType.Weight(GoalDirection.Lose, kgPerWeek = rate)
            val aim = GoalTargets.aimKcal(type, GoalBaseline(burnKcal = 1500, sex = BodySex.Female))!!
            assertTrue(aim >= 1200, "a pace of $rate kg a week aims at $aim")
        }
        // Already at the line: no pace at all, never a pace under it.
        assertTrue(WeightGoals.rates(GoalDirection.Lose, WeightUnit.Kg, 80.0, 1200, BodySex.Female).isEmpty())
        assertNull(WeightGoals.nearestRate(null, emptyList(), WeightUnit.Kg))
    }

    @Test
    fun gaining_isCappedAtOnePercentAWeek() {
        assertEquals(0.8, WeightGoals.fastestKgPerWeek(GoalDirection.Gain, 80.0, burnKcal = 2400, sex = BodySex.Male), 1e-9)
        assertEquals(0.0, WeightGoals.fastestKgPerWeek(GoalDirection.Maintain, 80.0, burnKcal = 2400, sex = null))
    }

    @Test
    fun poundsStepByAQuarter_andStartAtHalfAPound() {
        val rates = WeightGoals.rates(GoalDirection.Lose, WeightUnit.Lb, 90.0, null, null)
        assertEquals("0.25 lb", WeightGoals.weightText(rates.first(), WeightUnit.Lb))
        assertEquals("0.5 lb", WeightGoals.weightText(WeightGoals.nearestRate(null, rates, WeightUnit.Lb)!!, WeightUnit.Lb))
        assertEquals("0.2 kg", WeightGoals.weightText(WeightGoals.defaultRate(WeightUnit.Kg), WeightUnit.Kg))
        // A pace kept from before moves to the nearest one on offer.
        assertEquals(rates.last(), WeightGoals.nearestRate(5.0, rates, WeightUnit.Lb))
    }

    @Test
    fun theForecast_countsTheDaysAtThePace() {
        assertEquals(LocalDate(2026, 12, 10), WeightGoals.forecast(80.0, 77.0, 0.5, start))
        val goal = WeightGoal(GoalDirection.Gain, targetKg = 62.0, kgPerWeek = 0.25, startKg = 60.0, startDate = start)
        assertEquals(LocalDate(2026, 12, 24), goal.forecast)
        assertNull(WeightGoals.forecast(80.0, 80.2, 0.5, start), "already there")
        assertNull(WeightGoal(GoalDirection.Maintain, startKg = 80.0, startDate = start).forecast)
    }

    @Test
    fun aGoalUnderAHealthyRange_isNoted_onlyWithAHeight() {
        // 170 cm: BMI 18.5 is about 53.5 kg.
        assertEquals(53.465, WeightGoals.lowestHealthyKg(170.0)!!, 1e-3)
        assertTrue(WeightGoals.belowHealthyRange(52.0, 170.0))
        assertFalse(WeightGoals.belowHealthyRange(55.0, 170.0))
        assertFalse(WeightGoals.belowHealthyRange(40.0, null))
    }

    @Test
    fun theTarget_hasToPointTheWayTheGoalSays() {
        assertTrue(WeightGoals.pointsTheRightWay(GoalDirection.Lose, 80.0, 75.0))
        assertFalse(WeightGoals.pointsTheRightWay(GoalDirection.Lose, 80.0, 82.0))
        assertFalse(WeightGoals.pointsTheRightWay(GoalDirection.Lose, 80.0, 79.8))
        assertTrue(WeightGoals.pointsTheRightWay(GoalDirection.Gain, 60.0, 64.0))
    }

    @Test
    fun aGoalDrivesTheTargetsItWrote_untilTheyAreMovedByHand() {
        val goal = WeightGoal(GoalDirection.Lose, targetKg = 75.0, kgPerWeek = 0.5, startKg = 80.0, startDate = start)
        val targets = Targets.Default.withBand(1950, 1850, 2050)
        val stamped = goal.copy(targetsKcal = targets.energyKcal)

        assertTrue(stamped.drives(targets))
        assertFalse(stamped.drives(targets.raise()), "moved by hand")
        assertFalse(stamped.drives(targets.useExact()), "another style")
        assertFalse(goal.drives(targets), "a goal that never wrote one")
        assertTrue(stamped.drives(targets.copy(proteinG = 150)), "macros are the person's own")
    }

    private fun tenths(kg: Double): Double = kotlin.math.round(kg * 10) / 10
}
