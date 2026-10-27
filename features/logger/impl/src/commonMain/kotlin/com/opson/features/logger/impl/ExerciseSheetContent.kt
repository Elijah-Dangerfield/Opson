package com.opson.features.logger.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.opson.libraries.diary.ui.exerciseActivityLabel
import com.opson.libraries.nutrition.ExerciseActivities
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.ListRowChevron
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.chip.ChoiceChip
import com.opson.libraries.ui.components.dialog.TimePickerDialog
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconSize
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.SearchField
import com.opson.libraries.ui.components.text.SingleLineField
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.color.ToneColors
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.exercise_activity
import opson.libraries.resources.generated.resources.exercise_activity_pick
import opson.libraries.resources.generated.resources.exercise_delete
import opson.libraries.resources.generated.resources.exercise_duration
import opson.libraries.resources.generated.resources.exercise_estimate_label
import opson.libraries.resources.generated.resources.exercise_estimate_no_weight
import opson.libraries.resources.generated.resources.exercise_kcal_unit
import opson.libraries.resources.generated.resources.exercise_minutes_chip
import opson.libraries.resources.generated.resources.exercise_minutes_unit
import opson.libraries.resources.generated.resources.exercise_not_on_watch
import opson.libraries.resources.generated.resources.exercise_not_on_watch_detail
import opson.libraries.resources.generated.resources.exercise_other_name
import opson.libraries.resources.generated.resources.exercise_other_name_placeholder
import opson.libraries.resources.generated.resources.exercise_save
import opson.libraries.resources.generated.resources.exercise_search_clear_cd
import opson.libraries.resources.generated.resources.exercise_search_none
import opson.libraries.resources.generated.resources.exercise_search_placeholder
import opson.libraries.resources.generated.resources.exercise_time
import opson.libraries.resources.generated.resources.exercise_time_cancel
import opson.libraries.resources.generated.resources.exercise_time_ok
import opson.libraries.resources.generated.resources.exercise_time_pick
import opson.libraries.resources.generated.resources.exercise_title_add
import opson.libraries.resources.generated.resources.exercise_title_edit
import opson.libraries.resources.generated.resources.exercise_typed_label
import opson.libraries.resources.generated.resources.exercise_typed_label_needed
import opson.libraries.resources.generated.resources.exercise_typed_placeholder
import org.jetbrains.compose.resources.stringResource

/**
 * The exercise sheet's body: the form, or the activity list it opens in place. Stateless apart
 * from whether the time dialog is open; the entry point puts it in a `BottomSheet` with
 * `scrollableContent`, so neither pane scrolls on its own.
 */
@Composable
fun ExerciseSheetContent(
    state: ExerciseSheetState,
    onAction: (ExerciseSheetAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        if (state.loading) return@Column
        when (state.pane) {
            ExercisePane.Form -> ExerciseForm(state, onAction)
            ExercisePane.Pick -> ActivityPicker(state, onAction)
        }
    }
}

