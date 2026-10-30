package com.opson.features.foods.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.Recipe
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.Stepper
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.text.TextArea
import com.opson.libraries.ui.components.text.TextInput
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.foods_meal_remove_cd
import opson.libraries.resources.generated.resources.recipes_add_ingredient
import opson.libraries.resources.generated.resources.recipes_ingredients_full
import opson.libraries.resources.generated.resources.recipes_edit_title
import opson.libraries.resources.generated.resources.recipes_ingredients
import opson.libraries.resources.generated.resources.recipes_ingredients_empty
import opson.libraries.resources.generated.resources.recipes_makes_label
import opson.libraries.resources.generated.resources.recipes_name
import opson.libraries.resources.generated.resources.recipes_name_placeholder
import opson.libraries.resources.generated.resources.recipes_new_title
import opson.libraries.resources.generated.resources.recipes_notes
import opson.libraries.resources.generated.resources.recipes_notes_placeholder
import opson.libraries.resources.generated.resources.recipes_per_serving
import opson.libraries.resources.generated.resources.recipes_save
import opson.libraries.resources.generated.resources.recipes_whole
import org.jetbrains.compose.resources.stringResource

/**
 * New recipe or Edit recipe: the name and how many servings it makes, the ingredients for the
 * whole batch with the whole recipe's numbers and one serving's, "Add an ingredient" (search,
 * picking for this recipe), notes, and Save recipe pinned to the bottom. Stateless.
 */
@Composable
fun RecipeEditScreen(
    state: RecipeEditState,
    onAction: (RecipeEditAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    FormPage(
        title = stringResource(if (state.editing) Res.string.recipes_edit_title else Res.string.recipes_new_title),
        onClose = { onAction(RecipeEditAction.CloseTapped) },
        saveLabel = stringResource(Res.string.recipes_save),
        canSave = state.canSave,
        onSave = { onAction(RecipeEditAction.SaveTapped) },
        modifier = modifier,
    ) {
        if (state.loading) return@FormPage
        FormCard {
            TextInput(
                value = state.name,
                onValueChange = { onAction(RecipeEditAction.NameTyped(it)) },
                label = stringResource(Res.string.recipes_name),
                placeholder = stringResource(Res.string.recipes_name_placeholder),
                imeAction = ImeAction.Done,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel(stringResource(Res.string.recipes_makes_label))
                Stepper(
                    amount = state.servings,
                    onDecrease = { onAction(RecipeEditAction.FewerTapped) },
                    onIncrease = { onAction(RecipeEditAction.MoreTapped) },
                    canDecrease = state.canFewer,
                    canIncrease = state.canMore,
                )
            }
        }
        FoodsCard(label = stringResource(Res.string.recipes_ingredients)) {
            if (state.ingredients.isEmpty()) {
                FoodsCardNote(stringResource(Res.string.recipes_ingredients_empty))
                return@FoodsCard
            }
            state.ingredients.forEachIndexed { index, item ->
                FoodsRow(
                    name = item.name,
                    sub = item.amount,
                    calories = item.calories,
                    buttonIcon = AppIcons.Close,
                    buttonDescription = stringResource(Res.string.foods_meal_remove_cd, item.name),
                    onButton = { onAction(RecipeEditAction.IngredientRemoveTapped(index)) },
                    buttonContainer = AppTheme.colors.bg,
                    buttonContent = AppTheme.colors.ink,
                    showDivider = index < state.ingredients.lastIndex,
                )
            }
            HorizontalDivider(color = AppTheme.colors.ink, thickness = 1.5.dp)
            AmountRow(stringResource(Res.string.recipes_whole), state.whole, strong = false)
            HorizontalDivider(color = AppTheme.colors.line)
            AmountRow(stringResource(Res.string.recipes_per_serving), state.perServing, strong = true)
        }
        if (state.canAddIngredient) {
            ActionCard(AppIcons.Add, stringResource(Res.string.recipes_add_ingredient), onClick = { onAction(RecipeEditAction.AddIngredientTapped) })
        } else {
            FoodsCard { FoodsCardNote(stringResource(Res.string.recipes_ingredients_full, Recipe.MAX_INGREDIENTS)) }
        }
        FormCard {
            FieldLabel(stringResource(Res.string.recipes_notes))
            TextArea(
                value = state.notes,
                onValueChange = { onAction(RecipeEditAction.NotesTyped(it)) },
                placeholder = stringResource(Res.string.recipes_notes_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
