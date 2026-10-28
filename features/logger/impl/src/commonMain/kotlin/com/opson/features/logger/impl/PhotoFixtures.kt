package com.opson.features.logger.impl

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.foods.Confidence
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.foods.NutrientProfile
import com.opson.libraries.foods.ParsedItem
import com.opson.libraries.foods.Portion
import com.opson.libraries.nutrition.VisibilityPolicy

/**
 * Photo estimate as design v3 draws it (3.7): grilled chicken, olive oil and
 * steamed broccoli, about 379 kcal, with the note that told it about the oil.
 * For previews and `CaloriesHiddenLeakTest`; states come from the real builder and
 * the real amount rules, never a hand-built `Display`.
 */
object PhotoFixtures {
    const val NOTE = "Palm of chicken, a drizzle of oil, big pile of broccoli, no butter"

    val chicken = Food(
        id = "chicken",
        source = FoodSource.UsdaFndds,
        name = "Chicken breast, grilled",
        per100g = NutrientProfile(kcal = 165.0, protein = 31.0, carbs = 0.0, fat = 3.6),
        portions = listOf(Portion("1 piece", 120.0)),
    )
    val oil = Food(
        id = "oil",
        source = FoodSource.UsdaSr,
        name = "Olive oil",
        per100g = NutrientProfile(kcal = 884.0, protein = 0.0, carbs = 0.0, fat = 100.0),
        portions = listOf(Portion("1 tbsp", 13.5)),
    )
    val broccoli = Food(
        id = "broccoli",
        source = FoodSource.UsdaFndds,
        name = "Broccoli, steamed",
        per100g = NutrientProfile(kcal = 20.0, protein = 2.4, carbs = 3.6, fat = 0.4),
        portions = listOf(Portion("1 cup", 156.0)),
    )
    val thigh = Food(
        id = "thigh",
        source = FoodSource.UsdaFndds,
        name = "Chicken thigh, grilled",
        per100g = NutrientProfile(kcal = 209.0, protein = 26.0, carbs = 0.0, fat = 10.9),
        portions = listOf(Portion("1 thigh", 116.0)),
    )

    val foods: Map<String, Food> = listOf(chicken, oil, broccoli, thigh).associateBy { it.id }

    /** What the server sends back for the photo: the Describe row shape. */
    val items: List<ParsedItem> = listOf(
        ParsedItem(
            text = "120 g grilled chicken breast", name = chicken.name, quantity = 120.0, amountText = "120 g",
            confidence = Confidence.High, foodId = chicken.id, unit = "g", grams = 120.0, kcal = 198.0,
        ),
        ParsedItem(
            text = "1 tbsp olive oil", name = oil.name, quantity = 1.0, amountText = "1 tbsp",
            confidence = Confidence.High, foodId = oil.id, unit = "tbsp", portionLabel = "1 tbsp", grams = 13.5, kcal = 119.3,
        ),
        ParsedItem(
            text = "2 cups steamed broccoli", name = broccoli.name, quantity = 2.0, amountText = "2 cup",
            confidence = Confidence.Medium, foodId = broccoli.id, unit = "cup", portionLabel = "1 cup", grams = 312.0, kcal = 62.4,
        ),
    )

    fun inputs(): PhotoInputs =
        PhotoInputs(today = LoggerFixtures.today, day = LoggerFixtures.day())

    fun rows(policy: VisibilityPolicy): List<PhotoRow> {
        val householdOnly = PhotoEstimateStateBuilder.householdOnly(policy)
        return items.mapIndexed { index, item -> PhotoRows.fromEstimate("photo:$index", item, foods[item.foodId], householdOnly) }
    }

    fun model(step: PhotoStep = PhotoStep.Start, note: String = ""): PhotoModel =
        PhotoModel(slot = MealSlot.Dinner, step = step, note = note)

    fun reviewModel(policy: VisibilityPolicy, editing: Boolean = false): PhotoModel = model(PhotoStep.Review, NOTE).copy(
        rows = rows(policy),
        usedNote = true,
        editing = if (editing) "photo:2" else null,
    )

    fun state(model: PhotoModel, policy: VisibilityPolicy): PhotoEstimateState =
        PhotoEstimateStateBuilder.build(model, inputs(), policy)

    fun startState(policy: VisibilityPolicy, note: String = ""): PhotoEstimateState = state(model(note = note), policy)

    fun estimatingState(policy: VisibilityPolicy): PhotoEstimateState = state(model(PhotoStep.Estimating, NOTE), policy)

    fun reviewState(policy: VisibilityPolicy, editing: Boolean = false): PhotoEstimateState =
        state(reviewModel(policy, editing), policy)

    /** Swapping the chicken, with the catalog's answer in. */
    fun swapState(policy: VisibilityPolicy): PhotoEstimateState {
        val query = "Chicken breast"
        val swap = PhotoSwap(replacing = "photo:0", query = query, results = SwapAnswer(query, listOf(chicken, thigh)))
        return state(reviewModel(policy).copy(swap = swap), policy)
    }

    fun calmState(step: PhotoStep, policy: VisibilityPolicy): PhotoEstimateState = state(model(step, NOTE), policy)
}
