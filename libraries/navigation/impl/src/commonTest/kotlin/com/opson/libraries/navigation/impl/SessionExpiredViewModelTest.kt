package com.opson.libraries.navigation.impl

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
import com.opson.libraries.opson.AppCache
import com.opson.libraries.opson.AppData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SessionExpiredViewModelTest : CoroutineTest() {

    private val auth = FakeAuthRepository()
    private val cache = FakeAppCache(AppData(hasUserOnboarded = true))

    // Built inside each test: viewModelScope binds Main when the view model is made, and the test
    // dispatcher is only installed as Main before each test, after properties are initialised.
    private val viewModel by lazy { SessionExpiredViewModel(auth, cache) }

    @Test
    fun signingInAgainSignsOutAndGoesToSignIn() = runUnitTest {
        viewModel.takeAction(SessionExpiredViewModel.Action.SignInAgain)

        assertEquals(SessionExpiredViewModel.Event.NavigateToSignIn, viewModel.eventFlow.first())
        assertEquals(1, auth.signOuts)
        assertFalse(cache.get().hasUserOnboarded)
        assertFalse(viewModel.state.working)
    }
}

private class FakeAuthRepository : AuthRepository {
    var signOuts = 0

    override suspend fun signOut() {
        signOuts++
    }

    override suspend fun current(): AuthState = error("not used")
    override fun observe(): Flow<AuthState> = error("not used")
    override suspend fun retry(): AuthState = error("not used")
    override suspend fun signInWithEmail(email: String, password: String): SignInOutcome = error("not used")
    override suspend fun signUpWithEmail(email: String, password: String): SignUpOutcome = error("not used")
    override suspend fun refreshSession(): RefreshOutcome = error("not used")
    override suspend fun resendVerificationEmail(email: String): ResendOutcome = error("not used")
    override suspend fun sendPasswordResetEmail(email: String): SendResetOutcome = error("not used")
    override suspend fun deleteAccount(): DeleteAccountOutcome = error("not used")
    override suspend fun linkOAuthIdentity(provider: OAuthProvider): LinkIdentityOutcome = error("not used")
    override suspend fun signInWithOAuth(provider: OAuthProvider): SignInOutcome = error("not used")
    override suspend fun linkEmailIdentity(email: String, password: String): LinkEmailIdentityOutcome = error("not used")
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
