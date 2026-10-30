package com.opson.features.foods.impl

import com.opson.libraries.diary.Recipe
import com.opson.libraries.diary.RecipeDraft
import com.opson.libraries.diary.RecipeRepository
import com.opson.libraries.diary.RecipeSharing
import com.opson.libraries.foods.PublicRecipe
import com.opson.libraries.foods.PublicRecipeLookup
import com.opson.libraries.foods.PublicRecipes
import com.opson.libraries.foods.PublishResult
import com.opson.libraries.foods.RecipeActionResult
import com.opson.libraries.foods.RecipeReportReason
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.CameraPermissions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Recipes in memory, with the sharing each was last marked with. */
class FakeRecipeRepository(recipes: List<Recipe> = emptyList()) : RecipeRepository {
    val recipes = MutableStateFlow(recipes.associateBy { it.id })
    private var next = 0

    override fun observeAll(): Flow<List<Recipe>> = recipes.map { it.values.sortedBy { recipe -> recipe.name.lowercase() } }

    override fun observe(id: String): Flow<Recipe?> = recipes.map { it[id] }

    override suspend fun get(id: String): Recipe? = recipes.value[id]

    override suspend fun byShareCode(code: String): Recipe? = recipes.value.values.firstOrNull { it.sharing.code == code }

    override suspend fun create(draft: RecipeDraft): Recipe {
        val recipe = Recipe("new-${next++}", draft.name.trim(), draft.servings, draft.ingredients, draft.notes, draft.savedFrom)
        recipes.value += recipe.id to recipe
        return recipe
    }

    override suspend fun update(id: String, draft: RecipeDraft) {
        val existing = recipes.value[id] ?: return
        recipes.value += id to existing.copy(name = draft.name.trim(), servings = draft.servings, ingredients = draft.ingredients, notes = draft.notes)
    }

    override suspend fun delete(id: String) {
        recipes.value -= id
    }

    override suspend fun markShared(id: String, sharing: RecipeSharing) {
        val existing = recipes.value[id] ?: return
        recipes.value += id to existing.copy(sharing = sharing)
    }
}

/** The recipe routes, answering what each test sets. */
class FakePublicRecipes : PublicRecipes {
    var lookup: PublicRecipeLookup = PublicRecipeLookup.NotFound
    var publishResult: PublishResult = PublishResult.Failed(offline = true)
    var unpublishResult: RecipeActionResult = RecipeActionResult.Done
    var reportResult: RecipeActionResult = RecipeActionResult.Done
    var found: List<PublicRecipe> = emptyList()
    val published = mutableListOf<Pair<Recipe, Boolean>>()
    val unpublished = mutableListOf<String>()
    val reports = mutableListOf<Triple<String, String, RecipeReportReason>>()
    val saves = mutableListOf<String>()

    override suspend fun get(code: String): PublicRecipeLookup = lookup

    override suspend fun search(query: String): List<PublicRecipe> = found

    override suspend fun publish(recipe: Recipe, showName: Boolean): PublishResult {
        published += recipe to showName
        return publishResult
    }

    override suspend fun unpublish(recipeId: String): RecipeActionResult {
        unpublished += recipeId
        return unpublishResult
    }

    override suspend fun report(code: String, reportId: String, reason: RecipeReportReason, note: String?): RecipeActionResult {
        reports += Triple(code, reportId, reason)
        return reportResult
    }

    override suspend fun countSave(code: String) {
        saves += code
    }
}

class FakeCameraPermissions(var permission: CameraPermission = CameraPermission.Granted) : CameraPermissions {
    var settingsOpened = 0

    override fun current(): CameraPermission = permission

    override fun openSettings() {
        settingsOpened++
    }
}
