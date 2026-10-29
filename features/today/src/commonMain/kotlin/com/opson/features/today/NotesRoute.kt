package com.opson.features.today

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * Every note, newest first: each day's own note (the one Close the day and the Note sheet both
 * write) and each meal's Note, under the day and labelled by where it was written. A `screen<>`
 * Today registers, with a typeMap for [NotesOrigin]; Today's ⋮ menu opens it, Trends' Notes card
 * through `NotesListLink`, and More's Journal card, and [from] names the back label.
 */
@Serializable
data class NotesRoute(val from: NotesOrigin = NotesOrigin.Today) : Route()

/** Where the Notes list was opened from, for its back label. */
@Serializable
enum class NotesOrigin { Today, Trends, More }
