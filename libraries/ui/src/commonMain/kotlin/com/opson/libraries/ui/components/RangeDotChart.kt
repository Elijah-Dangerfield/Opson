package com.opson.libraries.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt

/**
 * One slot of a [RangeDotChart]. [value] is where the dot sits and [low] to [high] the bar behind
 * it, every one in `0..1` of the chart's height, `0` at the bottom; `null` [value] is a gap.
 * [isCurrent] is the slot still in progress, whose label is bold.
 */
@Immutable
data class RangeDot(
    val label: String,
    val value: Float?,
    val low: Float? = null,
    val high: Float? = null,
    val isCurrent: Boolean = false,
)

/** A word on a [RangeDotChart]'s side, at [position] (`0..1`, `0` at the bottom), with a faint line across the chart. */
@Immutable
data class RangeDotAxisLabel(val position: Float, val text: String)

/**
 * A dot per day or week with the spread behind it, for a reading that is a scale rather than an
 * amount: the 1 to 10 hunger level. The side carries the scale's own words ([axis]), never a
 * number, and one colour runs throughout, as on the other Trends charts: higher is not better.
 *
 * [readouts], one per slot and `null` for a gap, make it inspectable ([InspectableChart]).
 */
@Composable
fun RangeDotChart(
    dots: List<RangeDot>,
    axis: List<RangeDotAxisLabel>,
    modifier: Modifier = Modifier,
    readouts: List<ChartReadout?> = emptyList(),
    selection: ChartSelection = rememberChartSelection(readouts),
    chartHeight: Dp = 140.dp,
    color: ColorResource = AppTheme.colors.accent,
) {
    if (dots.isEmpty()) return
    val line = AppTheme.colors.line.color
    val dot = color.color
    val spread = color.color.copy(alpha = SPREAD_ALPHA)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            AxisWords(axis, Modifier.width(AXIS_WIDTH).height(chartHeight))
            InspectableChart(
                readouts = readouts,
                axis = ChartAxis.Slots(SLOT_GAP),
                selection = selection,
                modifier = Modifier.weight(1f).height(chartHeight),
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val inset = DOT_RADIUS.toPx()
                    val usable = size.height - inset * 2
                    fun y(position: Float) = inset + usable * (1f - position.coerceIn(0f, 1f))
                    axis.forEach { label ->
                        drawLine(line, Offset(0f, y(label.position)), Offset(size.width, y(label.position)), strokeWidth = GRID_WIDTH.toPx())
                    }
                    val picked = selection.index
                    dots.forEachIndexed { index, slot ->
                        val value = slot.value ?: return@forEachIndexed
                        val x = ChartPicking.slotCenter(index, dots.size, size.width, SLOT_GAP.toPx())
                        val alpha = if (picked == ChartPicking.NONE || picked == index) 1f else FADED_ALPHA
                        val low = slot.low
                        val high = slot.high
                        if (low != null && high != null && high > low) {
                            drawLine(
                                spread.copy(alpha = spread.alpha * alpha),
                                Offset(x, y(low)),
                                Offset(x, y(high)),
                                strokeWidth = SPREAD_WIDTH.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                        drawCircle(dot.copy(alpha = alpha), radius = DOT_RADIUS.toPx(), center = Offset(x, y(value)))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(AXIS_WIDTH))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(SLOT_GAP)) {
                dots.forEach { slot ->
                    Text(
                        text = slot.label,
                        typography = if (slot.isCurrent) AppTheme.type.secondaryStrong.Bold else AppTheme.type.secondary,
                        color = if (slot.isCurrent) AppTheme.colors.ink else AppTheme.colors.muted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** The scale's words down the side, each centred on its line and kept inside the chart's height. */
@Composable
private fun AxisWords(axis: List<RangeDotAxisLabel>, modifier: Modifier) {
    Layout(
        modifier = modifier,
        content = {
            axis.forEach { label ->
                Text(text = label.text, typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted, maxLines = 1)
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        val height = constraints.maxHeight
        val inset = DOT_RADIUS.toPx()
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                val center = inset + (height - inset * 2) * (1f - axis[index].position.coerceIn(0f, 1f))
                val top = (center - placeable.height / 2f).roundToInt().coerceIn(0, (height - placeable.height).coerceAtLeast(0))
                placeable.place(0, top)
            }
        }
    }
}

private val AXIS_WIDTH = 92.dp
private val SLOT_GAP = 6.dp
private val DOT_RADIUS = 5.dp
private val SPREAD_WIDTH = 8.dp
private val GRID_WIDTH = 1.dp
private const val SPREAD_ALPHA = 0.3f
private const val FADED_ALPHA = 0.35f

/** Four weeks of hunger on a 1 to 10 scale, hungrier higher, each week's spread behind its average. */
@Preview(widthDp = 390)
@Composable
private fun RangeDotChartPreview() {
    fun at(level: Float) = (10f - level) / 9f
    val weeks = listOf(Triple(4.2f, 3f, 6f), Triple(5.0f, 4f, 6f), Triple(3.6f, 2f, 5f), Triple(4.5f, 4f, 5f))
    PreviewLightDark {
        RangeDotChart(
            dots = weeks.mapIndexed { index, (average, hungriest, fullest) ->
                RangeDot("10/${5 + index * 7}", at(average), low = at(fullest), high = at(hungriest), isCurrent = index == weeks.lastIndex)
            },
            axis = listOf(1f to "Ravenous", 3f to "Hungry", 5f to "Neutral", 8f to "Full", 10f to "Stuffed").map { RangeDotAxisLabel(at(it.first), it.second) },
            selection = remember { ChartSelection() },
        )
    }
}
