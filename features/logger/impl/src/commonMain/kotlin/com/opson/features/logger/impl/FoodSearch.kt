package com.opson.features.logger.impl

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.diary.Settings
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.FoodSearchFilter
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

/** What full-screen search draws (design sync 4, 13.4c). Built by [FoodSearchStateBuilder]. */
data class FoodSearchState(
    val loading: Boolean = true,
    val slot: MealSlot = MealSlot.Dinner,
    /** The meal chip's menu. One entry, and no chevron, with meal groups off. */
    val slots: List<MealSlot> = MealSlot.entries,
    val query: String = "",
    /** The keyboard comes up when search opens, and not again on the way back from a food. */
    val focusSearch: Boolean = true,
    val scope: SearchScope = SearchScope.All,
    val scopes: List<SearchScope> = SearchScope.entries,
    /** The barcode button in the empty field: `release.barcode` on, and not picking for a meal. */
    val canScan: Boolean = false,
    /** "Not finding it?": Photograph it (`release.photo`) and Describe it log, so neither is offered when picking for a meal. */
    val canPhotograph: Boolean = false,
    val canDescribe: Boolean = true,
    val body: SearchBody = SearchBody.Searching,
    /** Opened by Create a meal: + picks instead of logging, and [picks] is the footer. */
    val pickingForMeal: Boolean = false,
    val picks: PicksFooter? = null,
    /** The options button and the category tabs; null with `release.searchOptions` off. */
    val options: SearchOptionsBar? = null,
) {
    val typing: Boolean get() = query.isNotBlank()
}

/** The chips after the meal: All, Your foods, Saved meals. */
enum class SearchScope { All, YourFoods, SavedMeals }

sealed interface SearchBody {
    /**
     * Before typing on All: "Usual at {slot}" (left out when there is nothing usual yet), then
     * Recent. For Uncategorized the first list is "Usual around {usualAround}".
     */
    data class Browse(
        val slot: MealSlot,
        val usual: List<FoodRowUi>,
        val recent: List<FoodRowUi>,
        val usualAround: LocalTime? = null,
    ) : SearchBody

    /** Nothing logged yet, so no usual picks or recents. */
    data object FirstRun : SearchBody

    data object Searching : SearchBody

    /**
     * [openFoodFacts]: some rows are Open Food Facts', so the licence's credit goes under them.
     * [nevo]: some are NEVO's, so RIVM's citation does, whatever the search options.
     */
    data class Results(
        val rows: List<FoodRowUi>,
        val offline: Boolean,
        val openFoodFacts: Boolean = false,
        val nevo: Boolean = false,
    ) : SearchBody

    /** Your foods, or a Favorites or Custom tab, before typing: the list itself, no heading. */
    data class Listed(val rows: List<FoodRowUi>) : SearchBody

    /** Your foods with nothing in it yet. */
    data object NoFoodsYet : SearchBody

    /** Favorites or Custom with nothing in them yet (`release.searchOptions`). */
    data class TabEmpty(val tab: SearchTab) : SearchBody

    /** Common, Restaurant or Beverages before typing: they search the catalog, so they need words. */
    data class TabHint(val tab: SearchTab) : SearchBody

    /** A miss is never a dead end: [query] goes into Describe or becomes a saved name. */
    data class NoResults(val query: String, val offline: Boolean) : SearchBody

    /** The Saved meals chip: the meals whose name or foods match, each logged whole by its +. */
    data class SavedMeals(val meals: List<SavedMealRowUi>) : SearchBody
}

/** A saved meal with its +: "Rice plate", "Arroz con pollo, Side salad", "585". */
data class SavedMealRowUi(val id: String, val name: String, val foods: String, val calories: Display, val added: Boolean)

/** Create a meal's footer: how many are picked, and their names. */
data class PicksFooter(val count: Int, val names: String)

