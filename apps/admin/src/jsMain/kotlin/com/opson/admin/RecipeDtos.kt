package com.opson.admin

import kotlinx.serialization.Serializable

/*
 * The shared-recipe routes' wire format, mirrored from the server's
 * `routes/RecipeAdminRoutes.kt` and `routes/RecipeDto.kt`.
 */

@Serializable
data class RecipeNutrientsDto(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
)

@Serializable
data class RecipeIngredientDto(
    val name: String,
    val amountText: String? = null,
    val grams: Double? = null,
    val nutrients: RecipeNutrientsDto = RecipeNutrientsDto(),
    val foodId: String? = null,
    val checked: Boolean = false,
)

@Serializable
data class PublicRecipeDto(
    val code: String,
    val name: String,
    val servings: Double,
    val notes: String? = null,
    val by: String? = null,
    val ingredients: List<RecipeIngredientDto> = emptyList(),
    val total: RecipeNutrientsDto = RecipeNutrientsDto(),
    val perServing: RecipeNutrientsDto = RecipeNutrientsDto(),
    val checkedCount: Int = 0,
    val publishedAtEpochMs: Long? = null,
    val updatedAtEpochMs: Long = 0,
)

@Serializable
data class AdminRecipeSummaryDto(
    val id: String,
    val code: String? = null,
    val name: String,
    val ownerId: String,
    val ownerName: String? = null,
    val showName: Boolean = false,
    val state: String,
    val servings: Double,
    val ingredientCount: Int = 0,
    val openReports: Int = 0,
    val reasons: Map<String, Int> = emptyMap(),
    val oldestOpenReportAtEpochMs: Long? = null,
    val publishedAtEpochMs: Long? = null,
    val updatedAtEpochMs: Long = 0,
    val saveCount: Int = 0,
    val removedReason: String? = null,
)

@Serializable
data class AdminRecipeListDto(val filter: String, val recipes: List<AdminRecipeSummaryDto> = emptyList())

@Serializable
data class AdminRecipeReportDto(
    val id: String,
    val reason: String,
    val note: String? = null,
    val createdAtEpochMs: Long,
    val status: String,
)

@Serializable
data class AdminRecipeDetailDto(
    val id: String,
    val ownerId: String,
    val ownerName: String? = null,
    val state: String,
    val notes: String? = null,
    val recipe: PublicRecipeDto,
    val reports: List<AdminRecipeReportDto> = emptyList(),
    val removedReason: String? = null,
)

@Serializable
data class RemoveRecipeDto(val reason: String)

@Serializable
data class RecipeModerationDto(val changed: Boolean, val reports: Int = 0)

/** What the Shared recipes table and panel say, kept apart from the DOM so tests can read it. */
object RecipeReview {
    val FILTERS = listOf("reported" to "Reported", "hidden" to "Hidden", "live" to "Shared", "removed" to "Removed")

    fun reason(wire: String): String = REASONS[wire] ?: wire

    /** "2 unsafe, 1 spam", most first. */
    fun reasons(counts: Map<String, Int>): String =
        counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .joinToString(", ") { (wire, count) -> "$count ${reason(wire).lowercase()}" }

    fun state(wire: String): String = when (wire) {
        "live" -> "Shared"
        "hidden" -> "Hidden, waiting for a look"
        "removed" -> "Removed"
        "private" -> "Not shared"
        else -> wire
    }

    /** Remove takes it down; Restore brings a hidden one back, or lets a removed one be shared again. */
    fun canRemove(state: String): Boolean = state == "live" || state == "hidden"

    fun canRestore(state: String): Boolean = state == "hidden" || state == "removed"

    fun link(code: String?): String? = code?.let { "https://www.opsontracker.com/r/$it" }

    private val REASONS = mapOf(
        "wrong_nutrition" to "Wrong numbers",
        "unsafe" to "Unsafe eating",
        "offensive" to "Offensive",
        "spam" to "Spam",
        "other" to "Other",
    )
}
