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
import com.opson.libraries.progressphotos.ProgressPhotosEnabled
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
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
    photosEnabled: ProgressPhotosEnabled,
) : SEAViewModel<ModesState, ModesEvent, ModesAction>(initialStateArg = ModesState()) {

    private val timeZone: TimeZone get() = TimeZone.currentSystemDefault()

    /** `release.progressPhotos`, read once when the page opens. */
    private val photosReleased = photosEnabled()

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
                ModesStateBuilder.build(action.numbers, timeZone, pickingRevealTime = it.pickingRevealTime, photosReleased = photosReleased)
            }
            is ModesAction.PresetTapped -> {
                // Picking the current preset again clears its changes; with none, there is nothing to do.
                if (state.presets.any { it.id == action.id && it.selected && it.changes == 0 }) return
                request(NumbersChange.PickPreset(action.id))
            }
            is ModesAction.LevelTapped -> {
                if (state.matrix.any { it.metric == action.metric && it.level == action.level }) return
                request(NumbersChange.Override(action.metric, action.level))
            }
            is ModesAction.PhotosTapped -> {
                val photos = state.photos ?: return
                if (photos.shown == action.shown) return
                // Showing them is showing more: the changer holds it for the pause when that is on.
                request(NumbersChange.SetProgressPhotos(action.shown))
            }
            ModesAction.PauseToggled -> {
                if (state.pauseOn) request(NumbersChange.TurnOffPause) else numbersChanger.turnOnPause()
            }
            ModesAction.RevealTimeTapped -> action.updateState { it.copy(pickingRevealTime = true) }
            ModesAction.RevealTimeDismissed -> action.updateState { it.copy(pickingRevealTime = false) }
            is ModesAction.RevealTimePicked -> {
                action.updateState { it.copy(pickingRevealTime = false) }
                if (action.time == state.revealAt) return
                request(NumbersChange.SetRevealAt(action.time))
            }
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
    data object Close : ModesEvent
}

sealed interface ModesAction {
    data class InputsArrived(val numbers: NumbersSettings) : ModesAction
    data class PresetTapped(val id: PresetId) : ModesAction
    data class LevelTapped(val metric: Metric, val level: Visibility) : ModesAction

    /** The Progress photos row: Show ([shown]) or Hide. */
    data class PhotosTapped(val shown: Boolean) : ModesAction
    data object PauseToggled : ModesAction
    data object RevealTimeTapped : ModesAction
    data object RevealTimeDismissed : ModesAction
    data class RevealTimePicked(val time: LocalTime) : ModesAction
    data object BackTapped : ModesAction
}
