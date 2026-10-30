package com.opson.admin

/** What a key does on the Review tab. */
internal enum class ReviewCommand {
    Next,
    Previous,
    Open,
    Approve,
    FocusReject,
    Save,
    ToggleText,
    Close,
    Legend,
}

/**
 * The Review tab's keyboard, as a pure mapping so it can be tested without a browser. The view
 * only asks for a command when focus is outside a text field and no confirm sheet is open, so a
 * reviewer typing a reason never approves anything by accident.
 */
internal object ReviewKeys {

    /** The legend the `?` key shows, in the order a weekly pass uses them. */
    val LEGEND: List<Pair<String, String>> = listOf(
        "j or ↓" to "next submission",
        "k or ↑" to "previous submission",
        "o or Enter" to "open the first one",
        "a" to "approve (asks first)",
        "r" to "type a reject reason",
        "s" to "save corrections",
        "t" to "show or hide the OCR text",
        "Esc" to "close the submission",
        "?" to "show or hide this list",
    )

    /**
     * [key] is `KeyboardEvent.key`. A key held with Ctrl, Cmd or Alt belongs to the browser.
     * Enter and o only open something when nothing is open, and Enter on a focused button or link
     * ([onControl]) keeps doing what that control does.
     */
    fun command(key: String, modified: Boolean, hasSelection: Boolean, onControl: Boolean = false): ReviewCommand? {
        if (modified) return null
        return when (key) {
            "j", "ArrowDown" -> ReviewCommand.Next
            "k", "ArrowUp" -> ReviewCommand.Previous
            "o" -> if (hasSelection) null else ReviewCommand.Open
            "Enter" -> if (hasSelection || onControl) null else ReviewCommand.Open
            "a" -> if (hasSelection) ReviewCommand.Approve else null
            "r" -> if (hasSelection) ReviewCommand.FocusReject else null
            "s" -> if (hasSelection) ReviewCommand.Save else null
            "t" -> if (hasSelection) ReviewCommand.ToggleText else null
            "Escape" -> if (hasSelection) ReviewCommand.Close else null
            "?" -> ReviewCommand.Legend
            else -> null
        }
    }

    /**
     * The submission [command] moves to in [ids], the queue as shown. Next from nothing opens the
     * first; past either end stays put.
     */
    fun move(ids: List<String>, current: String?, command: ReviewCommand): String? {
        if (ids.isEmpty()) return current
        val index = current?.let(ids::indexOf) ?: -1
        return when (command) {
            ReviewCommand.Next -> if (index < 0) ids.first() else ids[minOf(index + 1, ids.lastIndex)]
            ReviewCommand.Previous -> if (index < 0) ids.first() else ids[maxOf(index - 1, 0)]
            ReviewCommand.Open -> ids.first()
            else -> current
        }
    }

    /**
     * What opens after [decided] leaves the queue: the one that was below it, else the one above,
     * else nothing. [ids] is the queue before the decision.
     */
    fun afterDecision(ids: List<String>, decided: String): String? {
        val index = ids.indexOf(decided)
        if (index < 0) return ids.firstOrNull()
        return ids.getOrNull(index + 1) ?: ids.getOrNull(index - 1)
    }

    /** Whether a key landed in something that takes typing. */
    fun isTextTarget(tagName: String?, editable: Boolean): Boolean =
        editable || tagName?.uppercase() in setOf("INPUT", "TEXTAREA", "SELECT")
}
