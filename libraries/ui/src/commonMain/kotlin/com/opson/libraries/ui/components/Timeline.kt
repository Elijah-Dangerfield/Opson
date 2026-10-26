package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.color.MealTone
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * One row of a day timeline (Patterns, design v3 5.6): a soft track with a dot at each fraction
 * of the day in [dots], and the usual [window] tinted behind them. Positions are fractions the
 * caller worked out (6 am is 0, 9 pm is 1 on Patterns); nothing here knows about time.
 */
@Composable
fun TimelineTrack(
    dots: List<Float>,
    window: ClosedFloatingPointRange<Float>?,
    color: ColorResource,
    modifier: Modifier = Modifier,
    trackColor: ColorResource = AppTheme.colors.bg,
) {
    val dotColor = color.color
    val windowColor = color.withAlpha(WINDOW_ALPHA).color
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TimelineDefaults.Height)
            .clip(AppTheme.shapes.inputLarge)
            .background(trackColor)
            .drawBehind {
                val inset = TimelineDefaults.DotSize.toPx()
                val span = size.width - inset * 2
                fun x(fraction: Float) = inset + span * fraction.coerceIn(0f, 1f)
                window?.let {
                    val start = x(it.start) - inset / 2
                    val width = (x(it.endInclusive) - x(it.start) + inset).coerceAtLeast(inset)
                    drawRoundRect(
                        color = windowColor,
                        topLeft = Offset(start, 0f),
                        size = Size(width, size.height),
                        cornerRadius = CornerRadius(inset / 2),
                    )
                }
                val radius = TimelineDefaults.DotSize.toPx() / 2
                dots.forEach { drawCircle(dotColor, radius = radius, center = Offset(x(it), size.height / 2)) }
            },
    )
}

/** Labels spread evenly under a [TimelineTrack], first at the left edge and last at the right: `6a … 9p`. */
@Composable
fun TimelineAxis(labels: List<String>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { Text(text = it, typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted, maxLines = 1) }
    }
}

/**
 * One bar split into coloured parts by share (Patterns' "How big each meal is"). [parts] are
 * shares that need not add to 1; each gets its share of the width, with a hairline between.
 */
@Composable
fun ShareBar(parts: List<Pair<Float, ColorResource>>, modifier: Modifier = Modifier) {
    val visible = parts.filter { it.first > 0f }
    if (visible.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth().height(TimelineDefaults.ShareBarHeight).clip(AppTheme.shapes.pill),
        horizontalArrangement = Arrangement.spacedBy(TimelineDefaults.ShareGap),
    ) {
        visible.forEach { (share, color) -> Spacer(Modifier.weight(share).height(TimelineDefaults.ShareBarHeight).background(color)) }
    }
}

object TimelineDefaults {
    val Height: Dp = 24.dp
    val DotSize: Dp = 10.dp
    val ShareBarHeight: Dp = 14.dp
    internal val ShareGap: Dp = 2.dp
}

private const val WINDOW_ALPHA = 0.16f

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun TimelinePreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TimelineTrack(
                dots = listOf(0.12f, 0.14f, 0.15f, 0.25f),
                window = 0.12f..0.15f,
                color = AppTheme.colors.meal(MealTone.Breakfast).content,
            )
            TimelineTrack(
                dots = listOf(0.82f, 0.84f, 0.9f),
                window = 0.82f..0.84f,
                color = AppTheme.colors.meal(MealTone.Dinner).content,
            )
            TimelineAxis(listOf("6a", "9a", "12p", "3p", "6p", "9p"))
            ShareBar(
                listOf(
                    0.21f to AppTheme.colors.meal(MealTone.Breakfast).content,
                    0.27f to AppTheme.colors.meal(MealTone.Lunch).content,
                    0.14f to AppTheme.colors.meal(MealTone.Snack).content,
                    0.38f to AppTheme.colors.meal(MealTone.Dinner).content,
                ),
            )
        }
    }
}
