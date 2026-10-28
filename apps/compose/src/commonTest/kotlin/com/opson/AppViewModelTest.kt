package com.opson

import com.opson.features.today.TodayRoute
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
import com.opson.libraries.networking.AccessDeniedBus
import com.opson.libraries.opson.AppCache
import com.opson.libraries.opson.AppData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

/** The boot gate: where the app opens, and that nothing it waits on can hold it closed for good. */
class AppViewModelTest : CoroutineTest() {

    @Test
    fun aReturningUser_opensOnTodayAsSoonAsTheConfigIsIn() = runUnitTest {
        // The profile is a /v1/me round trip and nothing on Today reads it. On one bar of signal,
        // waiting for it held every cold start on the loading screen for up to the full cap.
        val vm = vm(onboarded = true, auth = FakeAuthRepository(AuthState.Unauthenticated()))
        runCurrent()

        assertIs<TodayRoute>(vm.startDestination.value)
        assertTrue(vm.isBootComplete.value)
    }

    @Test
    fun aConfigLoadThatNeverFinishes_stillLetsTheUserPastTheBootScreen() = runUnitTest {
        val vm = AppViewModel(
            appCache = FakeAppCache(AppData(hasUserOnboarded = true)),
            authRepository = FakeAuthRepository(AuthState.Unauthenticated()),
            ensureAppConfigLoaded = { awaitCancellation() },
            accessDeniedBus = QuietAccessDeniedBus,
        )
        runCurrent()

        assertFalse(vm.isBootComplete.value, "the gate waits for the config first")

        advanceTimeBy(BootWaitTimeoutMillis + 1)

        assertTrue(vm.isBootComplete.value, "a config that never arrives must not strand the user")
    }

    @Test
    fun anOnboardedGuest_isSentToSignIn_once() = runUnitTest {
        // Someone who started when accounts were optional. Accounts are required now.
        val auth = FakeAuthRepository(guest)
        val vm = vm(onboarded = true, auth = auth)
        val sent = mutableListOf<Unit>()
        backgroundScope.launch { vm.signInRequired.collect { sent += it } }
        runCurrent()

        auth.state.value = guest.copy(email = "still-a-guest")
        runCurrent()

        assertEquals(1, sent.size)
    }

    @Test
    fun aGuestStillInOnboarding_orASignedInAccount_isLeftAlone() = runUnitTest {
        listOf(
            vm(onboarded = false, auth = FakeAuthRepository(guest)),
            vm(onboarded = true, auth = FakeAuthRepository(guest.copy(isAnonymous = false))),
        ).forEach { vm ->
            val sent = mutableListOf<Unit>()
            backgroundScope.launch { vm.signInRequired.collect { sent += it } }
            runCurrent()

            assertTrue(sent.isEmpty())
        }
    }

    private val guest = AuthState.Authenticated(userId = "guest-1", isAnonymous = true, email = null)

    private fun vm(onboarded: Boolean, auth: AuthRepository) = AppViewModel(
        appCache = FakeAppCache(AppData(hasUserOnboarded = onboarded)),
        authRepository = auth,
        ensureAppConfigLoaded = { Result.success(Unit) },
        accessDeniedBus = QuietAccessDeniedBus,
    )
}

private class FakeAppCache(initial: AppData) : AppCache {
    private val state = MutableStateFlow(initial)
    override val updates: Flow<AppData> = state
    override suspend fun get(): AppData = state.value
    override suspend fun set(value: AppData) {
        state.value = value
    }
    override suspend fun clear() {
        state.value = AppData()
    }
}

private object QuietAccessDeniedBus : AccessDeniedBus {
    override fun signalDenied(denial: AccessDeniedBus.Denial) = Unit
    override val denials: Flow<AccessDeniedBus.Denial> = emptyFlow()
}

private class FakeAuthRepository(initial: AuthState) : AuthRepository {
    val state = MutableStateFlow(initial)
    override suspend fun current(): AuthState = state.value
    override fun observe(): Flow<AuthState> = state
    override suspend fun retry(): AuthState = state.value
    override suspend fun signInWithEmail(email: String, password: String): SignInOutcome = error("unused")
    override suspend fun signUpWithEmail(email: String, password: String): SignUpOutcome = error("unused")
    override suspend fun refreshSession(): RefreshOutcome = error("unused")
    override suspend fun resendVerificationEmail(email: String): ResendOutcome = error("unused")
    override suspend fun sendPasswordResetEmail(email: String): SendResetOutcome = error("unused")
    override suspend fun signOut() = Unit
    override suspend fun deleteAccount(): DeleteAccountOutcome = error("unused")
    override suspend fun linkOAuthIdentity(provider: OAuthProvider): LinkIdentityOutcome = error("unused")
    override suspend fun signInWithOAuth(provider: OAuthProvider): SignInOutcome = error("unused")
    override suspend fun linkEmailIdentity(email: String, password: String): LinkEmailIdentityOutcome =
        error("unused")
}
