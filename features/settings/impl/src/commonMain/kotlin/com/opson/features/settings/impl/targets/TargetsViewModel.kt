package com.opson.features.settings.impl.targets

import androidx.lifecycle.viewModelScope
import com.opson.features.settings.impl.Toast
import com.opson.libraries.diary.MacroMode
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_targets_floor_toast

/**
 * The energy range and macro split. Raising is always open; lowering stops at the floor
 * (`Targets.floorKcal`: `GOAL_FLOOR_KCAL`, or where the user started while that is unset), and
 * the floor check runs inside the write so nothing can step past it.
 */
@Inject
class TargetsViewModel(
    private val settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
) : SEAViewModel<TargetsState, TargetsEvent, TargetsAction>(initialStateArg = TargetsState()) {

    init {
        viewModelScope.launch {
            combine(settingsRepository.observe(), policySource.policy, TargetsAction::InputsArrived)
                .collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: TargetsAction) {
        when (action) {
            is TargetsAction.InputsArrived -> action.updateState { TargetsStateBuilder.build(action.settings, action.policy) }
            TargetsAction.RaiseTapped -> settingsRepository.update {
                it.copy(targets = it.targets.raise(scaleMacros = it.macroMode == MacroMode.Ratios))
            }
            TargetsAction.LowerTapped -> {
                var lowered = false
                settingsRepository.update { current ->
                    val next = current.targets.lower(scaleMacros = current.macroMode == MacroMode.Ratios)
                    if (next == null) {
                        current
                    } else {
                        lowered = true
                        current.copy(targets = next)
                    }
                }
                if (!lowered) sendEvent(TargetsEvent.ShowToast(Toast(Res.string.settings_targets_floor_toast)))
            }
            is TargetsAction.MacroModePicked -> settingsRepository.update { it.copy(macroMode = action.mode) }
            TargetsAction.ModesTapped -> sendEvent(TargetsEvent.OpenModes)
            TargetsAction.BackTapped -> sendEvent(TargetsEvent.Close)
        }
    }
}

sealed interface TargetsEvent {
    data class ShowToast(val toast: Toast) : TargetsEvent
    data object OpenModes : TargetsEvent
    data object Close : TargetsEvent
}

sealed interface TargetsAction {
    data class InputsArrived(val settings: Settings, val policy: VisibilityPolicy) : TargetsAction
    data object RaiseTapped : TargetsAction
    data object LowerTapped : TargetsAction
    data class MacroModePicked(val mode: MacroMode) : TargetsAction
    data object ModesTapped : TargetsAction
    data object BackTapped : TargetsAction
}
