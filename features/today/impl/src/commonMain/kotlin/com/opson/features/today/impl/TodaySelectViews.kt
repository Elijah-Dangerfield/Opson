package com.opson.features.today.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.DragHandle
import com.opson.libraries.diary.ui.RemoveConfirmRow
import com.opson.libraries.diary.ui.label
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.HeaderIconButton
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.libraries.ui.components.SheetHandle
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.checkbox.RoundCheck
import com.opson.libraries.ui.components.chip.ChoiceChip
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_day_today
import opson.libraries.resources.generated.resources.today_day_tomorrow
import opson.libraries.resources.generated.resources.today_day_yesterday
import opson.libraries.resources.generated.resources.today_remove_action
import opson.libraries.resources.generated.resources.today_remove_keep
import opson.libraries.resources.generated.resources.today_remove_many
import opson.libraries.resources.generated.resources.today_select_back_cd
import opson.libraries.resources.generated.resources.today_select_close_cd
import opson.libraries.resources.generated.resources.today_drag_more
import opson.libraries.resources.generated.resources.today_select_all
import opson.libraries.resources.generated.resources.today_select_copy
import opson.libraries.resources.generated.resources.today_select_copy_action
import opson.libraries.resources.generated.resources.today_select_copy_title
import opson.libraries.resources.generated.resources.today_select_delete
import opson.libraries.resources.generated.resources.today_select_foods_one
import opson.libraries.resources.generated.resources.today_select_foods_other
import opson.libraries.resources.generated.resources.today_select_move
import opson.libraries.resources.generated.resources.today_select_move_action
import opson.libraries.resources.generated.resources.today_select_move_title
import opson.libraries.resources.generated.resources.today_select_save_meal
import opson.libraries.resources.generated.resources.today_select_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * Select mode (design sync 4, 13.3b and 13.3c): the bar that replaces the day header while
 * selecting, the bottom bar with Save as meal, Move, Copy to… and Delete, the panel each of those opens, and
 * the card a drag carries. Nothing here shows an amount, so every mode draws the same.
 */

/** "2 selected" with Close and Select all. */
@Composable
internal fun SelectHeader(selection: Selection, onAction: (TodayAction) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface)
            .padding(start = 4.dp, end = AppTheme.spacing.row, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeaderIconButton(
            icon = AppIcons.Close.described(stringResource(Res.string.today_select_close_cd)),
            onClick = { onAction(TodayAction.SelectionClosed) },
        )
        Text(
            text = stringResource(Res.string.today_select_title, selection.count),
            typography = AppTheme.type.subH1,
            color = AppTheme.colors.ink,
            modifier = Modifier.weight(1f),
        )
        FlatButton(
            text = stringResource(Res.string.today_select_all),
            onClick = { onAction(TodayAction.SelectAllTapped) },
            tone = FlatButtonTone.AccentText,
            minHeight = MinTouchTarget,
            typography = AppTheme.type.button,
        )
    }
}

/** The bottom bar while selecting, or the panel for the step it opened. */
@Composable
internal fun SelectPanel(selection: Selection, onAction: (TodayAction) -> Unit, modifier: Modifier = Modifier) {
    when (val step = selection.step) {
        SelectStep.Actions -> SelectBar(enabled = selection.count > 0, onAction = onAction, modifier = modifier)
        else -> Column(
            modifier = modifier
                .fillMaxWidth()
                .shadow(12.dp, PanelShape, clip = false)
                .clip(PanelShape)
                .background(AppTheme.colors.surface)
                .navigationBarsPadding()
                .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
        ) {
            SheetHandle(Modifier.padding(top = 10.dp))
            when (step) {
                is SelectStep.CopyTo -> CopyTo(step, onAction)
                is SelectStep.MoveTo -> MoveTo(step, onAction)
                SelectStep.ConfirmRemove -> RemoveConfirmRow(
                    question = stringResource(Res.string.today_remove_many, selection.count),
                    keepLabel = stringResource(Res.string.today_remove_keep),
                    removeLabel = stringResource(Res.string.today_remove_action),
                    onKeep = { onAction(TodayAction.SelectStepBack) },
                    onRemove = { onAction(TodayAction.RemoveSelectedConfirmed) },
                )
                SelectStep.Actions -> Unit
            }
        }
    }
}

/**
 * Save as meal, Move, Copy to… and Delete, edge to edge where the tab bar was. Move is also how to
 * move without dragging. Opened from the ⋮ menu nothing is picked yet, and the four wait, dimmed,
 * for the first tap on a food.
 */
@Composable
private fun SelectBar(enabled: Boolean, onAction: (TodayAction) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(AppTheme.colors.surface).navigationBarsPadding()) {
        HorizontalDivider(color = AppTheme.colors.line)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            BarAction(AppIcons.BookmarkAdd, stringResource(Res.string.today_select_save_meal), enabled, Modifier.weight(1f)) {
                onAction(TodayAction.SaveAsMealTapped)
            }
            BarAction(AppIcons.DriveFileMove, stringResource(Res.string.today_select_move), enabled, Modifier.weight(1f)) { onAction(TodayAction.MoveTapped) }
            BarAction(AppIcons.ContentCopy, stringResource(Res.string.today_select_copy), enabled, Modifier.weight(1f)) { onAction(TodayAction.CopyToTapped) }
            BarAction(AppIcons.Delete, stringResource(Res.string.today_select_delete), enabled, Modifier.weight(1f)) {
                onAction(TodayAction.RemoveSelectedTapped)
            }
        }
    }
}

