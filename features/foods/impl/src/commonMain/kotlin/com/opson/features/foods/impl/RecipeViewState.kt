package com.opson.features.foods.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.foods.RecipeRoute
import com.opson.libraries.diary.Recipe
import com.opson.libraries.diary.RecipeRepository
import com.opson.libraries.diary.RecipeShareState
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.recipes_deleted

/** One of the person's recipes. Built by [RecipeViewStateBuilder]; every number in it is policy-resolved. */
data class RecipeViewState(
    val loading: Boolean = true,
    val name: String = "",
    /** "Makes 6 servings". */
    val servings: String = "",
    val perServing: AmountNumbersUi = AmountNumbersUi(),
    val whole: AmountNumbersUi = AmountNumbersUi(),
    val ingredients: List<IngredientUi> = emptyList(),
    val notes: String? = null,
    /** Saved from someone's shared recipe: theirs, not checked by Opson. */
    val fromSomeone: Boolean = false,
    val share: RecipeShareState = RecipeShareState.Private,
    /** Shown: the delete confirmation. */
    val confirmingDelete: Boolean = false,
)

sealed interface RecipeViewAction {
    data class Arrived(val recipe: Recipe?, val policy: VisibilityPolicy) : RecipeViewAction
    data object LogTapped : RecipeViewAction
    data object ShareTapped : RecipeViewAction
    data object EditTapped : RecipeViewAction
    data object DeleteTapped : RecipeViewAction
    data object DeleteConfirmed : RecipeViewAction
    data object DeleteDismissed : RecipeViewAction
    data object BackTapped : RecipeViewAction
}

sealed interface RecipeViewEvent {
    data class OpenLog(val recipeId: String) : RecipeViewEvent
    data class OpenShare(val recipeId: String) : RecipeViewEvent
    data class OpenEdit(val recipeId: String) : RecipeViewEvent
    data class ShowToast(val toast: Toast) : RecipeViewEvent
    data object Close : RecipeViewEvent
}

object RecipeViewStateBuilder {
    fun build(recipe: Recipe, policy: VisibilityPolicy, confirmingDelete: Boolean = false): RecipeViewState = RecipeViewState(
        loading = false,
        name = recipe.name,
        servings = RecipeNumbers.servings(recipe.servings),
        perServing = RecipeNumbers.perServing(recipe, policy),
        whole = RecipeNumbers.whole(recipe, policy),
        ingredients = recipe.ingredients.map { RecipeNumbers.ingredient(it, policy) },
        notes = recipe.notes,
        fromSomeone = recipe.savedFrom != null,
        share = if (recipe.sharing.code == null) RecipeShareState.Private else recipe.sharing.state,
        confirmingDelete = confirmingDelete,
    )
}

/**
 * One of the person's recipes, live from the device. Deleting a shared one takes its public copy
 * down too: the server shows no deleted recipe, and the delete syncs like any other change.
 */
@Inject
class RecipeViewModel(
    @Assisted private val route: RecipeRoute,
    private val recipes: RecipeRepository,
    policySource: VisibilityPolicySource,
) : SEAViewModel<RecipeViewState, RecipeViewEvent, RecipeViewAction>(initialStateArg = RecipeViewState()) {

    private var recipe: Recipe? = null

    /** Close is sent once: a delete closes the page, and the recipe going from the device would again. */
    private var closed = false

    init {
        viewModelScope.launch {
            combine(recipes.observe(route.recipeId), policySource.policy, RecipeViewAction::Arrived)
                .collect { takeAction(it) }
        }
    }

    override suspend fun handleAction(action: RecipeViewAction) {
        when (action) {
            is RecipeViewAction.Arrived -> {
                // Gone: deleted here or on another phone.
                val current = action.recipe ?: return close()
                recipe = current
                action.updateState { RecipeViewStateBuilder.build(current, action.policy, it.confirmingDelete) }
            }
            RecipeViewAction.LogTapped -> sendEvent(RecipeViewEvent.OpenLog(route.recipeId))
            RecipeViewAction.ShareTapped -> sendEvent(RecipeViewEvent.OpenShare(route.recipeId))
            RecipeViewAction.EditTapped -> sendEvent(RecipeViewEvent.OpenEdit(route.recipeId))
            RecipeViewAction.DeleteTapped -> action.updateState { it.copy(confirmingDelete = true) }
            RecipeViewAction.DeleteDismissed -> action.updateState { it.copy(confirmingDelete = false) }
            RecipeViewAction.DeleteConfirmed -> {
                val current = recipe ?: return
                action.updateState { it.copy(confirmingDelete = false) }
                closed = true
                recipes.delete(current.id)
                sendEvent(RecipeViewEvent.ShowToast(Toast(Res.string.recipes_deleted)))
                sendEvent(RecipeViewEvent.Close)
            }
            RecipeViewAction.BackTapped -> close()
        }
    }

    private suspend fun close() {
        if (closed) return
        closed = true
        sendEvent(RecipeViewEvent.Close)
    }
}
