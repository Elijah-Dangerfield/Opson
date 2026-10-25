package com.opson.libraries.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.opson.system.Dimension
import kotlin.math.min

/**
 * Softens the bottom edge of scrollable content, so a list that continues past
 * the fold looks cut off on purpose rather than by the window.
 *
 * The band is anchored to the bottom of the *viewport* and shrinks as the end
 * comes into reach, reaching zero exactly when there is nothing left to scroll.
 * It previously anchored at `height - maxValue + scroll`, which for any content
 * taller than the viewport is a coordinate above the top of the screen — so
 * every visible pixel fell past the gradient's transparent end and `DstIn`
 * erased the lot. It read as the fade covering most of the content and then
 * retreating as you scrolled, which is precisely backwards.
 *
 * The colour is irrelevant and deliberately absent: `DstIn` multiplies the
 * destination by the *source's alpha*, so only the gradient's opacity does any
 * work. Reading a theme colour here is what forced this through `composed`,
 * which allocates on every composition and cannot be skipped.
 */
fun Modifier.fadingEdge(
    scrollState: ScrollState,
    height: Dp = Dimension.D1500,
): Modifier = this
    // The layer is what gives the blend something to blend against; without it
    // DstIn has no isolated destination and the gradient does nothing.
    .graphicsLayer { alpha = 0.99f }
    .drawWithContent {
        drawContent()

        val remaining = (scrollState.maxValue - scrollState.value).toFloat()
        if (remaining <= 0f) return@drawWithContent

        val band = min(height.toPx(), remaining)
        drawRect(
            brush = Brush.verticalGradient(
                colors = FadeToNothing,
                startY = size.height - band,
                endY = size.height,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** As above, for a lazy list: the band shrinks as the final item comes fully
 *  into view rather than as a scroll offset closes. */
fun Modifier.fadingEdge(listState: LazyListState): Modifier = this
    .graphicsLayer { alpha = 0.99f }
    .drawWithContent {
        drawContent()
        if (!listState.canScrollForward) return@drawWithContent

        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull() ?: return@drawWithContent
        val full = Dimension.D1500.toPx()

        val band = if (last.index == info.totalItemsCount - 1 && last.size > 0) {
            val visible = (info.viewportEndOffset - last.offset).toFloat() / last.size
            full * (1f - visible).coerceIn(0f, 1f)
        } else {
            full
        }

        drawRect(
            brush = Brush.verticalGradient(
                colors = FadeToNothing,
                startY = size.height - band,
                endY = size.height,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** Opaque to nothing. Only the alpha is read; see the note on [fadingEdge]. */
private val FadeToNothing = listOf(Color.Black, Color.Transparent)
