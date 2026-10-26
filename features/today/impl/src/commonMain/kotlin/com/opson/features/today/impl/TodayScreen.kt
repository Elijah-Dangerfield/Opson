package com.opson.features.today.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.FastingStatus
import com.opson.libraries.diary.ui.FastingTodayCard
import com.opson.libraries.diary.ui.FoodListCard
import com.opson.libraries.diary.ui.FoodsLine
import com.opson.libraries.diary.ui.JournalEntry
import com.opson.libraries.diary.ui.MealCardItem
import com.opson.libraries.diary.ui.MealCardRow
import com.opson.libraries.diary.ui.MealRowCard
import com.opson.libraries.diary.ui.RemoveConfirmRow
import com.opson.libraries.diary.ui.SafeFoodsCard
import com.opson.libraries.diary.ui.SelectableFoodRow
import com.opson.libraries.diary.ui.SwipeFoodRow
import com.opson.libraries.diary.ui.TodayCard
import com.opson.libraries.diary.ui.TodayCardPadding
import com.opson.libraries.diary.ui.TodayMealCard
import com.opson.libraries.diary.ui.WaterCard
import com.opson.libraries.diary.ui.icon
import com.opson.libraries.diary.ui.label
import com.opson.libraries.diary.ui.rowLabel
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.diary.ui.tileColors
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.components.Banner
import com.opson.libraries.ui.components.ChecklistAction
import com.opson.libraries.ui.components.ChecklistRow
import com.opson.libraries.ui.components.CountChip
import com.opson.libraries.ui.components.DayHeader
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HeaderIconButton
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.InfoBanner
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.ListRowChevron
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
import com.opson.libraries.ui.components.TintTile
import com.opson.libraries.ui.components.TintTileGrid
import com.opson.libraries.ui.components.UndoBar
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.libraries.ui.time.RelativeDay
import com.opson.system.AppTheme
import com.opson.system.color.ToneColors
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.reminders_rhythm_change
import opson.libraries.resources.generated.resources.reminders_rhythm_next
import opson.libraries.resources.generated.resources.reminders_rhythm_next_body
import opson.libraries.resources.generated.resources.reminders_rhythm_off
import opson.libraries.resources.generated.resources.reminders_rhythm_off_body
import opson.libraries.resources.generated.resources.reminders_rhythm_set_up
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
import opson.libraries.resources.generated.resources.today_counts_glasses_one
import opson.libraries.resources.generated.resources.today_counts_glasses_other
import opson.libraries.resources.generated.resources.today_counts_meals_one
import opson.libraries.resources.generated.resources.today_counts_meals_other
import opson.libraries.resources.generated.resources.today_day_menu_cd
import opson.libraries.resources.generated.resources.today_explorer_safe
import opson.libraries.resources.generated.resources.today_explorer_safe_empty
import opson.libraries.resources.generated.resources.today_feel_pair
import opson.libraries.resources.generated.resources.today_feel_prompt
import opson.libraries.resources.generated.resources.today_item_sub
import opson.libraries.resources.generated.resources.today_loading_cd
import opson.libraries.resources.generated.resources.today_label_today
import opson.libraries.resources.generated.resources.today_label_tomorrow
import opson.libraries.resources.generated.resources.today_label_yesterday
import opson.libraries.resources.generated.resources.today_meal_around
import opson.libraries.resources.generated.resources.today_meal_usually
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
import opson.libraries.resources.generated.resources.today_move_to
import opson.libraries.resources.generated.resources.today_next_day_cd
import opson.libraries.resources.generated.resources.today_not_planned
import opson.libraries.resources.generated.resources.today_pick_day_cd
import opson.libraries.resources.generated.resources.today_plan_body
import opson.libraries.resources.generated.resources.today_plan_it
import opson.libraries.resources.generated.resources.today_plan_title
import opson.libraries.resources.generated.resources.today_planned
import opson.libraries.resources.generated.resources.today_prev_day_cd
import opson.libraries.resources.generated.resources.today_remove_action
import opson.libraries.resources.generated.resources.today_remove_keep
import opson.libraries.resources.generated.resources.today_remove_one
import opson.libraries.resources.generated.resources.today_removed_one
import opson.libraries.resources.generated.resources.today_removed_other
import opson.libraries.resources.generated.resources.today_rhythm_already_ate
import opson.libraries.resources.generated.resources.today_rhythm_ate
import opson.libraries.resources.generated.resources.today_rhythm_log
import opson.libraries.resources.generated.resources.today_rhythm_logged
import opson.libraries.resources.generated.resources.today_rhythm_planned
import opson.libraries.resources.generated.resources.today_rhythm_title
import opson.libraries.resources.generated.resources.today_select_start
import opson.libraries.resources.generated.resources.today_swipe_delete
import opson.libraries.resources.generated.resources.today_tile_ate
import opson.libraries.resources.generated.resources.today_tile_items_one
import opson.libraries.resources.generated.resources.today_tile_items_other
import opson.libraries.resources.generated.resources.today_untracked_action
import opson.libraries.resources.generated.resources.today_untracked_body
import opson.libraries.resources.generated.resources.today_untracked_title
import opson.libraries.resources.generated.resources.today_water
import opson.libraries.resources.generated.resources.today_water_cd
import opson.libraries.resources.generated.resources.today_weight
import opson.libraries.resources.generated.resources.today_weight_this_morning
import opson.libraries.resources.generated.resources.today_weight_today
import opson.libraries.resources.generated.resources.today_what_you_had
import org.jetbrains.compose.resources.stringResource

