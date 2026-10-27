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
import com.opson.libraries.identity.profile.Profile
import com.opson.libraries.identity.profile.ProfileRepository
import com.opson.libraries.identity.profile.UpdateProfileOutcome
import com.opson.libraries.networking.AccessDeniedBus
import com.opson.libraries.opson.AppCache
import com.opson.libraries.opson.AppData
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

/** The boot gate: where the app opens, and that nothing it waits on can hold it closed for good. */
class AppViewModelTest : CoroutineTest() {

    @Test
    fun aReturningUserWhoseProfileNeverResolves_stillGetsPastTheBootScreen() = runUnitTest {
        val vm = AppViewModel(
            appCache = FakeAppCache(AppData(hasUserOnboarded = true)),
            profileRepository = SilentProfileRepository,
            authRepository = SignedOutAuthRepository,
            ensureAppConfigLoaded = { Result.success(Unit) },
            accessDeniedBus = QuietAccessDeniedBus,
        )
        runCurrent()

        assertIs<TodayRoute>(vm.startDestination.value)
        assertFalse(vm.isBootComplete.value, "the gate waits for the profile first")

        advanceTimeBy(BootWaitTimeoutMillis + 1)

        assertTrue(vm.isBootComplete.value, "a profile that never arrives must not strand the user")
    }
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

/** A resolve that threw, so nothing was ever emitted. */
private object SilentProfileRepository : ProfileRepository {
    override suspend fun current(): Profile = error("never resolves")
    override fun observe(): Flow<Profile> = flow { awaitCancellation() }
    override suspend fun update(displayName: String?): UpdateProfileOutcome = error("unused")
}

private object QuietAccessDeniedBus : AccessDeniedBus {
    override fun signalDenied(denial: AccessDeniedBus.Denial) = Unit
    override val denials: Flow<AccessDeniedBus.Denial> = emptyFlow()
}

private object SignedOutAuthRepository : AuthRepository {
    private val state = MutableStateFlow<AuthState>(AuthState.Unauthenticated())
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
