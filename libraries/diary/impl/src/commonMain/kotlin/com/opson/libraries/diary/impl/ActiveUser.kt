package com.opson.libraries.diary.impl

import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.AuthState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** Who the device is signed in as, as far as whose settings are on screen is concerned. */
sealed interface ActiveUser {
    /** Auth has not answered yet (cold boot). */
    data object Unresolved : ActiveUser

    data object SignedOut : ActiveUser

    data class SignedIn(val userId: String) : ActiveUser
}

interface ActiveUserSource {
    val user: StateFlow<ActiveUser>
}

/**
 * [AuthRepository] behind a provider: the settings store is a user-scoped
 * clearer, the auth repository runs the clearers, so a direct dependency would
 * be a construction cycle. The provider is only called once the flow starts.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AuthActiveUserSource(
    authRepositoryProvider: () -> AuthRepository,
    appScope: AppCoroutineScope,
) : ActiveUserSource {
    override val user: StateFlow<ActiveUser> =
        flow { emitAll(authRepositoryProvider().observe()) }
            .map { state ->
                when (state) {
                    is AuthState.Authenticated -> ActiveUser.SignedIn(state.userId)
                    is AuthState.Unauthenticated -> ActiveUser.SignedOut
                }
            }
            .stateIn(appScope, SharingStarted.Eagerly, ActiveUser.Unresolved)
}
