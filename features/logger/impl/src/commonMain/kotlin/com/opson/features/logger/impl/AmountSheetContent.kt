package com.opson.features.logger.impl

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.ui.label
import com.opson.libraries.diary.ui.sentenceName
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
import com.opson.libraries.ui.components.StripedBar
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonStyle
import com.opson.libraries.ui.components.dialog.TimePickerDialog
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.IconSize
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_add_to
import opson.libraries.resources.generated.resources.logger_amount_all
import opson.libraries.resources.generated.resources.logger_amount_all_cd
import opson.libraries.resources.generated.resources.logger_amount_all_sub
import opson.libraries.resources.generated.resources.logger_amount_cancel
import opson.libraries.resources.generated.resources.logger_amount_carbs
import opson.libraries.resources.generated.resources.logger_amount_close_cd
import opson.libraries.resources.generated.resources.logger_amount_closed
import opson.libraries.resources.generated.resources.logger_amount_count_cd
import opson.libraries.resources.generated.resources.logger_amount_custom
import opson.libraries.resources.generated.resources.logger_amount_day
import opson.libraries.resources.generated.resources.logger_amount_day_change
import opson.libraries.resources.generated.resources.logger_amount_energy
import opson.libraries.resources.generated.resources.logger_amount_fat
import opson.libraries.resources.generated.resources.logger_amount_favorite_cd
import opson.libraries.resources.generated.resources.logger_amount_fiber
import opson.libraries.resources.generated.resources.logger_amount_keep_going
import opson.libraries.resources.generated.resources.logger_amount_kcal
import opson.libraries.resources.generated.resources.logger_amount_less_cd
import opson.libraries.resources.generated.resources.logger_amount_meal
import opson.libraries.resources.generated.resources.logger_amount_meal_cd
import opson.libraries.resources.generated.resources.logger_amount_loading_cd
import opson.libraries.resources.generated.resources.logger_amount_missing
import opson.libraries.resources.generated.resources.logger_amount_more_cd
import opson.libraries.resources.generated.resources.logger_amount_now
import opson.libraries.resources.generated.resources.logger_amount_of_energy
import opson.libraries.resources.generated.resources.logger_amount_ok
import opson.libraries.resources.generated.resources.logger_amount_open
import opson.libraries.resources.generated.resources.logger_amount_per_only_source
import opson.libraries.resources.generated.resources.logger_amount_per_source
import opson.libraries.resources.generated.resources.logger_amount_protein
import opson.libraries.resources.generated.resources.logger_amount_striped
import opson.libraries.resources.generated.resources.logger_amount_sure
import opson.libraries.resources.generated.resources.logger_amount_this_portion
import opson.libraries.resources.generated.resources.logger_amount_time
import opson.libraries.resources.generated.resources.logger_amount_time_cd
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
import opson.libraries.resources.generated.resources.logger_precision_estimate
import opson.libraries.resources.generated.resources.logger_precision_named
import opson.libraries.resources.generated.resources.logger_precision_weighed
import opson.libraries.resources.generated.resources.logger_source_off
import opson.libraries.resources.generated.resources.logger_source_usda
import opson.libraries.resources.generated.resources.logger_source_yours
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val Precision.label: StringResource
    get() = when (this) {
        Precision.Weighed -> Res.string.logger_precision_weighed
        Precision.Estimate -> Res.string.logger_precision_estimate
        Precision.Named -> Res.string.logger_precision_named
    }

/**
 * The amount sheet's body (design sync 2, 3.3): the food with its star, the amount and its unit
 * menu, meal, time and how sure, then "This portion", "Your day with this" and All nutrients, each
 * only when the policy leaves something in it, and the two pinned buttons. Stateless apart from
 * which menu or dialog is open; the entry point puts it in a `BottomSheet` with a fixed height so
 * the buttons stay at the bottom.
 */
@Composable
fun AmountSheetContent(
    state: AmountState,
    onAction: (AmountAction) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * For the unit menu's preview (3.3b): the menu drawn in place. The real menu is a popup, which
     * a screenshot cannot see.
     */
    unitMenuShown: Boolean = false,
    /** Where the cards start. A preview passes one at the bottom so All nutrients is on screen. */
    scrollState: ScrollState = rememberScrollState(),
) {
    Box(modifier = modifier.fillMaxSize()) {
        AmountColumn(state, onAction, scrollState)
        if (unitMenuShown) {
            MenuPopover(modifier = Modifier.align(Alignment.TopEnd).padding(top = UNIT_MENU_TOP, end = AppTheme.spacing.page)) {
                UnitMenuItems(state, onAction, close = {})
            }
        }
    }
    state.customSize?.let { CustomSizeDialog(it, onAction) }
}

