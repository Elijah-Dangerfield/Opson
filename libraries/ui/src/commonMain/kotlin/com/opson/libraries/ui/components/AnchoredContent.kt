package com.opson.libraries.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.roundToIntRect
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.animatePlacement
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.Dimension
import org.jetbrains.compose.ui.tooling.preview.Preview

enum class AnchorSide { Above, Below }

/**
 * Places [content] against [anchor] — a rect in this layout's own coordinate space — and lets
 * [animatePlacement] carry it there when the anchor moves.
 *
 * The layout is one ordinary measure-then-place pass: the content is measured first, so its size is
 * known by the time [placeAgainstAnchor] runs, and nothing about the position ever reaches
 * composition. Animating from inside a positioning component instead is what forces the placement
 * maths up into composition, where the size is not known yet.
 *
 * The component fills the constraints it is given, so the anchor rect and the placement share one
 * coordinate space. Put it in the `Box` that also draws the anchored thing and hand it the anchor's
 * `boundsInParent()`.
 */
@Composable
fun AnchoredContent(
    anchor: IntRect,
    modifier: Modifier = Modifier,
    preferredSide: AnchorSide = AnchorSide.Below,
    gap: Dp = Dimension.D300,
    margin: Dp = Dimension.D300,
    animateMovement: Boolean = true,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            if (animateMovement) {
                Box(modifier = Modifier.animatePlacement()) { content() }
            } else {
                content()
            }
        },
    ) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val height =
            if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
        val placeable = measurables.first().measure(
            Constraints(maxWidth = width, maxHeight = height),
        )
        val offset = placeAgainstAnchor(
            anchor = anchor,
            contentSize = IntSize(placeable.width, placeable.height),
            containerSize = IntSize(width, height),
            preferredSide = preferredSide,
            gap = gap.roundToPx(),
            margin = margin.roundToPx(),
        )
        layout(width, height) {
            placeable.place(offset)
        }
    }
}

/**
 * Where content of [contentSize] goes when it hangs off [anchor] inside a container of
 * [containerSize].
 *
 * Horizontally the content is centred on the anchor. Vertically it takes [preferredSide], falling
 * back to the other side when the preferred one has no room, and everything is then kept [margin]
 * inside the container. When neither side fits, the clamp wins and the content overlaps the anchor
 * rather than leaving the screen — a tooltip half off the edge is worse than one sitting on what it
 * describes.
 *
 * Pure on purpose: this is the part with the edge cases, and it is worth testing without a
 * composition.
 */
fun placeAgainstAnchor(
    anchor: IntRect,
    contentSize: IntSize,
    containerSize: IntSize,
    preferredSide: AnchorSide,
    gap: Int,
    margin: Int,
): IntOffset {
    val above = anchor.top - gap - contentSize.height
    val below = anchor.bottom + gap
    val fitsAbove = above >= margin
    val fitsBelow = below + contentSize.height <= containerSize.height - margin

    val y = when (preferredSide) {
        AnchorSide.Above -> if (fitsAbove || !fitsBelow) above else below
        AnchorSide.Below -> if (fitsBelow || !fitsAbove) below else above
    }
    val x = anchor.left + (anchor.width - contentSize.width) / 2

    return IntOffset(
        x = x.clampInside(containerSize.width, contentSize.width, margin),
        y = y.clampInside(containerSize.height, contentSize.height, margin),
    )
}

private fun Int.clampInside(container: Int, content: Int, margin: Int): Int {
    val max = container - content - margin
    return if (max < margin) margin else coerceIn(margin, max)
}

@Preview(widthDp = 240, heightDp = 200)
@Composable
private fun PreviewAnchoredContent() {
    PreviewContent {
        Box(modifier = Modifier.size(width = Dimension.D1900 * 2, height = Dimension.D1900 * 2)) {
            var anchor by remember { mutableStateOf(IntRect.Zero) }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(Dimension.D1900)
                    .background(AppTheme.colors.surfacePrimary.color)
                    .onPlaced { anchor = it.boundsInParent().roundToIntRect() },
            )

            AnchoredContent(anchor = anchor) {
                Text(text = "Anchored")
            }
        }
    }
}
