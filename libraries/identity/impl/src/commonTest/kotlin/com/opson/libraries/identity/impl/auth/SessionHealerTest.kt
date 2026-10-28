package com.opson.libraries.identity.impl.auth

import com.opson.libraries.opson.AppCache
import com.opson.libraries.opson.AppData
import com.opson.libraries.opson.AppEvent
import com.opson.libraries.core.AppState
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.identity.auth.DeleteAccountOutcome
import com.opson.libraries.identity.auth.LinkEmailIdentityOutcome
import com.opson.libraries.identity.auth.LinkIdentityOutcome
import com.opson.libraries.identity.auth.OAuthProvider
import com.opson.libraries.identity.auth.RefreshOutcome
import com.opson.libraries.identity.auth.ResendOutcome
import com.opson.libraries.identity.auth.SendResetOutcome
import com.opson.libraries.identity.auth.SignInOutcome
import com.opson.libraries.identity.auth.SignUpOutcome
import com.opson.libraries.identity.profile.Profile
import com.opson.libraries.identity.profile.ProfileRepository
import com.opson.libraries.identity.profile.UpdateProfileOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Pins the [SessionHealer] eligibility matrix: which trigger states end with
 * the session declared unrecoverable (so the person signs in again) and which
 * short-circuit. Each test maps to one decision branch.
 */
class SessionHealerTest : CoroutineTest() {

    @Test
    fun skip_whenAlreadyAuthenticated() = runUnitTest {
        val auth = FakeAuth(current = authed())
        val healer = build(auth)

        healer.onForeground(AppEvent.OnForeground(isColdBoot = false))
        advanceUntilIdle()

        assertEquals(emptyList(), auth.markedUnrecoverable, "a live session is left alone")
    }

    @Test
    fun skip_whenRetryRecoversSession() = runUnitTest {
        val auth = FakeAuth(current = AuthState.Unauthenticated(), retryResult = authed())
        val healer = build(auth)

        healer.onConnectivityRegained(AppEvent.ConnectivityRegained)
        advanceUntilIdle()

        assertEquals(emptyList(), auth.markedUnrecoverable, "a recovered session is left alone")
    }

    @Test
    fun skip_whenSessionExpired() = runUnitTest {
        val auth = FakeAuth(
            current = AuthState.Unauthenticated(),
            retryResult = AuthState.Unauthenticated(reason = AuthState.Unauthenticated.Reason.SessionExpired),
        )
        val healer = build(auth, onboarded = true)

        healer.onForeground(AppEvent.OnForeground(isColdBoot = false))
        advanceUntilIdle()

        assertEquals(emptyList(), auth.markedUnrecoverable, "already declared, and the app is routing")
    }

    @Test
    fun skip_whenTheStoredSessionIsOnlyAwaitingRefresh() = runUnitTest {
        // Offline at launch with an expired token: supabase-kt keeps the session
        // and retries. A cached profile must not read as a lost account.
        val unconfirmed = AuthState.Unauthenticated(cause = SessionUnconfirmed())
        val auth = FakeAuth(current = unconfirmed, retryResult = unconfirmed)
        val healer = build(auth, onboarded = true, profile = cachedAccount(isAnonymous = true))

        healer.onColdBoot(AppEvent.ColdBoot)
        advanceUntilIdle()

        assertEquals(emptyList(), auth.markedUnrecoverable)
    }

    @Test
    fun skip_whenSignedOut() = runUnitTest {
        val auth = FakeAuth(
            current = AuthState.Unauthenticated(),
            retryResult = AuthState.Unauthenticated(reason = AuthState.Unauthenticated.Reason.SignedOut),
        )
        val healer = build(auth, onboarded = true)

        healer.onForeground(AppEvent.OnForeground(isColdBoot = false))
        advanceUntilIdle()

        assertEquals(emptyList(), auth.markedUnrecoverable, "a deliberate sign-out is not a lost session")
    }

    @Test
    fun skip_withoutRetrying_whenTheLastStateIsASignOut() = runUnitTest {
        // A sign-out that couldn't reach the auth server used to leave the
        // session behind, and retry() revived it: the person was signed back in.
        val signedOut = AuthState.Unauthenticated(reason = AuthState.Unauthenticated.Reason.SignedOut)
        val auth = FakeAuth(current = signedOut, retryResult = authed())
        val healer = build(auth, onboarded = true)

        healer.onConnectivityRegained(AppEvent.ConnectivityRegained)
        advanceUntilIdle()

        assertEquals(0, auth.retries, "a deliberate sign-out is never re-resolved")
        assertEquals(emptyList(), auth.markedUnrecoverable)
    }

