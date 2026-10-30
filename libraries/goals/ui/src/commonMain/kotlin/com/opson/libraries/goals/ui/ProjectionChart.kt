package com.opson.libraries.goals.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.prefersStillness
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.goal_chart_cd
import opson.libraries.resources.generated.resources.goal_chart_cd_open
import org.jetbrains.compose.resources.stringResource

/**
 * Weight over time for a goal or a target (owner feedback, 2026-10-30: "graph it out to see best,
 * worst and middle"): the band between eating at the low and high end shaded, the target's line
 * through it, and the goal weight as a dashed line. The start weight sits where the lines begin and
 * the goal (or where the line ends) at the far side; the dates run underneath.
 *
 * With [animate], the lines draw in from the left once and [onDrawn] runs when they finish (the
 * reveal plays its haptic and shows the date then). Held still under a preview, a screenshot or
 * the system's reduce-motion setting, where [onDrawn] runs at once. The progress is read only in
 * the draw phase, so the drawing recomposes nothing.
 */
@Composable
fun ProjectionChart(
    chart: ProjectionChartUi,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    onDrawn: () -> Unit = {},
    height: Dp = 164.dp,
) {
    val still = prefersStillness()
    val progress = remember(chart.shape) { Animatable(if (animate && !still) 0f else 1f) }
    LaunchedEffect(chart.shape, animate) {
        if (animate && !still) progress.animateTo(1f, tween(DrawMillis, easing = FastOutSlowInEasing))
        onDrawn()
    }
    val description = if (chart.toGoal) {
        stringResource(Res.string.goal_chart_cd, chart.startWeight, chart.endWeight, chart.startDate, chart.endDate)
    } else {
        stringResource(Res.string.goal_chart_cd_open, chart.startWeight, chart.endWeight, chart.endDate)
    }
    val line = AppTheme.colors.accent.color
    val band = AppTheme.colors.accentTint.color
    val goalLine = AppTheme.colors.muted.color
    val grid = AppTheme.colors.line.color
    val shape = chart.shape
    val falling = shape.middle.last() < shape.start
    Column(modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
        Box(modifier = Modifier.fillMaxWidth().height(height)) {
            Canvas(modifier = Modifier.matchParentSize().padding(vertical = LabelRoom)) {
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                shape.goal?.let { goal ->
                    val y = size.height * (1 - goal)
                    drawLine(
                        goalLine,
                        Offset(0f, y),
                        Offset(size.width, y),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                    )
                }
                val shown = progress.value
                clipRect(right = size.width * shown) {
                    drawPath(bandPath(shape.low, shape.high), band)
                    drawPath(linePath(shape.middle), line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                drawCircle(line, radius = 4.5.dp.toPx(), center = Offset(0f, size.height * (1 - shape.start)))
            }
            // The start weight beside where the lines begin, the end on the far side: above the
            // lines when they fall, below when they rise, so neither label sits on a line.
            Text(
                text = chart.startWeight,
                typography = AppTheme.type.secondaryStrong,
                color = AppTheme.colors.ink,
                modifier = Modifier.align(if (falling) Alignment.TopStart else Alignment.BottomStart),
            )
            Text(
                text = chart.endWeight,
                typography = AppTheme.type.secondaryStrong,
                color = if (chart.toGoal) AppTheme.colors.ink else AppTheme.colors.muted,
                modifier = Modifier.align(if (falling) Alignment.BottomEnd else Alignment.TopEnd),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = chart.startDate, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            Text(text = chart.endDate, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
    }
}

/** Room at the top and bottom of the drawing for the weight labels. */
private val LabelRoom = 22.dp

private const val DrawMillis = 1200

private fun DrawScope.x(index: Int, count: Int): Float = if (count <= 1) 0f else size.width * index / (count - 1)

private fun DrawScope.y(height: Float): Float = size.height * (1 - height)

private fun DrawScope.linePath(values: List<Float>): Path = Path().apply {
    values.forEachIndexed { index, value ->
        if (index == 0) moveTo(x(index, values.size), y(value)) else lineTo(x(index, values.size), y(value))
    }
}

/** The shaded band: along [low] to the end, and back along [high]. */
private fun DrawScope.bandPath(low: List<Float>, high: List<Float>): Path = Path().apply {
    low.forEachIndexed { index, value ->
        if (index == 0) moveTo(x(index, low.size), y(value)) else lineTo(x(index, low.size), y(value))
    }
    for (index in high.indices.reversed()) lineTo(x(index, high.size), y(high[index]))
    close()
}
