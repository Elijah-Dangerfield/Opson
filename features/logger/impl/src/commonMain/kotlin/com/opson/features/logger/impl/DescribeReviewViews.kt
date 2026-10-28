package com.opson.features.logger.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.FoodRowsCard
import com.opson.libraries.diary.ui.FoodRowsNote
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.MenuItem
import com.opson.libraries.ui.components.PopoverMenu
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.TapField
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.SearchField
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.clearFocusOnScroll
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.describe_add_missed
import opson.libraries.resources.generated.resources.describe_find_add_title
import opson.libraries.resources.generated.resources.describe_find_none
import opson.libraries.resources.generated.resources.describe_find_offline
import opson.libraries.resources.generated.resources.describe_find_placeholder
import opson.libraries.resources.generated.resources.describe_find_searching
import opson.libraries.resources.generated.resources.describe_find_swap_title
import opson.libraries.resources.generated.resources.describe_less_cd
import opson.libraries.resources.generated.resources.describe_more_cd
import opson.libraries.resources.generated.resources.describe_not_matched
import opson.libraries.resources.generated.resources.describe_remove_cd
import opson.libraries.resources.generated.resources.describe_search_other
import opson.libraries.resources.generated.resources.describe_swap_cd
import opson.libraries.resources.generated.resources.describe_total_about
import opson.libraries.resources.generated.resources.describe_total_count
import opson.libraries.resources.generated.resources.describe_unit_cd
import opson.libraries.resources.generated.resources.logger_add_to
import opson.libraries.resources.generated.resources.logger_read_as
import opson.libraries.resources.generated.resources.logger_search_clear_cd
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What Describe read, under the box: each line with its own amount, a swap
 * menu on the name and a remove button, "Add something it missed", and the
 * total over Add. Every change is one tap and touches only its line.
 */
@Composable
internal fun DescribedLines(describe: DescribeUi, slot: MealSlot, onAction: (LoggerAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        SectionLabel(stringResource(Res.string.logger_read_as))
        FoodRowsCard {
            describe.lines.forEachIndexed { index, line ->
                DescribedLineRow(line, onAction)
                if (index < describe.lines.lastIndex) HorizontalDivider(color = AppTheme.colors.line)
            }
            ButtonGhost(
                onClick = { onAction(DescribeReviewAction.FindTapped()) },
                icon = AppIcons.Add,
                modifier = Modifier.padding(bottom = AppTheme.spacing.chip),
            ) {
                Text(stringResource(Res.string.describe_add_missed))
            }
        }
    }
    describe.total?.let { DescribeTotalRow(it, slot, onAction) }
}

@Composable
private fun DescribedLineRow(line: DescribedLine, onAction: (LoggerAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.chip),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            LineName(line, onAction, modifier = Modifier.weight(1f))
            NutrientText(
                metric = Metric.Calories,
                display = line.calories,
                typography = AppTheme.type.secondary.Tabular,
                color = AppTheme.colors.muted,
                maxLines = 1,
            )
            RoundIconButton(
                icon = AppIcons.Close.described(stringResource(Res.string.describe_remove_cd, line.name)),
                onClick = { onAction(DescribeReviewAction.RemoveTapped(line.key)) },
                size = AppTheme.spacing.compactTouchTarget,
                containerColor = AppTheme.colors.surface,
                contentColor = AppTheme.colors.muted,
            )
        }
        if (line.matched) {
            LineAmount(line, onAction)
        } else {
            Text(
                text = stringResource(Res.string.describe_not_matched),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.accent,
                modifier = Modifier.bounceClick(scaleDown = 0.98f) { onAction(DescribeReviewAction.FindTapped(line.key)) },
            )
        }
    }
}

/** The food's name; a tap offers the alternatives the server sent, then a search. */
@Composable
private fun LineName(line: DescribedLine, onAction: (LoggerAction) -> Unit, modifier: Modifier = Modifier) {
    var open by remember(line.key) { mutableStateOf(false) }
    val swapDescription = stringResource(Res.string.describe_swap_cd, line.name)
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .bounceClick(scaleDown = 0.98f) { if (line.matched) open = true else onAction(DescribeReviewAction.FindTapped(line.key)) }
                .semantics(mergeDescendants = true) { contentDescription = swapDescription },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = line.name, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
            if (line.matched) Icon(icon = AppIcons.ExpandMore, size = 20.dp, color = AppTheme.colors.muted)
        }
        PopoverMenu(expanded = open, onDismissRequest = { open = false }, alignment = Alignment.TopStart) {
            line.alternatives.forEachIndexed { index, name ->
                MenuItem(label = name, onClick = {
                    open = false
                    onAction(DescribeReviewAction.AlternativePicked(line.key, index))
                })
            }
            MenuItem(
                label = stringResource(Res.string.describe_search_other),
                icon = AppIcons.Search,
                showDivider = false,
                onClick = {
                    open = false
                    onAction(DescribeReviewAction.FindTapped(line.key))
                },
            )
        }
    }
}

