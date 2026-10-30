package com.opson.libraries.foods

import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Recipe
import com.opson.libraries.diary.RecipeDraft
import com.opson.libraries.diary.RecipeSharing

/** Nutrients for an amount. Null is unknown, which is not zero. */
data class RecipeNutrients(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
)

/**
 * One ingredient of a shared recipe as the server serves it. [checked]: its numbers were worked
 * out from a food Opson checked, by its weight. Otherwise they are the person's own.
 */
data class PublicIngredient(
    val name: String,
    val amountText: String?,
    val grams: Double?,
    val nutrients: RecipeNutrients,
    val foodId: String?,
    val checked: Boolean,
)

/**
 * Someone's shared recipe. Made by a person and not checked by Opson, whatever its ingredients:
 * every screen that shows one says so. [by] is the person's display name when they chose to show
 * it; null reads "an Opson user".
 */
data class PublicRecipe(
    val code: String,
    val name: String,
    val servings: Double,
    val notes: String?,
    val by: String?,
    val ingredients: List<PublicIngredient>,
    val total: RecipeNutrients,
    val perServing: RecipeNutrients,
) {
    val checkedCount: Int get() = ingredients.count { it.checked }

    /** As a recipe of one's own: the server's numbers for each ingredient, and where it came from. */
    fun toDraft(): RecipeDraft = RecipeDraft(
        name = name,
        servings = servings,
        ingredients = ingredients.map { it.toLogDraft() },
        notes = notes,
        savedFrom = code,
    )

    /** [count] servings to log, exactly as a saved copy would log them. */
    fun servingDraft(count: Double = 1.0): LogDraft =
        Recipe(id = code, name = name, servings = servings, ingredients = ingredients.map { it.toLogDraft() }).servingDraft(count)

    private fun PublicIngredient.toLogDraft() = LogDraft(
        name = name,
        precision = if (grams != null) Precision.Weighed else Precision.Estimate,
        foodId = foodId,
        amountText = amountText,
        grams = grams,
        kcal = nutrients.kcal,
        protein = nutrients.protein,
        carbs = nutrients.carbs,
        fat = nutrients.fat,
        fiber = nutrients.fiber,
    )
}

sealed interface PublicRecipeLookup {
    data class Found(val recipe: PublicRecipe) : PublicRecipeLookup

    /** Its owner stopped sharing it, or Opson took it down. */
    data object NotShared : PublicRecipeLookup
    data object NotFound : PublicRecipeLookup
    data class Failed(val offline: Boolean) : PublicRecipeLookup
}

sealed interface PublishResult {
    data class Published(val sharing: RecipeSharing) : PublishResult

    /** Opson took this recipe down; it can't be shared again. */
    data object Removed : PublishResult

    /** This account can't add to Opson right now (moderation). */
    data object Restricted : PublishResult
    data object TooMany : PublishResult
    data class Failed(val offline: Boolean) : PublishResult
}

/** What someone says is wrong with a shared recipe. Wire values match the server's. */
enum class RecipeReportReason(val wire: String) {
    WrongNutrition("wrong_nutrition"),
    Unsafe("unsafe"),
    Offensive("offensive"),
    Spam("spam"),
    Other("other"),
}

sealed interface RecipeActionResult {
    data object Done : RecipeActionResult
    data object NotFound : RecipeActionResult
    data object Restricted : RecipeActionResult
    data object TooMany : RecipeActionResult
    data class Failed(val offline: Boolean) : RecipeActionResult
}

/**
 * Shared recipes, online only: sharing one of the person's recipes, stopping, and reading,
 * finding, reporting and counting a save of anyone's. Nothing here is queued; a call that could
 * not go says so and the person tries again.
 */
interface PublicRecipes {
    suspend fun get(code: String): PublicRecipeLookup

    /** Shared recipes whose name holds [query], best first. Empty when there are none or the server can't be reached. */
    suspend fun search(query: String): List<PublicRecipe>

    suspend fun publish(recipe: Recipe, showName: Boolean): PublishResult

    suspend fun unpublish(recipeId: String): RecipeActionResult

    /** [reportId] is made once, so a retry is the same report. */
    suspend fun report(code: String, reportId: String, reason: RecipeReportReason, note: String?): RecipeActionResult

    /** Someone saved it to their recipes. Best effort. */
    suspend fun countSave(code: String)
}
