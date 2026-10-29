package com.opson.features.medication.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.opson.libraries.medication.DoseFormat
import com.opson.libraries.medication.InjectionSite
import com.opson.libraries.medication.MedicationForm
import com.opson.libraries.medication.MedicationProduct
import com.opson.libraries.medication.Severity
import com.opson.libraries.medication.Symptom
import com.opson.libraries.medication.inSentence
import com.opson.libraries.medication.label
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.chip.ChoiceChip
import com.opson.libraries.ui.components.dialog.TimePickerDialog
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.IconButton
import com.opson.libraries.ui.components.icon.ToneIcon
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.SingleLineField
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.color.ToneColors
import kotlinx.datetime.DayOfWeek
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.medication_care_line
import opson.libraries.resources.generated.resources.medication_current_dose
import opson.libraries.resources.generated.resources.medication_current_dose_body
import opson.libraries.resources.generated.resources.medication_disclosure
import opson.libraries.resources.generated.resources.medication_feeling_body
import opson.libraries.resources.generated.resources.medication_feeling_remove_cd
import opson.libraries.resources.generated.resources.medication_feeling_title
import opson.libraries.resources.generated.resources.medication_form
import opson.libraries.resources.generated.resources.medication_form_injection
import opson.libraries.resources.generated.resources.medication_form_pill
import opson.libraries.resources.generated.resources.medication_history
import opson.libraries.resources.generated.resources.medication_history_dose_site
import opson.libraries.resources.generated.resources.medication_history_empty
import opson.libraries.resources.generated.resources.medication_history_when
import opson.libraries.resources.generated.resources.medication_intro_body
import opson.libraries.resources.generated.resources.medication_intro_set_up
import opson.libraries.resources.generated.resources.medication_intro_title
import opson.libraries.resources.generated.resources.medication_log_dose
import opson.libraries.resources.generated.resources.medication_name
import opson.libraries.resources.generated.resources.medication_name_placeholder
import opson.libraries.resources.generated.resources.medication_reminder
import opson.libraries.resources.generated.resources.medication_reminder_body
import opson.libraries.resources.generated.resources.medication_reminder_denied
import opson.libraries.resources.generated.resources.medication_schedule
import opson.libraries.resources.generated.resources.medication_schedule_as_needed
import opson.libraries.resources.generated.resources.medication_schedule_daily
import opson.libraries.resources.generated.resources.medication_schedule_day
import opson.libraries.resources.generated.resources.medication_schedule_time
import opson.libraries.resources.generated.resources.medication_schedule_time_pick
import opson.libraries.resources.generated.resources.medication_schedule_weekly
import opson.libraries.resources.generated.resources.medication_sheet_not_a_dose
import opson.libraries.resources.generated.resources.medication_show_on_today
import opson.libraries.resources.generated.resources.medication_sites
import opson.libraries.resources.generated.resources.medication_sites_body
import opson.libraries.resources.generated.resources.medication_summary_off
import opson.libraries.resources.generated.resources.medication_time_cancel
import opson.libraries.resources.generated.resources.medication_time_ok
import opson.libraries.resources.generated.resources.medication_title
import opson.libraries.resources.generated.resources.medication_turn_off
import opson.libraries.resources.generated.resources.medication_turn_off_body
import opson.libraries.resources.generated.resources.medication_turn_on
import opson.libraries.resources.generated.resources.medication_unit
import opson.libraries.resources.generated.resources.medication_which
import opson.libraries.resources.generated.resources.settings_back_more
import opson.libraries.resources.generated.resources.settings_loading_cd
import org.jetbrains.compose.resources.stringResource

/**
 * More › Medication (spec, Screens 1). Top to bottom: what is tracked and Log a dose, how you feel
 * today, the history, then the medication, its schedule and reminder, the injection sites, Show
 * on Today, the disclosure and Stop tracking. Never a dose on More, and never advice.
 */
@Composable
fun MedicationScreen(
    state: MedicationState,
    onAction: (MedicationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    MedicationPageScaffold(modifier = modifier) {
        PageHeader(
            title = stringResource(Res.string.medication_title),
            backLabel = stringResource(Res.string.settings_back_more),
            onBack = { onAction(MedicationAction.BackTapped) },
        )
        when (val page = state.page) {
            null -> DelayedLoadingIndicator(contentDescription = stringResource(Res.string.settings_loading_cd))
            MedicationPage.Hidden -> Unit
            is MedicationPage.Intro -> Intro(page, onAction)
            is MedicationPage.Off -> Off(page, onAction)
            is MedicationPage.Tracking -> Tracking(page, onAction)
        }
    }
}

@Composable
private fun Intro(page: MedicationPage.Intro, onAction: (MedicationAction) -> Unit) {
    MedicationCardBox {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToneIcon(icon = AppIcons.Medication, tone = AppTheme.colors.nutrients.fiber)
            SectionTitle(stringResource(Res.string.medication_intro_title), Modifier.weight(1f))
        }
        Text(text = stringResource(Res.string.medication_intro_body), typography = AppTheme.type.body, color = AppTheme.colors.muted)
        if (!page.picking) {
            ButtonPrimary(onClick = { onAction(MedicationAction.SetUpTapped) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.medication_intro_set_up))
            }
        }
    }
    if (page.picking) {
        MedicationCardBox {
            SectionTitle(stringResource(Res.string.medication_which))
            ProductChips(selected = null, onAction = onAction)
        }
    }
    Disclosure()
}

