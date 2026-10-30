package com.opson.server.routes

import com.opson.server.domain.Nutrients
import com.opson.server.domain.PublicIngredient
import com.opson.server.domain.PublicRecipe
import kotlinx.serialization.Serializable

/*
 * Wire format for the recipe routes (`RecipeRoutes`), camelCase like the rest of the API. The
 * app mirrors these in `:libraries:foods:impl` (`HttpPublicRecipes`), and the website's recipe
 * page reads [PublicRecipeResponse].
 */

/** `POST /v1/recipes/{id}/publish`: the recipe as the app has it, and whether to show the owner's name. */
@Serializable
data class PublishRecipeRequest(
    val recipe: RecipeRowDto? = null,
    val showName: Boolean = false,
)

/** [state] is `live`, or `hidden` while reports on it wait for a person. */
@Serializable
data class PublishRecipeResponse(val code: String, val state: String)

/** Null is unknown, which is not zero. */
@Serializable
data class RecipeNutrientsJson(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
)

/** One ingredient. [checked]: worked out from a verified catalog food by its weight. */
@Serializable
data class PublicIngredientJson(
    val name: String,
    val amountText: String? = null,
    val grams: Double? = null,
    val nutrients: RecipeNutrientsJson,
    val foodId: String? = null,
    val checked: Boolean = false,
)

/** A shared recipe. [by] is the owner's display name when they chose to show it. */
@Serializable
data class PublicRecipeResponse(
    val code: String,
    val name: String,
    val servings: Double,
    val notes: String? = null,
    val by: String? = null,
    val ingredients: List<PublicIngredientJson>,
    val total: RecipeNutrientsJson,
    val perServing: RecipeNutrientsJson,
    val checkedCount: Int,
    val publishedAtEpochMs: Long? = null,
    val updatedAtEpochMs: Long,
)

@Serializable
data class RecipeSearchResponse(val query: String, val recipes: List<PublicRecipeResponse>)

@Serializable
data class RecipeReportRequest(
    /** Made once by the app, so a retry is the same report. */
    val id: String? = null,
    /** `wrong_nutrition`, `unsafe`, `offensive`, `spam` or `other`. */
    val reason: String? = null,
    val note: String? = null,
)

fun Nutrients.toJson(): RecipeNutrientsJson = RecipeNutrientsJson(kcal, protein, carbs, fat, fiber)

fun PublicIngredient.toJson(): PublicIngredientJson = PublicIngredientJson(
    name = name,
    amountText = amountText,
    grams = grams,
    nutrients = nutrients.toJson(),
    foodId = foodId?.toString(),
    checked = checked,
)

fun PublicRecipe.toResponse(): PublicRecipeResponse = PublicRecipeResponse(
    code = code,
    name = name,
    servings = servings,
    notes = notes,
    by = by,
    ingredients = ingredients.map { it.toJson() },
    total = total.toJson(),
    perServing = perServing.toJson(),
    checkedCount = ingredients.count { it.checked },
    publishedAtEpochMs = publishedAt?.toEpochMilliseconds(),
    updatedAtEpochMs = updatedAt.toEpochMilliseconds(),
)
