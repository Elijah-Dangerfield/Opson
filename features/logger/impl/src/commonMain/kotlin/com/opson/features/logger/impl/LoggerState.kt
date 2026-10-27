package com.opson.features.logger.impl

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.diary.Settings
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.ParsedItem
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.time.RelativeDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

/** What the add sheet draws. Built by [LoggerStateBuilder]; every number in it is policy-resolved. */
data class LoggerState(
    val loading: Boolean = true,
    /** The day it logs to, for the a11y of the title when it is not today. */
    val day: RelativeDay = RelativeDay.Today,
    val slot: MealSlot = MealSlot.Dinner,
    /** The title's dropdown. One entry, and no chevron, with meal groups off. */
    val slots: List<MealSlot> = MealSlot.entries,
    val pane: LoggerPane = LoggerPane.Actions,
    /** About 60% tall when opened from a +, full once dragged up or on Describe or Saved. */
    val height: SheetHeight = SheetHeight.Partial,
    /** The grid, in the design's order, only the ones that can run (design sync 4, 13.4a). */
    val actions: List<AddAction> = emptyList(),
    /** Under the grid at full height (13.4b): the last foods logged, each with a one-tap +. */
    val recent: List<FoodRowUi> = emptyList(),
    val describe: DescribeUi = DescribeUi(),
    val saved: SavedUi = SavedUi(),
)

/** How tall the add sheet is. It opens [Partial] from a + and only ever grows. */
enum class SheetHeight { Partial, Full }

/** The three faces of the sheet: the grid, and the two panes it opens in place. */
enum class LoggerPane { Actions, Describe, Saved }

/** The grid's actions (design sync 4, 13.4a). Each is in the list only when it can run. */
sealed interface AddAction {
    data object SearchFood : AddAction

    /** `release.barcode`. */
    data object ScanBarcode : AddAction

    /** `release.photo`. */
    data object Photo : AddAction

    /** Describe it; "Say or type it" when [voice] (`release.voice`) is on, since only then can it hear. */
    data class SayOrType(val voice: Boolean) : AddAction
    data object SavedMeals : AddAction

    /** Copies yesterday's [slot]: only when yesterday has one. */
    data class CopyMeal(val slot: MealSlot) : AddAction

    data object Water : AddAction
    data object WeighIn : AddAction
    data object Note : AddAction

    /** Only for a real meal: Uncategorized has no "ate" mark. */
    data object AteNoDetails : AddAction

    /** `release.fasting`, offered in this mode, and the timer on. */
    data object Fasting : AddAction
}

