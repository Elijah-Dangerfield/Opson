package com.opson.features.foods.impl

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.foods.NutrientProfile
import com.opson.libraries.foods.Portion
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

    /** The snack's peanut butter on Today, 2 tbsp weighed from the catalog: what 13.3e copies. */
    val peanutButter = LogItem(
        id = "pb-$today",
        day = today,
        slot = MealSlot.Snack,
        loggedAt = Instant.parse("2026-10-25T15:31:00Z"),
        name = "Peanut butter",
        precision = Precision.Weighed,
        method = LogMethod.Search,
        foodId = "peanut-butter",
        amountText = "2 tbsp",
        quantity = 2.0,
        unit = "tbsp",
        grams = 32.0,
        kcal = 190.0,
        protein = 8.0,
        carbs = 7.0,
        fat = 16.0,
        fiber = 2.0,
    )

    /** The copy named, its brand given, and calories and protein changed from 190 and 8 g. */
    fun editCopyState(policy: VisibilityPolicy, changed: Boolean = true): EditCopyState {
        val seed = EditCopyStateBuilder.seed(peanutButter, brand = null, isToday = true)
        val opened = EditCopyStateBuilder.form(seed, mine = "(mine)", policy = policy)
        val form = if (changed) {
            opened.copy(
                brand = "Store brand",
                values = opened.values + listOf(CopyNutrient.KCAL to "180", CopyNutrient.PROTEIN to "7").filter { it.first in opened.values },
            )
        } else {
            opened
        }
        return EditCopyStateBuilder.build(seed, form, policy)
    }

    /** A hazelnut creamer the person made, counted in tablespoons and never weighed. */
    val creamer = CustomFood(
        id = "creamer",
        name = "Hazelnut creamer",
        servingAmount = 1.0,
        servingUnit = "tbsp",
        brand = "Coffee aisle",
        kcal = 35.0,
        carbs = 5.0,
        fat = 1.5,
        micros = mapOf("sugars_g" to 5.0, "sodium_mg" to 5.0, "saturated_fat_g" to 1.0),
    )

    /**
     * The owner's creamer: copied from its detail, a tablespoon weighed at 15 g, and a "cup" of
     * 240 g added, so it logs by weight. [weighed] false is the copy as it opens.
     */
    fun creamerCopyState(policy: VisibilityPolicy, weighed: Boolean = true): EditCopyState {
        val seed = EditCopyStateBuilder.seed(creamer, saved = emptyList())
        val opened = EditCopyStateBuilder.form(seed, mine = "(mine)", policy = policy)
        val form = if (weighed) {
            opened.copy(
                servings = listOf(opened.servings.first().copy(grams = "15"), ServingRow("1 cup", "240", removable = true)),
            )
        } else {
            opened
        }
        return EditCopyStateBuilder.build(seed, form, policy)
    }

    /** Peanut butter from the catalog, per 100 g: the whole label, vitamins and minerals, and a 4/4/9 gap to fix. */
    val peanutButterFood = Food(
        id = "peanut-butter",
        source = FoodSource.UsdaSr,
        name = "Peanut butter, smooth",
        per100g = NutrientProfile(kcal = 588.0, protein = 25.0, carbs = 20.0, fat = 50.0, fiber = 6.0),
        micros = mapOf(
            "saturated_fat_g" to 10.0, "sodium_mg" to 459.0, "sugars_g" to 9.2, "vitamin_e_mg" to 9.1, "niacin_mg" to 13.1,
            "magnesium_mg" to 168.0, "potassium_mg" to 558.0, "zinc_mg" to 2.5, "caffeine_mg" to 0.0,
        ),
        portions = listOf(Portion("2 tbsp (32g)", 32.0), Portion("1 cup", 258.0)),
    )

    fun catalogCopyState(policy: VisibilityPolicy): EditCopyState {
        val seed = EditCopyStateBuilder.seed(peanutButterFood, saved = emptyList())
        val opened = EditCopyStateBuilder.form(seed, mine = "(mine)", policy = policy)
        val per100 = EditCopyStateBuilder.perOptions(opened).indexOfFirst { it.row == CopyForm.PER_100_G }
        val form = EditCopyStateBuilder.rePer(opened, EditCopyStateBuilder.perOptions(opened)[per100].row)
        // Calories typed off a label that disagrees with its own macros, where the mode has the field.
        val typed = if (CopyNutrient.KCAL in form.values) form.copy(values = form.values + (CopyNutrient.KCAL to "400")) else form
        return EditCopyStateBuilder.build(seed, typed, policy)
    }
}
