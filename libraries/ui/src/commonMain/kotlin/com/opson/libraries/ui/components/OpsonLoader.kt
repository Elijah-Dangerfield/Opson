package com.opson.libraries.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.system.isReduceMotionEnabled
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.opson_bowl
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.PI
import kotlin.math.cos
import opson.libraries.resources.generated.resources.Res as SharedRes

object OpsonLoaderDefaults {
    /**
     * The mark's size where a whole screen is waiting. The iOS launch image (LaunchMark in the
     * asset catalog) is drawn at this size in points and in the same place, so the boot screen's
     * first frame lands on it. Change both together.
     */
    val ScreenSize: Dp = 112.dp
}

/**
 * Opson's mark as a loading indicator: the bowl, with its two curls of steam rising off it on a
 * 1.6 s loop. It is for a whole screen that is waiting (boot, the splash). Content loading inside
 * a page keeps [DelayedLoadingIndicator], which is smaller and stays out of the way.
 *
 * Each curl rises 16 of the mark's 108 units on a cosine ease, fading in over the first 30% of
 * its cycle and out over the last 30%, the second curl half a cycle behind the first. Those
 * numbers are measured from the 24 loader frames in the brand export, because the animated SVG's
 * stylesheet did not survive the export. The frames show no sideways drift; the wobble is the
 * curl's own S rising past the eye.
 *
 * The loop runs in the draw phase only: its value is read inside `drawWithCache`, so each frame
 * redraws and nothing recomposes. It holds still, as the master mark, in previews and
 * screenshots and when the system's reduce-motion setting is on.
 *
 * Without a [contentDescription] it is decorative, as in a lockup. With one it reads as a busy
 * progress indicator.
 */
@Composable
fun OpsonLoader(
    modifier: Modifier = Modifier,
    size: Dp = OpsonLoaderDefaults.ScreenSize,
    contentDescription: String? = null,
) {
    val still = LocalInspectionMode.current || isReduceMotionEnabled()
    val cycle = if (still) {
        null
    } else {
        rememberLoopingFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(SteamCycleMillis, easing = LinearEasing)),
            label = "opson-steam",
        )
    }
    val described = if (contentDescription == null) {
        Modifier
    } else {
        Modifier.semantics {
            this.contentDescription = contentDescription
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        }
    }
    OpsonMark(
        steamPhase = cycle?.let { state -> { state.value } },
        modifier = modifier.size(size).then(described),
    )
}

/**
 * The mark, filling the smaller side of its bounds, with the steam [steamPhase] of the way
 * (0 to 1) through its loop, or at rest when that is null. [steamPhase] is only called while
 * drawing.
 *
 * On a dark ground the mark sits on a white disc, as the brand asks, because the ink outline
 * disappears without a light ground. The disc (radius 64 round the mark's centre, as the dark
 * lockup draws it) fills the bounds there, so the bowl is drawn smaller than on a light ground,
 * where the mark's own 108-unit box fills them.
 */
@Composable
internal fun OpsonMark(
    steamPhase: (() -> Float)?,
    modifier: Modifier = Modifier,
) {
    val bowl = painterResource(SharedRes.drawable.opson_bowl)
    val onDisc = AppTheme.colors.isDark
    Spacer(
        modifier.drawWithCache {
            val unit = size.minDimension / if (onDisc) DiscDiameter else MarkBox
            val origin = size.center - Offset(MarkCentre * unit, MarkCentre * unit)
            val bowlSize = Size(MarkBox * unit, MarkBox * unit)
            val curl = PathParser().parsePathString(SteamCurl).toPath()
            val stroke = Stroke(width = InkWidth, cap = StrokeCap.Round)
            onDrawBehind {
                if (onDisc) drawCircle(Disc, radius = DiscDiameter / 2 * unit)
                val phase = steamPhase?.invoke()
                withTransform({
                    translate(origin.x, origin.y)
                    scale(unit, unit, pivot = Offset.Zero)
                }) {
                    CurlOffsets.forEachIndexed { index, dx ->
                        val t = phase?.let { (it + index * HalfCycle) % 1f }
                        translate(left = dx, top = t?.let(::steamRise) ?: 0f) {
                            drawPath(curl, Ink, alpha = t?.let(::steamAlpha) ?: 1f, style = stroke)
                        }
                    }
                }
                translate(origin.x - BoxInset * unit, origin.y - BoxInset * unit) {
                    with(bowl) { draw(bowlSize) }
                }
            }
        },
    )
}

/** How far a curl sits below its resting place, in mark units, [t] of the way through its cycle. */
internal fun steamRise(t: Float): Float = SteamTravel / 2 * cos(PI.toFloat() * t)

/** A curl's opacity [t] of the way through its cycle: in over the first 30%, out over the last. */
internal fun steamAlpha(t: Float): Float = when {
    t < SteamFade -> t / SteamFade
    t > 1f - SteamFade -> (1f - t) / SteamFade
    else -> 1f
}

private const val SteamCycleMillis = 1600
private const val HalfCycle = 0.5f
private const val SteamTravel = 16f
private const val SteamFade = 0.3f

// The mark's geometry, in the units of its SVG (viewBox -4 -4 108 108).
private const val MarkBox = 108f
private const val BoxInset = 4f
private const val MarkCentre = 50f
private const val DiscDiameter = 128f
private const val InkWidth = 4f
private const val SteamCurl = "M36 38c-5-5-5-10 0-15s5-10 0-15"
private val CurlOffsets = listOf(0f, 24f)

private val Ink = Color(0xFF18202E)
private val Disc = Color.White

@Preview
@Composable
private fun OpsonLoaderPreview() {
    PreviewLightDark {
        OpsonLoader()
    }
}

/** The steam at four points in its loop, so a still render shows the motion. */
@Preview
@Composable
private fun OpsonLoaderSteamPreview() {
    PreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(0.1f, 0.35f, 0.6f, 0.85f).forEach { phase ->
                OpsonMark(steamPhase = { phase }, modifier = Modifier.size(64.dp))
            }
        }
    }
}
