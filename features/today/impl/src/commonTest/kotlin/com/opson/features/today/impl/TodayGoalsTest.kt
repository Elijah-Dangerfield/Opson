package com.opson.features.today.impl

import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.goals.GoalFixtures
import com.opson.libraries.goals.GoalToday
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.GoalAveraging
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RestingEnergy
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Today with a goal: the band a weekly average moves, the week's line, the chip, and the burned
 * bar's marks. The fixture day eats 1,245 against 2,180 burned (1,610 resting).
 */
class TodayGoalsTest {

    private fun card(
        settings: Settings = TodayFixtures.weeklyGoalSettings,
        goals: List<GoalToday> = TodayFixtures.weeklyGoals,
        usual: Int? = TodayFixtures.USUAL_BURN_KCAL,
        policy: VisibilityPolicy = policy(PresetId.Full),
    ): EnergyCard = assertNotNull(
        (
            TodayFixtures.todayState(
                policy,
                settings = settings,
                summaryOn = true,
                goalsAndEnergyOn = true,
                burned = TodayFixtures.burned,
                goals = goals,
                usualBurnKcal = usual,
            ).summary as TodaySummary.Energy
            ).card,
    )

    @Test
    fun aWeeklyGoal_movesTodaysBand_andSaysAboutHowMuchToday() {
        val card = card()
        // The goal's band is 1,830 to 2,030; the week asks 130 less of today.
        assertEquals("1,700 to 1,900", (card.goal as GoalLine.Range).range.text())
        assertEquals("1,800 kcal", assertIs<WeekLine.Eat>(card.weekLine).kcal.text())
    }

    @Test
    fun aWeekThatAsksNothingDifferent_isOnTrack() {
        val even = TodayFixtures.weeklyGoals.map { it.copy(week = it.week?.copy(adjustmentKcal = 0, todayTargetKcal = 1930)) }
        assertEquals(WeekLine.OnTrack, card(goals = even).weekLine)
        assertEquals("1,830 to 2,030", (card(goals = even).goal as GoalLine.Range).range.text())
    }

    @Test
    fun afterNumbersHaveBeenHard_aTighterWeekSaysNothing() {
        val hard = TodayFixtures.weeklyGoalSettings.copy(onboarding = OnboardingAnswers(hard = true))
        assertNull(card(settings = hard).weekLine)
    }

    @Test
    fun aDailyGoal_movesNothing_andOnlyNamesTheGoal() {
        val card = card(settings = GoalFixtures.settings(GoalFixtures.lose), goals = GoalFixtures.today(GoalFixtures.lose))
        assertNull(card.weekLine)
        assertEquals("1,830 to 2,030", (card.goal as GoalLine.Range).range.text())
        assertEquals(GoalChip("weight", GoalLabel.Lose), card.goalChip)
    }

    @Test
    fun anEnergyBalanceDeficit_saysTheDeficitToday() {
        val weekly = GoalFixtures.balance.copy(averaging = GoalAveraging.Weekly)
        val card = card(settings = GoalFixtures.settings(weekly), goals = GoalFixtures.today(weekly))
        val line = assertIs<WeekLine.Deficit>(card.weekLine)
        assertEquals(GoalLabel.Deficit, card.goalChip?.label)
        assertNotNull(line.kcal.text())
    }

    @Test
    fun noGoal_noChipAndNoLine() {
        val card = card(settings = Settings(), goals = emptyList(), usual = null)
        assertNull(card.goalChip)
        assertNull(card.weekLine)
        assertNull(card.burned?.usual)
        assertNull(card.burned?.goal)
    }

    @Test
    fun theBurnedBar_marksAUsualDay_andAnActiveBurnGoalOnTopOfResting() {
        val burned = assertNotNull(card().burned)
        assertEquals("2,350", burned.usual?.kcal?.text())
        // 500 active on top of today's 1,610 resting.
        assertEquals("2,110", burned.goal?.kcal?.text())
        // Every mark on the card's one scale: the usual day is the longest, so it sets it.
        assertEquals(2350 / (2350 * 1.2f), burned.usual!!.fraction, 0.001f)
        assertEquals(2180 / (2350 * 1.2f), burned.tick, 0.001f)
    }

    @Test
    fun anActiveBurnGoal_sitsPastTheEstimateAndDigesting_soOnlyRealActivityCrossesIt() {
        val goals = GoalFixtures.today(GoalFixtures.lose, GoalFixtures.burn)
        val estimated = Burned(RestingEnergy(1610, RestingFrom.Calculated), everydayKcal = 320, foodKcal = 120)
        assertEquals(1610 + 320 + 120 + 500, TodayGoals.burnGoalKcal(goals, estimated))
        val moved = estimated.copy(everydayKcal = null, movementKcal = 400)
        assertEquals(1610 + 120 + 500, TodayGoals.burnGoalKcal(goals, moved))
    }

    @Test
    fun theMarks_leaveWithEnergyOut() {
        val card = card(policy = policy(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Hide)))
        assertNull(card.burned)
    }

    @Test
    fun aUsualDay_isTheMedianOfWholeDays_andWaitsForEnough() {
        val today = TodayFixtures.today
        fun burned(kcal: Int) = Burned(RestingEnergy(kcal, RestingFrom.Calculated))
        val days = listOf(2100, 2400, 2200, 3900, 2300).mapIndexed { i, kcal -> today.minus(DatePeriod(days = i + 1)) to burned(kcal) }.toMap()
        assertEquals(2300, UsualBurn.of(days, today), "a marathon day doesn't move it")
        assertEquals(2300, UsualBurn.of(days + (today to burned(900)), today), "today is still going")
        assertNull(UsualBurn.of(days.entries.take(3).associate { it.key to it.value }, today))
        assertEquals(2300, UsualBurn.of(days.entries.take(4).associate { it.key to it.value }, today))
    }
}
