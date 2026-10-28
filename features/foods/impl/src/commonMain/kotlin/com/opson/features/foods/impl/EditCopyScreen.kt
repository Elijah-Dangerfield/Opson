package com.opson.features.foods.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.testTag
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.MenuItem
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.PageHeaderNavigation
import com.opson.libraries.ui.components.PopoverMenu
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconSize
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.components.text.TextInput
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.foods_copy_add_serving
import opson.libraries.resources.generated.resources.foods_copy_brand
import opson.libraries.resources.generated.resources.foods_copy_check
import opson.libraries.resources.generated.resources.foods_copy_check_fix
import opson.libraries.resources.generated.resources.foods_copy_daily_cd
import opson.libraries.resources.generated.resources.foods_copy_label
import opson.libraries.resources.generated.resources.foods_copy_name
import opson.libraries.resources.generated.resources.foods_copy_per
import opson.libraries.resources.generated.resources.foods_copy_per_cd
import opson.libraries.resources.generated.resources.foods_copy_remove_serving_cd
import opson.libraries.resources.generated.resources.foods_copy_save
import opson.libraries.resources.generated.resources.foods_copy_serving_grams_cd
import opson.libraries.resources.generated.resources.foods_copy_serving_hint
import opson.libraries.resources.generated.resources.foods_copy_serving_name_cd
import opson.libraries.resources.generated.resources.foods_copy_servings
import opson.libraries.resources.generated.resources.foods_copy_servings_body
import opson.libraries.resources.generated.resources.foods_copy_title
import opson.libraries.resources.generated.resources.foods_copy_use
import opson.libraries.resources.generated.resources.foods_copy_use_body_other
import opson.libraries.resources.generated.resources.foods_copy_use_body_today
import opson.libraries.resources.generated.resources.foods_copy_was
import opson.libraries.resources.generated.resources.foods_copy_weigh_hint
import opson.libraries.resources.generated.resources.foods_field_added_sugars
import opson.libraries.resources.generated.resources.foods_field_calories
import opson.libraries.resources.generated.resources.foods_field_carbs
import opson.libraries.resources.generated.resources.foods_field_cholesterol
import opson.libraries.resources.generated.resources.foods_field_fat
import opson.libraries.resources.generated.resources.foods_field_fiber
import opson.libraries.resources.generated.resources.foods_field_protein
import opson.libraries.resources.generated.resources.foods_field_saturated_fat
import opson.libraries.resources.generated.resources.foods_field_sugars
import opson.libraries.resources.generated.resources.foods_field_trans_fat
import opson.libraries.resources.generated.resources.foods_title
import opson.libraries.resources.generated.resources.foods_unit_g
import opson.libraries.resources.generated.resources.foods_unit_kcal
import opson.libraries.resources.generated.resources.foods_unit_mg
import opson.libraries.resources.generated.resources.foods_unit_ug
import opson.libraries.resources.generated.resources.logger_micro_calcium
import opson.libraries.resources.generated.resources.logger_micro_choline
import opson.libraries.resources.generated.resources.logger_micro_copper
import opson.libraries.resources.generated.resources.logger_micro_folate
import opson.libraries.resources.generated.resources.logger_micro_iron
import opson.libraries.resources.generated.resources.logger_micro_magnesium
import opson.libraries.resources.generated.resources.logger_micro_manganese
import opson.libraries.resources.generated.resources.logger_micro_niacin
import opson.libraries.resources.generated.resources.logger_micro_phosphorus
import opson.libraries.resources.generated.resources.logger_micro_potassium
import opson.libraries.resources.generated.resources.logger_micro_riboflavin
import opson.libraries.resources.generated.resources.logger_micro_selenium
import opson.libraries.resources.generated.resources.logger_micro_sodium
import opson.libraries.resources.generated.resources.logger_micro_thiamin
import opson.libraries.resources.generated.resources.logger_micro_vitamin_a
import opson.libraries.resources.generated.resources.logger_micro_vitamin_b12
import opson.libraries.resources.generated.resources.logger_micro_vitamin_b6
import opson.libraries.resources.generated.resources.logger_micro_vitamin_c
import opson.libraries.resources.generated.resources.logger_micro_vitamin_d
import opson.libraries.resources.generated.resources.logger_micro_vitamin_e
import opson.libraries.resources.generated.resources.logger_micro_vitamin_k
import opson.libraries.resources.generated.resources.logger_micro_zinc
import opson.libraries.resources.generated.resources.logger_micros_minerals
import opson.libraries.resources.generated.resources.logger_micros_vitamins
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Edit a copy: ✕, the title and Save; the name and brand; the serving sizes, each with its
 * weight; Nutrition per; then the label, the vitamins and the minerals the mode shows, a changed
 * number on the accent tint with "Was 190" under its label and each vitamin's share of a day
 * beside it; then Use for this entry, from an entry. Stateless apart from the Nutrition per menu.
 */
