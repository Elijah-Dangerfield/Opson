package com.opson.features.logger.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.EdgeCardDefaults
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.chip.PillChip
import com.opson.libraries.ui.components.dialog.bottomsheet.SheetFrame
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_add_to
import opson.libraries.resources.generated.resources.modes_amount_field
import opson.libraries.resources.generated.resources.modes_amount_quick
import opson.libraries.resources.generated.resources.modes_amount_title
import opson.libraries.resources.generated.resources.modes_hunger_a_little
import opson.libraries.resources.generated.resources.modes_hunger_hungry
import opson.libraries.resources.generated.resources.modes_hunger_not_really
import opson.libraries.resources.generated.resources.modes_hunger_question
import opson.libraries.resources.generated.resources.modes_hunger_very
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/*
 * The food detail in No numbers (design sync 4, 13.5e): the amount typed in household units with
 * its unit and a few quick amounts, and the optional hunger check. No nutrition anywhere, so no
 * portion, no day and no how sure. Kept apart from the Full numbers sheet so that one can be
 * rebuilt on its own.
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoNumbersAmount(state: AmountState, amount: NoNumbersAmount, onAction: (AmountAction) -> Unit) {
    EdgeCard(edgeToEdge = false, contentPadding = EdgeCardDefaults.InsetPadding, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Text(text = stringResource(Res.string.modes_amount_title), typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            NumberField(
                value = amount.typed,
                onValueChange = { onAction(AmountAction.AmountTyped(it)) },
                placeholder = stringResource(Res.string.modes_amount_field),
                modifier = Modifier.weight(1f),
            )
            UnitMenu(state, onAction, modifier = Modifier.weight(1f))
        }
        val unit = state.unit?.label
        if (state.quick.isNotEmpty() && unit != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                state.quick.forEach { quick ->
                    PillChip(
                        label = stringResource(Res.string.modes_amount_quick, quick.label, unit),
                        selected = quick.selected && amount.typed == state.count,
                        onClick = { onAction(AmountAction.QuickAmountPicked(quick.quantity)) },
                    )
                }
            }
        }
    }
    amount.hunger?.let { check ->
        EdgeCard(edgeToEdge = false, contentPadding = EdgeCardDefaults.InsetPadding, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            Text(text = stringResource(Res.string.modes_hunger_question), typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                HUNGER_ORDER.forEach { hunger ->
                    PillChip(
                        label = stringResource(hunger.askLabel),
                        selected = hunger == check.picked,
                        onClick = { onAction(AmountAction.HungerPicked(hunger)) },
                    )
                }
            }
        }
    }
}

/** "Add to Snack", alone: No numbers has no basket to keep going with. */
@Composable
internal fun AddButton(state: AmountState, onAction: (AmountAction) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = AppTheme.colors.line)
        ButtonPrimary(
            onClick = { onAction(AmountAction.AddToMealTapped) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, top = AppTheme.spacing.row, bottom = AppTheme.spacing.page),
        ) {
            Text(stringResource(Res.string.logger_add_to, stringResource(state.slot.sentenceName)), typography = AppTheme.type.buttonSmall, maxLines = 1)
        }
    }
}

/** Least to most, as the question reads. */
private val HUNGER_ORDER = listOf(Hunger.NotHungry, Hunger.Peckish, Hunger.Hungry, Hunger.VeryHungry)

/** The answers as the question asks them: "How hungry were you?" "A little". */
private val Hunger.askLabel: StringResource
    get() = when (this) {
        Hunger.NotHungry -> Res.string.modes_hunger_not_really
        Hunger.Peckish -> Res.string.modes_hunger_a_little
        Hunger.Hungry -> Res.string.modes_hunger_hungry
        Hunger.VeryHungry -> Res.string.modes_hunger_very
    }

/** 13.5e: two plates typed for a snack, "A little" picked. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodDetailNoNumbersPreview() = PreviewContent(darkTheme = true) {
    SheetFrame(heightFraction = AMOUNT_HEIGHT_FRACTION) {
        AmountSheetContent(LoggerFixtures.noNumbersAmountState(previewVisibilityPolicy(PresetId.NoNumbers)), onAction = {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FoodDetailNoNumbersLightPreview() = PreviewContent {
    SheetFrame(heightFraction = AMOUNT_HEIGHT_FRACTION) {
        AmountSheetContent(LoggerFixtures.noNumbersAmountState(previewVisibilityPolicy(PresetId.NoNumbers)), onAction = {})
    }
}
