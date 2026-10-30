package com.opson.libraries.goals

import com.opson.libraries.nutrition.GoalDay
import com.opson.libraries.nutrition.GoalVerdict
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Wrapping a day up against a goal: the outcome words, the week after the day counts, and goal streaks. */
class GoalWrapUpsTest {

    @Test
    fun aDayInsideTheBand_isOnPlan() {
        val wrap = GoalFixtures.wrapUp(eatenKcal = 1950.0).single()
        assertEquals(GoalVerdict.Met, wrap.day.verdict)
        assertEquals(GoalOutcome.OnPlan, wrap.outcome)
        assertNull(wrap.weekOnTrack, "a daily goal has no week to be on track")
    }

    @Test
    fun overTheBand_isABitOver_thenABigDay() {
        // Band 1,830 to 2,030; the near margin is 193 (a tenth of 1,930).
        assertEquals(GoalOutcome.ABitOver, GoalFixtures.wrapUp(eatenKcal = 2200.0).single().outcome)
        assertEquals(GoalOutcome.BigDay, GoalFixtures.wrapUp(eatenKcal = 2400.0).single().outcome)
    }

    @Test
    fun wellUnderALossGoal_isNeverOnPlan() {
        assertEquals(GoalOutcome.WellUnder, GoalFixtures.wrapUp().single().outcome)
        assertEquals(GoalOutcome.ABitUnder, GoalFixtures.wrapUp(eatenKcal = 1750.0).single().outcome)
    }

    @Test
    fun aWeeklyGoal_judgesTheDayByItsMorningTarget_andCountsItInTheWeek() {
        // This morning's target was 1,800 (1,700 to 1,900) after Tuesday's big day.
        val wrap = GoalFixtures.wrapUp(GoalFixtures.loseWeekly, eatenKcal = 1850.0).single()
        assertEquals(1800, wrap.day.targetKcal)
        assertEquals(GoalOutcome.OnPlan, wrap.outcome)
        val week = wrap.week!!
        assertEquals(3, week.countedDays, "Monday, Tuesday and today")
        assertEquals(3, week.remainingDays, "Friday to Sunday")
    }

    @Test
    fun aBigDayInAWeeklyGoal_leavesTheWeekToCarryOn() {
        val wrap = GoalFixtures.wrapUp(GoalFixtures.loseWeekly, eatenKcal = 2600.0).single()
        assertEquals(GoalOutcome.BigDay, wrap.outcome)
        // 1,210 over across three counted days, more than three days left can take within the caps.
        assertEquals(false, wrap.weekOnTrack)
    }

    @Test
    fun aWeekBackInsideItsTolerance_isOnTrack() {
        // Monday 430 under, Tuesday 970 over; 1,390 today is 540 under its 1,930: about even.
        val wrap = GoalFixtures.wrapUp(GoalFixtures.lose.copy(averaging = com.opson.libraries.nutrition.GoalAveraging.Weekly), eatenKcal = 1390.0).single()
        assertEquals(true, wrap.weekOnTrack)
    }

    @Test
    fun countGoals_metOrNotThisTime() {
        val wraps = GoalFixtures.wrapUp(GoalFixtures.lose, GoalFixtures.trackEveryDay, GoalFixtures.meals)
        assertEquals(GoalOutcome.OnPlan, wraps.first { it.goal.id == "track" }.outcome)
        assertEquals(GoalOutcome.NotThisTime, wraps.first { it.goal.id == "meals" }.outcome, "two meals of three")
    }

    @Test
    fun aStreak_onlyWhenSwitchedOn_andNeverForAWeightGoal() {
        assertNull(GoalFixtures.wrapUp(GoalFixtures.lose, GoalFixtures.trackEveryDay).first { it.goal.id == "track" }.streak)
        val wraps = GoalFixtures.wrapUp(GoalFixtures.lose, GoalFixtures.trackEveryDay, streaks = true)
        assertNull(wraps.first { it.goal.id == "weight" }.streak)
        // Monday and Tuesday, Wednesday skipped (a rest), today.
        assertEquals(3, wraps.first { it.goal.id == "track" }.streak)
    }

    @Test
    fun eligibility_isOnlyWhereMetIsntEatingLess() {
        assertTrue(GoalStreaks.eligible(GoalFixtures.trackEveryDay.type))
        assertTrue(GoalStreaks.eligible(GoalFixtures.meals.type))
        assertTrue(GoalStreaks.eligible(GoalFixtures.burn.type))
        assertTrue(GoalStreaks.eligible(GoalFixtures.steps.type))
        assertFalse(GoalStreaks.eligible(GoalFixtures.lose.type))
        assertFalse(GoalStreaks.eligible(GoalFixtures.balance.type))
    }

    @Test
    fun aRun_forgivesOneDay_andTwoEndIt() {
        fun days(vararg verdicts: GoalVerdict) = verdicts.mapIndexed { i, v -> GoalDay(GoalFixtures.monday.plus(DatePeriod(days = i)), v) }
        val met = GoalVerdict.Met
        val off = GoalVerdict.Off
        assertEquals(3, GoalStreaks.run(days(met, met, off, met)))
        assertEquals(1, GoalStreaks.run(days(met, met, off, off, met)))
        assertEquals(2, GoalStreaks.run(days(met, GoalVerdict.NotCounted, met, GoalVerdict.InProgress)))
        assertEquals(0, GoalStreaks.run(days(off, off)))
        assertEquals(2, GoalStreaks.run(days(met, met, off)), "a forgiven day at the end leaves the run going")
    }

    @Test
    fun theWeeksAdjustment_isTodaysMove_andNothingForADailyGoal() {
        assertEquals(-130, GoalFixtures.today(GoalFixtures.loseWeekly).single().weekAdjustmentKcal)
        assertEquals(0, GoalFixtures.today(GoalFixtures.lose).single().weekAdjustmentKcal)
    }
}
