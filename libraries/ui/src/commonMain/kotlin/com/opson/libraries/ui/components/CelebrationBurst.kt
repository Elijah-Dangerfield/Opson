package com.opson.libraries.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.system.isReduceMotionEnabled
import com.opson.system.AppTheme
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A milestone's moment: [content] springs up from a little smaller, over a soft disc, while a
 * handful of confetti in the nutrient colors bursts out from behind it once, falls a little and
 * fades. It plays once, about a second and a half, and never loops.
 *
 * Under `@Preview` and screenshots, and when the system's reduce-motion setting is on, nothing
 * moves: the disc and [content] are drawn at rest and the confetti not at all, so a capture
 * never waits on an animation and nobody who asked for stillness gets a burst. The burst and
 * the spring are read in the draw and layer phases only, so the animation recomposes nothing.
 */
@Composable
fun CelebrationBurst(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val still = LocalInspectionMode.current || isReduceMotionEnabled()
    val burst = remember { Animatable(if (still) 1f else 0f) }
    val rise = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        launch { rise.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        burst.animateTo(1f, tween(BurstMillis, easing = FastOutSlowInEasing))
    }
    val colors = with(AppTheme.colors) {
        listOf(nutrients.protein.content, nutrients.carbs.content, nutrients.fat.content, nutrients.fiber.content, accent).map { it.color }
    }
    val disc = AppTheme.colors.accentTint.color
    val pieces = remember { confetti(colors.size) }
    Box(
        modifier = modifier.drawBehind {
            val t = rise.value
            drawCircle(disc, radius = size.minDimension / 2 * (DiscRest + (1 - DiscRest) * t.coerceIn(0f, 1f)))
            if (!still) drawConfetti(pieces, colors, burst.value)
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                val t = rise.value
                val scale = ContentFrom + (1 - ContentFrom) * t
                scaleX = scale
                scaleY = scale
                alpha = t.coerceIn(0f, 1f)
            },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/** One piece: where it flies, how far, how it spins, and its size and color. */
private class Piece(val angle: Float, val reach: Float, val spin: Float, val size: Float, val round: Boolean, val color: Int)

/** Seeded, so every celebration bursts the same way and a screenshot of a mid-burst frame would too. */
private fun confetti(colorCount: Int): List<Piece> {
    val random = Random(CONFETTI_SEED)
    return List(PIECES) { i ->
        Piece(
            angle = (i.toFloat() / PIECES) * 2 * PI.toFloat() + random.nextFloat() * ANGLE_JITTER,
            reach = REACH_MIN + random.nextFloat() * (1 - REACH_MIN),
            spin = (random.nextFloat() - 0.5f) * SPIN_DEGREES,
            size = SIZE_MIN + random.nextFloat() * (1 - SIZE_MIN),
            round = i % 3 == 0,
            color = i % colorCount,
        )
    }
}

private fun DrawScope.drawConfetti(pieces: List<Piece>, colors: List<Color>, t: Float) {
    if (t <= 0f || t >= 1f) return
    val center = Offset(size.width / 2, size.height / 2)
    val reach = size.minDimension * REACH
    val piece = PieceSize.toPx()
    val fall = size.minDimension * FALL
    // Out fast, then drift down as it fades over the last 40%.
    val alpha = if (t < FADE_FROM) 1f else 1f - (t - FADE_FROM) / (1 - FADE_FROM)
    pieces.forEach { p ->
        val distance = reach * p.reach * t
        val at = center + Offset(cos(p.angle) * distance, sin(p.angle) * distance + fall * t * t)
        val side = piece * p.size
        val color = colors[p.color].copy(alpha = alpha)
        if (p.round) {
            drawCircle(color, radius = side / 2, center = at)
        } else {
            rotate(p.spin * t, pivot = at) {
                drawRoundRect(
                    color = color,
                    topLeft = at - Offset(side / 2, side / 4),
                    size = Size(side, side / 2),
                    cornerRadius = CornerRadius(side / 6),
                )
            }
        }
    }
}

private const val BurstMillis = 1500
private const val PIECES = 28
private const val CONFETTI_SEED = 7
private const val ANGLE_JITTER = 0.2f
private const val SPIN_DEGREES = 540f
private const val REACH_MIN = 0.55f
private const val SIZE_MIN = 0.6f

/** How far the farthest piece flies, as a share of the box's smaller side. */
private const val REACH = 0.62f

/** How far a piece drops by the end. */
private const val FALL = 0.18f
private const val FADE_FROM = 0.6f

/** The disc starts at this share of its size, and the content at this scale. */
private const val DiscRest = 0.82f
private const val ContentFrom = 0.72f
private val PieceSize = 10.dp
