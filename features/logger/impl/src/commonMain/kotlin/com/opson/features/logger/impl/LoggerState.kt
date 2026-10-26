package com.opson.features.logger.impl

import com.opson.features.logger.LogTab
import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.MacroPart
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.time.RelativeDay
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource

/** What the logger sheet draws. Built by [LoggerStateBuilder]; every number in it is policy-resolved. */
data class LoggerState(
    val loading: Boolean = true,
    /** Non-null when logging to a day other than today: the header says "For Yesterday". */
    val otherDay: RelativeDay? = null,
    val slot: MealSlot = MealSlot.Dinner,
    val slots: List<MealSlot> = MealSlot.entries,
    /** The day's totals, hidden columns left out; null when none is visible. */
    val totals: List<TotalsColumn>? = null,
    /** Shown instead of [totals] when no number is visible. */
    val mealChecks: List<MealCheck>? = null,
    val query: String = "",
    val focusSearch: Boolean = false,
    val methods: List<LoggerMethod> = emptyList(),
    val body: LoggerBody = LoggerBody.FirstRun,
    /** Null when nothing is picked. */
    val basket: BasketFooter? = null,
)

data class TotalsColumn(val metric: Metric, val label: StringResource, val display: Display)

data class MealCheck(val slot: MealSlot, val done: Boolean)

/** The method chips, in the prototype's order. */
sealed interface LoggerMethod {
    data object DescribeIt : LoggerMethod
    data object Water : LoggerMethod
    data object SavedMeals : LoggerMethod
    data class Yesterday(val slot: MealSlot) : LoggerMethod
    data object AteNoDetails : LoggerMethod
    data object WeighIn : LoggerMethod
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
    val amount: String,
    val macros: List<MacroPart>,
    val calories: Display,
    val picked: Boolean,
)

sealed interface LoggerBody {
    data class Browse(
        val favorites: List<FoodRowUi>,
        val usualTitle: UsualTitle,
        val usual: List<FoodRowUi>,
        val recent: List<FoodRowUi>,
    ) : LoggerBody

    /** Nothing logged yet, so no favorites, usual picks or recents. */
    data object FirstRun : LoggerBody

    data object Searching : LoggerBody

    data class Results(val rows: List<FoodRowUi>, val offline: Boolean) : LoggerBody

    data class NoResults(val offline: Boolean) : LoggerBody
}

sealed interface UsualTitle {
    data class UsualAt(val slot: MealSlot) : UsualTitle
    data object Suggested : UsualTitle
}

data class BasketFooter(val count: Int, val names: String, val calories: Display, val slot: MealSlot)

sealed interface LoggerAction {
    data class InputsArrived(val inputs: LoggerInputs) : LoggerAction
    data class PolicyArrived(val policy: VisibilityPolicy) : LoggerAction
    data class SlotPicked(val slot: MealSlot) : LoggerAction
    data class SearchTyped(val text: String) : LoggerAction
    data class SearchResultsArrived(val outcome: SearchOutcome) : LoggerAction
    data class FoodNameTapped(val row: FoodRowUi) : LoggerAction
    data class FoodToggleTapped(val row: FoodRowUi) : LoggerAction
    data object DescribeItTapped : LoggerAction
    data object WaterTapped : LoggerAction
    data object SavedMealsTapped : LoggerAction
    data object YesterdayTapped : LoggerAction
    data object AteNoDetailsTapped : LoggerAction
    data object WeighInTapped : LoggerAction
    data object JustANumberTapped : LoggerAction
    data object AddANoteTapped : LoggerAction
    data object AddToMealTapped : LoggerAction
    data object CloseTapped : LoggerAction
}

sealed interface LoggerEvent {
    data class OpenAmount(val ref: FoodRef, val source: PickSource) : LoggerEvent
    data class OpenLog(val day: LocalDate, val slot: MealSlot, val tab: LogTab, val focusNumber: Boolean = false) : LoggerEvent
    data object OpenWeight : LoggerEvent
    data class OpenMealNote(val day: LocalDate, val slot: MealSlot) : LoggerEvent
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
    val favorites: List<LogItem>,
    val usualBySlot: Map<MealSlot, List<LogItem>>,
    val recent: List<LogItem>,
    val customFoods: List<CustomFood>,
)

/** A catalog search answer for [query]; [offline] when it came from the device cache, or nowhere. */
data class SearchOutcome(val query: String, val foods: List<Food>, val offline: Boolean)
