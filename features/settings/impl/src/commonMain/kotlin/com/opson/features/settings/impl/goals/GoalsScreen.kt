package com.opson.features.settings.impl.goals

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SectionLabel
import com.opson.features.settings.impl.SettingsHeader
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsPreview
import com.opson.libraries.diary.GoalBook
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.goals.GoalFixtures
import com.opson.libraries.goals.GoalStatuses
import com.opson.libraries.nutrition.BaselineChange
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.GoalRecalculated
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.opson.features.settings.impl.targets.reason
import com.opson.libraries.goals.ui.GoalPanel
import com.opson.libraries.goals.ui.ProjectionChart
import com.opson.libraries.goals.ui.ProjectionForecastLines
import com.opson.libraries.goals.ui.title
import com.opson.libraries.nutrition.BurnMeasure
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GoalVerdict
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientPhrase
import com.opson.libraries.nutrition.ui.PaceInfoDialog
import com.opson.libraries.nutrition.ui.PaceNote
import com.opson.libraries.nutrition.ui.testTag
import com.opson.libraries.ui.components.ChevronLink
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.energy_info_cd
import opson.libraries.resources.generated.resources.goal_edit_back
import opson.libraries.resources.generated.resources.goal_edit_projection
import opson.libraries.resources.generated.resources.goals_add
import opson.libraries.resources.generated.resources.goals_burn_active
import opson.libraries.resources.generated.resources.goals_burn_total
import opson.libraries.resources.generated.resources.goals_change
import opson.libraries.resources.generated.resources.goals_day_ahead
import opson.libraries.resources.generated.resources.goals_day_met
import opson.libraries.resources.generated.resources.goals_day_near
import opson.libraries.resources.generated.resources.goals_day_not_counted
import opson.libraries.resources.generated.resources.goals_day_off
import opson.libraries.resources.generated.resources.goals_day_today
import opson.libraries.resources.generated.resources.goals_empty
import opson.libraries.resources.generated.resources.goals_history
import opson.libraries.resources.generated.resources.goals_history_kept
import opson.libraries.resources.generated.resources.goals_history_kept_plain
import opson.libraries.resources.generated.resources.goals_history_moved
import opson.libraries.resources.generated.resources.goals_history_moved_plain
import opson.libraries.resources.generated.resources.goals_meals
import opson.libraries.resources.generated.resources.goals_own
import opson.libraries.resources.generated.resources.goals_pause
import opson.libraries.resources.generated.resources.goals_paused
import opson.libraries.resources.generated.resources.goals_resume
import opson.libraries.resources.generated.resources.goals_sets_target
import opson.libraries.resources.generated.resources.goals_since
import opson.libraries.resources.generated.resources.goals_steps
import opson.libraries.resources.generated.resources.goals_title
import opson.libraries.resources.generated.resources.goals_to
import opson.libraries.resources.generated.resources.goals_track
import opson.libraries.resources.generated.resources.goals_typical_burn
import opson.libraries.resources.generated.resources.goals_waiting
import opson.libraries.resources.generated.resources.goals_week
import opson.libraries.resources.generated.resources.goals_weekly_today
import opson.libraries.resources.generated.resources.settings_loading_cd
import opson.libraries.resources.generated.resources.targets_goal_balance_over
import opson.libraries.resources.generated.resources.targets_goal_balance_under
import opson.libraries.resources.generated.resources.targets_goal_band
import opson.libraries.resources.generated.resources.targets_goal_gain
import opson.libraries.resources.generated.resources.targets_goal_keep
import opson.libraries.resources.generated.resources.targets_goal_lose
import opson.libraries.resources.generated.resources.targets_goal_target
import opson.libraries.resources.generated.resources.targets_goal_weekly
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Your goals: each one with what it asks, this week, where it leads and what changed its target. */
@Composable
fun GoalsScreen(state: GoalsState, onAction: (GoalsAction) -> Unit, modifier: Modifier = Modifier) {
    SettingsPage(modifier = modifier) {
        SettingsHeader(
            title = stringResource(Res.string.goals_title),
            backLabel = stringResource(Res.string.goal_edit_back),
            onBack = { onAction(GoalsAction.BackTapped) },
        )
        if (!state.loaded) {
            DelayedLoadingIndicator(contentDescription = stringResource(Res.string.settings_loading_cd))
            return@SettingsPage
        }
        if (state.goals.isEmpty()) Text(text = stringResource(Res.string.goals_empty), typography = AppTheme.type.body, color = AppTheme.colors.ink)
        state.goals.forEach { GoalCardView(it, state.supportLink, onAction) }
        if (state.canAdd) {
            FlatButton(
                text = stringResource(Res.string.goals_add),
                onClick = { onAction(GoalsAction.AddTapped) },
                tone = FlatButtonTone.Outlined,
                icon = AppIcons.Add,
                centered = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.paceInfo?.let { info ->
            PaceInfoDialog(
                limitText = (info.limit as? Display.Shown)?.text,
                estimated = info.estimated,
                canWorkOut = info.canWorkOut,
                onDismiss = { onAction(GoalsAction.PaceInfoDismissed) },
            )
        }
    }
}

@Composable
private fun GoalCardView(card: GoalCard, supportLink: Boolean, onAction: (GoalsAction) -> Unit) {
    GoalPanel {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(card.kind.title), typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
            AimLine(card.aim)
            Text(text = stringResource(Res.string.goals_since, card.since), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        when {
            card.paused -> Note(stringResource(Res.string.goals_paused))
            card.waiting -> Note(stringResource(Res.string.goals_waiting))
            card.own -> Note(stringResource(Res.string.goals_own))
            card.setsTarget -> TargetLines(card, onAction)
        }
        card.pace?.let { pace ->
            PaceNote(
                assessment = pace,
                onInfo = { onAction(GoalsAction.PaceInfoTapped) },
                onFindSupport = { onAction(GoalsAction.FindSupportTapped) }.takeIf { pace.needsCare && supportLink },
            )
        }
        if (card.week.isNotEmpty()) WeekStrip(card.week)
        card.chart?.let { chart ->
            Text(text = stringResource(Res.string.goal_edit_projection), typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
            ProjectionChart(chart)
            ProjectionForecastLines(chart)
        }
        if (card.history.isNotEmpty()) History(card.history)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlatButton(text = stringResource(Res.string.goals_change), onClick = { onAction(GoalsAction.EditTapped(card.id)) })
            if (card.paused) {
                FlatButton(text = stringResource(Res.string.goals_resume), onClick = { onAction(GoalsAction.ResumeTapped(card.id)) }, tone = FlatButtonTone.Outlined)
            } else {
                FlatButton(text = stringResource(Res.string.goals_pause), onClick = { onAction(GoalsAction.PauseTapped(card.id)) }, tone = FlatButtonTone.Outlined)
            }
        }
    }
}

@Composable
private fun AimLine(aim: GoalAim) {
    val style = AppTheme.type.cardTitle
    val ink = AppTheme.colors.ink
    when (aim) {
        is GoalAim.Weight -> {
            val pace = aim.paceText
            val text = when {
                aim.direction == GoalDirection.Lose && pace != null -> stringResource(Res.string.targets_goal_lose, pace)
                aim.direction == GoalDirection.Gain && pace != null -> stringResource(Res.string.targets_goal_gain, pace)
                else -> stringResource(Res.string.targets_goal_keep)
            }
            Text(text = text, typography = style, color = ink)
            (aim.goalWeight as? Display.Shown)?.let { Text(text = stringResource(Res.string.goals_to, it.text), typography = AppTheme.type.body, color = ink) }
        }
        is GoalAim.Balance -> NutrientPhrase(
            metric = Metric.Calories,
            display = aim.amount,
            phrase = { stringResource(if (aim.under) Res.string.targets_goal_balance_under else Res.string.targets_goal_balance_over, it) },
            typography = style,
            color = ink,
        )
        is GoalAim.Burn -> NutrientPhrase(
            metric = Metric.EnergyOut,
            display = aim.amount,
            phrase = { stringResource(if (aim.measure == BurnMeasure.Total) Res.string.goals_burn_total else Res.string.goals_burn_active, it) },
            typography = style,
            color = ink,
        )
        is GoalAim.Steps -> Text(text = stringResource(Res.string.goals_steps, aim.count), typography = style, color = ink)
        is GoalAim.Meals -> Text(text = pluralStringResource(Res.plurals.goals_meals, aim.count, aim.count), typography = style, color = ink)
        GoalAim.Track -> Text(text = stringResource(Res.string.goals_track), typography = style, color = ink)
    }
}

/** The goal's daily target and band, a weekly average's today, and the typical day behind it. */
@Composable
private fun TargetLines(card: GoalCard, onAction: (GoalsAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(Res.string.goals_sets_target), typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.accent)
        NutrientPhrase(
            metric = Metric.Calories,
            display = card.target,
            phrase = { stringResource(Res.string.targets_goal_target, it) },
            typography = AppTheme.type.bodyBold,
            color = AppTheme.colors.ink,
        )
        val low = card.low as? Display.Shown
        val high = card.high as? Display.Shown
        if (low != null && high != null) Note(stringResource(Res.string.targets_goal_band, low.text, high.text))
        if (card.weekly) Note(stringResource(Res.string.targets_goal_weekly))
        NutrientPhrase(metric = Metric.Calories, display = card.today, phrase = { stringResource(Res.string.goals_weekly_today, it) }, color = AppTheme.colors.ink)
        if (card.burn is Display.Shown) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NutrientPhrase(
                    metric = Metric.EnergyOut,
                    display = card.burn,
                    phrase = { stringResource(Res.string.goals_typical_burn, it) },
                    modifier = Modifier.weight(1f),
                )
                ChevronLink(text = stringResource(Res.string.energy_info_cd), onClick = { onAction(GoalsAction.BurnInfoTapped) })
            }
        }
    }
}

