package com.opson.features.settings.impl.energy

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.health.RestingChoices
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.RestingEnergies
import com.opson.libraries.nutrition.RestingSource
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.ui.components.text.NumberInput
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

/**
 * Resting energy. "Enter my own" writes only once it holds a resting energy from 500 to 5,000,
 * so typing "1600" writes once and a half-typed number never reaches Burned or sync. What is
 * typed stays on screen until then. The calculation's details are Profile's: this page links there.
 */
@Inject
class RestingEnergyViewModel(
    private val settingsRepository: SettingsRepository,
    burnedRepository: BurnedRepository,
    policySource: VisibilityPolicySource,
    health: HealthRepository,
    healthFlag: HealthReleaseFlag,
) : SEAViewModel<RestingEnergyState, RestingEnergyEvent, RestingEnergyAction>(initialStateArg = RestingEnergyState()) {

    private var inputs: RestingEnergyAction.InputsArrived? = null
    private var drafts = RestingDrafts()

    init {
        val activity = if (healthFlag()) {
            health.status.map { status -> health.source.takeIf { status == HealthStatus.Connected } }
        } else {
            flowOf(null)
        }
        viewModelScope.launch {
            combine(settingsRepository.observe(), burnedRepository.restingChoices(), policySource.policy, activity, RestingEnergyAction::InputsArrived)
                .collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: RestingEnergyAction) {
        when (action) {
            is RestingEnergyAction.InputsArrived -> {
                inputs = action
                rebuild(action)
            }
            is RestingEnergyAction.SourcePicked -> settingsRepository.update { it.copy(restingSource = action.source) }
            is RestingEnergyAction.OwnTyped -> {
                val text = NumberInput.sanitize(action.text, allowDecimal = false)
                drafts = drafts.copy(own = text)
                rebuild(action)
                val kcal = text.toIntOrNull()?.takeIf { it in RestingEnergies.OWN_MIN_KCAL..RestingEnergies.OWN_MAX_KCAL }
                if (kcal != null) settingsRepository.update { it.copy(restingOwnKcal = kcal, restingSource = RestingSource.Own) }
            }
            is RestingEnergyAction.ActivityInBurnedToggled -> settingsRepository.update { it.copy(activityInBurned = action.on) }
            is RestingEnergyAction.EverydayPicked -> settingsRepository.update { it.copy(everydayActivity = action.activity) }
            RestingEnergyAction.ProfileTapped -> sendEvent(RestingEnergyEvent.OpenProfile)
            RestingEnergyAction.ActivityTapped -> sendEvent(RestingEnergyEvent.OpenIntegrations)
            RestingEnergyAction.BackTapped -> sendEvent(RestingEnergyEvent.Close)
        }
    }

    private suspend fun rebuild(action: RestingEnergyAction) {
        val (settings, choices, policy, activity) = inputs ?: return
        action.updateState { RestingEnergyStateBuilder.build(settings, choices, policy, drafts, activity) }
    }
}

sealed interface RestingEnergyEvent {
    /** More › Profile, where the calculation's details are edited. */
    data object OpenProfile : RestingEnergyEvent
    data object OpenIntegrations : RestingEnergyEvent
    data object Close : RestingEnergyEvent
}

sealed interface RestingEnergyAction {
    data class InputsArrived(
        val settings: Settings,
        val choices: RestingChoices,
        val policy: VisibilityPolicy,
        val activitySource: HealthSource?,
    ) : RestingEnergyAction

    data class SourcePicked(val source: RestingSource) : RestingEnergyAction

    /** Typing in "Enter my own" picks it, once the number is sensible. */
    data class OwnTyped(val text: String) : RestingEnergyAction
    data class ActivityInBurnedToggled(val on: Boolean) : RestingEnergyAction
    data class EverydayPicked(val activity: EverydayActivity) : RestingEnergyAction
    data object ProfileTapped : RestingEnergyAction
    data object ActivityTapped : RestingEnergyAction
    data object BackTapped : RestingEnergyAction
}
