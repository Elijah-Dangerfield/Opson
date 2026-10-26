package com.opson.features.logger.impl

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.opson.libraries.diary.ui.FavoriteFoodTile
import com.opson.libraries.diary.ui.FoodPickRow
import com.opson.libraries.diary.ui.FoodRowsCard
import com.opson.libraries.diary.ui.FoodRowsNote
import com.opson.libraries.diary.ui.MealCheckChip
import com.opson.libraries.diary.ui.NutrientTotalColumn
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.diary.ui.label
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.chip.ActionChip
import com.opson.libraries.ui.components.chip.PillChip
import com.opson.libraries.ui.components.chip.PillChipSize
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.SearchField
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.color.ToneColors
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_add_to
import opson.libraries.resources.generated.resources.logger_basket_count
import opson.libraries.resources.generated.resources.logger_close_cd
import opson.libraries.resources.generated.resources.logger_favorites
import opson.libraries.resources.generated.resources.logger_first_run
import opson.libraries.resources.generated.resources.logger_for_day
import opson.libraries.resources.generated.resources.logger_method_ate
import opson.libraries.resources.generated.resources.logger_method_describe
import opson.libraries.resources.generated.resources.logger_method_note
import opson.libraries.resources.generated.resources.logger_method_number
import opson.libraries.resources.generated.resources.logger_method_saved
import opson.libraries.resources.generated.resources.logger_method_water
import opson.libraries.resources.generated.resources.logger_method_weigh_in
import opson.libraries.resources.generated.resources.logger_method_yesterday
import opson.libraries.resources.generated.resources.logger_no_results
import opson.libraries.resources.generated.resources.logger_offline_results
import opson.libraries.resources.generated.resources.logger_recent
import opson.libraries.resources.generated.resources.logger_search_placeholder
import opson.libraries.resources.generated.resources.logger_suggested
import opson.libraries.resources.generated.resources.logger_usual_at
import org.jetbrains.compose.resources.stringResource

/**
 * The logger sheet's body: a pinned header (close, meal chips, totals or meal
 * checks, search, method chips), the scrolling picks, and a pinned basket
 * footer. Stateless; the entry point puts it in a `BottomSheet` with a fixed
 * height, which is what lets the list scroll inside it.
 */
@Composable
fun LoggerSheetContent(
    state: LoggerState,
    onAction: (LoggerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        LoggerHeader(state, onAction)
        HorizontalDivider(color = AppTheme.colors.line)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = AppTheme.spacing.page,
                end = AppTheme.spacing.page,
                top = AppTheme.spacing.section,
                bottom = AppTheme.spacing.page,
            ),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            if (!state.loading) body(state.body, onAction)
        }
        state.basket?.let { BasketBar(it, onAction) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoggerHeader(state: LoggerState, onAction: (LoggerAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = AppTheme.spacing.row, bottom = AppTheme.spacing.row),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.page),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            RoundIconButton(
                icon = AppIcons.Close.described(stringResource(Res.string.logger_close_cd)),
                onClick = { onAction(LoggerAction.CloseTapped) },
                size = AppTheme.spacing.touchTarget,
                containerColor = AppTheme.colors.surface,
                contentColor = AppTheme.colors.ink,
            )
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
            ) {
                state.slots.forEach { slot ->
                    PillChip(
                        label = stringResource(slot.label),
                        selected = slot == state.slot,
                        onClick = { onAction(LoggerAction.SlotPicked(slot)) },
                        size = PillChipSize.Compact,
                    )
                }
            }
        }
        val inset = Modifier.padding(horizontal = AppTheme.spacing.page)
        state.otherDay?.let { day ->
            Text(
                text = stringResource(Res.string.logger_for_day, DateCopy.label(day)),
                typography = AppTheme.type.secondarySmallStrong,
                color = AppTheme.colors.muted,
                modifier = inset,
            )
        }
        state.totals?.let { totals ->
            Row(modifier = inset.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                totals.forEach { column ->
                    NutrientTotalColumn(
                        metric = column.metric,
                        label = stringResource(column.label),
                        display = column.display,
                        modifier = Modifier.weight(if (column.metric == Metric.Calories) ENERGY_COLUMN_WEIGHT else 1f),
                    )
                }
            }
        }
        state.mealChecks?.let { checks ->
            FlowRow(
                modifier = inset,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
            ) {
                checks.forEach { MealCheckChip(slot = it.slot, done = it.done) }
            }
        }
        SearchField(
            value = state.query,
            onValueChange = { onAction(LoggerAction.SearchTyped(it)) },
            placeholder = stringResource(Res.string.logger_search_placeholder),
            focusOnOpen = state.focusSearch,
            modifier = inset,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.spacing.page),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
        ) {
            state.methods.forEach { method -> MethodChip(method, onAction) }
        }
    }
}

