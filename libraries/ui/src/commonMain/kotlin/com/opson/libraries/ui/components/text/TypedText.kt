package com.opson.libraries.ui.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * A field's text while its value round-trips through a ViewModel.
 *
 * `SEAViewModel` handles actions on a coroutine, so the state carrying what
 * was typed comes back a frame or more later. Handing that late value straight
 * to the text field rewinds it and eats the keystrokes typed in between, which
 * is easy to hit on iOS. Here the field shows what the user typed, values it
 * sent are ignored when they come back, and any other value (Clear, a prefill,
 * a ViewModel that trims) replaces the text.
 *
 * A ViewModel that refuses an edit (a length cap, a dropped character) keeps
 * its old value, so nothing comes back at all. Once typing pauses, [settle]
 * puts the field back on the ViewModel's value, so the field never shows text
 * the ViewModel does not hold.
 */
internal class TypedText(initial: String) {
    var text by mutableStateOf(initial)
        private set

    var send: (String) -> Unit = {}

    // Sent and not yet seen coming back, oldest first.
    private val pending = ArrayDeque<String>()

    fun onType(new: String) {
        // A field-level filter can turn a keystroke into no change at all.
        if (new == text) return
        text = new
        pending.addLast(new)
        if (pending.size > MAX_PENDING) pending.removeFirst()
        send(new)
    }

    fun onValue(value: String) {
        val echo = pending.indexOf(value)
        if (echo >= 0) {
            // A StateFlow can skip intermediate values, so older echoes are done too.
            repeat(echo + 1) { pending.removeFirst() }
        } else if (value != text) {
            pending.clear()
            text = value
        }
    }

    /** Typing has paused with the field showing something other than [value]: the edit was refused. */
    fun settle(value: String) {
        pending.clear()
        text = value
    }

    private companion object {
        const val MAX_PENDING = 64
    }
}

@Composable
internal fun rememberTypedText(value: String, onValueChange: (String) -> Unit): TypedText {
    val typed = remember { TypedText(value) }
    typed.send = onValueChange
    LaunchedEffect(value) { typed.onValue(value) }
    LaunchedEffect(typed.text, value) {
        if (typed.text != value) {
            delay(RefusedEditWait)
            typed.settle(value)
        }
    }
    return typed
}

/**
 * How long after the last keystroke an unechoed edit counts as refused. Well past the frame or
 * two an echo takes, so it never rewinds a field that is merely slow to hear back.
 */
private val RefusedEditWait = 300.milliseconds