/** The Describe it pane. */
data class DescribeUi(
    val text: String = "",
    val status: DescribeStatus = DescribeStatus.Idle,
    /** What the read made of the text, in order, each with its own amount controls. */
    val lines: List<DescribedLine> = emptyList(),
    /** "3 items · About 379 kcal" over Add: everything Add writes. Null with nothing to add. */
    val total: DescribeTotal? = null,
    /** The search for a missed food or a line's replacement, drawn in place of the lines; null when closed. */
    val find: DescribeFindUi? = null,
    /** "Or just a number" is a calorie entry, so it is not offered when calories are hidden. */
    val canNumber: Boolean = false,
    val number: String = "",
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

/**
 * One line Describe read: "Egg, whole, cooked", "2 eggs", "176". [matched] is
 * false when nothing in the catalog fit, so it has no amount controls and a
 * tap finds the food instead.
 */
data class DescribedLine(
    val key: String,
    val name: String,
    val amount: String,
    val calories: Display,
    val matched: Boolean,
    val canLess: Boolean,
    /** The unit menu: "egg", "cup", "grams". Empty for a line with no food. */
    val units: List<String>,
    val unitIndex: Int,
    /** The next best foods for the same words, one tap each. */
    val alternatives: List<String>,
)

/** [count] lines and the energy of all of them, policy-resolved. */
data class DescribeTotal(val count: Int, val calories: Display)

/** The find-a-food search: [replacing] is the line's name when swapping one; [results] null until the catalog answers. */
data class DescribeFindUi(val replacing: String?, val query: String, val results: List<SwapResultUi>?)

data class SavedUi(val meals: List<SavedMealUi> = emptyList())

data class SavedMealUi(val id: String, val name: String, val foods: String, val calories: Display)

sealed interface LoggerAction {
    data class InputsArrived(val inputs: LoggerInputs) : LoggerAction
    data class PolicyArrived(val policy: VisibilityPolicy) : LoggerAction
    data class SlotPicked(val slot: MealSlot) : LoggerAction

    /** The handle or the grid dragged up while the sheet is at 60%. */
    data object SheetExpanded : LoggerAction

    // The grid.
    data object SearchFoodTapped : LoggerAction
    data object BarcodeTapped : LoggerAction
    data object PhotoTapped : LoggerAction
    /** Opens Describe it; with `release.voice` on it starts listening too, asking for the mic first if it must. */
    data object DescribeItTapped : LoggerAction
    data object SavedMealsTapped : LoggerAction
    data object CopyMealTapped : LoggerAction
    data object WaterTapped : LoggerAction
    data object WeighInTapped : LoggerAction
    data object NoteTapped : LoggerAction
    data object AteNoDetailsTapped : LoggerAction
    data object FastingTapped : LoggerAction

    /** Recent's + at full height: logs it at its last amount, and ✓ takes it back out. */
    data class RecentAddTapped(val row: FoodRowUi) : LoggerAction
    data class RecentNameTapped(val row: FoodRowUi) : LoggerAction

    // Saved meals.
    data class SavedMealPicked(val id: String) : LoggerAction

    // Describe it.
    data class DescribeTyped(val text: String) : LoggerAction
    data class DescribeArrived(val text: String, val items: List<ParsedItem>) : LoggerAction
    data class DescribeFailed(val text: String) : LoggerAction

    /** Describe's "Just save the name". */
    data object SaveNameTapped : LoggerAction
    data class NumberTyped(val text: String) : LoggerAction
    data object AddNumberTapped : LoggerAction

    /** Describe's Add: logs its lines. */
    data object AddToMealTapped : LoggerAction

    data object BackTapped : LoggerAction
    data object CloseTapped : LoggerAction
}

/**
 * Everything but [ShowToast], [WaterAdded] and [Dismiss] opens something in place of the sheet,
 * so Back from there lands where the sheet was opened.
 */
sealed interface LoggerEvent {
    data class OpenSearch(val day: LocalDate, val slot: MealSlot) : LoggerEvent
    data class OpenFoodDetail(val ref: FoodRef, val source: PickSource, val day: LocalDate, val slot: MealSlot) : LoggerEvent
    data class OpenMealNote(val day: LocalDate, val slot: MealSlot) : LoggerEvent
    data class OpenScanner(val day: LocalDate, val slot: MealSlot) : LoggerEvent
    data class OpenPhoto(val day: LocalDate, val slot: MealSlot) : LoggerEvent
    data object OpenWeighIn : LoggerEvent
    data object OpenFasting : LoggerEvent

    /** A glass went in, [ml] of it, on [day]: the snackbar's Undo takes it off again. */
    data class WaterAdded(val day: LocalDate, val ml: Int) : LoggerEvent
    data class ShowToast(val toast: Toast) : LoggerEvent
    data object Dismiss : LoggerEvent
}

/** Everything the add sheet reads, gathered in one place so the builder is a pure function of it. */
data class LoggerInputs(
    val today: LocalDate,
    val day: Day,
    val dayBefore: Day,
    val settings: Settings,
    /** The foods logged most lately, newest first: Recent at full height. */
    val recent: List<LogItem>,
    val savedMeals: List<SavedMeal>,
    /** The person's own foods: "Save the name" reuses one by the same name. */
    val customFoods: List<CustomFood> = emptyList(),
    /** The last 14 days and today (`MealSlotPicker.HISTORY_DAYS`), which the meal picker reads. */
    val recentDays: List<Day> = emptyList(),
    /** When these inputs were read, on the person's clock. */
    val now: LocalDateTime = LocalDateTime(today, LocalTime(NOON, 0)),
    val timeZone: TimeZone = TimeZone.UTC,
)

private const val NOON = 12
