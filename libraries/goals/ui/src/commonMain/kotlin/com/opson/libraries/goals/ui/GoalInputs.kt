package com.opson.libraries.goals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.nutrition.GoalAveraging
import com.opson.libraries.nutrition.GoalKind
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.ChoiceRow
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.BasicTextField
import com.opson.libraries.ui.components.text.NumberInput
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.system.minTouchTarget
import com.opson.system.typography.TypographyResource
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.goal_averaging_daily
import opson.libraries.resources.generated.resources.goal_averaging_daily_body
import opson.libraries.resources.generated.resources.goal_averaging_title
import opson.libraries.resources.generated.resources.goal_averaging_weekly
import opson.libraries.resources.generated.resources.goal_averaging_weekly_body
import opson.libraries.resources.generated.resources.goal_averaging_weekly_caps
import opson.libraries.resources.generated.resources.goal_band_option
import opson.libraries.resources.generated.resources.goal_band_title
import opson.libraries.resources.generated.resources.goal_kind_balance
import opson.libraries.resources.generated.resources.goal_kind_balance_line
import opson.libraries.resources.generated.resources.goal_kind_burn
import opson.libraries.resources.generated.resources.goal_kind_burn_line
import opson.libraries.resources.generated.resources.goal_kind_meals
import opson.libraries.resources.generated.resources.goal_kind_meals_line
import opson.libraries.resources.generated.resources.goal_kind_steps
import opson.libraries.resources.generated.resources.goal_kind_steps_line
import opson.libraries.resources.generated.resources.goal_kind_track
import opson.libraries.resources.generated.resources.goal_kind_track_line
import opson.libraries.resources.generated.resources.goal_kind_weight
import opson.libraries.resources.generated.resources.goal_kind_weight_line
import opson.libraries.resources.generated.resources.goal_lower
import opson.libraries.resources.generated.resources.goal_raise
import opson.libraries.resources.generated.resources.targets_goal_band
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The pieces a goal is set with, for every type the engine has (docs/plans/goals-v2.md, "Goal
 * types"). Settings' goal editor builds its page from them, and onboarding's goal question can
 * too, so a goal is set the same way wherever it is set. Each takes plain values and callbacks;
 * the caller's view model decides what a step means and what a typed number may be.
 */

/** A goal type's name ("Weight") and what it does, in one line. */
val GoalKind.title: StringResource
    get() = when (this) {
        GoalKind.Weight -> Res.string.goal_kind_weight
        GoalKind.EnergyBalance -> Res.string.goal_kind_balance
        GoalKind.Burn -> Res.string.goal_kind_burn
        GoalKind.Steps -> Res.string.goal_kind_steps
        GoalKind.TrackEveryDay -> Res.string.goal_kind_track
        GoalKind.Meals, GoalKind.Unknown -> Res.string.goal_kind_meals
    }

val GoalKind.line: StringResource
    get() = when (this) {
        GoalKind.Weight -> Res.string.goal_kind_weight_line
        GoalKind.EnergyBalance -> Res.string.goal_kind_balance_line
        GoalKind.Burn -> Res.string.goal_kind_burn_line
        GoalKind.Steps -> Res.string.goal_kind_steps_line
        GoalKind.TrackEveryDay -> Res.string.goal_kind_track_line
        GoalKind.Meals, GoalKind.Unknown -> Res.string.goal_kind_meals_line
    }

/** The types on offer ([com.opson.libraries.nutrition.GoalCatalog.offered]), one row each. */
@Composable
fun GoalKindChoices(kinds: List<GoalKind>, selected: GoalKind?, onPick: (GoalKind) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        kinds.forEach { kind ->
            ChoiceRow(
                label = stringResource(kind.title),
                detail = stringResource(kind.line),
                selected = kind == selected,
                onClick = { onPick(kind) },
                role = Role.RadioButton,
            )
        }
    }
}

/**
 * Daily or Weekly average, and what each means in plain words. The weekly line says the caps
 * gently: a day under eases the rest of the week, a big day asks only a little of it, and nothing
 * carries into the next. Offered only where calories and energy out both show
 * ([com.opson.libraries.nutrition.GoalCatalog.weeklyOffered]).
 */
