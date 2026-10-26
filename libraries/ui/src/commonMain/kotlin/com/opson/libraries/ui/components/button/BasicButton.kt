package com.opson.libraries.ui.components.button

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.Radii
import com.opson.system.thenIf
import com.opson.libraries.ui.Border
import com.opson.libraries.ui.Elevation
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.StandardBorderWidth
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.components.Surface
import com.opson.libraries.ui.components.icon.SmallIcon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.text.ProvideTextConfig
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.components.text.TextConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BasicButton(
    backgroundColor: ColorResource?,
    borderColor: ColorResource?,
    contentColor: ColorResource,
    size: ButtonSize,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    deepColor: ColorResource? = null,          // the 3D lip; null = flat button
    icon: IconResource? = null,
    contentPadding: PaddingValues = size.padding(hasIcon = icon != null),
    enabled: Boolean = true,
    onDisabledTap: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        val effectiveOnClick = if (enabled) onClick else onDisabledTap

        val face: @Composable () -> Unit = {
            Surface(
                modifier = Modifier.semantics { role = Role.Button },
                radius = Radii.Button,
                // The handoff's buttons are flat; the 3D lip is the only depth.
                elevation = Elevation.None,
                color = backgroundColor,
                contentColor = contentColor,
                border = borderColor?.let { Border(it, OutlinedButtonBorderWidth) },
                contentPadding = contentPadding,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(
                        ButtonIconSpacing,
                        Alignment.CenterHorizontally
                    )
                ) {
                    if (icon != null) SmallIcon(icon = icon)
                    ProvideTextConfig(size.textConfig(), content = content)
                }
            }
        }

        if (deepColor == null) {
            // Flat path (outlined / text / disabled / default): original behaviour + bounce.
            Box(
                contentAlignment = Alignment.Center,
                // Propagate min constraints so a caller-supplied `Modifier.fillMaxWidth()`
                // (which sets minWidth = maxWidth on the outer Box) flows down to the Surface
                // and stretches the button. Default behavior — no width modifier — leaves
                // minWidth at 0, so the button still wraps content inside weighted Rows.
                propagateMinConstraints = true,
                modifier = modifier.thenIf(effectiveOnClick != null) {
                    bounceClick(
                        mutableInteractionSource = interactionSource,
                        onClick = effectiveOnClick!!
                    )
                },
            ) { face() }
        } else {
            // Springy 3D lip: a hard "deep" band sits behind the face; the face DROPS onto it
            // on press — no soft Material shadow, no scale bounce.
            val depthAtRest = size.pressDepth()
            val pressed by interactionSource.collectIsPressedAsState()
            // Kept as State: `.offset { }` already defers to the layout phase,
            // so `by` here would be the one thing forcing the button and its
            // content to recompose on every frame of the press spring.
            val drop = animateDpAsState(
                targetValue = if (pressed) depthAtRest else 0.dp,
                animationSpec = spring(stiffness = Spring.StiffnessHigh),
                label = "ButtonLip",
            )
            Box(
                contentAlignment = Alignment.Center,
                propagateMinConstraints = true,
                modifier = modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = effectiveOnClick != null,
                    onClick = { effectiveOnClick?.invoke() },
                ),
            ) {
                // deep band fills the whole box (face + the reserved lip strip)
                Box(Modifier.matchParentSize().clip(Radii.Button.shape).background(deepColor.color))
                // face sized normally + a constant bottom reserve (stable height); drops on press.
                // propagateMinConstraints carries the outer Box's min width (set by a caller's
                // fillMaxWidth) down to the face Surface, so a full-width button's face stretches to
                // match the lip band instead of wrapping its content on the left.
                Box(
                    propagateMinConstraints = true,
                    modifier = Modifier
                        .padding(bottom = depthAtRest)
                        .offset { IntOffset(x = 0, y = drop.value.roundToPx()) },
                ) { face() }
            }
        }
    }
}

/** Lip depth by size — the constant strip the face drops onto. */
private fun ButtonSize.pressDepth(): Dp = when (this) {
    ButtonSize.Large -> 5.dp
    ButtonSize.Medium -> 4.dp
    ButtonSize.Small -> 3.dp
    ButtonSize.ExtraSmall -> 3.dp
}


@Composable
private fun ButtonSize.textConfig(): TextConfig = when (this) {
    ButtonSize.ExtraSmall -> ExtraSmallButtonTextConfig

    ButtonSize.Small -> SmallButtonTextConfig

    ButtonSize.Medium -> MediumButtonTextConfig

    ButtonSize.Large -> LargeButtonTextConfig
}

