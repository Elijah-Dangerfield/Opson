package com.opson.features.settings.impl.modes

import androidx.lifecycle.viewModelScope
import com.opson.features.settings.impl.Toast
import com.opson.libraries.diary.NumbersChanger
import com.opson.libraries.diary.NumbersOutcome
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_saved
import opson.libraries.resources.generated.resources.settings_saved_fewer

/**
 * Modes and numbers. Every change goes through [NumbersChanger], which decides whether it
 * applies now or has to wait for the pause; this view model only reports the outcome.
 */
@Inject
class ModesViewModel(
    settingsRepository: SettingsRepository,
    private val numbersChanger: NumbersChanger,
) : SEAViewModel<ModesState, ModesEvent, ModesAction>(initialStateArg = ModesState()) {

    init {
        viewModelScope.launch {
            settingsRepository.observe()
                .map { it.numbers }
                .distinctUntilChanged()
                .collect { takeAction(ModesAction.InputsArrived(it)) }
        }
    }

    override suspend fun handleAction(action: ModesAction) {
        when (action) {
            is ModesAction.InputsArrived -> action.updateState {
                ModesStateBuilder.build(action.numbers, TimeZone.currentSystemDefault())
            }
            is ModesAction.PresetTapped -> {
                if (state.presets.any { it.id == action.id && it.selected }) return
                request(NumbersChange.PickPreset(action.id))
            }
            is ModesAction.LevelTapped -> {
                if (state.matrix.any { it.metric == action.metric && it.level == action.level }) return
                request(NumbersChange.Override(action.metric, action.level))
            }
            ModesAction.PauseToggled -> {
                if (state.pauseOn) request(NumbersChange.TurnOffPause) else numbersChanger.turnOnPause()
            }
            ModesAction.TargetsTapped -> sendEvent(ModesEvent.OpenTargets)
            ModesAction.BackTapped -> sendEvent(ModesEvent.Close)
        }
    }

    private suspend fun request(change: NumbersChange) {
        when (val outcome = numbersChanger.request(change)) {
            NumbersOutcome.NeedsPause -> sendEvent(ModesEvent.OpenPause(change))
            is NumbersOutcome.Applied -> sendEvent(
                ModesEvent.ShowToast(Toast(if (outcome.fewer) Res.string.settings_saved_fewer else Res.string.settings_saved)),
            )
        }
    }
}

sealed interface ModesEvent {
    data class ShowToast(val toast: Toast) : ModesEvent
    data class OpenPause(val change: NumbersChange) : ModesEvent
    data object OpenTargets : ModesEvent
    data object Close : ModesEvent
}

sealed interface ModesAction {
    data class InputsArrived(val numbers: NumbersSettings) : ModesAction
    data class PresetTapped(val id: PresetId) : ModesAction
    data class LevelTapped(val metric: Metric, val level: Visibility) : ModesAction
    data object PauseToggled : ModesAction
    data object TargetsTapped : ModesAction
    data object BackTapped : ModesAction
}
