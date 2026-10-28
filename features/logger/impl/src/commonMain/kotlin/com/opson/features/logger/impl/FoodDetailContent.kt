package com.opson.features.logger.impl

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.ui.label
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.MicroGroup
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.testTag
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.EdgeCardDefaults
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.MenuItem
import com.opson.libraries.ui.components.MenuPopover
import com.opson.libraries.ui.components.PopoverMenu
import com.opson.libraries.ui.components.RangeBar
import com.opson.libraries.ui.components.RangeBarDefaults
import com.opson.libraries.ui.components.RangeBarSegment
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.chip.PillChip
import com.opson.libraries.ui.components.chip.PillChipStyle
import com.opson.libraries.ui.components.dialog.TimePickerDialog
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconSize
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.BasicTextField
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.adding_detail_adds
import opson.libraries.resources.generated.resources.adding_detail_all
import opson.libraries.resources.generated.resources.adding_detail_all_cd
import opson.libraries.resources.generated.resources.adding_detail_amount
import opson.libraries.resources.generated.resources.adding_detail_amount_cd
import opson.libraries.resources.generated.resources.adding_detail_back_cd
import opson.libraries.resources.generated.resources.adding_detail_edit_copy
import opson.libraries.resources.generated.resources.adding_detail_energy
import opson.libraries.resources.generated.resources.adding_detail_from_off
import opson.libraries.resources.generated.resources.adding_detail_from_opson
import opson.libraries.resources.generated.resources.adding_detail_from_usda
import opson.libraries.resources.generated.resources.adding_detail_from_yours
import opson.libraries.resources.generated.resources.adding_detail_unverified
import opson.libraries.resources.generated.resources.adding_detail_yours_checked
import opson.libraries.resources.generated.resources.adding_detail_yours_in_review
import opson.libraries.resources.generated.resources.adding_detail_label
import opson.libraries.resources.generated.resources.adding_detail_meal
import opson.libraries.resources.generated.resources.adding_detail_meal_cd
import opson.libraries.resources.generated.resources.adding_detail_menu_cd
import opson.libraries.resources.generated.resources.adding_detail_nutrient_count
import opson.libraries.resources.generated.resources.adding_detail_remove_recent
import opson.libraries.resources.generated.resources.adding_detail_report
import opson.libraries.resources.generated.resources.adding_detail_sure
import opson.libraries.resources.generated.resources.adding_detail_sure_cd
import opson.libraries.resources.generated.resources.adding_detail_time
import opson.libraries.resources.generated.resources.adding_detail_time_cd
import opson.libraries.resources.generated.resources.adding_detail_time_now
import opson.libraries.resources.generated.resources.adding_detail_today
import opson.libraries.resources.generated.resources.adding_detail_today_plus_minus
import opson.libraries.resources.generated.resources.adding_detail_weighs
import opson.libraries.resources.generated.resources.logger_add_to
import opson.libraries.resources.generated.resources.logger_amount_cancel
import opson.libraries.resources.generated.resources.logger_amount_carbs
import opson.libraries.resources.generated.resources.logger_amount_closed
import opson.libraries.resources.generated.resources.logger_amount_custom
import opson.libraries.resources.generated.resources.logger_amount_fat
import opson.libraries.resources.generated.resources.logger_amount_favorite_cd
import opson.libraries.resources.generated.resources.logger_amount_kcal
import opson.libraries.resources.generated.resources.logger_amount_loading_cd
import opson.libraries.resources.generated.resources.logger_amount_missing
import opson.libraries.resources.generated.resources.logger_amount_ok
import opson.libraries.resources.generated.resources.logger_amount_open
import opson.libraries.resources.generated.resources.logger_amount_protein
import opson.libraries.resources.generated.resources.logger_amount_time_title
import opson.libraries.resources.generated.resources.logger_amount_unfavorite_cd
import opson.libraries.resources.generated.resources.logger_amount_unit_cd
import opson.libraries.resources.generated.resources.logger_micro_calcium
import opson.libraries.resources.generated.resources.logger_micro_choline
import opson.libraries.resources.generated.resources.logger_micro_copper
import opson.libraries.resources.generated.resources.logger_micro_folate
import opson.libraries.resources.generated.resources.logger_micro_iron
import opson.libraries.resources.generated.resources.logger_micro_magnesium
import opson.libraries.resources.generated.resources.logger_micro_manganese
import opson.libraries.resources.generated.resources.logger_micro_niacin
import opson.libraries.resources.generated.resources.logger_micro_of_daily
import opson.libraries.resources.generated.resources.logger_micro_not_listed
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
import opson.libraries.resources.generated.resources.logger_precision_estimate
import opson.libraries.resources.generated.resources.logger_precision_named
import opson.libraries.resources.generated.resources.logger_precision_weighed
import opson.libraries.resources.generated.resources.logger_source_nevo
import opson.libraries.resources.generated.resources.modes_hunger_question
import opson.libraries.resources.generated.resources.search_badge_verified_cd
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val Precision.label: StringResource
    get() = when (this) {
        Precision.Weighed -> Res.string.logger_precision_weighed
        Precision.Estimate -> Res.string.logger_precision_estimate
        Precision.Label -> Res.string.adding_detail_label
        Precision.Named -> Res.string.logger_precision_named
    }

