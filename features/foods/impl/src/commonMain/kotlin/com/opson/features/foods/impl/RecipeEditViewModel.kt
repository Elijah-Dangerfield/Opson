package com.opson.features.foods.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.foods.RecipeEditRoute
import com.opson.libraries.diary.MealPicks
import com.opson.libraries.diary.RecipeDraft
import com.opson.libraries.diary.RecipeRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.recipes_saved
import kotlin.random.Random

/**
 * New recipe or Edit recipe: a name, how many servings it makes, ingredients picked through search
 * in pick-for-this-meal mode (the same one search Create a meal uses), and optional notes.
 */
@Inject
class RecipeEditViewModel(
    @Assisted private val route: RecipeEditRoute,
    private val recipes: RecipeRepository,
    private val mealPicks: MealPicks,
    policySource: VisibilityPolicySource,
) : SEAViewModel<RecipeEditState, RecipeEditEvent, RecipeEditAction>(
    initialStateArg = RecipeEditState(loading = route.recipeId != null, editing = route.recipeId != null),
) {

    /** This screen's answers from the logger, told apart from any other picker's. */
    private val requestId: String = "recipe-${Random.nextLong().toULong().toString(RADIX)}"

    private var draft = RecipeDraftUi()
    private var policy: VisibilityPolicy = policySource.policy.value

    /** Set once the recipe is written, so a second Save queued behind the first writes nothing. */
    private var saved = false

    /** An edit draws nothing until the recipe is read, so the form never flashes empty. */
    private var ready = route.recipeId == null

    init {
        viewModelScope.launch {
            policySource.policy.collect { takeAction(RecipeEditAction.PolicyArrived(it)) }
        }
        viewModelScope.launch {
            mealPicks.latest.filterNotNull().filter { it.requestId == requestId }.collect {
                takeAction(RecipeEditAction.IngredientsPicked(it))
            }
        }
        route.recipeId?.let { id -> viewModelScope.launch { takeAction(RecipeEditAction.Loaded(recipes.get(id))) } }
    }

    override suspend fun handleAction(action: RecipeEditAction) {
        when (action) {
            is RecipeEditAction.PolicyArrived -> {
                policy = action.policy
                action.rebuild()
            }
            is RecipeEditAction.Loaded -> {
                val recipe = action.recipe ?: return sendEvent(RecipeEditEvent.Close)
                ready = true
                action.change {
                    RecipeDraftUi(recipe.name, recipe.servings, recipe.ingredients, recipe.notes.orEmpty(), recipe.savedFrom)
                }
            }
            is RecipeEditAction.NameTyped -> action.change { copy(name = action.text) }
            RecipeEditAction.FewerTapped -> action.change { copy(servings = RecipeDraftUi.fewer(servings)) }
            RecipeEditAction.MoreTapped -> action.change { copy(servings = RecipeDraftUi.more(servings)) }
            is RecipeEditAction.NotesTyped -> action.change { copy(notes = action.text) }
            is RecipeEditAction.IngredientRemoveTapped -> action.change {
                copy(ingredients = ingredients.filterIndexed { index, _ -> index != action.index })
            }
            RecipeEditAction.AddIngredientTapped -> sendEvent(RecipeEditEvent.OpenPicker(requestId))
            is RecipeEditAction.IngredientsPicked -> {
                mealPicks.consumed(action.picks)
                action.change { copy(ingredients = ingredients + action.picks.items) }
            }
            RecipeEditAction.SaveTapped -> save()
            RecipeEditAction.CloseTapped -> sendEvent(RecipeEditEvent.Close)
        }
    }

    private suspend fun save() {
        val current = draft
        if (saved || current.name.isBlank() || current.ingredients.isEmpty()) return
        val recipe = RecipeDraft(
            name = current.name,
            servings = current.servings,
            ingredients = current.ingredients,
            notes = current.notes,
            savedFrom = current.savedFrom,
        )
        val id = route.recipeId
        val savedId = if (id == null) recipes.create(recipe).id else id.also { recipes.update(it, recipe) }
        saved = true
        sendEvent(RecipeEditEvent.ShowToast(Toast(Res.string.recipes_saved)))
        sendEvent(RecipeEditEvent.Saved(savedId, created = id == null))
    }

    private suspend fun RecipeEditAction.change(transform: RecipeDraftUi.() -> RecipeDraftUi) {
        draft = draft.transform()
        rebuild()
    }

    private suspend fun RecipeEditAction.rebuild() {
        if (!ready) return
        val current = draft
        val currentPolicy = policy
        updateState { RecipeEditStateBuilder.build(current, currentPolicy, editing = route.recipeId != null) }
    }

    private companion object {
        const val RADIX = 36
    }
}
