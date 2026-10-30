package com.opson.features.settings.impl.targets

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SectionLabel
import com.opson.features.settings.impl.SettingsCard
import com.opson.features.settings.impl.SettingsDefaults
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsHeader
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.SettingsPreview
import com.opson.features.settings.impl.SettingsRow
import com.opson.features.settings.impl.energy.EnergyInfoButton
import com.opson.features.settings.impl.energy.EnergyInfoLink
import com.opson.features.settings.impl.energy.EnergyTopic
import com.opson.features.settings.impl.integrations.title
import com.opson.libraries.diary.MacroMode
import com.opson.libraries.goals.ui.InlineNumberField
import com.opson.libraries.goals.ui.ProjectionChart
import com.opson.libraries.goals.ui.ProjectionChartUi
import com.opson.libraries.goals.ui.ProjectionForecastLines
import com.opson.libraries.diary.ui.detail
import com.opson.libraries.diary.ui.label
import com.opson.libraries.health.HealthSource
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.MIN_GOAL_KCAL
import com.opson.libraries.nutrition.MacroPreset
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RangeEnd
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.ui.NutrientPhrase
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.testTag
import com.opson.libraries.nutrition.withPreset
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.ChevronLink
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.chip.PillChip
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.ToneIcon
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.radio.RadioDot
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.system.minTouchTarget
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.color.ToneColors
import com.opson.system.thenIf
import com.opson.system.typography.TypographyResource
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_back_more
import opson.libraries.resources.generated.resources.settings_loading_cd
import opson.libraries.resources.generated.resources.settings_targets_activity
import opson.libraries.resources.generated.resources.settings_targets_activity_connect
import opson.libraries.resources.generated.resources.everyday_activity_body
import opson.libraries.resources.generated.resources.everyday_activity_title
import opson.libraries.resources.generated.resources.settings_targets_add_details
import opson.libraries.resources.generated.resources.settings_targets_burn_based
import opson.libraries.resources.generated.resources.settings_targets_burn_estimated
import opson.libraries.resources.generated.resources.settings_targets_burn_needs_details
import opson.libraries.resources.generated.resources.settings_targets_burn_target
import opson.libraries.resources.generated.resources.settings_targets_burned_title
import opson.libraries.resources.generated.resources.settings_targets_ceiling_locked
import opson.libraries.resources.generated.resources.settings_targets_deficit_about
import opson.libraries.resources.generated.resources.settings_targets_deficit_cap_note
import opson.libraries.resources.generated.resources.settings_targets_deficit_less
import opson.libraries.resources.generated.resources.settings_targets_deficit_locked
import opson.libraries.resources.generated.resources.settings_targets_deficit_more
import opson.libraries.resources.generated.resources.settings_targets_deficit_info
import opson.libraries.resources.generated.resources.settings_targets_deficit_title
import opson.libraries.resources.generated.resources.settings_targets_deficit_zero_note
import opson.libraries.resources.generated.resources.settings_targets_disclosure
import opson.libraries.resources.generated.resources.settings_targets_fast_note
import opson.libraries.resources.generated.resources.settings_targets_goal_style
import opson.libraries.resources.generated.resources.settings_targets_grams
import opson.libraries.resources.generated.resources.settings_targets_grams_note
import opson.libraries.resources.generated.resources.settings_targets_grams_only_note
import opson.libraries.resources.generated.resources.settings_targets_group_add
import opson.libraries.resources.generated.resources.settings_targets_group_day
import opson.libraries.resources.generated.resources.settings_targets_group_empty
import opson.libraries.resources.generated.resources.settings_targets_group_every_day
import opson.libraries.resources.generated.resources.settings_targets_group_goal
import opson.libraries.resources.generated.resources.settings_targets_group_numbered
import opson.libraries.resources.generated.resources.settings_targets_group_only
import opson.libraries.resources.generated.resources.settings_targets_group_other_days
import opson.libraries.resources.generated.resources.settings_targets_group_range
import opson.libraries.resources.generated.resources.settings_targets_group_remove
import opson.libraries.resources.generated.resources.settings_targets_group_rest
import opson.libraries.resources.generated.resources.settings_targets_group_shape_deficit
import opson.libraries.resources.generated.resources.settings_targets_group_shape_exact
import opson.libraries.resources.generated.resources.settings_targets_group_shape_range
import opson.libraries.resources.generated.resources.settings_targets_group_shape_surplus
import opson.libraries.resources.generated.resources.settings_targets_group_waiting
import opson.libraries.resources.generated.resources.settings_targets_hidden_body
import opson.libraries.resources.generated.resources.settings_targets_hidden_link
import opson.libraries.resources.generated.resources.settings_targets_hidden_title
import opson.libraries.resources.generated.resources.settings_targets_high_end
import opson.libraries.resources.generated.resources.settings_targets_high_end_inline
import opson.libraries.resources.generated.resources.settings_targets_kcal_a_day
import opson.libraries.resources.generated.resources.settings_targets_locked
import opson.libraries.resources.generated.resources.settings_targets_low_end
import opson.libraries.resources.generated.resources.settings_targets_low_end_inline
import opson.libraries.resources.generated.resources.settings_targets_low_note
import opson.libraries.resources.generated.resources.settings_targets_lower_end
import opson.libraries.resources.generated.resources.settings_targets_macro_carbs
import opson.libraries.resources.generated.resources.settings_targets_macro_fat
import opson.libraries.resources.generated.resources.settings_targets_macro_less
import opson.libraries.resources.generated.resources.settings_targets_macro_more
import opson.libraries.resources.generated.resources.settings_targets_macro_name_carbs
import opson.libraries.resources.generated.resources.settings_targets_macro_name_fat
import opson.libraries.resources.generated.resources.settings_targets_macro_name_protein
import opson.libraries.resources.generated.resources.settings_targets_macro_protein
import opson.libraries.resources.generated.resources.settings_targets_macro_split
import opson.libraries.resources.generated.resources.settings_targets_macros_done
import opson.libraries.resources.generated.resources.settings_targets_macros_edit
import opson.libraries.resources.generated.resources.settings_targets_macros_fit
import opson.libraries.resources.generated.resources.settings_targets_macros_goal_number
import opson.libraries.resources.generated.resources.settings_targets_macros_goal_range
import opson.libraries.resources.generated.resources.settings_targets_macros_goal_today
import opson.libraries.resources.generated.resources.settings_targets_macros_over
import opson.libraries.resources.generated.resources.settings_targets_macros_title
import opson.libraries.resources.generated.resources.settings_targets_macros_under
import opson.libraries.resources.generated.resources.settings_targets_macros_use
import opson.libraries.resources.generated.resources.settings_targets_min_note
import opson.libraries.resources.generated.resources.settings_targets_number_field
import opson.libraries.resources.generated.resources.settings_targets_number_lower
import opson.libraries.resources.generated.resources.settings_targets_number_raise
import opson.libraries.resources.generated.resources.settings_targets_number_too_high
import opson.libraries.resources.generated.resources.settings_targets_number_too_low
import opson.libraries.resources.generated.resources.settings_targets_preset_balanced
import opson.libraries.resources.generated.resources.settings_targets_preset_custom
import opson.libraries.resources.generated.resources.settings_targets_preset_higher_protein
import opson.libraries.resources.generated.resources.settings_targets_preset_lower_carb
import opson.libraries.resources.generated.resources.settings_targets_raise_end
import opson.libraries.resources.generated.resources.settings_targets_ratios
import opson.libraries.resources.generated.resources.settings_targets_ratios_note
import opson.libraries.resources.generated.resources.settings_targets_resting
import opson.libraries.resources.generated.resources.settings_targets_resting_calculated
import opson.libraries.resources.generated.resources.settings_targets_resting_own
import opson.libraries.resources.generated.resources.settings_targets_resting_unknown
import opson.libraries.resources.generated.resources.settings_targets_start_from_burn
import opson.libraries.resources.generated.resources.settings_targets_start_from_burn_note
import opson.libraries.resources.generated.resources.settings_targets_style_by_day
import opson.libraries.resources.generated.resources.settings_targets_style_deficit
import opson.libraries.resources.generated.resources.settings_targets_style_deficit_line
import opson.libraries.resources.generated.resources.settings_targets_style_exact
import opson.libraries.resources.generated.resources.settings_targets_style_range
import opson.libraries.resources.generated.resources.settings_targets_style_surplus
import opson.libraries.resources.generated.resources.settings_targets_style_surplus_line
import opson.libraries.resources.generated.resources.settings_targets_support_link
import opson.libraries.resources.generated.resources.settings_targets_surplus_cap
import opson.libraries.resources.generated.resources.settings_targets_surplus_from_target
import opson.libraries.resources.generated.resources.settings_targets_surplus_less
import opson.libraries.resources.generated.resources.settings_targets_surplus_locked
import opson.libraries.resources.generated.resources.settings_targets_surplus_more
import opson.libraries.resources.generated.resources.settings_targets_surplus_info
import opson.libraries.resources.generated.resources.settings_targets_surplus_title
import opson.libraries.resources.generated.resources.settings_targets_title
import opson.libraries.resources.generated.resources.settings_micros_row_line
import opson.libraries.resources.generated.resources.settings_micros_title
import opson.libraries.resources.generated.resources.settings_targets_unit_g
import opson.libraries.resources.generated.resources.settings_targets_unit_percent
import opson.libraries.resources.generated.resources.settings_targets_week
import opson.libraries.resources.generated.resources.settings_targets_macro_range
import opson.libraries.resources.generated.resources.settings_targets_macro_range_note
import opson.libraries.resources.generated.resources.settings_targets_type_amount
import opson.libraries.resources.generated.resources.targets_projection_body
import opson.libraries.resources.generated.resources.targets_projection_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun TargetsScreen(
    state: TargetsState,
    onAction: (TargetsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        SettingsHeader(
            title = stringResource(Res.string.settings_targets_title),
            backLabel = stringResource(Res.string.settings_back_more),
            onBack = { onAction(TargetsAction.BackTapped) },
        )
        if (!state.loaded) {
            // Not the background card: until the inputs arrive, nobody knows whether calories show.
            DelayedLoadingIndicator(contentDescription = stringResource(Res.string.settings_loading_cd))
            return@SettingsPage
        }
        state.dialog?.let { TargetsDialogView(it, onAction) }
        // The goal comes first: it is what sets everything under it, when it does.
        state.goal?.let { GoalCardView(it, onAction) }
        if (!state.visible) {
            BackgroundCard(onAction)
            if (state.macros.isNotEmpty()) MacroCard(state, onAction)
            if (state.micros) MicrosRow(onAction)
            Disclosure()
            return@SettingsPage
        }
        // While a goal sets the target, its card is the target: no style to pick, nothing to step.
        if (!state.locked) {
            SectionLabel(stringResource(Res.string.settings_targets_goal_style))
            StyleList(state, onAction)
            if (state.style == StyleKind.ByDay) ByDayCards(state, onAction)
        }
        state.projection?.let { OwnProjection(it) }
        state.safety?.let { SafetyNoteCard(it, state.supportLink, onAction) }
        state.burned?.let { BurnedSourceCard(it, onAction) }
        if (state.macros.isNotEmpty()) MacroCard(state, onAction)
        state.exercise?.let { ExerciseAdjustmentCard(it, onAction) }
        if (state.micros) MicrosRow(onAction)
        Disclosure()
    }
}

