package com.opson.features.logger.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.opson.libraries.diary.Fullness
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.ui.NutrientTile
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.diary.ui.label
import com.opson.libraries.diary.ui.pickerLabel
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.Stepper
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.chip.PillChip
import com.opson.libraries.ui.components.chip.PillChipSize
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_amount
import opson.libraries.resources.generated.resources.logger_amount_after
import opson.libraries.resources.generated.resources.logger_amount_before
import opson.libraries.resources.generated.resources.logger_amount_missing
import opson.libraries.resources.generated.resources.logger_amount_pick
import opson.libraries.resources.generated.resources.logger_amount_stop_asking
import opson.libraries.resources.generated.resources.logger_amount_sure
import org.jetbrains.compose.resources.stringResource

/** The hunger chips the amount sheet offers: a short version of the meal sheet's four. */
private val AskedHunger = listOf(Hunger.VeryHungry, Hunger.Peckish, Hunger.NotHungry)
private val AskedFullness = listOf(Fullness.StillHungry, Fullness.Satisfied, Fullness.VeryFull)

/**
 * The amount sheet's body: how sure, the amount, the policy-gated tiles, the
 * optional hunger and fullness chips, and "Pick this amount". Stateless; the
 * entry point puts it in a `BottomSheet` with `scrollableContent`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AmountSheetContent(
    state: AmountState,
    onAction: (AmountAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHeader(
            title = state.name,
            subtitle = state.tag,
            onClose = { onAction(AmountAction.CloseTapped) },
            closeButtonColor = AppTheme.colors.soft,
        )
        if (state.loading) return@Column
        if (state.missing) {
            Text(text = stringResource(Res.string.logger_amount_missing), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            return@Column
        }
        Labeled(stringResource(Res.string.logger_amount_sure)) {
            SegmentedControl(
                options = Precision.entries,
                selected = state.precision,
                onSelect = { onAction(AmountAction.PrecisionPicked(it)) },
                label = { stringResource(it.pickerLabel) },
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
                ) {
                    state.units.forEachIndexed { index, unit ->
                        PillChip(
                            label = unit,
                            selected = index == state.unitIndex,
                            onClick = { onAction(AmountAction.UnitPicked(index)) },
                            size = PillChipSize.Compact,
                        )
                    }
                }
            }
        }
        if (state.nutrition.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                state.nutrition.forEach { tile ->
                    NutrientTile(tile.metric, stringResource(tile.label), tile.display, Modifier.weight(1f))
                }
            }
        }
        if (state.askTags) {
            Labeled(stringResource(Res.string.logger_amount_before)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                    AskedHunger.forEach { hunger ->
                        PillChip(
                            label = stringResource(hunger.label),
                            selected = hunger == state.hunger,
                            onClick = { onAction(AmountAction.HungerTapped(hunger)) },
                            size = PillChipSize.Compact,
                        )
                    }
                }
            }
            Labeled(stringResource(Res.string.logger_amount_after)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                    AskedFullness.forEach { fullness ->
                        PillChip(
                            label = stringResource(fullness.label),
                            selected = fullness == state.fullness,
                            onClick = { onAction(AmountAction.FullnessTapped(fullness)) },
                            size = PillChipSize.Compact,
                        )
                    }
                }
            }
            ButtonGhost(onClick = { onAction(AmountAction.StopAskingTapped) }) {
                Text(stringResource(Res.string.logger_amount_stop_asking))
            }
        }
        ButtonPrimary(onClick = { onAction(AmountAction.PickTapped) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.logger_amount_pick))
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
