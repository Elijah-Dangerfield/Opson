package com.opson.libraries.ui.system

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScrollModifierNode
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Drops focus, and with it the keyboard, when a tap lands on nothing that wants it: empty space,
 * a label, a card. Put it once at the root of each window (the app root, a bottom sheet), so every
 * field below gets the platform's "tap outside to close the keyboard" for free.
 *
 * It only watches. A tap that something below handles (a button, a row, another field, the
 * focused field placing its cursor) consumes its up and is left alone, as is a press that turns
 * into a scroll or a drag, so it never steals a click or a scroll. That is also why it reads the
 * Main pass: by the time the event comes back up to the root, every child has had its say.
 *
 * Clearing focus rather than hiding the keyboard controller is deliberate: on iOS only dropping
 * focus ends the text input session, and the keyboard with it.
 */
fun Modifier.clearFocusOnTapOutside(): Modifier = this then ClearFocusOnTapElement

private data object ClearFocusOnTapElement : ModifierNodeElement<ClearFocusOnTapNode>() {
    override fun create() = ClearFocusOnTapNode()
    override fun update(node: ClearFocusOnTapNode) = Unit
    override fun InspectorInfo.inspectableProperties() {
        name = "clearFocusOnTapOutside"
    }
}

private class ClearFocusOnTapNode : DelegatingNode(), CompositionLocalConsumerModifierNode {
    init {
        delegate(
            SuspendingPointerInputModifierNode {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // Null when a child consumed the gesture or it left the bounds: not a tap on nothing.
                    if (waitForUpOrCancellation() != null) currentValueOf(LocalFocusManager).clearFocus()
                }
            },
        )
    }
}

/**
 * Drops focus when the person drags the content underneath to scroll it, the way an iOS search
 * results list puts the keyboard away. For lists of results under a search field, where the
 * keyboard covers the results being scrolled to; a form scrolled while typing keeps its keyboard.
 *
 * Only a drag counts: a list that scrolls itself (to a new result, an animated jump) does not
 * close the keyboard on someone who is still typing.
 */
fun Modifier.clearFocusOnScroll(): Modifier = this then ClearFocusOnScrollElement

private data object ClearFocusOnScrollElement : ModifierNodeElement<ClearFocusOnScrollNode>() {
    override fun create() = ClearFocusOnScrollNode()
    override fun update(node: ClearFocusOnScrollNode) = Unit
    override fun InspectorInfo.inspectableProperties() {
        name = "clearFocusOnScroll"
    }
}

private class ClearFocusOnScrollNode : DelegatingNode(), CompositionLocalConsumerModifierNode {
    private val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && available != Offset.Zero) {
                currentValueOf(LocalFocusManager).clearFocus()
            }
            return Offset.Zero
        }
    }

    init {
        delegate(nestedScrollModifierNode(connection, dispatcher = null))
    }
}