    @Test
    fun skip_whenNotOnboarded() = runUnitTest {
        val auth = FakeAuth(current = AuthState.Unauthenticated(), retryResult = AuthState.Unauthenticated())
        val healer = build(auth, onboarded = false)

        healer.onColdBoot(AppEvent.ColdBoot)
        advanceUntilIdle()

        assertEquals(emptyList(), auth.markedUnrecoverable, "onboarding's sign-in step is where a session comes from")
    }

    @Test
    fun skip_whenOffline() = runUnitTest {
        val auth = FakeAuth(current = AuthState.Unauthenticated(), retryResult = AuthState.Unauthenticated())
        val healer = build(auth, onboarded = true, offline = true)

        healer.onConnectivityRegained(AppEvent.ConnectivityRegained)
        advanceUntilIdle()

        assertEquals(emptyList(), auth.markedUnrecoverable, "offline looks like no session; decide once online")
    }

    @Test
    fun sendsToSignIn_whenStranded_onboardedOnlineSessionless() = runUnitTest {
        // A guest from an older build whose account never got created. Nothing
        // mints guests any more, so the way in is signing in.
        val auth = FakeAuth(current = AuthState.Unauthenticated(), retryResult = AuthState.Unauthenticated())
        val healer = build(auth, onboarded = true, profile = Profile.Fallback(id = "local-1"))

        healer.onForeground(AppEvent.OnForeground(isColdBoot = false))
        advanceUntilIdle()

        assertEquals(listOf(false), auth.markedUnrecoverable)
    }

    @Test
    fun coldBootForeground_isNotDoubleHandled() = runUnitTest {
        val auth = FakeAuth(current = AuthState.Unauthenticated(), retryResult = AuthState.Unauthenticated())
        val healer = build(auth, onboarded = true)

        // A cold-boot foreground is owned by onColdBoot; the foreground handler
        // must skip it so launch doesn't run the decision twice.
        healer.onForeground(AppEvent.OnForeground(isColdBoot = true))
        advanceUntilIdle()

        assertEquals(emptyList(), auth.markedUnrecoverable, "cold-boot foreground is skipped (onColdBoot owns it)")
    }

