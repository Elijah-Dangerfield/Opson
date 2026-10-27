package com.opson.features.foods.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.ui.components.chip.ChoiceChip
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.ToneIcon
import com.opson.libraries.ui.components.icon.ToneIconSize
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.components.text.TextInput
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.foods_add_food
import opson.libraries.resources.generated.resources.foods_field_calories
import opson.libraries.resources.generated.resources.foods_field_carbs
import opson.libraries.resources.generated.resources.foods_field_fat
import opson.libraries.resources.generated.resources.foods_field_fiber
import opson.libraries.resources.generated.resources.foods_field_protein
import opson.libraries.resources.generated.resources.foods_food_brand
import opson.libraries.resources.generated.resources.foods_food_brand_placeholder
import opson.libraries.resources.generated.resources.foods_food_fill_in
import opson.libraries.resources.generated.resources.foods_food_hidden_body
import opson.libraries.resources.generated.resources.foods_food_hidden_title
import opson.libraries.resources.generated.resources.foods_food_name
import opson.libraries.resources.generated.resources.foods_food_name_placeholder
import opson.libraries.resources.generated.resources.foods_food_per_serving
import opson.libraries.resources.generated.resources.foods_food_save
import opson.libraries.resources.generated.resources.foods_food_serving
import opson.libraries.resources.generated.resources.foods_food_serving_amount_cd
import opson.libraries.resources.generated.resources.foods_unit_cup
import opson.libraries.resources.generated.resources.foods_unit_g
import opson.libraries.resources.generated.resources.foods_unit_grams
import opson.libraries.resources.generated.resources.foods_unit_kcal
import opson.libraries.resources.generated.resources.foods_unit_piece
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Add a food (design v3, 11.3): name, brand, what a serving is, then the nutrition fields the
 * mode asks for, every one optional. With none to ask (Blind, 11.4) the second card says the
 * nutrition stays out of view instead, and shows no number field at all. Save food is pinned to
 * the bottom and needs only a name. Stateless.
 *
 * v3 also draws "Scan the label instead" and "Photograph the label"; label photos are 0.2, so
 * neither is built.
 */
@Composable
fun AddFoodScreen(
    state: AddFoodState,
    onAction: (AddFoodAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    FormPage(
        title = stringResource(Res.string.foods_add_food),
        onClose = { onAction(AddFoodAction.CloseTapped) },
        saveLabel = stringResource(Res.string.foods_food_save),
        canSave = state.canSave,
        onSave = { onAction(AddFoodAction.SaveTapped) },
        modifier = modifier,
    ) {
        AboutCard(state, onAction)
        if (state.labelOnly) HiddenCard() else NutritionCard(state, onAction)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutCard(state: AddFoodState, onAction: (AddFoodAction) -> Unit) {
    FormCard {
        TextInput(
            value = state.name,
            onValueChange = { onAction(AddFoodAction.NameTyped(it)) },
            label = stringResource(Res.string.foods_food_name),
            placeholder = stringResource(Res.string.foods_food_name_placeholder),
        )
        TextInput(
            value = state.brand,
            onValueChange = { onAction(AddFoodAction.BrandTyped(it)) },
            label = stringResource(Res.string.foods_food_brand),
            placeholder = stringResource(Res.string.foods_food_brand_placeholder),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel(stringResource(Res.string.foods_food_serving))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                val amountDescription = stringResource(Res.string.foods_food_serving_amount_cd)
                NumberField(
                    value = state.servingAmount,
                    onValueChange = { onAction(AddFoodAction.ServingAmountTyped(it)) },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(64.dp).semantics { contentDescription = amountDescription },
                )
                state.units.forEach { unit ->
                    ChoiceChip(
                        label = stringResource(unit.label),
                        selected = unit == state.servingUnit,
                        onClick = { onAction(AddFoodAction.ServingUnitPicked(unit)) },
                        unselectedColor = AppTheme.colors.surface,
                    )
                }
            }
        }
    }
}

@Composable
private fun NutritionCard(state: AddFoodState, onAction: (AddFoodAction) -> Unit) {
    FormCard {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            Text(
                text = stringResource(Res.string.foods_food_per_serving),
                typography = AppTheme.type.cardTitle,
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text(text = stringResource(Res.string.foods_food_fill_in), typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
        }
        // Two to a row, as v3 lays them out; an odd one out keeps its half width.
        state.fields.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                pair.forEach { field ->
                    NumberField(
                        value = field.text,
                        onValueChange = { onAction(AddFoodAction.NutritionTyped(field.metric, it)) },
                        label = stringResource(field.metric.fieldLabel),
                        unit = stringResource(if (field.metric == Metric.Calories) Res.string.foods_unit_kcal else Res.string.foods_unit_g),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Blind (11.4): no number fields, since a typed number would show itself back. */
@Composable
private fun HiddenCard() {
    FormCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
            ToneIcon(icon = AppIcons.VisibilityOff, tone = AppTheme.colors.nutrients.fiber, size = ToneIconSize.Small)
            Text(
                text = stringResource(Res.string.foods_food_hidden_title),
                typography = AppTheme.type.cardTitle,
                color = AppTheme.colors.ink,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(text = stringResource(Res.string.foods_food_hidden_body), typography = AppTheme.type.body, color = AppTheme.colors.muted)
    }
}

private val ServingUnit.label: StringResource
    get() = when (this) {
        ServingUnit.Piece -> Res.string.foods_unit_piece
        ServingUnit.Grams -> Res.string.foods_unit_grams
        ServingUnit.Cup -> Res.string.foods_unit_cup
    }

private val Metric.fieldLabel: StringResource
    get() = when (this) {
        Metric.Protein -> Res.string.foods_field_protein
        Metric.Carbs -> Res.string.foods_field_carbs
        Metric.Fat -> Res.string.foods_field_fat
        Metric.Fiber -> Res.string.foods_field_fiber
        else -> Res.string.foods_field_calories
    }