/** Where the person's own target leads, from the latest weigh-in, against a typical day. */
@Composable
private fun OwnProjection(chart: ProjectionChartUi) {
    SectionLabel(stringResource(Res.string.targets_projection_title))
    EdgeCard(edgeToEdge = false, contentPadding = SettingsDefaults.CardInset, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProjectionChart(chart)
        Text(text = stringResource(Res.string.targets_projection_body), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        ProjectionForecastLines(chart)
    }
}

/**
 * Under a low goal or a fast deficit: a gentle note on the warm tint the photo estimate's note
 * uses (it explains, it doesn't warn), and, with `release.findSupport`, a way to Find support.
 * Nothing to dismiss, nothing blocked: the goal is still the person's to set.
 */
@Composable
private fun SafetyNoteCard(note: SafetyNote, link: Boolean, onAction: (TargetsAction) -> Unit) {
    val warm = AppTheme.colors.nutrients.carbs
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.inputLarge)
            .background(warm.container)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = if (link) 0.dp else 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(icon = AppIcons.Info, size = NoteIconSize, color = AppTheme.colors.ink)
            Text(
                text = stringResource(
                    when (note) {
                        SafetyNote.LowGoal -> Res.string.settings_targets_low_note
                        SafetyNote.FastDeficit -> Res.string.settings_targets_fast_note
                    },
                ),
                typography = AppTheme.type.body,
                color = AppTheme.colors.ink,
            )
        }
        if (link) {
            ChevronLink(
                text = stringResource(Res.string.settings_targets_support_link),
                onClick = { onAction(TargetsAction.FindSupportTapped) },
                color = AppTheme.colors.ink,
                modifier = Modifier.padding(start = NoteIconSize + 10.dp),
            )
        }
    }
}