@Composable
fun EditCopyScreen(
    state: EditCopyState,
    onAction: (EditCopyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppTheme.spacing.page),
        ) {
            PageHeader(
                title = stringResource(Res.string.foods_copy_title),
                backLabel = stringResource(Res.string.foods_title),
                onBack = { onAction(EditCopyAction.CloseTapped) },
                navigation = PageHeaderNavigation.Close,
            ) {
                FlatButton(
                    text = stringResource(Res.string.foods_copy_save),
                    onClick = { onAction(EditCopyAction.SaveTapped) },
                    tone = FlatButtonTone.AccentText,
                    enabled = state.canSave,
                    minHeight = MinTouchTarget,
                    typography = AppTheme.type.button,
                )
            }
            if (state.loading) return@Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = AppTheme.spacing.page),
                verticalArrangement = Arrangement.spacedBy(FoodsDefaults.CardGap),
            ) {
                AboutCard(state, onAction)
                SectionLabel(stringResource(Res.string.foods_copy_servings))
                ServingsCard(state, onAction)
                if (state.perOptions.isNotEmpty()) PerRow(state, onAction)
                state.sections.forEach { section ->
                    SectionLabel(stringResource(section.kind.title))
                    SectionCard(section, if (section.kind == CopySectionKind.Label) state.energyCheck else null, onAction)
                }
                if (state.fromEntry) UseCard(state, onAction)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        typography = AppTheme.type.sectionLabel,
        color = AppTheme.colors.muted,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
    )
}

@Composable
private fun AboutCard(state: EditCopyState, onAction: (EditCopyAction) -> Unit) {
    CopyCard {
        LabeledRow(stringResource(Res.string.foods_copy_name)) {
            TextInput(value = state.name, onValueChange = { onAction(EditCopyAction.NameTyped(it)) })
        }
        HorizontalDivider(color = AppTheme.colors.line)
        LabeledRow(stringResource(Res.string.foods_copy_brand)) {
            TextInput(value = state.brand, onValueChange = { onAction(EditCopyAction.BrandTyped(it)) })
        }
    }
}

/** Each size its name and grams, a ✕ on all but the label's own serving, then Add serving size. */
@Composable
private fun ServingsCard(state: EditCopyState, onAction: (EditCopyAction) -> Unit) {
    CopyCard {
        Text(text = stringResource(Res.string.foods_copy_servings_body), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        state.servings.forEachIndexed { index, row ->
            ServingLine(index, row, onAction)
        }
        if (state.weighHint) {
            Text(text = stringResource(Res.string.foods_copy_weigh_hint), typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.accent)
        }
        FlatButton(
            text = stringResource(Res.string.foods_copy_add_serving),
            onClick = { onAction(EditCopyAction.ServingAdded) },
            tone = FlatButtonTone.Soft,
            icon = AppIcons.Add,
            minHeight = MinTouchTarget,
        )
    }
}

@Composable
private fun ServingLine(index: Int, row: ServingRow, onAction: (EditCopyAction) -> Unit) {
    val name = row.name.ifBlank { stringResource(Res.string.foods_copy_serving_hint) }
    val nameLabel = stringResource(Res.string.foods_copy_serving_name_cd)
    val gramsLabel = stringResource(Res.string.foods_copy_serving_grams_cd, name)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        Box(Modifier.weight(1f).semantics { contentDescription = nameLabel }) {
            TextInput(
                value = row.name,
                onValueChange = { onAction(EditCopyAction.ServingNameTyped(index, it)) },
                placeholder = stringResource(Res.string.foods_copy_serving_hint),
            )
        }
        NumberField(
            value = row.grams,
            onValueChange = { onAction(EditCopyAction.ServingGramsTyped(index, it)) },
            unit = stringResource(Res.string.foods_unit_g),
            textAlign = TextAlign.End,
            modifier = Modifier.width(GramsWidth).semantics { contentDescription = gramsLabel },
        )
        if (row.removable) {
            RoundIconButton(
                icon = AppIcons.Close.described(stringResource(Res.string.foods_copy_remove_serving_cd, name)),
                onClick = { onAction(EditCopyAction.ServingRemoved(index)) },
                size = MinTouchTarget,
                iconSize = IconSize.Small.dp,
                containerColor = AppTheme.colors.surface,
                contentColor = AppTheme.colors.muted,
            )
        } else {
            Box(Modifier.width(MinTouchTarget))
        }
    }
}

