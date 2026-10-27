package com.opson.libraries.diary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GoalStyle
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.ui.LocalVisibilityPolicy
import com.opson.libraries.nutrition.ui.NutrientBar
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.nutrition.ui.currentVisibilityPolicy
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.diary_meal_done_cd
import opson.libraries.resources.generated.resources.diary_meal_not_done_cd
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * One column of a day-totals strip (the logger's header): the amount, a thin
 * bar, and the label under it. Energy draws in the accent, macros in their own
 * colour. A hidden [display] removes the whole column, label included.
 *
 * The amount may wrap to two lines rather than truncate, for large text.
 */
@Composable
fun NutrientTotalColumn(
    metric: Metric,
    label: String,
    display: Display,
    modifier: Modifier = Modifier,
) {
    NutrientVisible(display) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NutrientText(
                metric = metric,
                display = display,
                typography = AppTheme.type.captionStrong.Tabular,
                color = AppTheme.colors.ink,
                maxLines = 2,
            )
            NutrientBar(
                metric = metric,
                display = display,
                color = metricColor(metric).color,
                trackColor = AppTheme.colors.track.color,
                modifier = Modifier.fillMaxWidth().height(4.dp),
            )
            Text(text = label, typography = AppTheme.type.navLabel, color = AppTheme.colors.muted, maxLines = 1)
        }
    }
}

@Composable
private fun metricColor(metric: Metric): ColorResource {
    val nutrients = AppTheme.colors.nutrients
    return when (metric) {
        Metric.Protein -> nutrients.protein.content
        Metric.Carbs -> nutrients.carbs.content
        Metric.Fat -> nutrients.fat.content
        Metric.Fiber -> nutrients.fiber.content
        Metric.Water -> nutrients.water.content
        else -> AppTheme.colors.accent
    }
}

/**
 * A meal and whether it is done, as a small pill: a filled check in the
 * meal's colour, or an empty circle. What the logger's header shows instead of
 * totals when no number is visible.
 */
@Composable
fun MealCheckChip(
    slot: MealSlot,
    done: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(slot.label)
    val description = stringResource(if (done) Res.string.diary_meal_done_cd else Res.string.diary_meal_not_done_cd, label)
    Row(
        modifier = modifier
            .background(AppTheme.colors.surface, AppTheme.shapes.pill)
            .clearAndSetSemantics { contentDescription = description }
            .padding(start = 7.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            icon = if (done) AppIcons.CheckCircle else AppIcons.RadioButtonUnchecked,
            size = 16.dp,
            color = if (done) slot.tileColors().content else AppTheme.colors.muted,
        )
        Text(text = label, typography = AppTheme.type.captionStrong, color = AppTheme.colors.ink, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(widthDp = 390)
@Composable
private fun DayTotalsPreview() {
    PreviewLightDark {
        val policy = currentVisibilityPolicy()
        fun context(of: Double) = FormatContext(of = of, bare = true, goal = GoalStyle.Slash)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple(Metric.Calories, "Energy", 1245.0 to 2100.0),
                    Triple(Metric.Protein, "Protein", 47.0 to 120.0),
                    Triple(Metric.Carbs, "Carbs", 160.0 to 250.0),
                    Triple(Metric.Fat, "Fat", 41.0 to 70.0),
                ).forEach { (metric, label, values) ->
                    NutrientTotalColumn(
                        metric = metric,
                        label = label,
                        display = policy.resolve(metric, values.first, Surface.Logger, context(values.second)),
                        modifier = Modifier.weight(if (metric == Metric.Calories) 1.35f else 1f),
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MealSlot.meals.forEachIndexed { index, slot -> MealCheckChip(slot = slot, done = index < 2) }
            }
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun DayTotalsFuelPreview() {
    CompositionLocalProvider(LocalVisibilityPolicy provides previewVisibilityPolicy(PresetId.MacrosOnly)) {
        DayTotalsPreview()
    }
}
