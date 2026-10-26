package com.opson.features.today.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.opson.libraries.diary.FastingStatus
import com.opson.libraries.diary.ui.EnergySummaryCard
import com.opson.libraries.diary.ui.FastingTodayCard
import com.opson.libraries.diary.ui.FocusSummaryCard
import com.opson.libraries.diary.ui.FoodListCard
import com.opson.libraries.diary.ui.FoodsLine
import com.opson.libraries.diary.ui.JournalEntry
import com.opson.libraries.diary.ui.MealCardItem
import com.opson.libraries.diary.ui.MealRowCard
import com.opson.libraries.diary.ui.SafeFoodsCard
import com.opson.libraries.diary.ui.SoftSummaryCard
import com.opson.libraries.diary.ui.SummaryMacro
import com.opson.libraries.diary.ui.TodayCard
import com.opson.libraries.diary.ui.TodayCardPadding
import com.opson.libraries.diary.ui.TodayMealCard
import com.opson.libraries.diary.ui.WaterCard
import com.opson.libraries.diary.ui.YesterdayBox
import com.opson.libraries.diary.ui.icon
import com.opson.libraries.diary.ui.label
import com.opson.libraries.diary.ui.rowLabel
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.diary.ui.tileColors
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.ui.components.Banner
import com.opson.libraries.ui.components.ChecklistAction
import com.opson.libraries.ui.components.ChecklistRow
import com.opson.libraries.ui.components.CountChip
import com.opson.libraries.ui.components.DayHeader
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HeaderIconButton
import com.opson.libraries.ui.components.HeroListCard
import com.opson.libraries.ui.components.HeroStatementCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.InfoBanner
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.ListRowChevron
import com.opson.libraries.ui.components.LockedHeroCard
import com.opson.libraries.ui.components.MenuGroupDivider
import com.opson.libraries.ui.components.MenuIconItem
import com.opson.libraries.ui.components.MenuPopover
import com.opson.libraries.ui.components.MenuPopoverDefaults
import com.opson.libraries.ui.components.MinTouchTarget
import com.opson.libraries.ui.components.PillAction
import com.opson.libraries.ui.components.PopoverMenu
import com.opson.libraries.ui.components.PromptAction
import com.opson.libraries.ui.components.PromptCard
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.SuggestionButton
import com.opson.libraries.ui.components.TintTile
import com.opson.libraries.ui.components.TintTileGrid
import com.opson.libraries.ui.components.UndoBar
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.time.DateCopy
import com.opson.libraries.ui.time.RelativeDay
import com.opson.system.AppTheme
import com.opson.system.color.ToneColors
import kotlinx.datetime.LocalTime
import opson.libraries.resources.generated.resources.Res
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.button.FlatButton
import opson.libraries.resources.generated.resources.reminders_rhythm_set_up
import opson.libraries.resources.generated.resources.reminders_rhythm_off_body
import opson.libraries.resources.generated.resources.reminders_rhythm_off
import opson.libraries.resources.generated.resources.reminders_rhythm_next_body
import opson.libraries.resources.generated.resources.reminders_rhythm_next
import opson.libraries.resources.generated.resources.reminders_rhythm_change
import opson.libraries.resources.generated.resources.integrations_today_expended
import opson.libraries.resources.generated.resources.today_add_to_cd
import opson.libraries.resources.generated.resources.today_added_one
import opson.libraries.resources.generated.resources.today_added_other
import opson.libraries.resources.generated.resources.today_added_undo
import opson.libraries.resources.generated.resources.today_bridge_body
import opson.libraries.resources.generated.resources.today_bridge_not_today
import opson.libraries.resources.generated.resources.today_bridge_title
import opson.libraries.resources.generated.resources.today_bridge_tried
import opson.libraries.resources.generated.resources.today_calendar_cd
import opson.libraries.resources.generated.resources.today_close_body
import opson.libraries.resources.generated.resources.today_close_title
import opson.libraries.resources.generated.resources.today_closed_body
import opson.libraries.resources.generated.resources.today_closed_title
import opson.libraries.resources.generated.resources.today_count_value
import opson.libraries.resources.generated.resources.today_counted_four
import opson.libraries.resources.generated.resources.today_counted_label
import opson.libraries.resources.generated.resources.today_counted_label_other_day
import opson.libraries.resources.generated.resources.today_counted_next
import opson.libraries.resources.generated.resources.today_counted_none
import opson.libraries.resources.generated.resources.today_counted_one
import opson.libraries.resources.generated.resources.today_counted_three
import opson.libraries.resources.generated.resources.today_counted_two
import opson.libraries.resources.generated.resources.today_counts_glasses_one
import opson.libraries.resources.generated.resources.today_counts_glasses_other
import opson.libraries.resources.generated.resources.today_counts_meals_one
import opson.libraries.resources.generated.resources.today_counts_meals_other
import opson.libraries.resources.generated.resources.today_day_menu_cd
import opson.libraries.resources.generated.resources.today_explorer_new_empty
import opson.libraries.resources.generated.resources.today_explorer_safe
import opson.libraries.resources.generated.resources.today_explorer_safe_empty
import opson.libraries.resources.generated.resources.today_feel_pair
import opson.libraries.resources.generated.resources.today_feel_prompt
import opson.libraries.resources.generated.resources.today_focus_title
import opson.libraries.resources.generated.resources.today_item_sub
import opson.libraries.resources.generated.resources.today_label_today
import opson.libraries.resources.generated.resources.today_label_tomorrow
import opson.libraries.resources.generated.resources.today_label_yesterday
import opson.libraries.resources.generated.resources.today_meal_around
import opson.libraries.resources.generated.resources.today_meal_copy
import opson.libraries.resources.generated.resources.today_meal_saved
import opson.libraries.resources.generated.resources.today_meal_usually
import opson.libraries.resources.generated.resources.today_meal_yesterday
import opson.libraries.resources.generated.resources.today_menu_add_note
import opson.libraries.resources.generated.resources.today_menu_clear
import opson.libraries.resources.generated.resources.today_menu_clear_confirm
import opson.libraries.resources.generated.resources.today_menu_close_day
import opson.libraries.resources.generated.resources.today_menu_close_this_day
import opson.libraries.resources.generated.resources.today_menu_copy_tomorrow
import opson.libraries.resources.generated.resources.today_menu_copy_yesterday
import opson.libraries.resources.generated.resources.today_menu_edit_cards
import opson.libraries.resources.generated.resources.today_menu_nutrients
import opson.libraries.resources.generated.resources.today_menu_tracked
import opson.libraries.resources.generated.resources.today_menu_untracked
import opson.libraries.resources.generated.resources.today_new_count_one
import opson.libraries.resources.generated.resources.today_new_count_other
import opson.libraries.resources.generated.resources.today_new_this_month
import opson.libraries.resources.generated.resources.today_next_day_cd
import opson.libraries.resources.generated.resources.today_not_planned
import opson.libraries.resources.generated.resources.today_pick_day_cd
import opson.libraries.resources.generated.resources.today_plan_body
import opson.libraries.resources.generated.resources.today_plan_it
import opson.libraries.resources.generated.resources.today_plan_title
import opson.libraries.resources.generated.resources.today_planned
import opson.libraries.resources.generated.resources.today_prev_day_cd
import opson.libraries.resources.generated.resources.today_revealed_body
import opson.libraries.resources.generated.resources.today_revealed_title
import opson.libraries.resources.generated.resources.today_rhythm_already_ate
import opson.libraries.resources.generated.resources.today_rhythm_ate
import opson.libraries.resources.generated.resources.today_rhythm_log
import opson.libraries.resources.generated.resources.today_rhythm_logged
import opson.libraries.resources.generated.resources.today_rhythm_planned
import opson.libraries.resources.generated.resources.today_rhythm_title
import opson.libraries.resources.generated.resources.today_soft_energy
import opson.libraries.resources.generated.resources.today_soft_meals
import opson.libraries.resources.generated.resources.today_summary_eaten
import opson.libraries.resources.generated.resources.today_summary_kcal
import opson.libraries.resources.generated.resources.today_summary_nothing_yet
import opson.libraries.resources.generated.resources.today_summary_nutrients
import opson.libraries.resources.generated.resources.today_summary_range
import opson.libraries.resources.generated.resources.today_tile_ate
import opson.libraries.resources.generated.resources.today_tile_items_one
import opson.libraries.resources.generated.resources.today_tile_items_other
import opson.libraries.resources.generated.resources.today_untracked_action
import opson.libraries.resources.generated.resources.today_untracked_body
import opson.libraries.resources.generated.resources.today_untracked_title
import opson.libraries.resources.generated.resources.today_water
import opson.libraries.resources.generated.resources.today_water_cd
import opson.libraries.resources.generated.resources.today_what_you_had
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Today tab (design v3, 2.1 to 2.11): the day header, the hero card the layout and levels
 * call for, then the cards the state carries, the meals, and Close the day. Stateless;
 * [TodayViewModel] owns what it shows. The ⋮ menu opens from the header as a popover.
 */
