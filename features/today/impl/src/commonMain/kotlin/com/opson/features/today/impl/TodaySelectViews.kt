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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.RemoveConfirmRow
import com.opson.libraries.diary.ui.label
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientFigure
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.MinTouchTarget
import com.opson.libraries.ui.components.SheetHandle
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
import com.opson.libraries.ui.components.button.FlatButtonTone
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
import opson.libraries.resources.generated.resources.today_select_all_cd
import opson.libraries.resources.generated.resources.today_select_back_cd
import opson.libraries.resources.generated.resources.today_select_close_cd
import opson.libraries.resources.generated.resources.today_select_copy
import opson.libraries.resources.generated.resources.today_select_copy_action
import opson.libraries.resources.generated.resources.today_select_copy_title
import opson.libraries.resources.generated.resources.today_select_foods_one
import opson.libraries.resources.generated.resources.today_select_foods_other
import opson.libraries.resources.generated.resources.today_select_move
import opson.libraries.resources.generated.resources.today_select_move_action
import opson.libraries.resources.generated.resources.today_select_move_title
import opson.libraries.resources.generated.resources.today_select_remove
import opson.libraries.resources.generated.resources.today_select_title
import org.jetbrains.compose.resources.stringResource

/*
 * Select foods (design sync 2, 2.12): the header that replaces the day header while selecting,
 * and the panel that totals the picks and holds Copy to…, Move and Remove. Every total goes
 * through the policy, so Blind's panel says "3 foods" and offers the actions, nothing else.
 */

/** "3 selected" on the accent tint, with Close and Select all. */
@Composable
internal fun SelectHeader(selection: Selection, onAction: (TodayAction) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.accentTint)
            .padding(horizontal = AppTheme.spacing.row, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        RoundIconButton(
            icon = AppIcons.Close.described(stringResource(Res.string.today_select_close_cd)),
            onClick = { onAction(TodayAction.SelectionClosed) },
            size = MinTouchTarget,
            containerColor = AppTheme.colors.accentTint,
            contentColor = AppTheme.colors.accent,
        )
        Text(
            text = stringResource(Res.string.today_select_title, selection.count),
            typography = AppTheme.type.subH1,
            color = AppTheme.colors.accent,
            modifier = Modifier.weight(1f),
        )
        RoundIconButton(
            icon = AppIcons.SelectAll.described(stringResource(Res.string.today_select_all_cd)),
            onClick = { onAction(TodayAction.SelectAllTapped) },
            size = MinTouchTarget,
            containerColor = AppTheme.colors.accentTint,
            contentColor = AppTheme.colors.accent,
        )
    }
}

/** The panel at the bottom while selecting, on whichever step it is. */
@Composable
internal fun SelectPanel(selection: Selection, onAction: (TodayAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
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
        when (val step = selection.step) {
            SelectStep.Actions -> Actions(selection, onAction)
            is SelectStep.CopyTo -> CopyTo(step, onAction)
            is SelectStep.MoveTo -> MoveTo(step, onAction)
            SelectStep.ConfirmRemove -> RemoveConfirmRow(
                question = stringResource(Res.string.today_remove_many, selection.count),
                keepLabel = stringResource(Res.string.today_remove_keep),
                removeLabel = stringResource(Res.string.today_remove_action),
                onKeep = { onAction(TodayAction.SelectStepBack) },
                onRemove = { onAction(TodayAction.RemoveSelectedConfirmed) },
            )
        }
    }
}

@Composable
private fun Actions(selection: Selection, onAction: (TodayAction) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (selection.count == 1) {
                    stringResource(Res.string.today_select_foods_one)
                } else {
                    stringResource(Res.string.today_select_foods_other, selection.count)
                },
                typography = AppTheme.type.sheetTitle,
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            NutrientFigure(Metric.Calories, selection.calories, typography = AppTheme.type.figureLarge.Tabular, restTypography = AppTheme.type.cardTitle)
        }
        if (selection.macros.isNotEmpty()) {
            Row(Modifier.fillMaxWidth()) {
                selection.macros.forEach { macro ->
                    NutrientVisible(macro.display) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = stringResource(macro.label), typography = AppTheme.type.secondaryStrong.Bold, color = macro.metric.color())
                            NutrientText(macro.metric, macro.display, typography = AppTheme.type.cardTitle.Tabular, color = AppTheme.colors.ink)
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = AppTheme.colors.line)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PanelAction(AppIcons.ContentCopy, stringResource(Res.string.today_select_copy)) { onAction(TodayAction.CopyToTapped) }
            PanelAction(AppIcons.Schedule, stringResource(Res.string.today_select_move)) { onAction(TodayAction.MoveTapped) }
            PanelAction(AppIcons.Delete, stringResource(Res.string.today_select_remove)) { onAction(TodayAction.RemoveSelectedTapped) }
        }
    }
}

@Composable
private fun PanelAction(icon: IconResource, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .defaultMinSize(minWidth = 88.dp, minHeight = 64.dp)
            .semantics { role = Role.Button }
            .bounceClick(scaleDown = 0.95f, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon = icon, size = 28.dp, color = AppTheme.colors.accent)
        Text(text = label, typography = AppTheme.type.bodyBold.ExtraBold, color = AppTheme.colors.ink)
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

private val CopyDay.label
    get() = when (this) {
        CopyDay.Yesterday -> Res.string.today_day_yesterday
        CopyDay.Today -> Res.string.today_day_today
        CopyDay.Tomorrow -> Res.string.today_day_tomorrow
    }

private val PanelShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
