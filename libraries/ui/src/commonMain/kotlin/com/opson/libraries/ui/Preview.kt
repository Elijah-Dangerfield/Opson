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
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import androidx.compose.ui.graphics.Color
import com.opson.system.thenIf

/**
 * A composable that is suitable as the root for any composable preview
 *
 * It will set up the theme and some suitable defaults like a background color.
 */
@Composable
fun PreviewContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    backgroundColor: ColorResource? = ThemeBackground,
    darkTheme: Boolean = false,
    largeText: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dialogHostState = rememberDialogHostState()
    CompositionLocalProvider(LocalDialogHostState provides dialogHostState) {
        AppTheme(darkTheme = darkTheme, largeText = largeText) {
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
    largeText: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable () -> Unit,
) {
    Column {
        listOf(false, true).forEach { dark ->
            AppTheme(darkTheme = dark, largeText = largeText) {
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

/** Sentinel for "use the theme's `bg`", resolved inside the theme so dark previews get a dark page. */
private val ThemeBackground = ColorResource.FromColor(Color.Unspecified, "preview-theme-background")
