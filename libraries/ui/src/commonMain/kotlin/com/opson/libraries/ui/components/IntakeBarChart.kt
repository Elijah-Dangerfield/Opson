package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * One bar in an [IntakeBarChart]. [fraction] is the bar's height over the
 * chart's scale, worked out by the screen's builder; `null` is an untracked day
 * and draws as a gap, not a zero. [isCurrent] marks the day or week still in
 * progress: it draws striped, so a part-day is never read as a small one.
 */
data class IntakeBar(val label: String, val fraction: Float?, val isCurrent: Boolean = false)

/**
 * The Trends intake chart: one bar per day or week, the user's range as a
 * shaded band behind them, and short labels underneath.
 *
 * Every bar is the same colour, inside the band or out of it (design v3): a
 * colour that changed with the amount would mean over or under. The bar still
 * in progress is striped and its label bold.
 *
 * There is deliberately no numeric axis: a calorie axis is a calorie amount on
 * screen. Nothing here can print a number; heights and the band arrive as
 * fractions of a scale the builder chose.
 *
 * [band] is in the same fractions, `min..max`. Pass `null` for no band. A band whose ends meet is
 * one number, and draws as a thin line at it.
 */
@Composable
fun IntakeBarChart(
    bars: List<IntakeBar>,
    band: ClosedFloatingPointRange<Float>?,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 130.dp,
    barColor: ColorResource = AppTheme.colors.accent,
    bandColor: ColorResource = AppTheme.colors.soft,
) {
    if (bars.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .drawBehind {
                    band ?: return@drawBehind
                    val low = band.start.coerceIn(0f, 1f)
                    val high = band.endInclusive.coerceIn(low, 1f)
                    // One number has no height of its own: a thin line at it, centred, not a band.
                    val height = (size.height * (high - low)).coerceAtLeast(GOAL_LINE.toPx())
                    val top = size.height * (1f - (low + high) / 2) - height / 2
                    drawRoundRect(
                        color = bandColor.color,
                        topLeft = Offset(0f, top),
                        size = Size(size.width, height),
                        cornerRadius = CornerRadius(BAND_RADIUS.toPx()),
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEach { bar ->
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val fraction = bar.fraction?.coerceIn(0f, 1f)
                    if (fraction != null && fraction > 0f) {
                        Box(
                            Modifier
                                // The cap comes first: after fillMaxWidth the slot's width is fixed and wins.
                                .widthIn(max = MAX_BAR_WIDTH)
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .heightIn(min = MIN_BAR_HEIGHT)
                                .clip(BarShape)
                                .then(if (bar.isCurrent) Modifier.stripes(barColor) else Modifier.background(barColor)),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BAR_GAP)) {
            bars.forEach { bar ->
                Text(
                    text = bar.label,
                    typography = if (bar.isCurrent) AppTheme.type.secondaryStrong.Bold else AppTheme.type.secondary,
                    color = if (bar.isCurrent) AppTheme.colors.ink else AppTheme.colors.muted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Diagonal stripes in two light shades of [color]: "still in progress". */
private fun Modifier.stripes(color: ColorResource): Modifier = drawBehind {
    drawRect(color.color.copy(alpha = STRIPE_BASE_ALPHA))
    val step = STRIPE_STEP.toPx()
    val stroke = STRIPE_WIDTH.toPx()
    val stripe = color.color.copy(alpha = STRIPE_ALPHA)
    var x = -size.height
    while (x < size.width) {
        drawLine(stripe, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = stroke)
        x += step
    }
}

private val BAR_GAP = 8.dp
private val MAX_BAR_WIDTH = 30.dp
private val MIN_BAR_HEIGHT = 2.dp
private val BAND_RADIUS = 4.dp
private val GOAL_LINE = 4.dp
private val STRIPE_STEP = 10.dp
private val STRIPE_WIDTH = 4.dp
private const val STRIPE_BASE_ALPHA = 0.3f
private const val STRIPE_ALPHA = 0.22f
private val BarShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp)

@Preview(widthDp = 390)
@Composable
private fun IntakeBarChartPreview() {
    // The prototype week, Oct 19 to 25 over a 3,200 scale, with Thursday untracked.
    val week = listOf("Mon" to 2050f, "Tue" to 1980f, "Wed" to 2240f, "Thu" to null, "Fri" to 2410f, "Sat" to 2160f, "Sun" to 1245f)
    PreviewLightDark {
        IntakeBarChart(
            bars = week.mapIndexed { index, (label, kcal) ->
                IntakeBar(label, kcal?.div(3200f), isCurrent = index == week.lastIndex)
            },
            band = 1900f / 3200f..2300f / 3200f,
        )
    }
}

@Preview(widthDp = 390)
@Composable
private fun IntakeBarChartOneNumberPreview() {
    val week = listOf("Mon" to 2050f, "Tue" to 1980f, "Wed" to 2240f, "Thu" to null, "Fri" to 2410f, "Sat" to 2160f, "Sun" to 1245f)
    PreviewLightDark {
        IntakeBarChart(
            bars = week.mapIndexed { index, (label, kcal) -> IntakeBar(label, kcal?.div(3200f), isCurrent = index == week.lastIndex) },
            band = 2100f / 3200f..2100f / 3200f,
        )
    }
}
