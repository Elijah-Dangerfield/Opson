package com.opson.features.today.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import com.opson.libraries.diary.FastingStatus
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.DayClosedNotice
import com.opson.libraries.diary.ui.FastingTodayCard
import com.opson.libraries.diary.ui.FoodMenuButton
import com.opson.libraries.diary.ui.FoodsLine
import com.opson.libraries.diary.ui.MealCardItem
import com.opson.libraries.diary.ui.MealCardNote
import com.opson.libraries.diary.ui.MealNoteBlock
import com.opson.libraries.diary.ui.MealCardRow
import com.opson.libraries.diary.ui.MealRowCard
import com.opson.libraries.diary.ui.RemoveConfirmRow
import com.opson.libraries.diary.ui.SelectableFoodRow
import com.opson.libraries.diary.ui.SwipeFoodRow
import com.opson.libraries.diary.ui.TodayCard
import com.opson.libraries.diary.ui.TodayCardPadding
import com.opson.libraries.diary.ui.TodayMealCard
import com.opson.libraries.diary.ui.GlassLabel
import com.opson.libraries.diary.ui.WaterCard
import com.opson.libraries.diary.ui.icon
import com.opson.libraries.diary.ui.label
import com.opson.libraries.diary.ui.rowLabel
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.diary.ui.tileColors
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.components.Banner
import com.opson.libraries.ui.components.PullRefreshIndicator
import com.opson.libraries.ui.components.pullRefresh
import com.opson.libraries.ui.components.rememberPullRefresh
import com.opson.libraries.ui.components.DayHeader
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.HeaderIconButton
import com.opson.libraries.ui.components.InfoBanner
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.ListRowChevron
import com.opson.libraries.ui.components.MenuGroupDivider
import com.opson.libraries.ui.components.MenuIconItem
import com.opson.libraries.ui.components.MenuPopover
import com.opson.libraries.ui.components.MenuPopoverDefaults
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.libraries.ui.system.confirm
import com.opson.libraries.ui.components.PillAction
import com.opson.libraries.ui.components.PopoverMenu
import com.opson.libraries.ui.components.PromptAction
import com.opson.libraries.ui.components.PromptCard
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.UndoBar
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.time.DateCopy
import com.opson.libraries.ui.time.RelativeDay
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.color.ToneColors
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlin.math.roundToInt
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_add_to_cd
import opson.libraries.resources.generated.resources.today_added_one
import opson.libraries.resources.generated.resources.today_added_other
import opson.libraries.resources.generated.resources.today_added_undo
import opson.libraries.resources.generated.resources.today_close_day_cd
import opson.libraries.resources.generated.resources.today_closed_day_cd
import opson.libraries.resources.generated.resources.today_day_menu_cd
import opson.libraries.resources.generated.resources.today_drop_to
import opson.libraries.resources.generated.resources.today_food_menu_cd
import opson.libraries.resources.generated.resources.today_item_sub
import opson.libraries.resources.generated.resources.today_loading_cd
import opson.libraries.resources.generated.resources.today_label_today
import opson.libraries.resources.generated.resources.today_label_tomorrow
import opson.libraries.resources.generated.resources.today_label_yesterday
import opson.libraries.resources.generated.resources.today_meal_around
import opson.libraries.resources.generated.resources.today_meal_usually
import opson.libraries.resources.generated.resources.today_lowlog_body
import opson.libraries.resources.generated.resources.today_lowlog_later
import opson.libraries.resources.generated.resources.today_lowlog_skip
import opson.libraries.resources.generated.resources.today_lowlog_title
import opson.libraries.resources.generated.resources.today_menu_add_note
import opson.libraries.resources.generated.resources.today_menu_clear
import opson.libraries.resources.generated.resources.today_menu_clear_confirm
import opson.libraries.resources.generated.resources.today_menu_close_day
import opson.libraries.resources.generated.resources.today_menu_close_this_day
import opson.libraries.resources.generated.resources.today_menu_copy_tomorrow
import opson.libraries.resources.generated.resources.today_menu_copy_yesterday
import opson.libraries.resources.generated.resources.today_menu_edit_cards
import opson.libraries.resources.generated.resources.today_menu_notes
import opson.libraries.resources.generated.resources.today_menu_nutrients
import opson.libraries.resources.generated.resources.today_menu_reopen_day
import opson.libraries.resources.generated.resources.today_menu_reopen_this_day
import opson.libraries.resources.generated.resources.today_menu_tracked
import opson.libraries.resources.generated.resources.today_menu_untracked
import opson.libraries.resources.generated.resources.today_move_row
import opson.libraries.resources.generated.resources.today_move_to
import opson.libraries.resources.generated.resources.today_moved_one
import opson.libraries.resources.generated.resources.today_moved_other
import opson.libraries.resources.generated.resources.today_next_day_cd
import opson.libraries.resources.generated.resources.today_note_hunger_line
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
import opson.libraries.resources.generated.resources.today_select_start
import opson.libraries.resources.generated.resources.today_swipe_delete
import opson.libraries.resources.generated.resources.today_tile_ate
import opson.libraries.resources.generated.resources.today_untracked_action
import opson.libraries.resources.generated.resources.today_untracked_body
import opson.libraries.resources.generated.resources.today_untracked_title
import opson.libraries.resources.generated.resources.today_water
import opson.libraries.resources.generated.resources.today_water_cd
import opson.libraries.resources.generated.resources.today_water_glass_cd
import opson.libraries.resources.generated.resources.today_water_glass_empty
import opson.libraries.resources.generated.resources.today_water_glass_fill
import opson.libraries.resources.generated.resources.today_water_glass_full
import opson.libraries.resources.generated.resources.today_water_glass_take_off
import opson.libraries.resources.generated.resources.today_weight
import opson.libraries.resources.generated.resources.today_weight_this_morning
import opson.libraries.resources.generated.resources.today_weight_today
import org.jetbrains.compose.resources.stringResource