@Composable
fun TodayScreen(
    state: TodayState,
    onAction: (TodayAction) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val rows = todayRows(state)
    LaunchedEffect(state.highlight) {
        val slot = state.highlight ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it is TodayRow.Meal && it.entry.slot == slot }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = AppTheme.spacing.row, end = AppTheme.spacing.row, bottom = AppTheme.spacing.section * 2),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
            ) {
                items(rows, key = { it.key }) { row -> TodayListItem(row, state, onAction) }
            }
            state.added?.let { added ->
                val meal = stringResource(added.slot.sentenceName)
                UndoBar(
                    message = if (added.count == 1) {
                        stringResource(Res.string.today_added_one, meal)
                    } else {
                        stringResource(Res.string.today_added_other, added.count, meal)
                    },
                    actionLabel = stringResource(Res.string.today_added_undo),
                    onAction = { onAction(TodayAction.UndoTapped) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(AppTheme.spacing.row),
                )
            }
        }
    }
}

/** One item of the Today list, in order. A pure mapping, so the highlight can find its meal's index. */
internal sealed interface TodayRow {
    val key: String

    data object Header : TodayRow { override val key = "header" }
    data class Banner(val banner: TodayBanner) : TodayRow { override val key = "banner" }
    data object Planning : TodayRow { override val key = "planning" }
    data class Summary(val summary: TodaySummary) : TodayRow { override val key = "summary" }
    data object Untracked : TodayRow { override val key = "untracked" }
    data class Bridge(val card: FoodBridgeCard) : TodayRow { override val key = "food-bridge" }
    data class CountsRow(val counts: Counts) : TodayRow { override val key = "counts" }
    data class Tiles(val tiles: List<MealTile>) : TodayRow { override val key = "tiles" }
    data class Rhythm(val card: RhythmCard) : TodayRow { override val key = "rhythm" }
    data class RhythmReminderLine(val reminder: RhythmReminder) : TodayRow { override val key = "rhythm-reminder" }
    data class SafeFoods(val foods: List<String>) : TodayRow { override val key = "safe-foods" }
    data class Fasting(val status: FastingStatus) : TodayRow { override val key = "fasting" }
    data class Water(val water: WaterRow) : TodayRow { override val key = "water" }
    data class Meal(val entry: MealEntry) : TodayRow { override val key = "meal-${entry.slot.wire}" }
    data class Had(val had: WhatYouHad) : TodayRow { override val key = "what-you-had" }
    data class Close(val card: CloseDayCard) : TodayRow { override val key = "close-day" }
}

