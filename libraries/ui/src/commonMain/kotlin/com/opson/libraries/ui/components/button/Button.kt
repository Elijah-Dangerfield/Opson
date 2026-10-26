/**
 * # Button Component System
 *
 * A button exposes ONE emphasis semantic — [ButtonType] — plus a [ButtonStyle] (Filled / Outlined
 * / Text) and a [ButtonSize]. Opting into `deep` renders a hard 3D "lip" under a filled, enabled
 * button that the face drops onto when pressed; the default treatment stays flat.
 *
 * ## Emphasis hierarchy (most → least prominent)
 *
 * | Type | Use case | Example |
 * |------|----------|---------|
 * | **Primary**   | The main CTA — accentPrimary by default | "Continue", "Save", "Sign In" |
 * | **Secondary** | Important but not the CTA — neutral fill / border | "Cancel", "Skip" |
 * | **Ghost**     | Minimal weight, inline links | "Forgot Password?", "Terms" |
 * | **Danger**    | Destructive action | "Delete", "Sign out" |
 *
 * Limit Primary to 1–2 per screen. Use Filled > Outlined > Text for decreasing emphasis.
 *
 * ## Accent (the rare two-CTA case)
 *
 * A *filled Primary* can be recolored with [ButtonAccent] (Primary = accentPrimary, Secondary =
 * accentSecondary). Accent is **role-named, never a literal color** — repointing an accent token
 * never touches a button. Reach for it only when a screen genuinely needs two primary-level
 * actions in different brand colors.
 *
 * ```kotlin
 * ButtonPrimary(onClick = { }) { Text("Continue") }
 * ButtonSecondary(onClick = { }) { Text("Cancel") }
 * ButtonGhost(onClick = { }) { Text("Forgot Password?") }
 * ButtonDanger(onClick = { }) { Text("Delete") }
 *
 * // two distinct primary-level CTAs
 * ButtonPrimary(onClick = { }, accent = ButtonAccent.Secondary) { Text("Upgrade") }
 *
 * // opt into the springy 3D lip
 * ButtonPrimary(onClick = { }, deep = true) { Text("Continue") }
 *
 * // full control
 * Button(type = ButtonType.Primary, style = ButtonStyle.Outlined, size = ButtonSize.Small, onClick = { }) {
 *     Text("Continue")
 * }
 * ```
 */