/**
 * The Today tab (design sync 2, 2.1 to 2.14): the pinned day header (or the select header), the
 * collapsed summary line when the person chose it, then the list: the summary card the layout
 * and levels call for, the cards the state carries, the meals, and Close the day. Once the card
 * scrolls away its one-line form sticks under the header. Stateless; [TodayViewModel] owns what
 * it shows and the scroll is the only thing kept here.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TodayScreen(
    state: TodayState,
    onAction: (TodayAction) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val rows = remember(state) { todayRows(state) }
    val scope = rememberCoroutineScope()
    // Back leaves selecting, or puts a row's confirmation away, before it leaves Today.
    BackHandler(enabled = state.selection != null) { onAction(TodayAction.SelectionClosed) }
    BackHandler(enabled = state.swipe.confirming != null) { onAction(TodayAction.RowRemoveKept) }
    LaunchedEffect(state.highlight) {
        val slot = state.highlight ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it is TodayRow.Meal && it.entry.slot == slot }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    // Scrolling closes a row left open with Delete showing (2.13).
    LaunchedEffect(listState, state.swipe.open) {
        if (state.swipe.open == null) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }.filter { it }.first()
        onAction(TodayAction.ListScrolled)
    }
    val anchor = rows.indexOfFirst { it is TodayRow.Summary || it is TodayRow.Rhythm }.coerceAtLeast(0)
    val scrolledPast by remember(listState, anchor) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            val size = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == anchor }?.size ?: 0
            first > anchor || (first == anchor && size > 0 && listState.firstVisibleItemScrollOffset > size * STICK_AT)
        }
    }
    val selection = state.selection
    val compact = state.compact
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            if (selection != null) {
                SelectHeader(selection, onAction)
            } else {
                Box(Modifier.padding(horizontal = AppTheme.spacing.row)) { Header(state, onAction) }
            }
            if (state.collapsed && compact != null && selection == null) {
                CollapsedSummary(compact, onAction, Modifier.padding(start = AppTheme.spacing.row, end = AppTheme.spacing.row, bottom = AppTheme.spacing.section))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = AppTheme.spacing.row,
                        end = AppTheme.spacing.row,
                        top = if (selection != null) AppTheme.spacing.section else 0.dp,
                        bottom = if (selection != null) SelectPanelRoom else AppTheme.spacing.section * 2,
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
                ) {
                    items(rows, key = { it.key }) { row -> TodayListItem(row, state, onAction) }
                }
                StickyLayer(
                    compact = compact?.takeIf { !state.collapsed && selection == null },
                    visible = scrolledPast,
                    page = state.summaryPage,
                    onScrollToTop = { scope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = AppTheme.spacing.row),
                )
                Column(Modifier.align(Alignment.BottomCenter)) {
                    BottomBars(state, onAction)
                    selection?.let { SelectPanel(it, onAction) }
                }
            }
        }
    }
}

/** The sticky bar under the header (2.1c), sliding in once the card has scrolled away. */
@Composable
private fun StickyLayer(compact: CompactSummary?, visible: Boolean, page: Int, onScrollToTop: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = compact != null && visible,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier,
    ) {
        compact?.let { StickyBar(it, page, onScrollToTop = onScrollToTop) }
    }
}

