package com.opson.features.logger.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.ui.NutrientTile
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.Stepper
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonStyle
import com.opson.libraries.ui.components.chip.ChoiceChip
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_add_to
import opson.libraries.resources.generated.resources.logger_amount
import opson.libraries.resources.generated.resources.logger_amount_hunger
import opson.libraries.resources.generated.resources.logger_amount_keep_going
import opson.libraries.resources.generated.resources.logger_amount_missing
import opson.libraries.resources.generated.resources.logger_amount_per
import opson.libraries.resources.generated.resources.logger_amount_per_only
import opson.libraries.resources.generated.resources.logger_amount_stop_asking
import opson.libraries.resources.generated.resources.logger_amount_sure
import opson.libraries.resources.generated.resources.logger_hunger_bit
import opson.libraries.resources.generated.resources.logger_hunger_hungry
import opson.libraries.resources.generated.resources.logger_hunger_not
import opson.libraries.resources.generated.resources.logger_hunger_very
import opson.libraries.resources.generated.resources.logger_precision_estimate
import opson.libraries.resources.generated.resources.logger_precision_named
import opson.libraries.resources.generated.resources.logger_precision_weighed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The amount sheet's hunger chips, in the design's order and with its short words. */
private val AskedHunger: List<Pair<Hunger, StringResource>> = listOf(
    Hunger.NotHungry to Res.string.logger_hunger_not,
    Hunger.Peckish to Res.string.logger_hunger_bit,
    Hunger.Hungry to Res.string.logger_hunger_hungry,
    Hunger.VeryHungry to Res.string.logger_hunger_very,
)

private val Precision.label: StringResource
    get() = when (this) {
        Precision.Weighed -> Res.string.logger_precision_weighed
        Precision.Estimate -> Res.string.logger_precision_estimate
        Precision.Named -> Res.string.logger_precision_named
    }

/**
 * The amount sheet's body: a back arrow and the food, how sure, the amount
 * and its units, the policy-gated tiles, optional hunger, and the two pinned
 * buttons. Stateless; the entry point puts it in a `BottomSheet` with a fixed
 * height so the buttons stay at the bottom.
 */
@Composable
fun AmountSheetContent(
    state: AmountState,
    onAction: (AmountAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.spacing.page),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
        ) {
            SheetHeader(
                title = state.name,
                subtitle = when {
                    state.perUnit.isEmpty() -> state.tag
                    state.tag != null -> stringResource(Res.string.logger_amount_per, state.tag, state.perUnit)
                    else -> stringResource(Res.string.logger_amount_per_only, state.perUnit)
                },
                onBack = { onAction(AmountAction.BackTapped) },
            )
            if (state.missing) {
                Text(text = stringResource(Res.string.logger_amount_missing), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            } else if (!state.loading) {
                AmountBody(state, onAction)
            }
        }
        if (!state.loading && !state.missing) Buttons(state, onAction)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountBody(state: AmountState, onAction: (AmountAction) -> Unit) {
    Labeled(stringResource(Res.string.logger_amount_sure)) {
        SegmentedControl(
            options = Precision.entries,
            selected = state.precision,
            onSelect = { onAction(AmountAction.PrecisionPicked(it)) },
            label = { stringResource(it.label) },
        )
    }
    if (state.showAmount) {
        Labeled(stringResource(Res.string.logger_amount)) {
            Stepper(
                amount = state.amountText,
                onDecrease = { onAction(AmountAction.LessTapped) },
                onIncrease = { onAction(AmountAction.MoreTapped) },
                canDecrease = state.canLess,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                state.units.forEachIndexed { index, unit ->
                    ChoiceChip(label = unit, selected = index == state.unitIndex, onClick = { onAction(AmountAction.UnitPicked(index)) })
                }
            }
        }
    }
    if (state.nutrition.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            state.nutrition.forEach { tile ->
                NutrientTile(
                    metric = tile.metric,
                    label = stringResource(tile.label),
                    display = tile.display,
                    labelColor = labelColor(tile.metric),
                    modifier = Modifier.weight(if (tile.metric == Metric.Calories) ENERGY_TILE_WEIGHT else 1f),
                )
            }
        }
    }
    if (state.askTags) {
        Labeled(stringResource(Res.string.logger_amount_hunger)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                AskedHunger.forEach { (hunger, label) ->
                    ChoiceChip(
                        label = stringResource(label),
                        selected = hunger == state.hunger,
                        onClick = { onAction(AmountAction.HungerTapped(hunger)) },
                    )
                }
            }
            InlineLink(stringResource(Res.string.logger_amount_stop_asking)) { onAction(AmountAction.StopAskingTapped) }
        }
    }
}

@Composable
private fun labelColor(metric: Metric): ColorResource {
    val nutrients = AppTheme.colors.nutrients
    return when (metric) {
        Metric.Protein -> nutrients.protein.content
        Metric.Carbs -> nutrients.carbs.content
        Metric.Fat -> nutrients.fat.content
        else -> AppTheme.colors.muted
    }
}

/**
 * "Pick and keep going" beside "Add to {meal}". The design draws them the same width; at phone
 * width the longer label needs its own room, so it sizes to its text and "Add to" takes the rest.
 */
@Composable
private fun Buttons(state: AmountState, onAction: (AmountAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, top = AppTheme.spacing.row, bottom = AppTheme.spacing.page),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ButtonPrimary(
            onClick = { onAction(AmountAction.PickAndKeepGoingTapped) },
            style = ButtonStyle.Tonal,
        ) {
            Text(stringResource(Res.string.logger_amount_keep_going), typography = AppTheme.type.buttonSmall, maxLines = 1)
        }
        ButtonPrimary(onClick = { onAction(AmountAction.AddToMealTapped) }, modifier = Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.logger_add_to, stringResource(state.slot.sentenceName)),
                typography = AppTheme.type.buttonSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun Labeled(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        SectionLabel(label)
        content()
    }
}

private const val ENERGY_TILE_WEIGHT = 1.3f