internal fun todayRows(state: TodayState): List<TodayRow> = buildList {
    add(TodayRow.Header)
    state.banner?.let { add(TodayRow.Banner(it)) }
    if (state.planning) add(TodayRow.Planning)
    state.summary?.let { add(TodayRow.Summary(it)) }
    if (state.untracked) add(TodayRow.Untracked)
    state.counts?.let { add(TodayRow.CountsRow(it)) }
    state.mealTiles?.let { add(TodayRow.Tiles(it)) }
    state.rhythm?.let { add(TodayRow.Rhythm(it)) }
    state.rhythm?.reminder?.let { add(TodayRow.RhythmReminderLine(it)) }
    state.whatYouHad?.takeIf { state.rhythm == null }?.let { add(TodayRow.Had(it)) }
    state.bridge?.let { add(TodayRow.Bridge(it)) }
    state.safeFoods?.let { add(TodayRow.SafeFoods(it)) }
    state.fasting?.let { add(TodayRow.Fasting(it)) }
    state.water?.let { add(TodayRow.Water(it)) }
    state.meals.forEach { add(TodayRow.Meal(it)) }
    state.whatYouHad?.takeIf { state.rhythm != null }?.let { add(TodayRow.Had(it)) }
    state.closeDay?.let { add(TodayRow.Close(it)) }
}

