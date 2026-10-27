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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.chip.PillChip
import com.opson.libraries.ui.components.chip.PillChipStyle
import com.opson.libraries.ui.components.dialog.TimePickerDialog
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconSize
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.adding_detail_all
import opson.libraries.resources.generated.resources.adding_detail_all_cd
import opson.libraries.resources.generated.resources.adding_detail_amount
import opson.libraries.resources.generated.resources.adding_detail_amount_cd
import opson.libraries.resources.generated.resources.adding_detail_back_cd
import opson.libraries.resources.generated.resources.adding_detail_label
import opson.libraries.resources.generated.resources.adding_detail_meal
import opson.libraries.resources.generated.resources.adding_detail_menu_cd
import opson.libraries.resources.generated.resources.adding_detail_sure
import opson.libraries.resources.generated.resources.adding_detail_time
import opson.libraries.resources.generated.resources.adding_detail_time_now
import opson.libraries.resources.generated.resources.adding_detail_today
import opson.libraries.resources.generated.resources.adding_detail_today_plus_minus
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
import opson.libraries.resources.generated.resources.logger_amount_per_only_source
import opson.libraries.resources.generated.resources.logger_amount_per_source
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
import opson.libraries.resources.generated.resources.modes_hunger_question
import opson.libraries.resources.generated.resources.logger_precision_estimate
import opson.libraries.resources.generated.resources.logger_precision_named
import opson.libraries.resources.generated.resources.logger_precision_weighed
import opson.libraries.resources.generated.resources.logger_source_nevo
import opson.libraries.resources.generated.resources.logger_source_off
import opson.libraries.resources.generated.resources.logger_source_usda
import opson.libraries.resources.generated.resources.logger_source_yours
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val Precision.label: StringResource
    get() = when (this) {
        Precision.Weighed -> Res.string.logger_precision_weighed
        Precision.Estimate -> Res.string.logger_precision_estimate
        Precision.Label -> Res.string.adding_detail_label
        Precision.Named -> Res.string.logger_precision_named
    }

/**
 * The food detail's body (design sync 4, 13.4d): back, the food and ⋮; the amount with its unit
 * and serving chips, how sure; the energy card when the policy leaves anything in it, All
 * nutrients under it when open; and "Add to {meal}" pinned. Stateless apart from which menu or
 * dialog is open.
 */
@Composable
fun FoodDetailContent(
    state: FoodDetailState,
    onAction: (FoodDetailAction) -> Unit,
    modifier: Modifier = Modifier,
    /** For the unit menu's preview: the menu drawn in place. The real menu is a popup, which a screenshot cannot see. */
    unitMenuShown: Boolean = false,
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
                        AmountCard(state, onAction)
                        state.hunger?.let { HungerCard(it, onAction) }
                        state.energy?.let { EnergySection(it, state.nutrients, onAction) }
                        state.nutrients?.takeIf { it.open }?.let { NutrientsSection(it) }
                        state.source?.let { SourceCaption(it) }
                    }
                }
            }
            if (!state.loading && !state.missing) AddButton(state, onAction)
        }
        if (unitMenuShown) {
            MenuPopover(modifier = Modifier.align(Alignment.TopEnd).padding(top = UNIT_MENU_TOP, end = AppTheme.spacing.page)) {
                UnitMenuItems(state, onAction, close = {})
            }
        }
    }
    state.customSize?.let { CustomSizeDialog(it, onAction) }
}

/** Back, the food and its tag, and ⋮ with the meal, the time and the favorite. */
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

private enum class MoreMenuPage { Closed, Main, Meals }

