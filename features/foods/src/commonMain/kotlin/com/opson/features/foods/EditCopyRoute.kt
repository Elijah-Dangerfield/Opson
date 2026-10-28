package com.opson.features.foods

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * Edit a copy: a food as a new food of the person's own, "<name> (mine)", with every number on
 * its label, its serving sizes and their weights. The food it came from is never changed.
 *
 * It starts from one of two places. From a food's ⋮ on Today, [entryId] on [dayIso] is the
 * logged entry, which can switch to the copy. From the food detail's ⋮, [foodId] is the catalog
 * food or, with [isCustom], the person's own; once saved, the copy's detail opens to log on
 * [dayIso] in [slot]. A `screen<>` with a typeMap for the nullable [MealSlot]; Foods registers it.
 */
@Serializable
data class EditCopyRoute(
    val dayIso: String,
    val entryId: String? = null,
    val foodId: String? = null,
    val isCustom: Boolean = false,
    val slot: MealSlot? = null,
) : Route()
