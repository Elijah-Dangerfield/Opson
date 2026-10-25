package com.opson.libraries.navigation.impl

import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.SignInOutcome
import com.opson.libraries.opson.AppCache
import me.tatarka.inject.annotations.Inject

/**
 * Drives the blocking session-expired screen. The auth server rejected the
 * device's session mid-run; the teardown already happened in the auth layer —
 * this VM only owns the two recovery paths:
 *
 *  - **Sign in again** (claimed accounts): tear down the dead session
 *    deliberately, then route to the sign-in screen. A successful sign-in
 *    re-marks `hasUserOnboarded` and lands on Home.
 *  - **Start fresh** (guests): an anonymous session is unrecoverable, so mint
 *    a brand-new guest session in place. On success the user keeps using the
 *    app (with a fresh account); on failure (offline) the screen surfaces a
 *    retryable error rather than stranding them.
 */
@Inject
class SessionExpiredViewModel(
    private val authRepository: AuthRepository,
    private val appCache: AppCache,
) : SEAViewModel<
    SessionExpiredViewModel.State,
    SessionExpiredViewModel.Event,
    SessionExpiredViewModel.Action,
    >(initialStateArg = State()) {

    data class State(
        val working: Boolean = false,
        val startFreshFailed: Boolean = false,
    )

    sealed interface Event {
        /** Route to the sign-in screen (claimed-account recovery). */
        data object NavigateToSignIn : Event

        /** A fresh guest session is live — route back into the app. */
        data object StartedFresh : Event
    }

    sealed interface Action {
        data object SignInAgain : Action
        data object StartFresh : Action
    }

    override suspend fun handleAction(action: Action) {
        when (action) {
            Action.SignInAgain -> action.signInAgain()
            Action.StartFresh -> action.startFresh()
        }
    }

    private suspend fun Action.signInAgain() {
        updateState { it.copy(working = true) }
        // Deliberate teardown: clears the sticky SessionExpired state so
        // nothing re-fires the blocking screen while the user signs in.
        authRepository.signOut()
        appCache.update { it.copy(hasUserOnboarded = false) }
        updateState { it.copy(working = false) }
        sendEvent(Event.NavigateToSignIn)
    }

    private suspend fun Action.startFresh() {
        updateState { it.copy(working = true, startFreshFailed = false) }
        val outcome = authRepository.createGuestSession()
        if (outcome is SignInOutcome.Success) {
            appCache.update { it.copy(hasUserOnboarded = true) }
            sendEvent(Event.StartedFresh)
            updateState { it.copy(working = false) }
        } else {
            updateState { it.copy(working = false, startFreshFailed = true) }
        }
    }
}
