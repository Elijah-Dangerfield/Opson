package com.opson.features.settings.impl.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SectionLabel
import com.opson.features.settings.impl.SettingsHeader
import com.opson.features.settings.impl.SettingsPage
import com.opson.libraries.goals.ui.AveragingChoice
import com.opson.libraries.goals.ui.BandChoice
import com.opson.libraries.goals.ui.GoalPanel
import com.opson.libraries.goals.ui.GoalReveal
import com.opson.libraries.goals.ui.GoalSetTap
import com.opson.libraries.goals.ui.GoalKindChoices
import com.opson.libraries.goals.ui.ProjectionChart
import com.opson.libraries.goals.ui.ProjectionForecastLines
import com.opson.libraries.goals.ui.TypedStepper
import com.opson.libraries.goals.ui.line
import com.opson.libraries.goals.ui.title
import com.opson.libraries.nutrition.BurnMeasure
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Goal
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GoalKind
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.nutrition.ui.NutrientPhrase
import com.opson.libraries.nutrition.ui.PaceInfoDialog
import com.opson.libraries.nutrition.ui.PaceNote
import com.opson.libraries.ui.components.ChevronLink
import com.opson.libraries.ui.components.ChoiceRow
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.Stepper
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.goal_edit_add_details
import opson.libraries.resources.generated.resources.goal_edit_back
import opson.libraries.resources.generated.resources.goal_edit_balance_label
import opson.libraries.resources.generated.resources.goal_edit_balance_over
import opson.libraries.resources.generated.resources.goal_edit_balance_over_line
import opson.libraries.resources.generated.resources.goal_edit_balance_under
import opson.libraries.resources.generated.resources.goal_edit_balance_under_line
import opson.libraries.resources.generated.resources.goal_edit_burn_active
import opson.libraries.resources.generated.resources.goal_edit_burn_active_line
import opson.libraries.resources.generated.resources.goal_edit_burn_label
import opson.libraries.resources.generated.resources.goal_edit_burn_total
import opson.libraries.resources.generated.resources.goal_edit_burn_total_line
import opson.libraries.resources.generated.resources.goal_edit_kcal_a_day
import opson.libraries.resources.generated.resources.goal_edit_meals_label
import opson.libraries.resources.generated.resources.goal_edit_meals_note
import opson.libraries.resources.generated.resources.goal_edit_meals_unit
import opson.libraries.resources.generated.resources.goal_edit_needs_details
import opson.libraries.resources.generated.resources.goal_edit_needs_weight
import opson.libraries.resources.generated.resources.goal_edit_pace_label
import opson.libraries.resources.generated.resources.goal_edit_pick
import opson.libraries.resources.generated.resources.goal_edit_pick_body
import opson.libraries.resources.generated.resources.goal_edit_projection
import opson.libraries.resources.generated.resources.goal_edit_replaces_balance
import opson.libraries.resources.generated.resources.goal_edit_replaces_weight
import opson.libraries.resources.generated.resources.goal_edit_save
import opson.libraries.resources.generated.resources.goal_edit_steps_label
import opson.libraries.resources.generated.resources.goal_edit_steps_note
import opson.libraries.resources.generated.resources.goal_edit_steps_unit
import opson.libraries.resources.generated.resources.goal_edit_target_title
import opson.libraries.resources.generated.resources.goal_edit_title_change
import opson.libraries.resources.generated.resources.goal_edit_title_new
import opson.libraries.resources.generated.resources.goal_edit_track_body
import opson.libraries.resources.generated.resources.goal_reveal_done
import opson.libraries.resources.generated.resources.goal_reveal_steady
import opson.libraries.resources.generated.resources.goal_reveal_target
import opson.libraries.resources.generated.resources.goal_reveal_title
import opson.libraries.resources.generated.resources.goal_reveal_waiting
import opson.libraries.resources.generated.resources.plan_goal_current
import opson.libraries.resources.generated.resources.plan_goal_gain
import opson.libraries.resources.generated.resources.plan_goal_hint_above
import opson.libraries.resources.generated.resources.plan_goal_hint_below
import opson.libraries.resources.generated.resources.plan_goal_lose
import opson.libraries.resources.generated.resources.plan_goal_low_note
import opson.libraries.resources.generated.resources.plan_goal_maintain
import opson.libraries.resources.generated.resources.plan_goal_target
import opson.libraries.resources.generated.resources.plan_rate_fastest
import opson.libraries.resources.generated.resources.plan_rate_gain
import opson.libraries.resources.generated.resources.plan_rate_kcal_gain
import opson.libraries.resources.generated.resources.plan_rate_kcal_lose
import opson.libraries.resources.generated.resources.plan_rate_lose
import opson.libraries.resources.generated.resources.plan_rate_none
import opson.libraries.resources.generated.resources.settings_loading_cd
import opson.libraries.resources.generated.resources.settings_look_weight_kg
import opson.libraries.resources.generated.resources.settings_look_weight_lb
import opson.libraries.resources.generated.resources.settings_targets_low_note
import opson.libraries.resources.generated.resources.settings_targets_support_link
import opson.libraries.resources.generated.resources.targets_goal_band
import opson.libraries.resources.generated.resources.targets_goal_target
import org.jetbrains.compose.resources.stringResource

