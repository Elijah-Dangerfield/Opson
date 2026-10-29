package com.opson.features.settings.impl.more

import androidx.lifecycle.viewModelScope
import com.opson.features.settings.MoreRowContribution
import com.opson.libraries.diary.DiarySync
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
import com.opson.libraries.navigation.Route
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
import kotlinx.coroutines.withTimeoutOrNull
import me.tatarka.inject.annotations.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * [contributions] are the rows other modules add ([MoreRowContribution]). Empty by default, so a
 * build without any contributing module still wires: kotlin-inject has no binding for an empty
 * multibound set, and uses the default instead.
 */
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
    private val diarySync: DiarySync,
    private val contributions: Set<MoreRowContribution> = emptySet(),
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
                    contributed = contributions.filter { it.visible() },
                ).copy(logOut = it.logOut)
            }
            MoreAction.ScreenResumed -> if (healthOn) health.refresh()
            is MoreAction.RowTapped -> rowTapped(action, action.row)
            MoreAction.LogOutCancelled -> action.updateState {
                if (it.logOut is LogOutStep.Working) it else it.copy(logOut = LogOutStep.Idle)
            }
            MoreAction.LogOutConfirmed -> logOut(action)
            is MoreAction.LogOutSyncFinished -> logOutSynced(action)
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
            MoreRow.ReportBug -> sendEvent(MoreEvent.OpenBugReport)
            MoreRow.SendFeedback -> sendEvent(MoreEvent.OpenFeedback)
            MoreRow.Plan -> Unit
            MoreRow.Privacy -> sendEvent(MoreEvent.OpenPrivacy)
            MoreRow.Terms -> sendEvent(MoreEvent.OpenTerms)
            MoreRow.Credits -> sendEvent(MoreEvent.OpenCredits)
            is MoreRow.LogOut -> action.updateState { it.copy(logOut = LogOutStep.Confirming(guest = row.account.guest)) }
            is MoreRow.Contributed -> if (row.row.visible()) sendEvent(MoreEvent.OpenContributed(row.row.route()))
        }
    }

    /**
     * Ends the session through the auth layer, which wipes this account's data from the phone,
     * the outbox included, and resets onboarding before it reports the signed-out state, then
     * starts over at onboarding. Signed in, it pushes what is queued first, and if the account
     * can't be reached it asks again ([LogOutStep.Unsynced]) rather than wipe changes that never
     * left the phone.
     */
    private suspend fun logOut(action: MoreAction) {
        when (val step = state.logOut) {
            is LogOutStep.Confirming -> {
                action.updateState { it.copy(logOut = LogOutStep.Working(guest = step.guest)) }
                // A guest's log can't be reached again once they leave, so there is nothing to send it for.
                if (step.guest) {
                    signOut(action)
                } else {
                    // Off the action queue, so a dismiss sent meanwhile meets Working and is ignored
                    // instead of waiting its turn and closing the warning before it is read.
                    viewModelScope.launch { takeAction(MoreAction.LogOutSyncFinished(reachedAccount())) }
                }
            }
            // "Log out anyway", having been told what stays behind.
            LogOutStep.Unsynced -> {
                action.updateState { it.copy(logOut = LogOutStep.Working(guest = false, unsynced = true)) }
                signOut(action)
            }
            LogOutStep.Idle, is LogOutStep.Working -> Unit
        }
    }

    private suspend fun logOutSynced(action: MoreAction.LogOutSyncFinished) {
        if (state.logOut !is LogOutStep.Working) return
        if (action.reachedAccount) {
            signOut(action)
        } else {
            action.updateState { it.copy(logOut = LogOutStep.Unsynced) }
        }
    }

    private suspend fun signOut(action: MoreAction) {
        auth.signOut()
        action.updateState { it.copy(logOut = LogOutStep.Idle) }
        sendEvent(MoreEvent.GoToOnboarding)
    }

    /**
     * Whether a sync reached the account and left nothing behind. The wait is capped so a server
     * that hangs can't hold the dialog; the sync itself runs on in the app scope. A sync that
     * succeeds can still leave rows queued (a day further ahead than the server takes yet), and
     * signing out would wipe those too.
     */
    private suspend fun reachedAccount(): Boolean {
        val synced = withTimeoutOrNull(LOG_OUT_SYNC_WAIT) { diarySync.syncNow() }?.isSuccess == true
        return synced && diarySync.pendingChanges() == 0
    }

    private companion object {
        val LOG_OUT_SYNC_WAIT = 10.seconds
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
    data object OpenBugReport : MoreEvent
    data object OpenFeedback : MoreEvent
    data object OpenFasting : MoreEvent
    data object OpenWidgets : MoreEvent
    data object OpenPrivacy : MoreEvent
    data object OpenTerms : MoreEvent
    data object OpenCredits : MoreEvent
    data class OpenContributed(val route: Route) : MoreEvent

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

    /** The sync before a signed-in log out came back, or ran out of time. */
    data class LogOutSyncFinished(val reachedAccount: Boolean) : MoreAction
}