@Suppress("CyclomaticComplexMethod")
@Composable
private fun TodayListItem(row: TodayRow, state: TodayState, onAction: (TodayAction) -> Unit) {
    when (row) {
        TodayRow.Header -> Header(state, onAction)
        is TodayRow.Banner -> Banner(text = stringResource(row.banner.text, *row.banner.args.toTypedArray()))
        TodayRow.Planning -> InfoBanner(
            icon = AppIcons.EventNote,
            title = stringResource(Res.string.today_plan_title),
            body = stringResource(Res.string.today_plan_body),
        )
        is TodayRow.Summary -> Summary(row.summary, state.dayLabel, onAction)
        TodayRow.Untracked -> PromptCard(
            icon = AppIcons.BeachAccess,
            tone = ToneColors(AppTheme.colors.accent, AppTheme.colors.accentTint),
            title = stringResource(Res.string.today_untracked_title),
            body = stringResource(Res.string.today_untracked_body),
        ) {
            PromptAction(
                label = stringResource(Res.string.today_untracked_action),
                onClick = { onAction(TodayAction.TrackAgainTapped) },
                modifier = Modifier.weight(1f),
            )
        }
        is TodayRow.Bridge -> PromptCard(
            icon = AppIcons.Route,
            tone = ToneColors(AppTheme.colors.accent, AppTheme.colors.accentTint),
            title = stringResource(Res.string.today_bridge_title, row.card.from),
            body = stringResource(Res.string.today_bridge_body, row.card.to, row.card.shared),
        ) {
            PromptAction(
                label = stringResource(Res.string.today_bridge_tried),
                onClick = { onAction(TodayAction.BridgeTriedTapped) },
                emphasis = true,
                modifier = Modifier.weight(1f),
            )
            PromptAction(
                label = stringResource(Res.string.today_bridge_not_today),
                onClick = { onAction(TodayAction.BridgeNotTodayTapped) },
                modifier = Modifier.weight(1f),
            )
        }
        is TodayRow.CountsRow -> Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            CountChip(
                icon = AppIcons.Restaurant,
                iconColor = AppTheme.colors.accent,
                text = stringResource(if (row.counts.meals == 1) Res.string.today_counts_meals_one else Res.string.today_counts_meals_other, row.counts.meals),
            )
            row.counts.glasses?.let { glasses ->
                CountChip(
                    icon = AppIcons.WaterDrop,
                    iconColor = AppTheme.colors.nutrients.water.content,
                    text = stringResource(if (glasses == 1) Res.string.today_counts_glasses_one else Res.string.today_counts_glasses_other, glasses),
                )
            }
        }
        is TodayRow.Tiles -> MealTiles(row.tiles, onAction)
        is TodayRow.Rhythm -> Rhythm(row.card, onAction)
        is TodayRow.RhythmReminderLine -> RhythmReminderCard(row.reminder, onAction)
        is TodayRow.SafeFoods -> SafeFoodsCard(
            title = stringResource(Res.string.today_explorer_safe),
            foods = row.foods,
            emptyText = stringResource(Res.string.today_explorer_safe_empty),
        )
        is TodayRow.Fasting -> FastingTodayCard(
            status = row.status,
            onOpen = { onAction(TodayAction.FastingCardTapped) },
            onEndFast = { onAction(TodayAction.EndFastTapped) },
        )
        is TodayRow.Water -> WaterCard(
            title = stringResource(Res.string.today_water),
            display = row.water.display,
            filled = row.water.filled,
            total = row.water.total,
            showSegments = row.water.showSegments,
            onAdd = { onAction(TodayAction.WaterPlusTapped) },
            addIcon = AppIcons.Add.described(stringResource(Res.string.today_water_cd)),
        )
        is TodayRow.Meal -> Meal(row.entry, highlighted = state.highlight == row.entry.slot, onAction = onAction)
        is TodayRow.Had -> when (val had = row.had) {
            is WhatYouHad.PerMeal -> FoodListCard(rows = had.rows.map { (slot, foods) -> stringResource(slot.label) to foods })
            is WhatYouHad.Together -> FoodListCard(rows = listOf(null to had.text), title = stringResource(Res.string.today_what_you_had))
        }
        is TodayRow.Close -> TodayCard(
            contentPadding = TodayCardPadding.List,
            onClick = { onAction(TodayAction.CloseDayCardTapped) },
        ) {
            ListRow(
                title = stringResource(if (row.card.closed) Res.string.today_closed_title else Res.string.today_close_title),
                subtitle = stringResource(if (row.card.closed) Res.string.today_closed_body else Res.string.today_close_body),
                icon = AppIcons.Bedtime,
                iconTone = ToneColors(AppTheme.colors.accent, AppTheme.colors.accentTint),
            ) {
                ListRowChevron()
            }
        }
    }
}

