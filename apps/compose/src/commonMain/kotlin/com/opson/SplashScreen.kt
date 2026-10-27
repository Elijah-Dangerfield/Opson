package com.opson

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.OpsonLockup
import com.opson.system.AppTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

private const val FadeInMillis = 450
private const val HoldMillis = 650
private const val FadeOutMillis = 450

/**
 * iOS's splash: the lockup's wordmark fades in under the mark, holds, and then the whole splash
 * fades out onto the app. The mark is there from the first frame because it sits where the
 * launch screen drew it, so only the wordmark appears to arrive. Android uses the platform
 * splash API.
 */
@Composable
fun SplashOverlay(
    onComplete: () -> Unit,
) {
    val wordmark = remember { Animatable(0f) }
    val splash = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        wordmark.animateTo(1f, tween(FadeInMillis, easing = LinearEasing))
        delay(HoldMillis.toLong())
        splash.animateTo(0f, tween(FadeOutMillis, easing = LinearEasing))
        onComplete()
    }

    SplashContent(wordmarkAlpha = { wordmark.value }, alpha = { splash.value })
}

/**
 * Takes the fades as lambdas, not `Float`s, so the reads happen in the draw
 * phase inside `graphicsLayer`. Passing `alpha.value` would subscribe the
 * caller to a value that moves every frame for the whole splash.
 */
@Composable
private fun SplashContent(wordmarkAlpha: () -> Float, alpha: () -> Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha() }
            .background(AppTheme.colors.background.color),
        contentAlignment = Alignment.Center,
    ) {
        OpsonLockup(wordmarkAlpha = wordmarkAlpha)
    }
}

@Preview
@Composable
private fun PreviewSplashOverlay() {
    PreviewContent {
        SplashContent(wordmarkAlpha = { 1f }, alpha = { 1f })
    }
}

@Preview
@Composable
private fun PreviewSplashOverlayDark() {
    PreviewContent(darkTheme = true) {
        SplashContent(wordmarkAlpha = { 1f }, alpha = { 1f })
    }
}