/** What every number on this page is: an estimate to start from. Said once, at the foot. */
@Composable
private fun Disclosure() {
    Text(
        text = stringResource(Res.string.settings_targets_disclosure),
        typography = AppTheme.type.secondarySmall,
        color = AppTheme.colors.muted,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** Vitamins and minerals: their own page, as their references are set one by one. */
@Composable
private fun MicrosRow(onAction: (TargetsAction) -> Unit) {
    SettingsCard {
        SettingsRow(
            title = stringResource(Res.string.settings_micros_title),
            subtitle = stringResource(Res.string.settings_micros_row_line),
            icon = null,
            chevron = true,
            onClick = { onAction(TargetsAction.MicrosTapped) },
        )
    }
}

internal val StyleKind.label: StringResource
    get() = when (this) {
        StyleKind.Range -> Res.string.settings_targets_style_range
        StyleKind.Exact -> Res.string.settings_targets_style_exact
        StyleKind.Deficit -> Res.string.settings_targets_style_deficit
        StyleKind.Surplus -> Res.string.settings_targets_style_surplus
        StyleKind.ByDay -> Res.string.settings_targets_style_by_day
    }

/**
 * The goal styles as one list (design sync 4, 13.2e). A tap picks a style at once; the picked
 * row opens its own controls underneath it. Different by day's groups follow the card.
 */
@Composable
private fun StyleList(state: TargetsState, onAction: (TargetsAction) -> Unit) {
    EdgeCard(edgeToEdge = false, contentPadding = PaddingValues(horizontal = SettingsDefaults.CardPadding), verticalArrangement = Arrangement.Top) {
        Column(modifier = Modifier.selectableGroup()) {
            state.styles.forEachIndexed { index, row ->
                StyleRowView(row, onAction)
                if (row.selected) {
                    Column(
                        modifier = Modifier.padding(bottom = AppTheme.spacing.row),
                        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
                    ) {
                        when (row.kind) {
                            StyleKind.Range -> state.range?.let { RangeBody(it, onAction) }
                            StyleKind.Exact -> state.number?.let { NumberBody(it, onAction) }
                            StyleKind.Deficit -> state.deficit?.let { DeficitBody(it, onAction) }
                            StyleKind.Surplus -> state.surplus?.let { SurplusBody(it, onAction) }
                            StyleKind.ByDay -> Unit
                        }
                    }
                }
                if (index < state.styles.lastIndex) HorizontalDivider(color = AppTheme.colors.line)
            }
        }
    }
}

@Composable
private fun StyleRowView(row: StyleRow, onAction: (TargetsAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = SettingsDefaults.RowMinHeight)
            .selectable(selected = row.selected, role = Role.RadioButton, onClick = { onAction(TargetsAction.StylePicked(row.kind)) })
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(row.kind.label), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
            val line = when (row.kind) {
                StyleKind.Deficit -> Res.string.settings_targets_style_deficit_line
                StyleKind.Surplus -> Res.string.settings_targets_style_surplus_line
                else -> null
            }
            when {
                row.range != null -> NutrientText(
                    metric = Metric.Calories,
                    display = row.range,
                    typography = AppTheme.type.secondary.Tabular,
                    color = AppTheme.colors.muted,
                )
                line != null -> Text(text = stringResource(line), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            }
        }
        RadioDot(selected = row.selected)
    }
}

@Composable
private fun RangeBody(range: RangeCard, onAction: (TargetsAction) -> Unit) {
    EndSteppers(range.low, range.high, group = null, onAction = onAction)
    RangeTrack(range.band)
    if (range.lowLocked) {
        Text(
            text = stringResource(Res.string.settings_targets_min_note),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
        )
    }
}

/**
 * The two ends side by side, as v3 draws them, when the wider amount fits between its steps at
 * the current text size; stacked, each the card's full width, when it does not (Large text). The
 * amount is never shrunk below the type scale or cut off.
 */
@Composable
private fun EndSteppers(low: EndStepper, high: EndStepper, group: Int?, onAction: (TargetsAction) -> Unit) {
    val ends = listOf(RangeEnd.Low to low, RangeEnd.High to high)
    val measurer = rememberTextMeasurer()
    val style = StepperValueType.style
    // Measured again only when an amount or the text size changes, not on every recomposition.
    val widestPx = remember(low.value, high.value, style, measurer) {
        ends.maxOf { (_, stepper) -> stepper.value.words?.let { measurer.measure(it, style, maxLines = 1).size.width } ?: 0 }
    }
    val widest = with(LocalDensity.current) { widestPx.toDp() }
    val gap = AppTheme.spacing.row
    BoxWithConstraints {
        val sideBySide = (maxWidth - gap) / 2 >= widest + StepSize * 2 + AppTheme.spacing.chip * 2
        val column: @Composable (Modifier, RangeEnd, EndStepper) -> Unit = { modifier, end, stepper ->
            EndColumn(modifier, end, stepper, group, onAction)
        }
        if (sideBySide) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                ends.forEach { (end, stepper) -> column(Modifier.weight(1f), end, stepper) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                ends.forEach { (end, stepper) -> column(Modifier.fillMaxWidth(), end, stepper) }
            }
        }
    }
}

@Composable
private fun EndColumn(modifier: Modifier, end: RangeEnd, stepper: EndStepper, group: Int?, onAction: (TargetsAction) -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(if (end == RangeEnd.Low) Res.string.settings_targets_low_end else Res.string.settings_targets_high_end),
            typography = AppTheme.type.secondaryStrong,
            color = AppTheme.colors.muted,
        )
        EndStepperView(
            stepper = stepper,
            endName = stringResource(
                if (end == RangeEnd.Low) Res.string.settings_targets_low_end_inline else Res.string.settings_targets_high_end_inline,
            ),
            onStep = { up -> onAction(TargetsAction.StepTapped(end, up, group)) },
            onAction = onAction,
        )
    }
}

/** What the amount reads, to measure it; null when hidden. */
private val Display.words: String?
    get() = when (this) {
        Display.Hidden -> null
        is Display.Soft -> text
        is Display.Shown -> text
    }

/**
 * − value +, in a soft rounded well. A lock replaces − where the floor blocks it, and + where a cap
 * does. [lower] and [raise] name the steps when they are not "Lower the [endName]".
 */
@Composable
private fun EndStepperView(
    stepper: EndStepper,
    endName: String,
    onStep: (up: Boolean) -> Unit,
    lower: String? = null,
    raise: String? = null,
    raiseLocked: String? = null,
    onAction: ((TargetsAction) -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.inputLarge)
            .background(AppTheme.colors.soft)
            // v3's 40 dp steps (the design's exception to 44), 4 dp in from the well's sides.
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (stepper.down) {
            StepState.Locked -> Box(modifier = Modifier.size(StepSize), contentAlignment = Alignment.Center) {
                Icon(
                    icon = AppIcons.LockOutlined.described(stringResource(Res.string.settings_targets_locked)),
                    size = 22.dp,
                    color = AppTheme.colors.muted,
                )
            }
            else -> StepButton(
                icon = AppIcons.Remove.described(lower ?: stringResource(Res.string.settings_targets_lower_end, endName)),
                enabled = stepper.down == StepState.Open,
                onClick = { onStep(false) },
                touchTarget = StepSize,
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            TypedAmount(stepper, endName, StepperValueType, onAction)
        }
        when (stepper.up) {
            StepState.Locked -> Box(modifier = Modifier.size(StepSize), contentAlignment = Alignment.Center) {
                Icon(
                    icon = AppIcons.LockOutlined.described(raiseLocked ?: stringResource(Res.string.settings_targets_ceiling_locked)),
                    size = 22.dp,
                    color = AppTheme.colors.muted,
                )
            }
            else -> StepButton(
                icon = AppIcons.Add.described(raise ?: stringResource(Res.string.settings_targets_raise_end, endName)),
                enabled = stepper.up == StepState.Open,
                onClick = { onStep(true) },
                touchTarget = StepSize,
            )
        }
    }
}

@Composable
private fun StepButton(
    icon: IconResource,
    enabled: Boolean,
    onClick: () -> Unit,
    size: Dp = StepSize,
    container: ColorResource? = null,
    touchTarget: Dp = MinTouchTarget,
) {
    Box(
        modifier = Modifier
            .minTouchTarget(touchTarget)
            .size(size)
            .clip(AppTheme.shapes.input)
            .background(container ?: AppTheme.colors.surface)
            .semantics { role = Role.Button }
            .bounceClick(enabled = enabled, scaleDown = 0.92f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon = icon, size = 26.dp, color = if (enabled) AppTheme.colors.ink else AppTheme.colors.line)
    }
}

/** The range as a band on a track, so moving either end is visible at a glance. */
@Composable
private fun RangeTrack(band: ClosedFloatingPointRange<Float>) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(AppTheme.shapes.pill)
            .background(AppTheme.colors.track),
    ) {
        Box(
            modifier = Modifier
                .offset(x = maxWidth * band.start)
                .width(maxWidth * (band.endInclusive - band.start))
                .fillMaxHeight()
                .clip(AppTheme.shapes.pill)
                .background(AppTheme.colors.accent),
        )
    }
}

/**
 * − amount kcal +, large, with 52 dp steps: the deficit, the surplus and one number. A lock
 * replaces − or + where a limit only a dietitian can move stops it, and says so as [locked].
 */
