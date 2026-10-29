package com.opson.features.foods.impl

import com.opson.features.logger.LoggerRoute
import com.opson.libraries.foods.ReviewStatus
import com.opson.libraries.navigation.Route
import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.RepeatRule
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
    /** `release.repeats`: the Repeating card, under Saved meals. */
    val repeatsOn: Boolean = false,
    val repeats: List<RepeatRowUi> = emptyList(),
    val savedMeals: List<FoodsRowUi> = emptyList(),
    val yourFoods: List<FoodsRowUi> = emptyList(),
    val recent: List<FoodsRowUi> = emptyList(),
)

/** What a Foods row opens the logger with. */
sealed interface FoodsTarget {
    data class Meal(val id: String) : FoodsTarget

    data class Food(val ref: FoodRef) : FoodsTarget

    /** A food opens its detail, a saved meal the + sheet on Saved meals; either way the person still chooses the meal. */
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
    /** One of the person's own foods sent to Opson's food database and not checked yet. */
    val unverified: Boolean = false,
)

sealed interface FoodsAction {
    data class InputsArrived(val inputs: FoodsInputs) : FoodsAction
    data class PolicyArrived(val policy: VisibilityPolicy) : FoodsAction
    data object SearchTapped : FoodsAction
    data object CreateMealTapped : FoodsAction
    data object AddFoodTapped : FoodsAction

    /** The row or its +: a food opens its detail, a saved meal the + sheet on Saved meals. */
    data class RowTapped(val target: FoodsTarget) : FoodsAction

    data object NewRepeatTapped : FoodsAction
    data class RepeatTapped(val id: String) : FoodsAction
}

sealed interface FoodsEvent {
    /** The + sheet with a row already chosen, or search. */
    data class OpenLogger(val route: Route) : FoodsEvent
    data object OpenCreateMeal : FoodsEvent
    data object OpenAddFood : FoodsEvent

    /** A repeat to edit, or a new one when [ruleId] is null. */
    data class OpenRepeat(val ruleId: String?) : FoodsEvent
}

/** Everything the tab reads, so [FoodsStateBuilder] is a pure function of it. */
data class FoodsInputs(
    val customFoods: List<CustomFood>,
    val savedMeals: List<SavedMeal>,
    val recent: List<LogItem>,
    /** Review status by custom food id, for the person's foods they sent to Opson's food database. */
    val reviews: Map<String, ReviewStatus> = emptyMap(),
    /** Null while `release.repeats` is off, so the card is not drawn at all. */
    val repeats: List<RepeatRule>? = null,
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
            repeatsOn = inputs.repeats != null,
            repeats = inputs.repeats.orEmpty().map { RepeatStateBuilder.row(it, inputs.savedMeals, policy) },
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
                    sub = listOfNotNull(food.brand?.takeIf { it.isNotBlank() }, food.toDraft().amountLabel.takeIf { it.isNotEmpty() }).joinToString(SEPARATOR),
                    calories = calories(food.kcal),
                    unverified = inputs.reviews[food.id] == ReviewStatus.Pending,
                )
            },
            // A quick number or a name-only line has no food behind it, so there is nothing to pick again.
            recent = inputs.recent.mapNotNull { item ->
                val ref = item.toDraft().foodRef() ?: return@mapNotNull null
                FoodsRowUi(
                    key = ref.key,
                    target = FoodsTarget.Food(ref),
                    name = item.name,
                    sub = item.amountLabel,
                    calories = calories(item.kcal),
                    unverified = ref is FoodRef.Custom && inputs.reviews[ref.id] == ReviewStatus.Pending,
                )
            }.distinctBy { it.key },
        )
    }

    private const val SEPARATOR = " · "
}
