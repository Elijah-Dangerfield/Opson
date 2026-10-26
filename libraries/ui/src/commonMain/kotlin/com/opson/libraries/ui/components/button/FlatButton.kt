package com.opson.libraries.ui.components.button

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.thenIf
import com.opson.system.typography.TypographyResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** The handoff's flat button fills. None of them is a warning colour: deleting is [Accent], not red. */
enum class FlatButtonTone {
    /** Soft fill, ink text: links on a card ("Find a provider", "Export everything (CSV)"). */
    Soft,

    /** A line border on the card colour ("Delete everything"). */
    Outlined,

    /** Soft fill inside a line border (Lower and Raise on Targets). */
    SoftOutlined,

    /** Ink fill, page-colour text: the one strong action in a sheet ("Show them"). */
    Ink,

    /** Accent fill ("Delete" after the confirm). */
    Accent,
}

/**
 * The handoff's plain rectangular button: full width by default, 14dp corners, 48dp tall,
 * text left-aligned like a list link unless [centered].
 *
 * [enabled] false draws the track fill with muted text and ignores taps (the pause sheet's
 * "Opens in 7s"). [dimmed] is for a button that should look unavailable but still answer a tap
 * so it can explain itself, as Lower does at the energy floor: transparent fill, muted text.
 */
@Composable
fun FlatButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: FlatButtonTone = FlatButtonTone.Soft,
    icon: IconResource? = null,
    centered: Boolean = false,
    enabled: Boolean = true,
    dimmed: Boolean = false,
    shape: Shape = AppTheme.shapes.inputLarge,
    minHeight: Dp = FlatButtonDefaults.MinHeight,
    typography: TypographyResource = AppTheme.type.menuItem,
) {
    val colors = AppTheme.colors
    val (container, content) = when {
        !enabled -> colors.track to colors.muted
        dimmed -> null to colors.muted
        else -> when (tone) {
            FlatButtonTone.Soft, FlatButtonTone.SoftOutlined -> colors.soft to colors.ink
            FlatButtonTone.Outlined -> null to colors.ink
            FlatButtonTone.Ink -> colors.ink to colors.bg
            FlatButtonTone.Accent -> colors.accent to colors.onAccent
        }
    }
    val bordered = tone == FlatButtonTone.Outlined || tone == FlatButtonTone.SoftOutlined
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .clip(shape)
            .thenIf(container != null) { background(container!!) }
            .thenIf(bordered) { border(1.dp, colors.line.color, shape) }
            .semantics {
                role = Role.Button
                if (!enabled) disabled()
            }
            .bounceClick(enabled = enabled, scaleDown = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) {
            Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
        } else {
            Arrangement.spacedBy(4.dp)
        },
    ) {
        if (icon != null) Icon(icon = icon, size = FlatButtonDefaults.IconSize, color = content)
        Text(
            text = text,
            typography = typography,
            color = content,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
    }
}

object FlatButtonDefaults {
    val MinHeight: Dp = 48.dp

    /** The pause sheet's main button. */
    val TallMinHeight: Dp = 52.dp

    /** Lower and Raise. */
    val StepperMinHeight: Dp = 46.dp

    /** Two buttons side by side inside a confirm box. */
    val CompactMinHeight: Dp = 44.dp

    internal val IconSize: Dp = 20.dp
}

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun FlatButtonPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlatButton(text = "Find a provider", onClick = {}, modifier = Modifier.fillMaxWidth())
            FlatButton(
                text = "Delete everything",
                onClick = {},
                tone = FlatButtonTone.Outlined,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlatButton(
                    text = "Lower",
                    onClick = {},
                    icon = AppIcons.Remove,
                    tone = FlatButtonTone.SoftOutlined,
                    centered = true,
                    dimmed = true,
                    shape = AppTheme.shapes.input,
                    minHeight = FlatButtonDefaults.StepperMinHeight,
                    modifier = Modifier.weight(1f),
                )
                FlatButton(
                    text = "Raise",
                    onClick = {},
                    icon = AppIcons.Add,
                    tone = FlatButtonTone.SoftOutlined,
                    centered = true,
                    shape = AppTheme.shapes.input,
                    minHeight = FlatButtonDefaults.StepperMinHeight,
                    modifier = Modifier.weight(1f),
                )
            }
            FlatButton(
                text = "Opens in 7s",
                onClick = {},
                tone = FlatButtonTone.Ink,
                centered = true,
                enabled = false,
                minHeight = FlatButtonDefaults.TallMinHeight,
                modifier = Modifier.fillMaxWidth(),
            )
            FlatButton(
                text = "Show them",
                onClick = {},
                tone = FlatButtonTone.Ink,
                centered = true,
                minHeight = FlatButtonDefaults.TallMinHeight,
                modifier = Modifier.fillMaxWidth(),
            )
            FlatButton(text = "Delete", onClick = {}, tone = FlatButtonTone.Accent, centered = true)
        }
    }
}