/**
 * The Today tab (design sync 2, 2.1 to 2.14): the pinned day header with Close the day's button
 * (or the select header), then the list: the summary card the layout and levels call for, the
 * cards the state carries, the meals, and Activity last. Once the card scrolls away its one-line
 * form sticks under the header. Stateless; [TodayViewModel] owns what it shows and the scroll is
 * the only thing kept here.
 */
@Composable
fun TodayScreen(
    state: TodayState,
    onAction: (TodayAction) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    activity: (@Composable () -> Unit)? = null,
    contributed: List<ContributedCard> = emptyList(),
    /** Pull to refresh is running (`TodayRefreshViewModel`). */
    refreshing: Boolean = false,
    /** Pull to refresh; null leaves the gesture off. */
    onRefresh: (() -> Unit)? = null,
) = TodayScreen(state, onAction, rememberFoodDrag(), modifier, listState, activity, contributed, refreshing, onRefresh)

/** [TodayScreen] with the [drag] handed in, so a preview can hold foods mid-drag (13.3c). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TodayScreen(
    state: TodayState,
    onAction: (TodayAction) -> Unit,
    drag: FoodDrag,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    /** The Activity card (`release.exercise`), with its own view model; null leaves it out. */
    activity: (@Composable () -> Unit)? = null,
    /** Cards other modules add (`TodayCardContribution`), drawn after Fasting. */
    contributed: List<ContributedCard> = emptyList(),
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    val hasActivity = activity != null
    val contributedKeys = contributed.map { it.key }
    val rows = remember(state, hasActivity, contributedKeys) { todayRows(state, hasActivity, contributedKeys) }
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
    val anchor = rows.indexOfFirst { it is TodayRow.Summary || it is TodayRow.Tiles }.coerceAtLeast(0)
    val scrolledPast by remember(listState, anchor) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            val size = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == anchor }?.size ?: 0
            first > anchor || (first == anchor && size > 0 && listState.firstVisibleItemScrollOffset > size * STICK_AT)
        }
    }
    val selection = state.selection
    val compact = state.compact
    val haptics = LocalHapticFeedback.current
    val dropTargets = remember(rows) { dropTargets(rows) }
    val rowContext = FoodRowContext.of(state)
    if (selection != null) FoodDragEffects(drag, listState, dropTargets, selection.slots)
    // Off while selecting: a pull there is the start of a drag.
    val pull = rememberPullRefresh(refreshing, onRefresh = { onRefresh?.invoke() }, enabled = onRefresh != null && selection == null)
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            if (selection != null) {
                SelectHeader(selection, onAction)
            } else {
                Box(Modifier.padding(horizontal = AppTheme.spacing.row)) { Header(state, onAction) }
            }
            // Clipped so the sticky bar slides out from under the header rather than over it. The box
            // follows a drag once a row's handle starts one (13.3c).
            Box(
                Modifier.weight(1f).fillMaxWidth().clipToBounds().pullRefresh(pull).followsFoodDrag(drag) { slot ->
                    haptics.confirm()
                    onAction(TodayAction.FoodsDropped(slot))
                },
            ) {
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
                    // A slot scrolled off is reused for a row of the same kind: a meal card for a meal card.
                    items(rows, key = { it.key }, contentType = { it::class }) { row ->
                        when (row) {
                            TodayRow.Activity -> activity?.invoke()
                            is TodayRow.Contributed -> contributed.firstOrNull { it.key == row.cardKey }?.content?.invoke()
                            else -> TodayListItem(row, state, rowContext, drag, onAction)
                        }
                    }
                }
                if (selection != null && drag.active) {
                    DragGhost(
                        selection = selection,
                        modifier = Modifier
                            .padding(horizontal = AppTheme.spacing.row + GhostInset)
                            .offset { IntOffset(0, (drag.y - GhostGrab.toPx()).roundToInt()) },
                    )
                }
                PullRefreshIndicator(pull, Modifier.align(Alignment.TopCenter))
                StickyLayer(
                    compact = compact?.takeIf { selection == null },
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

/**
 * The sticky bar under the header (2.1c), sliding in once the card has scrolled away. Its surface
 * stays opaque the whole way, so the rows under it never show through; only what is on it fades,
 * and in the draw phase.
 */
@Composable
private fun StickyLayer(compact: CompactSummary?, visible: Boolean, page: Int, onScrollToTop: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = compact != null && visible,
        enter = slideInVertically { -it / 2 },
        exit = slideOutVertically { -it / 2 },
        modifier = modifier,
    ) {
        val contentAlpha = transition.animateFloat(label = "StickyContent") { if (it == EnterExitState.Visible) 1f else 0f }
        compact?.let {
            Box(Modifier.clip(AppTheme.shapes.card).background(AppTheme.colors.surface)) {
                StickyBar(it, page, onScrollToTop = onScrollToTop, modifier = Modifier.graphicsLayer { alpha = contentAlpha.value })
            }
        }
    }
}