internal fun ButtonSize.padding(hasIcon: Boolean): PaddingValues =
    when (this) {
        ButtonSize.ExtraSmall -> if (hasIcon) ExtraSmallButtonWithIconPadding else ExtraSmallButtonPadding
        ButtonSize.Small -> if (hasIcon) SmallButtonWithIconPadding else SmallButtonPadding
        ButtonSize.Medium -> if (hasIcon) MediumButtonWithIconPadding else MediumButtonPadding
        ButtonSize.Large -> if (hasIcon) LargeButtonWithIconPadding else LargeButtonPadding
    }

// Button type follows the handoff: sentence case, 700 weight. Large is the 1em primary
// call to action (52dp tall), Medium and Small the 0.875em pill and inline buttons.

private val ExtraSmallButtonTextConfig: TextConfig
    @Composable get() = TextConfig(
        typography = AppTheme.type.secondarySmallStrong,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        allCaps = false
    )

private val SmallButtonTextConfig: TextConfig
    @Composable get() = TextConfig(
        typography = AppTheme.type.buttonSmall,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        allCaps = false
    )

private val MediumButtonTextConfig: TextConfig
    @Composable get() = TextConfig(
        typography = AppTheme.type.buttonSmall,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        allCaps = false
    )

private val LargeButtonTextConfig: TextConfig
    @Composable get() = TextConfig(
        typography = AppTheme.type.button,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        allCaps = false
    )


// Button padding follows a systematic approach:
// - Vertical padding stays consistent per size for predictable touch targets
// - Horizontal padding provides breathing room for text
// - Icon variants reduce start padding slightly (icon provides visual weight)
//   and increase end padding slightly (icon takes horizontal space)
// This creates balanced, symmetric button layouts

private val ExtraSmallButtonPadding = PaddingValues(
    horizontal = Dimension.D600,  // 14dp horizontal
    vertical = Dimension.D400      // 10dp vertical
)

private val ExtraSmallButtonWithIconPadding = PaddingValues(
    horizontal = Dimension.D500,  // 12dp both sides (icon adds visual weight)
    vertical = Dimension.D400      // 10dp vertical (same as text-only)
)

private val SmallButtonPadding = PaddingValues(
    horizontal = Dimension.D800,  // 20dp horizontal
    vertical = Dimension.D500      // 12dp vertical
)

private val SmallButtonWithIconPadding = PaddingValues(
    start = Dimension.D600,       // 14dp start (reduced, icon adds weight)
    end = Dimension.D800,          // 20dp end (extra space for icon)
    top = Dimension.D500,          // 12dp vertical (same as text-only)
    bottom = Dimension.D500
)

private val MediumButtonPadding = PaddingValues(
    horizontal = Dimension.D900,  // 24dp horizontal
    vertical = Dimension.D600      // 14dp vertical
)

private val MediumButtonWithIconPadding = PaddingValues(
    start = Dimension.D700,       // 16dp start (reduced, icon adds weight)
    end = Dimension.D900,          // 24dp end (extra space for icon)
    top = Dimension.D600,          // 14dp vertical (same as text-only)
    bottom = Dimension.D600
)

// 15dp vertical on 1em text gives the handoff's 52dp primary button.
private val LargeButtonPadding = PaddingValues(
    horizontal = 18.dp,
    vertical = 15.dp
)

private val LargeButtonWithIconPadding = PaddingValues(
    start = 16.dp,
    end = 18.dp,
    top = 15.dp,
    bottom = 15.dp
)

private val ButtonIconSpacing = Dimension.D200
private val OutlinedButtonBorderWidth = StandardBorderWidth

@Preview
@Composable
private fun LargeButton() {
    PreviewContent {
        BasicButton(
            backgroundColor = AppTheme.colors.accentPrimary,
            borderColor = null,
            contentColor = AppTheme.colors.onAccentPrimary,
            size = ButtonSize.Large,
            onClick = {},
            content = { Text(text = "Filled Button") }
        )
    }
}

@Preview
@Composable
private fun MediumButton() {
    PreviewContent {
        BasicButton(
            backgroundColor = null,
            borderColor = AppTheme.colors.border,
            contentColor = AppTheme.colors.text,
            size = ButtonSize.Medium,
            onClick = {},
            content = { Text(text = "Outlined Button") }
        )
    }
}

@Preview
@Composable
private fun SmallButton() {
    PreviewContent {
        BasicButton(
            backgroundColor = null,
            borderColor = null,
            contentColor = AppTheme.colors.accentPrimary,
            size = ButtonSize.Small,
            onClick = {},
            content = { Text(text = "Text Button") }
        )
    }
}
