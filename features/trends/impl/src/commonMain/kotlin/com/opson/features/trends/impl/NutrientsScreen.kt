package com.opson.features.trends.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.ui.components.ColorDot
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.EmptyCard
import com.opson.libraries.ui.components.EdgeCardDefaults
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.ListRowChevron
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.RangeBar
import com.opson.libraries.ui.components.RangeBarDefaults
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.ToneIcon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.background
import androidx.compose.ui.draw.clip
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.trends_back
import opson.libraries.resources.generated.resources.trends_loading_cd
import opson.libraries.resources.generated.resources.trends_micro_of
import opson.libraries.resources.generated.resources.trends_micro_of_limit
import opson.libraries.resources.generated.resources.trends_micros_caption
import opson.libraries.resources.generated.resources.trends_micros_off_body
import opson.libraries.resources.generated.resources.trends_micros_off_link
import opson.libraries.resources.generated.resources.trends_micros_off_title
import opson.libraries.resources.generated.resources.trends_nutrients_day
import opson.libraries.resources.generated.resources.trends_nutrients_goal
import opson.libraries.resources.generated.resources.trends_nutrients_empty_day
import opson.libraries.resources.generated.resources.trends_nutrients_empty_week
import opson.libraries.resources.generated.resources.trends_nutrients_nothing
import opson.libraries.resources.generated.resources.trends_nutrients_range
import opson.libraries.resources.generated.resources.trends_nutrients_title
import opson.libraries.resources.generated.resources.trends_nutrients_week
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
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        val back = when (val label = state.backLabel) {
            BackLabel.Trends -> stringResource(Res.string.trends_title)
            is BackLabel.Day -> label.text
        }
        PageHeader(
            title = stringResource(Res.string.trends_nutrients_title),
            subtitle = when (val subtitle = state.subtitle) {
                null -> null
                is NutrientsSubtitle.Day -> subtitle.text
                is NutrientsSubtitle.WeekAverage -> subtitle.range
            },
            backLabel = stringResource(Res.string.trends_back, back),
            onBack = { onAction(NutrientsAction.BackTapped) },
        )
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
            minHeight = MinTouchTarget,
        )
        if (state.loading) {
            DelayedLoadingIndicator(contentDescription = stringResource(Res.string.trends_loading_cd))
            return@Column
        }
        if (state.energy != null || state.macros != null) {
            ListCard {
                var first = true
                state.energy?.let {
                    EnergyRowView(it)
                    first = false
                }
                state.macros?.forEach { row ->
                    if (!first) HorizontalDivider(color = AppTheme.colors.line)
                    MacroRowView(row)
                    first = false
                }
            }
        }
        state.micros?.let { groups ->
            groups.forEach { group ->
                ListCard {
                    Text(
                        text = stringResource(group.title),
                        typography = AppTheme.type.secondaryStrong.Bold,
                        color = AppTheme.colors.muted,
                        modifier = Modifier.padding(top = Dimension.D500, bottom = Dimension.D300),
                    )
                    val color = AppTheme.colors.of(group.group)
                    group.rows.forEach { row -> PercentRow(row, color) }
                }
            }
            Text(
                text = stringResource(Res.string.trends_micros_caption),
                typography = AppTheme.type.body,
                color = AppTheme.colors.muted,
            )
        }
        if (state.empty) {
            EmptyCard(
                message = stringResource(
                    when (state.range) {
                        NutrientsRange.Day -> Res.string.trends_nutrients_empty_day
                        NutrientsRange.WeekAverage -> Res.string.trends_nutrients_empty_week
                    },
                ),
                icon = AppIcons.Science,
            )
        }
        if (state.microsOff) MicrosOffCard(onModes = { onAction(NutrientsAction.ModesTapped) })
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

/** A card of divided rows. */
@Composable
private fun ListCard(content: @Composable ColumnScope.() -> Unit) {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = PaddingValues(horizontal = Dimension.D700, vertical = Dimension.D200),
        verticalArrangement = Arrangement.Top,
        content = content,
    )
}