@Composable
private fun MoreMenu(state: FoodDetailState, onAction: (FoodDetailAction) -> Unit) {
    var page by remember { mutableStateOf(MoreMenuPage.Closed) }
    var pickingTime by remember { mutableStateOf(false) }
    Box {
        RoundIconButton(
            icon = AppIcons.MoreVert.described(stringResource(Res.string.adding_detail_menu_cd)),
            onClick = { page = MoreMenuPage.Main },
            size = AppTheme.spacing.touchTarget,
            iconSize = IconSize.Medium.dp,
            containerColor = AppTheme.colors.bg,
            contentColor = AppTheme.colors.ink,
        )
        PopoverMenu(expanded = page == MoreMenuPage.Main, onDismissRequest = { page = MoreMenuPage.Closed }, alignment = Alignment.TopEnd) {
            val meal = stringResource(state.slot.label)
            if (state.slots.size > 1) {
                MenuItem(label = stringResource(Res.string.adding_detail_meal, meal), onClick = { page = MoreMenuPage.Meals }, icon = AppIcons.Restaurant)
            }
            val clock = DateCopy.time(state.time.hour, state.time.minute)
            MenuItem(
                label = stringResource(if (state.timeIsNow) Res.string.adding_detail_time_now else Res.string.adding_detail_time, clock),
                onClick = {
                    page = MoreMenuPage.Closed
                    pickingTime = true
                },
                icon = AppIcons.Schedule,
            )
            MenuItem(
                label = stringResource(if (state.favorite) Res.string.logger_amount_unfavorite_cd else Res.string.logger_amount_favorite_cd),
                onClick = {
                    page = MoreMenuPage.Closed
                    onAction(FoodDetailAction.FavoriteTapped)
                },
                icon = if (state.favorite) AppIcons.Star else AppIcons.StarOutline,
                showDivider = false,
            )
        }
        PopoverMenu(expanded = page == MoreMenuPage.Meals, onDismissRequest = { page = MoreMenuPage.Closed }, alignment = Alignment.TopEnd) {
            state.slots.forEachIndexed { index, slot ->
                MenuItem(
                    label = stringResource(slot.label),
                    onClick = {
                        page = MoreMenuPage.Closed
                        onAction(FoodDetailAction.SlotPicked(slot))
                    },
                    icon = if (slot == state.slot) AppIcons.Check else null,
                    showDivider = index < state.slots.lastIndex,
                )
            }
        }
    }
    if (pickingTime) {
        TimePickerDialog(
            title = stringResource(Res.string.logger_amount_time_title),
            initial = state.time,
            okLabel = stringResource(Res.string.logger_amount_ok),
            cancelLabel = stringResource(Res.string.logger_amount_cancel),
            onPicked = {
                pickingTime = false
                onAction(FoodDetailAction.TimePicked(it))
            },
            onDismiss = { pickingTime = false },
        )
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    EdgeCard(edgeToEdge = false, contentPadding = EdgeCardDefaults.InsetPadding, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row), content = content)
}

@Composable
private fun CardLabel(text: String) {
    Text(text = text, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
}

/** The typed amount and its unit, the serving chips, and how sure. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountCard(state: FoodDetailState, onAction: (FoodDetailAction) -> Unit) {
    Card {
        CardLabel(stringResource(Res.string.adding_detail_amount))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            AmountField(state.amount, onTyped = { onAction(FoodDetailAction.AmountTyped(it)) }, modifier = Modifier.weight(1f))
            UnitDropdown(state, onAction, modifier = Modifier.weight(1f))
        }
        if (state.servings.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                state.servings.forEachIndexed { index, chip ->
                    ServingChipView(chip) { onAction(FoodDetailAction.ServingPicked(index)) }
                }
            }
        }
        // How sure feeds the day's ±, a number No numbers never shows, so it is not asked there.
        if (!state.noNumbers) {
            CardLabel(stringResource(Res.string.adding_detail_sure))
            SegmentedControl(
                options = FoodDetailStateBuilder.PRECISIONS,
                selected = state.precision,
                onSelect = { onAction(FoodDetailAction.PrecisionPicked(it)) },
                label = { stringResource(it.label) },
            )
        }
    }
}

/** No numbers' optional "How hungry were you?" (13.5e): the Note's scale, Starving to Stuffed; the same answer again clears it. */
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
 * The typed amount, big, in an accent outline (13.4d). A decimal keypad; what is typed goes to
 * the view model as is and comes back sanitized, so a stray character never shows.
 */
@Composable
private fun AmountField(value: String, onTyped: (String) -> Unit, modifier: Modifier) {
    val description = stringResource(Res.string.adding_detail_amount_cd)
    val shape = AppTheme.shapes.inputLarge
    BasicTextField(
        value = value,
        onValueChange = onTyped,
        modifier = modifier.semantics { contentDescription = description },
        textStyle = AppTheme.type.figureMedium.style(AppTheme.colors.ink.color),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        cursorBrush = SolidColor(AppTheme.colors.accent.color),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = UNIT_HEIGHT)
                    .clip(shape)
                    .background(AppTheme.colors.surface)
                    .border(2.dp, AppTheme.colors.accent.color, shape)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) { inner() }
        },
    )
}

/** "1 tbsp · 16 g": an outlined chip, tinted while the amount is exactly it. */
@Composable
private fun ServingChipView(chip: ServingChip, onClick: () -> Unit) {
    val text = listOfNotNull(chip.label, chip.grams).joinToString(UNIT_SEPARATOR)
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = AppTheme.spacing.touchTarget)
            .clip(AppTheme.shapes.input)
            .background(if (chip.selected) AppTheme.colors.accentTint else AppTheme.colors.surface)
            .border(1.dp, (if (chip.selected) AppTheme.colors.accentTint else AppTheme.colors.line).color, AppTheme.shapes.input)
            .semantics { role = Role.Button }
            .bounceClick(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, typography = AppTheme.type.bodyBold.Tabular, color = if (chip.selected) AppTheme.colors.accent else AppTheme.colors.ink, maxLines = 1)
    }
}