/** Which of the detail's menus is drawn in place, for previews: a real menu is a popup, which a screenshot cannot see. */
enum class DetailMenu { None, Units, More }

/**
 * The food detail's body: back, the food and ⋮; a list of rows (the amount with its unit, the
 * meal, the time, how sure); what is in the amount; Today with this; where the numbers come from,
 * with All nutrients under it when open; and "Add to {meal}" pinned. Stateless apart from which
 * menu or dialog is open.
 */
@Composable
fun FoodDetailContent(
    state: FoodDetailState,
    onAction: (FoodDetailAction) -> Unit,
    modifier: Modifier = Modifier,
    /** For previews: a menu drawn in place. */
    menuShown: DetailMenu = DetailMenu.None,
    /** Where the cards start. A preview passes one at the bottom so All nutrients is on screen. */
    scrollState: ScrollState = rememberScrollState(),
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(state, onAction)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
            ) {
                when {
                    state.missing -> Text(text = stringResource(Res.string.logger_amount_missing), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
                    state.loading -> DelayedLoadingIndicator(contentDescription = stringResource(Res.string.logger_amount_loading_cd))
                    else -> {
                        DetailsCard(state, onAction)
                        state.hunger?.let { HungerCard(it, onAction) }
                        state.breakdown?.let { BreakdownCard(it) }
                        state.targets?.let { TargetsCard(it) }
                        state.source?.let { SourceRow(it, state.nutrients, onAction) }
                        state.nutrients?.takeIf { it.open }?.let { NutrientsSection(it) }
                    }
                }
            }
            if (!state.loading && !state.missing) AddButton(state, onAction)
        }
        when (menuShown) {
            DetailMenu.Units -> MenuPopover(modifier = Modifier.align(Alignment.TopEnd).padding(top = UNIT_MENU_TOP, end = AppTheme.spacing.page)) {
                UnitMenuItems(state, onAction, close = {})
            }
            DetailMenu.More -> MenuPopover(modifier = Modifier.align(Alignment.TopEnd).padding(top = MORE_MENU_TOP, end = AppTheme.spacing.page)) {
                MoreMenuItems(state, onAction, close = {})
            }
            DetailMenu.None -> Unit
        }
    }
    state.customSize?.let { CustomSizeDialog(it, onAction) }
}

/** Back, the food and its tag, and ⋮. */
@Composable
private fun Header(state: FoodDetailState, onAction: (FoodDetailAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.chip, end = AppTheme.spacing.chip, top = AppTheme.spacing.chip, bottom = AppTheme.spacing.row),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
    ) {
        RoundIconButton(
            icon = AppIcons.ArrowBack.described(stringResource(Res.string.adding_detail_back_cd)),
            onClick = { onAction(FoodDetailAction.BackTapped) },
            size = AppTheme.spacing.touchTarget,
            iconSize = IconSize.Medium.dp,
            containerColor = AppTheme.colors.bg,
            contentColor = AppTheme.colors.ink,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = state.name, typography = AppTheme.type.sheetTitle, color = AppTheme.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            state.tag?.let { Text(text = it, typography = AppTheme.type.secondary, color = AppTheme.colors.muted, maxLines = 1) }
        }
        if (!state.loading && !state.missing) MoreMenu(state, onAction)
    }
}

