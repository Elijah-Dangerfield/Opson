package com.opson.features.settings.impl.more

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.FastingRelease
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.health.GoalsAndEnergyEnabled
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.reminders.RemindersEnabled
import com.opson.libraries.widgets.WidgetsEnabled
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

@Inject
class MoreViewModel(
    settingsRepository: SettingsRepository,
    private val health: HealthRepository,
    healthFlag: HealthReleaseFlag,
    private val remindersEnabled: RemindersEnabled,
    private val fastingRelease: FastingRelease,
    private val widgetsEnabled: WidgetsEnabled,
    private val goalsAndEnergy: GoalsAndEnergyEnabled,
) : SEAViewModel<MoreState, MoreEvent, MoreAction>(initialStateArg = MoreState()) {

    // Read once, as the page opens: with the flag off the health store is never touched, not even to check it.
    private val healthOn = healthFlag()

    init {
        val healthConnected: Flow<Boolean?> = if (healthOn) {
            flow {
                health.refresh()
                emitAll(health.status.map { it == HealthStatus.Connected })
            }
        } else {
            flowOf(null)
        }
        viewModelScope.launch {
            combine(settingsRepository.observe(), healthConnected, MoreAction::InputsArrived).collect { takeAction(it) }
        }
    }

    override suspend fun handleAction(action: MoreAction) {
        when (action) {
            is MoreAction.InputsArrived -> action.updateState {
                MoreStateBuilder.build(
                    action.settings,
                    action.healthConnected,
                    remindersOn = remindersEnabled(),
                    fastingReleased = fastingRelease.isOn(),
                    widgetsOn = widgetsEnabled(),
                    todayScreenOn = goalsAndEnergy(),
                )
            }
            MoreAction.ScreenResumed -> if (healthOn) health.refresh()
            is MoreAction.RowTapped -> when (action.row) {
                is MoreRow.Modes -> sendEvent(MoreEvent.OpenModes)
                MoreRow.Targets -> sendEvent(MoreEvent.OpenTargets)
                MoreRow.TodayScreen -> sendEvent(MoreEvent.OpenTodayScreen)
                MoreRow.LookAndFeel -> sendEvent(MoreEvent.OpenLookAndFeel)
                is MoreRow.Health -> sendEvent(MoreEvent.OpenHealth)
                is MoreRow.Reminders -> sendEvent(MoreEvent.OpenReminders)
                is MoreRow.Water -> sendEvent(MoreEvent.OpenWater)
                is MoreRow.Fasting -> sendEvent(MoreEvent.OpenFasting)
                MoreRow.Widgets -> sendEvent(MoreEvent.OpenWidgets)
                MoreRow.Support -> sendEvent(MoreEvent.OpenSupport)
                MoreRow.Plan -> Unit
            }
        }
    }
}

sealed interface MoreEvent {
    data object OpenModes : MoreEvent
    data object OpenTargets : MoreEvent
    data object OpenTodayScreen : MoreEvent
    data object OpenWater : MoreEvent
    data object OpenLookAndFeel : MoreEvent
    data object OpenHealth : MoreEvent
    data object OpenReminders : MoreEvent
    data object OpenSupport : MoreEvent
    data object OpenFasting : MoreEvent
    data object OpenWidgets : MoreEvent
}

sealed interface MoreAction {
    /** [healthConnected] is null while `release.health` is off. */
    data class InputsArrived(val settings: Settings, val healthConnected: Boolean? = null) : MoreAction

    /** Back on More: a connection changed elsewhere (Health Connect's own screen) shows here. */
    data object ScreenResumed : MoreAction
    data class RowTapped(val row: MoreRow) : MoreAction
}
