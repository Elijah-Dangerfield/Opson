package com.opson.features.medication.impl.dose

import androidx.lifecycle.viewModelScope
import com.opson.features.medication.LogDoseRoute
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.medication.DoseDraft
import com.opson.libraries.medication.DoseFormat
import com.opson.libraries.medication.DoseVia
import com.opson.libraries.medication.InjectionSite
import com.opson.libraries.medication.MedicationDose
import com.opson.libraries.medication.MedicationRepository
import com.opson.libraries.medication.SiteRotation
import com.opson.libraries.medication.displayName
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Log a dose, or edit one. A new dose starts on the current dose, now, and the site used longest
 * ago; everything can be changed, and a dose can be put on yesterday. Saving a new amount asks
 * once whether it is the current dose now. Nothing here judges a dose: no "too soon", no "too
 * much", no missed-dose advice.
 */
@Inject
class LogDoseViewModel(
    @Assisted route: LogDoseRoute,
    private val repository: MedicationRepository,
    private val calendar: LocalCalendar,
    private val clock: Clock,
) : SEAViewModel<LogDoseState, LogDoseEvent, LogDoseAction>(initialStateArg = LogDoseState()) {

    private val doseId = route.doseId
    private val via = DoseVia.entries.firstOrNull { it.wire == route.via } ?: DoseVia.Medication
    private var medicationId: String? = null
    private var editing: MedicationDose? = null

    init {
        viewModelScope.launch { load(route) }
    }

    private suspend fun load(route: LogDoseRoute) {
        val plan = repository.currentPlan()
        val medication = plan.medication
        if (medication == null) {
            sendEvent(LogDoseEvent.Dismiss)
            return
        }
        medicationId = medication.id
        val doses = repository.observeDoses().first()
        val existing = doseId?.let { repository.dose(it) }
        editing = existing
        val zone = calendar.timeZone()
        val today = calendar.today()
        val suggested = if (medication.injected) SiteRotation.next(plan.sites, doses) else null
        val due = route.dueIso?.let { Catching { LocalDate.parse(it) }.getOrNull() }
        val now = clock.now().toLocalDateTime(zone)
        // A reminder tapped the next morning logs against yesterday's dose day, at its time. One
        // older than that starts on now, as any new dose does: today at the schedule's time could
        // be later than now, and a dose is never put in the future.
        val forYesterday = due != null && due == today.minus(DatePeriod(days = 1))
        val loaded = state.let {
            if (existing != null) {
                val local = existing.takenAt.toLocalDateTime(zone)
                it.copy(
                    loading = false,
                    editing = true,
                    name = existing.displayName(),
                    doseText = DoseFormat.number(existing.dose),
                    unit = existing.unit,
                    chips = existing.product.chips,
                    day = dayOf(local.date, today),
                    time = LocalTime(local.hour, local.minute),
                    site = existing.site,
                    suggested = null,
                    sites = if (existing.site != null || medication.injected) plan.sites + listOfNotNull(existing.site) else null,
                    note = existing.note.orEmpty(),
                )
            } else {
                it.copy(
                    loading = false,
                    name = medication.displayName(),
                    doseText = medication.currentDose?.let(DoseFormat::number).orEmpty(),
                    unit = medication.doseUnit,
                    chips = medication.product.chips,
                    day = if (forYesterday) DoseDay.Yesterday else DoseDay.Today,
                    time = medication.schedule.reminderTime?.takeIf { forYesterday } ?: now.time.toMinute(),
                    site = suggested,
                    suggested = suggested,
                    sites = plan.sites.takeIf { medication.injected },
                )
            }
        }
        takeAction(LogDoseAction.Loaded(loaded))
    }

    override suspend fun handleAction(action: LogDoseAction) {
        when (action) {
            is LogDoseAction.Loaded -> action.updateState { action.state }
            is LogDoseAction.DoseTyped -> action.updateState {
                it.copy(doseText = action.text.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(MAX_DOSE_CHARS), notADose = false, failed = false)
            }
            is LogDoseAction.ChipPicked -> action.updateState { it.copy(doseText = DoseFormat.number(action.dose), notADose = false) }
            is LogDoseAction.DayPicked -> action.updateState { it.copy(day = action.day) }
            LogDoseAction.TimeTapped -> action.updateState { it.copy(pickingTime = true) }
            LogDoseAction.TimeDismissed -> action.updateState { it.copy(pickingTime = false) }
            is LogDoseAction.TimePicked -> action.updateState { it.copy(time = action.time, pickingTime = false) }
            is LogDoseAction.SiteTapped -> action.updateState { if (it.sites != null) it.copy(site = action.site) else it }
            is LogDoseAction.NoteTyped -> action.updateState { it.copy(note = action.text.take(DoseDraft.MAX_NOTE)) }
            LogDoseAction.SaveTapped -> save(action)
            LogDoseAction.DeleteTapped -> delete(action)
            LogDoseAction.MakeCurrentTapped -> makeCurrent()
            LogDoseAction.JustThisOnceTapped -> sendEvent(LogDoseEvent.Dismiss)
            LogDoseAction.CloseTapped -> sendEvent(LogDoseEvent.Dismiss)
        }
    }

    private suspend fun save(action: LogDoseAction) {
        val id = medicationId ?: return
        if (!state.canSave) {
            if (!state.loading) action.updateState { it.copy(notADose = true) }
            return
        }
        val dose = checkNotNull(state.dose)
        val draft = DoseDraft(
            medicationId = id,
            dose = dose,
            unit = state.unit,
            takenAt = takenAt(),
            site = state.site.takeIf { state.sites != null },
            note = state.note.trim().ifEmpty { null },
        )
        action.updateState { it.copy(saving = true, failed = false) }
        val existing = editing
        val written = Catching {
            if (existing != null) repository.updateDose(existing.id, draft) else repository.logDose(draft, via) != null
        }.logOnFailure("Couldn't save a dose").getOrNull() == true
        if (!written) {
            action.updateState { it.copy(saving = false, failed = true) }
            return
        }
        if (existing != null) {
            sendEvent(LogDoseEvent.Dismiss)
            return
        }
        val current = repository.currentPlan().medication?.currentDose
        when {
            // The first dose logged is the current dose; there is nothing to ask.
            current == null -> {
                repository.updatePlan { plan -> plan.medication?.let { plan.withMedication(it.copy(currentDose = dose)) } ?: plan }
                sendEvent(LogDoseEvent.Dismiss)
            }
            current != dose -> action.updateState { it.copy(saving = false, step = DoseStep.AskCurrent(dose, state.unit)) }
            else -> sendEvent(LogDoseEvent.Dismiss)
        }
    }

    private suspend fun makeCurrent() {
        val step = state.step as? DoseStep.AskCurrent ?: return
        repository.updatePlan { plan ->
            plan.medication?.let { plan.withMedication(it.copy(currentDose = step.dose, doseUnit = step.unit)) } ?: plan
        }
        sendEvent(LogDoseEvent.Dismiss)
    }

    private suspend fun delete(action: LogDoseAction) {
        val existing = editing ?: return
        val deleted = Catching { repository.deleteDose(existing.id) }.logOnFailure("Couldn't delete a dose").getOrNull() == true
        if (deleted) sendEvent(LogDoseEvent.Deleted(existing.id)) else action.updateState { it.copy(failed = true) }
    }

    private fun takenAt(): Instant {
        val zone = calendar.timeZone()
        val today = calendar.today()
        val date = when (val day = state.day) {
            DoseDay.Today -> today
            DoseDay.Yesterday -> today.minus(DatePeriod(days = 1))
            is DoseDay.Other -> day.date
        }
        return date.atTime(state.time).toInstant(zone)
    }

    private fun dayOf(date: LocalDate, today: LocalDate): DoseDay = when (date) {
        today -> DoseDay.Today
        today.minus(DatePeriod(days = 1)) -> DoseDay.Yesterday
        else -> DoseDay.Other(date)
    }

    /** To the minute: a dose's time is never shown in seconds. */
    private fun LocalTime.toMinute(): LocalTime = LocalTime(hour, minute)

    private companion object {
        const val MAX_DOSE_CHARS = 7
    }
}

sealed interface LogDoseAction {
    /** The sheet's starting state, once the plan and the dose have been read. */
    data class Loaded(val state: LogDoseState) : LogDoseAction
    data class DoseTyped(val text: String) : LogDoseAction
    data class ChipPicked(val dose: Double) : LogDoseAction
    data class DayPicked(val day: DoseDay) : LogDoseAction
    data object TimeTapped : LogDoseAction
    data object TimeDismissed : LogDoseAction
    data class TimePicked(val time: LocalTime) : LogDoseAction
    data class SiteTapped(val site: InjectionSite) : LogDoseAction
    data class NoteTyped(val text: String) : LogDoseAction
    data object SaveTapped : LogDoseAction
    data object DeleteTapped : LogDoseAction
    data object MakeCurrentTapped : LogDoseAction
    data object JustThisOnceTapped : LogDoseAction
    data object CloseTapped : LogDoseAction
}

sealed interface LogDoseEvent {
    data object Dismiss : LogDoseEvent

    /** The sheet closes and the snackbar offers Undo, which runs after the sheet has gone. */
    data class Deleted(val id: String) : LogDoseEvent
}
