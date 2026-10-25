package com.opson.libraries.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.opson.system.AppTheme
import com.opson.libraries.ui.system.color.ColorResource
import kotlin.math.max

/**
 * An overlay scrollbar that fades in while [state] is scrolling and fades out
 * again after it settles.
 *
 * `@Composable` rather than the deprecated `composed`: the fade needs
 * `animateFloatAsState` and the indicator colour comes from [AppTheme], both of
 * which have to run in composition. What `composed` added on top of that was a
 * fresh modifier instance per composition and a chain that can never skip —
 * paid by every scrolling screen in the app, on every recomposition, animating
 * or not.
 */
@Composable
fun Modifier.scrollbar(
    state: ScrollState,
    direction: Orientation,
    indicatorThickness: Dp = 4.dp,
    alpha: Float = if (state.isScrollInProgress) 0.8f else 0f,
    alphaAnimationSpec: AnimationSpec<Float> = tween(
        delayMillis = if (state.isScrollInProgress) 0 else 1500,
        durationMillis = if (state.isScrollInProgress) 150 else 500
    ),
    padding: PaddingValues = PaddingValues(all = 0.dp)
): Modifier {
    // Kept as State and read inside drawWithContent below, not unwrapped with
    // `by` here: `by` would subscribe this composable to a value that changes
    // every animation frame, recomposing the whole scrolling subtree for the
    // life of the fade. Read in the draw lambda, only the draw phase
    // invalidates.
    val scrollbarAlpha = animateFloatAsState(
        targetValue = alpha,
        animationSpec = alphaAnimationSpec
    )

    val indicatorColor = AppTheme.colors.surfaceDisabled

    return drawWithContent {
        drawContent()

        val showScrollBar = state.isScrollInProgress || scrollbarAlpha.value > 0.0f

        // Draw scrollbar only if currently scrolling or if scroll animation is ongoing.
        if (showScrollBar) {
            val (topPadding, bottomPadding, startPadding, endPadding) = listOf(
                padding.calculateTopPadding().toPx(), padding.calculateBottomPadding().toPx(),
                padding.calculateStartPadding(layoutDirection).toPx(),
                padding.calculateEndPadding(layoutDirection).toPx()
            )

            val contentOffset = state.value
            val viewPortLength = if (direction == Orientation.Vertical)
                size.height else size.width
            val viewPortCrossAxisLength = if (direction == Orientation.Vertical)
                size.width else size.height
            val contentLength = max(viewPortLength + state.maxValue, 0.001f)  // To prevent divide by zero error
            val indicatorLength =( ((viewPortLength / contentLength) * viewPortLength) - (
                    if (direction == Orientation.Vertical) topPadding + bottomPadding
                    else startPadding + endPadding
                    )).coerceAtLeast(20.dp.toPx())

            val indicatorThicknessPx = indicatorThickness.toPx()

            val scrollOffsetViewPort = viewPortLength * contentOffset / contentLength

            val scrollbarSizeWithoutInsets = if (direction == Orientation.Vertical)
                Size(indicatorThicknessPx, indicatorLength)
            else Size(indicatorLength, indicatorThicknessPx)

            val scrollbarPositionWithoutInsets = if (direction == Orientation.Vertical)
                Offset(
                    x = if (layoutDirection == LayoutDirection.Ltr)
                        viewPortCrossAxisLength - indicatorThicknessPx - endPadding
                    else startPadding,
                    y = scrollOffsetViewPort + topPadding
                )
            else
                Offset(
                    x = if (layoutDirection == LayoutDirection.Ltr)
                        scrollOffsetViewPort + startPadding
                    else viewPortLength - scrollOffsetViewPort - indicatorLength - endPadding,
                    y = viewPortCrossAxisLength - indicatorThicknessPx - bottomPadding
                )

            drawScrollbar(
                indicatorColor = indicatorColor,
                indicatorThicknessPx = indicatorThicknessPx,
                scrollbarPositionWithoutInsets = scrollbarPositionWithoutInsets,
                scrollbarSizeWithoutInsets = scrollbarSizeWithoutInsets,
                scrollbarAlpha = scrollbarAlpha.value
            )
        }
    }
}

