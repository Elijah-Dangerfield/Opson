package com.opson.features.foods

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * Edit a copy (design sync 4, 13.3e): a logged food's details as a new food of the person's own,
 * "<name> (mine)", from a food's ⋮ on Today. [dayIso] and [entryId] find the entry it starts
 * from, which can switch to the copy. The food it came from is never changed. A `screen<>`.
 */
@Serializable
data class EditCopyRoute(val dayIso: String, val entryId: String) : Route()