/** ⋮: the favorite, Remove from recents while it is on Recent, Report a problem and Edit a copy. */
@Composable
private fun MoreMenu(state: FoodDetailState, onAction: (FoodDetailAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundIconButton(
            icon = AppIcons.MoreVert.described(stringResource(Res.string.adding_detail_menu_cd)),
            onClick = { open = true },
            size = AppTheme.spacing.touchTarget,
            iconSize = IconSize.Medium.dp,
            containerColor = AppTheme.colors.bg,
            contentColor = AppTheme.colors.ink,
        )
        PopoverMenu(expanded = open, onDismissRequest = { open = false }, alignment = Alignment.TopEnd) {
            MoreMenuItems(state, onAction, close = { open = false })
        }
    }
}

@Composable
private fun MoreMenuItems(state: FoodDetailState, onAction: (FoodDetailAction) -> Unit, close: () -> Unit) {
    fun tap(action: FoodDetailAction): () -> Unit = {
        close()
        onAction(action)
    }
    MenuItem(
        label = stringResource(if (state.favorite) Res.string.logger_amount_unfavorite_cd else Res.string.logger_amount_favorite_cd),
        onClick = tap(FoodDetailAction.FavoriteTapped),
        icon = if (state.favorite) AppIcons.Star else AppIcons.StarOutline,
    )
    if (state.inRecents) {
        MenuItem(label = stringResource(Res.string.adding_detail_remove_recent), onClick = tap(FoodDetailAction.RemoveFromRecentsTapped), icon = AppIcons.History)
    }
    MenuItem(label = stringResource(Res.string.adding_detail_report), onClick = tap(FoodDetailAction.ReportTapped), icon = AppIcons.Support)
    MenuItem(label = stringResource(Res.string.adding_detail_edit_copy), onClick = tap(FoodDetailAction.EditCopyTapped), icon = AppIcons.EditNote, showDivider = false)
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    EdgeCard(edgeToEdge = false, contentPadding = EdgeCardDefaults.InsetPadding, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row), content = content)
}

@Composable
private fun CardLabel(text: String) {
    Text(text = text, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
}

/**
 * The rows: Amount, then Meal (only when meal groups are on), Time and How sure (not in No
 * numbers, where it would feed a number the mode never shows). Each is its label on the left and
 * its value on the right, the way a settings list reads.
 */
@Composable
private fun DetailsCard(state: FoodDetailState, onAction: (FoodDetailAction) -> Unit) {
    EdgeCard(edgeToEdge = false, contentPadding = EdgeCardDefaults.InsetPadding, verticalArrangement = Arrangement.Top) {
        AmountRow(state, onAction)
        if (state.slots.size > 1) {
            HorizontalDivider(color = AppTheme.colors.line)
            MealRow(state, onAction)
        }
        HorizontalDivider(color = AppTheme.colors.line)
        TimeRow(state, onAction)
        if (!state.noNumbers) {
            HorizontalDivider(color = AppTheme.colors.line)
            SureRow(state, onAction)
        }
    }
}

@Composable
private fun DetailRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Text(text = label, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
        content()
    }
}

/** A value on the right that opens a menu or a picker: "Dinner ⌄". */
@Composable
private fun RowValue(text: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = AppTheme.spacing.touchTarget)
            .semantics(mergeDescendants = true) {
                role = Role.DropdownList
                contentDescription = description
            }
            .bounceClick(scaleDown = 0.97f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = text, typography = AppTheme.type.body, color = AppTheme.colors.ink, maxLines = 1)
        Icon(icon = AppIcons.ArrowDropDown, size = IconSize.Medium, color = AppTheme.colors.muted)
    }
}

