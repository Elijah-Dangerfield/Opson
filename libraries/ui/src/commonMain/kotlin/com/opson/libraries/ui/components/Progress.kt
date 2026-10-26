package com.opson.libraries.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * How much of a bar or ring to fill: 0 to 1, whatever comes in. Design v3: bars stop at 100%,
 * so a bigger day is a full bar, never an overflow colour or a longer bar, and a missing or
 * broken value (NaN) is an empty one. Every bar and ring in the app reads its fill through this.
 */
fun barFill(fraction: Float): Float = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)

/**
 * A thin horizontal progress bar, 5dp tall with rounded ends (macro bars, nutrition score bars).
 * [fraction] is clamped to 0..1. Animates between values, reading the animation in the draw
 * phase only.
 */
@Composable
fun FillBar(
    fraction: Float,
    color: ColorResource,
    modifier: Modifier = Modifier,
    trackColor: ColorResource = AppTheme.colors.track,
    height: Dp = 5.dp,
) {
    val animated = animateFloatAsState(barFill(fraction), label = "FillBar")
    val fill = color.color
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(AppTheme.shapes.bar)
            .background(trackColor)
            .drawBehind {
                val width = size.width * animated.value
                if (width > 0f) {
                    drawRoundRect(
                        color = fill,
                        size = Size(width, size.height),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                }
            }
    )
}

/**
 * One segment per unit (the water glasses): [filled] of [total] segments in [color], the rest in
 * [trackColor]. 6dp tall with a 3dp gap, as in the handoff.
 */
@Composable
fun SegmentedBar(
    filled: Int,
    total: Int,
    modifier: Modifier = Modifier,
    color: ColorResource = AppTheme.colors.nutrients.water.content,
    trackColor: ColorResource = AppTheme.colors.track,
    height: Dp = 6.dp,
    gap: Dp = 3.dp,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(height),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        repeat(total.coerceAtLeast(0)) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(height)
                    .clip(AppTheme.shapes.bar)
                    .background(if (index < filled.coerceIn(0, total)) color else trackColor)
            )
        }
    }
}

/** A colored arc of a [ProgressRing], as a fraction of the full circle. */
@Immutable
data class RingSegment(val fraction: Float, val color: ColorResource)

object RingSegments {
    /**
     * Splits [filled] of the circle between [parts] in proportion to their weights, the way the
     * eaten ring splits by protein, carbs and fat. Weights are relative, not grams.
     */
    fun split(filled: Float, parts: List<Pair<Float, ColorResource>>): List<RingSegment> {
        val total = parts.sumOf { it.first.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
        val f = barFill(filled)
        return parts.map { (weight, color) -> RingSegment(fraction = f * weight / total, color = color) }
    }
}

/**
 * The summary ring: 62dp by default, drawn on the handoff's 86-unit viewbox with an 8-unit
 * stroke, a radius of 35 and round caps, starting at twelve o'clock.
 *
 * [segments] are laid end to end. When they close the circle each one is shortened by a small
 * gap so the colors stay distinct, matching the prototype. [content] holds the value and unit,
 * already formatted by the caller.
 */
@Composable
fun ProgressRing(
    segments: List<RingSegment>,
    modifier: Modifier = Modifier,
    size: Dp = 62.dp,
    trackColor: ColorResource = AppTheme.colors.heroTrack,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val track = trackColor.color
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val unit = this.size.minDimension / VIEWBOX
            val stroke = STROKE * unit
            val radius = RADIUS * unit
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)
            drawCircle(color = track, radius = radius, style = Stroke(width = stroke))

            val closes = segments.sumOf { it.fraction.toDouble() } >= 1.0 - EPSILON
            // The gap is in viewbox units along the circumference, as in the prototype.
            val gapDegrees = if (closes) GAP / CIRCUMFERENCE * FULL_CIRCLE else 0f
            var start = START_ANGLE
            segments.forEach { segment ->
                val sweep = barFill(segment.fraction) * FULL_CIRCLE
                val drawn = sweep - gapDegrees
                if (drawn > 0f) {
                    drawArc(
                        color = segment.color.color,
                        startAngle = start,
                        sweepAngle = drawn,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                start += sweep
            }
        }
        content()
    }
}

/** Convenience for a single-color ring. */
@Composable
fun ProgressRing(
    fraction: Float,
    color: ColorResource,
    modifier: Modifier = Modifier,
    size: Dp = 62.dp,
    trackColor: ColorResource = AppTheme.colors.heroTrack,
    content: @Composable BoxScope.() -> Unit = {},
) {
    ProgressRing(
        segments = listOf(RingSegment(fraction, color)),
        modifier = modifier,
        size = size,
        trackColor = trackColor,
        content = content,
    )
}

private const val VIEWBOX = 86f
private const val STROKE = 8f
private const val RADIUS = 35f
private const val GAP = 6f
private val CIRCUMFERENCE = 2f * kotlin.math.PI.toFloat() * RADIUS
private const val FULL_CIRCLE = 360f
private const val START_ANGLE = -90f
private const val EPSILON = 0.001

@Preview(widthDp = 390)
@Composable
private fun ProgressPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(AppTheme.shapes.card).background(AppTheme.colors.hero),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                val n = AppTheme.colors.nutrients
                ProgressRing(
                    segments = RingSegments.split(
                        filled = 1f,
                        parts = listOf(30f to n.protein.content, 50f to n.carbs.content, 20f to n.fat.content),
                    ),
                ) {
                    Text("1,840", typography = AppTheme.type.ringValue, color = AppTheme.colors.onHero)
                }
                ProgressRing(fraction = 0.72f, color = AppTheme.colors.onHero) {
                    Text("On", typography = AppTheme.type.ringValue, color = AppTheme.colors.onHero)
                }
            }
            FillBar(fraction = 0.6f, color = AppTheme.colors.nutrients.protein.content)
            SegmentedBar(filled = 3, total = 8)
        }
    }
}