/** The Undo bars: after adding (3.9), after a removal (2.14) and after a move (13.3c), six seconds each. */
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
    state.moved?.let { moved ->
        val meal = stringResource(moved.slot.sentenceName)
        UndoBar(
            message = if (moved.name != null && moved.count == 1) {
                stringResource(Res.string.today_moved_one, TodayStateBuilder.inSentence(moved.name), meal)
            } else {
                stringResource(Res.string.today_moved_other, moved.count, meal)
            },
            actionLabel = stringResource(Res.string.today_added_undo),
            onAction = { onAction(TodayAction.MovedUndoTapped) },
            modifier = padding,
        )
    }
}

/** A card from another module ([com.opson.features.today.TodayCardContribution]), keyed for the list. */
data class ContributedCard(val key: String, val content: @Composable () -> Unit)

/** One item of the Today list, in order. A pure mapping, so the highlight can find its meal's index. */
internal sealed interface TodayRow {
    val key: String

    /** The day has not been read yet; the header already shows which day it is. */
    data object Loading : TodayRow { override val key = "loading" }
    data class Banner(val banner: TodayBanner) : TodayRow { override val key = "banner" }
    data object Planning : TodayRow { override val key = "planning" }
    data class Summary(val summary: TodaySummary) : TodayRow { override val key = "summary" }
    data object Untracked : TodayRow { override val key = "untracked" }
    data object LowLogging : TodayRow { override val key = "low-logging" }

