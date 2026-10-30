package com.opson.features.logger.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.DayClosedNotice
import com.opson.libraries.diary.ui.label
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.MenuItem
import com.opson.libraries.ui.components.PopoverMenu
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.IconSize
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.RelativeDay
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.color.ToneColors
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.adding_action_copy
import opson.libraries.resources.generated.resources.adding_action_copy_cd
import opson.libraries.resources.generated.resources.adding_action_describe
import opson.libraries.resources.generated.resources.adding_action_exercise
import opson.libraries.resources.generated.resources.adding_action_fasting
import opson.libraries.resources.generated.resources.adding_action_check_in
import opson.libraries.resources.generated.resources.adding_action_note
import opson.libraries.resources.generated.resources.adding_action_pack
import opson.libraries.resources.generated.resources.adding_action_photo
import opson.libraries.resources.generated.resources.adding_action_save_meal
import opson.libraries.resources.generated.resources.adding_action_save_meal_cd
import opson.libraries.resources.generated.resources.adding_action_saved
import opson.libraries.resources.generated.resources.adding_action_say
import opson.libraries.resources.generated.resources.adding_action_scan
import opson.libraries.resources.generated.resources.adding_action_search
import opson.libraries.resources.generated.resources.adding_action_water
import opson.libraries.resources.generated.resources.adding_action_weigh
import opson.libraries.resources.generated.resources.adding_title
import opson.libraries.resources.generated.resources.logger_close_cd
import opson.libraries.resources.generated.resources.logger_for_meal
import opson.libraries.resources.generated.resources.logger_loading_cd
import opson.libraries.resources.generated.resources.logger_meal_picker_cd
import opson.libraries.resources.generated.resources.logger_recent
import org.jetbrains.compose.resources.stringResource
import opson.libraries.resources.generated.resources.builder_verify_tile

/**
 * The add sheet's body (design sync 4, 13.4a and 13.4b). On [LoggerPane.Actions]: the title with
 * the meal dropdown, the grid, and Recent under it at full height. Describe it and Saved meals
 * are the other two panes. Stateless; the entry point puts it in a `BottomSheet` with a fixed
 * height, which is what lets the grid scroll and fade inside it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoggerSheetContent(
    state: LoggerState,
    onAction: (LoggerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = state.pane != LoggerPane.Actions) { onAction(LoggerAction.BackTapped) }
    Column(modifier = modifier.fillMaxSize()) {
        when (state.pane) {
            LoggerPane.Actions -> ActionsPane(state, onAction)
            LoggerPane.Describe -> DescribePane(state, onAction)
            LoggerPane.Saved -> SavedPane(state, onAction)
        }
    }
}

@Composable
private fun ActionsPane(state: LoggerState, onAction: (LoggerAction) -> Unit) {
    val listState = rememberLazyListState()
    Column(modifier = Modifier.fillMaxSize()) {
        AddTitle(state, onAction)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
                verticalArrangement = Arrangement.spacedBy(GRID_ROW_GAP),
            ) {
                if (state.loading) {
                    item(key = "loading", contentType = "loading") {
                        DelayedLoadingIndicator(contentDescription = stringResource(Res.string.logger_loading_cd))
                    }
                    return@LazyColumn
                }
                if (state.closed) {
                    item(key = "closed", contentType = "closed") {
                        DayClosedNotice(isToday = state.day == RelativeDay.Today, onReopen = { onAction(LoggerAction.ReopenTapped) })
                    }
                }
                state.actions.chunked(GRID_COLUMNS).forEachIndexed { index, row ->
                    item(key = "grid-$index", contentType = "grid") { GridRow(row, onAction) }
                }
                // Recent is what drag-up reveals; at 60% the grid already fills the sheet.
                if (state.height == SheetHeight.Full && state.recent.isNotEmpty()) {
                    item(key = "recent-label", contentType = "label") {
                        Text(
                            text = stringResource(Res.string.logger_recent),
                            typography = AppTheme.type.secondaryStrong,
                            color = AppTheme.colors.muted,
                            modifier = Modifier.padding(top = AppTheme.spacing.section),
                        )
                    }
                    itemsIndexed(state.recent, key = { _, row -> "recent:${row.key}" }, contentType = { _, _ -> "food" }) { index, row ->
                        AddFoodRow(
                            row = row,
                            onOpen = { onAction(LoggerAction.RecentNameTapped(row)) },
                            onAdd = { onAction(LoggerAction.RecentAddTapped(row)) },
                            divider = index > 0,
                        )
                    }
                }
            }
            if (state.height == SheetHeight.Partial) BottomFade(listState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/**
 * The edge fades while there is more below (13.4a), so the half sheet reads as the top of
 * something rather than all of it. Read in draw, so scrolling does not recompose the sheet.
 */
