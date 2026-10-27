package com.opson.features.today.impl

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/*
 * The sheets only Today opens (`today-sheets.md`). Days travel as
 * `LocalDate.toString()`; a MealSlot argument needs its typeMap at the
 * registration in TodayFeatureEntryPoint.
 */

/** The month calendar that drops from the day's title, opened on [selectedIso]'s month. A `dialog<>`. */
@Serializable
data class CalendarRoute(val selectedIso: String) : Route()

/** The + on a meal: add food, a note, copy, ate no details, save as a meal. */
@Serializable
data class MealSheetRoute(val dayIso: String, val slot: MealSlot) : Route()

/** Add a glass, a custom amount, remove a glass, water settings. */
@Serializable
data class WaterSheetRoute(val dayIso: String) : Route()

/** One logged item, to remove a wrong entry. The day is how the sheet finds it. */
@Serializable
data class ItemSheetRoute(val dayIso: String, val entryId: String) : Route()

/** The day's note, from the Add menu's Note and the Day menu's "Add a note for the day". */
@Serializable
data class DayNoteRoute(val dayIso: String) : Route()

/** "Edit Today's cards" from the ⋮ menu: meal groups and the water card. */
@Serializable
class TodayCardsRoute : Route()

/** Day close's "Why not cut back?" on the big-day card: a short reason, nothing to do. */
@Serializable
class WhyNotCutBackRoute : Route()