    /** "Today is closed. Reopen it to add more.", standing in for every + the closed day has lost. */
    data object Closed : TodayRow { override val key = "closed" }
    data class Tiles(val tiles: List<MealTile>) : TodayRow { override val key = "tiles" }
    data class Fasting(val status: FastingStatus) : TodayRow { override val key = "fasting" }
    data class Water(val water: WaterRow) : TodayRow { override val key = "water" }
    data class Weight(val weight: WeightRow) : TodayRow { override val key = "weight" }
    data object Activity : TodayRow { override val key = "activity" }

    /** A card another module drew; see [ContributedCard]. */
    data class Contributed(val cardKey: String) : TodayRow { override val key = "contributed-$cardKey" }
    data class Meal(val entry: MealEntry) : TodayRow { override val key = "meal-${entry.slot.wire}" }
}

internal fun todayRows(state: TodayState, activity: Boolean = false, contributed: List<String> = emptyList()): List<TodayRow> = buildList {
    if (state.loading) {
        add(TodayRow.Loading)
        return@buildList
    }
    state.banner?.let { add(TodayRow.Banner(it)) }
    if (state.planning) add(TodayRow.Planning)
    state.summary?.let { add(TodayRow.Summary(it)) }
    if (state.untracked) add(TodayRow.Untracked)
    if (state.lowLogging) add(TodayRow.LowLogging)
    if (state.closed) add(TodayRow.Closed)
    state.mealTiles?.let { add(TodayRow.Tiles(it)) }
    state.fasting?.let { add(TodayRow.Fasting(it)) }
    contributed.forEach { add(TodayRow.Contributed(it)) }
    state.water?.let { add(TodayRow.Water(it)) }
    state.weight?.let { add(TodayRow.Weight(it)) }
    state.meals.forEach { add(TodayRow.Meal(it)) }
    // Below the meals: food is what Today is for, and what was burned is read after it (owner feedback, 2026-10-29).
    if (activity) add(TodayRow.Activity)
}

/** The rows foods can be dropped on, by list key: a meal with foods, or an empty one drawn as a row. */
private fun dropTargets(rows: List<TodayRow>): Map<Any, MealSlot> =
    rows.filterIsInstance<TodayRow.Meal>()
        .filter { it.entry is MealEntry.Full || it.entry is MealEntry.Collapsed }
        .associate { it.key to it.entry.slot }

@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
private fun TodayListItem(row: TodayRow, state: TodayState, context: FoodRowContext, drag: FoodDrag, onAction: (TodayAction) -> Unit) {
    when (row) {
        TodayRow.Loading -> DelayedLoadingIndicator(contentDescription = stringResource(Res.string.today_loading_cd))
        is TodayRow.Banner -> ModeTimerBanner(row.banner, onAction)
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
        TodayRow.LowLogging -> PromptCard(
            icon = AppIcons.BeachAccess,
            tone = ToneColors(AppTheme.colors.accent, AppTheme.colors.accentTint),
            title = stringResource(Res.string.today_lowlog_title),
            body = stringResource(Res.string.today_lowlog_body),
        ) {
            PromptAction(
                label = stringResource(Res.string.today_lowlog_later),
                onClick = { onAction(TodayAction.LowLoggingDismissed) },
                modifier = Modifier.weight(1f),
            )
            PromptAction(
                label = stringResource(Res.string.today_lowlog_skip),
                onClick = { onAction(TodayAction.LowLoggingSkipTapped) },
                modifier = Modifier.weight(1f),
                emphasis = true,
            )
        }
        TodayRow.Closed -> DayClosedNotice(isToday = state.dayLabel == RelativeDay.Today, onReopen = { onAction(TodayAction.ReopenTapped) })
        is TodayRow.Tiles -> MealTimelineCard(row.tiles, onAction)
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
            onAdd = if (state.closed) null else ({ onAction(TodayAction.WaterPlusTapped) }),
            addIcon = AppIcons.Add.described(stringResource(Res.string.today_water_cd)),
            onGlassTap = if (state.closed) null else ({ onAction(TodayAction.WaterGlassTapped(it)) }),
            glassLabels = glassLabels(row.water),
        )
        is TodayRow.Weight -> WeightCard(row.weight, state.dayLabel, onAction)
        // Drawn by the slots the entry point passes in; see TodayScreen.
        TodayRow.Activity, is TodayRow.Contributed -> Unit
        is TodayRow.Meal -> Meal(row.entry, context, drag, onAction)
    }
}