@Composable
private fun Header(state: TodayState, onAction: (TodayAction) -> Unit) {
    val label = state.dayLabel.text()
    DayHeader(
        title = label,
        subtitle = DateCopy.weekdayMonthDay(state.day),
        previousIcon = AppIcons.ChevronLeft.described(stringResource(Res.string.today_prev_day_cd)),
        nextIcon = AppIcons.ChevronRight.described(stringResource(Res.string.today_next_day_cd)),
        onPrevious = { onAction(TodayAction.PreviousDayTapped) },
        onNext = { onAction(TodayAction.NextDayTapped) },
        onTitleClick = { onAction(TodayAction.DayLabelTapped) },
        titleDescription = stringResource(Res.string.today_pick_day_cd) + ", " + label,
        nextEnabled = state.canGoForward,
    ) {
        HeaderIconButton(
            icon = AppIcons.CalendarMonth.described(stringResource(Res.string.today_calendar_cd)),
            onClick = { onAction(TodayAction.CalendarTapped) },
        )
        Box {
            HeaderIconButton(
                icon = AppIcons.MoreVert.described(stringResource(Res.string.today_day_menu_cd)),
                onClick = { onAction(TodayAction.DayMenuTapped) },
                selected = state.menu != null,
            )
            PopoverMenu(expanded = state.menu != null, onDismissRequest = { onAction(TodayAction.MenuDismissed) }) {
                state.menu?.let { DayMenuItems(it, onAction) }
            }
        }
    }
}

@Composable
private fun Summary(summary: TodaySummary, dayLabel: RelativeDay, onAction: (TodayAction) -> Unit) {
    when (summary) {
        is TodaySummary.Energy -> EnergySummaryCard(
            eatenLabel = stringResource(Res.string.today_summary_eaten),
            eaten = summary.eaten,
            unitLabel = stringResource(Res.string.today_summary_kcal),
            rangeLabel = stringResource(Res.string.today_summary_range),
            range = summary.range,
            rangeStart = summary.rangeStart,
            rangeEnd = summary.rangeEnd,
            macros = summary.macros.map { it.toSummaryMacro() },
            nutrientsLabel = stringResource(Res.string.today_summary_nutrients),
            onNutrients = { onAction(TodayAction.NutrientsTapped) },
            emptyTitle = if (summary.empty) stringResource(Res.string.today_summary_nothing_yet) else null,
            expendedLabel = stringResource(Res.string.integrations_today_expended),
            expended = summary.expended,
        )
        is TodaySummary.Soft -> SoftSummaryCard(
            energyLabel = stringResource(Res.string.today_soft_energy),
            energy = summary.energy,
            mealsLabel = summary.mealCount?.let { stringResource(Res.string.today_soft_meals) },
            mealsValue = summary.mealCount?.let { stringResource(Res.string.today_count_value, it.done, it.total) },
            mealsDone = summary.mealCount?.done ?: 0,
            mealsTotal = summary.mealCount?.total ?: 0,
            lead = summary.lead?.toSummaryMacro(),
            rows = summary.rows.map { it.toSummaryMacro() },
        )
        is TodaySummary.Focus -> FocusSummaryCard(title = stringResource(Res.string.today_focus_title), rows = summary.rows.map { it.toSummaryMacro() })
        is TodaySummary.Locked -> LockedHeroCard(title = stringResource(summary.title), body = stringResource(summary.body))
        TodaySummary.Revealed -> TodayCard(onClick = { onAction(TodayAction.RevealedTapped) }, contentPadding = TodayCardPadding.List, verticalArrangement = Arrangement.Top) {
            ListRow(
                title = stringResource(Res.string.today_revealed_title),
                subtitle = stringResource(Res.string.today_revealed_body),
                icon = AppIcons.Lock,
                iconTone = ToneColors(AppTheme.colors.accent, AppTheme.colors.accentTint),
            ) {
                ListRowChevron()
            }
        }
        is TodaySummary.Counted -> HeroStatementCard(
            label = stringResource(if (dayLabel == RelativeDay.Today) Res.string.today_counted_label else Res.string.today_counted_label_other_day),
            title = stringResource(countWord(summary.done)),
            body = if (summary.next != null && summary.nextAround != null && dayLabel == RelativeDay.Today) {
                stringResource(Res.string.today_counted_next, stringResource(summary.next.label), summary.nextAround.shortClock())
            } else {
                null
            },
        )
        is TodaySummary.NewFoods -> HeroListCard(
            title = stringResource(Res.string.today_new_this_month),
            trailing = stringResource(if (summary.foods.size == 1) Res.string.today_new_count_one else Res.string.today_new_count_other, summary.foods.size),
            rows = summary.foods.map { it.name to it.seen },
            emptyText = stringResource(Res.string.today_explorer_new_empty),
        )
    }
}

