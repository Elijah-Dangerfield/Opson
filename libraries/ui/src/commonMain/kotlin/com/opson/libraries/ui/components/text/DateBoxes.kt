package com.opson.libraries.ui.components.text

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.text.TextRange

/**
 * The three boxes of a [DateField] and the date they hold, kept in step on every keystroke.
 *
 * Each box is a `TextFieldState` whose [input] runs [DateEntry] inside the edit itself, against
 * [parts] as the last edit left it, and writes every box the edit touches before the next
 * keystroke can arrive. Nothing waits for a recomposition or for the screen's state to come back.
 * That is the fix for digits lost while typing quickly: the boxes used to work each edit out from
 * the date the screen last drew, so a digit typed in Day before Month's edit had come back from
 * the view model wrote Month's old value back over it, and the box showing it followed.
 *
 * Every box after the first starts with an invisible [LEAD] that is never shown ([output]) and
 * that the cursor cannot sit in front of. A delete from the keyboard in an empty box removes it,
 * which makes that delete an edit the box hears: an empty text field reports no change for a
 * delete, and the iOS keyboard sends no key event for one. A hardware Backspace deletes in what is
 * shown, so it never reaches the lead; [deleteAtStart] takes that one from the key event instead.
 */
internal class DateBoxes(private val order: DateOrder, initial: DateParts) {

    /** The date as typed, ahead of the screen's state, which hears of each edit through [onChange]. */
    var parts: DateParts = initial
        private set

    var onChange: (DateParts) -> Unit = {}

    /** Moves the cursor to a box: the next one once a box is done, the one before on a delete. */
    var moveTo: (DatePart) -> Unit = {}

    private val states: Map<DatePart, TextFieldState> = DatePart.entries.associateWith { TextFieldState(shown(it, initial)) }

    private val inputs: Map<DatePart, InputTransformation> = DatePart.entries.associateWith { part ->
        InputTransformation { edited(part) }
    }

    // Dates sent through onChange and not yet seen coming back, oldest first.
    private val sent = ArrayDeque<DateParts>()
    private var heard = initial

    fun state(part: DatePart): TextFieldState = states.getValue(part)

    fun input(part: DatePart): InputTransformation = inputs.getValue(part)

    fun output(part: DatePart): OutputTransformation? = if (leadOf(part).isEmpty()) null else HideLead

    /** Whether [part] shows nothing, for its placeholder. Reads the box's state, so it is observed. */
    fun isEmpty(part: DatePart): Boolean = state(part).text.length <= leadOf(part).length

    /**
     * A delete with the cursor at the start of [part], from a key event. True when it was taken:
     * the box before loses its last digit and the cursor goes there.
     */
    fun deleteAtStart(part: DatePart): Boolean {
        val selection = state(part).selection
        if (order.before(part) == null || !selection.collapsed || selection.start > leadOf(part).length) return false
        commit(DateEntry.deleteBefore(parts, part, order), from = part, written = null)
        return true
    }

    /**
     * The screen's state came back as [value]. One of this field's own edits is let through, as are
     * the ones before it that a conflating flow skipped; anything else (a prefill, a reset) replaces
     * every box.
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
        parts = value
        DatePart.entries.forEach { state(it).setTextAndPlaceCursorAtEnd(shown(it, value)) }
    }

    private fun TextFieldBuffer.edited(part: DatePart) {
        val lead = leadOf(part)
        val now = asCharSequence().toString()
        val was = originalText.toString()
        if (now == was) {
            // Only the cursor moved, by a tap or a drag: keep it after the lead.
            if (selection.min < lead.length) selection = TextRange(maxOf(selection.start, lead.length), maxOf(selection.end, lead.length))
            return
        }
        val deletedLead = lead.isNotEmpty() && was.startsWith(lead) && now == was.substring(lead.length)
        val edit = if (deletedLead) DateEntry.deleteBefore(parts, part, order) else DateEntry.type(parts, part, now.replace(lead, ""), order)
        val show = shown(part, edit.parts)
        // Only when it differs: a box whose edit stands as typed keeps the keyboard's own change,
        // which is what its undo history records.
        if (show != now) replace(0, length, show)
        placeCursorAtEnd()
        commit(edit, from = part, written = part)
    }

    /** [edit] into [parts], every box it changed but [written] (which the edit is writing itself), and on to the screen. */
    private fun commit(edit: DateEdit, from: DatePart, written: DatePart?) {
        val before = parts
        parts = edit.parts
        DatePart.entries
            .filter { it != written && parts[it] != before[it] }
            .forEach { state(it).setTextAndPlaceCursorAtEnd(shown(it, parts)) }
        if (parts != before) {
            sent.addLast(parts)
            if (sent.size > MAX_SENT) sent.removeFirst()
            onChange(parts)
        }
        edit.focus?.takeIf { it != from }?.let { moveTo(it) }
    }

    private fun leadOf(part: DatePart): String = if (part == order.parts.first()) "" else LEAD

    private fun shown(part: DatePart, parts: DateParts): String = leadOf(part) + parts[part]

    private object HideLead : OutputTransformation {
        override fun TextFieldBuffer.transformOutput() {
            if (length > 0 && charAt(0) == LEAD[0]) delete(0, 1)
        }
    }

    private companion object {
        /** A zero-width space: takes a delete, shows nothing, and is no digit, so no date reads it. */
        const val LEAD = "\u200B"
        const val MAX_SENT = 64
    }
}