@Composable
private fun AmountColumn(state: AmountState, onAction: (AmountAction) -> Unit, scrollState: ScrollState) {
    Column(modifier = Modifier.fillMaxSize()) {
        Header(state, onAction)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            if (state.missing) {
                Text(text = stringResource(Res.string.logger_amount_missing), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            } else if (state.loading) {
                DelayedLoadingIndicator(contentDescription = stringResource(Res.string.logger_amount_loading_cd))
            } else {
                if (state.showAmount) AmountCard(state, onAction)
                WhenCard(state, onAction)
                state.portion?.let { PortionSection(it) }
                state.day?.let { DaySection(it) }
                state.nutrients?.let { NutrientsSection(it, onAction) }
                state.source?.let { SourceCaption(it) }
            }
        }
        if (!state.loading && !state.missing) Buttons(state, onAction)
    }
}

/** Close, the food and its tag, and the star. */
@Composable
private fun Header(state: AmountState, onAction: (AmountAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.chip, end = AppTheme.spacing.chip, bottom = AppTheme.spacing.row),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
    ) {
        RoundIconButton(
            icon = AppIcons.Close.described(stringResource(Res.string.logger_amount_close_cd)),
            onClick = { onAction(AmountAction.BackTapped) },
            size = AppTheme.spacing.touchTarget,
            iconSize = IconSize.Medium.dp,
            containerColor = AppTheme.colors.bg,
            contentColor = AppTheme.colors.ink,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = state.name, typography = AppTheme.type.sheetTitle, color = AppTheme.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            state.tag?.let { Text(text = it, typography = AppTheme.type.secondary, color = AppTheme.colors.muted, maxLines = 1) }
        }
        if (!state.loading && !state.missing) {
            val description = stringResource(if (state.favorite) Res.string.logger_amount_unfavorite_cd else Res.string.logger_amount_favorite_cd)
            RoundIconButton(
                icon = (if (state.favorite) AppIcons.Star else AppIcons.StarOutline).described(description),
                onClick = { onAction(AmountAction.FavoriteTapped) },
                size = AppTheme.spacing.touchTarget,
                iconSize = IconSize.Medium.dp,
                containerColor = AppTheme.colors.bg,
                contentColor = if (state.favorite) AppTheme.colors.accent else AppTheme.colors.muted,
            )
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    EdgeCard(edgeToEdge = false, contentPadding = EdgeCardDefaults.InsetPadding, content = content)
}

/** − count + and the unit menu, then the quick amounts. */
@Composable
private fun AmountCard(state: AmountState, onAction: (AmountAction) -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            StepButton(AppIcons.Remove, stringResource(Res.string.logger_amount_less_cd), enabled = state.canLess) { onAction(AmountAction.LessTapped) }
            val countDescription = stringResource(Res.string.logger_amount_count_cd)
            Box(
                modifier = Modifier
                    .width(COUNT_WIDTH)
                    .height(AppTheme.spacing.touchTarget)
                    .clip(AppTheme.shapes.inputLarge)
                    .border(2.dp, AppTheme.colors.accent.color, AppTheme.shapes.inputLarge)
                    .semantics { contentDescription = countDescription },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = state.count, typography = AppTheme.type.figureMedium, color = AppTheme.colors.ink, maxLines = 1)
            }
            StepButton(AppIcons.Add, stringResource(Res.string.logger_amount_more_cd)) { onAction(AmountAction.MoreTapped) }
            UnitMenu(state, onAction, modifier = Modifier.weight(1f))
        }
        if (state.quick.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                state.quick.forEach { quick ->
                    QuickChip(quick, modifier = Modifier.weight(1f)) { onAction(AmountAction.QuickAmountPicked(quick.quantity)) }
                }
            }
        }
    }
}

@Composable
private fun StepButton(icon: IconResource, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    RoundIconButton(
        icon = icon.described(description),
        onClick = onClick,
        size = AppTheme.spacing.touchTarget,
        containerColor = AppTheme.colors.soft,
        contentColor = if (enabled) AppTheme.colors.ink else AppTheme.colors.muted,
        enabled = enabled,
    )
}