@Composable
private fun EnergyRowView(row: EnergyRow) {
    Column(
        modifier = Modifier.padding(vertical = Dimension.D500),
        verticalArrangement = Arrangement.spacedBy(Dimension.D300),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
        ) {
            Text(
                text = stringResource(Metric.Calories.label),
                typography = AppTheme.type.cardTitle,
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            // One word gap between the amount, "· range" and the range, as in a sentence.
            Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D100)) {
                NutrientText(metric = Metric.Calories, display = row.eaten, typography = AppTheme.type.body.Tabular, color = AppTheme.colors.muted)
                // "· range" is a label; the range itself renders through the policy's tagged text.
                NutrientVisible(row.range) {
                    Text(
                        text = stringResource(if (row.singleGoal) Res.string.trends_nutrients_goal else Res.string.trends_nutrients_range),
                        typography = AppTheme.type.body,
                        color = AppTheme.colors.muted,
                        maxLines = 1,
                    )
                    NutrientText(metric = Metric.Calories, display = row.range, typography = AppTheme.type.body.Tabular, color = AppTheme.colors.muted, maxLines = 1)
                }
            }
        }
        RangeBar(
            fill = row.eaten.fillFraction() ?: 0f,
            rangeStart = if (row.range is Display.Shown) row.rangeStart else 0f,
            rangeEnd = if (row.range is Display.Shown) row.rangeEnd else 0f,
            colors = RangeBarDefaults.onSurface(),
            height = RangeBarDefaults.CompactHeight,
        )
    }
}

@Composable
private fun MacroRowView(row: NutrientRow) {
    val color = AppTheme.colors.of(row.metric)
    Column(
        modifier = Modifier.padding(vertical = Dimension.D500),
        verticalArrangement = Arrangement.spacedBy(Dimension.D400),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimension.D400),
        ) {
            ColorDot(color, size = Dimension.D400)
            Text(
                text = stringResource(row.label),
                typography = AppTheme.type.cardTitle,
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            NutrientText(
                metric = row.metric,
                display = row.display,
                typography = AppTheme.type.body.Tabular,
                color = AppTheme.colors.muted,
            )
        }
        NutrientFillBar(row.metric, row.display, color, height = NutrientFillBarDefaults.TallHeight)
    }
}

/** A vitamin or mineral: name over `of 700 µg`, a bar that stops at 100%, and the percent at the end. */
@Composable
private fun PercentRow(row: MicroRow, color: ColorResource) {
    HorizontalDivider(color = AppTheme.colors.line)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimension.D400),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimension.D700),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = stringResource(row.label), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
            Text(
                text = stringResource(if (row.isLimit) Res.string.trends_micro_of_limit else Res.string.trends_micro_of, row.reference),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
            )
        }
        NutrientFillBar(
            metric = Metric.Micros,
            display = row.display,
            color = color,
            height = NutrientFillBarDefaults.TallHeight,
            modifier = Modifier.width(Dimension.D1900),
        )
        NutrientText(
            metric = Metric.Micros,
            display = row.display,
            typography = AppTheme.type.numeric.ExtraBold,
            color = AppTheme.colors.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = Dimension.D1400),
        )
    }
}

/** Vitamins and minerals are off: they turn on in Modes and numbers, where the pause applies. */
@Composable
private fun MicrosOffCard(onModes: () -> Unit) {
    EdgeCard(edgeToEdge = false, contentPadding = EdgeCardDefaults.InsetPadding, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            ToneIcon(icon = AppIcons.Science, tone = AppTheme.colors.nutrients.fiber)
            Text(text = stringResource(Res.string.trends_micros_off_title), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
        }
        Text(text = stringResource(Res.string.trends_micros_off_body), typography = AppTheme.type.body, color = AppTheme.colors.muted)
        ListRow(
            title = stringResource(Res.string.trends_micros_off_link),
            onClick = onModes,
            minHeight = MinTouchTarget,
            modifier = Modifier.clip(AppTheme.shapes.inputLarge).background(AppTheme.colors.bg).padding(horizontal = Dimension.D700),
            trailing = { ListRowChevron() },
        )
    }
}

/** How far the bar fills for a shown or softened amount; the policy already decided either may show. */
private fun Display.fillFraction(): Float? = when (this) {
    is Display.Shown -> fraction
    is Display.Soft -> fraction
    Display.Hidden -> null
}
