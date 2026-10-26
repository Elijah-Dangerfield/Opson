package com.opson.features.trends.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.components.BackLink
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.TrendLine
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.text.MaskedTextField
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.Dimension
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.trends_title
import opson.libraries.resources.generated.resources.trends_weight_average
import opson.libraries.resources.generated.resources.trends_weight_chip_1m
import opson.libraries.resources.generated.resources.trends_weight_chip_1w
import opson.libraries.resources.generated.resources.trends_weight_chip_3m
import opson.libraries.resources.generated.resources.trends_weight_hidden_body
import opson.libraries.resources.generated.resources.trends_weight_hidden_title
import opson.libraries.resources.generated.resources.trends_weight_log
import opson.libraries.resources.generated.resources.trends_weight_now
import opson.libraries.resources.generated.resources.trends_weight_range_1m
import opson.libraries.resources.generated.resources.trends_weight_range_1w
import opson.libraries.resources.generated.resources.trends_weight_range_3m
import opson.libraries.resources.generated.resources.trends_weight_save
import opson.libraries.resources.generated.resources.trends_weight_saved
import opson.libraries.resources.generated.resources.trends_weight_saved_hidden
import opson.libraries.resources.generated.resources.trends_weight_title
import opson.libraries.resources.generated.resources.trends_weight_unit_kg
import opson.libraries.resources.generated.resources.trends_weight_unit_lb
import opson.libraries.resources.generated.resources.trends_weight_view_hidden
import opson.libraries.resources.generated.resources.trends_weight_view_numbers
import opson.libraries.resources.generated.resources.trends_weight_view_trend
import org.jetbrains.compose.resources.stringResource

/** The weight page. Stateless; what shows and what is masked is decided in [WeightStateBuilder]. */
@Composable
fun WeightScreen(
    state: WeightState,
    onAction: (WeightAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        WeightContent(state, onAction, Modifier.padding(padding).verticalScroll(rememberScrollState()))
    }
}

/** The page without its scaffold, for previews and the leak test. */
@Composable
internal fun WeightContent(
    state: WeightState,
    onAction: (WeightAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D100)) {
            // "Trends" whatever opened it, as the prototype.
            BackLink(label = stringResource(Res.string.trends_title), onClick = { onAction(WeightAction.BackTapped) })
            Text(text = stringResource(Res.string.trends_weight_title), typography = AppTheme.type.subH1, color = AppTheme.colors.ink)
        }
        SegmentedControl(
            options = WeightView.entries,
            selected = state.selected,
            onSelect = { onAction(WeightAction.ViewPicked(it)) },
            label = {
                stringResource(
                    when (it) {
                        WeightView.Hidden -> Res.string.trends_weight_view_hidden
                        WeightView.TrendOnly -> Res.string.trends_weight_view_trend
                        WeightView.Numbers -> Res.string.trends_weight_view_numbers
                    },
                )
            },
        )
        if (state.now != null) NowCard(state)
        state.chart?.let { ChartCard(it, state.range, onAction) }
        if (state.hiddenCard) {
            EdgeCard(edgeToEdge = false, verticalArrangement = Arrangement.spacedBy(Dimension.D200)) {
                Text(text = stringResource(Res.string.trends_weight_hidden_title), typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
                Text(text = stringResource(Res.string.trends_weight_hidden_body), typography = AppTheme.type.menuItem, color = AppTheme.colors.muted)
            }
        }
        LogCard(state, onAction)
    }
}

@Composable
private fun NowCard(state: WeightState) {
    val now = state.now ?: return
    EdgeCard(edgeToEdge = false) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimension.D100)) {
                NutrientText(Metric.Weight, now, typography = AppTheme.type.h1.Tabular, color = AppTheme.colors.ink)
                Text(
                    text = when (val measured = state.nowWhen) {
                        is WeighInWhen.Day -> measured.text
                        WeighInWhen.ThisMorning, null -> stringResource(Res.string.trends_weight_now)
                    },
                    typography = AppTheme.type.secondary,
                    color = AppTheme.colors.muted,
                )
            }
            state.average?.let { average ->
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Dimension.D100)) {
                    NutrientText(Metric.Weight, average, typography = AppTheme.type.numeric, color = AppTheme.colors.ink)
                    Text(text = stringResource(Res.string.trends_weight_average), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
                }
            }
        }
    }
}

@Composable
private fun ChartCard(chart: WeightChart, range: WeightRange, onAction: (WeightAction) -> Unit) {
    EdgeCard(edgeToEdge = false) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
        ) {
            Text(
                text = stringResource(
                    when (range) {
                        WeightRange.W1 -> Res.string.trends_weight_range_1w
                        WeightRange.M1 -> Res.string.trends_weight_range_1m
                        WeightRange.M3 -> Res.string.trends_weight_range_3m
                    },
                ),
                typography = AppTheme.type.secondarySmallStrong,
                color = AppTheme.colors.muted,
                modifier = Modifier.weight(1f),
            )
            SegmentedControl(
                options = WeightRange.entries,
                selected = range,
                onSelect = { onAction(WeightAction.RangePicked(it)) },
                label = {
                    stringResource(
                        when (it) {
                            WeightRange.W1 -> Res.string.trends_weight_chip_1w
                            WeightRange.M1 -> Res.string.trends_weight_chip_1m
                            WeightRange.M3 -> Res.string.trends_weight_chip_3m
                        },
                    )
                },
                minHeight = AppTheme.spacing.compactTouchTarget,
                modifier = Modifier.weight(1f),
            )
        }
        TrendLine(points = chart.trend, dots = chart.dots)
        Text(text = stringResource(chart.caption), typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
    }
}

@Composable
private fun LogCard(state: WeightState, onAction: (WeightAction) -> Unit) {
    EdgeCard(edgeToEdge = false) {
        Text(text = stringResource(Res.string.trends_weight_log), typography = AppTheme.type.secondarySmallStrong, color = AppTheme.colors.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D300), verticalAlignment = Alignment.CenterVertically) {
            MaskedTextField(
                value = state.input,
                onValueChange = { onAction(WeightAction.WeightTyped(it)) },
                masked = state.inputMasked,
                placeholder = stringResource(
                    when (state.unit) {
                        WeightUnit.Kg -> Res.string.trends_weight_unit_kg
                        WeightUnit.Lb -> Res.string.trends_weight_unit_lb
                    },
                ),
                keyboardActions = KeyboardActions(onDone = { onAction(WeightAction.SaveTapped) }),
                modifier = Modifier.weight(1f),
            )
            ButtonPrimary(onClick = { onAction(WeightAction.SaveTapped) }) {
                Text(text = stringResource(Res.string.trends_weight_save))
            }
        }
        state.saved?.let { note ->
            Text(
                text = stringResource(
                    when (note) {
                        SavedNote.Plain -> Res.string.trends_weight_saved
                        SavedNote.OutOfView -> Res.string.trends_weight_saved_hidden
                    },
                ),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
            )
        }
    }
}
