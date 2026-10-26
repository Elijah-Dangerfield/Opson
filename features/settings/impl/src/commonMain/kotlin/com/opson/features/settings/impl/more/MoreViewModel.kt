package com.opson.features.settings.impl.more

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

@Inject
class MoreViewModel(
    settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
) : SEAViewModel<MoreState, MoreEvent, MoreAction>(initialStateArg = MoreState()) {

    init {
        viewModelScope.launch {
            combine(settingsRepository.observe(), policySource.policy, MoreAction::InputsArrived)
                .collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: MoreAction) {
        when (action) {
            is MoreAction.InputsArrived -> action.updateState { MoreStateBuilder.build(action.settings, action.policy) }
            is MoreAction.RowTapped -> sendEvent(
                when (action.row) {
                    is MoreRow.Modes -> MoreEvent.OpenModes
                    is MoreRow.Targets -> MoreEvent.OpenTargets
                    MoreRow.Water -> MoreEvent.OpenWater
                    MoreRow.LookAndFeel -> MoreEvent.OpenLookAndFeel
                    MoreRow.Support -> MoreEvent.OpenSupport
                },
            )
        }
    }
}

sealed interface MoreEvent {
    data object OpenModes : MoreEvent
    data object OpenTargets : MoreEvent
    data object OpenWater : MoreEvent
    data object OpenLookAndFeel : MoreEvent
    data object OpenSupport : MoreEvent
}

sealed interface MoreAction {
    data class InputsArrived(val settings: Settings, val policy: VisibilityPolicy) : MoreAction
    data class RowTapped(val row: MoreRow) : MoreAction
}
