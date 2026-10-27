package com.opson.libraries.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import kotlinx.coroutines.delay
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.ds_loading_almost_there
import opson.libraries.resources.generated.resources.ds_loading_getting_ready
import opson.libraries.resources.generated.resources.ds_loading_your_data
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A reassuring loading caption that only appears once a load is taking a while,
 * then crossfades through a list of [messages] on a fixed cadence.
 *
 * Used to soften a held splash / bootstrap wait: nothing shows for the first
 * [startDelay] (so a fast load never flashes text), after which the first
 * message fades in and the carousel rotates every [interval]. Purely cosmetic —
 * it owns no loading state; the caller hides it when the work completes.
 *
 * Messages cycle in order and wrap. A preview shows the first one, still.
 */
@Composable
fun CyclingLoadingMessage(
    messages: List<String>,
    modifier: Modifier = Modifier,
    startDelay: Duration = 5.seconds,
    interval: Duration = 2.5.seconds,
) {
    if (messages.isEmpty()) return

    val inspecting = LocalInspectionMode.current
    var index by remember(messages) { mutableStateOf(if (inspecting) 0 else -1) }

    LaunchedEffect(messages) {
        if (inspecting) return@LaunchedEffect
        delay(startDelay)
        index = 0
        while (true) {
            delay(interval)
            index = (index + 1) % messages.size
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                fadeIn(tween(400)) togetherWith fadeOut(tween(400))
            },
            label = "CyclingLoadingMessage",
        ) { current ->
            if (current >= 0) {
                Text(
                    text = messages[current],
                    color = AppTheme.colors.textSecondary,
                    typography = AppTheme.typography.Body.B500,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The phrases for a held app-bootstrap wait. */
val DefaultBootLoadingMessages: List<String>
    @Composable get() = listOf(
        stringResource(Res.string.ds_loading_getting_ready),
        stringResource(Res.string.ds_loading_your_data),
        stringResource(Res.string.ds_loading_almost_there),
    )

@Preview
@Composable
private fun PreviewCyclingLoadingMessage() {
    PreviewContent {
        CyclingLoadingMessage(messages = DefaultBootLoadingMessages)
    }
}