@Composable
private fun BarAction(icon: IconResource, label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = if (enabled) AppTheme.colors.ink else AppTheme.colors.muted
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 64.dp)
            .bounceClick(scaleDown = 0.95f, enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon = icon, size = 28.dp, color = color)
        Text(text = label, typography = AppTheme.type.bodyBold.ExtraBold, color = color, maxLines = 1)
    }
}

/**
 * The card under the finger while picked foods are dragged (13.3c): the tick, "Apple + 1 more"
 * and "2 foods", and the handle it was picked up by. Never an amount.
 */
@Composable
internal fun DragGhost(selection: Selection, modifier: Modifier = Modifier) {
    val lead = selection.lead.orEmpty()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { rotationZ = GHOST_TILT }
            .shadow(16.dp, GhostShape, clip = false)
            .clip(GhostShape)
            .background(AppTheme.colors.surface)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        RoundCheck(checked = true)
        Column(Modifier.weight(1f)) {
            Text(
                text = if (selection.count > 1) stringResource(Res.string.today_drag_more, lead, selection.count - 1) else lead,
                typography = AppTheme.type.bodyBold.ExtraBold,
                color = AppTheme.colors.ink,
                maxLines = 1,
            )
            Text(
                text = if (selection.count == 1) {
                    stringResource(Res.string.today_select_foods_one)
                } else {
                    stringResource(Res.string.today_select_foods_other, selection.count)
                },
                typography = AppTheme.type.secondarySmall,
                color = AppTheme.colors.muted,
            )
        }
        DragHandle()
    }
}

@Composable
private fun StepTitle(title: String, onAction: (TodayAction) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        RoundIconButton(
            icon = AppIcons.ArrowBack.described(stringResource(Res.string.today_select_back_cd)),
            onClick = { onAction(TodayAction.SelectStepBack) },
            size = MinTouchTarget,
            containerColor = AppTheme.colors.soft,
            contentColor = AppTheme.colors.ink,
        )
        Text(text = title, typography = AppTheme.type.sheetTitle, color = AppTheme.colors.ink)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CopyTo(step: SelectStep.CopyTo, onAction: (TodayAction) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        StepTitle(stringResource(Res.string.today_select_copy_title), onAction)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            CopyDay.entries.forEach { day ->
                ChoiceChip(
                    label = stringResource(day.label),
                    selected = step.day == day,
                    onClick = { onAction(TodayAction.CopyDayPicked(day)) },
                    unselectedColor = AppTheme.colors.surface,
                )
            }
        }
        SlotChips(MealSlot.meals, step.slot) { onAction(TodayAction.CopySlotPicked(it)) }
        FlatButton(
            text = stringResource(Res.string.today_select_copy_action),
            onClick = { onAction(TodayAction.CopyConfirmed) },
            tone = FlatButtonTone.Accent,
            centered = true,
            enabled = step.slot != null,
            minHeight = FlatButtonDefaults.TallMinHeight,
            typography = AppTheme.type.button,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// TODO(design-sync-2): Move also changes the time (2.12, "Move changes the meal or time"). The design
// has no frame or copy for the time control, so it waits for one; `TimePickerDialog` in :libraries:ui
// and `DiaryRepository.updateItem` (rewriting `loggedAt`) are all it needs.
@Composable
private fun MoveTo(step: SelectStep.MoveTo, onAction: (TodayAction) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        StepTitle(stringResource(Res.string.today_select_move_title), onAction)
        SlotChips(MealSlot.meals + MealSlot.Uncategorized, step.slot) { onAction(TodayAction.MoveSlotPicked(it)) }
        FlatButton(
            text = stringResource(Res.string.today_select_move_action),
            onClick = { onAction(TodayAction.MoveConfirmed) },
            tone = FlatButtonTone.Accent,
            centered = true,
            enabled = step.slot != null,
            minHeight = FlatButtonDefaults.TallMinHeight,
            typography = AppTheme.type.button,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotChips(slots: List<MealSlot>, picked: MealSlot?, onPick: (MealSlot) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        slots.forEach { slot ->
            ChoiceChip(label = stringResource(slot.label), selected = picked == slot, onClick = { onPick(slot) }, unselectedColor = AppTheme.colors.surface)
        }
    }
}

/** "Yesterday", "Today", "Tomorrow": Copy to… here and on a food's sheet. */
internal val CopyDay.label: StringResource
    get() = when (this) {
        CopyDay.Yesterday -> Res.string.today_day_yesterday
        CopyDay.Today -> Res.string.today_day_today
        CopyDay.Tomorrow -> Res.string.today_day_tomorrow
    }

private val PanelShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val GhostShape = RoundedCornerShape(20.dp)

/** The carried card leans a little, so it reads as lifted off the list. */
private const val GHOST_TILT = -1.5f
