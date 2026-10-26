package com.opson.features.settings.impl.pause

import androidx.lifecycle.viewModelScope
import com.opson.features.settings.impl.Toast
import com.opson.libraries.diary.NumbersChanger
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.NumbersChange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_pause_saved
import kotlin.time.Duration.Companion.seconds

/**
 * "Pause before showing more". Holds [change] for ten seconds; only then can "Show them" apply
 * it. A safety feature the user turned on for themselves, so nothing here shortens the wait:
 * a tap before zero does nothing, and leaving the sheet any way at all applies nothing.
 */
@Inject
class PauseViewModel(
    @Assisted private val change: NumbersChange,
    private val numbersChanger: NumbersChanger,
) : SEAViewModel<PauseState, PauseEvent, PauseAction>(
    initialStateArg = PauseState(subject = PauseSubject.of(change)),
) {

    init {
        numbersChanger.pauseShown(change)
        viewModelScope.launch {
            for (left in PAUSE_SECONDS - 1 downTo 0) {
                delay(1.seconds)
                takeAction(PauseAction.CountdownTicked(left))
            }
        }
    }

    override suspend fun handleAction(action: PauseAction) {
        when (action) {
            is PauseAction.CountdownTicked -> action.updateState { it.copy(secondsLeft = action.left) }
            is PauseAction.ReasonPicked -> action.updateState {
                it.copy(reason = if (it.reason == action.reason) null else action.reason)
            }
            PauseAction.ShowThemTapped -> {
                if (!state.canShow) return
                action.updateState { it.copy(finished = true) }
                numbersChanger.confirm(change)
                sendEvent(PauseEvent.ShowToast(Toast(Res.string.settings_pause_saved)))
                sendEvent(PauseEvent.Dismiss)
            }
            PauseAction.KeepAsTheyAreTapped -> {
                if (keep(action)) sendEvent(PauseEvent.Dismiss)
            }
            PauseAction.SheetDismissed -> keep(action)
        }
    }

    /** False when the sheet already finished, so there is nothing left to keep. */
    private suspend fun keep(action: PauseAction): Boolean {
        if (state.finished) return false
        action.updateState { it.copy(finished = true) }
        numbersChanger.pauseKept(change)
        return true
    }
}

sealed interface PauseEvent {
    data class ShowToast(val toast: Toast) : PauseEvent

    /** Animate the sheet away; its dismiss callback pops the route. */
    data object Dismiss : PauseEvent
}

sealed interface PauseAction {
    data class CountdownTicked(val left: Int) : PauseAction
    data class ReasonPicked(val reason: PauseReason) : PauseAction
    data object ShowThemTapped : PauseAction
    data object KeepAsTheyAreTapped : PauseAction

    /** A swipe down, a tap on the scrim or back. */
    data object SheetDismissed : PauseAction
}