/**
 * Set a goal or change one: the type (for a new goal), the type's own answers, how days count and
 * the band where a calorie target follows, the target it comes to and where it leads. Saving turns
 * the page into the reveal.
 */
@Composable
fun GoalEditScreen(state: GoalEditState, onAction: (GoalEditAction) -> Unit, modifier: Modifier = Modifier) {
    val view = state.view
    val saved = state.saved
    SettingsPage(modifier = modifier) {
        SettingsHeader(
            title = stringResource(if (view?.editing == true) Res.string.goal_edit_title_change else Res.string.goal_edit_title_new),
            backLabel = stringResource(Res.string.goal_edit_back),
            onBack = { onAction(GoalEditAction.BackTapped) },
        )
        when {
            saved != null -> Revealed(saved, onAction)
            !state.loaded || view == null -> DelayedLoadingIndicator(contentDescription = stringResource(Res.string.settings_loading_cd))
            view.kind == null -> Kinds(view, onAction)
            else -> Form(view, state.supportLink, onAction)
        }
        state.paceInfo?.let { info ->
            PaceInfoDialog(
                limitText = (info.limit as? Display.Shown)?.text,
                estimated = info.estimated,
                canWorkOut = info.canWorkOut,
                onDismiss = { onAction(GoalEditAction.PaceInfoDismissed) },
            )
        }
    }
}

