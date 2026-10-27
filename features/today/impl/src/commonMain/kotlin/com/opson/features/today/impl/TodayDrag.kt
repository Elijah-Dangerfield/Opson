package com.opson.features.today.impl

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.DragHandle
import com.opson.libraries.ui.system.tick

/*
 * Dragging picked foods to another meal (design sync 4, 13.3c). A row's handle only starts the
 * drag; the list's box follows the finger from then on, because the row the drag began on can
 * scroll out of the list (and out of composition) while the list scrolls itself near an edge.
 * Which meal is under the finger is read off the list's own layout, so it holds while scrolling.
 */

/** One drag at a time: whether it is on, where the finger is in the list's box, and the meal under it. */
@Stable
internal class FoodDrag {
    var active by mutableStateOf(false)
        private set

    /** The finger's height in the list's box. Read only in layout or draw (the carried card's offset). */
    var y by mutableFloatStateOf(0f)
        private set

    /** The meal the foods would land in now; null over anything else or over the meal they already all sit in. */
    var target by mutableStateOf<MealSlot?>(null)
        private set

    fun begin() {
        active = true
    }

    /** Held mid-drag over [over] at [y], for previews and the leak test, which draw a moment of it. */
    fun showing(y: Float, over: MealSlot) {
        active = true
        this.y = y
        target = over
    }

    fun follow(y: Float) {
        this.y = y
    }

    /** Re-reads the meal under the finger; true when it changed to another meal. */
    fun retarget(info: LazyListLayoutInfo, targets: Map<Any, MealSlot>, from: Set<MealSlot>): Boolean {
        val next = targetAt(y, info, targets)?.takeUnless { from.size == 1 && it in from }
        if (next == target) return false
        target = next
        return next != null
    }

    /** Ends the drag and says where the foods landed, if anywhere. */
    fun end(): MealSlot? {
        val landed = target
        active = false
        target = null
        return landed
    }

    companion object {
        /**
         * The meal row under [y], from the list's laid-out items. An item's offset counts from
         * the content's start, past the top padding, which [LazyListLayoutInfo.viewportStartOffset]
         * carries as a negative number.
         */
        fun targetAt(y: Float, info: LazyListLayoutInfo, targets: Map<Any, MealSlot>): MealSlot? =
            info.visibleItemsInfo.firstOrNull { item ->
                val top = item.offset - info.viewportStartOffset
                item.key in targets && y >= top && y < top + item.size
            }?.let { targets[it.key] }
    }
}

@Composable
internal fun rememberFoodDrag(): FoodDrag = remember { FoodDrag() }

/** A picked row's grip: pulling it past the touch slop starts the drag and picks the row. */
@Composable
internal fun FoodDragHandle(drag: FoodDrag, entryId: String, onAction: (TodayAction) -> Unit) {
    val act by rememberUpdatedState(onAction)
    DragHandle(
        Modifier.pointerInput(entryId) {
            detectDragGestures(
                onDragStart = {
                    act(TodayAction.DragStarted(entryId))
                    drag.begin()
                },
                // The box above follows the finger; consuming here keeps the list from scrolling under it.
                onDrag = { change, _ -> change.consume() },
            )
        },
    )
}

/**
 * The list box's half of the drag: follows the finger once a handle has started it, and drops on
 * the finger lifting. Hangs on the box round the list, which outlives any one row.
 */
internal fun Modifier.followsFoodDrag(drag: FoodDrag, onDrop: (MealSlot) -> Unit): Modifier =
    pointerInput(drag) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: break
                if (drag.active) {
                    drag.follow(change.position.y)
                    change.consume()
                }
            } while (event.changes.any { it.pressed })
            if (drag.active) drag.end()?.let(onDrop)
        }
    }

/**
 * While a drag is on: scrolls the list when the finger is near its top or bottom edge, faster the
 * closer it is, and re-reads the meal under the finger each frame. Plays a tick when the target
 * changes to a new meal.
 */
@Composable
internal fun FoodDragEffects(
    drag: FoodDrag,
    listState: LazyListState,
    targets: Map<Any, MealSlot>,
    from: Set<MealSlot>,
    haptics: HapticFeedback = LocalHapticFeedback.current,
) {
    val edge = with(LocalDensity.current) { AUTO_SCROLL_EDGE.toPx() }
    val maxStep = with(LocalDensity.current) { AUTO_SCROLL_MAX_STEP.toPx() }
    val currentTargets by rememberUpdatedState(targets)
    val currentFrom by rememberUpdatedState(from)
    // A preview holds a drag still; a frame loop there would never let the capture settle.
    val still = LocalInspectionMode.current
    LaunchedEffect(drag.active) {
        if (!drag.active || still) return@LaunchedEffect
        while (drag.active) {
            withFrameNanos { }
            val height = listState.layoutInfo.viewportSize.height.toFloat()
            val step = when {
                drag.y < edge -> -maxStep * (1f - drag.y / edge)
                drag.y > height - edge -> maxStep * (1f - (height - drag.y) / edge)
                else -> 0f
            }
            if (step != 0f) listState.scrollBy(step.coerceIn(-maxStep, maxStep))
            if (drag.retarget(listState.layoutInfo, currentTargets, currentFrom)) haptics.tick()
        }
    }
}

private val AUTO_SCROLL_EDGE = 72.dp
private val AUTO_SCROLL_MAX_STEP = 14.dp
