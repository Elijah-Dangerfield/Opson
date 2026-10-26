package com.opson.features.logger

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * The logger, opened by the center + and from a meal's own +. A bottom sheet at
 * `BottomSheetDefaults.FullHeightFraction` of the screen, not a tab.
 *
 * @param slot the meal to log into; `null` picks from the time of day
 * (`MealSlot.defaultFor`), or Uncategorized with meal groups off.
 * @param dayIso the day to log to (`LocalDate.toString()`); `null` is the day
 * Today is showing (`SelectedDay`).
 * @param focusSearch opens with the keyboard up in search ("Log something new").
 * @param prePickFoodId a food the Foods tab's + already picked, catalog or
 * custom per [prePickIsCustom], added to the basket once.
 * @param prePickSavedMealId a saved meal whose items go into the basket once.
 */
@Serializable
data class LoggerRoute(
    val slot: MealSlot? = null,
    val dayIso: String? = null,
    val focusSearch: Boolean = false,
    val prePickFoodId: String? = null,
    val prePickIsCustom: Boolean = false,
    val prePickSavedMealId: String? = null,
) : Route()

/** The Log screen's tabs. Search lives in the logger itself and Photo is not built in 0.1. */
@Serializable
enum class LogTab { Describe, Saved }

/**
 * The Log screen behind the logger's Describe it, Saved meals and Just a number
 * chips: free text to parsed items, or a saved meal / yesterday's meal.
 *
 * @param focusNumber opens Describe with the "Or just a number" field focused.
 */
@Serializable
data class LogRoute(
    val dayIso: String,
    val slot: MealSlot,
    val tab: LogTab = LogTab.Describe,
    val focusNumber: Boolean = false,
) : Route()
