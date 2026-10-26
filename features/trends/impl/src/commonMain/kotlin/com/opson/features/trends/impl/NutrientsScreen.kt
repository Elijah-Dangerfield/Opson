package com.opson.features.trends.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.opson.features.trends.NutrientsRange
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientFillBar
import com.opson.libraries.nutrition.ui.NutrientFillBarDefaults
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.components.ColorDot
import com.opson.libraries.ui.components.DotDefaults
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.IconTile
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.Dimension
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.trends_back
import opson.libraries.resources.generated.resources.trends_micros_caption
import opson.libraries.resources.generated.resources.trends_micros_off_body
import opson.libraries.resources.generated.resources.trends_micros_off_button
import opson.libraries.resources.generated.resources.trends_micros_off_title
import opson.libraries.resources.generated.resources.trends_nutrients_day
import opson.libraries.resources.generated.resources.trends_nutrients_macros
import opson.libraries.resources.generated.resources.trends_nutrients_nothing
import opson.libraries.resources.generated.resources.trends_nutrients_scores
import opson.libraries.resources.generated.resources.trends_nutrients_title
import opson.libraries.resources.generated.resources.trends_nutrients_week
import opson.libraries.resources.generated.resources.trends_nutrients_week_sub
import opson.libraries.resources.generated.resources.trends_title
import org.jetbrains.compose.resources.stringResource

/** Nutrients detail. Stateless; every amount arrives resolved. */
@Composable
fun NutrientsScreen(
    state: NutrientsState,
    onAction: (NutrientsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        NutrientsContent(state, onAction, Modifier.padding(padding).verticalScroll(rememberScrollState()))
    }
}

/** The page without its scaffold, for previews and the leak test. */
@Composable
internal fun NutrientsContent(
    state: NutrientsState,
    onAction: (NutrientsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D100)) {
            val back = when (val label = state.backLabel) {
                BackLabel.Trends -> stringResource(Res.string.trends_title)
                is BackLabel.Day -> label.text
            }
            PageHeader(
                title = stringResource(Res.string.trends_nutrients_title),
                backLabel = stringResource(Res.string.trends_back, back),
                onBack = { onAction(NutrientsAction.BackTapped) },
            )
            when (val subtitle = state.subtitle) {
                null -> Unit
                is NutrientsSubtitle.Day -> Subtitle(subtitle.text)
                is NutrientsSubtitle.WeekAverage -> Subtitle(stringResource(Res.string.trends_nutrients_week_sub, subtitle.range))
            }
        }
        SegmentedControl(
            options = NutrientsRange.entries,
            selected = state.range,
            onSelect = { onAction(NutrientsAction.RangePicked(it)) },
            label = {
                stringResource(
                    when (it) {
                        NutrientsRange.Day -> Res.string.trends_nutrients_day
                        NutrientsRange.WeekAverage -> Res.string.trends_nutrients_week
                    },
                )
            },
        )
        state.macros?.let { rows ->
            ListCard(title = stringResource(Res.string.trends_nutrients_macros)) {
                rows.forEach { row -> MacroRowView(row) }
            }
        }
        state.scores?.let { rows ->
            ListCard(title = stringResource(Res.string.trends_nutrients_scores)) {
                rows.forEach { row ->
                    PercentRow(
                        label = stringResource(row.label),
                        reference = null,
                        metricDisplay = row.display,
                        color = AppTheme.colors.accent,
                    )
                }
            }
        }
        state.micros?.let { groups ->
            groups.forEach { group ->
                ListCard(title = stringResource(group.title)) {
                    val color = AppTheme.colors.of(group.group)
                    group.rows.forEach { row ->
                        PercentRow(stringResource(row.label), row.reference, row.display, color)
                    }
                }
            }
            Text(
                text = stringResource(Res.string.trends_micros_caption),
                typography = AppTheme.type.secondarySmall,
                color = AppTheme.colors.muted,
            )
        }
        state.microsOff?.let { off -> MicrosOffCard(off, onTurnOn = { onAction(NutrientsAction.ShowVitaminsTapped) }) }
        if (state.nothing) {
            EdgeCard(edgeToEdge = false) {
                Text(
                    text = stringResource(Res.string.trends_nutrients_nothing),
                    typography = AppTheme.type.body,
                    color = AppTheme.colors.muted,
                )
            }
        }
    }
}

@Composable
private fun Subtitle(text: String) {
    Text(text = text, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
}

/** A card of divided rows under a small muted title. */
@Composable
private fun ListCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = PaddingValues(horizontal = AppTheme.spacing.cardHorizontal, vertical = Dimension.D200),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = title,
            typography = AppTheme.type.sectionLabel,
            color = AppTheme.colors.muted,
            modifier = Modifier.padding(top = Dimension.D400, bottom = Dimension.D200),
        )
        content()
    }
}

@Composable
private fun MacroRowView(row: NutrientRow) {
    val color = AppTheme.colors.of(row.metric)
    HorizontalDivider(color = AppTheme.colors.line)
    Column(
        modifier = Modifier.padding(vertical = Dimension.D400),
        verticalArrangement = Arrangement.spacedBy(Dimension.D200),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
        ) {
            ColorDot(color, size = DotDefaults.LargeSize)
            Text(
                text = stringResource(row.label),
                typography = AppTheme.type.bodyBold,
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            NutrientText(
                metric = row.metric,
                display = row.display,
                typography = AppTheme.type.menuItem.Tabular,
                color = AppTheme.colors.muted,
            )
        }
        NutrientFillBar(row.metric, row.display, color, height = NutrientFillBarDefaults.TallHeight)
    }
}

/** A vitamin, mineral or score: label (and reference), a bar, and the percent at the end. */
@Composable
private fun PercentRow(
    label: String,
    reference: String?,
    metricDisplay: Display,
    color: ColorResource,
) {
    HorizontalDivider(color = AppTheme.colors.line)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimension.D300),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimension.D400),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = label, typography = AppTheme.type.menuItem, color = AppTheme.colors.ink)
            if (reference != null) {
                Text(text = reference, typography = AppTheme.type.caption, color = AppTheme.colors.muted)
            }
        }
        NutrientFillBar(
            metric = Metric.Micros,
            display = metricDisplay,
            color = color,
            height = NutrientFillBarDefaults.TallHeight,
            modifier = Modifier.weight(PERCENT_BAR_WEIGHT),
        )
        NutrientText(
            metric = Metric.Micros,
            display = metricDisplay,
            typography = AppTheme.type.numericSmall,
            color = AppTheme.colors.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = Dimension.D1300),
        )
    }
}

@Composable
private fun MicrosOffCard(off: MicrosOff, onTurnOn: () -> Unit) {
    EdgeCard(edgeToEdge = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            IconTile(icon = AppIcons.Science, tone = AppTheme.colors.nutrients.fiber)
            Text(text = stringResource(Res.string.trends_micros_off_title), typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
        }
        Text(
            text = stringResource(Res.string.trends_micros_off_body),
            typography = AppTheme.type.menuItem,
            color = AppTheme.colors.muted,
        )
        if (off.canTurnOn) {
            ButtonPrimary(onClick = onTurnOn, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(Res.string.trends_micros_off_button))
            }
        }
    }
}

/** The bar takes this share of the row against the label's 1, near the handoff's 92 of about 220. */
private const val PERCENT_BAR_WEIGHT = 0.7f
