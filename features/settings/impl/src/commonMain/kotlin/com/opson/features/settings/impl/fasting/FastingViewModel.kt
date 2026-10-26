package com.opson.features.settings.impl.fasting

import androidx.lifecycle.viewModelScope
import com.opson.features.settings.impl.Toast
import com.opson.libraries.diary.FastingRelease
import com.opson.libraries.diary.FastingRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.MinuteTicker
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.fastingTimerOn
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.FastingPlan
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.fasting_toast_ended

/**
 * The Fasting page. Reads the settings, the latest fast and a once-a-minute tick, so the timer is
 * always computed from the stored start and survives the app being closed. While the timer is
 * on, each arrival asks the repository to start a fast that is due (the window closed since the
 * last look); the repository makes that idempotent.
 */
@Inject
class FastingViewModel(
    private val settingsRepository: SettingsRepository,
    private val fasting: FastingRepository,
    private val release: FastingRelease,
    private val calendar: LocalCalendar,
    ticker: MinuteTicker,
) : SEAViewModel<FastingState, FastingEvent, FastingAction>(initialStateArg = FastingState()) {

    private var closing = false

    init {
        viewModelScope.launch {
            combine(settingsRepository.settings, fasting.observeLatest(), ticker.ticks()) { settings, latest, now ->
                FastingAction.InputsArrived(FastingInputs(settings, release.isOn(), latest, now, calendar.timeZone()))
            }.collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: FastingAction) {
        when (action) {
            is FastingAction.InputsArrived -> arrived(action)
            FastingAction.TimerToggled -> {
                val wasOn = settingsRepository.settings.value.fasting
                settingsRepository.update { it.copy(fasting = !wasOn) }
                if (wasOn) fasting.endForTimerOff()
            }
            FastingAction.ShowOnTodayToggled -> whileTimerOn { settingsRepository.update { it.copy(fastToday = !it.fastToday) } }
            is FastingAction.PlanPicked -> whileTimerOn { settingsRepository.update { it.copy(fastPlan = action.plan) } }
            FastingAction.EndFastTapped -> {
                fasting.end()
                sendEvent(FastingEvent.ShowToast(Toast(Res.string.fasting_toast_ended)))
            }
            FastingAction.BackTapped -> sendEvent(FastingEvent.Close)
        }
    }

    private suspend fun arrived(action: FastingAction.InputsArrived) {
        val state = FastingStateBuilder.build(action.inputs)
        action.updateState { state }
        when {
            state.page == FastingPage.Hidden -> if (!closing) {
                closing = true
                sendEvent(FastingEvent.Close)
            }
            action.inputs.settings.fastingTimerOn(action.inputs.released) -> fasting.startDue()
        }
    }

    /** The prototype ignores the Today switch and the plans while the timer is off. */
    private suspend fun whileTimerOn(block: suspend () -> Unit) {
        if (settingsRepository.settings.value.fastingTimerOn(release.isOn())) block()
    }
}

sealed interface FastingEvent {
    data class ShowToast(val toast: Toast) : FastingEvent
    data object Close : FastingEvent
}

sealed interface FastingAction {
    data class InputsArrived(val inputs: FastingInputs) : FastingAction
    data object TimerToggled : FastingAction
    data object ShowOnTodayToggled : FastingAction
    data class PlanPicked(val plan: FastingPlan) : FastingAction
    data object EndFastTapped : FastingAction
    data object BackTapped : FastingAction
}
