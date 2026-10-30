package com.opson.features.foods.impl

import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.PickedForMeal
import com.opson.libraries.diary.Recipe
import com.opson.libraries.nutrition.VisibilityPolicy

/** New recipe or Edit recipe. Built by [RecipeEditStateBuilder]; every number in it is policy-resolved. */
data class RecipeEditState(
    val loading: Boolean = false,
    val editing: Boolean = false,
    val name: String = "",
    /** "6 servings". */
    val servings: String = RecipeNumbers.servings(RecipeDraftUi.DEFAULT_SERVINGS),
    val canFewer: Boolean = true,
    val canMore: Boolean = true,
    val ingredients: List<IngredientUi> = emptyList(),
    val whole: AmountNumbersUi = AmountNumbersUi(),
    val perServing: AmountNumbersUi = AmountNumbersUi(),
    val notes: String = "",
    /** A name and at least one ingredient. */
    val canSave: Boolean = false,
)

/** What the person has typed and picked so far. The view model holds it; [RecipeEditStateBuilder] draws it. */
data class RecipeDraftUi(
    val name: String = "",
    val servings: Double = DEFAULT_SERVINGS,
    val ingredients: List<LogDraft> = emptyList(),
    val notes: String = "",
    val savedFrom: String? = null,
) {
    companion object {
        const val DEFAULT_SERVINGS = 4.0

        /** Whole servings past one; halves and a quarter below, for a recipe that makes a single portion or less. */
        fun fewer(servings: Double): Double = when {
            servings > 1.0 -> servings - 1.0
            servings > 0.5 -> 0.5
            else -> Recipe.MIN_SERVINGS
        }

        fun more(servings: Double): Double = when {
            servings < 0.5 -> 0.5
            servings < 1.0 -> 1.0
            else -> minOf(servings + 1.0, Recipe.MAX_SERVINGS)
        }
    }
}

sealed interface RecipeEditAction {
    data class PolicyArrived(val policy: VisibilityPolicy) : RecipeEditAction
    data class Loaded(val recipe: Recipe?) : RecipeEditAction
    data class NameTyped(val text: String) : RecipeEditAction
    data object FewerTapped : RecipeEditAction
    data object MoreTapped : RecipeEditAction
    data class NotesTyped(val text: String) : RecipeEditAction
    data class IngredientRemoveTapped(val index: Int) : RecipeEditAction
    data object AddIngredientTapped : RecipeEditAction

    /** The logger, opened for this recipe, handed back what was picked. */
    data class IngredientsPicked(val picks: PickedForMeal) : RecipeEditAction
    data object SaveTapped : RecipeEditAction
    data object CloseTapped : RecipeEditAction
}

sealed interface RecipeEditEvent {
    /** Search in pick-for-this-meal mode, answering under [requestId]. */
    data class OpenPicker(val requestId: String) : RecipeEditEvent
    data class ShowToast(val toast: Toast) : RecipeEditEvent

    /** Saved: a new recipe opens on its own page in this screen's place; an edit goes back to it. */
    data class Saved(val recipeId: String, val created: Boolean) : RecipeEditEvent
    data object Close : RecipeEditEvent
}

object RecipeEditStateBuilder {

    fun build(draft: RecipeDraftUi, policy: VisibilityPolicy, editing: Boolean): RecipeEditState {
        val recipe = Recipe(id = "", name = draft.name, servings = draft.servings, ingredients = draft.ingredients)
        return RecipeEditState(
            loading = false,
            editing = editing,
            name = draft.name,
            servings = RecipeNumbers.servings(draft.servings),
            canFewer = draft.servings > Recipe.MIN_SERVINGS,
            canMore = draft.servings < Recipe.MAX_SERVINGS,
            ingredients = draft.ingredients.map { RecipeNumbers.ingredient(it, policy) },
            whole = RecipeNumbers.whole(recipe, policy),
            perServing = RecipeNumbers.perServing(recipe, policy),
            notes = draft.notes,
            canSave = draft.name.isNotBlank() && draft.ingredients.isNotEmpty(),
        )
    }
}
