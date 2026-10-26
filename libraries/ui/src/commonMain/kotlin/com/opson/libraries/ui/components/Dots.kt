package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.color.MealTone
import org.jetbrains.compose.ui.tooling.preview.Preview

/** A small filled circle: the colour key beside a macro label, or one meal in a [DotStrip]. */
@Composable
fun ColorDot(
    color: ColorResource,
    modifier: Modifier = Modifier,
    size: Dp = DotDefaults.Size,
) {
    Box(modifier.size(size).background(color, CircleShape))
}

/** One column of a [DotStrip]: dots top to bottom, then a short label. */
data class DotStripColumn(val label: String, val dots: List<ColorResource>)

/**
 * Equal-width columns of stacked dots with a label under each: the Trends recap,
 * where each column is a day and each dot a meal. Colours arrive from the caller
 * (a done meal in its own colour, the rest in the track colour), so the strip
 * itself never decides what counts.
 */
@Composable
fun DotStrip(
    columns: List<DotStripColumn>,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DotDefaults.ColumnGap)) {
        columns.forEach { column ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DotDefaults.LabelGap),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(DotDefaults.DotGap)) {
                    column.dots.forEach { ColorDot(it) }
                }
                Text(
                    text = column.label,
                    typography = AppTheme.type.caption,
                    color = AppTheme.colors.muted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

object DotDefaults {
    val Size: Dp = 8.dp
    val LargeSize: Dp = 9.dp
    internal val DotGap: Dp = 3.dp
    internal val LabelGap: Dp = 6.dp
    internal val ColumnGap: Dp = 8.dp
}

@Preview(widthDp = 390)
@Composable
private fun DotStripPreview() {
    PreviewLightDark {
        val meals = listOf(MealTone.Breakfast, MealTone.Lunch, MealTone.Snack, MealTone.Dinner)
        val done = listOf(4, 3, 4, 0, 2, 4, 2)
        DotStrip(
            columns = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").mapIndexed { day, label ->
                DotStripColumn(
                    label = label,
                    dots = meals.mapIndexed { index, meal ->
                        if (index < done[day]) AppTheme.colors.meal(meal).content else AppTheme.colors.track
                    },
                )
            },
        )
    }
}
