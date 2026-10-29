package com.opson.libraries.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

/**
 * A clickable that squashes to [scaleDown] under the finger and springs back. Screen readers hear
 * it as a [Role.Button] unless [role] says otherwise, and [onClickLabel] as what a double tap does.
 *
 * `@Composable` rather than the deprecated `composed`: the press animation needs
 * a remembered [Animatable] and a `LaunchedEffect` collecting the interaction
 * source, so the body genuinely requires composition. `composed` supplied that
 * but allocated a new modifier every composition and made the whole chain
 * unskippable — this sits on buttons and list rows, which recompose with the
 * screen behind them.
 */
@Composable
fun Modifier.bounceClick(
    enabled: Boolean = true,
    mutableInteractionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    scaleDown: Float = 0.90f,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    onClick: () -> Unit = {}
): Modifier {

    val interactionSource = mutableInteractionSource ?: remember { MutableInteractionSource() }

    val animatable = remember { Animatable(1f) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> animatable.animateTo(scaleDown)
                is PressInteraction.Release -> animatable.animateTo(1f)
                is PressInteraction.Cancel -> animatable.animateTo(1f)
            }
        }
    }

    return this
        .graphicsLayer {
            val scale = animatable.value
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = indication,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick
        )
}