@Composable
private fun BigStepper(
    stepper: EndStepper,
    lower: String,
    raise: String,
    locked: String,
    onStep: (up: Boolean) -> Unit,
    unit: StringResource = Res.string.settings_targets_kcal_a_day,
    raiseLocked: String = locked,
    name: String = lower,
    onAction: ((TargetsAction) -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BigStep(state = stepper.down, icon = AppIcons.Remove.described(lower), locked = locked, onClick = { onStep(false) })
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            TypedAmount(stepper, name, AppTheme.type.h1.Tabular, onAction, modifier = Modifier.weight(1f, fill = false))
            Text(
                text = stringResource(unit),
                typography = AppTheme.type.bodyBold,
                color = AppTheme.colors.muted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        BigStep(state = stepper.up, icon = AppIcons.Add.described(raise), locked = raiseLocked, onClick = { onStep(true) })
    }
}

@Composable
private fun BigStep(state: StepState, icon: IconResource, locked: String, onClick: () -> Unit) {
    if (state == StepState.Locked) {
        Box(
            modifier = Modifier.size(BigStepSize).clip(AppTheme.shapes.input).background(AppTheme.colors.soft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon = AppIcons.LockOutlined.described(locked), size = 22.dp, color = AppTheme.colors.muted)
        }
    } else {
        StepButton(
            icon = icon,
            enabled = state == StepState.Open,
            onClick = onClick,
            size = BigStepSize,
            container = AppTheme.colors.soft,
        )
    }
}

/** One number: the goal, each day, stepped 50 at a time or typed, between the lowest and highest a goal goes. */
@Composable
private fun NumberBody(number: NumberCard, onAction: (TargetsAction) -> Unit) {
    BigStepper(
        stepper = number.stepper,
        lower = stringResource(Res.string.settings_targets_number_lower),
        raise = stringResource(Res.string.settings_targets_number_raise),
        locked = stringResource(Res.string.settings_targets_locked),
        raiseLocked = stringResource(Res.string.settings_targets_ceiling_locked),
        onStep = { up -> onAction(TargetsAction.NumberStepped(up)) },
    )
    NumberField(
        value = number.text,
        onValueChange = { onAction(TargetsAction.NumberTyped(it)) },
        label = stringResource(Res.string.settings_targets_number_field),
        unit = stringResource(Res.string.settings_targets_kcal_a_day),
        allowDecimal = false,
        keyboardActions = KeyboardActions(
            onDone = {
                onAction(TargetsAction.NumberDone)
                defaultKeyboardAction(ImeAction.Done)
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    when {
        number.tooLow -> NutrientPhrase(
            metric = Metric.Calories,
            display = number.floor,
            phrase = { stringResource(Res.string.settings_targets_number_too_low, it) },
            typography = AppTheme.type.secondary.Tabular,
            color = AppTheme.colors.muted,
        )
        number.tooHigh -> NutrientPhrase(
            metric = Metric.Calories,
            display = number.ceiling,
            phrase = { stringResource(Res.string.settings_targets_number_too_high, it) },
            typography = AppTheme.type.secondary.Tabular,
            color = AppTheme.colors.muted,
        )
        number.stepper.down == StepState.Locked -> Note(stringResource(Res.string.settings_targets_min_note))
    }
}

/** A set deficit: the amount, held at where it started, and the target it comes to against Burned. */
@Composable
private fun DeficitBody(deficit: BurnGoalCard, onAction: (TargetsAction) -> Unit) {
    BigStepper(
        stepper = EndStepper(deficit.amount, deficit.down, deficit.up, TypedField(TypedField.Kind.Deficit), deficit.text),
        lower = stringResource(Res.string.settings_targets_deficit_less),
        raise = stringResource(Res.string.settings_targets_deficit_more),
        locked = stringResource(Res.string.settings_targets_deficit_locked),
        onStep = { up -> onAction(TargetsAction.DeficitStepped(up)) },
        name = stringResource(Res.string.settings_targets_deficit_title),
        onAction = onAction,
    )
    BurnTarget(deficit.target)
    BurnBasis(deficit, onAction)
    when {
        deficit.needsDetails || deficit.canStartFromBurn -> Unit
        deficit.down == StepState.Closed && deficit.up == StepState.Locked -> Note(stringResource(Res.string.settings_targets_deficit_zero_note))
        deficit.up == StepState.Locked -> Note(stringResource(Res.string.settings_targets_deficit_cap_note))
    }
    EnergyInfoLink(stringResource(Res.string.settings_targets_deficit_info), onClick = { onAction(TargetsAction.ExplainTapped(EnergyTopic.Balance)) })
}

/** A set surplus: as the deficit, over what the user burns, or over the usual target until that is known. */
@Composable
private fun SurplusBody(surplus: BurnGoalCard, onAction: (TargetsAction) -> Unit) {
    BigStepper(
        stepper = EndStepper(surplus.amount, surplus.down, surplus.up, TypedField(TypedField.Kind.Surplus), surplus.text),
        lower = stringResource(Res.string.settings_targets_surplus_less),
        raise = stringResource(Res.string.settings_targets_surplus_more),
        locked = stringResource(Res.string.settings_targets_surplus_locked),
        onStep = { up -> onAction(TargetsAction.SurplusStepped(up)) },
        name = stringResource(Res.string.settings_targets_surplus_title),
        onAction = onAction,
    )
    if (surplus.fromAim && surplus.target != null) {
        NutrientPhrase(
            metric = Metric.Calories,
            display = surplus.target,
            phrase = { stringResource(Res.string.settings_targets_surplus_from_target, it) },
            typography = AppTheme.type.secondary.Tabular,
            color = AppTheme.colors.ink,
        )
    } else {
        BurnTarget(surplus.target)
    }
    BurnBasis(surplus, onAction)
    if (surplus.up == StepState.Locked && surplus.down == StepState.Open && !surplus.canStartFromBurn) {
        Note(stringResource(Res.string.settings_targets_surplus_cap))
    }
    EnergyInfoLink(stringResource(Res.string.settings_targets_surplus_info), onClick = { onAction(TargetsAction.ExplainTapped(EnergyTopic.Balance)) })
}

/** "Today's target 1,680 · moves with what you burn". */
@Composable
private fun BurnTarget(target: Display?) {
    if (target == null || target is Display.Hidden) return
    NutrientPhrase(
        metric = Metric.Calories,
        display = target,
        phrase = { stringResource(Res.string.settings_targets_burn_target, it) },
        typography = AppTheme.type.secondary.Tabular,
        color = AppTheme.colors.ink,
    )
}

/**
 * What the target is based on: "Based on about 2,180 burned today", and whether that is an
 * estimate; or, with no Burned yet, what Opson needs to work it out and a way to add it. Then,
 * for an amount that started at 0 with nothing known, a way to start again from today's burn.
 */
@Composable
private fun BurnBasis(card: BurnGoalCard, onAction: (TargetsAction) -> Unit) {
    if (card.needsDetails) {
        Note(stringResource(Res.string.settings_targets_burn_needs_details))
        FlatButton(
            text = stringResource(Res.string.settings_targets_add_details),
            onClick = { onAction(TargetsAction.RestingEnergyTapped) },
            tone = FlatButtonTone.Outlined,
            centered = true,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    card.burned?.let { burned ->
        NutrientPhrase(
            metric = Metric.EnergyOut,
            display = burned,
            phrase = { stringResource(Res.string.settings_targets_burn_based, it) },
            typography = AppTheme.type.secondary.Tabular,
            color = AppTheme.colors.muted,
        )
    }
    if (card.estimated) Note(stringResource(Res.string.settings_targets_burn_estimated))
    if (card.canStartFromBurn) {
        Note(stringResource(Res.string.settings_targets_start_from_burn_note))
        FlatButton(
            text = stringResource(Res.string.settings_targets_start_from_burn),
            onClick = { onAction(TargetsAction.StartFromBurnTapped) },
            tone = FlatButtonTone.Outlined,
            centered = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(text = text, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
}

/**
 * "Burned comes from" (design sync 4): resting energy as it is now and where it comes from, and
 * everyday activity: the health app when one is connected, else everyday activity, picked here.
 */
@Composable
private fun BurnedSourceCard(burned: BurnedCard, onAction: (TargetsAction) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(stringResource(Res.string.settings_targets_burned_title), modifier = Modifier.weight(1f))
        EnergyInfoButton(onClick = { onAction(TargetsAction.ExplainTapped(EnergyTopic.Burned)) })
    }
    SettingsCard {
        SettingsRow(
            title = stringResource(Res.string.settings_targets_resting),
            icon = null,
            subtitle = stringResource(
                when (burned.restingFrom) {
                    RestingFrom.Calculated -> Res.string.settings_targets_resting_calculated
                    RestingFrom.Health -> burned.activity?.title ?: Res.string.settings_targets_resting_calculated
                    RestingFrom.Own -> Res.string.settings_targets_resting_own
                    null -> Res.string.settings_targets_resting_unknown
                },
            ),
            chevron = true,
            showDivider = true,
            onClick = { onAction(TargetsAction.RestingEnergyTapped) },
            trailing = {
                NutrientText(metric = Metric.EnergyOut, display = burned.resting, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink)
            },
        )
        val source = burned.activity
        SettingsRow(
            title = stringResource(if (source != null) Res.string.settings_targets_activity else Res.string.everyday_activity_title),
            icon = null,
            subtitle = source?.let { stringResource(it.title) } ?: stringResource(burned.everydayActivity.label),
            chevron = source != null,
            onClick = { onAction(TargetsAction.ActivityTapped) },
        )
        if (burned.levels.isNotEmpty()) EverydayActivities(burned, onAction)
    }
}

@Composable
private fun EverydayActivities(burned: BurnedCard, onAction: (TargetsAction) -> Unit) {
    Column(modifier = Modifier.selectableGroup().padding(bottom = SettingsDefaults.CardPadding)) {
        burned.levels.forEach { level ->
            val selected = level == burned.everydayActivity
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = SettingsDefaults.RowMinHeight)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onAction(TargetsAction.EverydayActivityPicked(level)) })
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = stringResource(level.label), typography = AppTheme.type.body, color = AppTheme.colors.ink)
                    Text(text = stringResource(level.detail), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
                }
                RadioDot(selected = selected)
            }
        }
        Note(stringResource(Res.string.everyday_activity_body))
        FlatButton(
            text = stringResource(Res.string.settings_targets_activity_connect),
            onClick = { onAction(TargetsAction.ConnectHealthTapped) },
            tone = FlatButtonTone.Outlined,
            centered = true,
            modifier = Modifier.fillMaxWidth().padding(top = AppTheme.spacing.row),
        )
    }
}

@Composable
private fun ByDayCards(state: TargetsState, onAction: (TargetsAction) -> Unit) {
    // Design sync 2: the named groups first, "Other days" (the first group, which takes every
    // day nobody else has) last. Every day still belongs to exactly one group.
    val ordered = if (state.groups.size > 1) state.groups.drop(1) + state.groups.first() else state.groups
    ordered.forEach { group -> DayGroupCardView(group, onAction) }
    if (state.canAddGroup) {
        Row(
            modifier = Modifier
                .minTouchTarget()
                .semantics { role = Role.Button }
                .bounceClick(scaleDown = 0.97f, onClick = { onAction(TargetsAction.AddGroupTapped) })
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Icon(icon = AppIcons.Add, size = 24.dp, color = AppTheme.colors.accent)
            Text(
                text = stringResource(Res.string.settings_targets_group_add),
                typography = AppTheme.type.cardTitle,
                color = AppTheme.colors.accent,
            )
        }
    }
    if (state.week.isNotEmpty()) WeekStrip(state.week)
}

/**
 * A day group, its controls always open: its days, its shape, its steppers. A tap on a day moves
 * it here, or a day of this group back to the first; the first group's own days are just shown
 * (they have nowhere to go), and with only one group there is nothing to pick at all. Every group
 * but the first has a remove button in its header.
 */
@Composable
private fun DayGroupCardView(group: DayGroupCard, onAction: (TargetsAction) -> Unit) {
    val name = group.label.text()
    val tone = groupTone(group.index)
    EdgeCard(
        edgeToEdge = false,
        contentPadding = SettingsDefaults.CardInset,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.defaultMinSize(minHeight = MinTouchTarget)) {
            Text(text = name, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
            if (group.removable) {
                Box(
                    modifier = Modifier
                        .minTouchTarget()
                        .semantics { role = Role.Button }
                        .bounceClick(scaleDown = 0.9f, onClick = { onAction(TargetsAction.RemoveGroupTapped(group.index)) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon = AppIcons.DeleteOutline.described(stringResource(Res.string.settings_targets_group_remove, name)),
                        size = 24.dp,
                        color = AppTheme.colors.muted,
                    )
                }
            }
        }
        if (group.days.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                group.days.forEach { chip ->
                    DayChipView(
                        chip = chip,
                        tone = tone,
                        description = stringResource(Res.string.settings_targets_group_day, DateCopy.longWeekday(chip.day), name),
                        onClick = { onAction(TargetsAction.DayTapped(group.index, chip.day)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        when {
            group.days.isEmpty() -> Note(stringResource(Res.string.settings_targets_group_only))
            group.empty -> Note(stringResource(Res.string.settings_targets_group_empty))
            group.index == 0 -> Note(stringResource(Res.string.settings_targets_group_rest))
        }
        HorizontalDivider(color = AppTheme.colors.line)
        if (group.shapes.isNotEmpty()) {
            SegmentedControl(
                options = group.shapes,
                selected = group.shape,
                onSelect = { onAction(TargetsAction.GroupShapePicked(group.index, it)) },
                label = { stringResource(it.label) },
            )
        }
        if (group.low != null && group.high != null) EndSteppers(group.low, group.high, group = group.index, onAction = onAction)
        group.number?.let { number ->
            GroupStepper(
                title = stringResource(Res.string.settings_targets_group_goal),
                stepper = number,
                lower = stringResource(Res.string.settings_targets_number_lower),
                raise = stringResource(Res.string.settings_targets_number_raise),
                onStep = { up -> onAction(TargetsAction.NumberStepped(up, group.index)) },
                onAction = onAction,
            )
        }
        group.surplus?.let { surplus ->
            GroupStepper(
                title = stringResource(Res.string.settings_targets_surplus_title),
                stepper = surplus,
                lower = stringResource(Res.string.settings_targets_surplus_less),
                raise = stringResource(Res.string.settings_targets_surplus_more),
                raiseLocked = stringResource(Res.string.settings_targets_surplus_locked),
                onStep = { up -> onAction(TargetsAction.SurplusStepped(up, group.index)) },
                onAction = onAction,
            )
            GroupSummary(group)
        }
        group.deficit?.let { deficit ->
            GroupStepper(
                title = stringResource(Res.string.settings_targets_deficit_title),
                stepper = deficit,
                lower = stringResource(Res.string.settings_targets_deficit_less),
                raise = stringResource(Res.string.settings_targets_deficit_more),
                raiseLocked = stringResource(Res.string.settings_targets_deficit_locked),
                onStep = { up -> onAction(TargetsAction.DeficitStepped(up, group.index)) },
                onAction = onAction,
            )
            GroupSummary(group)
        }
        if (group.waitingOnBurn) Note(stringResource(Res.string.settings_targets_group_waiting))
    }
}

/** "Range 2,300 to 2,700 kcal", "Goal 2,500 kcal", or "Goal About 2,800 kcal" for a deficit or surplus. */
@Composable
private fun GroupSummary(group: DayGroupCard) {
    val typography = AppTheme.type.bodyBold.Tabular
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(
                if (group.shape == ShapeKind.Range || group.waitingOnBurn) Res.string.settings_targets_group_range else Res.string.settings_targets_group_goal,
            ),
            typography = AppTheme.type.body,
            color = AppTheme.colors.ink,
            modifier = Modifier.weight(1f),
        )
        if ((group.shape == ShapeKind.Surplus || group.shape == ShapeKind.Deficit) && !group.waitingOnBurn) {
            NutrientPhrase(
                metric = Metric.Calories,
                display = group.summary,
                phrase = { stringResource(Res.string.settings_targets_deficit_about, it) },
                typography = typography,
                color = AppTheme.colors.ink,
            )
        } else {
            NutrientText(metric = Metric.Calories, display = group.summary, typography = typography, color = AppTheme.colors.ink)
        }
    }
}

/** A day group's one number or surplus: its name over the well the range's ends use. */
@Composable
private fun GroupStepper(
    title: String,
    stepper: EndStepper,
    lower: String,
    raise: String,
    onStep: (up: Boolean) -> Unit,
    raiseLocked: String? = null,
    onAction: ((TargetsAction) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
        EndStepperView(stepper = stepper, endName = title, onStep = onStep, lower = lower, raise = raise, raiseLocked = raiseLocked, onAction = onAction)
    }
}

/**
 * An amount in a stepper's well, typed in place where it can be ([EndStepper.field]): the same
 * size and weight as the number it replaces, carrying the calorie tag the leak test reads. Done
 * says why a number couldn't be used. Elsewhere, the number as it was.
 */
@Composable
private fun TypedAmount(
    stepper: EndStepper,
    name: String,
    typography: TypographyResource,
    onAction: ((TargetsAction) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val field = stepper.field
    val text = stepper.text
    if (onAction == null || field == null || text == null || stepper.value !is Display.Shown) {
        NutrientText(metric = Metric.Calories, display = stepper.value, typography = typography, color = AppTheme.colors.ink, maxLines = 1, modifier = modifier)
        return
    }
    InlineNumberField(
        value = text,
        onValueChange = { onAction(TargetsAction.FieldTyped(field, it)) },
        label = stringResource(Res.string.settings_targets_type_amount, name),
        typography = typography,
        onDone = { onAction(TargetsAction.FieldDone(field)) },
        modifier = modifier.testTag(Metric.Calories.testTag()),
    )
}

internal val ShapeKind.label: StringResource
    get() = when (this) {
        ShapeKind.Range -> Res.string.settings_targets_group_shape_range
        ShapeKind.Exact -> Res.string.settings_targets_group_shape_exact
        ShapeKind.Deficit -> Res.string.settings_targets_group_shape_deficit
        ShapeKind.Surplus -> Res.string.settings_targets_group_shape_surplus
    }

@Composable
private fun DayChipView(chip: DayChip, tone: ToneColors, description: String, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .minTouchTarget()
            .height(40.dp)
            .clip(AppTheme.shapes.input)
            .background(if (chip.inGroup) tone.container else AppTheme.colors.surface)
            .thenIf(!chip.inGroup) { border(1.dp, AppTheme.colors.line.color, AppTheme.shapes.input) }
            .semantics {
                if (chip.tappable) role = Role.Checkbox
                selected = chip.inGroup
                contentDescription = description
            }
            .thenIf(chip.tappable) { bounceClick(scaleDown = 0.92f, onClick = onClick) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = DateCopy.narrowWeekday(chip.day),
            typography = AppTheme.type.bodyBold,
            color = if (chip.inGroup) tone.content else AppTheme.colors.muted,
        )
    }
}

/** Each weekday as a bar raised to its group's level. */
@Composable
private fun WeekStrip(week: List<WeekBand>) {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = SettingsDefaults.CardInset,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        Text(
            text = stringResource(Res.string.settings_targets_week),
            typography = AppTheme.type.secondaryStrong,
            color = AppTheme.colors.muted,
        )
        Row(modifier = Modifier.fillMaxWidth().height(WeekHeight), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            week.forEach { band ->
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Box(
                        modifier = Modifier
                            .offset(y = (WeekHeight - WeekBarHeight) * (1f - band.position))
                            .fillMaxWidth()
                            .height(WeekBarHeight)
                            .clip(AppTheme.shapes.bar)
                            .background(groupTone(band.groupIndex).container),
                    )
                }
            }
        }
    }
}

/** A group's colour. Only tells groups apart: no group is better or worse. */
@Composable
private fun groupTone(index: Int): ToneColors {
    val colors = AppTheme.colors
    return when (index % 4) {
        0 -> ToneColors(content = colors.accent, container = colors.accentTint)
        1 -> ToneColors(content = colors.onAccent, container = colors.accent)
        2 -> ToneColors(content = colors.bg, container = colors.ink)
        else -> ToneColors(content = colors.ink, container = colors.line)
    }
}

@Composable
private fun GroupLabel.text(): String = when (this) {
    GroupLabel.EveryDay -> stringResource(Res.string.settings_targets_group_every_day)
    GroupLabel.OtherDays -> stringResource(Res.string.settings_targets_group_other_days)
    is GroupLabel.Numbered -> stringResource(Res.string.settings_targets_group_numbered, number)
}

/**
 * The macro split: a bar of protein, carbs and fat, their grams, and the editor under them while
 * it is open (grams or percents, presets, typed amounts, and what doesn't add up).
 */
@Composable
private fun MacroCard(state: TargetsState, onAction: (TargetsAction) -> Unit) {
    val modeLabel = stringResource(
        when (state.macroMode) {
            MacroMode.Ratios -> Res.string.settings_targets_ratios
            MacroMode.Grams -> Res.string.settings_targets_grams
        },
    )
    EdgeCard(
        edgeToEdge = false,
        contentPadding = SettingsDefaults.CardInset,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.settings_targets_macro_split),
                typography = AppTheme.type.cardTitle,
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            if (state.visible) Text(text = modeLabel, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
        }
        // One bar of flush segments, rounded only at its ends (design sync 2).
        Row(
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(AppTheme.shapes.pill),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            state.macros.forEach { macro ->
                Box(
                    modifier = Modifier
                        .weight(macro.weight.coerceAtLeast(1f))
                        .fillMaxHeight()
                        .background(macroColor(macro.metric)),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            state.macros.forEach { macro ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    NutrientPhrase(
                        metric = macro.metric,
                        display = macro.grams,
                        phrase = { stringResource(macro.metric.gramsPhrase, it) },
                        typography = AppTheme.type.body.Tabular,
                        color = AppTheme.colors.ink,
                        numberWeight = FontWeight.ExtraBold,
                    )
                    val low = macro.low as? Display.Shown
                    if (low != null) {
                        NutrientPhrase(
                            metric = macro.metric,
                            display = macro.high,
                            phrase = { stringResource(Res.string.settings_targets_macro_range, low.text, it) },
                            typography = AppTheme.type.secondary.Tabular,
                            color = AppTheme.colors.muted,
                        )
                    }
                }
            }
        }
        if (state.macroRange) {
            Text(text = stringResource(Res.string.settings_targets_macro_range_note), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        state.macroEditor?.let { MacroEditorView(it, onAction) }
        FlatButton(
            text = stringResource(if (state.editingMacros) Res.string.settings_targets_macros_done else Res.string.settings_targets_macros_edit),
            onClick = { onAction(TargetsAction.MacroCardTapped) },
            tone = FlatButtonTone.Outlined,
            centered = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MacroEditorView(editor: MacroEditor, onAction: (TargetsAction) -> Unit) {
    if (editor.percents) {
        Text(
            text = stringResource(Res.string.settings_targets_macros_title),
            typography = AppTheme.type.secondaryStrong,
            color = AppTheme.colors.muted,
        )
        SegmentedControl(
            options = MacroMode.entries,
            selected = editor.mode,
            onSelect = { onAction(TargetsAction.MacroModePicked(it)) },
            label = {
                stringResource(
                    when (it) {
                        MacroMode.Ratios -> Res.string.settings_targets_ratios
                        MacroMode.Grams -> Res.string.settings_targets_grams
                    },
                )
            },
        )
    }
    Text(
        text = stringResource(
            when {
                !editor.percents -> Res.string.settings_targets_grams_only_note
                editor.mode == MacroMode.Ratios -> Res.string.settings_targets_ratios_note
                else -> Res.string.settings_targets_grams_note
            },
        ),
        typography = AppTheme.type.secondarySmall,
        color = AppTheme.colors.muted,
    )
    if (editor.presets.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            editor.presets.forEach { chip ->
                PillChip(
                    label = stringResource(chip.preset.label),
                    selected = chip.selected,
                    onClick = { chip.preset?.let { onAction(TargetsAction.MacroPresetPicked(it)) } },
                )
            }
        }
    }
    editor.rows.forEachIndexed { index, row ->
        // Next walks the macros in order; the last one's key closes the keyboard.
        MacroEditRowView(row, editor.inPercent, if (index < editor.rows.lastIndex) ImeAction.Next else ImeAction.Done, onAction)
    }
    editor.fit?.let { MacroFitView(it, onAction) }
}

/** Protein: − [field] + with the other unit beside it ("131 g" in percent mode, "25%" in grams). */
@Composable
private fun MacroEditRowView(row: MacroEditRow, inPercent: Boolean, imeAction: ImeAction, onAction: (TargetsAction) -> Unit) {
    val name = stringResource(row.metric.nameLabel)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = name, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted, modifier = Modifier.weight(1f))
            NutrientText(
                metric = row.metric,
                display = if (inPercent) row.grams else row.percent,
                typography = AppTheme.type.secondary.Tabular,
                color = AppTheme.colors.muted,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepButton(
                icon = AppIcons.Remove.described(stringResource(Res.string.settings_targets_macro_less, name)),
                enabled = row.canLower,
                onClick = { onAction(TargetsAction.MacroStepped(row.metric, up = false)) },
                container = AppTheme.colors.soft,
            )
            NumberField(
                value = row.text,
                onValueChange = { onAction(TargetsAction.MacroTyped(row.metric, it)) },
                unit = stringResource(if (inPercent) Res.string.settings_targets_unit_percent else Res.string.settings_targets_unit_g),
                allowDecimal = false,
                textAlign = TextAlign.Center,
                imeAction = imeAction,
                modifier = Modifier.weight(1f),
            )
            StepButton(
                icon = AppIcons.Add.described(stringResource(Res.string.settings_targets_macro_more, name)),
                enabled = row.canRaise,
                onClick = { onAction(TargetsAction.MacroStepped(row.metric, up = true)) },
                container = AppTheme.colors.soft,
            )
        }
    }
}

/**
 * "Your macros come to 2,210 kcal, 110 over your goal of 2,100." and the two ways to agree: the
 * goal drives (fit the macros) or the macros drive (make their total the goal).
 */
@Composable
private fun MacroFitView(fit: MacroFitLine, onAction: (TargetsAction) -> Unit) {
    val macroKcal = fit.macroKcal.words ?: return
    val goal = fit.goal.words ?: return
    val gap = fit.gap.words ?: return
    val goalPhrase = stringResource(
        when (fit.goalKind) {
            MacroGoalKind.Number -> Res.string.settings_targets_macros_goal_number
            MacroGoalKind.RangeMiddle -> Res.string.settings_targets_macros_goal_range
            MacroGoalKind.Today -> Res.string.settings_targets_macros_goal_today
        },
        goal,
    )
    Text(
        text = stringResource(
            if (fit.over) Res.string.settings_targets_macros_over else Res.string.settings_targets_macros_under,
            macroKcal,
            gap,
            goalPhrase,
        ),
        typography = AppTheme.type.secondary.Tabular,
        color = AppTheme.colors.ink,
    )
    FlatButton(
        text = stringResource(Res.string.settings_targets_macros_fit, goal),
        onClick = { onAction(TargetsAction.MacrosFitTapped) },
        centered = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (fit.canUseAsGoal) {
        FlatButton(
            text = stringResource(Res.string.settings_targets_macros_use, macroKcal),
            onClick = { onAction(TargetsAction.MacrosAsGoalTapped) },
            tone = FlatButtonTone.Outlined,
            centered = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val MacroPreset?.label: StringResource
    get() = when (this) {
        MacroPreset.Balanced -> Res.string.settings_targets_preset_balanced
        MacroPreset.HigherProtein -> Res.string.settings_targets_preset_higher_protein
        MacroPreset.LowerCarb -> Res.string.settings_targets_preset_lower_carb
        null -> Res.string.settings_targets_preset_custom
    }

private val Metric.nameLabel: StringResource
    get() = when (this) {
        Metric.Protein -> Res.string.settings_targets_macro_name_protein
        Metric.Carbs -> Res.string.settings_targets_macro_name_carbs
        else -> Res.string.settings_targets_macro_name_fat
    }

private val Metric.gramsPhrase: StringResource
    get() = when (this) {
        Metric.Protein -> Res.string.settings_targets_macro_protein
        Metric.Carbs -> Res.string.settings_targets_macro_carbs
        else -> Res.string.settings_targets_macro_fat
    }

@Composable
private fun macroColor(metric: Metric): ColorResource {
    val nutrients = AppTheme.colors.nutrients
    return when (metric) {
        Metric.Protein -> nutrients.protein
        Metric.Carbs -> nutrients.carbs
        Metric.Fat -> nutrients.fat
        else -> nutrients.fiber
    }.content
}

@Composable
private fun BackgroundCard(onAction: (TargetsAction) -> Unit) {
    EdgeCard(edgeToEdge = false, contentPadding = SettingsDefaults.CardInset) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            ToneIcon(
                icon = AppIcons.VisibilityOff,
                tone = ToneColors(content = AppTheme.colors.accent, container = AppTheme.colors.accentTint),
            )
            Text(
                text = stringResource(Res.string.settings_targets_hidden_title),
                typography = AppTheme.type.cardTitle,
                color = AppTheme.colors.ink,
            )
        }
        Text(
            text = stringResource(Res.string.settings_targets_hidden_body),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
        )
        FlatButton(
            text = stringResource(Res.string.settings_targets_hidden_link),
            onClick = { onAction(TargetsAction.ModesTapped) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val StepSize = 40.dp
private val NoteIconSize = 22.dp
private val BigStepSize = 52.dp

/** The ends' amounts, a step under `numericLarge` (design sync 2). */
private val StepperValueType: TypographyResource
    @Composable get() = AppTheme.type.figureLarge

private val WeekHeight = 40.dp
private val WeekBarHeight = 20.dp

@Preview
@Composable
private fun TargetsPreview() {
    val settings = SettingsFixtures.settings()
    SettingsPreview { TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers)), onAction = {}) }
}

/** Design sync 4's 13.2e: a set deficit picked, and where Burned comes from. */
@Preview
@Composable
private fun TargetsGoalStyleV4Preview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.deficitTargets)
    SettingsPreview {
        TargetsScreen(
            TargetsStateBuilder.build(
                settings,
                SettingsFixtures.policy(settings.numbers),
                burned = SettingsFixtures.burned,
                goalsAndEnergy = true,
                healthSource = HealthSource.HealthConnect,
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun TargetsDeficitPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.deficitTargets)
    SettingsPreview {
        TargetsScreen(
            TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers), burned = SettingsFixtures.burned),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun TargetsDeficitNoBurnYetPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.deficitTargets)
    SettingsPreview { TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers)), onAction = {}) }
}

@Preview
@Composable
private fun TargetsDeficitAtCapPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.newDeficitTargets)
    SettingsPreview { TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers)), onAction = {}) }
}

@Preview
@Composable
private fun TargetsExactPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.exactTargets)
    SettingsPreview { TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers)), onAction = {}) }
}

