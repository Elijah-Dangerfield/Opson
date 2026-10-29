package com.opson.libraries.nutrition

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * When a goal is low or a pace too fast for most people without a doctor's guidance: the lines,
 * by sex and with none given, the deficit check with and without a weight, and a weight goal's
 * pace by a date, which no screen asks yet (the goal screen is being designed separately).
 *
 * Not covered here: how Targets shows the note, which is `TargetsSafetyTest`'s; and the lines
 * themselves being right, which is a clinician's review, not a test.
 */
class GoalSafetyTest {

    private val today = LocalDate(2026, 10, 29)

    @Test
    fun theLowGoalLine_followsSex_andIsHalfwayWhenNotSaid() {
        assertEquals(1200, GoalSafety.lowGoalKcal(BodySex.Female))
        assertEquals(1500, GoalSafety.lowGoalKcal(BodySex.Male))
        assertEquals(1350, GoalSafety.lowGoalKcal(BodySex.Unsaid))
        assertEquals(1350, GoalSafety.lowGoalKcal(null))
    }

    @Test
    fun aGoalIsLowOnlyUnderTheLine() {
        assertTrue(GoalSafety.isLowGoal(1150, BodySex.Female))
        assertFalse(GoalSafety.isLowGoal(1200, BodySex.Female))
        assertTrue(GoalSafety.isLowGoal(1400, BodySex.Male))
        assertFalse(GoalSafety.isLowGoal(Targets.Default.minKcal, null))
    }

    @Test
    fun aDeficitIsFastOver1000ADay_orOver1PercentOfBodyWeightAWeek() {
        assertFalse(GoalSafety.isFastDeficit(0))
        assertFalse(GoalSafety.isFastDeficit(1000))
        assertTrue(GoalSafety.isFastDeficit(1050))
        // 700 a day is 4,900 a week, about 0.64 kg: over 1% of 55 kg, under 1% of 80 kg.
        assertTrue(GoalSafety.isFastDeficit(700, weightKg = 55.0))
        assertFalse(GoalSafety.isFastDeficit(700, weightKg = 80.0))
        assertFalse(GoalSafety.isFastDeficit(700, weightKg = null))
    }

    @Test
    fun aWeightGoal_aGentlePace() {
        // 5 kg in 10 weeks: 0.5 kg a week, 550 a day.
        val pace = requireNotNull(GoalSafety.weightGoalPace(80.0, 75.0, today, today.plus(DatePeriod(days = 70))))
        assertEquals(-550, pace.dailyKcal)
        assertEquals(-0.5, pace.kgPerWeek, 0.001)
        assertEquals(Pace.Gentle, pace.pace)
    }

    @Test
    fun aWeightGoal_overAThousandADay_isTooFast() {
        // 10 kg in 8 weeks: 1,375 a day.
        val pace = requireNotNull(GoalSafety.weightGoalPace(90.0, 80.0, today, today.plus(DatePeriod(days = 56))))
        assertEquals(-1375, pace.dailyKcal)
        assertEquals(Pace.TooFast, pace.pace)
    }

    @Test
    fun aWeightGoal_overOnePercentAWeek_isTooFast_evenUnderAThousandADay() {
        // 4 kg in 5 weeks from 55 kg: 0.8 kg a week (1.45%), 880 a day.
        val pace = requireNotNull(GoalSafety.weightGoalPace(55.0, 51.0, today, today.plus(DatePeriod(days = 35))))
        assertEquals(-880, pace.dailyKcal)
        assertEquals(Pace.TooFast, pace.pace)
    }

    @Test
    fun aWeightGoal_gainingFast_isTooFast_gainingSlowly_isGentle() {
        val fast = requireNotNull(GoalSafety.weightGoalPace(60.0, 66.0, today, today.plus(DatePeriod(days = 42))))
        assertTrue(fast.dailyKcal > 0)
        assertEquals(Pace.TooFast, fast.pace)
        val slow = requireNotNull(GoalSafety.weightGoalPace(60.0, 62.0, today, today.plus(DatePeriod(days = 140))))
        assertEquals(Pace.Gentle, slow.pace)
    }

    @Test
    fun aWeightGoal_alreadyThere_isAtGoal() {
        assertEquals(Pace.AtGoal, GoalSafety.weightGoalPace(70.0, 70.3, today, today.plus(DatePeriod(days = 30)))?.pace)
    }

    @Test
    fun aWeightGoal_withNoTimeLeft_hasNoPace() {
        assertNull(GoalSafety.weightGoalPace(80.0, 75.0, today, today))
        assertNull(GoalSafety.weightGoalPace(80.0, 75.0, today, LocalDate(2026, 10, 1)))
        assertNull(GoalSafety.weightGoalPace(0.0, 75.0, today, today.plus(DatePeriod(days = 30))))
    }
}
