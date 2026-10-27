package com.opson.libraries.diary

import kotlinx.coroutines.flow.StateFlow

/**
 * Foods the logger picked for a meal someone is putting together, for the
 * caller that asked ([requestId]). [items] are what the picks would have
 * logged; nothing was written to the diary.
 */
data class PickedForMeal(val requestId: String, val items: List<LogDraft>)

/**
 * The hand-off from search, opened in pick-for-this-meal mode, back
 * to Create a meal. Search calls [picked] and closes; Create a meal, when
 * [latest] carries its own request id, adds the items and calls [consumed].
 *
 * A state rather than an event stream for the reason [RecentLogEvents] gives:
 * a collector that is not listening at the moment of the write still gets it.
 */
interface MealPicks {
    val latest: StateFlow<PickedForMeal?>

    fun picked(picks: PickedForMeal)

    /** Clears [latest] if it is still [picks]; a newer hand-off is left alone. */
    fun consumed(picks: PickedForMeal)
}