/** "Nutrition per 1 tbsp ⌄": which serving the numbers below are typed for. */
@Composable
private fun PerRow(state: EditCopyState, onAction: (EditCopyAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = state.perOptions.getOrNull(state.perIndex).orEmpty()
    val description = stringResource(Res.string.foods_copy_per_cd, current)
    CopyCard {
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget), verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(Res.string.foods_copy_per), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
            Box {
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = MinTouchTarget)
                        .semantics(mergeDescendants = true) {
                            role = Role.DropdownList
                            contentDescription = description
                        }
                        .bounceClick(scaleDown = 0.97f) { open = state.perOptions.size > 1 },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = current, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink, maxLines = 1)
                    if (state.perOptions.size > 1) Icon(icon = AppIcons.ArrowDropDown, size = IconSize.Medium, color = AppTheme.colors.muted)
                }
                PopoverMenu(expanded = open, onDismissRequest = { open = false }, alignment = Alignment.TopEnd) {
                    state.perOptions.forEachIndexed { index, option ->
                        MenuItem(
                            label = option,
                            onClick = {
                                open = false
                                onAction(EditCopyAction.PerPicked(index))
                            },
                            icon = if (index == state.perIndex) AppIcons.Check else null,
                            showDivider = index < state.perOptions.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(section: CopySection, check: EnergyCheck?, onAction: (EditCopyAction) -> Unit) {
    EdgeCard(edgeToEdge = false, contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.Top) {
        check?.let { EnergyCheckLine(it, onAction) }
        section.fields.forEachIndexed { index, field ->
            if (index > 0 || check != null) HorizontalDivider(color = AppTheme.colors.line)
            NumberRow(field, onAction)
        }
    }
}

/** The 4/4/9 check, with Use to put the macros' energy in the field. */
@Composable
private fun EnergyCheckLine(check: EnergyCheck, onAction: (EditCopyAction) -> Unit) {
    val computed = (check.computed as? Display.Shown)?.text ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.accentTint)
            .padding(horizontal = FoodsDefaults.CardPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Text(
            text = stringResource(Res.string.foods_copy_check, computed),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.ink,
            modifier = Modifier.weight(1f).testTag(Metric.Calories.testTag()),
        )
        FlatButton(
            text = stringResource(Res.string.foods_copy_check_fix, computed),
            onClick = { onAction(EditCopyAction.EnergyFixTapped) },
            tone = FlatButtonTone.AccentText,
            minHeight = MinTouchTarget,
        )
    }
}

@Composable
private fun NumberRow(field: CopyField, onAction: (EditCopyAction) -> Unit) {
    val label = stringResource(field.nutrient.label)
    // Energy is tagged as calories, so the leak test's control sees it in All numbers and no other mode may.
    val tag = if (field.metric == Metric.Calories) Modifier.testTag(Metric.Calories.testTag()) else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (field.was != null) Modifier.background(AppTheme.colors.accentTint) else Modifier)
            .defaultMinSize(minHeight = RowHeight)
            .padding(
                start = if (field.nutrient.sub) FoodsDefaults.CardPadding + SubIndent else FoodsDefaults.CardPadding,
                end = FoodsDefaults.CardPadding,
                top = 8.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = label, typography = if (field.nutrient.sub) AppTheme.type.body else AppTheme.type.cardTitle, color = AppTheme.colors.ink)
            field.was?.let { was ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = stringResource(Res.string.foods_copy_was), typography = AppTheme.type.secondaryStrong.Bold, color = AppTheme.colors.accent)
                    NutrientText(field.metric, was, typography = AppTheme.type.secondaryStrong.Bold.Tabular, color = AppTheme.colors.accent, maxLines = 1)
                }
            }
        }
        DailyShare(field.daily)
        NumberField(
            value = field.text,
            onValueChange = { onAction(EditCopyAction.NutritionTyped(field.nutrient.key, it)) },
            unit = stringResource(field.nutrient.unit.label),
            textAlign = TextAlign.End,
            modifier = Modifier.width(FieldWidth).then(tag).semantics { contentDescription = label },
        )
    }
}

