package com.opson.features.logger

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * The logger, opened by the center + and from a meal's own +. A bottom sheet at
 * `BottomSheetDefaults.FullHeightFraction` of the screen, not a tab. Search,
 * Describe it and Saved meals all live inside it.
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
) : Route()

/** Where the logger sheet opens. [Number] is Describe it with the "Or just a number" field focused. */
@Serializable
enum class LoggerStart { Search, Describe, Saved, Number }

/** The old Log screen's tabs, kept for [LogRoute]. */
@Serializable
enum class LogTab { Describe, Saved }

/**
 * An alias for the logger sheet opened on Describe it or Saved meals. Design v3
 * folded the separate Log screen into the sheet; this route stays so callers
 * written against it keep working, and opens the same sheet as [toLoggerRoute].
 * New code passes [LoggerRoute.start] instead.
 */
@Serializable
data class LogRoute(
    val dayIso: String,
    val slot: MealSlot,
    val tab: LogTab = LogTab.Describe,
    val focusNumber: Boolean = false,
) : Route() {
    fun toLoggerRoute(): LoggerRoute = LoggerRoute(
        slot = slot,
        dayIso = dayIso,
        start = when {
            tab == LogTab.Saved -> LoggerStart.Saved
            focusNumber -> LoggerStart.Number
            else -> LoggerStart.Describe
        },
    )
}

/**
 * How it felt: hunger before, fullness after, mood and a note for one meal. The
 * logger opens it after adding when the mode asks, or when hunger questions are
 * on; Today's meal sheet can open it too. A bottom sheet with a typeMap for
 * [MealSlot], registered by the logger.
 */
@Serializable
data class HowItFeltRoute(val dayIso: String, val slot: MealSlot) : Route()
