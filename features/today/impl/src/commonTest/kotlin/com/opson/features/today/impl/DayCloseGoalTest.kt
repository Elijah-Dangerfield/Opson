package com.opson.features.today.impl

import com.opson.features.today.impl.dayclose.GoalWrapCard
import com.opson.features.today.impl.dayclose.GoalWrapLine
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.goals.GoalFixtures
import com.opson.libraries.goals.GoalOutcome
import com.opson.libraries.goals.GoalWrapUp
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Wrap up the day's goal card: kind words for how the day went, the week so far, and a goal streak. */
class DayCloseGoalTest {

    private fun card(
        goals: List<GoalWrapUp>,
        policy: VisibilityPolicy = policy(PresetId.Full),
        hard: Boolean = false,
    ): GoalWrapCard? = TodayFixtures.dayCloseState(
        policy,
        settings = GoalFixtures.settings(GoalFixtures.lose).copy(onboarding = OnboardingAnswers(hard = hard)),
        goals = goals,
    ).goal

    @Test
    fun aDayInsideTheBand_isOnPlan() {
        val row = assertNotNull(card(GoalFixtures.wrapUp(eatenKcal = 1950.0))).rows.single()
        assertEquals(listOf<GoalWrapLine>(GoalWrapLine.Calories(GoalOutcome.OnPlan, weekly = false, weekOnTrack = false)), row.lines)
    }

    @Test
    fun aBitOverAWeeklyGoal_saysTheWeekIsStillOnTrack_andAsksNothingOfTomorrow() {
        // This morning's target was 1,800 (1,700 to 1,900); 1,950 is a bit over and the week can take it.
        val lines = assertNotNull(card(GoalFixtures.wrapUp(GoalFixtures.loseWeekly, eatenKcal = 1950.0))).rows.single().lines
        assertEquals(listOf<GoalWrapLine>(GoalWrapLine.Calories(GoalOutcome.ABitOver, weekly = true, weekOnTrack = true)), lines)
    }

    @Test
    fun aBigDayInAWeeklyGoal_neverSpellsOutATighterWeek() {
        val lines = assertNotNull(card(GoalFixtures.wrapUp(GoalFixtures.loseWeekly, eatenKcal = 2700.0))).rows.single().lines
        assertEquals(1, lines.size, "no line about eating less on the days left")
        assertEquals(GoalOutcome.BigDay, assertIs<GoalWrapLine.Calories>(lines.single()).outcome)
    }

    @Test
    fun aWeekThatLeavesRoom_saysAboutHowMuchADay() {
        val lines = assertNotNull(card(GoalFixtures.wrapUp(GoalFixtures.loseWeekly))).rows.single().lines
        assertEquals(GoalOutcome.WellUnder, assertIs<GoalWrapLine.Calories>(lines.first()).outcome)
        assertNotNull(assertIs<GoalWrapLine.WeekEase>(lines[1]).kcal.text())
    }

    @Test
    fun withCaloriesHidden_aCalorieGoalSaysNothing_butMealsStill() {
        val hidden = policy(PresetId.Full, overrides = mapOf(Metric.Calories to Visibility.Hide))
        val rows = assertNotNull(card(GoalFixtures.wrapUp(GoalFixtures.lose, GoalFixtures.meals), policy = hidden)).rows
        assertEquals(listOf("meals"), rows.map { it.goalId })
        assertEquals(listOf<GoalWrapLine>(GoalWrapLine.Meals(done = 2, goal = 3, met = false)), rows.single().lines)
    }

    @Test
    fun aGoalStreak_showsForMeals_andNeverWithoutCounts() {
        val wraps = GoalFixtures.wrapUp(GoalFixtures.lose, GoalFixtures.trackEveryDay, streaks = true)
        val track = assertNotNull(card(wraps)).rows.first { it.goalId == "track" }
        assertEquals(3, track.streak)
        assertNull(assertNotNull(card(wraps)).rows.first { it.goalId == "weight" }.streak)
        assertTrue(assertNotNull(card(wraps, hard = true)).rows.all { it.streak == null }, "gentle celebrations say no counts")
    }

    @Test
    fun aBurnGoalShortOfItsLine_saysBothAmounts_andHidesThemWithEnergyOut() {
        val wraps = GoalFixtures.wrapUp(GoalFixtures.burn)
        // Today's 300 active against 500.
        val burn = assertIs<GoalWrapLine.Burn>(assertNotNull(card(wraps)).rows.single().lines.single())
        assertEquals("300 kcal", burn.burned?.text)
        assertEquals("500 kcal", burn.goal?.text)
        val hidden = policy(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Hide))
        assertNull(card(wraps, policy = hidden))
    }
}
