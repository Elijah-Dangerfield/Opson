package com.opson.features.settings.impl.micros

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.health.GoalsAndEnergyEnabled
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.ui.components.text.NumberInput
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

/** Vitamins and minerals. It only reads; each nutrient's sheet does the writing. */
@Inject
class MicroTargetsViewModel(
    @Assisted private val backToTodayScreen: Boolean,
    settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    private val calendar: LocalCalendar,
    private val goalsAndEnergy: GoalsAndEnergyEnabled,
) : SEAViewModel<MicroTargetsState, MicroTargetsEvent, MicroTargetsAction>(
    initialStateArg = MicroTargetsState(backToTodayScreen = backToTodayScreen),
) {
    init {
        viewModelScope.launch {
            combine(settingsRepository.observe(), policySource.policy, MicroTargetsAction::InputsArrived).collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: MicroTargetsAction) {
        when (action) {
            is MicroTargetsAction.InputsArrived -> action.updateState {
                MicroTargetsStateBuilder.build(action.settings, action.policy, calendar.today(), backToTodayScreen, goalsAndEnergy())
            }
            is MicroTargetsAction.NutrientTapped -> sendEvent(MicroTargetsEvent.OpenNutrient(action.micro))
            MicroTargetsAction.AddDetailsTapped -> sendEvent(MicroTargetsEvent.OpenProfile)
            MicroTargetsAction.ModesTapped -> sendEvent(MicroTargetsEvent.OpenModes)
            MicroTargetsAction.BackTapped -> sendEvent(MicroTargetsEvent.Close)
        }
    }
}

sealed interface MicroTargetsAction {
    data class InputsArrived(val settings: Settings, val policy: VisibilityPolicy) : MicroTargetsAction
    data class NutrientTapped(val micro: Micronutrient) : MicroTargetsAction
    data object AddDetailsTapped : MicroTargetsAction
    data object ModesTapped : MicroTargetsAction
    data object BackTapped : MicroTargetsAction
}

sealed interface MicroTargetsEvent {
    data class OpenNutrient(val micro: Micronutrient) : MicroTargetsEvent
    /** More › Profile, where the birth year and sex are set. */
    data object OpenProfile : MicroTargetsEvent
    data object OpenModes : MicroTargetsEvent
    data object Close : MicroTargetsEvent
}

/**
 * One nutrient's sheet. A typed amount is saved once it is a sensible daily amount, so a
 * half-typed number never reaches the percents or sync; emptying the field goes back to the
 * suggestion. What is typed stays on screen until it is saved.
 */
@Inject
class MicroTargetViewModel(
    @Assisted private val micro: Micronutrient,
    private val settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    private val calendar: LocalCalendar,
) : SEAViewModel<MicroTargetSheetState, MicroTargetEvent, MicroTargetAction>(
    initialStateArg = MicroTargetSheetState(micro = micro),
) {
    private var inputs: MicroTargetAction.InputsArrived? = null
    private var typed: String? = null

    init {
        viewModelScope.launch {
            combine(settingsRepository.observe(), policySource.policy, MicroTargetAction::InputsArrived).collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: MicroTargetAction) {
        when (action) {
            is MicroTargetAction.InputsArrived -> {
                inputs = action
                rebuild(action)
            }
            is MicroTargetAction.OwnTyped -> {
                val text = NumberInput.sanitize(action.text, allowDecimal = true)
                typed = text
                rebuild(action)
                when {
                    text.isEmpty() -> settingsRepository.update { it.withMicroTarget(micro, null) }
                    else -> MicroTargetsStateBuilder.parseOwn(micro, text)?.let { amount ->
                        settingsRepository.update { it.withMicroTarget(micro, amount) }
                    }
                }
            }
            MicroTargetAction.UseSuggestedTapped -> {
                typed = null
                settingsRepository.update { it.withMicroTarget(micro, null) }
            }
            is MicroTargetAction.TodayToggled -> settingsRepository.update { it.withTodayNutrient(micro, action.on) }
            MicroTargetAction.CloseTapped -> sendEvent(MicroTargetEvent.Dismiss)
        }
    }

    private suspend fun rebuild(action: MicroTargetAction) {
        val (settings, policy) = inputs ?: return
        action.updateState { MicroTargetsStateBuilder.sheet(settings, policy, calendar.today(), micro, typed) }
    }
}

sealed interface MicroTargetAction {
    data class InputsArrived(val settings: Settings, val policy: VisibilityPolicy) : MicroTargetAction
    data class OwnTyped(val text: String) : MicroTargetAction
    data object UseSuggestedTapped : MicroTargetAction
    data class TodayToggled(val on: Boolean) : MicroTargetAction
    data object CloseTapped : MicroTargetAction
}

sealed interface MicroTargetEvent {
    data object Dismiss : MicroTargetEvent
}
