package com.opson.features.foods.impl

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.RepeatDays
import com.opson.libraries.diary.ui.label
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.chip.ChoiceChip
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.background
import kotlinx.datetime.DayOfWeek
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.repeat_add_food
import opson.libraries.resources.generated.resources.repeat_days
import opson.libraries.resources.generated.resources.repeat_days_every
import opson.libraries.resources.generated.resources.repeat_days_weekdays
import opson.libraries.resources.generated.resources.repeat_days_weekends
import opson.libraries.resources.generated.resources.repeat_delete
import opson.libraries.resources.generated.resources.repeat_end_month
import opson.libraries.resources.generated.resources.repeat_end_never
import opson.libraries.resources.generated.resources.repeat_end_on
import opson.libraries.resources.generated.resources.repeat_end_two_weeks
import opson.libraries.resources.generated.resources.repeat_end_week
import opson.libraries.resources.generated.resources.repeat_ends
import opson.libraries.resources.generated.resources.repeat_how
import opson.libraries.resources.generated.resources.repeat_meal
import opson.libraries.resources.generated.resources.repeat_note_counts_today
import opson.libraries.resources.generated.resources.repeat_note_done_today
import opson.libraries.resources.generated.resources.repeat_note_logged_today
import opson.libraries.resources.generated.resources.repeat_note_logs_today
import opson.libraries.resources.generated.resources.repeat_note_never
import opson.libraries.resources.generated.resources.repeat_note_next_on
import opson.libraries.resources.generated.resources.repeat_note_paused
import opson.libraries.resources.generated.resources.repeat_note_tomorrow
import opson.libraries.resources.generated.resources.repeat_pause
import opson.libraries.resources.generated.resources.repeat_pause_body
import opson.libraries.resources.generated.resources.repeat_remove_cd
import opson.libraries.resources.generated.resources.repeat_save
import opson.libraries.resources.generated.resources.repeat_saved_meals
import opson.libraries.resources.generated.resources.repeat_start_today
import opson.libraries.resources.generated.resources.repeat_start_tomorrow
import opson.libraries.resources.generated.resources.repeat_starts
import opson.libraries.resources.generated.resources.repeat_title_edit
import opson.libraries.resources.generated.resources.repeat_title_new
import opson.libraries.resources.generated.resources.repeat_what
import opson.libraries.resources.generated.resources.repeat_what_empty
import org.jetbrains.compose.resources.stringResource

/**
 * A repeat (`foods.md`, Repeating): what it logs, the meal and the days, when it starts and
 * ends, then what saving does. A saved one also has Pause and Delete. Save is pinned to the
 * bottom and off until there is a food and a day. Stateless.
 */
