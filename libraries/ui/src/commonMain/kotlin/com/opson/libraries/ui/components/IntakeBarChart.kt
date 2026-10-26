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
 * and draws as a gap, not a zero.
 */
data class IntakeBar(val label: String, val fraction: Float?, val isCurrent: Boolean = false)

/**
 * The Trends intake chart: one bar per day or week, the user's range as a
 * shaded band behind them, and short labels underneath.
 *
 * Deliberately not the template's `BarChart`, which this would otherwise
 * extend: that one draws a numeric axis from a value formatter, and a calorie
 * axis is a calorie amount on screen. Nothing here can print a number; heights
 * and the band arrive as fractions of a scale the builder chose.
 *
 * [band] is in the same fractions, `min..max`. Pass `null` for no band.
 */
@Composable
fun IntakeBarChart(
    bars: List<IntakeBar>,
    band: ClosedFloatingPointRange<Float>?,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 150.dp,
    barColor: ColorResource = AppTheme.colors.fill,
    currentBarColor: ColorResource = AppTheme.colors.nutrients.fat.content,
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
                    drawRoundRect(
                        color = bandColor.color,
                        topLeft = Offset(0f, size.height * (1f - high)),
                        size = Size(size.width, size.height * (high - low)),
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
                                .fillMaxWidth()
                                .widthIn(max = MAX_BAR_WIDTH)
                                .fillMaxHeight(fraction)
                                .heightIn(min = MIN_BAR_HEIGHT)
                                .clip(BarShape)
                                .background(if (bar.isCurrent) currentBarColor else barColor),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BAR_GAP)) {
            bars.forEach { bar ->
                Text(
                    text = bar.label,
                    typography = AppTheme.type.caption,
                    color = AppTheme.colors.muted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val BAR_GAP = 8.dp
private val MAX_BAR_WIDTH = 30.dp
private val MIN_BAR_HEIGHT = 2.dp
private val BAND_RADIUS = 4.dp
private val BarShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp)

@Preview(widthDp = 390)
@Composable
private fun IntakeBarChartPreview() {
    // The prototype week, Oct 19 to 25 over a 3,200 scale, with Thursday untracked.
    val week = listOf("Mo" to 2050f, "Tu" to 1980f, "We" to 2240f, "Th" to null, "Fr" to 2410f, "Sa" to 2160f, "Su" to 1245f)
    PreviewLightDark {
        IntakeBarChart(
            bars = week.mapIndexed { index, (label, kcal) ->
                IntakeBar(label, kcal?.div(3200f), isCurrent = index == week.lastIndex)
            },
            band = 1900f / 3200f..2300f / 3200f,
        )
    }
}
