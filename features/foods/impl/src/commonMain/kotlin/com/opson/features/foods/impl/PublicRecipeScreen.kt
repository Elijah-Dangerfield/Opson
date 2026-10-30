package com.opson.features.foods.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.components.CircularProgressIndicator
import com.opson.libraries.ui.components.NoteBanner
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.foods_title
import opson.libraries.resources.generated.resources.recipes_ingredients
import opson.libraries.resources.generated.resources.recipes_log_serving
import opson.libraries.resources.generated.resources.recipes_makes
import opson.libraries.resources.generated.resources.recipes_public_by
import opson.libraries.resources.generated.resources.recipes_public_by_anon
import opson.libraries.resources.generated.resources.recipes_public_checked
import opson.libraries.resources.generated.resources.recipes_public_failed_body
import opson.libraries.resources.generated.resources.recipes_public_failed_title
import opson.libraries.resources.generated.resources.recipes_public_not_found_body
import opson.libraries.resources.generated.resources.recipes_public_not_found_title
import opson.libraries.resources.generated.resources.recipes_public_not_shared_body
import opson.libraries.resources.generated.resources.recipes_public_not_shared_title
import opson.libraries.resources.generated.resources.recipes_public_offline_body
import opson.libraries.resources.generated.resources.recipes_public_open_mine
import opson.libraries.resources.generated.resources.recipes_public_report
import opson.libraries.resources.generated.resources.recipes_public_save
import opson.libraries.resources.generated.resources.recipes_public_unverified
import opson.libraries.resources.generated.resources.recipes_public_saved_before
import opson.libraries.resources.generated.resources.recipes_public_yours
import opson.libraries.resources.generated.resources.recipes_try_again
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Someone's shared recipe: its name and who made it, the line that a person made it and Opson
 * didn't check it, how many ingredients came from checked foods, one serving's numbers, the
 * ingredients and notes, then Log a serving, Save to my recipes and Report. Stateless.
 */
@Composable
fun PublicRecipeScreen(
    state: PublicRecipeState,
    onAction: (PublicRecipeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppTheme.spacing.page),
        ) {
            PageHeader(title = "", backLabel = stringResource(Res.string.foods_title), onBack = { onAction(PublicRecipeAction.BackTapped) })
            when (state) {
                PublicRecipeState.Loading -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is PublicRecipeState.Missing -> Missing(state.why, onAction)
                is PublicRecipeState.Loaded -> Loaded(state, onAction, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Loaded(state: PublicRecipeState.Loaded, onAction: (PublicRecipeAction) -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = AppTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(FoodsDefaults.CardGap),
    ) {
        RecipeTitle(
            name = state.name,
            sub = listOf(
                state.by?.let { stringResource(Res.string.recipes_public_by, it) } ?: stringResource(Res.string.recipes_public_by_anon),
                stringResource(Res.string.recipes_makes, state.servings),
            ).joinToString(" · "),
        )
        NoteBanner(
            listOf(
                stringResource(Res.string.recipes_public_unverified),
                pluralStringResource(Res.plurals.recipes_public_checked, state.ingredients.size, state.checkedCount, state.ingredients.size),
            ).joinToString(" "),
        )
        if (state.mineId != null) {
            NoteBanner(stringResource(Res.string.recipes_public_yours))
        } else if (state.savedId != null) {
            NoteBanner(stringResource(Res.string.recipes_public_saved_before))
        }
        NumbersCard(state.perServing, whole = null)
        FoodsCard(label = stringResource(Res.string.recipes_ingredients)) {
            state.ingredients.forEachIndexed { index, item -> IngredientRow(item, showDivider = index < state.ingredients.lastIndex) }
        }
        state.notes?.let { NotesCard(it) }
        ButtonPrimary(onClick = { onAction(PublicRecipeAction.LogTapped) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.recipes_log_serving))
        }
        if (state.mineId != null || state.savedId != null) {
            ButtonSecondary(onClick = { onAction(PublicRecipeAction.OpenMineTapped) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.recipes_public_open_mine))
            }
        }
        if (state.mineId == null && state.savedId != null) {
            ButtonGhost(onClick = { onAction(PublicRecipeAction.ReportTapped) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.recipes_public_report))
            }
        }
        if (state.mineId == null && state.savedId == null) {
            ButtonSecondary(
                onClick = { onAction(PublicRecipeAction.SaveTapped) },
                icon = AppIcons.BookmarkAdd,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.recipes_public_save))
            }
            ButtonGhost(onClick = { onAction(PublicRecipeAction.ReportTapped) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.recipes_public_report))
            }
        }
    }
}

@Composable
private fun Missing(why: PublicRecipeState.Missing.Why, onAction: (PublicRecipeAction) -> Unit) {
    val (title, body) = when (why) {
        PublicRecipeState.Missing.Why.NotShared -> Res.string.recipes_public_not_shared_title to Res.string.recipes_public_not_shared_body
        PublicRecipeState.Missing.Why.NotFound -> Res.string.recipes_public_not_found_title to Res.string.recipes_public_not_found_body
        PublicRecipeState.Missing.Why.Offline -> Res.string.recipes_public_failed_title to Res.string.recipes_public_offline_body
        PublicRecipeState.Missing.Why.Failed -> Res.string.recipes_public_failed_title to Res.string.recipes_public_failed_body
    }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(title), typography = AppTheme.type.sheetTitle, color = AppTheme.colors.ink)
        Text(stringResource(body), typography = AppTheme.type.body, color = AppTheme.colors.muted)
        if (why == PublicRecipeState.Missing.Why.Offline || why == PublicRecipeState.Missing.Why.Failed) {
            ButtonSecondary(onClick = { onAction(PublicRecipeAction.RetryTapped) }) { Text(stringResource(Res.string.recipes_try_again)) }
        }
    }
}
