package com.opson.libraries.nutrition.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The handoff's thin macro bar (Trends, Nutrients detail) for a resolved
 * [display], in theme colours: full width, [height] tall, rounded, on the
 * track colour. Renders nothing when the display is hidden or has no goal to
 * fill towards, and carries the `metric:<key>` tag like every other number.
 */
@Composable
fun NutrientFillBar(
    metric: Metric,
    display: Display,
    color: ColorResource,
    modifier: Modifier = Modifier,
    trackColor: ColorResource = AppTheme.colors.track,
    height: Dp = NutrientFillBarDefaults.Height,
) {
    NutrientBar(
        metric = metric,
        display = display,
        color = color.color,
        trackColor = trackColor.color,
        modifier = modifier.fillMaxWidth().height(height),
        shape = AppTheme.shapes.bar,
    )
}

object NutrientFillBarDefaults {
    /** Macro tiles on Trends. */
    val Height: Dp = 5.dp

    /** Rows on the nutrients detail. */
    val TallHeight: Dp = 6.dp
}

@Preview(widthDp = 390)
@Composable
private fun NutrientFillBarPreview() {
    val policy = previewVisibilityPolicy()
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NutrientFillBar(
                metric = Metric.Protein,
                display = policy.resolve(Metric.Protein, 96.0, Surface.Week, FormatContext(of = 120.0)),
                color = AppTheme.colors.nutrients.protein.content,
            )
            NutrientFillBar(
                metric = Metric.Fiber,
                display = policy.resolve(Metric.Fiber, 24.0, Surface.Week, FormatContext(of = 30.0)),
                color = AppTheme.colors.nutrients.fiber.content,
                height = NutrientFillBarDefaults.TallHeight,
            )
        }
    }
}
