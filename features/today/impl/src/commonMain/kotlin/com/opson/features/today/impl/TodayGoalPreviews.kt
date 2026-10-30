package com.opson.features.today.impl

import androidx.compose.runtime.Composable
import com.opson.features.today.impl.dayclose.DayCloseScreen
import com.opson.libraries.core.fixed
import com.opson.libraries.diary.SummaryCardPreset
import com.opson.libraries.goals.GoalFixtures
import com.opson.libraries.goals.GoalToday
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.diary.Settings
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Clock

// Today and Wrap up the day with a goal set (owner feedback, 2026-10-30), from the real builders
// over TodayFixtures and GoalFixtures, as the leak test draws them.

private val full = VisibilityPolicy(NumbersSettings(preset = PresetId.Full), Clock.fixed(TodayFixtures.noon), TodayFixtures.timeZone)

@Composable
private fun TodayWithGoals(settings: Settings, goals: List<GoalToday>, usual: Int? = TodayFixtures.USUAL_BURN_KCAL, dark: Boolean = false) =
    PreviewContent(darkTheme = dark) {
        TodayScreen(
            state = TodayFixtures.todayState(
                full,
                settings = settings,
                expended = TodayFixtures.expended,
                summaryOn = true,
                goalsAndEnergyOn = true,
                burned = TodayFixtures.burned,
                goals = goals,
                usualBurnKcal = usual,
            ),
            onAction = {},
        )
    }

/** A weekly average after a big day: "about 1,800 kcal today", the goal's chip, and the burned bar's two marks. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayGoalWeeklyPreview() = TodayWithGoals(TodayFixtures.weeklyGoalSettings, TodayFixtures.weeklyGoals)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayGoalWeeklyDarkPreview() = TodayWithGoals(TodayFixtures.weeklyGoalSettings, TodayFixtures.weeklyGoals, dark = true)

/** Range bar: the marks are the legend's words under the bar. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayGoalRangeBarPreview() = TodayWithGoals(
    TodayFixtures.weeklyGoalSettings.copy(todayCard = SummaryCardPreset.RangeBar),
    TodayFixtures.weeklyGoals,
)

/** A daily goal: the target is the goal's own band, and only the chip says so. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TodayGoalDailyPreview() = TodayWithGoals(GoalFixtures.settings(GoalFixtures.lose), GoalFixtures.today(GoalFixtures.lose), usual = null)

/** Wrapping up a bit over under a weekly average, beside a meals goal with its streak on. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayCloseGoalWeeklyPreview() = PreviewContent {
    DayCloseScreen(
        state = TodayFixtures.dayCloseState(
            full,
            settings = GoalFixtures.settings(GoalFixtures.loseWeekly, GoalFixtures.meals).copy(goalStreakEnabled = true),
            goals = GoalFixtures.wrapUp(GoalFixtures.loseWeekly, GoalFixtures.meals, streaks = true, eatenKcal = 1950.0),
        ),
        onAction = {},
    )
}

/** A big day against a daily goal: the same goal tomorrow, nothing to make up. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DayCloseGoalBigDayPreview() = PreviewContent {
    DayCloseScreen(
        state = TodayFixtures.dayCloseState(
            full,
            bigDay = true,
            settings = GoalFixtures.settings(GoalFixtures.lose),
            goals = GoalFixtures.wrapUp(GoalFixtures.lose, eatenKcal = 2700.0),
        ),
        onAction = {},
    )
}
