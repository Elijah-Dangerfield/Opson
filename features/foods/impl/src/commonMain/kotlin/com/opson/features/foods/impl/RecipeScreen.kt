package com.opson.features.foods.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.RecipeShareState
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.NoteBanner
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.dialog.BasicDialog
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.foods_title
import opson.libraries.resources.generated.resources.recipes_cancel
import opson.libraries.resources.generated.resources.recipes_delete
import opson.libraries.resources.generated.resources.recipes_delete_body
import opson.libraries.resources.generated.resources.recipes_delete_confirm
import opson.libraries.resources.generated.resources.recipes_delete_shared_body
import opson.libraries.resources.generated.resources.recipes_delete_title
import opson.libraries.resources.generated.resources.recipes_edit
import opson.libraries.resources.generated.resources.recipes_from_someone
import opson.libraries.resources.generated.resources.recipes_ingredients
import opson.libraries.resources.generated.resources.recipes_log_serving
import opson.libraries.resources.generated.resources.recipes_makes
import opson.libraries.resources.generated.resources.recipes_notes_title
import opson.libraries.resources.generated.resources.recipes_per_serving
import opson.libraries.resources.generated.resources.recipes_share
import opson.libraries.resources.generated.resources.recipes_state_hidden
import opson.libraries.resources.generated.resources.recipes_state_live
import opson.libraries.resources.generated.resources.recipes_state_removed
import opson.libraries.resources.generated.resources.recipes_whole
import org.jetbrains.compose.resources.stringResource

/**
 * One of the person's recipes: its name and how many servings it makes, one serving's numbers and
 * the whole recipe's, whether it is shared, the ingredients, the notes, then Log a serving, Share,
 * Edit and Delete. Stateless.
 */
@Composable
fun RecipeScreen(
    state: RecipeViewState,
    onAction: (RecipeViewAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppTheme.spacing.page),
        ) {
            PageHeader(title = "", backLabel = stringResource(Res.string.foods_title), onBack = { onAction(RecipeViewAction.BackTapped) })
            if (state.loading) return@Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = AppTheme.spacing.section),
                verticalArrangement = Arrangement.spacedBy(FoodsDefaults.CardGap),
            ) {
                RecipeTitle(state.name, stringResource(Res.string.recipes_makes, state.servings))
                if (state.fromSomeone) NoteBanner(stringResource(Res.string.recipes_from_someone))
                shareLine(state.share)?.let { NoteBanner(stringResource(it)) }
                NumbersCard(state.perServing, state.whole)
                FoodsCard(label = stringResource(Res.string.recipes_ingredients)) {
                    state.ingredients.forEachIndexed { index, item -> IngredientRow(item, showDivider = index < state.ingredients.lastIndex) }
                }
                state.notes?.let { NotesCard(it) }
                ButtonPrimary(onClick = { onAction(RecipeViewAction.LogTapped) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.recipes_log_serving))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ButtonSecondary(onClick = { onAction(RecipeViewAction.ShareTapped) }, icon = AppIcons.Share, modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.recipes_share))
                    }
                    ButtonSecondary(onClick = { onAction(RecipeViewAction.EditTapped) }, icon = AppIcons.EditOutline, modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.recipes_edit))
                    }
                }
                ButtonGhost(onClick = { onAction(RecipeViewAction.DeleteTapped) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.recipes_delete))
                }
            }
        }
        if (state.confirmingDelete) {
            BasicDialog(
                title = stringResource(Res.string.recipes_delete_title),
                description = stringResource(
                    if (state.share == RecipeShareState.Live) Res.string.recipes_delete_shared_body else Res.string.recipes_delete_body,
                ),
                primaryButtonText = stringResource(Res.string.recipes_delete_confirm),
                secondaryButtonText = stringResource(Res.string.recipes_cancel),
                onDismissRequest = { onAction(RecipeViewAction.DeleteDismissed) },
                onPrimaryButtonClicked = { onAction(RecipeViewAction.DeleteConfirmed) },
                onSecondaryButtonClicked = { onAction(RecipeViewAction.DeleteDismissed) },
            )
        }
    }
}

private fun shareLine(state: RecipeShareState) = when (state) {
    RecipeShareState.Private -> null
    RecipeShareState.Live -> Res.string.recipes_state_live
    RecipeShareState.Hidden -> Res.string.recipes_state_hidden
    RecipeShareState.Removed -> Res.string.recipes_state_removed
}

/** A recipe's name in large type with a muted line under it. */
@Composable
internal fun RecipeTitle(name: String, sub: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)) {
        Text(text = name, typography = AppTheme.type.h1, color = AppTheme.colors.ink)
        Text(text = sub, typography = AppTheme.type.body, color = AppTheme.colors.muted)
    }
}

/** Per serving, strong, then the whole recipe. */
@Composable
internal fun NumbersCard(perServing: AmountNumbersUi, whole: AmountNumbersUi?) {
    FoodsCard {
        AmountRow(stringResource(Res.string.recipes_per_serving), perServing, strong = true)
        if (whole != null) {
            HorizontalDivider(color = AppTheme.colors.line)
            AmountRow(stringResource(Res.string.recipes_whole), whole, strong = false)
        }
    }
}

@Composable
internal fun NotesCard(notes: String) {
    FoodsCard(label = stringResource(Res.string.recipes_notes_title)) {
        Text(text = notes, typography = AppTheme.type.body, color = AppTheme.colors.ink, modifier = Modifier.padding(vertical = 14.dp))
    }
}
