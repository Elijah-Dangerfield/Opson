package com.opson.features.trends.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.EdgeCardDefaults
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.TrendLine
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconTile
import com.opson.system.color.ToneColors
import com.opson.libraries.ui.components.text.MaskedTextField
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.trends_back
import opson.libraries.resources.generated.resources.trends_title
import opson.libraries.resources.generated.resources.trends_weight_caption
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
import opson.libraries.resources.generated.resources.trends_weight_saved_trend
import opson.libraries.resources.generated.resources.trends_weight_title
import opson.libraries.resources.generated.resources.trends_weight_trend_label
import opson.libraries.resources.generated.resources.trends_weight_trend_only_note
import opson.libraries.resources.generated.resources.trends_weight_unit_kg
import opson.libraries.resources.generated.resources.trends_weight_unit_lb
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
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        // "Trends" whatever opened it, as the prototype; it is the arrow's spoken label.
        PageHeader(
            title = stringResource(Res.string.trends_weight_title),
            backLabel = stringResource(Res.string.trends_back, stringResource(Res.string.trends_title)),
            onBack = { onAction(WeightAction.BackTapped) },
        )
        when (val trend = state.trend) {
            is Display.Shown -> {
                SummaryCard(trend, state)
                state.chart?.let { ChartCard(it, state.range, onAction) }
            }
            is Display.Soft -> SoftCard(trend, state.chart, state.range)
            Display.Hidden, null -> state.chart?.let { ChartCard(it, state.range, onAction) }
        }
        if (state.hiddenCard) HiddenCard()
        LogCard(state, onAction)
        state.saved?.let { SavedBanner(it) }
    }
}

@Composable
private fun WeightCard(content: @Composable ColumnScope.() -> Unit) {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = EdgeCardDefaults.InsetPadding,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        content = content,
    )
}

/** The trend as the large number and the latest reading small beside it: the same priority as the chart. */
@Composable
private fun SummaryCard(trend: Display, state: WeightState) {
    WeightCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimension.D100)) {
                Label(stringResource(Res.string.trends_weight_trend_label))
                // The trend arrives bare, so the unit can sit small beside it.
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Dimension.D200)) {
                    NutrientText(Metric.Weight, trend, typography = AppTheme.type.figureDisplay, color = AppTheme.colors.ink)
                    Text(
                        text = stringResource(state.unit.label),
                        typography = AppTheme.type.cardTitle,
                        color = AppTheme.colors.muted,
                        modifier = Modifier.padding(bottom = Dimension.D100),
                    )
                }
            }
            state.latest?.let { latest ->
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Dimension.D100)) {
                    Label(
                        when (val measured = state.latestWhen) {
                            is WeighInWhen.Day -> measured.text
                            WeighInWhen.ThisMorning, null -> stringResource(Res.string.trends_weight_now)
                        },
                    )
                    NutrientText(Metric.Weight, latest, typography = AppTheme.type.figureMedium.ExtraBold, color = AppTheme.colors.ink)
                }
            }
        }
    }
}

@Composable
private fun ChartCard(chart: WeightChart, range: WeightRange, onAction: (WeightAction) -> Unit) {
    WeightCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
        ) {
            Label(stringResource(range.label), Modifier.weight(1f))
            if (chart.ranges) {
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
                    minHeight = MinTouchTarget,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        TrendLine(points = chart.trend, dots = chart.dots)
        if (chart.from != null && chart.to != null) {
            Row(Modifier.fillMaxWidth()) {
                Text(text = chart.from, typography = AppTheme.type.secondary, color = AppTheme.colors.muted, modifier = Modifier.weight(1f))
                Text(text = chart.to, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            }
        }
        Text(text = stringResource(Res.string.trends_weight_caption), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
    }
}

/** Trend only: which way the line is going, as a word, and the line. No numbers, no weigh-in dots. */
@Composable
private fun SoftCard(word: Display, chart: WeightChart?, range: WeightRange) {
    WeightCard {
        Label(stringResource(range.label))
        NutrientText(Metric.Weight, word, typography = AppTheme.type.subH1, color = AppTheme.colors.ink)
        if (chart != null && chart.trend.isNotEmpty()) {
            TrendLine(
                points = chart.trend,
                dots = null,
                modifier = Modifier.fillMaxWidth().height(Dimension.D1900).padding(vertical = Dimension.D700),
            )
        }
    }
}

@Composable
private fun HiddenCard() {
    WeightCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            IconTile(icon = AppIcons.VisibilityOff, tone = ToneColors(AppTheme.colors.accent, AppTheme.colors.accentTint))
            Text(text = stringResource(Res.string.trends_weight_hidden_title), typography = AppTheme.type.sheetTitle, color = AppTheme.colors.ink)
        }
        Text(text = stringResource(Res.string.trends_weight_hidden_body), typography = AppTheme.type.body, color = AppTheme.colors.muted)
    }
}

@Composable
private fun LogCard(state: WeightState, onAction: (WeightAction) -> Unit) {
    WeightCard {
        Label(stringResource(Res.string.trends_weight_log))
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row), verticalAlignment = Alignment.CenterVertically) {
            MaskedTextField(
                value = state.input,
                onValueChange = { onAction(WeightAction.WeightTyped(it)) },
                masked = state.inputMasked,
                unit = stringResource(state.unit.label),
                keyboardActions = KeyboardActions(onDone = { onAction(WeightAction.SaveTapped) }),
                modifier = Modifier.weight(1f),
            )
            ButtonPrimary(onClick = { onAction(WeightAction.SaveTapped) }) {
                Text(text = stringResource(Res.string.trends_weight_save))
            }
        }
        if (state.trendOnlyNote) {
            Text(text = stringResource(Res.string.trends_weight_trend_only_note), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
    }
}

@Composable
private fun SavedBanner(note: SavedNote) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.inputLarge)
            .background(AppTheme.colors.accentTint)
            .padding(horizontal = AppTheme.spacing.cardHorizontal, vertical = Dimension.D700),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Icon(icon = AppIcons.CheckCircle, size = Dimension.D900, color = AppTheme.colors.accent)
        Text(
            text = stringResource(
                when (note) {
                    SavedNote.Plain -> Res.string.trends_weight_saved
                    SavedNote.TrendOnly -> Res.string.trends_weight_saved_trend
                    SavedNote.OutOfView -> Res.string.trends_weight_saved_hidden
                },
            ),
            typography = AppTheme.type.bodyBold,
            color = AppTheme.colors.accent,
        )
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text = text, typography = AppTheme.type.secondaryStrong.Bold, color = AppTheme.colors.muted, modifier = modifier)
}

private val WeightRange.label
    get() = when (this) {
        WeightRange.W1 -> Res.string.trends_weight_range_1w
        WeightRange.M1 -> Res.string.trends_weight_range_1m
        WeightRange.M3 -> Res.string.trends_weight_range_3m
    }

private val WeightUnit.label
    get() = when (this) {
        WeightUnit.Kg -> Res.string.trends_weight_unit_kg
        WeightUnit.Lb -> Res.string.trends_weight_unit_lb
    }