@Composable
private fun BottomFade(listState: LazyListState, modifier: Modifier) {
    val more by remember(listState) { derivedStateOf { listState.canScrollForward } }
    if (!more) return
    val bg = AppTheme.colors.bg.color
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FADE_HEIGHT)
            .background(Brush.verticalGradient(listOf(Color.Transparent, bg))),
    )
}

/** Close, "Add to" over the meal with its dropdown. */
@Composable
private fun AddTitle(state: LoggerState, onAction: (LoggerAction) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val canPick = state.slots.size > 1
    val meal = stringResource(state.slot.label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.chip, bottom = AppTheme.spacing.section),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            val pickerDescription = stringResource(Res.string.logger_meal_picker_cd, meal)
            Row(
                modifier = if (canPick) {
                    Modifier
                        .semantics(mergeDescendants = true) {
                            role = Role.DropdownList
                            contentDescription = pickerDescription
                        }
                        .bounceClick(scaleDown = 0.98f) { picking = true }
                } else {
                    Modifier
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
            ) {
                Text(
                    text = stringResource(Res.string.adding_title, meal),
                    typography = AppTheme.type.sheetTitle,
                    color = AppTheme.colors.ink,
                    maxLines = 2,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (canPick) Icon(icon = AppIcons.ArrowDropDown, size = IconSize.Medium, color = AppTheme.colors.muted)
            }
            PopoverMenu(expanded = picking, onDismissRequest = { picking = false }, alignment = Alignment.TopStart) {
                state.slots.forEachIndexed { index, slot ->
                    MenuItem(
                        label = stringResource(slot.label),
                        onClick = {
                            picking = false
                            onAction(LoggerAction.SlotPicked(slot))
                        },
                        icon = if (slot == state.slot) AppIcons.Check else null,
                        showDivider = index < state.slots.lastIndex,
                    )
                }
            }
        }
        RoundIconButton(
            icon = AppIcons.Close.described(stringResource(Res.string.logger_close_cd)),
            onClick = { onAction(LoggerAction.CloseTapped) },
            size = AppTheme.spacing.touchTarget,
            iconSize = IconSize.Medium.dp,
            containerColor = AppTheme.colors.bg,
            contentColor = AppTheme.colors.ink,
        )
    }
}

/** Three tiles, the last row's empty places left empty so every tile keeps its column. */
@Composable
private fun GridRow(actions: List<AddAction>, onAction: (LoggerAction) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        actions.forEach { action -> ActionTile(action, onAction, Modifier.weight(1f)) }
        repeat(GRID_COLUMNS - actions.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun ActionTile(action: AddAction, onAction: (LoggerAction) -> Unit, modifier: Modifier) {
    val tile = action.tile()
    val description = tile.description
    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.Button
                description?.let { contentDescription = it }
            }
            .bounceClick(scaleDown = 0.95f) { onAction(tile.action) }
            .defaultMinSize(minHeight = AppTheme.spacing.touchTarget),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Box(
            modifier = Modifier
                .size(TILE_SIZE)
                .clip(AppTheme.shapes.pill)
                .background(tile.tone.container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon = tile.icon, size = TILE_ICON, color = tile.tone.content)
        }
        Text(text = tile.label, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink, textAlign = TextAlign.Center, maxLines = 2)
    }
}

private data class Tile(
    val label: String,
    val icon: IconResource,
    val tone: ToneColors,
    val action: LoggerAction,
    val description: String? = null,
)

