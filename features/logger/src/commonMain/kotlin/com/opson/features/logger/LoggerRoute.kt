package com.opson.features.logger

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * The logger, opened by the center + and from a meal's own +. A bottom sheet, not a tab: about
 * 60% tall from a +, full height once dragged up, searched, or opened to type or on a pane.
 * Search, Describe it and Saved meals all live inside it.
 *
 * @param slot the meal to log into; `null` picks from the time of day
 * (`MealSlot.defaultFor`), or Uncategorized with meal groups off.
 * @param dayIso the day to log to (`LocalDate.toString()`); `null` is the day
 * Today is showing (`SelectedDay`).
 * @param focusSearch opens with the keyboard up in search ("Log something new").
 * @param prePickFoodId a food the Foods tab's + already picked, catalog or
 * custom per [prePickIsCustom], added to the basket once.
 * @param prePickSavedMealId a saved meal whose items go into the basket once.
 * @param start the pane the sheet opens on.
 * @param pickForMeal set by Create a meal: the sheet picks foods for a meal
 * being put together instead of logging them, and hands the picks back through
 * `MealPicks` under this request id. Its picks stay out of the logger's basket.
 */
@Serializable
data class LoggerRoute(
    val slot: MealSlot? = null,
    val dayIso: String? = null,
    val focusSearch: Boolean = false,
    val prePickFoodId: String? = null,
    val prePickIsCustom: Boolean = false,
    val prePickSavedMealId: String? = null,
    val start: LoggerStart = LoggerStart.Search,
    val pickForMeal: String? = null,
) : Route()

/** Where the logger sheet opens. [Number] is Describe it with the "Or just a number" field focused. */
@Serializable
enum class LoggerStart { Search, Describe, Saved, Number }
