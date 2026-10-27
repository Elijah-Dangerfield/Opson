package com.opson.libraries.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.components.dialog.DialogHost
import com.opson.libraries.ui.components.dialog.LocalDialogHostState
import com.opson.libraries.ui.components.dialog.rememberDialogHostState
import com.opson.libraries.ui.system.LocalAppState
import com.opson.libraries.ui.system.LocalBuildInfo
import com.opson.libraries.ui.system.LocalClock
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.core.AppState
import com.opson.libraries.core.BuildInfo
import com.opson.libraries.core.fixed
import com.opson.system.AppTheme
import com.opson.system.background
import androidx.compose.ui.graphics.Color
import com.opson.system.color.Palette
import com.opson.system.thenIf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A composable that is suitable as the root for any composable preview
 *
 * It will set up the theme and some suitable defaults like a background color.
 */
@Composable
fun PreviewContent(
    modifier: Modifier = Modifier,
    appState: AppState = PreviewAppState,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    backgroundColor: ColorResource? = ThemeBackground,
    palette: Palette = Palette.Pine,
    darkTheme: Boolean = false,
    largeText: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dialogHostState = rememberDialogHostState()
    CompositionLocalProvider(
        LocalAppState provides appState,
        LocalClock provides Clock.fixed(Instant.parse("2023-01-01T00:00:00Z")),
        LocalBuildInfo provides BuildInfo,
        LocalDialogHostState provides dialogHostState
    ) {
        AppTheme(palette = palette, darkTheme = darkTheme, largeText = largeText) {
            val resolvedBackground = if (backgroundColor === ThemeBackground) AppTheme.colors.bg else backgroundColor
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .thenIf(resolvedBackground != null) { background(resolvedBackground!!) }
                    .padding(contentPadding),
            ) {
                content()
                DialogHost(
                    modifier = Modifier.matchParentSize(),
                    hostState = dialogHostState
                )
            }
        }
    }
}


/**
 * Renders [content] once in light and once in dark, stacked, on the page background with page
 * padding. The standard wrapper for design-system component previews.
 */
@Composable
fun PreviewLightDark(
    palette: Palette = Palette.Pine,
    largeText: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppState provides PreviewAppState,
        LocalClock provides Clock.fixed(Instant.parse("2023-01-01T00:00:00Z")),
        LocalBuildInfo provides BuildInfo,
    ) {
        Column {
            listOf(false, true).forEach { dark ->
                AppTheme(palette = palette, darkTheme = dark, largeText = largeText) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppTheme.colors.bg)
                            .padding(contentPadding)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/** Sentinel for "use the theme's `bg`", resolved inside the theme so dark previews get a dark page. */
private val ThemeBackground = ColorResource.FromColor(Color.Unspecified, "preview-theme-background")

val PreviewAppState = object : AppState {
    override val isOffline: StateFlow<Boolean> = MutableStateFlow(false)

    override val isBlockActive: StateFlow<Boolean> = MutableStateFlow(false)
}