/** The number and its unit on one row, the quick amounts under it, and what the amount weighs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountRow(state: FoodDetailState, onAction: (FoodDetailAction) -> Unit) {
    Column(modifier = Modifier.padding(bottom = AppTheme.spacing.chip), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        DetailRow(stringResource(Res.string.adding_detail_amount)) {
            AmountField(state.amount, onTyped = { onAction(FoodDetailAction.AmountTyped(it)) }, modifier = Modifier.width(AMOUNT_WIDTH))
            UnitDropdown(state, onAction, modifier = Modifier.width(UNIT_WIDTH))
        }
        if (state.servings.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                state.servings.forEachIndexed { index, chip ->
                    PillChip(
                        label = chip.label,
                        selected = chip.selected,
                        onClick = { onAction(FoodDetailAction.ServingPicked(index)) },
                        style = PillChipStyle.Tint,
                    )
                }
            }
        }
        state.weight?.let { grams ->
            Text(
                text = stringResource(Res.string.adding_detail_weighs, grams),
                typography = AppTheme.type.secondary.Tabular,
                color = AppTheme.colors.muted,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MealRow(state: FoodDetailState, onAction: (FoodDetailAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val meal = stringResource(state.slot.label)
    DetailRow(stringResource(Res.string.adding_detail_meal)) {
        Box {
            RowValue(meal, stringResource(Res.string.adding_detail_meal_cd, meal)) { open = true }
            PopoverMenu(expanded = open, onDismissRequest = { open = false }, alignment = Alignment.TopEnd) {
                state.slots.forEachIndexed { index, slot ->
                    MenuItem(
                        label = stringResource(slot.label),
                        onClick = {
                            open = false
                            onAction(FoodDetailAction.SlotPicked(slot))
                        },
                        icon = if (slot == state.slot) AppIcons.Check else null,
                        showDivider = index < state.slots.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeRow(state: FoodDetailState, onAction: (FoodDetailAction) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val clock = DateCopy.time(state.time.hour, state.time.minute)
    val text = if (state.timeIsNow) stringResource(Res.string.adding_detail_time_now, clock) else clock
    DetailRow(stringResource(Res.string.adding_detail_time)) {
        RowValue(text, stringResource(Res.string.adding_detail_time_cd, text)) { picking = true }
    }
    if (picking) {
        TimePickerDialog(
            title = stringResource(Res.string.logger_amount_time_title),
            initial = state.time,
            okLabel = stringResource(Res.string.logger_amount_ok),
            cancelLabel = stringResource(Res.string.logger_amount_cancel),
            onPicked = {
                picking = false
                onAction(FoodDetailAction.TimePicked(it))
            },
            onDismiss = { picking = false },
        )
    }
}

/** How sure, worked out until the person picks: it only widens or narrows the day's ±, so it sits quietly here. */
@Composable
private fun SureRow(state: FoodDetailState, onAction: (FoodDetailAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val sure = stringResource(state.precision.label)
    DetailRow(stringResource(Res.string.adding_detail_sure)) {
        Box {
            RowValue(sure, stringResource(Res.string.adding_detail_sure_cd, sure)) { open = true }
            PopoverMenu(expanded = open, onDismissRequest = { open = false }, alignment = Alignment.TopEnd) {
                FoodDetailStateBuilder.PRECISIONS.forEachIndexed { index, precision ->
                    MenuItem(
                        label = stringResource(precision.label),
                        onClick = {
                            open = false
                            onAction(FoodDetailAction.PrecisionPicked(precision))
                        },
                        icon = if (precision == state.precision) AppIcons.Check else null,
                        showDivider = index < FoodDetailStateBuilder.PRECISIONS.lastIndex,
                    )
                }
            }
        }
    }
}

/** No numbers' optional "How hungry were you?": the Note's scale, Starving to Stuffed; the same answer again clears it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HungerCard(check: HungerCheck, onAction: (FoodDetailAction) -> Unit) {
    Card {
        CardLabel(stringResource(Res.string.modes_hunger_question))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            Hunger.entries.forEach { hunger ->
                PillChip(
                    label = stringResource(hunger.label),
                    selected = hunger == check.picked,
                    onClick = { onAction(FoodDetailAction.HungerPicked(hunger)) },
                    style = PillChipStyle.Tint,
                )
            }
        }
    }
}

/**
 * The typed amount, in an accent outline. A decimal keypad; what is typed goes to the view model
 * as is. A character the view model drops shows until typing pauses, then the field settles back
 * on the view model's value. The library field holds keystrokes until the view model's echo
 * catches up: fed the state directly, a fast "150" rewound to "15" while the echo was a frame behind.
 */
@Composable
private fun AmountField(value: String, onTyped: (String) -> Unit, modifier: Modifier) {
    val description = stringResource(Res.string.adding_detail_amount_cd)
    val shape = AppTheme.shapes.input
    BasicTextField(
        value = value,
        onValueChange = onTyped,
        modifier = modifier.semantics { contentDescription = description },
        typographyToken = AppTheme.type.figureMedium,
        color = AppTheme.colors.ink.color,
        textAlign = TextAlign.End,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        cursorBrush = SolidColor(AppTheme.colors.accent.color),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = FIELD_HEIGHT)
                    .clip(shape)
                    .background(AppTheme.colors.bg)
                    .border(2.dp, AppTheme.colors.accent.color, shape)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { inner() }
        },
    )
}