/** The week, a day a dot: filled on track, half close, a ring for today, faint for what's ahead. "Not this time" is never red. */
@Composable
private fun WeekStrip(days: List<WeekDot>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = stringResource(Res.string.goals_week), typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEach { dot ->
                val name = DateCopy.shortWeekday(dot.day)
                val description = stringResource(
                    when {
                        dot.today && dot.verdict == GoalVerdict.InProgress -> Res.string.goals_day_today
                        dot.verdict == GoalVerdict.Met -> Res.string.goals_day_met
                        dot.verdict == GoalVerdict.Near -> Res.string.goals_day_near
                        dot.verdict == GoalVerdict.Off -> Res.string.goals_day_off
                        dot.verdict == GoalVerdict.Future -> Res.string.goals_day_ahead
                        else -> Res.string.goals_day_not_counted
                    },
                    name,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                ) {
                    Dot(dot)
                    Text(
                        text = DateCopy.narrowWeekday(dot.day),
                        typography = AppTheme.type.secondary,
                        color = if (dot.today) AppTheme.colors.ink else AppTheme.colors.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun Dot(dot: WeekDot) {
    val accent = AppTheme.colors.accent
    val base = Modifier.size(DotSize).clip(CircleShape)
    when (dot.verdict) {
        GoalVerdict.Met -> Box(base.background(accent), contentAlignment = Alignment.Center) {
            Icon(icon = AppIcons.Check, size = 14.dp, color = AppTheme.colors.onAccent)
        }
        GoalVerdict.Near -> Box(base.background(AppTheme.colors.accentTint))
        GoalVerdict.InProgress -> Box(base.border(2.dp, accent.color, CircleShape))
        GoalVerdict.Off -> Box(base.background(AppTheme.colors.soft))
        GoalVerdict.Future -> Box(base.border(1.dp, AppTheme.colors.line.color, CircleShape))
        GoalVerdict.NotCounted -> Box(base, contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 10.dp, height = 2.dp).background(AppTheme.colors.line))
        }
    }
}

@Composable
private fun History(rows: List<HistoryRow>) {
    SectionLabel(stringResource(Res.string.goals_history), modifier = Modifier.padding(start = 0.dp))
    rows.forEach { row ->
        val old = row.old as? Display.Shown
        val new = row.new as? Display.Shown
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val line = when {
                row.accepted && old != null && new != null -> stringResource(Res.string.goals_history_moved, row.date, old.text, new.text)
                row.accepted -> stringResource(Res.string.goals_history_moved_plain, row.date)
                old != null && new != null -> stringResource(Res.string.goals_history_kept, row.date, old.text, new.text)
                else -> stringResource(Res.string.goals_history_kept_plain, row.date)
            }
            // A line with the targets in it carries the calorie tag, as Targets' goal card does, for the leak test.
            Text(
                text = line,
                typography = AppTheme.type.body,
                color = AppTheme.colors.ink,
                modifier = if (new != null) Modifier.testTag(Metric.Calories.testTag()) else Modifier,
            )
            row.reasons.firstOrNull()?.let { Note(stringResource(it.reason)) }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text = text, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
}

private val DotSize = 26.dp


// ---------- Previews ----------

@Composable
private fun GoalsPreview(settings: Settings, preset: PresetId = PresetId.Full, dark: Boolean = false, largeText: Boolean = false) {
    val numbers = settings.copy(numbers = NumbersSettings(preset = preset))
    val days = GoalStatuses.days(numbers, GoalFixtures.week, GoalFixtures.monday, GoalFixtures.monday.plus(DatePeriod(days = 6)), GoalFixtures.today, GoalFixtures.WEIGHT_KG)
    val inputs = GoalsInputs(
        settings = numbers,
        policy = SettingsFixtures.policy(preset),
        statuses = GoalStatuses.today(numbers, GoalFixtures.week, GoalFixtures.today, GoalFixtures.WEIGHT_KG),
        days = days,
        weightKg = GoalFixtures.WEIGHT_KG,
        today = GoalFixtures.today,
    )
    SettingsPreview(dark = dark, largeText = largeText) { GoalsScreen(GoalsStateBuilder.build(inputs, focus = null, supportLink = true), onAction = {}) }
}

private val goalsBase: Settings = SettingsFixtures.settings().copy(heightCm = 180.0, birthYear = 1996, bodySex = BodySex.Male)

/** A weight goal setting the target, with its week, projection and a recalculation taken; meals beside it. */
@Preview(heightDp = 2000)
@Composable
private fun GoalsWeightAndMealsPreview() {
    val answered = GoalFixtures.lose.recorded(GoalRecalculated(GoalFixtures.monday, setOf(BaselineChange.ActivitySource), 1980, 1930, accepted = true))
    GoalsPreview(GoalFixtures.settings(answered, GoalFixtures.meals, base = goalsBase))
}

/** Averaged over the week, after a big Tuesday: today's number, and the steps goal beside it. Dark. */
@Preview(heightDp = 2000)
@Composable
private fun GoalsWeeklyDarkPreview() = GoalsPreview(GoalFixtures.settings(GoalFixtures.loseWeekly, GoalFixtures.steps, base = goalsBase), dark = true)

/** An energy balance goal, its twelve weeks drawn, and a burn goal. */
@Preview(heightDp = 1800)
@Composable
private fun GoalsBalancePreview() = GoalsPreview(GoalFixtures.settings(GoalFixtures.balance, GoalFixtures.burn, base = goalsBase))

/** The weight goal paused. */
@Preview(heightDp = 1000)
@Composable
private fun GoalsPausedPreview() = GoalsPreview(GoalBook.pause(GoalFixtures.settings(base = goalsBase), GoalFixtures.lose.id))

/** No goals yet. */
@Preview
@Composable
private fun GoalsEmptyPreview() = GoalsPreview(goalsBase)

/** Macros with grams: the weight goal in words, no calories. */
@Preview(heightDp = 1600)
@Composable
private fun GoalsGramsPreview() = GoalsPreview(GoalFixtures.settings(base = goalsBase), preset = PresetId.MacrosWithGrams)

/** After "numbers have been hard": the gentle goals only. Large text. */
@Preview(heightDp = 1200)
@Composable
private fun GoalsGentleLargeTextPreview() =
    GoalsPreview(GoalFixtures.gentleSettings().copy(onboarding = OnboardingAnswers(hard = true)), largeText = true)
