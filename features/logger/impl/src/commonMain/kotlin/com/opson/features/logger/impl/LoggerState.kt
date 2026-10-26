package com.opson.features.logger.impl

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.FoodKind
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.diary.Settings
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.FoodSearchFilter
import com.opson.libraries.foods.ParsedItem
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.MacroPart
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.time.RelativeDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

/** What the logger sheet draws. Built by [LoggerStateBuilder]; every number in it is policy-resolved. */
data class LoggerState(
    val loading: Boolean = true,
    /** Right of the title: Today, Yesterday or a date. */
    val day: RelativeDay = RelativeDay.Today,
    val slot: MealSlot = MealSlot.Dinner,
    /** The title's dropdown. One entry, and no chevron, with meal groups off. */
    val slots: List<MealSlot> = MealSlot.entries,
    val pane: LoggerPane = LoggerPane.Search,
    val query: String = "",
    val focusSearch: Boolean = false,
    /** About 60% tall when opened from a +, full once dragged up or searched (design sync 2). */
    val height: SheetHeight = SheetHeight.Partial,
    /** The sideways row: Photo, Describe, Yesterday's, Saved. Empty while the person is typing, so more results fit. */
    val methods: List<LoggerMethod> = emptyList(),
    /**
     * The second row, Ate no details and Just a number (or Add a note). Both log at once, so they
     * step aside while something is picked, as well as while typing.
     */
    val quickMethods: List<LoggerMethod> = emptyList(),
    val body: LoggerBody = LoggerBody.FirstRun,
    val describe: DescribeUi = DescribeUi(),
    val saved: SavedUi = SavedUi(),
    /** Null when nothing is picked, and on Describe and Saved, which have their own buttons. */
    val basket: BasketFooter? = null,
    /**
     * Opened by Create a meal's "Add a food": the title reads "This meal" with no
     * dropdown or day, and a miss offers only "Save the name only".
     */
    val pickingForMeal: Boolean = false,
    /** The barcode button in the empty search bar: `release.barcode` on, and not picking for a meal. */
    val canScan: Boolean = false,
    /** The options button and the category tabs; null with `release.searchOptions` off. */
    val options: SearchOptionsBar? = null,
) {
    /** The search is not blank, so the chips and the day label step aside. */
    val typing: Boolean get() = query.isNotBlank()
}

/** How tall the logger sheet is. It opens [Partial] from a + and only ever grows. */
enum class SheetHeight { Partial, Full }

/** The three faces of the sheet. Describe and Saved were the Log screen's tabs before design v3. */
enum class LoggerPane { Search, Describe, Saved }

/** The method chips, in the design's order. Photo is 0.2, behind `release.photo`. */
sealed interface LoggerMethod {
    data object Photo : LoggerMethod
    data object DescribeIt : LoggerMethod
    data class Yesterday(val slot: MealSlot) : LoggerMethod
    data object SavedMeals : LoggerMethod
    data object AteNoDetails : LoggerMethod
    data object JustANumber : LoggerMethod

    /** Stands in for [JustANumber] when calories are hidden: typing a number is itself a calorie entry. */
    data object AddANote : LoggerMethod
}

/** A food the user can pick. [key] is unique per food, so the same food reads as picked in every section. */
data class FoodRowUi(
    val key: String,
    val ref: FoodRef,
    val source: PickSource,
    val name: String,
    val shortName: String,
    /** The brand (cuisine, once the catalog has it), or null. */
    val subtitle: String?,
    val amount: String,
    val macros: List<MacroPart>,
    /** Bare, as v3 draws a row: "525". */
    val calories: Display,
    val picked: Boolean,
    /** Where the food came from; null with the search options off, or for a source with no badge. */
    val badge: SourceBadge? = null,
    /** Tiers 1 to 3: someone accountable stands behind the numbers. Never set for the person's own foods. */
    val verified: Boolean = false,
    /** The tile's picture, read from the name. */
    val kind: FoodKind = FoodKind.Plate,
)

sealed interface LoggerBody {
    /**
     * "Usual at {slot}" (left out when there is nothing usual yet), then Recent. For
     * Uncategorized the first list is "Usual around {usualAround}": what this person logs near
     * this time of day, whatever the meal.
     */
    data class Browse(
        val slot: MealSlot,
        val usual: List<FoodRowUi>,
        val recent: List<FoodRowUi>,
        val usualAround: LocalTime? = null,
    ) : LoggerBody

    /** Nothing logged yet, so no usual picks or recents. */
    data object FirstRun : LoggerBody

    data object Searching : LoggerBody

    /** [openFoodFacts]: some rows are Open Food Facts', so the licence's credit goes under them. */
    data class Results(val rows: List<FoodRowUi>, val offline: Boolean, val openFoodFacts: Boolean = false) : LoggerBody

    /** Favorites, Custom or My foods before typing: the list itself, no heading. */
    data class Listed(val rows: List<FoodRowUi>) : LoggerBody

    /** Favorites or Custom with nothing in them yet. */
    data class TabEmpty(val tab: SearchTab) : LoggerBody

    /** Common, Restaurant or Beverages before typing: they search the catalog, so they need words. */
    data class TabHint(val tab: SearchTab) : LoggerBody

    /** A miss is never a dead end: [query] goes into Describe or becomes a saved name. */
    data class NoResults(val query: String, val offline: Boolean) : LoggerBody
}

data class BasketFooter(val count: Int, val names: String, val calories: Display, val slot: MealSlot)