/** "12%", a vitamin's share of a day, beside its field. */
@Composable
private fun DailyShare(daily: Display) {
    val text = (daily as? Display.Shown)?.text
    val description = text?.let { stringResource(Res.string.foods_copy_daily_cd, it) }
    Box(Modifier.width(DailyWidth), contentAlignment = Alignment.CenterEnd) {
        if (description != null) {
            NutrientText(
                metric = Metric.Micros,
                display = daily,
                typography = AppTheme.type.secondary.Tabular,
                color = AppTheme.colors.muted,
                maxLines = 1,
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
    }
}

@Composable
private fun UseCard(state: EditCopyState, onAction: (EditCopyAction) -> Unit) {
    CopyCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            Column(Modifier.weight(1f)) {
                Text(text = stringResource(Res.string.foods_copy_use), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
                Text(
                    text = if (state.isToday && state.slot.isMeal) {
                        stringResource(Res.string.foods_copy_use_body_today, stringResource(state.slot.sentenceName))
                    } else {
                        stringResource(Res.string.foods_copy_use_body_other)
                    },
                    typography = AppTheme.type.secondary,
                    color = AppTheme.colors.muted,
                )
            }
            AppSwitch(checked = state.useForEntry, onCheckedChange = { onAction(EditCopyAction.UseForEntryToggled) })
        }
    }
}

@Composable
private fun CopyCard(content: @Composable () -> Unit) {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = PaddingValues(FoodsDefaults.CardPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}

/** A label on the left and its field on the right, as the design lays out Name and Brand. */
@Composable
private fun LabeledRow(label: String, field: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Text(text = label, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink, modifier = Modifier.width(LabelWidth))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) { field() }
    }
}

private val CopySectionKind.title: StringResource
    get() = when (this) {
        CopySectionKind.Label -> Res.string.foods_copy_label
        CopySectionKind.Vitamins -> Res.string.logger_micros_vitamins
        CopySectionKind.Minerals -> Res.string.logger_micros_minerals
    }

private val NutrientUnit.label: StringResource
    get() = when (this) {
        NutrientUnit.Kcal -> Res.string.foods_unit_kcal
        NutrientUnit.G -> Res.string.foods_unit_g
        NutrientUnit.Mg -> Res.string.foods_unit_mg
        NutrientUnit.Ug -> Res.string.foods_unit_ug
    }

private val CopyNutrient.label: StringResource
    get() = micro?.label ?: when (key) {
        CopyNutrient.KCAL -> Res.string.foods_field_calories
        CopyNutrient.PROTEIN -> Res.string.foods_field_protein
        CopyNutrient.CARBS -> Res.string.foods_field_carbs
        CopyNutrient.FAT -> Res.string.foods_field_fat
        CopyNutrient.FIBER -> Res.string.foods_field_fiber
        "saturated_fat_g" -> Res.string.foods_field_saturated_fat
        "trans_fat_g" -> Res.string.foods_field_trans_fat
        "cholesterol_mg" -> Res.string.foods_field_cholesterol
        "sugars_g" -> Res.string.foods_field_sugars
        else -> Res.string.foods_field_added_sugars
    }

private val Micronutrient.label: StringResource
    get() = when (this) {
        Micronutrient.VitaminA -> Res.string.logger_micro_vitamin_a
        Micronutrient.VitaminB12 -> Res.string.logger_micro_vitamin_b12
        Micronutrient.VitaminC -> Res.string.logger_micro_vitamin_c
        Micronutrient.VitaminD -> Res.string.logger_micro_vitamin_d
        Micronutrient.VitaminE -> Res.string.logger_micro_vitamin_e
        Micronutrient.VitaminK -> Res.string.logger_micro_vitamin_k
        Micronutrient.Folate -> Res.string.logger_micro_folate
        Micronutrient.Calcium -> Res.string.logger_micro_calcium
        Micronutrient.Iron -> Res.string.logger_micro_iron
        Micronutrient.Magnesium -> Res.string.logger_micro_magnesium
        Micronutrient.Potassium -> Res.string.logger_micro_potassium
        Micronutrient.Sodium -> Res.string.logger_micro_sodium
        Micronutrient.Zinc -> Res.string.logger_micro_zinc
        Micronutrient.Thiamin -> Res.string.logger_micro_thiamin
        Micronutrient.Riboflavin -> Res.string.logger_micro_riboflavin
        Micronutrient.Niacin -> Res.string.logger_micro_niacin
        Micronutrient.VitaminB6 -> Res.string.logger_micro_vitamin_b6
        Micronutrient.Choline -> Res.string.logger_micro_choline
        Micronutrient.Copper -> Res.string.logger_micro_copper
        Micronutrient.Manganese -> Res.string.logger_micro_manganese
        Micronutrient.Phosphorus -> Res.string.logger_micro_phosphorus
        Micronutrient.Selenium -> Res.string.logger_micro_selenium
    }

private val RowHeight = 56.dp
private val FieldWidth = 112.dp
private val GramsWidth = 96.dp
private val DailyWidth = 40.dp
private val LabelWidth = 96.dp
private val SubIndent = 16.dp
