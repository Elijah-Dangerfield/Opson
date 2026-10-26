package com.opson.libraries.ui.components.dialog.bottomsheet

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * For a sheet that opens part of the way ([BottomSheetDefaults.PartialHeightFraction]): an
 * upward drag over this content calls [onExpand] once instead of scrolling or dragging, so the
 * sheet can grow to full height. A downward drag is left alone, so the sheet's own drag still
 * closes it. Off when [enabled] is false, which is what a full-height sheet passes.
 *
 * Two paths, because a sheet has two kinds of content: a list consumes its drags through nested
 * scroll, and anything else (the handle, the title) sees raw pointer moves.
 */
fun Modifier.expandOnDragUp(enabled: Boolean, onExpand: () -> Unit): Modifier = composed {
    val expand by rememberUpdatedState(onExpand)
    val active by rememberUpdatedState(enabled)
    val threshold = with(LocalDensity.current) { ExpandThreshold.toPx() }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!active || source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
                expand()
                // The first upward drag grows the sheet; the list starts scrolling once it is full.
                return available
            }
        }
    }
    nestedScroll(connection).pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var travelled = 0f
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (change.isConsumed) continue
                travelled += change.positionChange().y
                if (travelled < -threshold) {
                    change.consume()
                    expand()
                    break
                }
            }
        }
    }
}

/** Far enough that a tap with a wobble is still a tap. */
private val ExpandThreshold = 24.dp

/**
 * [BottomSheet]'s `heightFraction`, read in the layout phase so a sheet that grows (the logger
 * from [BottomSheetDefaults.PartialHeightFraction] to full) can animate without recomposing its
 * content on every frame. Pass it as the sheet's `modifier` with `heightFraction` left null.
 */
fun Modifier.sheetHeightFraction(fraction: () -> Float): Modifier = layout { measurable, constraints ->
    val height = if (constraints.hasBoundedHeight) (constraints.maxHeight * fraction().coerceIn(0f, 1f)).toInt() else constraints.minHeight
    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}