/** "tbsp ⌄": each unit with its weight in the menu, the one in use checked, then "Custom size…". */
@Composable
private fun UnitDropdown(state: FoodDetailState, onAction: (FoodDetailAction) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val unit = state.unit ?: return
    val description = stringResource(Res.string.logger_amount_unit_cd, unit.label)
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = FIELD_HEIGHT)
                .clip(AppTheme.shapes.input)
                .border(1.dp, AppTheme.colors.line.color, AppTheme.shapes.input)
                .semantics(mergeDescendants = true) {
                    role = Role.DropdownList
                    contentDescription = description
                }
                .bounceClick(scaleDown = 0.98f) { open = true }
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = unit.label,
                typography = AppTheme.type.bodyBold,
                color = AppTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(icon = AppIcons.ArrowDropDown, size = IconSize.Medium, color = AppTheme.colors.muted)
        }
        PopoverMenu(expanded = open, onDismissRequest = { open = false }, alignment = Alignment.TopEnd) {
            UnitMenuItems(state, onAction, close = { open = false })
        }
    }
}

@Composable
private fun UnitMenuItems(state: FoodDetailState, onAction: (FoodDetailAction) -> Unit, close: () -> Unit) {
    state.units.forEachIndexed { index, option ->
        MenuRow(label = option.label, detail = option.grams, selected = index == state.unitIndex) {
            close()
            onAction(FoodDetailAction.UnitPicked(index))
        }
    }
    if (state.canAddUnit) {
        MenuRow(label = stringResource(Res.string.logger_amount_custom), detail = null, selected = false) {
            close()
            onAction(FoodDetailAction.CustomSizeTapped)
        }
    }
}

/** A menu line with a weight on the right and a check when it is the one in use. */
@Composable
private fun MenuRow(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) AppTheme.colors.accentTint else AppTheme.colors.surface)
            .semantics { role = Role.Button }
            .bounceClick(scaleDown = 0.99f, onClick = onClick)
            .defaultMinSize(minHeight = AppTheme.spacing.touchTarget)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
    ) {
        Text(text = label, typography = AppTheme.type.menuItem, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
        detail?.let { Text(text = it, typography = AppTheme.type.secondary.Tabular, color = AppTheme.colors.muted) }
        if (selected) Icon(icon = AppIcons.Check, size = IconSize.Medium, color = AppTheme.colors.accent)
    }
}