/** The Undo bars: after adding (3.9) and after a removal (2.14), six seconds each. */
@Composable
private fun BottomBars(state: TodayState, onAction: (TodayAction) -> Unit) {
    val padding = Modifier.padding(AppTheme.spacing.row)
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
            modifier = padding,
        )
    }
    state.removed?.let { removed ->
        UndoBar(
            message = if (removed.name != null && removed.count == 1) {
                stringResource(Res.string.today_removed_one, TodayStateBuilder.inSentence(removed.name))
            } else {
                stringResource(Res.string.today_removed_other, removed.count)
            },
            actionLabel = stringResource(Res.string.today_added_undo),
            onAction = { onAction(TodayAction.RemovedUndoTapped) },
            modifier = padding,
        )
    }
}

/** One item of the Today list, in order. A pure mapping, so the highlight can find its meal's index. */
internal sealed interface TodayRow {
    val key: String

    /** The day has not been read yet; the header already shows which day it is. */
    data object Loading : TodayRow { override val key = "loading" }
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
    data class Weight(val weight: WeightRow) : TodayRow { override val key = "weight" }
    data class Meal(val entry: MealEntry) : TodayRow { override val key = "meal-${entry.slot.wire}" }
    data class Had(val had: WhatYouHad) : TodayRow { override val key = "what-you-had" }
    data class Close(val card: CloseDayCard) : TodayRow { override val key = "close-day" }
}

internal fun todayRows(state: TodayState): List<TodayRow> = buildList {
    if (state.loading) {
        add(TodayRow.Loading)
        return@buildList
    }
    state.banner?.let { add(TodayRow.Banner(it)) }
    if (state.planning) add(TodayRow.Planning)
    // Collapsed, the card is the pinned line above the list instead.
    state.summary?.takeUnless { state.collapsed }?.let { add(TodayRow.Summary(it)) }
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
    state.weight?.let { add(TodayRow.Weight(it)) }
    state.meals.forEach { add(TodayRow.Meal(it)) }
    state.whatYouHad?.takeIf { state.rhythm != null }?.let { add(TodayRow.Had(it)) }
    state.closeDay?.let { add(TodayRow.Close(it)) }
}

