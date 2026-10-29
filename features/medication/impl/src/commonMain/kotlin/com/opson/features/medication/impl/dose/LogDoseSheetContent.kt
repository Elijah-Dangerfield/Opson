package com.opson.features.medication.impl.dose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.opson.features.medication.impl.InjectionSiteMap
import com.opson.features.medication.impl.Muted
import com.opson.features.medication.impl.SiteMark
import com.opson.features.medication.impl.clock
import com.opson.features.medication.impl.doseText
import com.opson.features.medication.impl.short
import com.opson.features.medication.impl.text
import com.opson.libraries.medication.DoseFormat
import com.opson.libraries.medication.label
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.chip.ChoiceChip
import com.opson.libraries.ui.components.dialog.TimePickerDialog
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.SingleLineField
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.color.ToneColors
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.medication_schedule_time_pick
import opson.libraries.resources.generated.resources.medication_sheet_delete
import opson.libraries.resources.generated.resources.medication_sheet_dose
import opson.libraries.resources.generated.resources.medication_sheet_edit
import opson.libraries.resources.generated.resources.medication_sheet_failed
import opson.libraries.resources.generated.resources.medication_sheet_log
import opson.libraries.resources.generated.resources.medication_sheet_make_current
import opson.libraries.resources.generated.resources.medication_sheet_make_current_once
import opson.libraries.resources.generated.resources.medication_sheet_make_current_yes
import opson.libraries.resources.generated.resources.medication_sheet_not_a_dose
import opson.libraries.resources.generated.resources.medication_sheet_note
import opson.libraries.resources.generated.resources.medication_sheet_note_placeholder
import opson.libraries.resources.generated.resources.medication_sheet_save
import opson.libraries.resources.generated.resources.medication_sheet_site
import opson.libraries.resources.generated.resources.medication_sheet_site_suggested
import opson.libraries.resources.generated.resources.medication_sheet_today
import opson.libraries.resources.generated.resources.medication_sheet_when
import opson.libraries.resources.generated.resources.medication_sheet_yesterday
import opson.libraries.resources.generated.resources.medication_time_cancel
import opson.libraries.resources.generated.resources.medication_time_ok
import org.jetbrains.compose.resources.stringResource

/**
 * The dose sheet's body (spec, Screens 2): the dose, when, the site and a note, then Save. The
 * entry point puts it in a `BottomSheet` with `scrollableContent`, so it does not scroll itself.
 */
@Composable
fun LogDoseSheetContent(
    state: LogDoseState,
    onAction: (LogDoseAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        if (state.loading) return@Column
        when (val step = state.step) {
            DoseStep.Form -> Form(state, onAction)
            is DoseStep.AskCurrent -> AskCurrent(step, onAction)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Form(state: LogDoseState, onAction: (LogDoseAction) -> Unit) {
    SheetHeader(
        title = stringResource(if (state.editing) Res.string.medication_sheet_edit else Res.string.medication_sheet_log),
        subtitle = state.name?.text(),
        onClose = { onAction(LogDoseAction.CloseTapped) },
    )
    NumberField(
        value = state.doseText,
        onValueChange = { onAction(LogDoseAction.DoseTyped(it)) },
        label = stringResource(Res.string.medication_sheet_dose),
        unit = stringResource(state.unit.label),
        allowDecimal = true,
        imeAction = ImeAction.Next,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.notADose) {
        Text(text = stringResource(Res.string.medication_sheet_not_a_dose), typography = AppTheme.type.secondary, color = AppTheme.colors.danger)
    }
    if (state.chips.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            state.chips.forEach { dose ->
                ChoiceChip(
                    label = doseText(dose, state.unit),
                    selected = state.doseText == DoseFormat.number(dose),
                    onClick = { onAction(LogDoseAction.ChipPicked(dose)) },
                )
            }
        }
    }
    When(state, onAction)
    state.sites?.let { sites ->
        Label(stringResource(Res.string.medication_sheet_site))
        val site = state.site
        if (site != null) {
            val name = stringResource(site.label)
            Text(
                text = if (site == state.suggested) stringResource(Res.string.medication_sheet_site_suggested, name) else name,
                typography = AppTheme.type.bodyBold,
                color = AppTheme.colors.ink,
            )
        }
        InjectionSiteMap(
            marks = sites.associateWith { if (it == state.site) SiteMark.Picked else SiteMark.Available },
            onTap = { onAction(LogDoseAction.SiteTapped(it)) },
            maxWidth = SHEET_MAP_WIDTH,
        )
    }
    Label(stringResource(Res.string.medication_sheet_note))
    SingleLineField(
        value = state.note,
        onValueChange = { onAction(LogDoseAction.NoteTyped(it)) },
        placeholder = stringResource(Res.string.medication_sheet_note_placeholder),
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.failed) Muted(stringResource(Res.string.medication_sheet_failed))
    ButtonPrimary(onClick = { onAction(LogDoseAction.SaveTapped) }, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.medication_sheet_save))
    }
    if (state.editing) {
        FlatButton(
            text = stringResource(Res.string.medication_sheet_delete),
            onClick = { onAction(LogDoseAction.DeleteTapped) },
            tone = FlatButtonTone.Outlined,
            icon = AppIcons.DeleteOutline,
            centered = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun When(state: LogDoseState, onAction: (LogDoseAction) -> Unit) {
    Label(stringResource(Res.string.medication_sheet_when))
    val choices = state.dayChoices
    if (choices.size > 1) {
        SegmentedControl(
            options = choices,
            selected = state.day,
            onSelect = { onAction(LogDoseAction.DayPicked(it)) },
            label = { it.label() },
        )
    }
    ListRow(
        title = state.day.label(),
        icon = AppIcons.Schedule,
        iconTone = ToneColors(AppTheme.colors.ink, AppTheme.colors.soft),
        trailingText = state.time.clock(),
        onClick = { onAction(LogDoseAction.TimeTapped) },
    )
    if (state.pickingTime) {
        TimePickerDialog(
            title = stringResource(Res.string.medication_schedule_time_pick),
            initial = state.time,
            okLabel = stringResource(Res.string.medication_time_ok),
            cancelLabel = stringResource(Res.string.medication_time_cancel),
            onPicked = { onAction(LogDoseAction.TimePicked(it)) },
            onDismiss = { onAction(LogDoseAction.TimeDismissed) },
        )
    }
}

@Composable
private fun AskCurrent(step: DoseStep.AskCurrent, onAction: (LogDoseAction) -> Unit) {
    SheetHeader(
        title = stringResource(Res.string.medication_sheet_make_current, doseText(step.dose, step.unit)),
        onClose = { onAction(LogDoseAction.JustThisOnceTapped) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        FlatButton(
            text = stringResource(Res.string.medication_sheet_make_current_once),
            onClick = { onAction(LogDoseAction.JustThisOnceTapped) },
            tone = FlatButtonTone.Outlined,
            centered = true,
            modifier = Modifier.weight(1f),
        )
        ButtonPrimary(onClick = { onAction(LogDoseAction.MakeCurrentTapped) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(Res.string.medication_sheet_make_current_yes))
        }
    }
}

@Composable
private fun DoseDay.label(): String = when (this) {
    DoseDay.Today -> stringResource(Res.string.medication_sheet_today)
    DoseDay.Yesterday -> stringResource(Res.string.medication_sheet_yesterday)
    is DoseDay.Other -> date.short()
}

@Composable
private fun Label(text: String) {
    Text(text = text, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
}

private val SHEET_MAP_WIDTH = 240.dp
