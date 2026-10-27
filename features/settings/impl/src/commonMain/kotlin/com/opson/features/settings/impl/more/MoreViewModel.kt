package com.opson.features.settings.impl.more

import androidx.lifecycle.viewModelScope
import com.opson.features.settings.impl.qa.QaAccess
import com.opson.libraries.diary.FastingRelease
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.health.GoalsAndEnergyEnabled
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.reminders.RemindersEnabled
import com.opson.libraries.widgets.WidgetsEnabled
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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
    private val auth: AuthRepository,
    private val qaAccess: QaAccess,
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
        // The list draws before auth resolves; the Account card joins it once there is a session.
        val account: Flow<MoreAccount?> = auth.observe()
            .map { (it as? AuthState.Authenticated)?.let { a -> MoreAccount(email = a.email, guest = a.isAnonymous) } }
            .onStart { emit(null) }
        viewModelScope.launch {
            combine(settingsRepository.observe(), healthConnected, account, MoreAction::InputsArrived).collect { takeAction(it) }
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
                    account = action.account,
                    qa = qaAccess.allowed(),
                ).copy(logOut = it.logOut)
            }
            MoreAction.ScreenResumed -> if (healthOn) health.refresh()
            is MoreAction.RowTapped -> rowTapped(action, action.row)
            MoreAction.LogOutCancelled -> action.updateState {
                if (it.logOut is LogOutStep.Working) it else it.copy(logOut = LogOutStep.Idle)
            }
            MoreAction.LogOutConfirmed -> logOut(action)
        }
    }

    private suspend fun rowTapped(action: MoreAction, row: MoreRow) {
        when (row) {
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
            MoreRow.Privacy -> sendEvent(MoreEvent.OpenPrivacy)
            MoreRow.Terms -> sendEvent(MoreEvent.OpenTerms)
            MoreRow.Credits -> sendEvent(MoreEvent.OpenCredits)
            is MoreRow.LogOut -> action.updateState { it.copy(logOut = LogOutStep.Confirming(guest = row.account.guest)) }
            MoreRow.Qa -> if (qaAccess.allowed()) sendEvent(MoreEvent.OpenQa)
        }
    }

    /**
     * Ends the session through the auth layer, which wipes this account's data from the phone and
     * resets onboarding before it reports the signed-out state, then starts over at onboarding.
     */
    private suspend fun logOut(action: MoreAction) {
        val step = state.logOut as? LogOutStep.Confirming ?: return
        action.updateState { it.copy(logOut = LogOutStep.Working(guest = step.guest)) }
        auth.signOut()
        action.updateState { it.copy(logOut = LogOutStep.Idle) }
        sendEvent(MoreEvent.GoToOnboarding)
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
    data object OpenPrivacy : MoreEvent
    data object OpenTerms : MoreEvent
    data object OpenCredits : MoreEvent
    data object OpenQa : MoreEvent

    /** Signed out: start again from onboarding with nothing behind it. */
    data object GoToOnboarding : MoreEvent
}

sealed interface MoreAction {
    /** [healthConnected] is null while `release.health` is off; [account] is null with no session. */
    data class InputsArrived(
        val settings: Settings,
        val healthConnected: Boolean? = null,
        val account: MoreAccount? = null,
    ) : MoreAction

    /** Back on More: a connection changed elsewhere (Health Connect's own screen) shows here. */
    data object ScreenResumed : MoreAction
    data class RowTapped(val row: MoreRow) : MoreAction
    data object LogOutConfirmed : MoreAction
    data object LogOutCancelled : MoreAction
}
