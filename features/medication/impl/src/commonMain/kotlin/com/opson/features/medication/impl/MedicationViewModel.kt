package com.opson.features.medication.impl

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.medication.DoseDraft
import com.opson.libraries.medication.DoseFormat
import com.opson.libraries.medication.DoseSchedule
import com.opson.libraries.medication.DoseUnit
import com.opson.libraries.medication.InjectionSite
import com.opson.libraries.medication.Medication
import com.opson.libraries.medication.MedicationEnabled
import com.opson.libraries.medication.MedicationForm
import com.opson.libraries.medication.MedicationProduct
import com.opson.libraries.medication.MedicationRepository
import com.opson.libraries.medication.Severity
import com.opson.libraries.medication.Symptom
import com.opson.libraries.medication.SymptomLog
import com.opson.libraries.reminders.NotificationPermission
import com.opson.libraries.reminders.NotificationPermissions
import com.opson.libraries.reminders.RemindersEnabled
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import me.tatarka.inject.annotations.Inject
import kotlin.uuid.Uuid

/**
 * More › Medication. Every change is written as it is made, as the other settings pages do; the
 * reminder planner and the Today card pick the plan up from the repository.
 *
 * Notification permission is asked at the moment someone turns the dose reminder on, and only
 * then. A denial keeps the choice and says how to fix it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Inject
class MedicationViewModel(
    private val repository: MedicationRepository,
    private val symptoms: SymptomLog,
    private val calendar: LocalCalendar,
    private val permissions: NotificationPermissions,
    enabled: MedicationEnabled,
    remindersEnabled: RemindersEnabled,
) : SEAViewModel<MedicationState, MedicationEvent, MedicationAction>(initialStateArg = MedicationState()) {

    /** Read once when the page opens, as the other settings pages read their flags. */
    private val released = enabled.value
    private val remindersReleased = remindersEnabled.value

    private var inputs: MedicationInputs? = null
    private var ui = MedicationUi()
    private var permission = NotificationPermission.Granted

    init {
        viewModelScope.launch {
            val today = calendar.today()
            val todaySymptoms = if (released) symptoms.observeDay(today) else flowOf(emptyMap())
            combine(repository.plan, if (released) repository.observeDoses() else flowOf(emptyList()), todaySymptoms) { plan, doses, felt ->
                MedicationAction.InputsArrived(
                    MedicationInputs(
                        released = released,
                        plan = plan,
                        doses = doses,
                        symptoms = felt,
                        zone = calendar.timeZone(),
                        remindersReleased = remindersReleased,
                    ),
                )
            }.collect(::takeAction)
        }
        viewModelScope.launch { takeAction(MedicationAction.PermissionChecked(permissions.status())) }
    }

    @Suppress("CyclomaticComplexMethod")
    override suspend fun handleAction(action: MedicationAction) {
        when (action) {
            is MedicationAction.InputsArrived -> {
                inputs = action.inputs
                action.rebuild()
            }
            is MedicationAction.PermissionChecked -> {
                permission = action.permission
                action.rebuild()
            }
            MedicationAction.SetUpTapped -> {
                ui = ui.copy(settingUp = true)
                action.rebuild()
            }
            is MedicationAction.ProductPicked -> productPicked(action.product)
            is MedicationAction.NameTyped -> {
                ui = ui.copy(nameText = action.text)
                action.rebuild()
                val name = action.text.trim().take(MAX_NAME)
                editMedication { it.copy(brand = name.ifEmpty { null }) }
            }
            is MedicationAction.FormPicked -> editMedication { if (it.product.eitherForm) it.copy(form = action.form) else it }
            is MedicationAction.DoseTyped -> doseTyped(action)
            is MedicationAction.DoseChipPicked -> {
                ui = ui.copy(doseText = DoseFormat.number(action.dose))
                editMedication { it.copy(currentDose = action.dose) }
            }
            is MedicationAction.UnitPicked -> editMedication { if (action.unit in it.product.units) it.copy(doseUnit = action.unit) else it }
            is MedicationAction.ScheduleKindPicked -> editMedication { it.copy(schedule = schedule(action.kind, it.schedule)) }
            is MedicationAction.DayPicked -> editMedication {
                val time = it.schedule.reminderTime ?: DoseSchedule.DEFAULT_TIME
                if (it.schedule is DoseSchedule.Weekly) it.copy(schedule = DoseSchedule.Weekly(action.day, time)) else it
            }
            MedicationAction.TimeTapped -> {
                ui = ui.copy(pickingTime = true)
                action.rebuild()
            }
            MedicationAction.TimeDismissed -> {
                ui = ui.copy(pickingTime = false)
                action.rebuild()
            }
            is MedicationAction.TimePicked -> {
                ui = ui.copy(pickingTime = false)
                editMedication { it.copy(schedule = it.schedule.at(action.time)) }
            }
            MedicationAction.ReminderToggled -> reminderToggled()
            MedicationAction.OpenSettingsTapped -> permissions.openSettings()
            MedicationAction.ScreenResumed -> takeAction(MedicationAction.PermissionChecked(permissions.status()))
            is MedicationAction.SiteToggled -> repository.updatePlan { plan ->
                val next = if (action.site in plan.sites) plan.sites - action.site else plan.sites + action.site
                // At least one site stays on: a suggestion needs somewhere to suggest.
                if (next.isEmpty()) plan else plan.copy(sites = next)
            }
            MedicationAction.ShowOnTodayToggled -> repository.updatePlan { it.copy(todayCard = !it.todayCard) }
            is MedicationAction.SymptomTapped -> symptomsChanged { it + (action.symptom to (it[action.symptom] ?: Severity.Mild)) }
            is MedicationAction.SeverityPicked -> symptomsChanged { it + (action.symptom to action.severity) }
            is MedicationAction.SymptomRemoved -> symptomsChanged { it - action.symptom }
            MedicationAction.LogDoseTapped -> sendEvent(MedicationEvent.OpenDose(null))
            is MedicationAction.DoseTapped -> sendEvent(MedicationEvent.OpenDose(action.id))
            MedicationAction.StopTapped -> {
                repository.updatePlan { it.copy(on = false) }
                sendEvent(MedicationEvent.Stopped)
            }
            MedicationAction.TurnOnTapped -> repository.updatePlan { it.copy(on = true) }
            MedicationAction.BackTapped -> sendEvent(MedicationEvent.Close)
        }
    }

    /** From the intro, the pick creates the medication and turns tracking on; later, it changes it. */
    private suspend fun productPicked(product: MedicationProduct) {
        val existing = inputs?.plan?.medication
        ui = ui.copy(doseText = null, nameText = null)
        if (existing == null) {
            val today = calendar.today()
            val medication = Medication(
                id = Uuid.random().toString(),
                product = product,
                doseUnit = product.units.first(),
                schedule = when (product.usualSchedule) {
                    is DoseSchedule.Weekly -> DoseSchedule.Weekly(today.dayOfWeek, DoseSchedule.DEFAULT_TIME)
                    else -> product.usualSchedule
                },
                addedOn = today,
            )
            repository.updatePlan { it.withMedication(medication).copy(on = true) }
            return
        }
        editMedication { current ->
            if (current.product == product) return@editMedication current
            val sameDrug = current.drug == product.drug
            current.copy(
                product = product,
                drug = product.drug,
                form = if (product.eitherForm) current.form else product.form,
                brand = current.brand.takeIf { product.named },
                doseUnit = current.doseUnit.takeIf { it in product.units } ?: product.units.first(),
                // A dose of one drug means nothing for another.
                currentDose = current.currentDose.takeIf { sameDrug },
                schedule = schedule(MedicationStateBuilder.summary(product.usualSchedule).kind, current.schedule),
            )
        }
    }

    private suspend fun doseTyped(action: MedicationAction.DoseTyped) {
        val text = action.text.filter { it.isDigit() || it == '.' || it == ',' }.take(MAX_DOSE_CHARS)
        ui = ui.copy(doseText = text)
        action.rebuild()
        val dose = DoseFormat.parse(text)
        when {
            text.isBlank() -> editMedication { it.copy(currentDose = null) }
            dose != null && dose > 0.0 && dose <= DoseDraft.MAX_DOSE -> editMedication { it.copy(currentDose = dose) }
            // Not a dose yet ("2."): the plan keeps what it had until it is one.
        }
    }

    private suspend fun reminderToggled() {
        val medication = inputs?.plan?.medication ?: return
        val on = !medication.reminder
        if (on) {
            val status = permissions.request()
            permission = status
        }
        editMedication { it.copy(reminder = on) }
    }

    private suspend fun symptomsChanged(change: (Map<Symptom, Severity>) -> Map<Symptom, Severity>) {
        val current = inputs?.symptoms ?: return
        symptoms.set(calendar.today(), change(current))
    }

    private suspend fun editMedication(change: (Medication) -> Medication) {
        repository.updatePlan { plan -> plan.medication?.let { plan.withMedication(change(it)) } ?: plan }
    }

    private suspend fun MedicationAction.rebuild() {
        val current = inputs ?: return
        val next = MedicationStateBuilder.build(current.copy(permission = permission), ui)
        updateState { next }
    }

    private fun schedule(kind: ScheduleKind, from: DoseSchedule): DoseSchedule {
        val time = from.reminderTime ?: DoseSchedule.DEFAULT_TIME
        return when (kind) {
            ScheduleKind.Weekly -> (from as? DoseSchedule.Weekly) ?: DoseSchedule.Weekly(calendar.today().dayOfWeek, time)
            ScheduleKind.Daily -> DoseSchedule.Daily(time)
            ScheduleKind.AsNeeded -> DoseSchedule.AsNeeded
        }
    }

    private fun DoseSchedule.at(time: LocalTime): DoseSchedule = when (this) {
        is DoseSchedule.Weekly -> copy(time = time)
        is DoseSchedule.Daily -> copy(time = time)
        DoseSchedule.AsNeeded -> this
    }

    private companion object {
        const val MAX_NAME = 60
        const val MAX_DOSE_CHARS = 7
    }
}

