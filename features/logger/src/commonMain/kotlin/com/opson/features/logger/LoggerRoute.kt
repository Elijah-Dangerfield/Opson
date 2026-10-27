package com.opson.features.logger

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * The add sheet, opened by the center + and from a meal's own +: a grid of ways to add, about
 * 60% tall, full height once dragged up (design sync 4, 13.4a and 13.4b). Describe it and Saved
 * meals are panes of it; search, the scanner and the rest open in its place.
 *
 * @param slot the meal to log into; `null` picks from this person's logging times
 * (`MealSlotPicker`), or Uncategorized with meal groups off.
 * @param dayIso the day to log to (`LocalDate.toString()`); `null` is the day
 * Today is showing (`SelectedDay`).
 * @param prePickFoodId a food already chosen elsewhere (the Foods tab, Today), catalog or
 * custom per [prePickIsCustom]: its food detail opens in place of the sheet.
 * @param prePickSavedMealId a saved meal chosen on the Foods tab: the sheet opens on Saved meals.
 * @param start the pane the sheet opens on.
 * @param describeText what Describe it starts reading, for search's "Describe it" on a miss.
 */
@Serializable
data class LoggerRoute(
    val slot: MealSlot? = null,
    val dayIso: String? = null,
    val prePickFoodId: String? = null,
    val prePickIsCustom: Boolean = false,
    val prePickSavedMealId: String? = null,
    val start: LoggerStart = LoggerStart.Actions,
    val describeText: String? = null,
) : Route()

/** Where the add sheet opens. */
@Serializable
enum class LoggerStart { Actions, Describe, Saved }

/**
 * Search, a full screen (design sync 4, 13.4c): + on a result logs it at once and stays here.
 *
 * @param slot the meal to log into; `null` picks from this person's logging times, as the sheet does.
 * @param dayIso the day to log to; `null` is the day Today is showing.
 * @param pickForMeal set by Create a meal: + picks foods for a meal being put together instead of
 * logging them, and "Add to meal" hands them back through `MealPicks` under this request id.
 */
@Serializable
data class FoodSearchRoute(
    val slot: MealSlot? = null,
    val dayIso: String? = null,
    val pickForMeal: String? = null,
) : Route()

/**
 * The exercise sheet (`release.exercise`): log an activity, or edit or delete one.
 *
 * @param entryId the entry to edit; `null` logs a new one.
 * @param dayIso the day a new entry counts on; `null` is the day Today is showing.
 */
@Serializable
data class ExerciseRoute(
    val entryId: String? = null,
    val dayIso: String? = null,
) : Route()
