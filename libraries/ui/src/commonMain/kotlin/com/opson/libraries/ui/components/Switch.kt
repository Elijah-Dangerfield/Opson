package com.opson.libraries.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.thenIf
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The handoff's switch: a 50 by 30 track with a 24dp white knob, accent when on and the track
 * color when off.
 *
 * Both animations are kept as `State` and read in `drawBehind` / `offset { }`, so a toggle
 * redraws the switch without recomposing it.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val trackOn = AppTheme.colors.accent.color
    val trackOff = AppTheme.colors.track.color
    val trackColor = animateColorAsState(if (checked) trackOn else trackOff, label = "SwitchTrack")
    val knobTravel = animateDpAsState(if (checked) SwitchTravel else 0.dp, label = "SwitchKnob")

    Box(
        modifier = modifier
            .size(width = SwitchWidth, height = SwitchHeight)
            .thenIf(!enabled) { alpha(DisabledAlpha) }
            .drawBehind {
                drawRoundRect(color = trackColor.value, cornerRadius = CornerRadius(size.height / 2))
            }
            .thenIf(onCheckedChange != null) {
                toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    interactionSource = interactionSource,
                    indication = null,
                    onValueChange = onCheckedChange!!,
                )
            }
            .padding(SwitchPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(knobTravel.value.roundToPx(), 0) }
                .size(KnobSize)
                .background(ColorResource.White, CircleShape)
        )
    }
}

/** Kept for existing callers. It is the handoff switch now; see [AppSwitch]. */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    AppSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
    )
}

private val SwitchWidth = 50.dp
private val SwitchHeight = 30.dp
private val SwitchPadding = 3.dp
private val KnobSize = 24.dp
private val SwitchTravel = SwitchWidth - KnobSize - SwitchPadding * 2
private const val DisabledAlpha = 0.5f

@Preview
@Composable
private fun AppSwitchPreview() {
    PreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppSwitch(checked = true, onCheckedChange = {})
            AppSwitch(checked = false, onCheckedChange = {})
            AppSwitch(checked = true, onCheckedChange = {}, enabled = false)
            AppSwitch(checked = false, onCheckedChange = {}, enabled = false)
        }
    }
}