private fun countWord(done: Int): StringResource = when (done) {
    0 -> Res.string.today_counted_none
    1 -> Res.string.today_counted_one
    2 -> Res.string.today_counted_two
    3 -> Res.string.today_counted_three
    else -> Res.string.today_counted_four
}

@Composable
private fun MacroTile.toSummaryMacro(): SummaryMacro = SummaryMacro(metric, stringResource(label), display, metric.color())

@Composable
private fun Metric.color(): ColorResource = when (this) {
    Metric.Protein -> AppTheme.colors.nutrients.protein.content
    Metric.Carbs -> AppTheme.colors.nutrients.carbs.content
    Metric.Fat -> AppTheme.colors.nutrients.fat.content
    else -> AppTheme.colors.nutrients.fiber.content
}

@Composable
private fun MealTiles(tiles: List<MealTile>, onAction: (TodayAction) -> Unit) {
    TintTileGrid(count = tiles.size) { index, tileModifier ->
        val tile = tiles[index]
        val status = tile.status
        TintTile(
            icon = tile.slot.icon,
            tone = tile.slot.tileColors(),
            title = stringResource(tile.slot.label),
            subtitle = when (status) {
                is TileStatus.Empty -> stringResource(Res.string.today_meal_around, status.around.shortClock())
                TileStatus.AteNoDetails -> stringResource(Res.string.today_tile_ate)
                is TileStatus.Items -> stringResource(
                    if (status.count == 1) Res.string.today_tile_items_one else Res.string.today_tile_items_other,
                    status.count,
                    status.time.clock(),
                )
            },
            done = status !is TileStatus.Empty,
            onClick = { onAction(TodayAction.MealTileTapped(tile.slot)) },
            modifier = tileModifier,
        )
    }
}

/** v3 `today-rhythm.png`, under the checklist: when the next nudge comes, and where to change it. */
@Composable
private fun RhythmReminderCard(reminder: RhythmReminder, onAction: (TodayAction) -> Unit) {
    val (title, body, action) = when (reminder) {
        is RhythmReminder.Next -> Triple(
            stringResource(Res.string.reminders_rhythm_next, stringResource(reminder.slot.label)),
            stringResource(Res.string.reminders_rhythm_next_body, reminder.at.clock(), stringResource(reminder.slot.sentenceName)),
            stringResource(Res.string.reminders_rhythm_change),
        )
        RhythmReminder.Off -> Triple(
            stringResource(Res.string.reminders_rhythm_off),
            stringResource(Res.string.reminders_rhythm_off_body),
            stringResource(Res.string.reminders_rhythm_set_up),
        )
    }
    EdgeCard(edgeToEdge = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
                Text(text = body, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            }
            FlatButton(text = action, onClick = { onAction(TodayAction.RemindersTapped) }, tone = FlatButtonTone.AccentText)
        }
    }
}