@Preview
@Composable
private fun TargetsExactAtFloorPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.rangeTargets.useExact().withEnergy(MIN_GOAL_KCAL))
    SettingsPreview { TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers)), onAction = {}) }
}

@Preview
@Composable
private fun TargetsSurplusPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.surplusTargets)
    SettingsPreview {
        TargetsScreen(
            TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers), burned = SettingsFixtures.burned),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun TargetsSurplusNoBurnYetPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.surplusTargets)
    SettingsPreview { TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers)), onAction = {}) }
}

@Preview
@Composable
private fun TargetsByDayPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.byDayTargets)
    SettingsPreview { TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers)), onAction = {}) }
}

/** Different by day just picked: every day still in Other days, and a new group ready for its days. */
@Preview
@Composable
private fun TargetsByDayNewGroupPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.newByDayTargets)
    SettingsPreview { TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers)), onAction = {}) }
}

/** A range whose low end is under the line for a woman: the gentle note and Find support, nothing locked. */
@Preview
@Composable
private fun TargetsLowGoalPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.lowRangeTargets).copy(bodySex = BodySex.Female)
    SettingsPreview { TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers)), onAction = {}) }
}

/** A set deficit past a healthy pace: the same note, about the pace. */
@Preview
@Composable
private fun TargetsFastDeficitPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.fastDeficitTargets)
    SettingsPreview {
        TargetsScreen(
            TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers), burned = SettingsFixtures.activeBurned, goalsAndEnergy = true),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun TargetsByDayEditingDarkPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.byDayTargets).copy(macroMode = MacroMode.Grams)
    SettingsPreview(dark = true) {
        TargetsScreen(
            TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers), editingMacros = true),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun TargetsByDayShapesPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.shapedByDayTargets)
    SettingsPreview {
        TargetsScreen(
            TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers), goalsAndEnergy = true),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun TargetsByDaySurplusDarkPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.shapedByDayTargets)
    SettingsPreview(dark = true) {
        TargetsScreen(
            TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers), goalsAndEnergy = true),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun TargetsBlindPreview() {
    val settings = SettingsFixtures.settings(NumbersSettings(preset = PresetId.NoNumbers))
    SettingsPreview { TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers)), onAction = {}) }
}

