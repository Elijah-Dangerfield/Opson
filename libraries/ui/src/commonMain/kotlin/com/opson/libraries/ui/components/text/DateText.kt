package com.opson.libraries.ui.components.text

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What a [DateField] holds: one `TextFieldState` of digits ([DateEntry]) that the three boxes are
 * drawn from, kept in step with the screen's date.
 *
 * One field, so iOS keeps one text input session for the whole date. Three fields handing focus to
 * each other on the next frame lost what was typed during the hand-off: each session swap takes
 * down UIKit's text input view and puts up a new one, and keys that arrive in between reach
 * neither field. Paste, autofill and delete need nothing special either: they are edits to one
 * string, and [input] reads every edit through [DateEntry.edit] before the next one can arrive.
 *
 * A date from the screen with a gap in it (a year known, the month and day not) can't be typed
 * as one run of digits. The run fills the boxes before the gap, and [rest] keeps what comes after
 * it on screen until a digit lands in that box.
 */
internal class DateText(private val order: DateOrder, initial: DateParts) {

    val state = TextFieldState(DateEntry.typed(initial, order))

    /** Boxes past the typed digits that still show what the screen gave them. */
    private var rest by mutableStateOf(restOf(initial))

    var onChange: (DateParts) -> Unit = {}

    val input = InputTransformation { edited() }

    /** The date in the boxes. Reads the field's state, so it is observed. */
    val parts: DateParts get() = merged(state.text.toString(), rest)

    /** The box the next digit goes into, null once the date is in. */
    val current: DatePart? get() = DateEntry.current(state.text.toString(), order)

    // Dates sent through onChange and not yet seen coming back, oldest first.
    private val sent = ArrayDeque<DateParts>()
    private var heard = initial

    /**
     * The screen's date came back as [value]. One of this field's own edits is let through, as are
     * the ones before it that a conflating flow skipped; anything else (a prefill, a reset)
     * replaces what the field holds.
     */
    fun heard(value: DateParts) {
        if (value == heard) return
        heard = value
        val echo = sent.indexOf(value)
        if (echo >= 0) {
            repeat(echo + 1) { sent.removeFirst() }
            return
        }
        sent.clear()
        if (value == parts) return
        rest = restOf(value)
        state.setTextAndPlaceCursorAtEnd(DateEntry.typed(value, order))
    }

    private fun TextFieldBuffer.edited() {
        val was = originalText.toString()
        val now = asCharSequence().toString()
        val typed = DateEntry.edit(was, now, order)
        if (typed != now) replace(0, length, typed)
        // Digits only ever go on at the end, and delete takes the last one, wherever a tap put the cursor.
        placeCursorAtEnd()
        if (typed == was) return
        val before = merged(was, rest)
        val reached = DateEntry.boxes(typed, order)
        rest = DatePart.entries.fold(rest) { left, part -> if (reached[part].isEmpty()) left else left.with(part, "") }
        val after = merged(typed, rest)
        if (after == before) return
        sent.addLast(after)
        if (sent.size > MAX_SENT) sent.removeFirst()
        onChange(after)
    }

    private fun merged(typed: String, rest: DateParts): DateParts {
        val boxes = DateEntry.boxes(typed, order)
        return DatePart.entries.fold(boxes) { parts, part -> if (parts[part].isEmpty()) parts.with(part, rest[part]) else parts }
    }

    /** What of [parts] the typed digits can't hold: every box after the first empty one. */
    private fun restOf(parts: DateParts): DateParts {
        val reached = DateEntry.boxes(DateEntry.typed(parts, order), order)
        return DatePart.entries.fold(DateParts()) { left, part -> if (reached[part].isEmpty()) left.with(part, parts[part]) else left }
    }

    private companion object {
        const val MAX_SENT = 64
    }
}
