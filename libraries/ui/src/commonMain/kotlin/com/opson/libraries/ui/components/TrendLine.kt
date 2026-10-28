package com.opson.libraries.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.max

/**
 * A trend as a smoothed line, with optional [dots] for the readings behind it:
 * the weight chart and the Trends weight card.
 *
 * Both lists are heights in `0..1` of the chart box, `0` at the bottom, evenly
 * spaced across the width in order. The builder normalises them, so the chart
 * carries no scale and no axis, and "Trend only" really is only a trend.
 * Size it with [modifier]; the default is the handoff's 310 by 130.
 *
 * [readouts], one per point, make it inspectable ([InspectableChart]): the picked point gets a ring
 * on the line and, with [dots], its reading drawn solid.
 */
@Composable
fun TrendLine(
    points: List<Float>,
    dots: List<Float>?,
    modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(DEFAULT_ASPECT),
    readouts: List<ChartReadout?> = emptyList(),
    selection: ChartSelection = rememberChartSelection(readouts),
    lineColor: ColorResource = AppTheme.colors.accent,
    dotColor: ColorResource = AppTheme.colors.muted,
    lineWidth: Dp = 3.5.dp,
    dotRadius: Dp = 2.5.dp,
) {
    if (points.isEmpty() && dots.isNullOrEmpty()) return
    val line = lineColor.color
    val dotSolid = dotColor.color
    val dot = dotSolid.copy(alpha = DOT_ALPHA)
    val ring = AppTheme.colors.surface.color
    InspectableChart(readouts, ChartAxis.Points(maxOf(lineWidth / 2, dotRadius)), selection, modifier) {
        Canvas(Modifier.matchParentSize()) {
            val inset = max(lineWidth.toPx() / 2, dotRadius.toPx())
            val dotCenters = dots?.let { positions(it, inset) }.orEmpty()
            dotCenters.forEach { drawCircle(dot, radius = dotRadius.toPx(), center = it) }
            val lineCenters = positions(points, inset)
            val path = smoothPath(lineCenters)
            if (path != null) {
                drawPath(path, line, style = Stroke(width = lineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            val picked = selection.index
            dotCenters.getOrNull(picked)?.let { drawCircle(dotSolid, radius = dotRadius.toPx() * PICKED_DOT_SCALE, center = it) }
            lineCenters.getOrNull(picked)?.let {
                drawCircle(line, radius = lineWidth.toPx() * PICKED_RING_SCALE, center = it)
                drawCircle(ring, radius = lineWidth.toPx() * PICKED_CORE_SCALE, center = it)
            }
        }
    }
}

private fun DrawScope.positions(values: List<Float>, inset: Float): List<Offset> {
    val height = size.height - inset * 2
    return values.mapIndexed { index, value ->
        Offset(ChartPicking.pointX(index, values.size, size.width, inset), inset + height * (1f - value.coerceIn(0f, 1f)))
    }
}

/**
 * A curve through every point with horizontal tangents at each: each segment
 * is a cubic whose control points sit halfway across at the start and end
 * heights. It never overshoots the points, so a smooth line cannot invent a
 * low or a high the data does not have.
 */
private fun smoothPath(points: List<Offset>): Path? {
    if (points.isEmpty()) return null
    return Path().apply {
        moveTo(points.first().x, points.first().y)
        if (points.size == 1) {
            // A lone reading: a dot-sized stroke, so the line is still visible.
            lineTo(points.first().x, points.first().y)
            return@apply
        }
        for (i in 1 until points.size) {
            val from = points[i - 1]
            val to = points[i]
            val midX = (from.x + to.x) / 2
            cubicTo(midX, from.y, midX, to.y, to.x, to.y)
        }
    }
}

private const val DEFAULT_ASPECT = 310f / 130f
private const val DOT_ALPHA = 0.45f
private const val PICKED_DOT_SCALE = 1.6f
private const val PICKED_RING_SCALE = 1.5f
private const val PICKED_CORE_SCALE = 0.75f

/** The prototype's WEIGHTS shape, normalised: a slow drift with daily noise around it. */
private fun previewReadings() = List(28) { i -> 0.55f - i * 0.006f + kotlin.math.sin(i * 1.7f) * 0.18f + kotlin.math.cos(i * 0.9f) * 0.1f }

@Preview(widthDp = 390)
@Composable
private fun TrendLinePreview() {
    val readings = previewReadings()
    val trend = readings.runningReduce { average, reading -> average * 0.8f + reading * 0.2f }
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TrendLine(points = trend, dots = readings)
            TrendLine(points = trend, dots = null, modifier = Modifier.fillMaxWidth().height(56.dp))
        }
    }
}

/** A weigh-in picked: the ring on the line, the reading solid, the label with both. */
@Preview(widthDp = 390)
@Composable
private fun TrendLinePickedPreview() {
    val readings = previewReadings()
    val trend = readings.runningReduce { average, reading -> average * 0.8f + reading * 0.2f }
    PreviewLightDark(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 64.dp, bottom = 20.dp)) {
        TrendLine(
            points = trend,
            dots = readings,
            readouts = trend.indices.map { day ->
                ChartReadout("Oct ${day + 1}", listOf(ChartReading(value = "72.${day % 10} kg"), ChartReading(value = "73.0 kg")))
            },
            selection = remember { ChartSelection(initial = 17) },
        )
    }
}