@Composable
private fun Off(page: MedicationPage.Off, onAction: (MedicationAction) -> Unit) {
    MedicationCardBox {
        SectionTitle(page.name.text())
        Muted(stringResource(Res.string.medication_summary_off))
        ButtonPrimary(onClick = { onAction(MedicationAction.TurnOnTapped) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.medication_turn_on))
        }
    }
    History(page.history, onAction)
    Disclosure()
}

@Composable
private fun Tracking(page: MedicationPage.Tracking, onAction: (MedicationAction) -> Unit) {
    MedicationCardBox {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToneIcon(icon = AppIcons.Medication, tone = AppTheme.colors.nutrients.fiber)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionTitle(page.name.text())
                Muted(page.schedule.text())
            }
        }
        ButtonPrimary(onClick = { onAction(MedicationAction.LogDoseTapped) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.medication_log_dose))
        }
    }
    FeelingCard(page.feeling, onAction)
    History(page.history, onAction)
    SetupCard(page.setup, onAction)
    ScheduleCard(page.setup, onAction)
    page.sites?.let { SitesCard(it, onAction) }
    MedicationCardBox {
        SwitchRow(
            title = stringResource(Res.string.medication_show_on_today),
            body = null,
            checked = page.showOnToday,
            onToggle = { onAction(MedicationAction.ShowOnTodayToggled) },
        )
    }
    Disclosure()
    FlatButton(
        text = stringResource(Res.string.medication_turn_off),
        onClick = { onAction(MedicationAction.StopTapped) },
        tone = FlatButtonTone.Outlined,
        centered = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Muted(stringResource(Res.string.medication_turn_off_body), Modifier.padding(horizontal = 4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductChips(selected: MedicationProduct?, onAction: (MedicationAction) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        MedicationProduct.entries.forEach { product ->
            ChoiceChip(
                label = stringResource(product.label),
                selected = product == selected,
                onClick = { onAction(MedicationAction.ProductPicked(product)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupCard(setup: Setup, onAction: (MedicationAction) -> Unit) {
    MedicationCardBox(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
        SectionTitle(stringResource(Res.string.medication_which))
        ProductChips(selected = setup.product, onAction = onAction)
        if (setup.product.named) {
            Label(stringResource(Res.string.medication_name))
            SingleLineField(
                value = setup.name,
                onValueChange = { onAction(MedicationAction.NameTyped(it)) },
                placeholder = stringResource(Res.string.medication_name_placeholder),
                imeAction = ImeAction.Next,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (setup.formChoice) {
            Label(stringResource(Res.string.medication_form))
            SegmentedControl(
                options = MedicationForm.entries,
                selected = setup.form,
                onSelect = { onAction(MedicationAction.FormPicked(it)) },
                label = {
                    stringResource(
                        when (it) {
                            MedicationForm.Injection -> Res.string.medication_form_injection
                            MedicationForm.Pill -> Res.string.medication_form_pill
                        },
                    )
                },
            )
        }
        NumberField(
            value = setup.doseText,
            onValueChange = { onAction(MedicationAction.DoseTyped(it)) },
            label = stringResource(Res.string.medication_current_dose),
            unit = stringResource(setup.unit.label),
            allowDecimal = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (setup.doseInvalid) {
            Text(text = stringResource(Res.string.medication_sheet_not_a_dose), typography = AppTheme.type.secondary, color = AppTheme.colors.danger)
        } else {
            Muted(stringResource(Res.string.medication_current_dose_body))
        }
        if (setup.chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                setup.chips.forEach { dose ->
                    ChoiceChip(
                        label = doseText(dose, setup.unit),
                        selected = setup.doseText == DoseFormat.number(dose),
                        onClick = { onAction(MedicationAction.DoseChipPicked(dose)) },
                    )
                }
            }
        }
        if (setup.units.size > 1) {
            Label(stringResource(Res.string.medication_unit))
            SegmentedControl(
                options = setup.units,
                selected = setup.unit,
                onSelect = { onAction(MedicationAction.UnitPicked(it)) },
                label = { stringResource(it.label) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleCard(setup: Setup, onAction: (MedicationAction) -> Unit) {
    val schedule = setup.schedule
    MedicationCardBox(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
        SectionTitle(stringResource(Res.string.medication_schedule))
        SegmentedControl(
            options = ScheduleKind.entries,
            selected = schedule.kind,
            onSelect = { onAction(MedicationAction.ScheduleKindPicked(it)) },
            label = {
                stringResource(
                    when (it) {
                        ScheduleKind.Weekly -> Res.string.medication_schedule_weekly
                        ScheduleKind.Daily -> Res.string.medication_schedule_daily
                        ScheduleKind.AsNeeded -> Res.string.medication_schedule_as_needed
                    },
                )
            },
        )
        if (schedule.kind == ScheduleKind.Weekly) {
            Label(stringResource(Res.string.medication_schedule_day))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                DayOfWeek.entries.forEach { day ->
                    ChoiceChip(
                        label = DateCopy.shortWeekday(day),
                        selected = day == schedule.day,
                        onClick = { onAction(MedicationAction.DayPicked(day)) },
                    )
                }
            }
        }
        schedule.time?.let { time ->
            ListRow(
                title = stringResource(Res.string.medication_schedule_time),
                icon = AppIcons.Schedule,
                iconTone = ToneColors(AppTheme.colors.ink, AppTheme.colors.soft),
                trailingText = time.clock(),
                onClick = { onAction(MedicationAction.TimeTapped) },
            )
            if (setup.pickingTime) {
                TimePickerDialog(
                    title = stringResource(Res.string.medication_schedule_time_pick),
                    initial = time,
                    okLabel = stringResource(Res.string.medication_time_ok),
                    cancelLabel = stringResource(Res.string.medication_time_cancel),
                    onPicked = { onAction(MedicationAction.TimePicked(it)) },
                    onDismiss = { onAction(MedicationAction.TimeDismissed) },
                )
            }
        }
        setup.reminder?.let { reminder ->
            HorizontalDivider(color = AppTheme.colors.line)
            SwitchRow(
                title = stringResource(Res.string.medication_reminder),
                body = stringResource(Res.string.medication_reminder_body),
                checked = reminder.on,
                onToggle = { onAction(MedicationAction.ReminderToggled) },
            )
            if (reminder.denied) {
                FlatButton(
                    text = stringResource(Res.string.medication_reminder_denied),
                    onClick = { onAction(MedicationAction.OpenSettingsTapped) },
                    icon = AppIcons.Notifications,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SitesCard(sites: Set<InjectionSite>, onAction: (MedicationAction) -> Unit) {
    MedicationCardBox(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
        SectionTitle(stringResource(Res.string.medication_sites))
        Muted(stringResource(Res.string.medication_sites_body))
        InjectionSiteMap(
            marks = InjectionSite.entries.associateWith { if (it in sites) SiteMark.On else SiteMark.Off },
            onTap = { onAction(MedicationAction.SiteToggled(it)) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeelingCard(feeling: Feeling, onAction: (MedicationAction) -> Unit) {
    MedicationCardBox(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
        SectionTitle(stringResource(Res.string.medication_feeling_title))
        Muted(stringResource(Res.string.medication_feeling_body))
        feeling.symptoms.entries.sortedBy { it.key.ordinal }.forEach { (symptom, severity) ->
            SymptomRow(symptom, severity, onAction)
        }
        val untouched = Symptom.entries.filter { it !in feeling.symptoms }
        if (untouched.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                untouched.forEach { symptom ->
                    ChoiceChip(
                        label = stringResource(symptom.label),
                        selected = false,
                        role = Role.Checkbox,
                        onClick = { onAction(MedicationAction.SymptomTapped(symptom)) },
                    )
                }
            }
        }
        if (feeling.needCare) {
            Text(text = stringResource(Res.string.medication_care_line), typography = AppTheme.type.body, color = AppTheme.colors.ink)
        }
    }
}

@Composable
private fun SymptomRow(symptom: Symptom, severity: Severity, onAction: (MedicationAction) -> Unit) {
    val name = stringResource(symptom.label)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                typography = AppTheme.type.bodyBold,
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                icon = AppIcons.Close.described(stringResource(Res.string.medication_feeling_remove_cd, name)),
                onClick = { onAction(MedicationAction.SymptomRemoved(symptom)) },
            )
        }
        SegmentedControl(
            options = Severity.entries,
            selected = severity,
            onSelect = { onAction(MedicationAction.SeverityPicked(symptom, it)) },
            label = { stringResource(it.label) },
        )
    }
}

@Composable
private fun History(history: List<DoseRow>, onAction: (MedicationAction) -> Unit) {
    MedicationCardBox(verticalArrangement = Arrangement.Top) {
        SectionTitle(stringResource(Res.string.medication_history), Modifier.padding(bottom = AppTheme.spacing.row))
        if (history.isEmpty()) Muted(stringResource(Res.string.medication_history_empty))
        history.forEachIndexed { index, row ->
            val amount = doseText(row.dose, row.unit)
            ListRow(
                title = row.site?.let { stringResource(Res.string.medication_history_dose_site, amount, stringResource(it.inSentence)) } ?: amount,
                subtitle = stringResource(Res.string.medication_history_when, row.day.short(), row.time.clock()),
                onClick = { onAction(MedicationAction.DoseTapped(row.id)) },
                showDivider = index < history.lastIndex,
            )
        }
    }
}

@Composable
private fun Disclosure() {
    Muted(stringResource(Res.string.medication_disclosure), Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun SwitchRow(title: String, body: String?, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
            if (body != null) Muted(body)
        }
        AppSwitch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun Label(text: String) {
    Text(text = text, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
}
