package com.opson.features.foods.impl

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * Realistic Foods inputs for previews and `BlindModeLeakTest`, from design v3's Foods screens
 * (11.1 to 11.4). Real calorie values on purpose: Full must have something to show, or the
 * leak test's control proves nothing. Never builds a `Display`; states come from the real
 * builders.
 */
object FoodsFixtures {
    val today: LocalDate = LocalDate(2026, 10, 25)

    @Suppress("LongParameterList")
    private fun draft(name: String, amount: String, kcal: Double, protein: Double, carbs: Double, fat: Double, foodId: String? = null) =
        LogDraft(
            name = name,
            precision = Precision.Estimate,
            foodId = foodId,
            amountText = amount,
            kcal = kcal,
            protein = protein,
            carbs = carbs,
            fat = fat,
            method = LogMethod.Search,
        )

    val porridge = draft("Oat porridge with milk", "1 bowl", 300.0, 10.0, 50.0, 6.0, foodId = "porridge")
    val banana = draft("Banana", "1 medium", 105.0, 1.3, 27.0, 0.4, foodId = "banana")
    val coffee = draft("Coffee with milk", "1 mug", 40.0, 2.0, 3.0, 2.0, foodId = "coffee")
    val adobo = draft("Chicken adobo", "1 cup", 310.0, 28.0, 4.0, 20.0, foodId = "adobo")
    val rice = draft("Jasmine rice", "1 cup", 205.0, 4.3, 44.0, 0.4, foodId = "rice")
    val yogurt = draft("Greek yogurt", "1 package", 100.0, 17.0, 6.0, 0.7, foodId = "yogurt")
    val berries = draft("Berries", "1 cup", 70.0, 1.0, 17.0, 0.5, foodId = "berries")

    val savedMeals: List<SavedMeal> = listOf(
        SavedMeal(id = "usual-breakfast", name = "Usual breakfast", usualSlot = MealSlot.Breakfast, items = listOf(porridge, banana, coffee)),
        SavedMeal(id = "work-lunch", name = "Work lunch", usualSlot = MealSlot.Lunch, items = listOf(adobo, rice)),
        SavedMeal(id = "yogurt-fruit", name = "Yogurt and fruit", usualSlot = MealSlot.Snack, items = listOf(yogurt, berries)),
    )

    val pandesal = CustomFood(
        id = "pandesal",
        name = "Lola's pandesal",
        servingAmount = 1.0,
        servingUnit = "roll",
        brand = "Corner bakery",
        kcal = 120.0,
        protein = 4.0,
        carbs = 22.0,
        fat = 2.0,
    )

    val recent: List<LogItem> = listOf(rice, banana, coffee).mapIndexed { index, food ->
        LogItem(
            id = "recent-$index",
            day = today,
            slot = MealSlot.Lunch,
            loggedAt = Instant.parse("2026-10-25T12:00:00Z"),
            name = food.name,
            precision = food.precision,
            method = LogMethod.Search,
            foodId = food.foodId,
            amountText = food.amountText,
            kcal = food.kcal,
            protein = food.protein,
            carbs = food.carbs,
            fat = food.fat,
        )
    }

    fun foodsInputs(withRecent: Boolean = true, empty: Boolean = false): FoodsInputs = if (empty) {
        FoodsInputs(customFoods = emptyList(), savedMeals = emptyList(), recent = emptyList())
    } else {
        FoodsInputs(customFoods = listOf(pandesal), savedMeals = savedMeals, recent = if (withRecent) recent else emptyList())
    }

    fun foodsState(policy: VisibilityPolicy, withRecent: Boolean = true, empty: Boolean = false): FoodsState =
        FoodsStateBuilder.build(foodsInputs(withRecent, empty), policy)

    /** 11.2: "Usual breakfast" at breakfast, three foods. */
    fun mealDraft(empty: Boolean = false): MealDraft = if (empty) {
        MealDraft()
    } else {
        MealDraft(name = "Usual breakfast", usualSlot = MealSlot.Breakfast, items = listOf(porridge, banana, coffee))
    }

    fun createMealState(policy: VisibilityPolicy, empty: Boolean = false): CreateMealState =
        CreateMealStateBuilder.build(mealDraft(empty), policy)

    /** 11.3: Lola's pandesal from the corner bakery, a roll (here a piece), with its label filled in. */
    fun foodForm(): FoodForm = FoodForm(
        name = "Lola's pandesal",
        brand = "Corner bakery",
        servingAmount = "1",
        servingUnit = ServingUnit.Piece,
        nutrition = mapOf(Metric.Calories to "120", Metric.Protein to "4", Metric.Carbs to "22", Metric.Fat to "2"),
    )

    fun addFoodState(policy: VisibilityPolicy): AddFoodState = AddFoodStateBuilder.build(foodForm(), policy)
}
