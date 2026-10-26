package com.opson.features.foods.impl

import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.PickedForMeal
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.MacroPart
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NutrientAmounts
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.macroLine

/** Create a meal (design v3, 11.2). Built by [CreateMealStateBuilder]; every number in it is policy-resolved. */
data class CreateMealState(
    val name: String = "",
    val usualSlot: MealSlot = MealSlot.Lunch,
    val slots: List<MealSlot> = MealSlot.meals,
    val items: List<MealItemUi> = emptyList(),
    /** "445 kcal", or hidden. */
    val total: Display = Display.Hidden,
    /** "13P 80C 8F"; empty when the mode shows no macros, and then the total says how many foods. */
    val totalMacros: List<MacroPart> = emptyList(),
    /** A name and at least one food. */
    val canSave: Boolean = false,
)

/** One food in the meal. [calories] is bare, as v3 draws a row: "300". */
data class MealItemUi(val name: String, val amount: String, val calories: Display)

/** What the person has put together so far. The view model holds it; [CreateMealStateBuilder] draws it. */
data class MealDraft(
    val name: String = "",
    val usualSlot: MealSlot = MealSlot.Lunch,
    val items: List<LogDraft> = emptyList(),
)

sealed interface CreateMealAction {
    data class PolicyArrived(val policy: VisibilityPolicy) : CreateMealAction
    data class PrefillArrived(val slot: MealSlot, val items: List<LogDraft>) : CreateMealAction
    data class NameTyped(val text: String) : CreateMealAction
    data class UsualSlotPicked(val slot: MealSlot) : CreateMealAction
    data class ItemRemoveTapped(val index: Int) : CreateMealAction
    data object AddFoodTapped : CreateMealAction

    /** The logger, opened for this meal, handed back what was picked. */
    data class FoodsPicked(val picks: PickedForMeal) : CreateMealAction
    data object SaveTapped : CreateMealAction
    data object CloseTapped : CreateMealAction
}

sealed interface CreateMealEvent {
    /** The logger sheet in pick-for-this-meal mode, answering under [requestId]. */
    data class OpenPicker(val requestId: String) : CreateMealEvent
    data class ShowToast(val toast: Toast) : CreateMealEvent
    data object Close : CreateMealEvent
}

/**
 * Create a meal's state from the draft, so previews and the Blind leak test run the same
 * mapping as the app. Numbers resolve on [Surface.Foods] with no goal.
 */
object CreateMealStateBuilder {

    fun build(draft: MealDraft, policy: VisibilityPolicy): CreateMealState {
        val bare = FormatContext(bare = true)
        val items = draft.items
        val kcal = items.mapNotNull { it.kcal }.takeIf { it.isNotEmpty() }?.sum()
        // A name-only item adds nothing; a line of "0P 0C 0F" for three of them would say something false.
        val anyMacros = items.any { it.protein != null || it.carbs != null || it.fat != null }
        val amounts = NutrientAmounts(
            kcal = kcal,
            protein = items.sumOf { it.protein ?: 0.0 },
            carbs = items.sumOf { it.carbs ?: 0.0 },
            fat = items.sumOf { it.fat ?: 0.0 },
            fiber = items.sumOf { it.fiber ?: 0.0 },
        )
        return CreateMealState(
            name = draft.name,
            usualSlot = draft.usualSlot,
            items = items.map { item ->
                MealItemUi(
                    name = item.name,
                    amount = item.amountLabel,
                    calories = policy.resolve(Metric.Calories, item.kcal, Surface.Foods, bare),
                )
            },
            total = policy.resolve(Metric.Calories, kcal, Surface.Foods),
            totalMacros = if (anyMacros) policy.macroLine(amounts, Surface.Foods, style = MacroLineStyle.Compact) else emptyList(),
            canSave = draft.name.isNotBlank() && items.isNotEmpty(),
        )
    }
}
