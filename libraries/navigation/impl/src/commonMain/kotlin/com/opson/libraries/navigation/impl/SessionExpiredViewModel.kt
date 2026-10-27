package com.opson.libraries.navigation.impl

import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.opson.AppCache
import me.tatarka.inject.annotations.Inject

/**
 * Drives the blocking session-expired screen. The auth server rejected the
 * device's session mid-run, or identity self-heal declared it lost; the
 * teardown already happened in the auth layer. The one way on is signing in:
 * tear down the dead session deliberately, then route to onboarding's sign-in
 * step. A guest's lost session can't be recovered, so for them that sign-in
 * starts a new account.
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
    )

    sealed interface Event {
        /** Route to onboarding, which opens on sign-in. */
        data object NavigateToSignIn : Event
    }

    sealed interface Action {
        data object SignInAgain : Action
    }

    override suspend fun handleAction(action: Action) {
        when (action) {
            Action.SignInAgain -> action.signInAgain()
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
}
