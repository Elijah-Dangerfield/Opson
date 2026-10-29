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
 * One entry already on the log, from its row on Today: the food detail for exactly [entryId] on
 * [dayIso] (`LocalDate.toString()`), with Save changes and Remove from log in place of Add. Two
 * helpings of the same food logged the same day are two entries; what this saves or removes is
 * this one alone. Its numbers are the entry's own, as they were logged. Plain strings, no typeMap.
 */
@Serializable
data class LoggedFoodRoute(val dayIso: String, val entryId: String) : Route()

/**
 * The exercise sheet (`release.exercise`): log an activity, or edit or delete one.
 *
 * @param entryId the entry to edit; `null` logs a new one.
 * @param dayIso the day a new entry counts on; `null` is the day Today is showing.
 * @param notOnWatch a new entry starts with "My watch didn't record this" on: Today's Activity
 *   card opens it so when a health app already counts the day's workouts.
 */
@Serializable
data class ExerciseRoute(
    val entryId: String? = null,
    val dayIso: String? = null,
    val notOnWatch: Boolean = false,
) : Route()

/**
 * Add a packaged food from photos of its pack's front and nutrition label (`release.submitFood`,
 * `docs/plans/screens/submit-food.md`): the server reads the label, the person checks it, and the
 * food is saved to their foods and sent to Opson's food database for review.
 *
 * With [dayIso] and [slot] it is also logged to that meal. Without them (from the Foods tab's Add
 * a food) it is only saved: there is no meal to log to. [barcode] is a code the scanner read,
 * shown and not retyped. [entry] is where the person started, for telemetry and for where the
 * screen goes when it is done.
 */
@Serializable
data class SubmitFoodRoute(
    val dayIso: String? = null,
    val slot: MealSlot? = null,
    val barcode: String? = null,
    val entry: SubmitEntry = SubmitEntry.EmptySearch,
) : Route() {
    /** Whether saving it also logs it to a meal. */
    val logs: Boolean get() = dayIso != null && slot != null
}

/**
 * The barcode scanner for a Builder checking packs (More › Builders' "Check a food against its
 * pack"): Also read the label starts on, so a code that finds a food opens Check against the pack
 * instead of its detail, and a code nothing knows offers Add a packaged food without logging it.
 * [dayIso] is only where a food goes if they turn the label reading off and add it from its
 * detail. Show it only to an active Builder with `release.builders` on.
 */
@Serializable
data class CheckPackRoute(val dayIso: String) : Route()

/** Where "Add a packaged food" was opened from. */
@Serializable
enum class SubmitEntry(val wire: String) {
    /** The scanner read a code nothing knew. */
    BarcodeMiss("barcode_miss"),

    /** Search found nothing. */
    EmptySearch("empty_search"),

    /** "Can't find it?" under search's results. */
    SearchResults("search_results"),

    /** The + sheet's Packaged food tile. */
    AddSheet("add_sheet"),

    /** The Foods tab's Add a food, which saves without logging. */
    Foods("foods"),

    /** More › Builders' Add a food from its pack, which saves without logging, as Foods does. */
    Builders("builders"),
}

/**
 * Whether "Add a packaged food" can open (`release.submitFood`), for screens outside the logger
 * that offer it. Read when the screen opens, like the logger's own flags.
 */
interface SubmitFoodAvailability {
    fun available(): Boolean
}
