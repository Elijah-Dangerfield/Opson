package com.opson.libraries.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.opson.libraries.ui.components.rememberLoopingFloat

/**
 * Breathes whatever it is applied to between its natural size and [scale].
 *
 * `@Composable` rather than `composed`: the body genuinely needs composition to
 * remember the transition, and `composed` is the deprecated way to get it — it
 * allocates a fresh modifier on every composition and opts the whole chain it
 * sits in out of skipping, which is the opposite of what an always-animating
 * element wants.
 *
 * The pulse holds still at its resting size under `@Preview` and in screenshot
 * tests; see [rememberLoopingFloat] for why that is load-bearing.
 */
@Composable
fun Modifier.pulsate(scale: Float = 1.2f): Modifier {
    // Kept as State rather than unwrapped with `by`. This animation never ends,
    // so `by` here would recompose everything this modifier is applied to at
    // 60fps forever — and `Modifier.scale` reads its argument during
    // composition, which would keep it that way. graphicsLayer takes a lambda
    // that runs in the draw phase, so reading `.value` inside it means the
    // animation never invalidates composition at all.
    val pulse = rememberLoopingFloat(
        initialValue = 1f,
        targetValue = scale,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulsate",
        previewValue = 1f,
    )

    return graphicsLayer {
        scaleX = pulse.value
        scaleY = pulse.value
    }
}
