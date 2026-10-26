package com.opson.features.settings.impl.more

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.reminders.RemindersEnabled
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

@Inject
class MoreViewModel(
    settingsRepository: SettingsRepository,
    private val remindersEnabled: RemindersEnabled,
) : SEAViewModel<MoreState, MoreEvent, MoreAction>(initialStateArg = MoreState()) {

    init {
        viewModelScope.launch {
            settingsRepository.observe().collect { takeAction(MoreAction.InputsArrived(it)) }
        }
    }

    override suspend fun handleAction(action: MoreAction) {
        when (action) {
            is MoreAction.InputsArrived -> action.updateState { MoreStateBuilder.build(action.settings, remindersEnabled()) }
            is MoreAction.RowTapped -> when (action.row) {
                is MoreRow.Modes -> sendEvent(MoreEvent.OpenModes)
                MoreRow.Targets -> sendEvent(MoreEvent.OpenTargets)
                MoreRow.LookAndFeel -> sendEvent(MoreEvent.OpenLookAndFeel)
                is MoreRow.Reminders -> sendEvent(MoreEvent.OpenReminders)
                is MoreRow.Water -> sendEvent(MoreEvent.OpenWater)
                MoreRow.Support -> sendEvent(MoreEvent.OpenSupport)
                MoreRow.Plan -> Unit
            }
        }
    }
}

sealed interface MoreEvent {
    data object OpenModes : MoreEvent
    data object OpenTargets : MoreEvent
    data object OpenWater : MoreEvent
    data object OpenLookAndFeel : MoreEvent
    data object OpenReminders : MoreEvent
    data object OpenSupport : MoreEvent
}

sealed interface MoreAction {
    data class InputsArrived(val settings: Settings) : MoreAction
    data class RowTapped(val row: MoreRow) : MoreAction
}
