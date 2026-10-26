package com.opson.features.trends.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.opson.libraries.diary.ui.tileColors
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientFillBar
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.ui.components.ColorDot
import com.opson.libraries.ui.components.DotStrip
import com.opson.libraries.ui.components.DotStripColumn
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.IntakeBarChart
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.TrendLine
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconSize
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.Dimension
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.trends_average
import opson.libraries.resources.generated.resources.trends_intake_caption
import opson.libraries.resources.generated.resources.trends_nutrients
import opson.libraries.resources.generated.resources.trends_range_3m
import opson.libraries.resources.generated.resources.trends_range_4w
import opson.libraries.resources.generated.resources.trends_recap
import opson.libraries.resources.generated.resources.trends_recap_breakfast
import opson.libraries.resources.generated.resources.trends_recap_breakfast_value
import opson.libraries.resources.generated.resources.trends_recap_caption
import opson.libraries.resources.generated.resources.trends_recap_days
import opson.libraries.resources.generated.resources.trends_recap_days_value
import opson.libraries.resources.generated.resources.trends_recap_new_foods
import opson.libraries.resources.generated.resources.trends_settings_cd
import opson.libraries.resources.generated.resources.trends_title
import opson.libraries.resources.generated.resources.trends_weight
import opson.libraries.resources.generated.resources.trends_weight_hidden
import opson.libraries.resources.generated.resources.trends_weight_none
import opson.libraries.resources.generated.resources.trends_weight_now
import opson.libraries.resources.generated.resources.trends_weight_trend
import org.jetbrains.compose.resources.stringResource

/** The Trends tab. Stateless: everything on it comes from [state], already resolved by the policy. */
@Composable
fun TrendsScreen(
    state: TrendsState,
    onAction: (TrendsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        TrendsContent(state, onAction, Modifier.padding(padding).verticalScroll(rememberScrollState()))
    }
}

/** The page without its scaffold, so previews and the leak test render it at full height. */
@Composable
internal fun TrendsContent(
    state: TrendsState,
    onAction: (TrendsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        Header(state.rangeLabel, onSettings = { onAction(TrendsAction.SettingsTapped) })
        state.intake?.let { IntakeCardView(it) }
        state.recap?.let { RecapCardView(it) }
        state.nutrients?.let { NutrientsCardView(it, onClick = { onAction(TrendsAction.NutrientsCardTapped) }) }
        state.weight?.let { WeightCardView(it, onClick = { onAction(TrendsAction.WeightCardTapped) }) }
    }
}

@Composable
private fun Header(rangeLabel: RangeLabel?, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimension.D50)) {
            val range = when (rangeLabel) {
                null -> null
                is RangeLabel.Week -> rangeLabel.text
                RangeLabel.LastFourWeeks -> stringResource(Res.string.trends_range_4w)
                RangeLabel.LastThreeMonths -> stringResource(Res.string.trends_range_3m)
            }
            if (range != null) Text(text = range, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            Text(text = stringResource(Res.string.trends_title), typography = AppTheme.type.h1, color = AppTheme.colors.ink)
        }
        RoundIconButton(
            icon = AppIcons.Tune.described(stringResource(Res.string.trends_settings_cd)),
            onClick = onSettings,
            size = AppTheme.spacing.touchTarget,
            iconSize = IconSize.AppBar.dp,
            containerColor = AppTheme.colors.surface,
            contentColor = AppTheme.colors.ink,
        )
    }
}

@Composable
private fun IntakeCardView(card: IntakeCard) {
    TrendsCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            Text(
                text = stringResource(card.title),
                typography = AppTheme.type.secondarySmallStrong,
                color = AppTheme.colors.muted,
                modifier = Modifier.weight(1f),
            )
            // "Average" is a label beside the number, so the metric tag carries only the amount.
            NutrientVisible(card.average) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D100)) {
                    Text(text = stringResource(Res.string.trends_average), typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.ink)
                    NutrientText(
                        metric = Metric.Calories,
                        display = card.average,
                        typography = AppTheme.type.secondaryStrong.Tabular,
                        color = AppTheme.colors.ink,
                    )
                }
            }
        }
        IntakeBarChart(bars = card.bars, band = card.band)
        Text(
            text = stringResource(Res.string.trends_intake_caption),
            typography = AppTheme.type.secondarySmall,
            color = AppTheme.colors.muted,
        )
    }
}

