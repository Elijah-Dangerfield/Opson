package com.opson.features.logger.impl

import com.opson.features.logger.LogTab
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.diary.Settings
import com.opson.libraries.foods.ParsedItem
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.LocalDate

/** What the Log screen draws. Built by [LogStateBuilder]. */
data class LogState(
    val loading: Boolean = true,
    val slot: MealSlot = MealSlot.Dinner,
    val slots: List<MealSlot> = MealSlot.entries,
    val tab: LogTab = LogTab.Describe,
    val describe: DescribeUi = DescribeUi(),
    val saved: SavedUi = SavedUi(),
)

data class DescribeUi(
    val text: String = "",
    val status: DescribeStatus = DescribeStatus.Idle,
    /** "Or just a number" is a calorie entry, so it is not offered when calories are hidden. */
    val canNumber: Boolean = false,
    val number: String = "",
    val focusNumber: Boolean = false,
)

sealed interface DescribeStatus {
    data object Idle : DescribeStatus
    data object Parsing : DescribeStatus
    data class Parsed(val items: List<ParsedItemUi>) : DescribeStatus
    data object Offline : DescribeStatus
    data object Failed : DescribeStatus
}

data class ParsedItemUi(val name: String, val amount: String, val calories: Display)

data class SavedUi(val dayBefore: DayBeforeRow? = null, val meals: List<SavedMealUi> = emptyList())

/** "Copy yesterday's dinner", offered only when the day before has something in that slot. */
data class DayBeforeRow(val slot: MealSlot, val names: String)

data class SavedMealUi(val id: String, val name: String, val foods: String, val calories: Display)

/** The parse as the view model holds it: the raw items, so adding them needs no second lookup. */
sealed interface DescribeModel {
    data object Idle : DescribeModel
    data object Parsing : DescribeModel
    data class Parsed(val items: List<ParsedItem>) : DescribeModel
    data object Offline : DescribeModel
    data object Failed : DescribeModel
}

data class LogInputs(
    val today: LocalDate,
    val day: Day,
    val dayBefore: Day,
    val settings: Settings,
    val savedMeals: List<SavedMeal>,
)

sealed interface LogAction {
    data class InputsArrived(val inputs: LogInputs) : LogAction
    data class PolicyArrived(val policy: VisibilityPolicy) : LogAction
    data class SlotPicked(val slot: MealSlot) : LogAction
    data class TabPicked(val tab: LogTab) : LogAction
    data class DescribeTyped(val text: String) : LogAction
    data object GoTapped : LogAction
    data class DescribeArrived(val items: List<ParsedItem>) : LogAction
    data object DescribeFailed : LogAction
    data object AddParsedTapped : LogAction
    data class NumberTyped(val text: String) : LogAction
    data object AddNumberTapped : LogAction
    data object SaveNameOnlyTapped : LogAction
    data object CopyDayBeforeTapped : LogAction
    data class SavedMealAddTapped(val id: String) : LogAction
    data object BackTapped : LogAction
}

sealed interface LogEvent {
    data class ShowToast(val toast: Toast) : LogEvent
    data object Close : LogEvent
}

/** The Log screen's state as a pure function. Surface [Surface.Logger]. */
object LogStateBuilder {

    @Suppress("LongParameterList")
    fun build(
        inputs: LogInputs,
        policy: VisibilityPolicy,
        slot: MealSlot,
        tab: LogTab,
        text: String,
        describe: DescribeModel,
        number: String,
        focusNumber: Boolean,
    ): LogState {
        val groupsOn = inputs.day.mealGroupsOn(inputs.settings)
        val effectiveSlot = if (groupsOn) slot else MealSlot.Uncategorized
        val context = inputs.day.formatContext(inputs.today)
        val canNumber = policy.level(Metric.Calories) != Visibility.Hide
        return LogState(
            loading = false,
            slot = effectiveSlot,
            slots = if (groupsOn) MealSlot.entries else listOf(MealSlot.Uncategorized),
            tab = tab,
            describe = DescribeUi(
                text = text,
                status = when (describe) {
                    DescribeModel.Idle -> DescribeStatus.Idle
                    DescribeModel.Parsing -> DescribeStatus.Parsing
                    DescribeModel.Offline -> DescribeStatus.Offline
                    DescribeModel.Failed -> DescribeStatus.Failed
                    is DescribeModel.Parsed -> DescribeStatus.Parsed(
                        describe.items.map { item ->
                            ParsedItemUi(
                                name = item.name,
                                amount = item.amountText,
                                calories = policy.resolve(Metric.Calories, item.kcal, Surface.Logger, context),
                            )
                        },
                    )
                },
                canNumber = canNumber,
                number = if (canNumber) number else "",
                focusNumber = focusNumber && canNumber,
            ),
            saved = SavedUi(
                dayBefore = inputs.dayBefore.items(effectiveSlot)
                    .takeIf { it.isNotEmpty() }
                    ?.let { items -> DayBeforeRow(effectiveSlot, items.joinToString(", ") { firstPart(it.name) }) },
                meals = inputs.savedMeals.map { meal ->
                    SavedMealUi(
                        id = meal.id,
                        name = meal.name,
                        foods = meal.items.joinToString(", ") { firstPart(it.name) },
                        calories = policy.resolve(Metric.Calories, meal.totals.kcal.value, Surface.Logger, context),
                    )
                },
            ),
        )
    }

    // The prototype cuts at the first comma only ("Greek yogurt, plain" to "Greek yogurt").
    private fun firstPart(name: String): String = name.substringBefore(',').trim()
}
