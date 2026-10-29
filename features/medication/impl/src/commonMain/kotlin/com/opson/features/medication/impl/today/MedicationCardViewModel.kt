package com.opson.features.medication.impl.today

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.medication.MedicationDose
import com.opson.libraries.medication.MedicationEnabled
import com.opson.libraries.medication.MedicationPlan
import com.opson.libraries.medication.MedicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Inject

/**
 * Today's Medication card. Today tells it which day is on show ([MedicationCardAction.DayShown]);
 * the card exists only on today, and the day is re-read then, so it rolls over at midnight with
 * Today rather than on its own timer.
 */
@Inject
class MedicationCardViewModel(
    private val repository: MedicationRepository,
    private val calendar: LocalCalendar,
    private val enabled: MedicationEnabled,
) : SEAViewModel<MedicationCardState, MedicationCardEvent, MedicationCardAction>(initialStateArg = MedicationCardState()) {

    private val shown = MutableStateFlow<LocalDate?>(null)

    init {
        viewModelScope.launch {
            combine(repository.plan, repository.observeDoses(), shown) { plan, doses, day -> Triple(plan, doses, day) }
                .collect { (plan, doses, day) -> takeAction(MedicationCardAction.InputsArrived(plan, doses, day)) }
        }
    }

    override suspend fun handleAction(action: MedicationCardAction) {
        when (action) {
            is MedicationCardAction.InputsArrived -> {
                val today = calendar.today()
                val card = if (action.day == today) {
                    MedicationCardBuilder.build(enabled.value, action.plan, action.doses, today, calendar.timeZone())
                } else {
                    null
                }
                action.updateState { it.copy(card = card) }
            }
            is MedicationCardAction.DayShown -> shown.value = action.day
            MedicationCardAction.LogTapped -> sendEvent(MedicationCardEvent.OpenSheet(doseId = null))
            MedicationCardAction.CardTapped -> when (val card = state.card) {
                is MedicationCard.Logged -> sendEvent(MedicationCardEvent.OpenSheet(card.doseId))
                null -> Unit
                else -> sendEvent(MedicationCardEvent.OpenMedication)
            }
            // Writes nothing but the dose day it was for, so the prompt waits for the next one.
            MedicationCardAction.SkippedTapped -> (state.card as? MedicationCard.NotLogged)?.let { card ->
                repository.updatePlan { it.copy(skippedDue = card.due) }
            }
        }
    }
}

data class MedicationCardState(val card: MedicationCard? = null)

sealed interface MedicationCardAction {
    data class InputsArrived(val plan: MedicationPlan, val doses: List<MedicationDose>, val day: LocalDate?) : MedicationCardAction
    data class DayShown(val day: LocalDate) : MedicationCardAction
    data object LogTapped : MedicationCardAction
    data object CardTapped : MedicationCardAction
    data object SkippedTapped : MedicationCardAction
}

sealed interface MedicationCardEvent {
    data class OpenSheet(val doseId: String?) : MedicationCardEvent
    data object OpenMedication : MedicationCardEvent
}