package com.opson.libraries.ui.components.button

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.system.color.animateColorResourceAsState
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.system.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.opson.libraries.ui.catalog.BUTTON_SUBTITLE
import com.opson.libraries.ui.catalog.ButtonCatalogBody
import com.opson.libraries.ui.catalog.CatalogPage

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: IconResource? = null,
    type: ButtonType = LocalButtonType.current,
    accent: ButtonAccent = ButtonAccent.Primary,
    size: ButtonSize = LocalButtonSize.current,
    style: ButtonStyle = LocalButtonStyle.current,
    enabled: Boolean = true,
    deep: Boolean = false,
    onDisabledTap: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val backgroundColor = backgroundColor(type, accent, style, enabled)
        ?.let { targetColor ->
            key(type, accent, style) {
                animateColorResourceAsState(
                    targetValue = targetColor,
                    label = "Background_Color_Anim"
                )
            }.value
        }

    val contentColor by key(type, accent, style) {
        animateColorResourceAsState(
            targetValue = contentColor(type, accent, style, enabled),
            label = "Content_Color_Anim"
        )
    }

    val borderColor = borderColor(type, accent, style, enabled)
    val deepColor = deepColor(type, accent, style, enabled, deep)

    BasicButton(
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        contentColor = contentColor,
        deepColor = deepColor,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        size = size,
        enabled = enabled,
        onDisabledTap = onDisabledTap,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Emphasis hierarchy — the only semantic a button exposes (most → least prominent):
 * Primary > Secondary > Ghost. Danger is destructive emphasis, orthogonal to the ladder.
 *
 * - **Primary** — the main CTA. accentPrimary filled by default; recolor with [ButtonAccent].
 *   1–2 per screen.
 * - **Secondary** — important but not the CTA. Neutral fill (filled) or border (outlined).
 * - **Ghost** — minimal weight, text-only. Inline links, supplementary actions.
 * - **Danger** — destructive action (danger token).
 */
enum class ButtonType {
    /** The main CTA — accentPrimary filled by default. */
    Primary,

    /** Important but not the CTA — neutral fill / border. */
    Secondary,

    /** Text-only button — minimal visual weight. */
    Ghost,

    /** Destructive action. */
    Danger,
}

/**
 * Which accent a *filled Primary* renders. Role-named, never a literal color, so repointing an
 * accent token never touches a button.
 */
enum class ButtonAccent { Primary, Secondary }

enum class ButtonSize {
    Large,
    Medium,
    Small,
    ExtraSmall
}

/**
 * The visual treatment, independent of [ButtonType] emphasis.
 *
 * - **Filled** — solid background; highest prominence. Filled + enabled + `deep` gets the 3D lip.
 * - **Outlined** — border only, transparent fill; medium prominence, works on any surface.
 * - **Text** — no background or border; minimal weight, reads as a link.
 * - **Tonal** — the soft accent tint with accent text, design v3's second action beside a primary
 *   ("Pick and keep going", "Save the name only").
 */
enum class ButtonStyle {
    Filled,
    Outlined,
    Text,
    Tonal,
}

@Composable
fun ProvideButtonConfig(
    type: ButtonType = LocalButtonType.current,
    size: ButtonSize = LocalButtonSize.current,
    style: ButtonStyle = LocalButtonStyle.current,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalButtonType provides type,
        LocalButtonSize provides size,
        LocalButtonStyle provides style,
        content = content
    )
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Convenience Functions - For Better Code Readability
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Primary button - Main call-to-action.
 *
 * Use for the most important action on a screen (e.g., "Continue", "Save", "Submit").
 * Limit to 1-2 per screen for maximum impact.
 *
 * @see Button for full documentation
 */
@Composable
fun ButtonPrimary(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: IconResource? = null,
    accent: ButtonAccent = ButtonAccent.Primary,
    size: ButtonSize = LocalButtonSize.current,
    style: ButtonStyle = ButtonStyle.Filled,
    onDisabledTap: (() -> Unit)? = null,
    enabled: Boolean = true,
    deep: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        type = ButtonType.Primary,
        accent = accent,
        size = size,
        onDisabledTap = onDisabledTap,
        style = style,
        enabled = enabled,
        deep = deep,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Secondary button - important but not primary.
 *
 * Use for important actions that aren't the main CTA (e.g., "Cancel", "Skip", "Back").
 * Multiple allowed per screen.
 *
 * @see Button for full documentation
 */
@Composable
fun ButtonSecondary(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: IconResource? = null,
    size: ButtonSize = LocalButtonSize.current,
    style: ButtonStyle = ButtonStyle.Outlined,
    onDisabledTap: (() -> Unit)? = null,
    enabled: Boolean = true,
    deep: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        type = ButtonType.Secondary,
        size = size,
        onDisabledTap = onDisabledTap,
        style = style,
        enabled = enabled,
        deep = deep,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Ghost button - Text-only with minimal visual weight.
 *
 * Use for supplementary actions and inline links (e.g., "Forgot Password?", "Terms").
 *
 * @see Button for full documentation
 */
@Composable
fun ButtonGhost(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: IconResource? = null,
    size: ButtonSize = LocalButtonSize.current,
    style: ButtonStyle = ButtonStyle.Text,
    enabled: Boolean = true,
    deep: Boolean = false,
    onDisabledTap: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        type = ButtonType.Ghost,
        size = size,
        onDisabledTap = onDisabledTap,
        style = style,
        enabled = enabled,
        deep = deep,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Danger button
 * @see Button for full documentation
 */
@Composable
fun ButtonDanger(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: IconResource? = null,
    size: ButtonSize = LocalButtonSize.current,
    style: ButtonStyle = ButtonStyle.Filled,
    onDisabledTap: (() -> Unit)? = null,
    enabled: Boolean = true,
    deep: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        type = ButtonType.Danger,
        onDisabledTap = onDisabledTap,
        size = size,
        style = style,
        enabled = enabled,
        deep = deep,
        interactionSource = interactionSource,
        content = content
    )
}

private val LocalButtonType =
    compositionLocalOf { ButtonType.Primary }
internal val LocalButtonSize =
    compositionLocalOf { ButtonSize.Large }
private val LocalButtonStyle =
    compositionLocalOf { ButtonStyle.Filled }

// ── accent token resolvers ───────────────────────────────────
@Composable
@ReadOnlyComposable
private fun accentSolid(a: ButtonAccent) = when (a) {
    ButtonAccent.Primary -> AppTheme.colors.accentPrimary
    ButtonAccent.Secondary -> AppTheme.colors.accentSecondary
}

@Composable
@ReadOnlyComposable
private fun onAccent(a: ButtonAccent) = when (a) {
    ButtonAccent.Primary -> AppTheme.colors.onAccentPrimary
    ButtonAccent.Secondary -> AppTheme.colors.onAccentSecondary
}

/**
 * The hard band under a `deep` filled button. Derived from the face color
 * rather than a dedicated token so the lip tracks any palette repoint —
 * the template's color system has no `*Deep` tokens on purpose.
 */
private const val DeepDarkenFraction = 0.3f

private fun ColorResource.deepened(): ColorResource = ColorResource.FromColor(
    color = lerp(color, Color.Black, DeepDarkenFraction),
    name = "$designSystemName-deep",
)

@Composable
@ReadOnlyComposable
private fun backgroundColor(
    type: ButtonType,
    accent: ButtonAccent,
    style: ButtonStyle,
    enabled: Boolean,
): ColorResource? = when {
    !enabled && (style == ButtonStyle.Filled || style == ButtonStyle.Tonal) -> AppTheme.colors.surfaceDisabled
    style == ButtonStyle.Tonal -> AppTheme.colors.accentTint
    style != ButtonStyle.Filled -> null
    else -> when (type) {
        ButtonType.Primary -> accentSolid(accent)
        // The handoff's secondary buttons ("Today", "Raise") sit on the soft fill.
        ButtonType.Secondary -> AppTheme.colors.soft
        ButtonType.Ghost -> null
        ButtonType.Danger -> AppTheme.colors.danger
    }
}

@Composable
@ReadOnlyComposable
private fun deepColor(
    type: ButtonType,
    accent: ButtonAccent,
    style: ButtonStyle,
    enabled: Boolean,
    deep: Boolean,
): ColorResource? = when {
    // only filled, enabled buttons that opt in get the lip
    !deep || !enabled || style != ButtonStyle.Filled -> null
    else -> when (type) {
        ButtonType.Primary -> accentSolid(accent).deepened()
        ButtonType.Secondary -> AppTheme.colors.border
        ButtonType.Ghost -> null
        ButtonType.Danger -> AppTheme.colors.danger.deepened()
    }
}

@Composable
@ReadOnlyComposable
private fun borderColor(
    type: ButtonType,
    accent: ButtonAccent,
    style: ButtonStyle,
    enabled: Boolean
): ColorResource? = when {
    style != ButtonStyle.Outlined -> null
    !enabled -> AppTheme.colors.borderDisabled
    else -> when (type) {
        ButtonType.Primary -> accentSolid(accent)
        ButtonType.Secondary -> AppTheme.colors.border
        ButtonType.Ghost -> null
        ButtonType.Danger -> AppTheme.colors.danger
    }
}

@Composable
@ReadOnlyComposable
private fun contentColor(
    type: ButtonType,
    accent: ButtonAccent,
    style: ButtonStyle,
    enabled: Boolean
): ColorResource = when {
    !enabled -> AppTheme.colors.textDisabled
    style == ButtonStyle.Tonal -> AppTheme.colors.accent
    style == ButtonStyle.Filled -> when (type) {
        ButtonType.Primary -> onAccent(accent)
        ButtonType.Secondary -> AppTheme.colors.ink
        ButtonType.Ghost -> AppTheme.colors.accent
        ButtonType.Danger -> AppTheme.colors.onAccent
    }
    style == ButtonStyle.Outlined -> when (type) {
        ButtonType.Primary -> accentSolid(accent)
        ButtonType.Secondary -> AppTheme.colors.text
        ButtonType.Ghost -> AppTheme.colors.text
        ButtonType.Danger -> AppTheme.colors.danger
    }
    else -> when (type) { // Text
        ButtonType.Danger -> AppTheme.colors.danger
        ButtonType.Secondary -> AppTheme.colors.textSecondary
        // Text buttons ("Stop asking these") are accent links in the handoff.
        else -> AppTheme.colors.accent
    }
}

@Preview(widthDp = 1100, heightDp = 1700)
@Composable
private fun ButtonsPreview() {
    CatalogPage(title = "Buttons", subtitle = BUTTON_SUBTITLE) { ButtonCatalogBody() }
}