@Composable
private fun Kinds(view: GoalEditView, onAction: (GoalEditAction) -> Unit) {
    Text(text = stringResource(Res.string.goal_edit_pick), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
    Text(text = stringResource(Res.string.goal_edit_pick_body), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
    GoalKindChoices(kinds = view.kinds, selected = null, onPick = { onAction(GoalEditAction.Edit(GoalEdit.KindPicked(it))) })
}

@Composable
private fun ColumnScope.Form(view: GoalEditView, supportLink: Boolean, onAction: (GoalEditAction) -> Unit) {
    val kind = view.kind ?: return
    val edit = { change: GoalEdit -> onAction(GoalEditAction.Edit(change)) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(kind.title), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
        Text(text = stringResource(kind.line), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
    }
    view.weight?.let { WeightForm(it, view, supportLink, edit, onAction) }
    view.balance?.let { BalanceForm(it, supportLink, edit, onAction) }
    view.burn?.let { burn ->
        SegmentedControl(
            options = BurnMeasure.entries,
            selected = burn.measure,
            onSelect = { edit(GoalEdit.MeasurePicked(it)) },
            label = { stringResource(if (it == BurnMeasure.Total) Res.string.goal_edit_burn_total else Res.string.goal_edit_burn_active) },
        )
        Note(stringResource(if (burn.measure == BurnMeasure.Total) Res.string.goal_edit_burn_total_line else Res.string.goal_edit_burn_active_line))
        GoalPanel {
            TypedStepper(
                text = burn.amount.text,
                onType = { edit(GoalEdit.AmountTyped(it)) },
                onStep = { edit(GoalEdit.AmountStepped(it)) },
                label = stringResource(Res.string.goal_edit_burn_label),
                unit = stringResource(Res.string.goal_edit_kcal_a_day),
                canLower = burn.amount.canLower,
                canRaise = burn.amount.canRaise,
            )
        }
    }
    view.steps?.let { steps ->
        GoalPanel {
            TypedStepper(
                text = steps.text,
                onType = { edit(GoalEdit.AmountTyped(it)) },
                onStep = { edit(GoalEdit.AmountStepped(it)) },
                label = stringResource(Res.string.goal_edit_steps_label),
                unit = stringResource(Res.string.goal_edit_steps_unit),
                canLower = steps.canLower,
                canRaise = steps.canRaise,
            )
        }
        Note(stringResource(Res.string.goal_edit_steps_note))
    }
    view.meals?.let { meals ->
        GoalPanel {
            TypedStepper(
                text = meals.text,
                onType = { edit(GoalEdit.AmountTyped(it)) },
                onStep = { edit(GoalEdit.AmountStepped(it)) },
                label = stringResource(Res.string.goal_edit_meals_label),
                unit = stringResource(Res.string.goal_edit_meals_unit),
                canLower = meals.canLower,
                canRaise = meals.canRaise,
            )
        }
        Note(stringResource(Res.string.goal_edit_meals_note))
    }
    if (kind == GoalKind.TrackEveryDay) Text(text = stringResource(Res.string.goal_edit_track_body), typography = AppTheme.type.body, color = AppTheme.colors.ink)
    if (view.needsWeight || view.needsDetails) {
        GoalPanel(color = AppTheme.colors.soft) {
            Text(
                text = stringResource(if (view.needsWeight) Res.string.goal_edit_needs_weight else Res.string.goal_edit_needs_details),
                typography = AppTheme.type.body,
                color = AppTheme.colors.ink,
            )
            ChevronLink(text = stringResource(Res.string.goal_edit_add_details), onClick = { onAction(GoalEditAction.DetailsTapped) })
        }
    }
    TargetPanel(view, supportLink, onAction)
    view.averaging?.let { AveragingChoice(selected = it, onPick = { picked -> edit(GoalEdit.AveragingPicked(picked)) }) }
    view.toleranceKcal?.let { tolerance ->
        BandChoice(
            options = Goal.TOLERANCES_KCAL,
            selected = tolerance,
            onPick = { edit(GoalEdit.BandPicked(it)) },
            low = (view.low as? Display.Shown)?.text,
            high = (view.high as? Display.Shown)?.text,
        )
    }
    view.chart?.let { chart ->
        SectionLabel(stringResource(Res.string.goal_edit_projection))
        GoalPanel {
            ProjectionChart(chart)
            ProjectionForecastLines(chart)
        }
    }
    view.replaces?.let { replaced ->
        Note(stringResource(if (replaced == GoalKind.EnergyBalance) Res.string.goal_edit_replaces_balance else Res.string.goal_edit_replaces_weight))
    }
    FlatButton(
        text = stringResource(Res.string.goal_edit_save),
        onClick = { onAction(GoalEditAction.SaveTapped) },
        tone = FlatButtonTone.Accent,
        centered = true,
        enabled = view.canSave,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun WeightForm(
    weight: WeightPart,
    view: GoalEditView,
    supportLink: Boolean,
    edit: (GoalEdit) -> Unit,
    onAction: (GoalEditAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        listOf(GoalDirection.Lose to Res.string.plan_goal_lose, GoalDirection.Maintain to Res.string.plan_goal_maintain, GoalDirection.Gain to Res.string.plan_goal_gain)
            .forEach { (direction, label) ->
                ChoiceRow(
                    label = stringResource(label),
                    selected = weight.direction == direction,
                    onClick = { edit(GoalEdit.DirectionPicked(direction)) },
                    role = Role.RadioButton,
                )
            }
    }
    if (view.needsWeight) return
    (weight.current as? Display.Shown)?.let { Note(stringResource(Res.string.plan_goal_current, it.text)) }
    if (!weight.direction.changesWeight) return
    NumberField(
        value = weight.targetText,
        onValueChange = { edit(GoalEdit.TargetTyped(it)) },
        label = stringResource(Res.string.plan_goal_target),
        unit = stringResource(if (weight.unit == WeightUnit.Lb) Res.string.settings_look_weight_lb else Res.string.settings_look_weight_kg),
        allowDecimal = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (weight.wrongWay) {
        Note(stringResource(if (weight.direction == GoalDirection.Lose) Res.string.plan_goal_hint_above else Res.string.plan_goal_hint_below))
    }
    if (weight.belowHealthy) {
        Text(text = stringResource(Res.string.plan_goal_low_note), typography = AppTheme.type.secondary, color = AppTheme.colors.ink)
        if (supportLink) ChevronLink(text = stringResource(Res.string.settings_targets_support_link), onClick = { onAction(GoalEditAction.FindSupportTapped) })
    }
    if (weight.noRoom) {
        Note(stringResource(Res.string.plan_rate_none))
        return
    }
    val pace = weight.paceText ?: return
    SectionLabel(stringResource(Res.string.goal_edit_pace_label))
    GoalPanel {
        Text(
            text = stringResource(if (weight.direction == GoalDirection.Gain) Res.string.plan_rate_gain else Res.string.plan_rate_lose),
            typography = AppTheme.type.secondaryStrong,
            color = AppTheme.colors.muted,
        )
        Stepper(
            amount = pace,
            onDecrease = { edit(GoalEdit.PaceStepped(faster = false)) },
            onIncrease = { edit(GoalEdit.PaceStepped(faster = true)) },
            canDecrease = weight.canSlower,
            canIncrease = weight.canFaster,
        )
        NutrientPhrase(
            metric = Metric.Calories,
            display = weight.dailyKcal,
            phrase = { stringResource(if (weight.direction == GoalDirection.Gain) Res.string.plan_rate_kcal_gain else Res.string.plan_rate_kcal_lose, it) },
        )
        weight.pace?.let { assessment ->
            PaceNote(
                assessment = assessment,
                onInfo = { onAction(GoalEditAction.PaceInfoTapped) },
                onFindSupport = { onAction(GoalEditAction.FindSupportTapped) }.takeIf { assessment.needsCare && supportLink },
            )
        }
        if (!weight.canFaster) Note(stringResource(Res.string.plan_rate_fastest))
    }
}

@Composable
private fun BalanceForm(balance: BalancePart, supportLink: Boolean, edit: (GoalEdit) -> Unit, onAction: (GoalEditAction) -> Unit) {
    SegmentedControl(
        options = listOf(true, false),
        selected = balance.under,
        onSelect = { edit(GoalEdit.SidePicked(it)) },
        label = { stringResource(if (it) Res.string.goal_edit_balance_under else Res.string.goal_edit_balance_over) },
    )
    Note(stringResource(if (balance.under) Res.string.goal_edit_balance_under_line else Res.string.goal_edit_balance_over_line))
    GoalPanel {
        TypedStepper(
            text = balance.amount.text,
            onType = { edit(GoalEdit.AmountTyped(it)) },
            onStep = { edit(GoalEdit.AmountStepped(it)) },
            label = stringResource(Res.string.goal_edit_balance_label),
            unit = stringResource(Res.string.goal_edit_kcal_a_day),
            canLower = balance.amount.canLower,
            canRaise = balance.amount.canRaise,
        )
        balance.pace?.let { assessment ->
            PaceNote(
                assessment = assessment,
                onInfo = { onAction(GoalEditAction.PaceInfoTapped) },
                onFindSupport = { onAction(GoalEditAction.FindSupportTapped) }.takeIf { assessment.needsCare && supportLink },
            )
        }
    }
}

/** The daily target the goal comes to, with its band, where calories show. */
@Composable
private fun TargetPanel(view: GoalEditView, supportLink: Boolean, onAction: (GoalEditAction) -> Unit) {
    val target = view.target as? Display.Shown ?: return
    GoalPanel(color = AppTheme.colors.accentTint) {
        Text(text = stringResource(Res.string.goal_edit_target_title), typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
        NutrientPhrase(
            metric = Metric.Calories,
            display = target,
            phrase = { stringResource(Res.string.targets_goal_target, it) },
            typography = AppTheme.type.cardTitle,
            color = AppTheme.colors.ink,
        )
        val low = view.low as? Display.Shown
        val high = view.high as? Display.Shown
        if (low != null && high != null) Note(stringResource(Res.string.targets_goal_band, low.text, high.text))
        if (view.lowAim) {
            Text(text = stringResource(Res.string.settings_targets_low_note), typography = AppTheme.type.secondary, color = AppTheme.colors.ink)
            if (supportLink) ChevronLink(text = stringResource(Res.string.settings_targets_support_link), onClick = { onAction(GoalEditAction.FindSupportTapped) })
        }
    }
}

/** Saved: where the goal leads, drawn in, with the success tap; or, without a chart, the goal and its target. */
@Composable
private fun Revealed(saved: GoalSaved, onAction: (GoalEditAction) -> Unit) {
    Text(text = stringResource(Res.string.goal_reveal_title), typography = AppTheme.type.h1, color = AppTheme.colors.ink)
    Text(text = stringResource(saved.kind.title), typography = AppTheme.type.bodyBold, color = AppTheme.colors.muted)
    NutrientPhrase(
        metric = Metric.Calories,
        display = saved.target,
        phrase = { stringResource(Res.string.goal_reveal_target, it) },
        typography = AppTheme.type.body,
        color = AppTheme.colors.ink,
    )
    if (saved.waiting) Note(stringResource(Res.string.goal_reveal_waiting))
    saved.chart?.let { chart -> GoalPanel { GoalReveal(chart) } } ?: GoalSetTap()
    Note(stringResource(Res.string.goal_reveal_steady))
    FlatButton(
        text = stringResource(Res.string.goal_reveal_done),
        onClick = { onAction(GoalEditAction.DoneTapped) },
        tone = FlatButtonTone.Accent,
        centered = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(text = text, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
}