/** What the person has done on search so far, as opposed to what it read. */
data class SearchChoices(
    val slot: MealSlot,
    /** The meal was given or picked, so the picker's answer no longer moves it. */
    val slotChosen: Boolean = true,
    val query: String = "",
    /** Until the person types or opens a food. */
    val focusSearch: Boolean = true,
    val search: SearchOutcome? = null,
    val scope: SearchScope = SearchScope.All,
    /** `release.barcode`, `release.photo` and `release.searchOptions`, read once when search opens. */
    val scanAvailable: Boolean = false,
    val photoAvailable: Boolean = false,
    val searchOptions: Boolean = false,
    /** The category tab (`release.searchOptions`); search always opens on All. */
    val tab: SearchTab = SearchTab.All,
    /** Whether the server offers Open Food Facts as a database. */
    val openFoodFacts: Boolean = false,
    /** Picking for a meal Create a meal is putting together: nothing is written to the diary. */
    val pickingForMeal: Boolean = false,
    val picked: List<LogPick> = emptyList(),
    /** The day's item ids when it was first read; anything logged to this meal since reads as added (✓). */
    val seen: Set<String>? = null,
)

/** Everything search reads, so the builder is a pure function of it. */
data class SearchInputs(
    val today: LocalDate,
    val day: Day,
    val settings: Settings,
    val usualBySlot: Map<MealSlot, List<LogItem>>,
    /** The foods logged most lately, newest first: Recent before typing. */
    val recent: List<LogItem>,
    val customFoods: List<CustomFood>,
    val savedMeals: List<SavedMeal>,
    /** Foods logged, newest first: each one's last amount, Your foods, and Most recent. */
    val history: List<LogItem> = emptyList(),
    /** The foods logged most over the last month: "Your usual", and Favorites. */
    val frequent: List<LogItem> = emptyList(),
    /** Catalog foods on the device by id, so a row from the diary can still name its source. Empty with the search options off. */
    val known: Map<String, Food> = emptyMap(),
    /** The last 14 days and today, which the meal picker and "Usual around" read. */
    val recentDays: List<Day> = emptyList(),
    val now: LocalDateTime = LocalDateTime(today, LocalTime(NOON, 0)),
    val timeZone: TimeZone = TimeZone.UTC,
)

private const val NOON = 12

/**
 * A catalog search answer for [query] asked with [filter]; [offline] when it came from the device
 * cache, or nowhere. An answer for another query or filter is stale.
 */
data class SearchOutcome(
    val query: String,
    val foods: List<Food>,
    val offline: Boolean,
    val filter: FoodSearchFilter = FoodSearchFilter.None,
)

sealed interface SearchAction {
    data class InputsArrived(val inputs: SearchInputs) : SearchAction
    data class PolicyArrived(val policy: VisibilityPolicy) : SearchAction
    data class SlotPicked(val slot: MealSlot) : SearchAction
    data class SearchTyped(val text: String) : SearchAction
    data object SearchCleared : SearchAction
    data class SearchResultsArrived(val outcome: SearchOutcome) : SearchAction
    data class ScopePicked(val scope: SearchScope) : SearchAction

    /** A food's name: its detail. */
    data class FoodNameTapped(val row: FoodRowUi) : SearchAction

    /** A food's +: logged now at its last amount. Its ✓: taken back out. Picking for a meal, a pick either way. */
    data class AddTapped(val row: FoodRowUi) : SearchAction
    data class SavedMealAddTapped(val id: String) : SearchAction
    data object BarcodeTapped : SearchAction

    // "Not finding it?"
    data object PhotographTapped : SearchAction
    data object DescribeTapped : SearchAction
    data object SaveNameTapped : SearchAction

    /** Create a meal's "Add to meal": the picks go back. */
    data object AddPicksTapped : SearchAction
    data object BackTapped : SearchAction

    // release.searchOptions
    data object SearchOptionsTapped : SearchAction
    data class TabPicked(val tab: SearchTab) : SearchAction
    data class SearchOptionsArrived(val openFoodFacts: Boolean) : SearchAction
}

sealed interface SearchEvent {
    data class OpenFoodDetail(val ref: FoodRef, val source: PickSource, val day: LocalDate, val slot: MealSlot) : SearchEvent
    data class OpenScanner(val day: LocalDate, val slot: MealSlot) : SearchEvent
    data class OpenPhoto(val day: LocalDate, val slot: MealSlot) : SearchEvent
    data class OpenDescribe(val day: LocalDate, val slot: MealSlot, val text: String) : SearchEvent
    data class OpenSearchOptions(val openFoodFacts: Boolean) : SearchEvent

    /** Logged by a +: "[name] added to {meal}" with Undo for exactly [event]'s items. */
    data class Added(val name: String, val event: LoggedToMeal) : SearchEvent

    /** A + picked or unpicked a food for a meal being put together. */
    data class Picked(val on: Boolean) : SearchEvent
    data object Close : SearchEvent
}