@Composable
fun RepeatScreen(
    state: RepeatState,
    onAction: (RepeatAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    FormPage(
        title = stringResource(if (state.isNew) Res.string.repeat_title_new else Res.string.repeat_title_edit),
        onClose = { onAction(RepeatAction.CloseTapped) },
        saveLabel = stringResource(Res.string.repeat_save),
        canSave = state.canSave,
        onSave = { onAction(RepeatAction.SaveTapped) },
        modifier = modifier,
    ) {
        if (state.loading) return@FormPage
        WhatCard(state, onAction)
        if (state.savedMeals.isNotEmpty()) SavedMealsCard(state, onAction)
        WhenCard(state, onAction)
        RunCard(state, onAction)
        if (!state.isNew) PauseCard(state, onAction)
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = noteText(state), typography = AppTheme.type.body, color = AppTheme.colors.ink)
            Text(text = stringResource(Res.string.repeat_how), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        if (!state.isNew) {
            FlatButton(
                text = stringResource(Res.string.repeat_delete),
                onClick = { onAction(RepeatAction.DeleteTapped) },
                tone = FlatButtonTone.Outlined,
                centered = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WhatCard(state: RepeatState, onAction: (RepeatAction) -> Unit) {
    FoodsCard(label = state.mealName ?: stringResource(Res.string.repeat_what)) {
        if (state.items.isEmpty()) FoodsCardNote(stringResource(Res.string.repeat_what_empty))
        state.items.forEachIndexed { index, item ->
            FoodsRow(
                name = item.name,
                sub = item.amount,
                calories = item.calories,
                buttonIcon = AppIcons.Close,
                buttonDescription = stringResource(Res.string.repeat_remove_cd, item.name),
                onButton = { onAction(RepeatAction.ItemRemoveTapped(index)) },
                buttonContainer = AppTheme.colors.bg,
                buttonContent = AppTheme.colors.ink,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .semantics { role = Role.Button }
                .bounceClick(scaleDown = 0.98f, onClick = { onAction(RepeatAction.AddFoodTapped) }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Icon(icon = AppIcons.Add, size = 24.dp, color = AppTheme.colors.accent)
            Text(text = stringResource(Res.string.repeat_add_food), typography = AppTheme.type.cardTitle, color = AppTheme.colors.accent)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedMealsCard(state: RepeatState, onAction: (RepeatAction) -> Unit) {
    FormCard {
        Column {
            FieldLabel(stringResource(Res.string.repeat_saved_meals))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.savedMeals.forEach { meal ->
                    ChoiceChip(
                        label = meal.name,
                        selected = meal.selected,
                        onClick = { onAction(RepeatAction.SavedMealTapped(meal.id)) },
                        unselectedColor = AppTheme.colors.surface,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhenCard(state: RepeatState, onAction: (RepeatAction) -> Unit) {
    FormCard {
        Column {
            FieldLabel(stringResource(Res.string.repeat_meal))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.slots.forEach { slot ->
                    ChoiceChip(
                        label = stringResource(slot.label),
                        selected = slot == state.slot,
                        onClick = { onAction(RepeatAction.SlotPicked(slot)) },
                        unselectedColor = AppTheme.colors.surface,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FieldLabel(stringResource(Res.string.repeat_days))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DaysPresets.forEach { (days, label) ->
                    ChoiceChip(
                        label = stringResource(label),
                        selected = state.days == days,
                        onClick = { onAction(RepeatAction.DaysPicked(days)) },
                        unselectedColor = AppTheme.colors.surface,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DayOfWeek.entries.forEach { day ->
                    DayToggle(day = day, on = day in state.days, onToggle = { onAction(RepeatAction.DayToggled(day)) })
                }
            }
        }
    }
}

/** One day of the week, its initial in a circle: filled when the repeat logs on it. */
@Composable
private fun DayToggle(day: DayOfWeek, on: Boolean, onToggle: () -> Unit) {
    val colors = AppTheme.colors
    val name = DateCopy.longWeekday(day)
    Box(
        modifier = Modifier
            .size(DayToggleSize)
            .clip(CircleShape)
            .background(if (on) colors.accent else colors.surface)
            .border(1.dp, if (on) colors.accent.color else colors.line.color, CircleShape)
            .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = name.take(1), typography = AppTheme.type.bodyBold, color = if (on) colors.bg else colors.ink)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RunCard(state: RepeatState, onAction: (RepeatAction) -> Unit) {
    FormCard {
        if (state.starts.isNotEmpty()) {
            Column {
                FieldLabel(stringResource(Res.string.repeat_starts))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.starts.forEach { start ->
                        ChoiceChip(
                            label = stringResource(if (start == RepeatStart.Today) Res.string.repeat_start_today else Res.string.repeat_start_tomorrow),
                            selected = start == state.start,
                            onClick = { onAction(RepeatAction.StartPicked(start)) },
                            unselectedColor = AppTheme.colors.surface,
                        )
                    }
                }
            }
        }
        Column {
            FieldLabel(stringResource(Res.string.repeat_ends))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.ends.forEach { end ->
                    ChoiceChip(
                        label = endLabel(end),
                        selected = end == state.end,
                        onClick = { onAction(RepeatAction.EndPicked(end)) },
                        unselectedColor = AppTheme.colors.surface,
                    )
                }
            }
        }
    }
}

@Composable
private fun PauseCard(state: RepeatState, onAction: (RepeatAction) -> Unit) {
    FormCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = stringResource(Res.string.repeat_pause), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
                Text(text = stringResource(Res.string.repeat_pause_body), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            }
            AppSwitch(checked = state.paused, onCheckedChange = { onAction(RepeatAction.PauseToggled) })
        }
    }
}

@Composable
private fun endLabel(end: RepeatEnd): String = when (end) {
    RepeatEnd.Never -> stringResource(Res.string.repeat_end_never)
    RepeatEnd.Week -> stringResource(Res.string.repeat_end_week)
    RepeatEnd.TwoWeeks -> stringResource(Res.string.repeat_end_two_weeks)
    RepeatEnd.Month -> stringResource(Res.string.repeat_end_month)
    is RepeatEnd.On -> stringResource(Res.string.repeat_end_on, DateCopy.monthDay(end.day))
}

@Composable
private fun noteText(state: RepeatState): String = when (val note = state.note) {
    RepeatNote.LogsToday -> stringResource(Res.string.repeat_note_logs_today)
    RepeatNote.CountsToday -> stringResource(Res.string.repeat_note_counts_today)
    RepeatNote.LoggedToday -> stringResource(Res.string.repeat_note_logged_today)
    RepeatNote.DoneToday -> stringResource(Res.string.repeat_note_done_today)
    RepeatNote.Tomorrow -> stringResource(Res.string.repeat_note_tomorrow)
    is RepeatNote.NextOn -> stringResource(Res.string.repeat_note_next_on, DateCopy.weekdayMonthDay(note.day))
    RepeatNote.Never -> stringResource(Res.string.repeat_note_never)
    RepeatNote.Paused -> stringResource(Res.string.repeat_note_paused)
}

/** The schedule as the Foods tab says it: "Every day", "Weekdays", or "Mon, Wed, Fri". */
@Composable
internal fun daysLabel(days: Set<DayOfWeek>): String = when (days) {
    RepeatDays.EveryDay -> stringResource(Res.string.repeat_days_every)
    RepeatDays.Weekdays -> stringResource(Res.string.repeat_days_weekdays)
    RepeatDays.Weekends -> stringResource(Res.string.repeat_days_weekends)
    else -> days.sorted().joinToString(", ") { DateCopy.shortWeekday(it) }
}

private val DaysPresets = listOf(
    RepeatDays.EveryDay to Res.string.repeat_days_every,
    RepeatDays.Weekdays to Res.string.repeat_days_weekdays,
    RepeatDays.Weekends to Res.string.repeat_days_weekends,
)

private val DayToggleSize = 44.dp