@Composable
fun AveragingChoice(selected: GoalAveraging, onPick: (GoalAveraging) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(Res.string.goal_averaging_title), typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
        SegmentedControl(
            options = GoalAveraging.entries,
            selected = selected,
            onSelect = onPick,
            label = { stringResource(if (it == GoalAveraging.Weekly) Res.string.goal_averaging_weekly else Res.string.goal_averaging_daily) },
        )
        if (selected == GoalAveraging.Weekly) {
            Text(text = stringResource(Res.string.goal_averaging_weekly_body), typography = AppTheme.type.secondary, color = AppTheme.colors.ink)
            Text(text = stringResource(Res.string.goal_averaging_weekly_caps), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        } else {
            Text(text = stringResource(Res.string.goal_averaging_daily_body), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
    }
}

/**
 * How wide "on track" is either side of the target: [options] in kcal ([com.opson.libraries.nutrition.Goal.TOLERANCES_KCAL]),
 * and [low] to [high], what the picked one comes to, already through the policy (null where
 * calories don't show).
 */
@Composable
fun BandChoice(options: List<Int>, selected: Int, onPick: (Int) -> Unit, low: String?, high: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(Res.string.goal_band_title), typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
        SegmentedControl(options = options, selected = selected, onSelect = onPick, label = { stringResource(Res.string.goal_band_option, it) })
        if (low != null && high != null) {
            Text(text = stringResource(Res.string.targets_goal_band, low, high), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
    }
}

/**
 * A number typed where it shows: digits only, centred, Done closing the keyboard. What it holds
 * is the caller's (a draft while typing, else the saved number); [onValueChange] gets the
 * sanitized text, and [onDone] the keyboard's Done. [label] names it for a screen reader.
 */
@Composable
fun InlineNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    typography: TypographyResource = AppTheme.type.h1,
    onDone: () -> Unit = {},
    allowDecimal: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(NumberInput.sanitize(it, allowDecimal)) },
        typographyToken = typography,
        color = AppTheme.colors.ink.color,
        textAlign = TextAlign.Center,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.semantics { contentDescription = label },
    )
}

/**
 * − typed number +: a goal's amount, stepped or typed. [unit] sits after the number ("kcal a day",
 * "steps"); [lowerLabel] and [raiseLabel] name the steps for a screen reader. Steps that can't go
 * further dim but stay focusable, as the app's steppers do.
 */
@Composable
fun TypedStepper(
    text: String,
    onType: (String) -> Unit,
    onStep: (up: Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    canLower: Boolean = true,
    canRaise: Boolean = true,
    onDone: () -> Unit = {},
    allowDecimal: Boolean = false,
    fieldModifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepButton(AppIcons.Remove, stringResource(Res.string.goal_lower, label), canLower) { onStep(false) }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            InlineNumberField(
                value = text,
                onValueChange = onType,
                label = label,
                onDone = onDone,
                allowDecimal = allowDecimal,
                modifier = Modifier.fillMaxWidth().then(fieldModifier),
            )
            unit?.let { Text(text = it, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted) }
        }
        StepButton(AppIcons.Add, stringResource(Res.string.goal_raise, label), canRaise) { onStep(true) }
    }
}

@Composable
private fun StepButton(icon: com.opson.libraries.ui.components.icon.IconResource, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .minTouchTarget(StepSize)
            .size(StepSize)
            .clip(AppTheme.shapes.input)
            .background(AppTheme.colors.soft)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .bounceClick(enabled = enabled, scaleDown = 0.92f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon = icon, size = 26.dp, color = if (enabled) AppTheme.colors.ink else AppTheme.colors.line)
    }
}

/** A soft card the goal screens group a goal's parts in. */
@Composable
fun GoalPanel(modifier: Modifier = Modifier, color: ColorResource = AppTheme.colors.surface, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.inputLarge)
            .background(color)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

private val StepSize: Dp = 52.dp