/** As above, for a lazy list. See the note on the [ScrollState] overload. */
@Composable
fun Modifier.scrollbar(
    state: LazyListState,
    direction: Orientation,
    indicatorThickness: Dp = 4.dp,
    alpha: Float = if (state.isScrollInProgress) 0.8f else 0f,
    alphaAnimationSpec: AnimationSpec<Float> = tween(
        delayMillis = if (state.isScrollInProgress) 0 else 1500,
        durationMillis = if (state.isScrollInProgress) 150 else 500
    ),
    padding: PaddingValues = PaddingValues(all = 0.dp)
): Modifier {
    // Kept as State and read inside drawWithContent below, not unwrapped with
    // `by` here: `by` would subscribe this composable to a value that changes
    // every animation frame, recomposing the whole scrolling subtree for the
    // life of the fade. Read in the draw lambda, only the draw phase
    // invalidates.
    val scrollbarAlpha = animateFloatAsState(
        targetValue = alpha,
        animationSpec = alphaAnimationSpec
    )

    val indicatorColor = AppTheme.colors.surfaceDisabled

    return drawWithContent {
        drawContent()

        val showScrollBar = state.isScrollInProgress || scrollbarAlpha.value > 0.0f
        if (showScrollBar) {
            val layoutInfo = state.layoutInfo
            val totalItemCount = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            val averageVisibleItemSize = visibleItems.map { it.size }.average().toFloat()

            val totalContentHeight = totalItemCount * averageVisibleItemSize
            val viewportHeight = size.height

            val scrolledDistance =
                (state.firstVisibleItemIndex * averageVisibleItemSize) + state.firstVisibleItemScrollOffset
            val scrolledPercentage = scrolledDistance / (totalContentHeight - viewportHeight)

            val scrollbarHeight = (viewportHeight / totalContentHeight * viewportHeight).coerceAtLeast(20.dp.toPx())
            val scrollbarYPosition = scrolledPercentage * (viewportHeight - scrollbarHeight)

            val indicatorThicknessPx = indicatorThickness.toPx()
            val (topPadding, bottomPadding, startPadding, endPadding) = listOf(
                padding.calculateTopPadding().toPx(), padding.calculateBottomPadding().toPx(),
                padding.calculateStartPadding(layoutDirection).toPx(),
                padding.calculateEndPadding(layoutDirection).toPx()
            )

            val scrollbarPositionWithoutInsets = if (direction == Orientation.Vertical)
                Offset(
                    x = if (layoutDirection == LayoutDirection.Ltr) size.width - indicatorThicknessPx - endPadding else startPadding,
                    y = scrollbarYPosition + topPadding
                )
            else
                Offset(
                    x = scrollbarYPosition + startPadding,
                    y = if (layoutDirection == LayoutDirection.Ltr) size.height - indicatorThicknessPx - bottomPadding else topPadding
                )

            val scrollbarSizeWithoutInsets = if (direction == Orientation.Vertical)
                Size(indicatorThicknessPx, scrollbarHeight)
            else
                Size(scrollbarHeight, indicatorThicknessPx)

            // Draw the scrollbar

            drawScrollbar(
                indicatorColor = indicatorColor,
                indicatorThicknessPx = indicatorThicknessPx,
                scrollbarPositionWithoutInsets = scrollbarPositionWithoutInsets,
                scrollbarSizeWithoutInsets = scrollbarSizeWithoutInsets,
                scrollbarAlpha = scrollbarAlpha.value
            )
        }
    }
}

@Composable
fun Modifier.verticalScrollWithBar(
    scrollState: ScrollState
): Modifier = this.verticalScroll(scrollState).scrollbar(scrollState, Orientation.Vertical)

@Composable
fun Modifier.verticalScrollWithBar(
    lazyListState: LazyListState
): Modifier = this.scrollbar(lazyListState, Orientation.Vertical)

@Composable
fun Modifier.horizontalScrollWithBar(
    scrollState: ScrollState
): Modifier = this.horizontalScroll(scrollState).scrollbar(scrollState, Orientation.Horizontal)

@Composable
fun Modifier.horizontalScrollWithBar(
    lazyListState: LazyListState
): Modifier = this.scrollbar(lazyListState, Orientation.Horizontal)

private fun ContentDrawScope.drawScrollbar(
    indicatorColor: ColorResource,
    indicatorThicknessPx: Float,
    scrollbarPositionWithoutInsets: Offset,
    scrollbarSizeWithoutInsets: Size,
    scrollbarAlpha: Float
) {
    drawRoundRect(
        color = indicatorColor.color,
        cornerRadius = CornerRadius(
            x = indicatorThicknessPx / 2, y = indicatorThicknessPx / 2
        ),
        topLeft = scrollbarPositionWithoutInsets,
        size = scrollbarSizeWithoutInsets,
        alpha = scrollbarAlpha
    )
}
