package com.opson.libraries.ui.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * A field's text while its value round-trips through a ViewModel.
 *
 * `SEAViewModel` handles actions on a coroutine, so the state carrying what
 * was typed comes back a frame or more later. Handing that late value straight
 * to the text field rewinds it and eats the keystrokes typed in between, which
 * is easy to hit on iOS. Here the field shows what the user typed, values it
 * sent are ignored when they come back, and any other value (Clear, a prefill,
 * a ViewModel that trims) replaces the text.
 */
internal class TypedText(initial: String) {
    var text by mutableStateOf(initial)
        private set

    var send: (String) -> Unit = {}

    // Sent and not yet seen coming back, oldest first.
    private val pending = ArrayDeque<String>()

    fun onType(new: String) {
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

    private companion object {
        const val MAX_PENDING = 64
    }
}

@Composable
internal fun rememberTypedText(value: String, onValueChange: (String) -> Unit): TypedText {
    val typed = remember { TypedText(value) }
    typed.send = onValueChange
    LaunchedEffect(value) { typed.onValue(value) }
    return typed
}