/** Macros with grams: no calorie goal, the gram targets under it. */
@Preview
@Composable
private fun TargetsMacrosWithGramsPreview() {
    val settings = SettingsFixtures.settings(NumbersSettings(preset = PresetId.MacrosWithGrams))
    SettingsPreview { TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers)), onAction = {}) }
}

/** A deficit with no health app: Burned is resting energy plus everyday activity, whose choices are open. */
@Preview
@Composable
private fun TargetsDeficitEstimatedPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.deficitTargets).copy(everydayActivity = EverydayActivity.SomeWalking)
    SettingsPreview {
        TargetsScreen(
            TargetsStateBuilder.build(
                settings,
                SettingsFixtures.policy(settings.numbers),
                burned = SettingsFixtures.estimatedBurned,
                goalsAndEnergy = true,
                choosingActivity = true,
            ),
            onAction = {},
        )
    }
}

/** A deficit picked before Burned was known, which started at 0: now it is, it can start from today's burn. */
@Preview
@Composable
private fun TargetsDeficitStartFromBurnPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.newDeficitTargets.copy(startingKcal = 1800))
    SettingsPreview {
        TargetsScreen(
            TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers), burned = SettingsFixtures.estimatedBurned, goalsAndEnergy = true),
            onAction = {},
        )
    }
}

