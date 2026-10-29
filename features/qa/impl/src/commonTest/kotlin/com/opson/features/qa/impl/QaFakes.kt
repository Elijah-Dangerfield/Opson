package com.opson.features.qa.impl

import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.flowroutines.SEAViewModel
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/*
 * Hand-rolled fakes for the QA menu's tests (docs/practices/testing.md).
 */

/** Who is signed in; the QA menu only reads it. */
class FakeAuthRepository(initial: AuthState = AuthState.Unauthenticated()) : AuthRepository {
    val state = MutableStateFlow(initial)

    override suspend fun current(): AuthState = state.value
    override fun observe(): Flow<AuthState> = state
    override suspend fun deleteAccount(): DeleteAccountOutcome = unused()
    override suspend fun signOut() = unused()
    override suspend fun retry(): AuthState = unused()
    override suspend fun signInWithEmail(email: String, password: String): SignInOutcome = unused()
    override suspend fun signUpWithEmail(email: String, password: String): SignUpOutcome = unused()
    override suspend fun refreshSession(): RefreshOutcome = unused()
    override suspend fun resendVerificationEmail(email: String): ResendOutcome = unused()
    override suspend fun sendPasswordResetEmail(email: String): SendResetOutcome = unused()
    override suspend fun linkOAuthIdentity(provider: OAuthProvider): LinkIdentityOutcome = unused()
    override suspend fun signInWithOAuth(provider: OAuthProvider): SignInOutcome = unused()
    override suspend fun linkEmailIdentity(email: String, password: String): LinkEmailIdentityOutcome = unused()

    private fun unused(): Nothing = error("The QA menu never calls this")
}

class FakeCalendar(private val today: LocalDate = LocalDate(2026, 10, 27)) : LocalCalendar {
    override fun timeZone(): TimeZone = TimeZone.UTC
    override fun today(): LocalDate = today
}

/** Every event [vm] sends from here on, collected in the background. */
fun <E : Any> TestScope.eventsOf(vm: SEAViewModel<*, E, *>): List<E> {
    val events = mutableListOf<E>()
    backgroundScope.launch { vm.eventFlow.collect { events += it } }
    return events
}
