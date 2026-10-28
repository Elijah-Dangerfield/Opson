package com.opson.features.logger.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.logger.ExerciseRoute
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.ExerciseRepository
import com.opson.libraries.diary.ExerciseRules
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.SelectedDay
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.ExerciseActivities
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.exercise_toast_deleted
import opson.libraries.resources.generated.resources.exercise_toast_saved
import kotlin.time.Clock

/**
 * The exercise sheet: a new entry on [ExerciseRoute.dayIso] (the day Today shows by default), or
 * the entry [ExerciseRoute.entryId] to edit or delete. Writes go to [ExerciseRepository], which
 * queues them for sync; Burned picks them up from there.
 */
@Inject
@Suppress("LongParameterList")
class ExerciseSheetViewModel(
    @Assisted route: ExerciseRoute,
    private val exercise: ExerciseRepository,
    weights: WeightRepository,
    health: HealthRepository,
    healthFlag: HealthReleaseFlag,
    policySource: VisibilityPolicySource,
    calendar: LocalCalendar,
    selectedDay: SelectedDay,
    clock: Clock,
) : SEAViewModel<ExerciseSheetState, ExerciseSheetEvent, ExerciseSheetAction>(initialStateArg = ExerciseSheetState()) {

    private val entryId = route.entryId
    private var form = ExerciseForm()
    private var inputs: ExerciseInputs? = null
    private var policy: VisibilityPolicy = policySource.policy.value

    /** Saved or deleted, and the sheet is on its way down: a second tap queued behind the first would write again. */
    private var written = false

    init {
        val connected: Flow<Boolean> =
            if (healthFlag()) health.status.map { it == HealthStatus.Connected } else flowOf(false)
        val latestKg = weights.observeWeighIns().map { list -> list.maxByOrNull { it.measuredAt }?.kg }
        val entry = flow { emit(entryId?.let { exercise.get(it) }) }
        viewModelScope.launch {
            combine(latestKg, connected, entry) { kg, isConnected, existing ->
                val zone = calendar.timeZone()
                ExerciseInputs(
                    day = existing?.day ?: route.dayIso?.let(LocalDate::parse) ?: selectedDay.date.value,
                    now = clock.now().toLocalDateTime(zone),
                    timeZone = zone,
                    weightKg = kg,
                    healthConnected = isConnected,
                    entry = existing,
                )
            }.distinctUntilChanged { old, new -> old.copy(now = new.now) == new }
                .collect { takeAction(ExerciseSheetAction.InputsArrived(it)) }
        }
        viewModelScope.launch { policySource.policy.collect { takeAction(ExerciseSheetAction.PolicyArrived(it)) } }
    }

    @Suppress("CyclomaticComplexMethod")
    override suspend fun handleAction(action: ExerciseSheetAction) {
        when (action) {
            is ExerciseSheetAction.InputsArrived -> {
                val first = inputs == null
                inputs = action.inputs
                // The entry is read once; after that the form is the person's.
                if (first) action.inputs.entry?.let { form = ExerciseForm.from(it, action.inputs.timeZone) }
                if (first && action.inputs.entry == null) form = form.copy(pane = ExercisePane.Pick)
                action.rebuild()
            }
            is ExerciseSheetAction.PolicyArrived -> {
                policy = action.policy
                action.rebuild()
            }
            ExerciseSheetAction.ActivityTapped -> action.change { copy(pane = ExercisePane.Pick, query = "") }
            is ExerciseSheetAction.QueryTyped -> action.change { copy(query = action.text) }
            is ExerciseSheetAction.ActivityPicked -> if (offered(action.key)) {
                action.change { copy(activity = action.key, pane = ExercisePane.Form, minutes = minutes.ifEmpty { DEFAULT_MINUTES }) }
            }
            ExerciseSheetAction.PickBackTapped -> if (form.activity == null) {
                sendEvent(ExerciseSheetEvent.Dismiss)
            } else {
                action.change { copy(pane = ExercisePane.Form) }
            }
            is ExerciseSheetAction.NameTyped -> action.change { copy(name = action.text.take(ExerciseRules.MAX_NAME)) }
            is ExerciseSheetAction.MinutesTyped -> action.change { copy(minutes = ExerciseSheetStateBuilder.sanitizeNumber(action.text)) }
            is ExerciseSheetAction.MinutesPicked -> action.change { copy(minutes = action.minutes.toString()) }
            is ExerciseSheetAction.TimePicked -> action.change { copy(startedAt = action.time) }
            is ExerciseSheetAction.NumberTyped -> action.change { copy(typed = ExerciseSheetStateBuilder.sanitizeNumber(action.text)) }
            is ExerciseSheetAction.NotOnWatchToggled -> action.change { copy(notOnWatch = action.on) }
            ExerciseSheetAction.SaveTapped -> save()
            ExerciseSheetAction.DeleteTapped -> delete()
            ExerciseSheetAction.CloseTapped -> sendEvent(ExerciseSheetEvent.Dismiss)
        }
    }

    /** A listed activity, or Other where the sheet offers it. */
    private fun offered(key: String): Boolean = when (key) {
        ExerciseActivities.OTHER -> inputs?.let { ExerciseSheetStateBuilder.offersOther(form, it, policy) } == true
        else -> ExerciseActivities[key] != null
    }

    private suspend fun save() {
        if (written) return
        val current = inputs ?: return
        val draft = ExerciseSheetStateBuilder.draft(form, current) ?: return
        written = true
        val id = current.entry?.id
        finish(if (id == null) exercise.add(draft) else exercise.update(id, draft), Toast(Res.string.exercise_toast_saved))
    }

    private suspend fun delete() {
        if (written) return
        val id = inputs?.entry?.id ?: return
        written = true
        finish(exercise.delete(id), Toast(Res.string.exercise_toast_deleted))
    }

    private fun finish(write: DayWrite<*>, toast: Toast) {
        when (write) {
            is DayWrite.Written -> {
                sendEvent(ExerciseSheetEvent.ShowToast(toast))
                sendEvent(ExerciseSheetEvent.Dismiss)
            }
            is DayWrite.DayClosed -> sendEvent(ExerciseSheetEvent.DayClosed(write.day))
        }
    }

    private suspend fun ExerciseSheetAction.change(edit: ExerciseForm.() -> ExerciseForm) {
        form = form.edit()
        rebuild()
    }

    private suspend fun ExerciseSheetAction.rebuild() {
        val current = inputs ?: return
        updateState { ExerciseSheetStateBuilder.build(form, current, policy) }
    }

    private companion object {
        const val DEFAULT_MINUTES = "30"
    }
}