    @Test
    fun concurrentHeals_runTheDecisionOnce() = runUnitTest {
        val gate = CompletableDeferred<Unit>()
        val auth = FakeAuth(
            current = AuthState.Unauthenticated(),
            retryResult = AuthState.Unauthenticated(),
            gate = gate,
        )
        val healer = build(auth, onboarded = true)

        healer.heal("first")
        advanceUntilIdle() // first heal is parked inside markSessionUnrecoverable
        healer.heal("second")
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, auth.markedUnrecoverable.size, "overlapping triggers must not run the decision twice")
    }

    @Test
    fun heal_runsAgainAfterThePreviousRunCompletes() = runUnitTest {
        val auth = FakeAuth(current = AuthState.Unauthenticated(), retryResult = AuthState.Unauthenticated())
        val healer = build(auth, onboarded = true)

        healer.heal("first")
        advanceUntilIdle()
        healer.heal("second")
        advanceUntilIdle()

        assertEquals(2, auth.markedUnrecoverable.size, "single-flight is skip-if-running, not once-ever")
    }

    @Test
    fun cachedAccount_isDeclaredUnrecoverable_carryingItsAnonymity() = runUnitTest {
        // AUTH-19: a cached server profile with no revivable session means this
        // device HAS a real account whose token storage failed (the TestFlight
        // upgrade case). The session-expired screen picks its copy from whether
        // that account was a guest.
        val auth = FakeAuth(current = AuthState.Unauthenticated(), retryResult = AuthState.Unauthenticated())
        val healer = build(auth, onboarded = true, profile = cachedAccount(isAnonymous = true))

        healer.onForeground(AppEvent.OnForeground(isColdBoot = false))
        advanceUntilIdle()

        assertEquals(
            listOf(true),
            auth.markedUnrecoverable,
            "recovery is declared once, carrying the stranded account's anonymity",
        )
    }

    @Test
    fun aRelaunchAfterASessionExpiry_sendsTheHeldAccountToSignIn_withItsGuestCopy() = runUnitTest {
        // The profile cache is gone (cleared on expiry) but the account's meals
        // are still on the device, held for it to sign back in.
        val expiredAccounts = ExpiredAccountStore(TestCacheFactory())
        expiredAccounts.hold(ExpiredAccount(userId = "guest-1", wasAnonymous = true))
        val auth = FakeAuth(current = AuthState.Unauthenticated(), retryResult = AuthState.Unauthenticated())
        val healer = build(auth, onboarded = true, offline = true, expiredAccounts = expiredAccounts)

        healer.onColdBoot(AppEvent.ColdBoot)
        advanceUntilIdle()

        assertEquals(listOf(true), auth.markedUnrecoverable, "routed to sign in, even offline, with the guest copy")
    }

    private fun build(
        auth: AuthRepository,
        onboarded: Boolean = true,
        offline: Boolean = false,
        profile: Profile = Profile.Fallback(id = "local"),
        expiredAccounts: ExpiredAccountStore = ExpiredAccountStore(TestCacheFactory()),
    ) = SessionHealer(
        authRepositoryProvider = { auth },
        profileRepositoryProvider = { FakeProfile(profile) },
        expiredAccounts = expiredAccounts,
        appCache = FakeAppCache(onboarded),
        appState = FakeAppState(MutableStateFlow(offline)),
        appScope = AppCoroutineScope(dispatchers),
    )

    private fun authed() = AuthState.Authenticated(userId = "u1", isAnonymous = true, email = null)

    private fun cachedAccount(isAnonymous: Boolean) = Profile.Authenticated(
        id = "u1",
        displayName = "Foxy",
        email = null,
        isAnonymous = isAnonymous,
        createdAt = Instant.fromEpochMilliseconds(0),
    )

    private class FakeAppState(
        override val isOffline: MutableStateFlow<Boolean>,
    ) : AppState

    private class FakeAppCache(onboarded: Boolean) : AppCache {
        private val state = MutableStateFlow(AppData(hasUserOnboarded = onboarded))
        override val updates: Flow<AppData> = state
        override suspend fun get(): AppData = state.value
        override suspend fun set(value: AppData) { state.value = value }
        override suspend fun clear() { state.value = AppData() }
    }

    private class FakeProfile(private val profile: Profile) : ProfileRepository {
        override suspend fun current(): Profile = profile
        override fun observe(): Flow<Profile> = emptyFlow()
        override suspend fun update(
            displayName: String?,
        ): UpdateProfileOutcome = error("unused")
    }

    private class FakeAuth(
        private val current: AuthState,
        private val retryResult: AuthState = AuthState.Unauthenticated(),
        private val gate: CompletableDeferred<Unit>? = null,
    ) : AuthRepository {
        val markedUnrecoverable = mutableListOf<Boolean>()
        var retries = 0
            private set
        override suspend fun current(): AuthState = current
        override suspend fun retry(): AuthState {
            retries++
            return retryResult
        }
        override suspend fun markSessionUnrecoverable(wasAnonymous: Boolean) {
            markedUnrecoverable += wasAnonymous
            gate?.await()
        }
        override fun observe(): Flow<AuthState> = emptyFlow()
        override suspend fun signInWithEmail(email: String, password: String): SignInOutcome = error("unused")
        override suspend fun signUpWithEmail(email: String, password: String): SignUpOutcome = error("unused")
        override suspend fun refreshSession(): RefreshOutcome = error("unused")
        override suspend fun resendVerificationEmail(email: String): ResendOutcome = error("unused")
        override suspend fun sendPasswordResetEmail(email: String): SendResetOutcome = error("unused")
        override suspend fun signOut() = error("unused")
        override suspend fun deleteAccount(): DeleteAccountOutcome = error("unused")
        override suspend fun linkOAuthIdentity(provider: OAuthProvider): LinkIdentityOutcome = error("unused")
        override suspend fun signInWithOAuth(provider: OAuthProvider): SignInOutcome = error("unused")
        override suspend fun linkEmailIdentity(email: String, password: String): LinkEmailIdentityOutcome = error("unused")
    }
}
