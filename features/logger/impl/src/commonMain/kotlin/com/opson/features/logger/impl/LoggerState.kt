package com.opson.features.logger.impl

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.diary.Settings
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.ParsedItem
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.MacroPart
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.time.RelativeDay
import kotlinx.datetime.LocalDate

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
    /** Empty while the person is typing, so more results fit. */
    val methods: List<LoggerMethod> = emptyList(),
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
) {
    /** The search is not blank, so the chips and the day label step aside. */
    val typing: Boolean get() = query.isNotBlank()
}

/** The three faces of the sheet. Describe and Saved were the Log screen's tabs before design v3. */
enum class LoggerPane { Search, Describe, Saved }

/** The method chips, in the design's order. Photo is Later and not built. */
sealed interface LoggerMethod {
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
)

sealed interface LoggerBody {
    /** "Usual at {slot}" (left out when there is nothing usual yet), then Recent. */
    data class Browse(
        val slot: MealSlot,
        val usual: List<FoodRowUi>,
        val recent: List<FoodRowUi>,
    ) : LoggerBody

    /** Nothing logged yet, so no usual picks or recents. */
    data object FirstRun : LoggerBody

    data object Searching : LoggerBody

    data class Results(val rows: List<FoodRowUi>, val offline: Boolean) : LoggerBody

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
}

sealed interface LoggerEvent {
    data class OpenAmount(val ref: FoodRef, val source: PickSource, val day: LocalDate, val slot: MealSlot) : LoggerEvent
    data class OpenMealNote(val day: LocalDate, val slot: MealSlot) : LoggerEvent
    data class OpenHowItFelt(val day: LocalDate, val slot: MealSlot) : LoggerEvent
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
)

/** A catalog search answer for [query]; [offline] when it came from the device cache, or nowhere. */
data class SearchOutcome(val query: String, val foods: List<Food>, val offline: Boolean)
