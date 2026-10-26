package com.opson.features.foods.impl

import com.opson.features.logger.LoggerRoute
import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.foodRef
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.VisibilityPolicy

/** The Foods tab (design v3, 11.1). Built by [FoodsStateBuilder]; every number in it is policy-resolved. */
data class FoodsState(
    val loading: Boolean = true,
    val savedMeals: List<FoodsRowUi> = emptyList(),
    val yourFoods: List<FoodsRowUi> = emptyList(),
    val recent: List<FoodsRowUi> = emptyList(),
)

/** What a Foods row opens the logger with. */
sealed interface FoodsTarget {
    data class Meal(val id: String) : FoodsTarget

    data class Food(val ref: FoodRef) : FoodsTarget

    /** The logger, opened with this already picked, so the person still chooses the meal and the day. */
    fun toLoggerRoute(): LoggerRoute = when (this) {
        is Meal -> LoggerRoute(prePickSavedMealId = id)
        is Food -> LoggerRoute(prePickFoodId = ref.id, prePickIsCustom = ref is FoodRef.Custom)
    }
}

/** A saved meal, a food of the user's own, or a recent food. [calories] is bare, as v3 draws it: "445". */
data class FoodsRowUi(
    val key: String,
    val target: FoodsTarget,
    val name: String,
    val sub: String,
    val calories: Display,
)

sealed interface FoodsAction {
    data class InputsArrived(val inputs: FoodsInputs) : FoodsAction
    data class PolicyArrived(val policy: VisibilityPolicy) : FoodsAction
    data object SearchTapped : FoodsAction
    data object CreateMealTapped : FoodsAction
    data object AddFoodTapped : FoodsAction

    /** The row or its +: both open the logger with it picked. */
    data class RowTapped(val target: FoodsTarget) : FoodsAction
}

sealed interface FoodsEvent {
    data class OpenLogger(val route: LoggerRoute) : FoodsEvent
    data object OpenCreateMeal : FoodsEvent
    data object OpenAddFood : FoodsEvent
}

/** Everything the tab reads, so [FoodsStateBuilder] is a pure function of it. */
data class FoodsInputs(
    val customFoods: List<CustomFood>,
    val savedMeals: List<SavedMeal>,
    val recent: List<LogItem>,
)

/**
 * The Foods tab's state from what it reads, so previews and the Blind leak test run the same
 * mapping as the app. Calories resolve on [Surface.Foods] with no goal: shown at Show, hidden
 * otherwise (a soft word needs a goal to measure against).
 */
object FoodsStateBuilder {

    fun build(inputs: FoodsInputs, policy: VisibilityPolicy): FoodsState {
        val bare = FormatContext(bare = true)
        fun calories(kcal: Double?) = policy.resolve(Metric.Calories, kcal, Surface.Foods, bare)
        return FoodsState(
            loading = false,
            savedMeals = inputs.savedMeals.map { meal ->
                FoodsRowUi(
                    key = "meal:${meal.id}",
                    target = FoodsTarget.Meal(meal.id),
                    name = meal.name,
                    sub = meal.items.joinToString(", ") { shortName(it.name) },
                    calories = calories(meal.totals.kcal.value),
                )
            },
            yourFoods = inputs.customFoods.map { food ->
                val ref = FoodRef.Custom(food.id)
                FoodsRowUi(
                    key = ref.key,
                    target = FoodsTarget.Food(ref),
                    name = food.name,
                    sub = listOfNotNull(food.brand?.takeIf { it.isNotBlank() }, food.toDraft().amountText).joinToString(SEPARATOR),
                    calories = calories(food.kcal),
                )
            },
            // A quick number or a name-only line has no food behind it, so there is nothing to pick again.
            recent = inputs.recent.mapNotNull { item ->
                val ref = item.toDraft().foodRef() ?: return@mapNotNull null
                FoodsRowUi(
                    key = ref.key,
                    target = FoodsTarget.Food(ref),
                    name = item.name,
                    sub = item.amountText.orEmpty(),
                    calories = calories(item.kcal),
                )
            }.distinctBy { it.key },
        )
    }

    private const val SEPARATOR = " · "
}
