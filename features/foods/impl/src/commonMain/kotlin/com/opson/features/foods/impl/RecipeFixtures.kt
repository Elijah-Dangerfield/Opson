package com.opson.features.foods.impl

import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Recipe
import com.opson.libraries.diary.RecipeLinks
import com.opson.libraries.diary.RecipeShareState
import com.opson.libraries.diary.RecipeSharing
import com.opson.libraries.foods.PublicIngredient
import com.opson.libraries.foods.PublicRecipe
import com.opson.libraries.foods.RecipeNutrients
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.qr.QrCode

/** Recipes for previews and tests: a weeknight chili for six, its shared copy, and someone else's oats. */
object RecipeFixtures {

    private fun ingredient(name: String, amount: String, grams: Double, kcal: Double, protein: Double, carbs: Double, fat: Double, foodId: String? = null) =
        LogDraft(
            name = name, precision = Precision.Weighed, foodId = foodId, amountText = amount, grams = grams,
            kcal = kcal, protein = protein, carbs = carbs, fat = fat,
        )

    val chiliIngredients = listOf(
        ingredient("Black beans, canned", "2 cans", 800.0, 728.0, 48.0, 133.0, 2.4, foodId = "f-beans"),
        ingredient("Ground beef, 90% lean", "1 lb", 454.0, 798.0, 90.0, 0.0, 45.0, foodId = "f-beef"),
        ingredient("Crushed tomatoes", "1 can", 794.0, 254.0, 13.0, 56.0, 2.0),
        ingredient("Onion, yellow", "1 large", 150.0, 60.0, 1.6, 14.0, 0.2, foodId = "f-onion"),
    )

    val chili = Recipe(
        id = "recipe-chili",
        name = "Weeknight chili",
        servings = 6.0,
        ingredients = chiliIngredients,
        notes = "Brown the beef with the onion, add the rest and simmer for 40 minutes. Better the next day.",
    )

    val sharedChili = chili.copy(sharing = RecipeSharing("k7m2p9qx", RecipeShareState.Live, showName = false))

    val oats = PublicRecipe(
        code = "abcd2345",
        name = "Overnight oats",
        servings = 2.0,
        notes = "Stir together the night before. Top with berries in the morning.",
        by = "Sam",
        ingredients = listOf(
            PublicIngredient("Rolled oats", "1 cup", 81.0, RecipeNutrients(307.0, 10.7, 54.8, 5.3, 8.2), "f-oats", checked = true),
            PublicIngredient("Milk, 2%", "1 cup", 244.0, RecipeNutrients(122.0, 8.1, 11.7, 4.8, 0.0), "f-milk", checked = true),
            PublicIngredient("Chia seeds", "2 tbsp", 24.0, RecipeNutrients(117.0, 4.0, 10.0, 7.4, 8.3), null, checked = false),
        ),
        total = RecipeNutrients(546.0, 22.8, 76.5, 17.5, 16.5),
        perServing = RecipeNutrients(273.0, 11.4, 38.3, 8.8, 8.3),
    )

    fun editState(policy: VisibilityPolicy, empty: Boolean = false, editing: Boolean = false): RecipeEditState =
        RecipeEditStateBuilder.build(
            if (empty) RecipeDraftUi() else RecipeDraftUi(chili.name, chili.servings, chili.ingredients, chili.notes.orEmpty()),
            policy,
            editing = editing,
        )

    fun viewState(policy: VisibilityPolicy, recipe: Recipe = chili): RecipeViewState = RecipeViewStateBuilder.build(recipe, policy)

    fun publicState(policy: VisibilityPolicy, mine: Boolean = false): PublicRecipeState.Loaded =
        PublicRecipeStateBuilder.build(oats, policy, mineId = if (mine) "recipe-oats" else null)

    fun consentState(displayName: String? = "Elijah", showName: Boolean = false) = ShareSheetState.Consent(
        name = chili.name,
        ingredientCount = chili.ingredients.size,
        hasNotes = true,
        displayName = displayName,
        showName = showName,
    )

    fun sharedState(hidden: Boolean = false): ShareSheetState.Shared {
        val link = RecipeLinks.web("k7m2p9qx")
        return ShareSheetState.Shared(name = chili.name, link = link, qr = QrCode.encode(link), hidden = hidden)
    }

    fun logState(policy: VisibilityPolicy): LogRecipeState {
        val draft = chili.servingDraft()
        return LogRecipeState(
            loading = false,
            name = chili.name,
            amount = draft.amountLabel,
            canFewer = true,
            numbers = RecipeNumbers.amounts(draft.kcal, draft.protein, draft.carbs, draft.fat, draft.fiber, policy),
            slot = MealSlot.Dinner,
        )
    }

    fun recipes(): List<Recipe> = listOf(
        sharedChili,
        Recipe(
            id = "recipe-oats",
            name = "Overnight oats",
            servings = 2.0,
            ingredients = oats.toDraft().ingredients,
            savedFrom = oats.code,
        ),
    )
}