/** The calories big, the macros split end to end, then a line per macro: its grams and its share of the energy. */
@Composable
private fun BreakdownCard(breakdown: Breakdown) {
    Card {
        if (breakdown.energy is Display.Shown) {
            Row(verticalAlignment = Alignment.Bottom) {
                NutrientText(metric = Metric.Calories, display = breakdown.energy, typography = AppTheme.type.figureDisplay, color = AppTheme.colors.ink)
                Spacer(Modifier.width(4.dp))
                Text(text = stringResource(Res.string.logger_amount_kcal), typography = AppTheme.type.bodyBold, color = AppTheme.colors.muted, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        val split = breakdown.macros.filter { it.weight > 0f }
        if (split.isNotEmpty()) {
            RangeBar(
                fill = 1f,
                rangeStart = 0f,
                rangeEnd = 0f,
                colors = RangeBarDefaults.summary(),
                segments = split.map { RangeBarSegment(it.weight, it.metric.tone()) },
                height = 12.dp,
            )
        }
        breakdown.macros.forEach { macro -> MacroLine(macro) }
    }
}

@Composable
private fun MacroLine(macro: MacroRow) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        Box(Modifier.size(10.dp).background(macro.metric.tone(), CircleShape))
        Text(text = stringResource(macro.metric.amountLabel), typography = AppTheme.type.body, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
        NutrientText(metric = macro.metric, display = macro.amount, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink, maxLines = 1)
        NutrientText(
            metric = macro.metric,
            display = macro.share,
            typography = AppTheme.type.secondary.Tabular,
            color = AppTheme.colors.muted,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(SHARE_WIDTH),
        )
    }
}

/** Today with this: each number the day comes to with this food, the food's part of the bar in its colour. */
@Composable
private fun TargetsCard(targets: TodayTargets) {
    Card {
        CardLabel(stringResource(Res.string.adding_detail_today))
        targets.rows.forEach { row -> TargetLine(row) }
    }
}

@Composable
private fun TargetLine(row: TargetRow) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            Text(text = stringResource(row.metric.targetLabel), typography = AppTheme.type.body, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
            TargetTotal(row)
            if (row.adds is Display.Shown) {
                Text(
                    text = stringResource(Res.string.adding_detail_adds, row.adds.text),
                    typography = AppTheme.type.secondary.Tabular,
                    color = AppTheme.colors.muted,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(SHARE_WIDTH).testTag(row.metric.testTag()),
                )
            }
        }
        RangeBar(
            fill = (row.soFar + row.food).coerceAtMost(1f),
            rangeStart = 0f,
            rangeEnd = 0f,
            colors = RangeBarDefaults.summary(),
            segments = listOf(RangeBarSegment(row.soFar, AppTheme.colors.muted), RangeBarSegment(row.food, row.metric.tone())),
            height = 8.dp,
        )
    }
}

@Composable
private fun TargetTotal(row: TargetRow) {
    val bare = row.bare
    val plusMinus = row.plusMinus
    if (bare is Display.Shown && plusMinus is Display.Shown) {
        Text(
            text = stringResource(Res.string.adding_detail_today_plus_minus, bare.text, plusMinus.text),
            typography = AppTheme.type.bodyBold.Tabular,
            color = AppTheme.colors.ink,
            modifier = Modifier.testTag(Metric.Calories.testTag()),
        )
    } else {
        NutrientText(metric = row.metric, display = row.total, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink, maxLines = 1)
    }
}

/** Where the numbers come from, how many it lists and All nutrients. Open Food Facts' licence asks for the credit. */
@Composable
private fun SourceRow(source: SourceLine, nutrients: AllNutrients?, onAction: (FoodDetailAction) -> Unit) {
    val origin = source.source?.let { stringResource(it.label) }
    val review = source.review?.takeIf { it != SourceReview.Verified }
    if (origin == null && nutrients == null && review == null) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            origin?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    // Someone accountable stands behind these numbers: a check, no more words.
                    if (source.review == SourceReview.Verified) {
                        Icon(icon = AppIcons.CheckCircle.described(stringResource(Res.string.search_badge_verified_cd)), size = 12.dp, color = AppTheme.colors.muted)
                    }
                    Text(text = it, typography = AppTheme.type.caption, color = AppTheme.colors.muted)
                }
            }
            review?.let {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(icon = if (it == SourceReview.YoursChecked) AppIcons.CheckCircle else AppIcons.Schedule, size = 12.dp, color = AppTheme.colors.muted)
                    Text(text = stringResource(it.label), typography = AppTheme.type.caption, color = AppTheme.colors.muted)
                }
            }
            source.nutrientCount?.let { count ->
                Text(text = pluralStringResource(Res.plurals.adding_detail_nutrient_count, count, count), typography = AppTheme.type.caption, color = AppTheme.colors.muted)
            }
        }
        nutrients?.let { AllNutrientsLink(it, onAction) }
    }
}

@Composable
private fun AllNutrientsLink(nutrients: AllNutrients, onAction: (FoodDetailAction) -> Unit) {
    val state = stringResource(if (nutrients.open) Res.string.logger_amount_open else Res.string.logger_amount_closed)
    val description = stringResource(Res.string.adding_detail_all_cd, state)
    Row(
        modifier = Modifier
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
            }
            .bounceClick(scaleDown = 0.97f) { onAction(FoodDetailAction.AllNutrientsTapped) }
            .defaultMinSize(minHeight = AppTheme.spacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = stringResource(Res.string.adding_detail_all), typography = AppTheme.type.bodyBold, color = AppTheme.colors.accent)
        Icon(icon = if (nutrients.open) AppIcons.ExpandMore else AppIcons.ChevronRight, size = IconSize.Medium, color = AppTheme.colors.accent)
    }
}

