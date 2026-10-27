package com.opson.features.settings.impl.modes

import androidx.lifecycle.viewModelScope
import com.opson.features.settings.impl.Toast
import com.opson.libraries.diary.NumbersChanger
import com.opson.libraries.diary.NumbersOutcome
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ModeTiming
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
 * Modes and numbers, and its Each number page. Every change to what shows goes through
 * [NumbersChanger], which decides whether it applies now or has to wait for the pause; this view
 * model only reports the outcome.
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

    private var numbers: NumbersSettings = NumbersSettings()

    /** End after a set time's choices are open; they show once the timer is written. */
    private var editingEndAfter = false

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
            is ModesAction.InputsArrived -> {
                numbers = action.numbers
                action.updateState { rebuild(it) }
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
            ModesAction.EndAfterToggled -> {
                editingEndAfter = !state.endAfter.on
                action.updateState { rebuild(it) }
                if (editingEndAfter) {
                    numbersChanger.setEndAfter(ModeTiming.DEFAULT_WEEKS, ModeTiming.defaultThen(numbers.preset))
                } else {
                    numbersChanger.clearEndAfter()
                }
            }
            ModesAction.EndAfterTapped -> {
                editingEndAfter = !editingEndAfter
                action.updateState { rebuild(it) }
            }
            is ModesAction.EndAfterWeeksPicked -> {
                val timing = state.endAfter.timing ?: return
                if (timing.weeks != action.weeks) numbersChanger.setEndAfter(action.weeks, timing.then)
            }
            is ModesAction.EndAfterThenPicked -> {
                val timing = state.endAfter.timing ?: return
                if (timing.then != action.then) numbersChanger.setEndAfter(timing.weeks, action.then)
            }
            ModesAction.EachNumberTapped -> sendEvent(ModesEvent.OpenEachNumber)
            ModesAction.RevealTimeTapped -> action.updateState { it.copy(pickingRevealTime = true) }
            ModesAction.RevealTimeDismissed -> action.updateState { it.copy(pickingRevealTime = false) }
            is ModesAction.RevealTimePicked -> {
                action.updateState { it.copy(pickingRevealTime = false) }
                if (action.time == state.reveal?.time) return
                request(NumbersChange.SetRevealAt(action.time))
            }
            ModesAction.RevealWeeklyToggled -> {
                val reveal = state.reveal ?: return
                request(NumbersChange.SetRevealWeekly(weekly = !reveal.weekly))
            }
            ModesAction.BackTapped -> sendEvent(ModesEvent.Close)
        }
    }

    private fun rebuild(current: ModesState): ModesState =
        ModesStateBuilder.build(
            numbers,
            timeZone,
            pickingRevealTime = current.pickingRevealTime,
            photosReleased = photosReleased,
            editingEndAfter = editingEndAfter,
        )

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
    data object OpenEachNumber : ModesEvent
    data object Close : ModesEvent
}

sealed interface ModesAction {
    data class InputsArrived(val numbers: NumbersSettings) : ModesAction
    data class PresetTapped(val id: PresetId) : ModesAction
    data class LevelTapped(val metric: Metric, val level: Visibility) : ModesAction

    /** The Progress photos row: Show ([shown]) or Hide. */
    data class PhotosTapped(val shown: Boolean) : ModesAction
    data object PauseToggled : ModesAction

    /** The End after a set time switch. */
    data object EndAfterToggled : ModesAction

    /** The End after a set time row itself: opens or closes its choices. */
    data object EndAfterTapped : ModesAction
    data class EndAfterWeeksPicked(val weeks: Int) : ModesAction
    data class EndAfterThenPicked(val then: PresetId) : ModesAction
    data object EachNumberTapped : ModesAction
    data object RevealTimeTapped : ModesAction
    data object RevealTimeDismissed : ModesAction
    data class RevealTimePicked(val time: LocalTime) : ModesAction

    /** "Once a week" for Later's totals. */
    data object RevealWeeklyToggled : ModesAction
    data object BackTapped : ModesAction
}