sealed interface MedicationAction {
    data class InputsArrived(val inputs: MedicationInputs) : MedicationAction
    data class PermissionChecked(val permission: NotificationPermission) : MedicationAction
    data object SetUpTapped : MedicationAction
    data class ProductPicked(val product: MedicationProduct) : MedicationAction
    data class NameTyped(val text: String) : MedicationAction
    data class FormPicked(val form: MedicationForm) : MedicationAction
    data class DoseTyped(val text: String) : MedicationAction
    data class DoseChipPicked(val dose: Double) : MedicationAction
    data class UnitPicked(val unit: DoseUnit) : MedicationAction
    data class ScheduleKindPicked(val kind: ScheduleKind) : MedicationAction
    data class DayPicked(val day: DayOfWeek) : MedicationAction
    data object TimeTapped : MedicationAction
    data object TimeDismissed : MedicationAction
    data class TimePicked(val time: LocalTime) : MedicationAction
    data object ReminderToggled : MedicationAction
    data object OpenSettingsTapped : MedicationAction
    data object ScreenResumed : MedicationAction
    data class SiteToggled(val site: InjectionSite) : MedicationAction
    data object ShowOnTodayToggled : MedicationAction
    data class SymptomTapped(val symptom: Symptom) : MedicationAction
    data class SeverityPicked(val symptom: Symptom, val severity: Severity) : MedicationAction
    data class SymptomRemoved(val symptom: Symptom) : MedicationAction
    data object LogDoseTapped : MedicationAction
    data class DoseTapped(val id: String) : MedicationAction
    data object StopTapped : MedicationAction
    data object TurnOnTapped : MedicationAction
    data object BackTapped : MedicationAction
}

sealed interface MedicationEvent {
    data object Close : MedicationEvent

    /** The dose sheet: a new dose, or [id]'s. */
    data class OpenDose(val id: String?) : MedicationEvent

    /** "Your dose history stays. Turn this back on any time." */
    data object Stopped : MedicationEvent
}
