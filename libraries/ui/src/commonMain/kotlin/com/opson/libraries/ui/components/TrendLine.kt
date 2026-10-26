package com.opson.libraries.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
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
 */
@Composable
fun TrendLine(
    points: List<Float>,
    dots: List<Float>?,
    modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(DEFAULT_ASPECT),
    lineColor: ColorResource = AppTheme.colors.accent,
    dotColor: ColorResource = AppTheme.colors.muted,
    lineWidth: Dp = 3.5.dp,
    dotRadius: Dp = 2.5.dp,
) {
    if (points.isEmpty() && dots.isNullOrEmpty()) return
    val line = lineColor.color
    val dot = dotColor.color.copy(alpha = DOT_ALPHA)
    Canvas(modifier) {
        val inset = max(lineWidth.toPx() / 2, dotRadius.toPx())
        dots?.let { values ->
            positions(values, inset).forEach { drawCircle(dot, radius = dotRadius.toPx(), center = it) }
        }
        val path = smoothPath(positions(points, inset)) ?: return@Canvas
        drawPath(path, line, style = Stroke(width = lineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.positions(values: List<Float>, inset: Float): List<Offset> {
    if (values.isEmpty()) return emptyList()
    val width = size.width - inset * 2
    val height = size.height - inset * 2
    val step = if (values.size > 1) width / (values.size - 1) else 0f
    return values.mapIndexed { index, value ->
        val x = if (values.size > 1) inset + step * index else size.width / 2
        Offset(x, inset + height * (1f - value.coerceIn(0f, 1f)))
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

@Preview(widthDp = 390)
@Composable
private fun TrendLinePreview() {
    // The prototype's WEIGHTS shape, normalised: a slow drift with daily noise around it.
    val readings = List(28) { i -> 0.55f - i * 0.006f + kotlin.math.sin(i * 1.7f) * 0.18f + kotlin.math.cos(i * 0.9f) * 0.1f }
    val trend = readings.runningReduce { average, reading -> average * 0.8f + reading * 0.2f }
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TrendLine(points = trend, dots = readings)
            TrendLine(points = trend, dots = null, modifier = Modifier.fillMaxWidth().height(56.dp))
        }
    }
}