/** One number, typed under the lowest a goal goes and Done pressed: the field keeps it and the note says the lowest. */
@Preview
@Composable
private fun TargetsExactTypedTooLowPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.exactTargets)
    SettingsPreview {
        TargetsScreen(
            TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers), drafts = TargetsDrafts(number = "700", numberDone = true)),
            onAction = {},
        )
    }
}

/** Different by day with every goal type, the deficit day open. */
@Preview
@Composable
private fun TargetsByDayEveryShapePreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.everyShapeByDayTargets)
    SettingsPreview {
        TargetsScreen(
            TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers), burned = SettingsFixtures.burned, goalsAndEnergy = true),
            onAction = {},
        )
    }
}

/** A deficit day with no Burned yet: it reads as its range, and says why. */
@Preview
@Composable
private fun TargetsByDayDeficitNoBurnPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.everyShapeByDayTargets)
    SettingsPreview {
        TargetsScreen(
            TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers), goalsAndEnergy = true),
            onAction = {},
        )
    }
}

/** The macro editor in percents of the calorie goal, on a preset. */
@Preview
@Composable
private fun TargetsMacrosPercentPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.rangeTargets.withPreset(MacroPreset.HigherProtein, 2100))
    SettingsPreview {
        TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers), editingMacros = true), onAction = {})
    }
}

