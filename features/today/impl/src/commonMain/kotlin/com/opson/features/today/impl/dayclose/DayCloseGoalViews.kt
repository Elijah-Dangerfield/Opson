package com.opson.features.today.impl.dayclose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.opson.features.today.impl.text
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.diary.ui.TodayCard
import com.opson.libraries.goals.GoalOutcome
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientPhrase
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.goal_wrap_a_bit_over
import opson.libraries.resources.generated.resources.goal_wrap_a_bit_over_week
import opson.libraries.resources.generated.resources.goal_wrap_a_bit_under
import opson.libraries.resources.generated.resources.goal_wrap_a_bit_under_week
import opson.libraries.resources.generated.resources.goal_wrap_big_day
import opson.libraries.resources.generated.resources.goal_wrap_big_day_week
import opson.libraries.resources.generated.resources.goal_wrap_burn_met
import opson.libraries.resources.generated.resources.goal_wrap_burn_short
import opson.libraries.resources.generated.resources.goal_wrap_meals_met
import opson.libraries.resources.generated.resources.goal_wrap_meals_short
import opson.libraries.resources.generated.resources.goal_wrap_on_plan
import opson.libraries.resources.generated.resources.goal_wrap_steps_met
import opson.libraries.resources.generated.resources.goal_wrap_streak
import opson.libraries.resources.generated.resources.goal_wrap_title
import opson.libraries.resources.generated.resources.goal_wrap_track
import opson.libraries.resources.generated.resources.goal_wrap_week_ease
import opson.libraries.resources.generated.resources.goal_wrap_week_on_track
import opson.libraries.resources.generated.resources.goal_wrap_well_under
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** "Your goal", then each goal's lines; a goal's name only when there is more than one. */
@Composable
internal fun GoalWrapCardView(card: GoalWrapCard) {
    TodayCard(contentPadding = PaddingValues(AppTheme.spacing.page), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        SectionLabel(stringResource(Res.string.goal_wrap_title))
        card.rows.forEach { row ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (card.rows.size > 1) Text(text = stringResource(row.label.text), typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
                row.lines.forEachIndexed { index, line -> WrapLine(line, primary = index == 0) }
                row.streak?.let { days ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(icon = AppIcons.AutoAwesome, size = 18.dp, color = AppTheme.colors.accent)
                        Text(text = pluralStringResource(Res.plurals.goal_wrap_streak, days, days), typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun WrapLine(line: GoalWrapLine, primary: Boolean) {
    val typography = if (primary) AppTheme.type.bodyStrong else AppTheme.type.secondary.Tabular
    val color = if (primary) AppTheme.colors.ink else AppTheme.colors.inkSoft
    when (line) {
        is GoalWrapLine.Calories -> Text(text = stringResource(line.text()), typography = typography, color = color)
        GoalWrapLine.WeekOnTrack -> Text(text = stringResource(Res.string.goal_wrap_week_on_track), typography = typography, color = color)
        is GoalWrapLine.WeekEase -> NutrientPhrase(Metric.Calories, line.kcal, phrase = { stringResource(Res.string.goal_wrap_week_ease, it) }, typography = typography, color = color)
        GoalWrapLine.ShowedUp -> Text(text = stringResource(Res.string.goal_wrap_track), typography = typography, color = color)
        is GoalWrapLine.Meals -> Text(
            text = stringResource(if (line.met) Res.string.goal_wrap_meals_met else Res.string.goal_wrap_meals_short, line.done, line.goal),
            typography = typography,
            color = color,
        )
        is GoalWrapLine.Burn -> {
            val burned = line.burned
            val goal = line.goal
            // Both amounts arrive shown in full: the builder leaves the line out otherwise.
            val text = if (line.met || burned == null || goal == null) {
                stringResource(Res.string.goal_wrap_burn_met)
            } else {
                stringResource(Res.string.goal_wrap_burn_short, burned.text, goal.text)
            }
            Text(text = text, typography = typography, color = color)
        }
        GoalWrapLine.StepsMet -> Text(text = stringResource(Res.string.goal_wrap_steps_met), typography = typography, color = color)
    }
}

/** The sentence for a calorie goal's outcome. The builder never hands over an uncounted or count-goal outcome. */
private fun GoalWrapLine.Calories.text(): StringResource = when (outcome) {
    GoalOutcome.ABitOver -> if (weekly && weekOnTrack) Res.string.goal_wrap_a_bit_over_week else Res.string.goal_wrap_a_bit_over
    GoalOutcome.ABitUnder -> if (weekly && weekOnTrack) Res.string.goal_wrap_a_bit_under_week else Res.string.goal_wrap_a_bit_under
    GoalOutcome.BigDay -> if (weekly) Res.string.goal_wrap_big_day_week else Res.string.goal_wrap_big_day
    GoalOutcome.WellUnder -> Res.string.goal_wrap_well_under
    GoalOutcome.OnPlan, GoalOutcome.NotThisTime, GoalOutcome.NotCounted -> Res.string.goal_wrap_on_plan
}
