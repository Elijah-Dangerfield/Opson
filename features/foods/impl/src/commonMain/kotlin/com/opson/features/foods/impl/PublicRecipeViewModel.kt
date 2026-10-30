package com.opson.features.foods.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.foods.PublicRecipeRoute
import com.opson.libraries.diary.RecipeRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.PublicRecipe
import com.opson.libraries.foods.PublicRecipeLookup
import com.opson.libraries.foods.PublicRecipes
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.recipes_public_saved

/** Someone's shared recipe, or why it can't be shown. */
sealed interface PublicRecipeState {
    data object Loading : PublicRecipeState

    data class Missing(val why: Why) : PublicRecipeState {
        enum class Why { NotShared, NotFound, Offline, Failed }
    }

    /**
     * [by] is the maker's display name when they show it. [checkedCount] of [ingredients] came from
     * foods Opson checked. [mineId] is set when this is the person's own recipe, [savedId] when
     * they already saved a copy of it ([com.opson.libraries.diary.Recipe.savedFrom]).
     */
    data class Loaded(
        val name: String,
        val by: String?,
        val servings: String,
        val perServing: AmountNumbersUi,
        val ingredients: List<IngredientUi>,
        val checkedCount: Int,
        val notes: String?,
        val mineId: String? = null,
        val savedId: String? = null,
        val saving: Boolean = false,
    ) : PublicRecipeState
}

sealed interface PublicRecipeAction {
    data class Arrived(val lookup: PublicRecipeLookup, val mineId: String?, val savedId: String? = null) : PublicRecipeAction
    data class PolicyArrived(val policy: VisibilityPolicy) : PublicRecipeAction
    data object RetryTapped : PublicRecipeAction
    data object LogTapped : PublicRecipeAction
    data object SaveTapped : PublicRecipeAction
    data object OpenMineTapped : PublicRecipeAction
    data object ReportTapped : PublicRecipeAction
    data object BackTapped : PublicRecipeAction
}

sealed interface PublicRecipeEvent {
    data class OpenLog(val code: String) : PublicRecipeEvent

    /** The person's own copy, in this page's place. */
    data class OpenRecipe(val recipeId: String) : PublicRecipeEvent
    data class OpenReport(val code: String, val name: String) : PublicRecipeEvent
    data class ShowToast(val toast: Toast) : PublicRecipeEvent
    data object Close : PublicRecipeEvent
}

object PublicRecipeStateBuilder {
    fun build(recipe: PublicRecipe, policy: VisibilityPolicy, mineId: String?, savedId: String? = null): PublicRecipeState.Loaded = PublicRecipeState.Loaded(
        name = recipe.name,
        by = recipe.by,
        servings = RecipeNumbers.servings(recipe.servings),
        perServing = RecipeNumbers.of(recipe.perServing, policy),
        ingredients = recipe.ingredients.map { RecipeNumbers.ingredient(it, policy) },
        checkedCount = recipe.checkedCount,
        notes = recipe.notes?.takeIf { it.isNotBlank() },
        mineId = mineId,
        savedId = savedId,
    )
}

/**
 * A shared recipe, read from the server each time it opens: a link or a QR code can be old, and
 * the server says whether it is still shared. Saving makes the person's own copy, marked as
 * someone else's.
 */
@Inject
class PublicRecipeViewModel(
    @Assisted private val route: PublicRecipeRoute,
    private val publicRecipes: PublicRecipes,
    private val recipes: RecipeRepository,
    policySource: VisibilityPolicySource,
) : SEAViewModel<PublicRecipeState, PublicRecipeEvent, PublicRecipeAction>(initialStateArg = PublicRecipeState.Loading) {

    private var policy: VisibilityPolicy = policySource.policy.value
    private var recipe: PublicRecipe? = null
    private var saved = false

    init {
        load()
        viewModelScope.launch {
            policySource.policy.collect { takeAction(PublicRecipeAction.PolicyArrived(it)) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val lookup = publicRecipes.get(route.code)
            val mine = recipes.byShareCode(route.code)?.id
            // A copy saved before: opening the link again offers it rather than a second copy.
            val saved = recipes.observeAll().first().firstOrNull { it.savedFrom == route.code }?.id
            takeAction(PublicRecipeAction.Arrived(lookup, mine, saved))
        }
    }

    override suspend fun handleAction(action: PublicRecipeAction) {
        when (action) {
            is PublicRecipeAction.Arrived -> action.updateState {
                when (val lookup = action.lookup) {
                    is PublicRecipeLookup.Found -> {
                        recipe = lookup.recipe
                        PublicRecipeStateBuilder.build(lookup.recipe, policy, action.mineId, action.savedId)
                    }
                    PublicRecipeLookup.NotShared -> PublicRecipeState.Missing(PublicRecipeState.Missing.Why.NotShared)
                    PublicRecipeLookup.NotFound -> PublicRecipeState.Missing(PublicRecipeState.Missing.Why.NotFound)
                    is PublicRecipeLookup.Failed -> PublicRecipeState.Missing(
                        if (lookup.offline) PublicRecipeState.Missing.Why.Offline else PublicRecipeState.Missing.Why.Failed,
                    )
                }
            }
            is PublicRecipeAction.PolicyArrived -> {
                policy = action.policy
                val current = recipe ?: return
                action.updateState { state ->
                    (state as? PublicRecipeState.Loaded)?.let { PublicRecipeStateBuilder.build(current, action.policy, it.mineId, it.savedId).copy(saving = it.saving) } ?: state
                }
            }
            PublicRecipeAction.RetryTapped -> {
                action.updateState { PublicRecipeState.Loading }
                load()
            }
            PublicRecipeAction.LogTapped -> sendEvent(PublicRecipeEvent.OpenLog(route.code))
            PublicRecipeAction.SaveTapped -> save(action)
            PublicRecipeAction.OpenMineTapped -> (state as? PublicRecipeState.Loaded)?.let { it.mineId ?: it.savedId }?.let {
                sendEvent(PublicRecipeEvent.OpenRecipe(it))
            }
            PublicRecipeAction.ReportTapped -> recipe?.let { sendEvent(PublicRecipeEvent.OpenReport(route.code, it.name)) }
            PublicRecipeAction.BackTapped -> sendEvent(PublicRecipeEvent.Close)
        }
    }

    private suspend fun save(action: PublicRecipeAction) {
        val current = recipe ?: return
        val loaded = state as? PublicRecipeState.Loaded ?: return
        if (saved || loaded.mineId != null || loaded.savedId != null) return
        saved = true
        action.updateState { loaded.copy(saving = true) }
        val copy = recipes.create(current.toDraft())
        // Counted for ranking and the board; a save that can't be counted is still a save.
        viewModelScope.launch { publicRecipes.countSave(route.code) }
        sendEvent(PublicRecipeEvent.ShowToast(Toast(Res.string.recipes_public_saved)))
        sendEvent(PublicRecipeEvent.OpenRecipe(copy.id))
    }
}