/** All nutrients, opened from the source row: vitamins, then minerals, as a percent of a day. */
@Composable
private fun NutrientsSection(nutrients: AllNutrients) {
    Card {
        nutrients.groups.forEachIndexed { index, group ->
            Text(
                text = stringResource(group.group.label),
                typography = AppTheme.type.secondarySmallStrong,
                color = AppTheme.colors.muted,
                modifier = if (index > 0) Modifier.padding(top = AppTheme.spacing.row) else Modifier,
            )
            group.rows.forEach { row -> NutrientLine(row) }
        }
    }
}

@Composable
private fun NutrientLine(row: NutrientRow) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = stringResource(row.micro.label), typography = AppTheme.type.body, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
        if (row.notListed) {
            Text(text = stringResource(Res.string.logger_micro_not_listed), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            return@Row
        }
        when (val shown = row.display) {
            is Display.Shown -> Text(
                text = stringResource(Res.string.logger_micro_of_daily, shown.text),
                typography = AppTheme.type.secondary.Tabular,
                color = AppTheme.colors.muted,
                modifier = Modifier.testTag(Metric.Micros.testTag()),
            )
            else -> NutrientText(metric = Metric.Micros, display = shown, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
    }
}

/** "Add to {meal}", pinned. Its confirm haptic plays once the log is saved. */
@Composable
private fun AddButton(state: FoodDetailState, onAction: (FoodDetailAction) -> Unit) {
    ButtonPrimary(
        onClick = { onAction(FoodDetailAction.AddToMealTapped) },
        enabled = state.canAdd,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, top = AppTheme.spacing.row, bottom = AppTheme.spacing.page),
    ) {
        Text(stringResource(Res.string.logger_add_to, stringResource(state.slot.label)), maxLines = 1)
    }
}

@Composable
private fun Metric.tone(): ColorResource = when (this) {
    Metric.Protein -> AppTheme.colors.nutrients.protein.content
    Metric.Carbs -> AppTheme.colors.nutrients.carbs.content
    Metric.Fat -> AppTheme.colors.nutrients.fat.content
    else -> AppTheme.colors.accent
}

private val Metric.amountLabel: StringResource
    get() = when (this) {
        Metric.Protein -> Res.string.logger_amount_protein
        Metric.Carbs -> Res.string.logger_amount_carbs
        else -> Res.string.logger_amount_fat
    }

private val Metric.targetLabel: StringResource
    get() = if (this == Metric.Calories) Res.string.adding_detail_energy else amountLabel

private val FoodOrigin.label: StringResource
    get() = when (this) {
        FoodOrigin.Usda -> Res.string.adding_detail_from_usda
        FoodOrigin.OpenFoodFacts -> Res.string.adding_detail_from_off
        FoodOrigin.Nevo -> Res.string.logger_source_nevo
        FoodOrigin.Opson -> Res.string.adding_detail_from_opson
        FoodOrigin.Yours -> Res.string.adding_detail_from_yours
    }

private val SourceReview.label: StringResource
    get() = when (this) {
        SourceReview.Verified -> Res.string.search_badge_verified_cd
        SourceReview.Unverified -> Res.string.adding_detail_unverified
        SourceReview.YoursInReview -> Res.string.adding_detail_yours_in_review
        SourceReview.YoursChecked -> Res.string.adding_detail_yours_checked
    }

private val MicroGroup.label: StringResource
    get() = when (this) {
        MicroGroup.Vitamins -> Res.string.logger_micros_vitamins
        MicroGroup.Minerals -> Res.string.logger_micros_minerals
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

private val UNIT_MENU_TOP = 110.dp
private val MORE_MENU_TOP = 56.dp

/** The amount field and the unit dropdown beside it, the same height, on the Amount row. */
private val FIELD_HEIGHT = 48.dp
private val AMOUNT_WIDTH = 88.dp
private val UNIT_WIDTH = 128.dp
private val ROW_HEIGHT = 56.dp

/** "17%", "+12%": right-aligned in a column so the bars and amounts line up. */
private val SHARE_WIDTH = 44.dp