@Composable
private fun Rhythm(card: RhythmCard, onAction: (TodayAction) -> Unit) {
    TodayCard(verticalArrangement = Arrangement.Top, contentPadding = TodayCardPadding.Checklist) {
        Row(Modifier.fillMaxWidth()) {
            Text(text = stringResource(Res.string.today_rhythm_title), typography = AppTheme.type.sheetTitle, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.today_count_value, card.done, card.total),
                typography = AppTheme.type.cardTitle.Tabular,
                color = AppTheme.colors.muted,
            )
        }
        HorizontalDivider(color = AppTheme.colors.line, modifier = Modifier.padding(top = AppTheme.spacing.row))
        card.rows.forEachIndexed { index, row ->
            val status = row.status
            val actions: (@Composable RowScope.() -> Unit)? = if (row.showActions && status is RhythmStatus.Pending) {
                {
                    ChecklistAction(
                        label = stringResource(Res.string.today_rhythm_already_ate),
                        onClick = { onAction(TodayAction.AlreadyAteTapped(row.slot)) },
                        modifier = Modifier.weight(1f),
                    )
                    ChecklistAction(
                        label = stringResource(Res.string.today_rhythm_log),
                        onClick = { onAction(TodayAction.RhythmLogTapped(row.slot)) },
                        primary = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                null
            }
            ChecklistRow(
                title = stringResource(row.slot.label),
                subtitle = when (status) {
                    is RhythmStatus.Pending -> stringResource(Res.string.today_rhythm_planned, status.around.shortClock())
                    is RhythmStatus.Logged -> stringResource(Res.string.today_rhythm_logged, status.time.clock(), status.around.shortClock())
                    is RhythmStatus.AteNoDetails -> stringResource(Res.string.today_rhythm_ate, status.around.shortClock())
                },
                done = status !is RhythmStatus.Pending,
                showDivider = index < card.rows.lastIndex,
                actions = actions,
            )
        }
    }
}

@Composable
private fun Meal(entry: MealEntry, highlighted: Boolean, onAction: (TodayAction) -> Unit) {
    val slot = entry.slot
    val sentence = stringResource(slot.sentenceName)
    val add: @Composable RowScope.() -> Unit = {
        RoundIconButton(
            icon = AppIcons.Add.described(stringResource(Res.string.today_add_to_cd, sentence)),
            onClick = { onAction(TodayAction.MealPlusTapped(slot)) },
            size = MinTouchTarget,
        )
    }
    when (entry) {
        is MealEntry.Full -> FullMeal(entry.card, highlighted, onAction)
        is MealEntry.Collapsed -> MealRowCard(
            icon = slot.icon,
            tone = slot.tileColors(),
            title = stringResource(slot.label),
            subtitle = if (slot.isMeal) {
                stringResource(if (entry.usual) Res.string.today_meal_usually else Res.string.today_meal_around, entry.around.clock())
            } else {
                null
            },
            trailing = add,
            below = suggestions(entry, sentence, onAction),
        )
        is MealEntry.Compact -> MealRowCard(
            icon = slot.icon,
            tone = slot.tileColors(),
            title = stringResource(entry.title),
            subtitle = if (entry.feel == null && !entry.askFeel) entry.foods else entry.time?.clock(),
            trailing = add,
            below = if (entry.feel != null || entry.askFeel) {
                {
                    JournalEntry(
                        foods = entry.foods,
                        feel = entry.feel?.let { feelChips(it) }.orEmpty(),
                        prompt = stringResource(Res.string.today_feel_prompt),
                        onPrompt = { onAction(TodayAction.FeelPromptTapped(slot)) },
                    )
                }
            } else {
                null
            },
        )
        is MealEntry.Planned -> MealRowCard(
            icon = slot.icon,
            tone = slot.tileColors(),
            title = stringResource(slot.label),
            subtitle = stringResource(Res.string.today_planned),
            calories = entry.calories,
            trailing = add,
            below = { FoodsLine(entry.foods) },
        )
        is MealEntry.NotPlanned -> MealRowCard(
            icon = slot.icon,
            tone = slot.tileColors(),
            title = stringResource(slot.label),
            subtitle = stringResource(Res.string.today_not_planned),
            trailing = { PillAction(label = stringResource(Res.string.today_plan_it), onClick = { onAction(TodayAction.PlanItTapped(slot)) }) },
        )
    }
}

private fun suggestions(
    entry: MealEntry.Collapsed,
    sentence: String,
    onAction: (TodayAction) -> Unit,
): (@Composable ColumnScope.() -> Unit)? = when {
    entry.yesterday != null -> {
        {
            YesterdayBox(
                label = stringResource(Res.string.today_meal_yesterday, sentence),
                foods = entry.yesterday.foods,
                calories = entry.yesterday.calories,
                copyLabel = stringResource(Res.string.today_meal_copy),
                onCopy = { onAction(TodayAction.CopyYesterdayMealTapped(entry.slot)) },
            )
        }
    }
    entry.offerSaved -> {
        {
            SuggestionButton(
                label = stringResource(Res.string.today_meal_saved),
                icon = AppIcons.Bookmark,
                onClick = { onAction(TodayAction.SavedMealsTapped(entry.slot)) },
            )
        }
    }
    else -> null
}

@Composable
private fun feelChips(feel: FeelLine): List<String> {
    val hunger = feel.hunger?.let { stringResource(it) }
    val fullness = feel.fullness?.let { stringResource(it) }
    val pair = when {
        hunger != null && fullness != null -> stringResource(Res.string.today_feel_pair, hunger, fullness)
        else -> hunger ?: fullness
    }
    return listOfNotNull(pair) + feel.moods.map { stringResource(it) }
}

@Composable
private fun FullMeal(card: MealCard, highlighted: Boolean, onAction: (TodayAction) -> Unit) {
    val sentence = stringResource(card.slot.sentenceName)
    TodayMealCard(
        icon = card.slot.icon,
        tone = card.slot.tileColors(),
        title = stringResource(card.title),
        time = card.time?.clock() ?: if (card.ateNoDetails) stringResource(Res.string.today_tile_ate) else null,
        calories = card.calories,
        macros = card.macros,
        macroStyle = card.macroStyle,
        addIcon = AppIcons.Add.described(stringResource(Res.string.today_add_to_cd, sentence)),
        onAdd = { onAction(TodayAction.MealPlusTapped(card.slot)) },
        items = card.items.map { item ->
            val precision = stringResource(item.precision.rowLabel)
            MealCardItem(
                key = item.entryId,
                name = item.name,
                sub = if (item.amount.isBlank()) precision else stringResource(Res.string.today_item_sub, item.amount, precision),
                calories = item.calories,
            )
        },
        onItemClick = { onAction(TodayAction.ItemTapped(it.key)) },
        feel = card.feel.map { stringResource(it) },
        note = card.note,
        highlighted = highlighted,
    )
}

/** The ⋮ menu's groups, for inside a popover. */
@Composable
fun ColumnScope.DayMenuItems(menu: DayMenu, onAction: (TodayAction) -> Unit) {
    menu.groups.forEachIndexed { groupIndex, group ->
        if (groupIndex > 0) MenuGroupDivider()
        group.forEach { item ->
            val armed = item == DayMenuItem.ClearDay && menu.clearArmed
            val (label, icon, action) = when (item) {
                is DayMenuItem.CloseDay -> Triple(
                    if (item.isToday) Res.string.today_menu_close_day else Res.string.today_menu_close_this_day,
                    AppIcons.Bedtime,
                    TodayAction.DayCloseMenuTapped,
                )
                DayMenuItem.Nutrients -> Triple(Res.string.today_menu_nutrients, AppIcons.Science, TodayAction.DayNutrientsTapped)
                DayMenuItem.CopyYesterday -> Triple(Res.string.today_menu_copy_yesterday, AppIcons.ContentCopy, TodayAction.CopyYesterdayTapped)
                DayMenuItem.CopyToTomorrow -> Triple(Res.string.today_menu_copy_tomorrow, AppIcons.FileCopy, TodayAction.CopyToTomorrowTapped)
                DayMenuItem.AddNote -> Triple(Res.string.today_menu_add_note, AppIcons.EditNote, TodayAction.DayNoteTapped)
                is DayMenuItem.Untracked -> Triple(
                    if (item.marked) Res.string.today_menu_tracked else Res.string.today_menu_untracked,
                    AppIcons.BeachAccess,
                    TodayAction.UntrackedMenuTapped,
                )
                DayMenuItem.EditCards -> Triple(Res.string.today_menu_edit_cards, AppIcons.DashboardCustomize, TodayAction.EditCardsTapped)
                DayMenuItem.ClearDay -> Triple(
                    if (armed) Res.string.today_menu_clear_confirm else Res.string.today_menu_clear,
                    AppIcons.Delete,
                    TodayAction.ClearDayTapped,
                )
            }
            val quiet = item == DayMenuItem.EditCards || item == DayMenuItem.ClearDay
            MenuIconItem(
                label = stringResource(label),
                icon = icon,
                iconColor = when {
                    armed -> AppTheme.colors.nutrients.protein.content
                    quiet -> AppTheme.colors.muted
                    else -> AppTheme.colors.accent
                },
                onClick = { onAction(action) },
            )
        }
    }
}

/** The menu drawn in place, without the popup window, for previews and the leak test. */
@Composable
fun DayMenuContent(menu: DayMenu, onAction: (TodayAction) -> Unit, modifier: Modifier = Modifier) {
    MenuPopover(modifier = modifier, width = MenuPopoverDefaults.WideWidth) { DayMenuItems(menu, onAction) }
}

@Composable
internal fun RelativeDay.text(): String = when (this) {
    RelativeDay.Today -> stringResource(Res.string.today_label_today)
    RelativeDay.Yesterday -> stringResource(Res.string.today_label_yesterday)
    RelativeDay.Tomorrow -> stringResource(Res.string.today_label_tomorrow)
    is RelativeDay.Date -> DateCopy.weekdayMonthDay(date)
}

/** `8:10 AM`. */
internal fun LocalTime.clock(): String = DateCopy.time(hour, minute)

/** `7:00`, for "around 7:00", where the time of day is plain from the meal. */
internal fun LocalTime.shortClock(): String {
    val h = (hour % HALF_DAY).let { if (it == 0) HALF_DAY else it }
    return "$h:${minute.toString().padStart(2, '0')}"
}

private const val HALF_DAY = 12