@Composable
private fun MethodChip(method: LoggerMethod, onAction: (LoggerAction) -> Unit) {
    val nutrients = AppTheme.colors.nutrients
    val (label, icon, tone) = when (method) {
        LoggerMethod.DescribeIt -> Chip(stringResource(Res.string.logger_method_describe), AppIcons.Mic, nutrients.fiber)
        LoggerMethod.Water -> Chip(stringResource(Res.string.logger_method_water), AppIcons.WaterDrop, nutrients.water)
        LoggerMethod.SavedMeals -> Chip(stringResource(Res.string.logger_method_saved), AppIcons.Bookmark, nutrients.fat)
        is LoggerMethod.Yesterday -> Chip(
            stringResource(Res.string.logger_method_yesterday, stringResource(method.slot.sentenceName)),
            AppIcons.History,
            nutrients.carbs,
        )
        LoggerMethod.AteNoDetails -> Chip(stringResource(Res.string.logger_method_ate), AppIcons.CheckCircle, nutrients.protein)
        LoggerMethod.WeighIn -> Chip(stringResource(Res.string.logger_method_weigh_in), AppIcons.MonitorWeight, nutrients.fiber)
        LoggerMethod.JustANumber -> Chip(stringResource(Res.string.logger_method_number), AppIcons.Dialpad, nutrients.fat)
        LoggerMethod.AddANote -> Chip(stringResource(Res.string.logger_method_note), AppIcons.EditNote, nutrients.fat)
    }
    val action = when (method) {
        LoggerMethod.DescribeIt -> LoggerAction.DescribeItTapped
        LoggerMethod.Water -> LoggerAction.WaterTapped
        LoggerMethod.SavedMeals -> LoggerAction.SavedMealsTapped
        is LoggerMethod.Yesterday -> LoggerAction.YesterdayTapped
        LoggerMethod.AteNoDetails -> LoggerAction.AteNoDetailsTapped
        LoggerMethod.WeighIn -> LoggerAction.WeighInTapped
        LoggerMethod.JustANumber -> LoggerAction.JustANumberTapped
        LoggerMethod.AddANote -> LoggerAction.AddANoteTapped
    }
    ActionChip(label = label, icon = icon, tone = tone, onClick = { onAction(action) })
}

private data class Chip(val label: String, val icon: IconResource, val tone: ToneColors)

private fun LazyListScope.body(body: LoggerBody, onAction: (LoggerAction) -> Unit) {
    when (body) {
        LoggerBody.FirstRun -> item(key = "first-run") {
            Text(
                text = stringResource(Res.string.logger_first_run),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
            )
        }
        LoggerBody.Searching -> Unit
        is LoggerBody.NoResults -> {
            if (body.offline) item(key = "offline") { OfflineCaption() }
            item(key = "no-results") {
                FoodRowsCard { FoodRowsNote(stringResource(Res.string.logger_no_results)) }
            }
        }
        is LoggerBody.Results -> {
            if (body.offline) item(key = "offline") { OfflineCaption() }
            item(key = "results") { FoodRows(body.rows, onAction) }
        }
        is LoggerBody.Browse -> {
            if (body.favorites.isNotEmpty()) {
                item(key = "favorites-label") { SectionLabel(stringResource(Res.string.logger_favorites)) }
                item(key = "favorites") { Favorites(body.favorites, onAction) }
            }
            if (body.usual.isNotEmpty()) {
                item(key = "usual-label") {
                    val title = when (val usual = body.usualTitle) {
                        is UsualTitle.UsualAt -> stringResource(Res.string.logger_usual_at, stringResource(usual.slot.sentenceName))
                        UsualTitle.Suggested -> stringResource(Res.string.logger_suggested)
                    }
                    SectionLabel(title)
                }
                item(key = "usual") { FoodRows(body.usual, onAction) }
            }
            if (body.recent.isNotEmpty()) {
                item(key = "recent-label") { SectionLabel(stringResource(Res.string.logger_recent)) }
                item(key = "recent") { FoodRows(body.recent, onAction) }
            }
        }
    }
}

@Composable
private fun OfflineCaption() {
    Text(text = stringResource(Res.string.logger_offline_results), typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
}

@Composable
private fun Favorites(rows: List<FoodRowUi>, onAction: (LoggerAction) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            FavoriteFoodTile(
                name = row.shortName,
                fullName = row.name,
                iconSeed = row.key,
                picked = row.picked,
                onToggle = { onAction(LoggerAction.FoodToggleTapped(row)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FoodRows(rows: List<FoodRowUi>, onAction: (LoggerAction) -> Unit) {
    FoodRowsCard {
        rows.forEachIndexed { index, row ->
            FoodPickRow(
                name = row.name,
                amount = row.amount,
                macros = row.macros,
                calories = row.calories,
                picked = row.picked,
                onOpen = { onAction(LoggerAction.FoodNameTapped(row)) },
                onToggle = { onAction(LoggerAction.FoodToggleTapped(row)) },
                iconSeed = row.key,
                showDivider = index < rows.lastIndex,
            )
        }
    }
}

@Composable
private fun BasketBar(basket: BasketFooter, onAction: (LoggerAction) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = AppTheme.colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppTheme.spacing.page,
                    end = AppTheme.spacing.page,
                    top = AppTheme.spacing.row,
                    bottom = AppTheme.spacing.page,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.logger_basket_count, basket.count),
                    typography = AppTheme.type.cardTitle,
                    color = AppTheme.colors.ink,
                )
                Text(
                    text = basket.names,
                    typography = AppTheme.type.secondarySmall,
                    color = AppTheme.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            NutrientText(
                metric = Metric.Calories,
                display = basket.calories,
                typography = AppTheme.type.secondaryStrong.Tabular,
                color = AppTheme.colors.muted,
                maxLines = 1,
            )
            ButtonPrimary(onClick = { onAction(LoggerAction.AddToMealTapped) }) {
                Text(stringResource(Res.string.logger_add_to, stringResource(basket.slot.sentenceName)))
            }
        }
    }
}

private const val ENERGY_COLUMN_WEIGHT = 1.35f
