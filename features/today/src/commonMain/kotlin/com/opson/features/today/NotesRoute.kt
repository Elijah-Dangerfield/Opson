package com.opson.features.today

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * Every meal's Note, newest first: the day, the meal, the hunger level and the words. A
 * `screen<>` Today registers; Today's ⋮ menu opens it, and Trends' Notes card through
 * `NotesListLink` with [fromTrends], so the back label names where it came from.
 */
@Serializable
data class NotesRoute(val fromTrends: Boolean = false) : Route()