/** Food actions on the accent tint; water, weight, fasting and exercise in the cool tone; the note warm, as 13.4a draws them. */
@Composable
private fun AddAction.tile(): Tile {
    val colors = AppTheme.colors
    val food = ToneColors(content = colors.accent, container = colors.accentTint)
    val body = colors.nutrients.fat
    val note = colors.nutrients.carbs
    return when (this) {
        AddAction.SearchFood -> Tile(stringResource(Res.string.adding_action_search), AppIcons.Search, food, LoggerAction.SearchFoodTapped)
        AddAction.ScanBarcode -> Tile(stringResource(Res.string.adding_action_scan), AppIcons.BarcodeScanner, food, LoggerAction.BarcodeTapped)
        AddAction.PackagedFood -> Tile(stringResource(Res.string.adding_action_pack), AppIcons.PostAdd, food, LoggerAction.PackagedFoodTapped)
        AddAction.Photo -> Tile(stringResource(Res.string.adding_action_photo), AppIcons.PhotoCamera, food, LoggerAction.PhotoTapped)
        is AddAction.SayOrType -> Tile(
            stringResource(if (voice) Res.string.adding_action_say else Res.string.adding_action_describe),
            if (voice) AppIcons.Mic else AppIcons.EditNote,
            food,
            LoggerAction.DescribeItTapped,
        )
        AddAction.SavedMeals -> Tile(stringResource(Res.string.adding_action_saved), AppIcons.Bookmark, food, LoggerAction.SavedMealsTapped)
        is AddAction.CopyMeal -> Tile(
            stringResource(Res.string.adding_action_copy),
            AppIcons.ContentCopy,
            food,
            LoggerAction.CopyMealTapped,
            description = stringResource(Res.string.adding_action_copy_cd, stringResource(slot.sentenceName)),
        )
        is AddAction.SaveAsMeal -> Tile(
            stringResource(Res.string.adding_action_save_meal),
            AppIcons.BookmarkAdd,
            food,
            LoggerAction.SaveAsMealTapped,
            description = stringResource(Res.string.adding_action_save_meal_cd, stringResource(slot.sentenceName)),
        )
        AddAction.Water -> Tile(stringResource(Res.string.adding_action_water), AppIcons.WaterDrop, body, LoggerAction.WaterTapped)
        AddAction.WeighIn -> Tile(stringResource(Res.string.adding_action_weigh), AppIcons.MonitorWeight, body, LoggerAction.WeighInTapped)
        AddAction.Note -> Tile(stringResource(Res.string.adding_action_note), AppIcons.EditNote, note, LoggerAction.NoteTapped)
        AddAction.CheckIn -> Tile(stringResource(Res.string.adding_action_check_in), AppIcons.Mood, note, LoggerAction.CheckInTapped)
        AddAction.Fasting -> Tile(stringResource(Res.string.adding_action_fasting), AppIcons.HourglassTop, body, LoggerAction.FastingTapped)
        AddAction.Exercise -> Tile(stringResource(Res.string.adding_action_exercise), AppIcons.FitnessCenter, body, LoggerAction.ExerciseTapped)
        AddAction.VerifyFood -> Tile(stringResource(Res.string.builder_verify_tile), AppIcons.Builder, colors.builder, LoggerAction.VerifyFoodTapped)
    }
}

/** "For dinner", the overline on the Describe and Saved panes. */
@Composable
internal fun forMeal(slot: MealSlot): String = stringResource(Res.string.logger_for_meal, stringResource(slot.sentenceName))

/** A bold accent text button with a 48 dp tall touch area: "Just save the name", "Describe it". */
@Composable
internal fun InlineLink(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .semantics { role = Role.Button }
            .bounceClick(scaleDown = 0.97f, onClick = onClick)
            .defaultMinSize(minHeight = AppTheme.spacing.touchTarget),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.accent)
    }
}


private const val GRID_COLUMNS = 3
private val GRID_ROW_GAP = 20.dp
private val TILE_SIZE = 68.dp
private val TILE_ICON = 30.dp
private val FADE_HEIGHT = 96.dp