@Composable
private fun QuickChip(quick: QuickAmount, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = AppTheme.spacing.touchTarget)
            .clip(AppTheme.shapes.input)
            .background(if (quick.selected) AppTheme.colors.accentTint else AppTheme.colors.surface)
            .border(1.dp, (if (quick.selected) AppTheme.colors.accentTint else AppTheme.colors.line).color, AppTheme.shapes.input)
            .semantics { role = Role.Button }
            .bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = quick.label, typography = AppTheme.type.bodyBold, color = if (quick.selected) AppTheme.colors.accent else AppTheme.colors.ink)
    }
}

/** "plate · 350 g ⌄": each unit with its weight, the selected one checked, then "Custom size…". */
@Composable
private fun UnitMenu(state: AmountState, onAction: (AmountAction) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val unit = state.unit ?: return
    val label = listOfNotNull(unit.label, unit.grams).joinToString(UNIT_SEPARATOR)
    Box(modifier = modifier) {
        Pill(description = stringResource(Res.string.logger_amount_unit_cd, label), onClick = { open = true }) {
            // The weight is what makes a unit more than a guess (3.3b), so a long name never pushes it off:
            // "container (7 oz)" goes over "200 g" instead of being cut to "container (7 oz) · 2…".
            OneLineOrStacked(
                oneLine = { PillText(label) },
                stacked = {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(text = unit.label, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        unit.grams?.let { Text(text = it, typography = AppTheme.type.secondary.Tabular, color = AppTheme.colors.muted, maxLines = 1) }
                    }
                },
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        PopoverMenu(expanded = open, onDismissRequest = { open = false }, alignment = Alignment.TopEnd) {
            UnitMenuItems(state, onAction, close = { open = false })
        }
    }
}

@Composable
private fun UnitMenuItems(state: AmountState, onAction: (AmountAction) -> Unit, close: () -> Unit) {
    state.units.forEachIndexed { index, option ->
        MenuRow(
            label = option.label,
            detail = option.grams,
            selected = index == state.unitIndex,
            onClick = {
                close()
                onAction(AmountAction.UnitPicked(index))
            },
        )
    }
    if (state.canAddUnit) {
        MenuRow(label = stringResource(Res.string.logger_amount_custom), detail = null, selected = false) {
            close()
            onAction(AmountAction.CustomSizeTapped)
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

/** A soft rounded pill with a ⌄, for the meal and time menus. */
@Composable
private fun Pill(label: String, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Pill(description = description, modifier = modifier, onClick = onClick) {
        PillText(label, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun PillText(label: String, modifier: Modifier = Modifier) {
    Text(text = label, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

@Composable
private fun Pill(description: String, modifier: Modifier = Modifier, onClick: () -> Unit, label: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = AppTheme.spacing.touchTarget)
            .clip(AppTheme.shapes.input)
            .background(AppTheme.colors.soft)
            .semantics(mergeDescendants = true) {
                role = Role.DropdownList
                contentDescription = description
            }
            .bounceClick(scaleDown = 0.98f, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        label()
        Icon(icon = AppIcons.ExpandMore, size = IconSize.Small, color = AppTheme.colors.ink)
    }
}

/** [oneLine] when it fits the width on offer, else [stacked]. Only the one in use is placed. */
@Composable
private fun OneLineOrStacked(oneLine: @Composable () -> Unit, stacked: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Layout(contents = listOf(oneLine, stacked), modifier = modifier) { (single, twoLines), constraints ->
        val line = single.first()
        val chosen = if (line.maxIntrinsicWidth(constraints.maxHeight) <= constraints.maxWidth) line else twoLines.first()
        val placeable = chosen.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/** Meal, time and how sure. */
@Composable
private fun WhenCard(state: AmountState, onAction: (AmountAction) -> Unit) {
    Card {
        LabeledRow(stringResource(Res.string.logger_amount_meal)) {
            var open by remember { mutableStateOf(false) }
            val meal = stringResource(state.slot.label)
            Box {
                Pill(label = meal, description = stringResource(Res.string.logger_amount_meal_cd, meal)) { if (state.slots.size > 1) open = true }
                PopoverMenu(expanded = open, onDismissRequest = { open = false }, alignment = Alignment.TopEnd) {
                    state.slots.forEachIndexed { index, slot ->
                        MenuItem(
                            label = stringResource(slot.label),
                            onClick = {
                                open = false
                                onAction(AmountAction.SlotPicked(slot))
                            },
                            icon = if (slot == state.slot) AppIcons.Check else null,
                            showDivider = index < state.slots.lastIndex,
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = AppTheme.colors.line)
        LabeledRow(stringResource(Res.string.logger_amount_time)) {
            var picking by remember { mutableStateOf(false) }
            val time = DateCopy.time(state.time.hour, state.time.minute)
            if (state.timeIsNow) Text(text = stringResource(Res.string.logger_amount_now), typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
            Pill(label = time, description = stringResource(Res.string.logger_amount_time_cd, time)) { picking = true }
            if (picking) {
                TimePickerDialog(
                    title = stringResource(Res.string.logger_amount_time_title),
                    initial = state.time,
                    okLabel = stringResource(Res.string.logger_amount_ok),
                    cancelLabel = stringResource(Res.string.logger_amount_cancel),
                    onPicked = {
                        picking = false
                        onAction(AmountAction.TimePicked(it))
                    },
                    onDismiss = { picking = false },
                )
            }
        }
        HorizontalDivider(color = AppTheme.colors.line)
        Text(text = stringResource(Res.string.logger_amount_sure), typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
        SegmentedControl(
            options = Precision.entries,
            selected = state.precision,
            onSelect = { onAction(AmountAction.PrecisionPicked(it)) },
            label = { stringResource(it.label) },
        )
    }
}

@Composable
private fun LabeledRow(label: String, trailing: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
    ) {
        Text(text = label, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** "This portion": the energy big, the weight, the split bar and a column per macro. */
@Composable
private fun PortionSection(portion: PortionCard) {
    Card {
        Text(text = stringResource(Res.string.logger_amount_this_portion), typography = AppTheme.type.secondarySmallStrong, color = AppTheme.colors.muted)
        Row(verticalAlignment = Alignment.Bottom) {
            if (portion.energy is Display.Shown) {
                NutrientText(metric = Metric.Calories, display = portion.energy, typography = AppTheme.type.figureDisplay, color = AppTheme.colors.ink)
                Spacer(Modifier.width(4.dp))
                Text(text = stringResource(Res.string.logger_amount_kcal), typography = AppTheme.type.bodyBold, color = AppTheme.colors.muted, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.weight(1f))
            portion.grams?.let { Text(text = it, typography = AppTheme.type.secondary.Tabular, color = AppTheme.colors.muted) }
        }
        if (portion.showsSplit) SplitBar(portion.macros.filter { it.metric != Metric.Fiber })
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            portion.macros.forEach { macro ->
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(macro.metric.amountLabel), typography = AppTheme.type.secondarySmallStrong, color = macroColor(macro.metric))
                    NutrientText(metric = macro.metric, display = macro.amount, typography = AppTheme.type.figureMedium, color = AppTheme.colors.ink, maxLines = 1)
                    val share = macro.share
                    if (share is Display.Shown) {
                        Text(
                            text = stringResource(Res.string.logger_amount_of_energy, share.text),
                            typography = AppTheme.type.caption,
                            color = AppTheme.colors.muted,
                            modifier = Modifier.testTag(macro.metric.testTag()),
                        )
                    }
                }
            }
        }
    }
}

/** The energy split by macro, each segment as wide as its share. Same colours as everywhere; none means good or bad. */
@Composable
private fun SplitBar(macros: List<PortionMacro>) {
    Row(modifier = Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        macros.forEach { macro ->
            val share = ((macro.amount as? Display.Shown)?.fraction ?: 0f).coerceAtLeast(MIN_SEGMENT)
            Box(
                modifier = Modifier
                    .weight(share)
                    .fillMaxSize()
                    .clip(AppTheme.shapes.bar)
                    .background(macroColor(macro.metric)),
            )
        }
    }
}

/** "Your day with this": today so far solid, this food striped on top. */
@Composable
private fun DaySection(day: DayWithThis) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(Res.string.logger_amount_day), typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
            Text(text = stringResource(Res.string.logger_amount_striped), typography = AppTheme.type.caption, color = AppTheme.colors.muted)
        }
        day.rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = AppTheme.colors.line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(row.metric.amountLabel), typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
                val before = row.before
                val added = row.added
                val after = row.after
                val text = when {
                    before is Display.Shown && added is Display.Shown && after is Display.Shown ->
                        stringResource(Res.string.logger_amount_day_change, before.text, added.text, after.text)
                    after is Display.Shown -> after.text
                    after is Display.Soft -> after.text
                    else -> null
                }
                text?.let {
                    Text(
                        text = it,
                        typography = AppTheme.type.secondary.Tabular,
                        color = AppTheme.colors.muted,
                        textAlign = TextAlign.End,
                        modifier = Modifier.testTag(row.metric.testTag()),
                    )
                }
            }
            StripedBar(
                before = row.before.fraction() ?: 0f,
                after = row.after.fraction() ?: 0f,
                color = if (row.metric == Metric.Calories) AppTheme.colors.ink else macroColor(row.metric),
                modifier = Modifier.testTag(row.metric.testTag()),
            )
        }
    }
}

private fun Display.fraction(): Float? = when (this) {
    is Display.Shown -> fraction
    is Display.Soft -> fraction
    Display.Hidden -> null
}

/** All nutrients, collapsed until tapped. */
@Composable
private fun NutrientsSection(nutrients: AllNutrients, onAction: (AmountAction) -> Unit) {
    Card {
        val state = stringResource(if (nutrients.open) Res.string.logger_amount_open else Res.string.logger_amount_closed)
        val description = stringResource(Res.string.logger_amount_all_cd, state)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = description
                }
                .bounceClick(scaleDown = 0.99f) { onAction(AmountAction.AllNutrientsTapped) }
                .defaultMinSize(minHeight = AppTheme.spacing.touchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(Res.string.logger_amount_all), typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
                Text(text = stringResource(Res.string.logger_amount_all_sub), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            }
            Icon(icon = if (nutrients.open) AppIcons.ExpandMore else AppIcons.ChevronRight, size = IconSize.Medium, color = AppTheme.colors.muted)
        }
        if (nutrients.open) {
            nutrients.groups.forEach { group ->
                Text(
                    text = stringResource(group.group.label),
                    typography = AppTheme.type.secondarySmallStrong,
                    color = AppTheme.colors.muted,
                    modifier = Modifier.padding(top = AppTheme.spacing.row),
                )
                group.rows.forEach { row -> NutrientLine(row) }
            }
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

/** "Per 350 g plate · Source: USDA FoodData Central". Amounts of food, not nutrients. */
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

/**
 * "Pick and keep going" beside "Add to {meal}". The design draws them the same width; at phone
 * width the longer label needs its own room, so it sizes to its text and "Add to" takes the rest.
 */
@Composable
private fun Buttons(state: AmountState, onAction: (AmountAction) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = AppTheme.colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, top = AppTheme.spacing.row, bottom = AppTheme.spacing.page),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ButtonPrimary(
                onClick = { onAction(AmountAction.PickAndKeepGoingTapped) },
                style = ButtonStyle.Tonal,
            ) {
                Text(stringResource(Res.string.logger_amount_keep_going), typography = AppTheme.type.buttonSmall, maxLines = 1)
            }
            ButtonPrimary(onClick = { onAction(AmountAction.AddToMealTapped) }, modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.logger_add_to, stringResource(state.slot.sentenceName)),
                    typography = AppTheme.type.buttonSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun macroColor(metric: Metric): ColorResource {
    val nutrients = AppTheme.colors.nutrients
    return when (metric) {
        Metric.Protein -> nutrients.protein.content
        Metric.Carbs -> nutrients.carbs.content
        Metric.Fat -> nutrients.fat.content
        Metric.Fiber -> nutrients.fiber.content
        else -> AppTheme.colors.muted
    }
}

private val Metric.amountLabel: StringResource
    get() = when (this) {
        Metric.Protein -> Res.string.logger_amount_protein
        Metric.Carbs -> Res.string.logger_amount_carbs
        Metric.Fat -> Res.string.logger_amount_fat
        Metric.Fiber -> Res.string.logger_amount_fiber
        else -> Res.string.logger_amount_energy
    }

private val FoodOrigin.label: StringResource
    get() = when (this) {
        FoodOrigin.Usda -> Res.string.logger_source_usda
        FoodOrigin.OpenFoodFacts -> Res.string.logger_source_off
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

private val COUNT_WIDTH = 64.dp
private const val UNIT_SEPARATOR = " · "
private val UNIT_MENU_TOP = 120.dp
private const val MIN_SEGMENT = 0.02f
