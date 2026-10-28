package com.opson.libraries.identity.impl.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs [RealSupabaseAuthGateway] over a real supabase-kt client whose every
 * request fails the way it does in airplane mode.
 */
class RealSupabaseAuthGatewayTest {

    @Test
    fun signOut_offline_stillClearsTheStoredSession() = runTest {
        val storage = RecordingSessionManager()
        val supabase = offlineSupabase(storage)
        supabase.signIn()
        val gateway = RealSupabaseAuthGateway(supabase)

        val failure = runCatching { gateway.signOut() }.exceptionOrNull()

        assertNotNull(failure, "the logout request can't reach the server, and the caller hears so")
        assertNull(supabase.auth.currentSessionOrNull(), "the in-memory session is gone")
        assertNull(gateway.currentSession())
        assertTrue(storage.deleted, "the Keychain / EncryptedSharedPreferences copy is gone")
    }

    @Test
    fun signOut_cancelledWhileTheRequestHangs_stillClearsTheStoredSession() = runTest {
        // The production store hops to the IO dispatcher, which a cancelled
        // coroutine can't do, so the cleanup has to run outside cancellation.
        val storage = RecordingSessionManager(hopsDispatcher = true)
        val requestSent = CompletableDeferred<Unit>()
        val supabase = supabase(storage) {
            requestSent.complete(Unit)
            awaitCancellation()
        }
        supabase.signIn()
        val gateway = RealSupabaseAuthGateway(supabase)

        val signingOut = launch(Dispatchers.Default) { gateway.signOut() }
        requestSent.await()
        signingOut.cancelAndJoin()

        assertTrue(storage.deleted, "the Keychain / EncryptedSharedPreferences copy is gone")
        assertNull(supabase.auth.currentSessionOrNull())
    }

    private fun offlineSupabase(sessionStore: SessionManager): SupabaseClient =
        supabase(sessionStore) { throw IOException("Unable to resolve host") }

    private fun supabase(sessionStore: SessionManager, handler: MockRequestHandler): SupabaseClient =
        createSupabaseClient(supabaseUrl = "https://test.supabase.co", supabaseKey = "test-key") {
            httpEngine = MockEngine(handler)
            install(Auth) {
                sessionManager = sessionStore
                codeVerifierCache = MemoryCodeVerifierCache()
                alwaysAutoRefresh = false
                autoLoadFromStorage = false
                enableLifecycleCallbacks = false
            }
        }

    /**
     * Imports a session once the client has finished initialising: an import
     * that races initialisation can be overwritten with "not signed in".
     */
    private suspend fun SupabaseClient.signIn() {
        auth.awaitInitialization()
        auth.importSession(storedSession(), autoRefresh = false)
        assertNotNull(auth.currentSessionOrNull())
    }

    private fun storedSession() = UserSession(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresIn = 3_600,
        tokenType = "bearer",
    )

    private class RecordingSessionManager(private val hopsDispatcher: Boolean = false) : SessionManager {
        private var session: UserSession? = null
        var deleted = false
            private set

        override suspend fun saveSession(session: UserSession) {
            this.session = session
        }

        override suspend fun loadSession(): UserSession = session ?: error("no session")

        override suspend fun deleteSession() {
            if (hopsDispatcher) withContext(Dispatchers.Default) {}
            session = null
            deleted = true
        }
    }
}
