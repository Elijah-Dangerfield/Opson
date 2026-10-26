package com.opson.features.settings.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.Screen
import com.opson.system.AppTheme
import org.jetbrains.compose.resources.StringResource

/**
 * The frame every More page shares: the page colour, system-bar insets, page padding, the
 * section gap, and one scroll for the whole page.
 */
@Composable
internal fun SettingsPage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
            content = content,
        )
    }
}

/** A one-off message for the snackbar. Every settings view model's toast event carries one. */
data class Toast(val message: StringResource, val args: List<Any> = emptyList())

/** Light and dark side by side, for previews of whole pages. */
@Composable
internal fun LightAndDark(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxSize()) {
        listOf(false, true).forEach { dark ->
            Box(Modifier.weight(1f)) {
                PreviewContent(darkTheme = dark) { content() }
            }
        }
    }
}
