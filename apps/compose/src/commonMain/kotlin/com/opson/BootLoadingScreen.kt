package com.opson

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.CyclingLoadingMessage
import com.opson.libraries.ui.components.DefaultBootLoadingMessages
import com.opson.libraries.ui.components.OpsonLoader
import com.opson.system.AppTheme
import com.opson.system.Dimension
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.ds_loading
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The Compose loading gate shown after the platform splash hands off but
 * before the app has finished booting (app-config + profile resolve): the
 * steaming bowl, centred, where the iOS launch screen drew it still. It
 * surfaces [CyclingLoadingMessage] once the wait runs long (it self-delays,
 * so a fast boot never flashes the caption).
 *
 * The caption start-delay is short enough that the status text cycles during
 * the wait rather than appearing only after a long hold.
 */
@Composable
fun BootLoadingScreen(
    modifier: Modifier = Modifier,
    messageStartDelay: Duration = 1.5.seconds,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.background.color),
        contentAlignment = Alignment.Center,
    ) {
        OpsonLoader(contentDescription = stringResource(Res.string.ds_loading))
        CyclingLoadingMessage(
            messages = DefaultBootLoadingMessages,
            startDelay = messageStartDelay,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = Dimension.D900, vertical = Dimension.D1700),
        )
    }
}

@Preview
@Composable
private fun BootLoadingScreenPreview_CaptionShowing() {
    PreviewContent {
        BootLoadingScreen(messageStartDelay = 0.milliseconds)
    }
}

@Preview
@Composable
private fun BootLoadingScreenPreview_Dark() {
    PreviewContent(darkTheme = true) {
        BootLoadingScreen(messageStartDelay = 0.milliseconds)
    }
}
