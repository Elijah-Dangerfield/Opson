package com.opson.features.settings.impl.energy

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

/** One (i) sheet: settings, the resting energy choices and today's Burned, rebuilt as they change. */
@Inject
class EnergyExplainerViewModel(
    @Assisted private val topic: EnergyTopic,
    private val settingsRepository: SettingsRepository,
    burnedRepository: BurnedRepository,
    policySource: VisibilityPolicySource,
    calendar: LocalCalendar,
) : SEAViewModel<EnergyExplainerState, EnergyExplainerEvent, EnergyExplainerAction>(initialStateArg = EnergyExplainerState(topic = topic)) {

    private val today: LocalDate = calendar.today()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.observe(),
                burnedRepository.restingChoices(),
                burnedRepository.burned(today),
                policySource.policy,
            ) { settings, choices, burned, policy ->
                EnergyExplainerAction.InputsArrived(EnergyExplainerInputs(settings, choices, burned, today), policy)
            }.collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: EnergyExplainerAction) {
        when (action) {
            is EnergyExplainerAction.InputsArrived -> action.updateState { EnergyExplainerStateBuilder.build(topic, action.inputs, action.policy) }
            EnergyExplainerAction.ProfileTapped -> sendEvent(EnergyExplainerEvent.OpenProfile)
            EnergyExplainerAction.RestingEnergyTapped -> sendEvent(EnergyExplainerEvent.OpenRestingEnergy)
            EnergyExplainerAction.DoneTapped -> sendEvent(EnergyExplainerEvent.Dismiss)
            is EnergyExplainerAction.DigestingToggled -> settingsRepository.update { it.copy(thermicEffectInBurned = action.on) }
            EnergyExplainerAction.DigestingInfoTapped -> sendEvent(EnergyExplainerEvent.OpenTopic(EnergyTopic.Digesting))
        }
    }
}

sealed interface EnergyExplainerEvent {
    /** More › Profile, where the details the calculation reads are changed. */
    data object OpenProfile : EnergyExplainerEvent

    /** Resting energy, where its source is picked. */
    data object OpenRestingEnergy : EnergyExplainerEvent

    /** Another (i) sheet in this one's place: Burned's link to digesting food. */
    data class OpenTopic(val topic: EnergyTopic) : EnergyExplainerEvent
    data object Dismiss : EnergyExplainerEvent
}

sealed interface EnergyExplainerAction {
    data class InputsArrived(val inputs: EnergyExplainerInputs, val policy: VisibilityPolicy) : EnergyExplainerAction
    data object ProfileTapped : EnergyExplainerAction
    data object RestingEnergyTapped : EnergyExplainerAction
    data object DoneTapped : EnergyExplainerAction

    /** "Count digesting food (TEF)", switched right on the sheet. */
    data class DigestingToggled(val on: Boolean) : EnergyExplainerAction

    /** Burned's "What digesting food adds". */
    data object DigestingInfoTapped : EnergyExplainerAction
}
