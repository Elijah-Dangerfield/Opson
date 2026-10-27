package com.opson.libraries.identity.impl.auth

import com.opson.libraries.opson.AppCache
import com.opson.libraries.core.AppState
import com.opson.libraries.core.AuthGate
import com.opson.libraries.core.AuthReason
import com.opson.libraries.core.AuthRequirement
import com.opson.libraries.core.AuthVerdict
import com.opson.libraries.core.AutoInit
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The single producer of auth-readiness (see [AuthGate]). An [AutoInit] so it
 * starts caching at app launch, before any gated navigation or authed call can
 * consult it. Fail-closed: until auth resolves (cached null) an `Account`
 * verdict is Blocked — correct for navigation, where gated routes are only
 * reachable well after resolve; launch-time callers go through [awaitVerdict].
 *
 * A stranded user (onboarded + online + genuinely session-less) gets
 * [AuthReason.FinishingSetup] and a fire-and-forget [SessionHealer.heal] kick,
 * which either revives the session or routes them to sign in again. The kick
 * never happens for unresolved, signed-out, expired, offline, or
 * pre-onboarding states.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AuthGate::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class AuthGateImpl(
    authRepository: AuthRepository,
    appCache: AppCache,
    private val appState: AppState,
    private val healer: SessionHealer,
    appScope: AppCoroutineScope,
) : AuthGate, AutoInit {

    private val authState = MutableStateFlow<AuthState?>(null)
    private val hasOnboarded = MutableStateFlow(false)

    init {
        appScope.launch {
            Catching { authRepository.observe().collect { authState.value = it } }
                .logOnFailure { "Auth-gate auth observation failed" }
        }
        appScope.launch {
            Catching {
                hasOnboarded.value = appCache.get().hasUserOnboarded
                appCache.updates.collect { hasOnboarded.value = it.hasUserOnboarded }
            }.logOnFailure { "Auth-gate onboarding observation failed" }
        }
    }

    override fun verdict(requirement: AuthRequirement): AuthVerdict {
        if (requirement == AuthRequirement.None) return AuthVerdict.Ready
        return when (val auth = authState.value) {
            is AuthState.Authenticated ->
                if (requirement == AuthRequirement.ClaimedAccount && auth.isAnonymous) {
                    AuthVerdict.Blocked(AuthReason.NeedClaimedAccount)
                } else {
                    // Even offline: we hold a (possibly stale) session, so the
                    // request proceeds and fails at transport like any ordinary
                    // network error — never through the gate.
                    AuthVerdict.Ready
                }
            else -> AuthVerdict.Blocked(blockedReason(auth as? AuthState.Unauthenticated))
        }
    }

    override suspend fun awaitVerdict(requirement: AuthRequirement): AuthVerdict {
        if (requirement == AuthRequirement.None) return AuthVerdict.Ready
        authState.first { it != null }
        return verdict(requirement)
    }

    private fun blockedReason(unauth: AuthState.Unauthenticated?): AuthReason = when {
        healable(unauth) -> {
            healer.heal("authGate")
            AuthReason.FinishingSetup
        }
        appState.isOffline.value -> AuthReason.Offline
        unauth?.reason == AuthState.Unauthenticated.Reason.SessionExpired -> AuthReason.SessionExpired
        else -> AuthReason.NeedAccount
    }

    // Stranded: onboarded, online, and genuinely session-less — resolved to
    // Unauthenticated with no deliberate sign-out and no server-killed session.
    // Unresolved (null) auth is never healable: fail closed.
    private fun healable(unauth: AuthState.Unauthenticated?): Boolean =
        unauth != null &&
            unauth.reason == AuthState.Unauthenticated.Reason.None &&
            hasOnboarded.value &&
            !appState.isOffline.value
}