@Composable
private fun ExerciseForm(state: ExerciseSheetState, onAction: (ExerciseSheetAction) -> Unit) {
    SheetHeader(
        title = stringResource(if (state.editing) Res.string.exercise_title_edit else Res.string.exercise_title_add),
        onClose = { onAction(ExerciseSheetAction.CloseTapped) },
    )
    val activity = state.activity?.let { exerciseActivityLabel(it) }?.let { stringResource(it) }
        ?: stringResource(Res.string.exercise_activity_pick)
    ListRow(
        title = activity,
        subtitle = stringResource(Res.string.exercise_activity),
        icon = AppIcons.FitnessCenter,
        iconTone = ToneColors(AppTheme.colors.accent, AppTheme.colors.accentTint),
        onClick = { onAction(ExerciseSheetAction.ActivityTapped) },
    ) {
        ListRowChevron()
    }
    state.name?.let { name ->
        Label(stringResource(Res.string.exercise_other_name))
        SingleLineField(
            value = name,
            onValueChange = { onAction(ExerciseSheetAction.NameTyped(it)) },
            placeholder = stringResource(Res.string.exercise_other_name_placeholder),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Duration(state, onAction)
    StartTime(state, onAction)
    Burned(state)
    if (state.canNumber) {
        NumberField(
            value = state.typed,
            onValueChange = { onAction(ExerciseSheetAction.NumberTyped(it)) },
            label = stringResource(if (state.numberNeeded) Res.string.exercise_typed_label_needed else Res.string.exercise_typed_label),
            unit = stringResource(Res.string.exercise_kcal_unit),
            placeholder = stringResource(Res.string.exercise_typed_placeholder),
            allowDecimal = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    state.notOnWatch?.let { on ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(Res.string.exercise_not_on_watch), typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
                Text(text = stringResource(Res.string.exercise_not_on_watch_detail), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            }
            AppSwitch(checked = on, onCheckedChange = { onAction(ExerciseSheetAction.NotOnWatchToggled(it)) })
        }
    }
    ButtonPrimary(
        onClick = { onAction(ExerciseSheetAction.SaveTapped) },
        enabled = state.canSave,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(Res.string.exercise_save))
    }
    if (state.editing) {
        FlatButton(
            text = stringResource(Res.string.exercise_delete),
            onClick = { onAction(ExerciseSheetAction.DeleteTapped) },
            tone = FlatButtonTone.Outlined,
            icon = AppIcons.DeleteOutline,
            centered = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Duration(state: ExerciseSheetState, onAction: (ExerciseSheetAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        NumberField(
            value = state.minutes,
            onValueChange = { onAction(ExerciseSheetAction.MinutesTyped(it)) },
            label = stringResource(Res.string.exercise_duration),
            unit = stringResource(Res.string.exercise_minutes_unit),
            allowDecimal = false,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            ExerciseSheetStateBuilder.QUICK_MINUTES.forEach { minutes ->
                ChoiceChip(
                    label = stringResource(Res.string.exercise_minutes_chip, minutes),
                    selected = state.minutes == minutes.toString(),
                    onClick = { onAction(ExerciseSheetAction.MinutesPicked(minutes)) },
                )
            }
        }
    }
}

@Composable
private fun StartTime(state: ExerciseSheetState, onAction: (ExerciseSheetAction) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    ListRow(
        title = stringResource(Res.string.exercise_time),
        icon = AppIcons.Schedule,
        iconTone = ToneColors(AppTheme.colors.ink, AppTheme.colors.soft),
        trailingText = DateCopy.time(state.startedAt.hour, state.startedAt.minute),
        onClick = { picking = true },
    )
    if (picking) {
        TimePickerDialog(
            title = stringResource(Res.string.exercise_time_pick),
            initial = state.startedAt,
            okLabel = stringResource(Res.string.exercise_time_ok),
            cancelLabel = stringResource(Res.string.exercise_time_cancel),
            onPicked = {
                picking = false
                onAction(ExerciseSheetAction.TimePicked(it))
            },
            onDismiss = { picking = false },
        )
    }
}

/** "Burned 133 kcal", or why there is no estimate. A hidden estimate takes its label with it. */
@Composable
private fun Burned(state: ExerciseSheetState) {
    NutrientVisible(state.estimate) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.exercise_estimate_label),
                typography = AppTheme.type.bodyBold,
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            NutrientText(Metric.EnergyOut, state.estimate, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
        }
    }
    if (state.noWeight) {
        Text(text = stringResource(Res.string.exercise_estimate_no_weight), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
    }
}

@Composable
private fun ActivityPicker(state: ExerciseSheetState, onAction: (ExerciseSheetAction) -> Unit) {
    SheetHeader(
        title = stringResource(Res.string.exercise_activity_pick),
        onBack = { onAction(ExerciseSheetAction.PickBackTapped) },
        onClose = { onAction(ExerciseSheetAction.CloseTapped) },
    )
    SearchField(
        value = state.query,
        onValueChange = { onAction(ExerciseSheetAction.QueryTyped(it)) },
        placeholder = stringResource(Res.string.exercise_search_placeholder),
        onClear = { onAction(ExerciseSheetAction.QueryTyped("")) },
        clearDescription = stringResource(Res.string.exercise_search_clear_cd),
        modifier = Modifier.fillMaxWidth(),
    )
    val labels = ExerciseActivities.all.map { it.key to stringResource(checkNotNull(exerciseActivityLabel(it.key))) }
    val query = state.query.trim()
    val matches = remember(labels, query) {
        if (query.isEmpty()) labels else labels.filter { (_, label) -> label.contains(query, ignoreCase = true) }
    }
    Column {
        if (matches.isEmpty()) {
            Text(text = stringResource(Res.string.exercise_search_none), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        matches.forEach { (key, label) ->
            ActivityRow(label, selected = key == state.activity) { onAction(ExerciseSheetAction.ActivityPicked(key)) }
        }
        val other = stringResource(checkNotNull(exerciseActivityLabel(ExerciseActivities.OTHER)))
        ActivityRow(other, selected = state.activity == ExerciseActivities.OTHER, divider = false) {
            onAction(ExerciseSheetAction.ActivityPicked(ExerciseActivities.OTHER))
        }
    }
}

@Composable
private fun ActivityRow(label: String, selected: Boolean, divider: Boolean = true, onClick: () -> Unit) {
    ListRow(title = label, onClick = onClick, showDivider = divider, minHeight = AppTheme.spacing.touchTarget) {
        if (selected) Icon(icon = AppIcons.Check, size = IconSize.Medium, color = AppTheme.colors.accent)
    }
}

@Composable
private fun Label(text: String) {
    Text(text = text, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
}