/** "grams ⌄": each unit with its weight in the menu, the one in use checked, then "Custom size…". */
@Composable
private fun UnitDropdown(state: FoodDetailState, onAction: (FoodDetailAction) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val unit = state.unit ?: return
    val description = stringResource(Res.string.logger_amount_unit_cd, unit.label)
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = UNIT_HEIGHT)
                .clip(AppTheme.shapes.input)
                .border(1.dp, AppTheme.colors.line.color, AppTheme.shapes.input)
                .semantics(mergeDescendants = true) {
                    role = Role.DropdownList
                    contentDescription = description
                }
                .bounceClick(scaleDown = 0.98f) { open = true }
                .padding(horizontal = 14.dp),
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

/** kcal and "All nutrients ›", a tile per macro in its colour, then the day with this food. */
@Composable
private fun EnergySection(energy: EnergyCard, nutrients: AllNutrients?, onAction: (FoodDetailAction) -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                if (energy.energy is Display.Shown) {
                    NutrientText(metric = Metric.Calories, display = energy.energy, typography = AppTheme.type.figureDisplay, color = AppTheme.colors.ink)
                    Spacer(Modifier.width(4.dp))
                    Text(text = stringResource(Res.string.logger_amount_kcal), typography = AppTheme.type.bodyBold, color = AppTheme.colors.muted, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            nutrients?.let { AllNutrientsLink(it, onAction) }
        }
        if (energy.macros.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                energy.macros.forEach { tile -> MacroTileView(tile, Modifier.weight(1f)) }
            }
        }
        energy.today?.let { today ->
            HorizontalDivider(color = AppTheme.colors.line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(Res.string.adding_detail_today), typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted, modifier = Modifier.weight(1f))
                TodayTotal(today)
            }
        }
    }
}

@Composable
private fun TodayTotal(today: TodayWithThis) {
    val bare = today.bare
    val plusMinus = today.plusMinus
    if (bare is Display.Shown && plusMinus is Display.Shown) {
        Text(
            text = stringResource(Res.string.adding_detail_today_plus_minus, bare.text, plusMinus.text),
            typography = AppTheme.type.bodyBold.Tabular,
            color = AppTheme.colors.ink,
            modifier = Modifier.testTag(Metric.Calories.testTag()),
        )
    } else {
        NutrientText(metric = Metric.Calories, display = today.total, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink, maxLines = 1)
    }
}

@Composable
private fun MacroTileView(tile: MacroTile, modifier: Modifier) {
    val tone = when (tile.metric) {
        Metric.Protein -> AppTheme.colors.nutrients.protein
        Metric.Carbs -> AppTheme.colors.nutrients.carbs
        else -> AppTheme.colors.nutrients.fat
    }
    Column(
        modifier = modifier
            .clip(AppTheme.shapes.input)
            .background(tone.container)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(text = stringResource(tile.metric.amountLabel), typography = AppTheme.type.secondarySmallStrong, color = tone.content)
        NutrientText(metric = tile.metric, display = tile.amount, typography = AppTheme.type.figureMedium, color = tone.content, maxLines = 1)
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
        Text(text = stringResource(Res.string.adding_detail_all), typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
        Icon(icon = if (nutrients.open) AppIcons.ExpandMore else AppIcons.ChevronRight, size = IconSize.Medium, color = AppTheme.colors.ink)
    }
}

/** All nutrients, opened from the energy card: vitamins, then minerals, as a percent of a day. */
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

/** "Per 350 g plate · Source: USDA FoodData Central". Amounts of food, not nutrients; Open Food Facts' licence asks for the credit. */
@Composable
private fun SourceCaption(source: SourceLine) {
    val origin = source.source?.let { stringResource(it.label) } ?: return
    val text = source.per?.let { stringResource(Res.string.logger_amount_per_source, it, origin) }
        ?: stringResource(Res.string.logger_amount_per_only_source, origin)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        Icon(icon = AppIcons.CheckCircleOutline, size = IconSize.Small, color = AppTheme.colors.accent)
        Text(text = text, typography = AppTheme.type.caption, color = AppTheme.colors.muted)
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

private val Metric.amountLabel: StringResource
    get() = when (this) {
        Metric.Protein -> Res.string.logger_amount_protein
        Metric.Carbs -> Res.string.logger_amount_carbs
        else -> Res.string.logger_amount_fat
    }

private val FoodOrigin.label: StringResource
    get() = when (this) {
        FoodOrigin.Usda -> Res.string.logger_source_usda
        FoodOrigin.OpenFoodFacts -> Res.string.logger_source_off
        FoodOrigin.Nevo -> Res.string.logger_source_nevo
        FoodOrigin.Yours -> Res.string.logger_source_yours
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

private const val UNIT_SEPARATOR = " · "
private val UNIT_MENU_TOP = 150.dp

/** The amount field and the unit dropdown beside it, the same height. */
private val UNIT_HEIGHT = 64.dp