/** − 2 eggs +, and the measure when the food has more than one. */
@Composable
private fun LineAmount(line: DescribedLine, onAction: (LoggerAction) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        RoundIconButton(
            icon = AppIcons.Remove.described(stringResource(Res.string.describe_less_cd, line.name)),
            onClick = { onAction(DescribeReviewAction.LessTapped(line.key)) },
            enabled = line.canLess,
            containerColor = AppTheme.colors.soft,
            contentColor = AppTheme.colors.ink,
        )
        Text(
            text = line.amount,
            typography = AppTheme.type.numeric,
            color = AppTheme.colors.ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.widthIn(min = AMOUNT_MIN_WIDTH),
        )
        RoundIconButton(
            icon = AppIcons.Add.described(stringResource(Res.string.describe_more_cd, line.name)),
            onClick = { onAction(DescribeReviewAction.MoreTapped(line.key)) },
            containerColor = AppTheme.colors.soft,
            contentColor = AppTheme.colors.ink,
        )
        if (line.units.size > 1) UnitMenu(line, onAction, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun UnitMenu(line: DescribedLine, onAction: (LoggerAction) -> Unit, modifier: Modifier = Modifier) {
    var open by remember(line.key) { mutableStateOf(false) }
    val current = line.units.getOrNull(line.unitIndex).orEmpty()
    Box(modifier = modifier) {
        TapField(
            text = current,
            onClick = { open = true },
            active = open,
            description = stringResource(Res.string.describe_unit_cd, line.name, current),
        )
        PopoverMenu(expanded = open, onDismissRequest = { open = false }) {
            line.units.forEachIndexed { index, unit ->
                MenuItem(
                    label = unit,
                    icon = if (index == line.unitIndex) AppIcons.Check else null,
                    showDivider = index < line.units.lastIndex,
                    onClick = {
                        open = false
                        onAction(DescribeReviewAction.UnitPicked(line.key, index))
                    },
                )
            }
        }
    }
}

/** "3 items · Estimate", "About 379 kcal", then Add: what Add writes, before it writes it. */
@Composable
private fun DescribeTotalRow(total: DescribeTotal, slot: MealSlot, onAction: (LoggerAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pluralStringResource(Res.plurals.describe_total_count, total.count, total.count),
                typography = AppTheme.type.cardTitle,
                color = AppTheme.colors.ink,
                maxLines = 1,
            )
            // "About" belongs to the number, so it goes when the number does.
            if (total.calories !is Display.Hidden) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                    Text(stringResource(Res.string.describe_total_about), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
                    NutrientText(
                        metric = Metric.Calories,
                        display = total.calories,
                        typography = AppTheme.type.secondary,
                        color = AppTheme.colors.muted,
                        maxLines = 1,
                    )
                }
            }
        }
        ButtonPrimary(onClick = { onAction(LoggerAction.AddToMealTapped) }) {
            Text(stringResource(Res.string.logger_add_to, stringResource(slot.sentenceName)))
        }
    }
}

/** Find a food, in place of the lines: to add one it missed, or to swap a line for the right one. */
@Composable
internal fun DescribeFindPane(find: DescribeFindUi, onAction: (LoggerAction) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        SheetHeader(
            title = find.replacing?.let { stringResource(Res.string.describe_find_swap_title, it) }
                ?: stringResource(Res.string.describe_find_add_title),
            onBack = { onAction(DescribeReviewAction.FindClosed) },
            modifier = Modifier.padding(horizontal = AppTheme.spacing.page),
        )
        SearchField(
            value = find.query,
            onValueChange = { onAction(DescribeReviewAction.FindTyped(it)) },
            placeholder = stringResource(Res.string.describe_find_placeholder),
            focusOnOpen = find.query.isEmpty(),
            onClear = { onAction(DescribeReviewAction.FindTyped("")) },
            clearDescription = stringResource(Res.string.logger_search_clear_cd),
            modifier = Modifier.padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, top = AppTheme.spacing.row),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().clearFocusOnScroll(),
            contentPadding = PaddingValues(
                start = AppTheme.spacing.page,
                end = AppTheme.spacing.page,
                top = AppTheme.spacing.section,
                bottom = AppTheme.spacing.page,
            ),
        ) {
            val results = find.results
            when {
                results == null -> item { FoodRowsNote(stringResource(Res.string.describe_find_searching)) }
                results.isEmpty() && find.query.isNotBlank() -> item {
                    FoodRowsNote(stringResource(if (find.offline) Res.string.describe_find_offline else Res.string.describe_find_none))
                }
                else -> itemsIndexed(results, key = { _, result -> result.id }) { index, result ->
                    ListRow(
                        title = result.name,
                        subtitle = listOfNotNull(result.subtitle, result.amount.ifEmpty { null }).joinToString(" · "),
                        showDivider = index < results.lastIndex,
                        onClick = { onAction(DescribeReviewAction.FindPicked(result.id)) },
                    ) {
                        NutrientText(
                            metric = Metric.Calories,
                            display = result.calories,
                            typography = AppTheme.type.secondary.Tabular,
                            color = AppTheme.colors.muted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private val AMOUNT_MIN_WIDTH = 72.dp
