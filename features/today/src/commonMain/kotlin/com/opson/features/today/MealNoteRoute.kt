package com.opson.features.today

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * The meal note sheet (`today-sheets.md`), from the Meal sheet and the
 * logger's "Add a note" chip. A `bottomSheet<>` with a typeMap for [MealSlot];
 * Today registers it.
 */
@Serializable
data class MealNoteRoute(val dayIso: String, val slot: MealSlot) : Route()
