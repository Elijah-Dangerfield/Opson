package com.opson.features.foods.impl

import com.opson.libraries.diary.HouseholdAmount
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.Recipe
import com.opson.libraries.diary.RecipeShareState
import com.opson.libraries.foods.PublicIngredient
import com.opson.libraries.foods.RecipeNutrients
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.MacroPart
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NutrientAmounts
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.macroLine

/** One ingredient row: "Black beans", "2 cans", "728". [checked] only on someone's shared recipe. */
data class IngredientUi(
    val name: String,
    val amount: String,
    val calories: Display,
    val checked: Boolean = false,
)

/** "445 kcal" and "13P 80C 8F" for an amount; the macros are empty when the mode shows none. */
data class AmountNumbersUi(
    val calories: Display = Display.Hidden,
    val macros: List<MacroPart> = emptyList(),
)

/** A recipe on the Foods tab: "Weeknight chili", "Makes 6 servings · 4 ingredients", "312". */
data class RecipeRowUi(
    val id: String,
    val name: String,
    val servings: String,
    val ingredientCount: Int,
    val perServing: Display,
    val shared: Boolean,
)

/**
 * Recipe numbers through the visibility policy, on [Surface.Foods] with no goal, as every Foods
 * screen resolves them. A recipe's numbers are an estimate, but the policy decides what shows, not
 * the recipe.
 */
object RecipeNumbers {

    /** "6 servings", "1½ servings". */
    fun servings(count: Double): String = HouseholdAmount.of(count, Recipe.SERVING)

    fun amounts(kcal: Double?, protein: Double?, carbs: Double?, fat: Double?, fiber: Double?, policy: VisibilityPolicy): AmountNumbersUi {
        // A recipe of name-only ingredients has no macros to show; a line of zeros would say something false.
        val anyMacros = protein != null || carbs != null || fat != null
        val amounts = NutrientAmounts(kcal = kcal, protein = protein ?: 0.0, carbs = carbs ?: 0.0, fat = fat ?: 0.0, fiber = fiber ?: 0.0)
        return AmountNumbersUi(
            calories = policy.resolve(Metric.Calories, kcal, Surface.Foods),
            macros = if (anyMacros) policy.macroLine(amounts, Surface.Foods, style = MacroLineStyle.Compact) else emptyList(),
        )
    }

    fun perServing(recipe: Recipe, policy: VisibilityPolicy): AmountNumbersUi = recipe.servingDraft().let {
        amounts(it.kcal, it.protein, it.carbs, it.fat, it.fiber, policy)
    }

    fun whole(recipe: Recipe, policy: VisibilityPolicy): AmountNumbersUi = recipe.totals.let {
        amounts(it.kcal.value, it.protein.value, it.carbs.value, it.fat.value, it.fiber.value, policy)
    }

    fun of(nutrients: RecipeNutrients, policy: VisibilityPolicy): AmountNumbersUi =
        amounts(nutrients.kcal, nutrients.protein, nutrients.carbs, nutrients.fat, nutrients.fiber, policy)

    fun ingredient(draft: LogDraft, policy: VisibilityPolicy): IngredientUi = IngredientUi(
        name = draft.name,
        amount = draft.amountLabel,
        calories = policy.resolve(Metric.Calories, draft.kcal, Surface.Foods, FormatContext(bare = true)),
    )

    fun ingredient(ingredient: PublicIngredient, policy: VisibilityPolicy): IngredientUi = IngredientUi(
        name = ingredient.name,
        amount = ingredient.amountText?.let(HouseholdAmount::display).orEmpty(),
        calories = policy.resolve(Metric.Calories, ingredient.nutrients.kcal, Surface.Foods, FormatContext(bare = true)),
        checked = ingredient.checked,
    )

    fun row(recipe: Recipe, policy: VisibilityPolicy): RecipeRowUi = RecipeRowUi(
        id = recipe.id,
        name = recipe.name,
        servings = servings(recipe.servings),
        ingredientCount = recipe.ingredients.size,
        perServing = policy.resolve(Metric.Calories, recipe.servingDraft().kcal, Surface.Foods, FormatContext(bare = true)),
        shared = recipe.sharing.code != null && recipe.sharing.state == RecipeShareState.Live,
    )
}
