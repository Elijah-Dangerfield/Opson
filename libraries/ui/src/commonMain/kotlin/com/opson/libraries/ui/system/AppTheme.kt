package com.opson.system

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.remember
import com.opson.libraries.ui.system.LocalColors
import com.opson.libraries.ui.system.LocalContentColor
import com.opson.libraries.ui.system.LocalTypeScale
import com.opson.libraries.ui.system.LocalTypography
import com.opson.system.color.Colors
import com.opson.system.color.Palette
import com.opson.system.color.appColors
import com.opson.system.typography.AppFontFamily
import com.opson.system.typography.TypeScale
import com.opson.system.typography.rememberTypography

/**
 * Every design token, read inside a composition:
 *
 * ```kotlin
 * Box(Modifier.background(AppTheme.colors.hero.color, AppTheme.shapes.heroBlock))
 * Text("Water", typography = AppTheme.type.cardTitle)
 * Spacer(Modifier.height(AppTheme.spacing.section))
 * IconTile(AppIcons.WaterDrop, tone = AppTheme.colors.nutrients.water)
 * ```
 *
 * [type] is the handoff's scale and is what new code uses. [typography] is the older role-named
 * scale, kept so existing screens compile; it renders in the same font.
 */
object AppTheme {
    val colors: Colors
        @ReadOnlyComposable
        @Composable
        get() = LocalColors.current

    val type: TypeScale
        @ReadOnlyComposable
        @Composable
        get() = LocalTypeScale.current

    val typography
        @ReadOnlyComposable
        @Composable
        get() = LocalTypography.current

    val shapes: AppShapes get() = DefaultShapes

    val spacing: AppSpacing get() = DefaultSpacing

    val shadows: AppShadows get() = DefaultShadows
}

/**
 * The app theme. [palette] is the user's brand pick, [darkTheme] follows the system unless the
 * caller overrides it, and [largeText] moves the type base from 16 to 19 sp.
 */
@Composable
fun AppTheme(
    palette: Palette = Palette.Pine,
    darkTheme: Boolean = isSystemInDarkTheme(),
    largeText: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = remember(palette, darkTheme) { appColors(palette, darkTheme) }

    val textSelectionColors = TextSelectionColors(
        handleColor = colors.accent.color,
        backgroundColor = colors.accent.color.copy(alpha = 0.4F)
    )

    val typography = rememberTypography()
    val fontFamily = AppFontFamily
    val baseSize = if (largeText) TypeScale.LARGE_BASE_SIZE else TypeScale.BASE_SIZE
    val typeScale = remember(fontFamily, baseSize) { TypeScale(fontFamily, baseSize) }

    MaterialWrapper {
        CompositionLocalProvider(
            LocalContentColor provides colors.ink,
            LocalTextSelectionColors provides textSelectionColors,
            LocalTypography provides typography,
            LocalTypeScale provides typeScale,
            androidx.compose.material3.LocalContentColor provides colors.ink.color,
            LocalColors provides colors,
            content = content
        )
    }
}

/** The default theme: Pine, following the system light or dark setting. */
@Composable
fun AppThemeProvider(
    content: @Composable () -> Unit
) {
    AppTheme(content = content)
}

@Composable
private fun MaterialWrapper(content: @Composable () -> Unit) {
    val invalidTextStyle = TextStyle(color = Color.Red)
    MaterialTheme(
        typography = Typography(
            displayLarge = invalidTextStyle,
            displayMedium = invalidTextStyle,
            displaySmall = invalidTextStyle,
            headlineLarge = invalidTextStyle,
            headlineMedium = invalidTextStyle,
            headlineSmall = invalidTextStyle,
            titleLarge = invalidTextStyle,
            titleMedium = invalidTextStyle,
            titleSmall = invalidTextStyle,
            bodyLarge = invalidTextStyle,
            bodyMedium = invalidTextStyle,
            bodySmall = invalidTextStyle,
            labelLarge = invalidTextStyle,
            labelMedium = invalidTextStyle,
            labelSmall = invalidTextStyle
        ),
        colorScheme = ColorScheme(
            primary = Color.Red,
            onPrimary = Color.Red,
            primaryContainer = Color.Red,
            onPrimaryContainer = Color.Red,
            inversePrimary = Color.Red,
            secondary = Color.Red,
            onSecondary = Color.Red,
            secondaryContainer = Color.Red,
            onSecondaryContainer = Color.Red,
            tertiary = Color.Red,
            onTertiary = Color.Red,
            tertiaryContainer = Color.Red,
            onTertiaryContainer = Color.Red,
            background = Color.Red,
            onBackground = Color.Red,
            surface = Color.Red,
            onSurface = Color.Red,
            surfaceVariant = Color.Red,
            onSurfaceVariant = Color.Red,
            surfaceTint = Color.Red,
            inverseSurface = Color.Red,
            inverseOnSurface = Color.Red,
            error = Color.Red,
            onError = Color.Red,
            errorContainer = Color.Red,
            onErrorContainer = Color.Red,
            outline = Color.Red,
            outlineVariant = Color.Red,
            scrim = Color.Red
        ),
        content = content
    )
}
