package com.opson.features.foods.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.ui.label
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.MacroLine
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.separator
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.PageHeaderNavigation
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.chip.ChoiceChip
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.components.text.TextInput
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.foods_create_meal
import opson.libraries.resources.generated.resources.foods_meal_add_food
import opson.libraries.resources.generated.resources.foods_meal_count
import opson.libraries.resources.generated.resources.foods_meal_empty
import opson.libraries.resources.generated.resources.foods_meal_items
import opson.libraries.resources.generated.resources.foods_meal_name
import opson.libraries.resources.generated.resources.foods_meal_name_placeholder
import opson.libraries.resources.generated.resources.foods_meal_remove_cd
import opson.libraries.resources.generated.resources.foods_meal_save
import opson.libraries.resources.generated.resources.foods_meal_total
import opson.libraries.resources.generated.resources.foods_meal_usually_at
import opson.libraries.resources.generated.resources.foods_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Create a meal (design v3, 11.2): name and "Usually at", the foods in it with a total, "Add a
 * food" (search, picking for this meal), and Save meal pinned to the bottom, disabled
 * until there is a name and a food. Stateless.
 */
@Composable
fun CreateMealScreen(
    state: CreateMealState,
    onAction: (CreateMealAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    FormPage(
        title = stringResource(Res.string.foods_create_meal),
        onClose = { onAction(CreateMealAction.CloseTapped) },
        saveLabel = stringResource(Res.string.foods_meal_save),
        canSave = state.canSave,
        onSave = { onAction(CreateMealAction.SaveTapped) },
        modifier = modifier,
    ) {
        NameCard(state, onAction)
        ItemsCard(state, onAction)
        AddFoodCard(onClick = { onAction(CreateMealAction.AddFoodTapped) })
    }
}

/**
 * The frame Create a meal and Add a food share: the top app bar with a close X, one scroll for
 * the cards, and the save button pinned under it.
 */
@Composable
internal fun FormPage(
    title: String,
    onClose: () -> Unit,
    saveLabel: String,
    canSave: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppTheme.spacing.page),
        ) {
            PageHeader(
                title = title,
                backLabel = stringResource(Res.string.foods_title),
                onBack = onClose,
                navigation = PageHeaderNavigation.Close,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = AppTheme.spacing.section),
                verticalArrangement = Arrangement.spacedBy(FoodsDefaults.CardGap),
                content = content,
            )
            ButtonPrimary(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = AppTheme.spacing.page),
            ) {
                Text(saveLabel)
            }
        }
    }
}

/** A card of form fields, padded 16 all round. */
@Composable
internal fun FormCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    EdgeCard(
        modifier = modifier,
        edgeToEdge = false,
        contentPadding = PaddingValues(FoodsDefaults.CardPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** The small muted label above a field or a chip row. */
@Composable
internal fun FieldLabel(text: String) {
    Text(text = text, typography = AppTheme.type.secondarySmallStrong, color = AppTheme.colors.muted)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameCard(state: CreateMealState, onAction: (CreateMealAction) -> Unit) {
    FormCard {
        TextInput(
            value = state.name,
            onValueChange = { onAction(CreateMealAction.NameTyped(it)) },
            label = stringResource(Res.string.foods_meal_name),
            placeholder = stringResource(Res.string.foods_meal_name_placeholder),
            // The form's only field: nothing for Next to move to.
            imeAction = ImeAction.Done,
        )
        Column {
            FieldLabel(stringResource(Res.string.foods_meal_usually_at))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.slots.forEach { slot ->
                    ChoiceChip(
                        label = stringResource(slot.label),
                        selected = slot == state.usualSlot,
                        onClick = { onAction(CreateMealAction.UsualSlotPicked(slot)) },
                        unselectedColor = AppTheme.colors.surface,
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemsCard(state: CreateMealState, onAction: (CreateMealAction) -> Unit) {
    FoodsCard(label = stringResource(Res.string.foods_meal_items)) {
        if (state.items.isEmpty()) {
            FoodsCardNote(stringResource(Res.string.foods_meal_empty))
            return@FoodsCard
        }
        state.items.forEachIndexed { index, item ->
            FoodsRow(
                name = item.name,
                sub = item.amount,
                calories = item.calories,
                buttonIcon = AppIcons.Close,
                buttonDescription = stringResource(Res.string.foods_meal_remove_cd, item.name),
                onButton = { onAction(CreateMealAction.ItemRemoveTapped(index)) },
                buttonContainer = AppTheme.colors.bg,
                buttonContent = AppTheme.colors.ink,
                showDivider = index < state.items.lastIndex,
            )
        }
        HorizontalDivider(color = AppTheme.colors.ink, thickness = 1.5.dp)
        TotalRow(state)
    }
}

@Composable
private fun TotalRow(state: CreateMealState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(Res.string.foods_meal_total), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
            val sub = AppTheme.type.secondary.Tabular
            if (state.totalMacros.isNotEmpty()) {
                MacroLine(parts = state.totalMacros, separator = MacroLineStyle.Compact.separator, typography = sub)
            } else {
                Text(
                    text = pluralStringResource(Res.plurals.foods_meal_count, state.items.size, state.items.size),
                    typography = sub,
                    color = AppTheme.colors.muted,
                )
            }
        }
        NutrientText(
            metric = Metric.Calories,
            display = state.total,
            typography = AppTheme.type.cardTitle.Tabular,
            color = AppTheme.colors.ink,
            maxLines = 1,
        )
    }
}

/** "+ Add a food": opens search to pick for this meal. */
@Composable
private fun AddFoodCard(onClick: () -> Unit) {
    EdgeCard(edgeToEdge = false, contentPadding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .semantics { role = Role.Button }
                .bounceClick(scaleDown = 0.98f, onClick = onClick)
                .padding(horizontal = FoodsDefaults.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Icon(icon = AppIcons.Add, size = 24.dp, color = AppTheme.colors.accent)
            Text(text = stringResource(Res.string.foods_meal_add_food), typography = AppTheme.type.cardTitle, color = AppTheme.colors.accent)
        }
    }
}