/** "Glass 3 of 8", full or empty, and what a tap on it does, for each of the water card's glasses. */
@Composable
private fun glassLabels(water: WaterRow): List<GlassLabel> {
    val full = stringResource(Res.string.today_water_glass_full)
    val empty = stringResource(Res.string.today_water_glass_empty)
    val fill = stringResource(Res.string.today_water_glass_fill)
    val takeOff = stringResource(Res.string.today_water_glass_take_off)
    return List(water.total) { index ->
        GlassLabel(
            name = stringResource(Res.string.today_water_glass_cd, index + 1, water.total),
            state = if (index < water.filled) full else empty,
            action = if (index + 1 == water.filled) takeOff else fill,
        )
    }
}

@Composable
private fun Header(state: TodayState, onAction: (TodayAction) -> Unit) {
    val label = state.dayLabel.text()
    DayHeader(
        title = label,
        previousIcon = AppIcons.ChevronLeft.described(stringResource(Res.string.today_prev_day_cd)),
        nextIcon = AppIcons.ChevronRight.described(stringResource(Res.string.today_next_day_cd)),
        onPrevious = { onAction(TodayAction.PreviousDayTapped) },
        onNext = { onAction(TodayAction.NextDayTapped) },
        onTitleClick = { onAction(TodayAction.DayLabelTapped) },
        titleDescription = stringResource(Res.string.today_pick_day_cd) + ", " + label,
        nextEnabled = state.canGoForward,
    ) {
        state.closeDay?.let { close ->
            // An empty circle until the day is closed, then filled: the check reads as done.
            val icon = if (close.closed) AppIcons.CheckCircle else AppIcons.CheckCircleOutline
            HeaderIconButton(
                icon = icon.described(stringResource(if (close.closed) Res.string.today_closed_day_cd else Res.string.today_close_day_cd)),
                onClick = { onAction(TodayAction.CloseDayButtonTapped) },
            )
        }
        Box {
            HeaderIconButton(
                icon = AppIcons.MoreVert.described(stringResource(Res.string.today_day_menu_cd)),
                onClick = { onAction(TodayAction.DayMenuTapped) },
                selected = state.menu != null,
            )
            PopoverMenu(menu = state.menu, onDismissRequest = { onAction(TodayAction.MenuDismissed) }) { menu ->
                DayMenuItems(menu, onAction)
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

/**
 * What a meal card and its food rows read from [TodayState], and nothing else. Equal from one state
 * to the next unless selecting, a swipe, the ⋮ flag, the outline or the day's closing changed, so an
 * unchanged card skips when the rest of Today moves (the fasting minute, an Undo bar, the sticky bar).
 */
@Immutable
internal data class FoodRowContext(
    /** The picked foods; null when not selecting. */
    val selected: Set<String>?,
    val swipe: SwipeState,
    val selectTools: Boolean,
    val foodMenus: Boolean,
    val highlight: MealSlot?,
    /** Closed with Close the day: no + on any meal. */
    val closed: Boolean,
) {
    val selecting: Boolean get() = selected != null

    companion object {
        fun of(state: TodayState) = FoodRowContext(state.selection?.ids, state.swipe, state.selectTools, state.foodMenus, state.highlight, state.closed)
    }
}

@Composable
private fun Meal(entry: MealEntry, context: FoodRowContext, drag: FoodDrag, onAction: (TodayAction) -> Unit) {
    val slot = entry.slot
    val sentence = stringResource(slot.sentenceName)
    val dropLabel = if (drag.target == slot) stringResource(Res.string.today_drop_to, stringResource(slot.label)) else null
    val add: @Composable RowScope.() -> Unit = {
        if (!context.selecting && !context.closed) {
            RoundIconButton(
                icon = AppIcons.Add.described(stringResource(Res.string.today_add_to_cd, sentence)),
                onClick = { onAction(TodayAction.MealPlusTapped(slot)) },
                size = MinTouchTarget,
            )
        }
    }
    when (entry) {
        is MealEntry.Full -> FullMeal(entry.card, context, drag, dropLabel, onAction)
        is MealEntry.Collapsed -> MealRowCard(
            icon = slot.icon,
            iconColor = slot.tileColors().content,
            title = stringResource(slot.label),
            subtitle = if (slot.isMeal) {
                stringResource(if (entry.usual) Res.string.today_meal_usually else Res.string.today_meal_around, entry.around.clock())
            } else {
                null
            },
            trailing = add,
            dropLabel = dropLabel,
            below = entry.note?.let { note -> { MealNoteBlock(note.forCard()) } },
        )
        // A planned meal is filled in from Plan it or the add sheet; the card itself has no + (2.10).
        is MealEntry.Planned -> MealRowCard(
            icon = slot.icon,
            iconColor = slot.tileColors().content,
            title = stringResource(slot.label),
            subtitle = stringResource(Res.string.today_planned),
            calories = entry.calories,
            below = { FoodsLine(entry.foods) },
        )
        is MealEntry.NotPlanned -> MealRowCard(
            icon = slot.icon,
            iconColor = slot.tileColors().content,
            title = stringResource(slot.label),
            subtitle = stringResource(Res.string.today_not_planned),
            trailing = { PillAction(label = stringResource(Res.string.today_plan_it), onClick = { onAction(TodayAction.PlanItTapped(slot)) }) },
        )
    }
}

@Composable
private fun FullMeal(card: MealCard, context: FoodRowContext, drag: FoodDrag, dropLabel: String?, onAction: (TodayAction) -> Unit) {
    val sentence = stringResource(card.slot.sentenceName)
    val selecting = context.selecting
    TodayMealCard(
        icon = card.slot.icon,
        iconColor = card.slot.tileColors().content,
        title = stringResource(card.title),
        time = card.time?.clock() ?: if (card.ateNoDetails) stringResource(Res.string.today_tile_ate) else null,
        calories = card.calories,
        macros = card.macros,
        macroStyle = card.macroStyle,
        items = card.items.map { item ->
            val precision = item.precision?.let { stringResource(it.rowLabel) }
            MealCardItem(
                key = item.entryId,
                name = item.name,
                sub = when {
                    precision == null -> item.amount
                    item.amount.isBlank() -> precision
                    else -> stringResource(Res.string.today_item_sub, item.amount, precision)
                },
                calories = item.calories,
                repeat = item.repeat,
            )
        },
        note = card.note?.forCard(),
        highlighted = context.highlight == card.slot,
        onHeaderClick = if (selecting) ({ onAction(TodayAction.MealHeaderToggled(card.slot)) }) else null,
        headerTrailing = {
            when {
                selecting || context.closed -> Unit
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
        dropLabel = dropLabel,
        itemRow = { item -> FoodRow(item, sentence, context, drag, onAction) },
    )
}

/**
 * One food on a meal card, as the moment calls for: tickable with its handle, its own
 * confirmation, swipeable, or plain. Outside select mode it carries its ⋮ (`release.foodActions`).
 */
@Composable
private fun FoodRow(item: MealCardItem, meal: String, context: FoodRowContext, drag: FoodDrag, onAction: (TodayAction) -> Unit) {
    val id = item.key
    val selected = context.selected
    val menu: @Composable RowScope.() -> Unit = {
        if (context.foodMenus) {
            FoodMenuButton(
                description = stringResource(Res.string.today_food_menu_cd, item.name),
                onClick = { onAction(TodayAction.ItemMenuTapped(id)) },
            )
        }
    }
    when {
        selected != null -> SelectableFoodRow(
            name = item.name,
            sub = item.sub,
            calories = item.calories,
            selected = id in selected,
            onToggle = { onAction(TodayAction.FoodToggled(id)) },
            dimmed = drag.active && id in selected,
            moveLabel = stringResource(Res.string.today_move_row),
            onMove = { onAction(TodayAction.MoveRowRequested(id)) },
            handle = { FoodDragHandle(drag, id, onAction) },
        )
        context.swipe.confirming == id -> RemoveConfirmRow(
            question = stringResource(Res.string.today_remove_one, TodayStateBuilder.inSentence(item.name), meal),
            keepLabel = stringResource(Res.string.today_remove_keep),
            removeLabel = stringResource(Res.string.today_remove_action),
            onKeep = { onAction(TodayAction.RowRemoveKept) },
            onRemove = { onAction(TodayAction.RowRemoveConfirmed(id)) },
        )
        context.selectTools -> SwipeFoodRow(
            name = item.name,
            sub = item.sub,
            calories = item.calories,
            repeat = item.repeat,
            open = context.swipe.open == id,
            deleteLabel = stringResource(Res.string.today_swipe_delete),
            onClick = { onAction(TodayAction.ItemTapped(id)) },
            onOpened = { onAction(TodayAction.RowSwipedOpen(id)) },
            onClosed = { onAction(TodayAction.RowClosed) },
            onDelete = { onAction(TodayAction.DeleteRequested(id)) },
            onLongClick = { onAction(TodayAction.FoodLongPressed(id)) },
            longClickLabel = stringResource(Res.string.today_select_start),
            trailing = menu,
        )
        else -> MealCardRow(item, onClick = { onAction(TodayAction.ItemTapped(id)) }, trailing = menu)
    }
}

/** "Hunger level: Hungry" over the words, as a meal's card shows them. */
@Composable
private fun MealNoteUi.forCard(): MealCardNote =
    MealCardNote(hunger = hunger?.let { stringResource(Res.string.today_note_hunger_line, stringResource(it)) }, text = text)

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
                    AppIcons.CheckCircleOutline,
                    TodayAction.DayCloseMenuTapped,
                )
                is DayMenuItem.Reopen -> Triple(
                    if (item.isToday) Res.string.today_menu_reopen_day else Res.string.today_menu_reopen_this_day,
                    AppIcons.CheckCircle,
                    TodayAction.ReopenTapped,
                )
                DayMenuItem.Nutrients -> Triple(Res.string.today_menu_nutrients, AppIcons.Science, TodayAction.DayNutrientsTapped)
                DayMenuItem.Notes -> Triple(Res.string.today_menu_notes, AppIcons.EventNote, TodayAction.NotesMenuTapped)
                DayMenuItem.CopyYesterday -> Triple(Res.string.today_menu_copy_yesterday, AppIcons.ContentCopy, TodayAction.CopyYesterdayTapped)
                DayMenuItem.CopyToTomorrow -> Triple(Res.string.today_menu_copy_tomorrow, AppIcons.EventRepeat, TodayAction.CopyToTomorrowTapped)
                DayMenuItem.AddNote -> Triple(Res.string.today_menu_add_note, AppIcons.EditNote, TodayAction.DayNoteTapped)
                is DayMenuItem.Untracked -> Triple(
                    if (item.marked) Res.string.today_menu_tracked else Res.string.today_menu_untracked,
                    AppIcons.BeachAccess,
                    TodayAction.UntrackedMenuTapped,
                )
                DayMenuItem.Select -> Triple(Res.string.today_select_start, AppIcons.Check, TodayAction.SelectMenuTapped)
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

/** Room under the list for select mode's bottom bar, so the last food can scroll above it. */
private val SelectPanelRoom = 140.dp

/** The carried card sits a little inside the list's edges (13.3c). */
private val GhostInset = 8.dp

/** Where the finger holds the carried card, from its top: about its middle. */
private val GhostGrab = 36.dp
