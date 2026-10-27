package com.opson.features.today

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * A meal's Note sheet (`today-sheets.md`), from the Meal sheet, the Notes list
 * and the logger's Note. A `bottomSheet<>` with a typeMap for [MealSlot];
 * Today registers it.
 */
@Serializable
data class MealNoteRoute(val dayIso: String, val slot: MealSlot) : Route()
