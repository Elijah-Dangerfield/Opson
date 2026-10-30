package com.opson.libraries.goals

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.GoalBook
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.GoalContext
import com.opson.libraries.nutrition.GoalDays
import com.opson.libraries.nutrition.GoalVerdict
import com.opson.libraries.nutrition.RestingEnergy
import com.opson.libraries.nutrition.RestingFrom
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import com.opson.libraries.nutrition.PaceTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The engine put together for settings and a week of the log, through the fixtures the goal UI previews with. */
class GoalStatusesTest {

    @Test
    fun theGoalSettingTheTarget_comesFirst_withItsBandAndWeek() {
        val statuses = GoalFixtures.today(GoalFixtures.lose, GoalFixtures.trackEveryDay)
        assertEquals(listOf("weight", "track"), statuses.map { it.goal.id })
        val weight = statuses.first()
        assertEquals(1930, weight.target?.aimKcal)
        assertEquals(1930, weight.week?.todayTargetKcal, "a daily goal never moves")
        assertEquals(GoalVerdict.InProgress, weight.day.verdict)
        assertEquals(PaceTier.Steady, weight.pace?.tier)
        assertEquals(GoalVerdict.Met, statuses[1].day.verdict, "today has food in it")
        assertNull(statuses[1].target)
    }

    @Test
    fun aWeeklyAverage_tightensTodayOnlyALittle_afterABigDay() {
        val week = GoalFixtures.today(GoalFixtures.loseWeekly).single().week!!
        // Monday 430 under, Tuesday 970 over, Wednesday skipped: 540 over four days left.
        assertEquals(-540, week.balanceKcal)
        assertEquals(4, week.remainingDays)
        assertEquals(-130, week.adjustmentKcal)
        assertEquals(1800, week.todayTargetKcal)
    }

    @Test
    fun aWeeklyAverage_setMidweek_doesntAnswerForTheDaysBeforeIt() {
        val wednesday = GoalFixtures.loseWeekly.copy(startDate = GoalFixtures.today.minus(DatePeriod(days = 1)))
        val week = GoalFixtures.today(wednesday).single().week!!
        assertEquals(0, week.balanceKcal, "Monday and Tuesday came before the goal")
        assertEquals(1930, week.todayTargetKcal)
        // The calendar judges Thursday, seen from Friday, by the same budget: not tightened by Tuesday.
        val thursday = GoalFixtures.today
        val closed = GoalFixtures.week + (thursday to GoalFixtures.week.getValue(thursday).copy(closed = true))
        val days = GoalStatuses.days(GoalFixtures.settings(wednesday), closed, GoalFixtures.monday, thursday, thursday.plus(DatePeriod(days = 1)), GoalFixtures.WEIGHT_KG)
        assertEquals(1930, days.getValue(wednesday.id).last().targetKcal)
    }

    @Test
    fun theWeekReadsDayByDay() {
        assertEquals(
            listOf(GoalVerdict.Near, GoalVerdict.Off, GoalVerdict.NotCounted, GoalVerdict.InProgress, GoalVerdict.Future, GoalVerdict.Future, GoalVerdict.Future),
            GoalFixtures.days().map { it.verdict },
        )
    }

    @Test
    fun theProjectionStartsFromTheLatestWeighIn() {
        val projection = GoalFixtures.projection()!!
        assertEquals(GoalFixtures.today, projection.points.first().date)
        assertEquals(GoalFixtures.WEIGHT_KG, projection.points.first().targetKg)
    }

    @Test
    fun theFixturesRecalculations_sayWhatChanged() {
        assertEquals(1930, GoalFixtures.connectedHealth.oldAimKcal)
        assertEquals(2150, GoalFixtures.connectedHealth.newAimKcal)
        assertTrue(GoalFixtures.lighter.newAimKcal < 1930)
    }

    @Test
    fun aDayFromTheLog_becomesFacts() {
        val facts = GoalStatuses.facts(Day(GoalFixtures.today, untracked = true), burned = null)
        assertTrue(facts.untracked)
        assertEquals(0, facts.mealsWithFood)
        assertEquals(false, facts.showedUp)
    }

    @Test
    fun anActiveBurn_countsRealActivityOnly_andADayWithOnlyTheEstimateIsNotJudged() {
        val resting = RestingEnergy(1610, RestingFrom.Calculated)
        val estimate = GoalStatuses.facts(Day(GoalFixtures.monday), Burned(resting, everydayKcal = 600, foodKcal = 150))
        assertNull(estimate.activeKcal, "the everyday estimate and digesting food aren't activity")
        val days = GoalDays.verdicts(GoalFixtures.burn, listOf(GoalFixtures.monday), mapOf(GoalFixtures.monday to estimate), GoalContext(today = GoalFixtures.today))
        assertEquals(GoalVerdict.NotCounted, days.single().verdict)

        val moved = GoalStatuses.facts(Day(GoalFixtures.monday), Burned(resting, movementKcal = 300, workoutKcal = 150, exerciseKcal = 100, foodKcal = 150))
        assertEquals(550, moved.activeKcal)
    }

    @Test
    fun aDayFromTheLog_carriesItsSteps() {
        assertEquals(8200, GoalStatuses.facts(Day(GoalFixtures.today), burned = null, steps = 8200).steps)
    }

    @Test
    fun theStepsGoal_isTheFirstActiveOne() {
        assertEquals("steps", GoalStatuses.stepsGoal(GoalFixtures.settings(GoalFixtures.lose, GoalFixtures.steps))?.id)
        assertNull(GoalStatuses.stepsGoal(GoalFixtures.settings(GoalFixtures.lose)))
    }

    @Test
    fun gentleGoals_haveNoTargetAndNoPace() {
        val statuses = GoalStatuses.today(GoalFixtures.gentleSettings(), GoalFixtures.week, GoalFixtures.today, GoalFixtures.WEIGHT_KG)
        assertEquals(listOf("track", "meals"), statuses.map { it.goal.id })
        assertTrue(statuses.all { it.target == null && it.pace == null })
        assertNull(GoalBook.driving(GoalFixtures.gentleSettings()))
    }
}
