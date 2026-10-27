package com.opson.features.foods

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * Create a meal (`foods.md`). From the Meal sheet's "Save as a meal" it arrives
 * pre-filled with [fromSlot]'s items on [fromDayIso] (`LocalDate.toString()`).
 * From Today's selected foods it arrives with [entryIds] instead: just those
 * entries on [fromDayIso], in the day's order, and [fromSlot] only when they all
 * share that meal (it names the meal and picks "Usually at").
 * A `screen<>` with a typeMap for the nullable [MealSlot]; Foods registers it.
 */
@Serializable
data class CreateMealRoute(
    val fromDayIso: String? = null,
    val fromSlot: MealSlot? = null,
    val entryIds: List<String>? = null,
) : Route()