/** The macro editor in grams that add up to more than the goal: both ways to make them agree. */
@Preview
@Composable
private fun TargetsMacrosOverPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.gramsOverTargets).copy(macroMode = MacroMode.Grams)
    SettingsPreview {
        TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers), editingMacros = true), onAction = {})
    }
}

/** Macros with grams: calories stay out of view, so the editor is grams alone. */
@Preview
@Composable
private fun TargetsMacrosWithGramsEditingPreview() {
    val settings = SettingsFixtures.settings(NumbersSettings(preset = PresetId.MacrosWithGrams))
    SettingsPreview {
        TargetsScreen(
            TargetsStateBuilder.build(
                settings,
                SettingsFixtures.policy(settings.numbers),
                editingMacros = true,
                drafts = TargetsDrafts(macros = mapOf(Metric.Protein to "13")),
            ),
            onAction = {},
        )
    }
}

// The largest text size the app offers (Look and feel, Large): every stepper and amount whole.

@Preview
@Composable
private fun TargetsLargeTextPreview() {
    val settings = SettingsFixtures.settings()
    SettingsPreview(largeText = true) { TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers)), onAction = {}) }
}

@Preview
@Composable
private fun TargetsDeficitLargeTextPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.deficitTargets)
    SettingsPreview(largeText = true) {
        TargetsScreen(
            TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers), burned = SettingsFixtures.burned),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun TargetsMacrosOverLargeTextPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.gramsOverTargets).copy(macroMode = MacroMode.Grams)
    SettingsPreview(largeText = true) {
        TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers), editingMacros = true), onAction = {})
    }
}

@Preview
@Composable
private fun TargetsSurplusLargeTextPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.surplusTargets)
    SettingsPreview(largeText = true) { TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers)), onAction = {}) }
}

@Preview
@Composable
private fun TargetsByDayEditingLargeTextPreview() {
    val settings = SettingsFixtures.settings(targets = SettingsFixtures.byDayTargets)
    SettingsPreview(largeText = true) {
        TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers)), onAction = {})
    }
}
