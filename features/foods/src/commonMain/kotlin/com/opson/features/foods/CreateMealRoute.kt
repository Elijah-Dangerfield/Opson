package com.opson.features.foods

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * Create a meal (`foods.md`). From the Meal sheet's "Save as a meal" it arrives
 * pre-filled with [fromSlot]'s items on [fromDayIso] (`LocalDate.toString()`).
 * A `screen<>` with a typeMap for the nullable [MealSlot]; Foods registers it.
 */
@Serializable
data class CreateMealRoute(
    val fromDayIso: String? = null,
    val fromSlot: MealSlot? = null,
) : Route()
