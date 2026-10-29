package com.opson.libraries.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.system.isReduceMotionEnabled
import com.opson.system.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/*
 * A figure that reads another way when tapped, as Cronometer's remaining and deficit swap: the
 * whole figure is the tap target, a small ⇄ beside it says it can, and the two readings cross-fade.
 * The readings, and whether there is another one at all, are the caller's.
 */

/**
 * What a tap on a flippable figure does: [onFlip], and [nextLabel], what a screen reader offers
 * after the figure's own text ("Show your deficit", read as "double tap to show your deficit").
 */
class FlipControl(val nextLabel: String, val onFlip: () -> Unit)

/** Makes the figure it is on one button that flips it, its texts merged. Without [control] the figure is plain text. */
@Composable
fun Modifier.flipToggle(control: FlipControl?): Modifier =
    if (control == null) {
        this
    } else {
        bounceClick(scaleDown = 0.97f, onClickLabel = control.nextLabel, onClick = control.onFlip)
    }

/**
 * Draws [target] with [content], cross-fading from the previous one when it changes. Key it on
 * what the figure shows (the reading and its value) so the outgoing layer keeps its own text.
 * Still under Reduce Motion and in previews: the new reading just appears.
 */
@Composable
fun <T> FlipContent(target: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    if (LocalInspectionMode.current || isReduceMotionEnabled()) {
        Box(modifier) { content(target) }
    } else {
        Crossfade(targetState = target, modifier = modifier, animationSpec = tween(FLIP_MILLIS), label = "flip") { content(it) }
    }
}

/** The small ⇄ that says a figure flips. Decorative: the figure's button says it for a screen reader. */
@Composable
fun FlipIcon(modifier: Modifier = Modifier, size: Dp = 18.dp, color: ColorResource = AppTheme.colors.accent) {
    Icon(icon = AppIcons.SwapHoriz, size = size, color = color, modifier = modifier)
}

private const val FLIP_MILLIS = 180

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun FlipPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            listOf("To your range" to "655 kcal", "Eaten" to "1,245 kcal").forEach { (label, value) ->
                Column(Modifier.flipToggle(FlipControl("Show the other reading") {})) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = label, typography = AppTheme.type.bodyBold.ExtraBold, color = AppTheme.colors.accent)
                        FlipIcon()
                    }
                    FlipContent(value) { Text(text = it, typography = AppTheme.type.figureLarge, color = AppTheme.colors.ink) }
                }
            }
        }
    }
}