@Composable
private fun RecapCardView(card: RecapCard) {
    TrendsCard {
        Text(
            text = stringResource(Res.string.trends_recap),
            typography = AppTheme.type.secondarySmallStrong,
            color = AppTheme.colors.muted,
        )
        RecapRow(
            label = stringResource(Res.string.trends_recap_breakfast),
            value = stringResource(Res.string.trends_recap_breakfast_value, card.breakfastDays),
        )
        RecapRow(label = stringResource(Res.string.trends_recap_new_foods), value = card.newFoods.toString())
        RecapRow(
            label = stringResource(Res.string.trends_recap_days),
            value = stringResource(Res.string.trends_recap_days_value, card.daysLogged),
        )
        DotStrip(
            columns = card.days.map { day ->
                DotStripColumn(
                    label = day.label,
                    dots = day.meals.map { slot -> slot?.tileColors()?.content ?: AppTheme.colors.track },
                )
            },
            modifier = Modifier.padding(top = Dimension.D100),
        )
        Text(
            text = stringResource(Res.string.trends_recap_caption),
            typography = AppTheme.type.secondarySmall,
            color = AppTheme.colors.muted,
        )
    }
}

@Composable
private fun RecapRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimension.D400)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            Text(text = label, typography = AppTheme.type.body, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
            Text(text = value, typography = AppTheme.type.numeric, color = AppTheme.colors.ink)
        }
        HorizontalDivider(color = AppTheme.colors.line)
    }
}

@Composable
private fun NutrientsCardView(card: NutrientsCard, onClick: () -> Unit) {
    TrendsCard(onClick = onClick) {
        CardTitle(title = stringResource(Res.string.trends_nutrients), subtitle = stringResource(card.subtitle))
        card.macros.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimension.D700)) {
                pair.forEach { tile -> MacroTileView(tile, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MacroTileView(tile: MacroTile, modifier: Modifier = Modifier) {
    val color = AppTheme.colors.of(tile.metric)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimension.D100)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimension.D200),
        ) {
            ColorDot(color)
            Text(
                text = stringResource(tile.label),
                typography = AppTheme.type.secondarySmallStrong,
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            NutrientText(
                metric = tile.metric,
                display = tile.display,
                typography = AppTheme.type.secondarySmall.Tabular,
                color = AppTheme.colors.muted,
                maxLines = 1,
            )
        }
        NutrientFillBar(metric = tile.metric, display = tile.display, color = color)
    }
}

@Composable
private fun WeightCardView(card: WeightCard, onClick: () -> Unit) {
    TrendsCard(onClick = onClick) {
        val subtitle = when (val sub = card.subtitle) {
            WeightSubtitle.Hidden -> stringResource(Res.string.trends_weight_hidden)
            WeightSubtitle.NoWeighIns -> stringResource(Res.string.trends_weight_none)
            WeightSubtitle.Trend -> stringResource(Res.string.trends_weight_trend)
            is WeightSubtitle.Latest -> sub.dayText ?: stringResource(Res.string.trends_weight_now)
        }
        CardTitle(title = stringResource(Res.string.trends_weight), subtitle = subtitle) {
            card.now?.let {
                NutrientText(Metric.Weight, it, typography = AppTheme.type.numeric, color = AppTheme.colors.ink)
            }
        }
        if (!card.trend.isNullOrEmpty()) {
            TrendLine(
                points = card.trend,
                dots = null,
                modifier = Modifier.fillMaxWidth().height(Dimension.D1400),
            )
        }
    }
}

/** A title over a muted subtitle, then [trailing] and the chevron that says the card opens a page. */
@Composable
private fun CardTitle(title: String, subtitle: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Column(Modifier.weight(1f)) {
            Text(text = title, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
            Text(text = subtitle, typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
        }
        trailing()
        Icon(icon = AppIcons.ChevronRight, size = IconSize.AppBar.dp, color = AppTheme.colors.muted)
    }
}

/** The Trends card: inside the page padding, as the handoff draws this page, not edge to edge. */
@Composable
private fun TrendsCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    EdgeCard(
        edgeToEdge = false,
        onClick = onClick,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        content = content,
    )
}
