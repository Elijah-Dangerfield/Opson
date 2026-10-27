package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.opson.libraries.ui.PreviewLightDark
import com.opson.system.AppTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A centred spinner for content that is still loading. It stays empty for
 * [delay] first, so an answer that comes back quickly doesn't flash a spinner.
 * Previews draw it straight away.
 */
@Composable
fun DelayedLoadingIndicator(
    contentDescription: String,
    modifier: Modifier = Modifier,
    delay: Duration = 500.milliseconds,
) {
    val inspecting = LocalInspectionMode.current
    var visible by remember { mutableStateOf(inspecting) }
    LaunchedEffect(Unit) {
        delay(delay)
        visible = true
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppTheme.spacing.section)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (visible) CircularProgressIndicator()
    }
}

@Preview
@Composable
private fun DelayedLoadingIndicatorPreview() {
    PreviewLightDark {
        DelayedLoadingIndicator(contentDescription = "Loading")
    }
}