@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
private fun TodayListItem(row: TodayRow, state: TodayState, onAction: (TodayAction) -> Unit) {
    when (row) {
        TodayRow.Loading -> DelayedLoadingIndicator(contentDescription = stringResource(Res.string.today_loading_cd))
        is TodayRow.Banner -> Banner(text = stringResource(row.banner.text, *row.banner.args.toTypedArray()))
        TodayRow.Planning -> InfoBanner(
            icon = AppIcons.EventNote,
            title = stringResource(Res.string.today_plan_title),
            body = stringResource(Res.string.today_plan_body),
        )
        is TodayRow.Summary -> Summary(row.summary, state, onAction)
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
            tone = AppTheme.colors.nutrients.carbs,
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
        is TodayRow.Weight -> WeightCard(row.weight, state.dayLabel, onAction)
        is TodayRow.Meal -> Meal(row.entry, state, onAction)
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

/** The day's weigh-in (2.1): "Weight / This morning · 7:02 AM", the weight or its soft word, and Weight. */
@Composable
private fun WeightCard(weight: WeightRow, dayLabel: RelativeDay, onAction: (TodayAction) -> Unit) {
    val time = weight.at.clock()
    TodayCard(contentPadding = TodayCardPadding.List, onClick = { onAction(TodayAction.WeightTapped) }) {
        ListRow(
            title = stringResource(Res.string.today_weight),
            subtitle = when {
                dayLabel == RelativeDay.Today && weight.morning -> stringResource(Res.string.today_weight_this_morning, time)
                dayLabel == RelativeDay.Today -> stringResource(Res.string.today_weight_today, time)
                else -> time
            },
            icon = AppIcons.MonitorWeight,
            iconTone = ToneColors(AppTheme.colors.ink, AppTheme.colors.soft),
        ) {
            NutrientText(Metric.Weight, weight.display, typography = AppTheme.type.cardTitle.Tabular, color = AppTheme.colors.ink, maxLines = 1)
            ListRowChevron()
        }
    }
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
private fun Meal(entry: MealEntry, state: TodayState, onAction: (TodayAction) -> Unit) {
    val slot = entry.slot
    val sentence = stringResource(slot.sentenceName)
    val add: @Composable RowScope.() -> Unit = {
        if (state.selection == null) {
            RoundIconButton(
                icon = AppIcons.Add.described(stringResource(Res.string.today_add_to_cd, sentence)),
                onClick = { onAction(TodayAction.MealPlusTapped(slot)) },
                size = MinTouchTarget,
            )
        }
    }
    when (entry) {
        is MealEntry.Full -> FullMeal(entry.card, state, onAction)
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
        // A planned meal is filled in from Plan it or the add sheet; the card itself has no + (2.10).
        is MealEntry.Planned -> MealRowCard(
            icon = slot.icon,
            tone = slot.tileColors(),
            title = stringResource(slot.label),
            subtitle = stringResource(Res.string.today_planned),
            calories = entry.calories,
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
private fun FullMeal(card: MealCard, state: TodayState, onAction: (TodayAction) -> Unit) {
    val sentence = stringResource(card.slot.sentenceName)
    val selection = state.selection
    TodayMealCard(
        icon = card.slot.icon,
        tone = card.slot.tileColors(),
        title = stringResource(card.title),
        time = card.time?.clock() ?: if (card.ateNoDetails) stringResource(Res.string.today_tile_ate) else null,
        calories = card.calories,
        macros = card.macros,
        macroStyle = card.macroStyle,
        items = card.items.map { item ->
            val precision = stringResource(item.precision.rowLabel)
            MealCardItem(
                key = item.entryId,
                name = item.name,
                sub = if (item.amount.isBlank()) precision else stringResource(Res.string.today_item_sub, item.amount, precision),
                calories = item.calories,
            )
        },
        feel = card.feel.map { stringResource(it) },
        note = card.note,
        highlighted = state.highlight == card.slot,
        onHeaderClick = if (selection != null) ({ onAction(TodayAction.MealHeaderToggled(card.slot)) }) else null,
        headerTrailing = {
            when {
                selection != null -> Unit
                card.fileable -> PillAction(
                    label = stringResource(Res.string.today_move_to),
                    onClick = { onAction(TodayAction.MoveUncategorizedTapped(card.items.map { it.entryId })) },
                )
                else -> RoundIconButton(
                    icon = AppIcons.Add.described(stringResource(Res.string.today_add_to_cd, sentence)),
                    onClick = { onAction(TodayAction.MealPlusTapped(card.slot)) },
                    size = MinTouchTarget,
                )
            }
        },
        itemRow = { item -> FoodRow(item, sentence, state, onAction) },
    )
}

/** One food on a meal card, as the moment calls for: tickable, its own confirmation, swipeable, or plain. */
@Composable
private fun FoodRow(item: MealCardItem, meal: String, state: TodayState, onAction: (TodayAction) -> Unit) {
    val id = item.key
    val selection = state.selection
    when {
        selection != null -> SelectableFoodRow(
            name = item.name,
            sub = item.sub,
            calories = item.calories,
            selected = id in selection.ids,
            onToggle = { onAction(TodayAction.FoodToggled(id)) },
        )
        state.swipe.confirming == id -> RemoveConfirmRow(
            question = stringResource(Res.string.today_remove_one, TodayStateBuilder.inSentence(item.name), meal),
            keepLabel = stringResource(Res.string.today_remove_keep),
            removeLabel = stringResource(Res.string.today_remove_action),
            onKeep = { onAction(TodayAction.RowRemoveKept) },
            onRemove = { onAction(TodayAction.RowRemoveConfirmed(id)) },
        )
        state.selectTools -> SwipeFoodRow(
            name = item.name,
            sub = item.sub,
            calories = item.calories,
            open = state.swipe.open == id,
            deleteLabel = stringResource(Res.string.today_swipe_delete),
            onClick = { onAction(TodayAction.ItemTapped(id)) },
            onOpened = { onAction(TodayAction.RowSwipedOpen(id)) },
            onClosed = { onAction(TodayAction.RowClosed) },
            onDelete = { onAction(TodayAction.DeleteRequested(id)) },
            onLongClick = { onAction(TodayAction.FoodLongPressed(id)) },
            longClickLabel = stringResource(Res.string.today_select_start),
        )
        else -> MealCardRow(item) { onAction(TodayAction.ItemTapped(id)) }
    }
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
                DayMenuItem.CopyToTomorrow -> Triple(Res.string.today_menu_copy_tomorrow, AppIcons.EventRepeat, TodayAction.CopyToTomorrowTapped)
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

/** The card is mostly out of view: past this share of its height, the sticky bar takes over. */
private const val STICK_AT = 0.6f

/** Room under the list for the select panel, so the last food can scroll above it. */
private val SelectPanelRoom = 300.dp
