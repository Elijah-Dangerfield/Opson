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
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodCheck

/** The Foods tab (design v3, 11.1). Built by [FoodsStateBuilder]; every number in it is policy-resolved. */
data class FoodsState(
    val loading: Boolean = true,
    /** `release.repeats`: the Repeating card, under Saved meals. */
    val repeatsOn: Boolean = false,
    /** An active Builder (`release.builders`): Verify a food, under the tiles, in the Builders color. */
    val verifyOn: Boolean = false,
    /** The foods starred on their detail, newest star first. */
    val favorites: List<FoodsRowUi> = emptyList(),
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
    /** A catalog food a person checked against its package: the Verified badge. */
    val verified: Boolean = false,
)

sealed interface FoodsAction {
    data class InputsArrived(val inputs: FoodsInputs) : FoodsAction
    data class PolicyArrived(val policy: VisibilityPolicy) : FoodsAction
    data object SearchTapped : FoodsAction
    data object CreateMealTapped : FoodsAction
    data object AddFoodTapped : FoodsAction
    data object VerifyTapped : FoodsAction

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

    /** A Builder's Verify a food: the scanner reading labels. [dayIso] is today, where a food goes if they log it instead. */
    data class OpenVerify(val dayIso: String) : FoodsEvent

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
    /** Starred food keys ([FoodRef.key]), newest first, from the person's settings. */
    val favoriteKeys: List<String> = emptyList(),
    /** Catalog foods the device keeps, by id: favorites and the ones picked lately, for names, amounts and badges. */
    val catalog: Map<String, Food> = emptyMap(),
    /** What was logged lately, newest first: a favorite shows the amount it was last logged at. */
    val history: List<LogItem> = emptyList(),
    val verifyOn: Boolean = false,
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
            verifyOn = inputs.verifyOn,
            favorites = favorites(inputs, ::calories),
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
                    verified = ref is FoodRef.Catalog && inputs.catalog[ref.id]?.check == FoodCheck.Verified,
                )
            }.distinctBy { it.key },
        )
    }

    /**
     * The starred foods, newest star first. A food logged before reads as it was last logged; a
     * catalog food never logged reads at its first serving (or 100 g); the person's own food as
     * Your foods has it. A star whose food the device does not have yet waits until it does.
     */
    private fun favorites(inputs: FoodsInputs, calories: (Double?) -> Display): List<FoodsRowUi> {
        val logged = inputs.history.mapNotNull { item -> item.toDraft().foodRef()?.let { it.key to item } }.distinctBy { it.first }.toMap()
        val own = inputs.customFoods.associateBy { FoodRef.Custom(it.id).key }
        return inputs.favoriteKeys.distinct().mapNotNull { key ->
            val custom = own[key]
            val catalogFood = key.removePrefix(CATALOG_PREFIX).takeIf { key.startsWith(CATALOG_PREFIX) }?.let { inputs.catalog[it] }
            val item = logged[key]
            when {
                custom != null -> FoodsRowUi(
                    key = key,
                    target = FoodsTarget.Food(FoodRef.Custom(custom.id)),
                    name = custom.name,
                    sub = listOfNotNull(custom.brand?.takeIf { it.isNotBlank() }, custom.toDraft().amountLabel.takeIf { it.isNotEmpty() }).joinToString(SEPARATOR),
                    calories = calories(custom.kcal),
                    unverified = inputs.reviews[custom.id] == ReviewStatus.Pending,
                )
                item != null -> FoodsRowUi(
                    key = key,
                    target = FoodsTarget.Food(item.toDraft().foodRef() ?: return@mapNotNull null),
                    name = item.name,
                    sub = item.amountLabel,
                    calories = calories(item.kcal),
                    verified = catalogFood?.check == FoodCheck.Verified,
                )
                catalogFood != null -> {
                    val portion = catalogFood.portions.firstOrNull()
                    val grams = portion?.grams ?: HUNDRED_GRAMS
                    FoodsRowUi(
                        key = key,
                        target = FoodsTarget.Food(FoodRef.Catalog(catalogFood.id)),
                        name = catalogFood.name,
                        sub = listOfNotNull(catalogFood.brand?.takeIf { it.isNotBlank() }, portion?.label).joinToString(SEPARATOR),
                        calories = calories(catalogFood.per100g.kcal?.let { it * grams / HUNDRED_GRAMS }),
                        verified = catalogFood.check == FoodCheck.Verified,
                    )
                }
                else -> null
            }
        }
    }

    private const val CATALOG_PREFIX = "f:"
    private const val HUNDRED_GRAMS = 100.0

    private const val SEPARATOR = " · "
}
