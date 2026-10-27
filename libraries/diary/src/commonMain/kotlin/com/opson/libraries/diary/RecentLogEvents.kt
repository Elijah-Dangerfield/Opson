package com.opson.libraries.diary

import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Items the logger just wrote to one meal: a food from search or the + sheet, Describe's
 * lines, a saved meal, yesterday's copy, or a quick number. [itemIds] are exactly the rows
 * written, so undoing removes those and nothing the user logged before.
 */
data class LoggedToMeal(
    val day: LocalDate,
    val slot: MealSlot,
    val itemIds: List<String>,
    val at: Instant,
) {
    /** Whether the Undo offer for this is still open at [now]: the design gives it six seconds. */
    fun undoOpenAt(now: Instant): Boolean = now - at < UNDO_WINDOW

    companion object {
        val UNDO_WINDOW = 6.seconds
    }
}

/**
 * The hand-off from the logger to Today after something is added. The logger
 * closes and calls [logged]; Today, when [latest] is for the day it shows and
 * still inside [LoggedToMeal.UNDO_WINDOW], scrolls to the meal, outlines it,
 * shows "Added N foods to {meal}" with Undo, and calls [consumed] so it shows
 * once. Undo is [undo].
 *
 * A state rather than an event stream on purpose: Today may not be collecting
 * at the moment the logger writes (the logger can be opened from Foods), and a
 * late collector still needs to see what happened and how long ago.
 */
interface RecentLogEvents {
    val latest: StateFlow<LoggedToMeal?>

    fun logged(event: LoggedToMeal)

    /** Clears [latest] if it is still [event]; a newer add is left alone. */
    fun consumed(event: LoggedToMeal)

    /** Removes exactly [event]'s items, then [consumed]. */
    suspend fun undo(event: LoggedToMeal)
}