/** The Describe it pane. */
data class DescribeUi(
    val text: String = "",
    val status: DescribeStatus = DescribeStatus.Idle,
    /** What the parse put in the basket, in order. */
    val lines: List<DescribedLine> = emptyList(),
    /** "Or just a number" is a calorie entry, so it is not offered when calories are hidden. */
    val canNumber: Boolean = false,
    val number: String = "",
    val focusNumber: Boolean = false,
    /** The mic and what it is doing; null with `release.voice` off. */
    val voice: VoiceUi? = null,
)

sealed interface DescribeStatus {
    data object Idle : DescribeStatus
    data object Parsing : DescribeStatus
    data object Parsed : DescribeStatus
    data object Offline : DescribeStatus
    data object Failed : DescribeStatus
}

/** One line Describe read: "Egg, large", "2 · Estimate", "156". [ref] is null when nothing matched, so it cannot be edited. */
data class DescribedLine(
    val key: String,
    val ref: FoodRef?,
    val name: String,
    val amount: String,
    val calories: Display,
)

data class SavedUi(val meals: List<SavedMealUi> = emptyList())

data class SavedMealUi(val id: String, val name: String, val foods: String, val calories: Display)

sealed interface LoggerAction {
    data class InputsArrived(val inputs: LoggerInputs) : LoggerAction
    data class PolicyArrived(val policy: VisibilityPolicy) : LoggerAction
    data class SlotPicked(val slot: MealSlot) : LoggerAction
    data class SearchTyped(val text: String) : LoggerAction
    data object SearchCleared : LoggerAction
    data class SearchResultsArrived(val outcome: SearchOutcome) : LoggerAction
    data class FoodNameTapped(val row: FoodRowUi) : LoggerAction
    data class FoodToggleTapped(val row: FoodRowUi) : LoggerAction
    data object DescribeItTapped : LoggerAction

    /** The no-results state's "Describe {term}" and the "Describe it" link under results. */
    data object DescribeTermTapped : LoggerAction

    /** "Save the name only", "save the name", and Describe's "Just save the name". */
    data object SaveNameTapped : LoggerAction
    data object SavedMealsTapped : LoggerAction
    data class SavedMealPicked(val id: String) : LoggerAction
    data object YesterdayTapped : LoggerAction
    data object AteNoDetailsTapped : LoggerAction
    data object JustANumberTapped : LoggerAction
    data object AddANoteTapped : LoggerAction
    data class DescribeTyped(val text: String) : LoggerAction
    data class DescribeArrived(val text: String, val items: List<ParsedItem>) : LoggerAction
    data class DescribeFailed(val text: String) : LoggerAction
    data class DescribedLineEditTapped(val line: DescribedLine) : LoggerAction
    data class NumberTyped(val text: String) : LoggerAction
    data object AddNumberTapped : LoggerAction
    data object BackTapped : LoggerAction
    data object AddToMealTapped : LoggerAction
    data object CloseTapped : LoggerAction

    /** The handle or the list dragged up while the sheet is at 60%. */
    data object SheetExpanded : LoggerAction

    /** A tap in the search field. */
    data object SearchFocused : LoggerAction

    /** The barcode button in the empty search bar. */
    data object BarcodeTapped : LoggerAction

    /** The Photo chip. */
    data object PhotoTapped : LoggerAction

    /** The options button in the search bar (`release.searchOptions`). */
    data object SearchOptionsTapped : LoggerAction

    data class TabPicked(val tab: SearchTab) : LoggerAction

    /** Whether the server searches Open Food Facts, asked once when the sheet opens. */
    data class SearchOptionsArrived(val openFoodFacts: Boolean) : LoggerAction
}

sealed interface LoggerEvent {
    data class OpenAmount(val ref: FoodRef, val source: PickSource, val day: LocalDate, val slot: MealSlot) : LoggerEvent
    data class OpenMealNote(val day: LocalDate, val slot: MealSlot) : LoggerEvent
    data class OpenScanner(val day: LocalDate, val slot: MealSlot) : LoggerEvent
    data class OpenPhoto(val day: LocalDate, val slot: MealSlot) : LoggerEvent
    data class OpenSearchOptions(val openFoodFacts: Boolean) : LoggerEvent
    data class ShowToast(val toast: Toast) : LoggerEvent
    data object Dismiss : LoggerEvent
}

/** Everything the logger reads, gathered in one place so the builder is a pure function of it. */
data class LoggerInputs(
    val today: LocalDate,
    val day: Day,
    val dayBefore: Day,
    val settings: Settings,
    val basket: List<BasketItem>,
    val usualBySlot: Map<MealSlot, List<LogItem>>,
    val recent: List<LogItem>,
    val customFoods: List<CustomFood>,
    val savedMeals: List<SavedMeal>,
    /** What the Logger calls Favorites: the foods logged most lately. Empty with the search options off. */
    val favorites: List<LogItem> = emptyList(),
    /** Foods logged, newest first, for Most recent and My foods. Empty with the search options off. */
    val history: List<LogItem> = emptyList(),
    /** Catalog foods on the device by id, so a row from the diary can still name its source. */
    val known: Map<String, Food> = emptyMap(),
    /** The last 14 days and today (`MealSlotPicker.HISTORY_DAYS`), which the meal picker and "Usual around" read. */
    val recentDays: List<Day> = emptyList(),
    /** When these inputs were read, on the person's clock. */
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
