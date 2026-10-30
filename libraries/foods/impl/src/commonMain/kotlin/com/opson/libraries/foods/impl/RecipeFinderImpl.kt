package com.opson.libraries.foods.impl

import com.opson.libraries.diary.RecipeRepository
import com.opson.libraries.diary.RecipesRelease
import com.opson.libraries.foods.PublicRecipes
import com.opson.libraries.foods.RecipeFinder
import com.opson.libraries.foods.RecipeHit
import kotlinx.coroutines.flow.first
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/**
 * The person's own recipes whose name holds the query, by name, then the server's shared ones.
 * A shared recipe the person already has (theirs, or saved from it) shows once, as theirs.
 */
@ContributesBinding(AppScope::class)
@Inject
class RecipeFinderImpl(
    private val recipes: RecipeRepository,
    private val publicRecipes: PublicRecipes,
    private val release: RecipesRelease,
) : RecipeFinder {

    override fun isOn(): Boolean = release.isOn()

    override suspend fun find(query: String): List<RecipeHit> {
        val q = query.trim()
        if (!isOn() || q.length < MIN_QUERY) return emptyList()
        val all = recipes.observeAll().first()
        val own = all.filter { it.name.contains(q, ignoreCase = true) }.take(MAX_OWN)
        val have = all.flatMap { listOfNotNull(it.sharing.code, it.savedFrom) }.toSet()
        val shared = publicRecipes.search(q).filter { it.code !in have }.take(MAX_SHARED)
        return own.map { RecipeHit(it.name, it.servingDraft().kcal, recipeId = it.id) } +
            shared.map { RecipeHit(it.name, it.perServing.kcal, code = it.code) }
    }

    private companion object {
        const val MIN_QUERY = 2
        const val MAX_OWN = 3
        const val MAX_SHARED = 5
    }
}
