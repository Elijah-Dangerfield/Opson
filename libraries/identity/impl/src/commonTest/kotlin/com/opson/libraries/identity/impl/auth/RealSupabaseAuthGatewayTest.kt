package com.opson.libraries.identity.impl.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
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
        supabase.auth.importSession(storedSession(), autoRefresh = false)
        assertNotNull(supabase.auth.currentSessionOrNull())
        val gateway = RealSupabaseAuthGateway(supabase)

        val failure = runCatching { gateway.signOut() }.exceptionOrNull()

        assertNotNull(failure, "the logout request can't reach the server, and the caller hears so")
        assertNull(supabase.auth.currentSessionOrNull(), "the in-memory session is gone")
        assertNull(gateway.currentSession())
        assertTrue(storage.deleted, "the Keychain / EncryptedSharedPreferences copy is gone")
    }

    private fun offlineSupabase(sessionStore: SessionManager): SupabaseClient =
        createSupabaseClient(supabaseUrl = "https://test.supabase.co", supabaseKey = "test-key") {
            httpEngine = MockEngine { throw IOException("Unable to resolve host") }
            install(Auth) {
                sessionManager = sessionStore
                codeVerifierCache = MemoryCodeVerifierCache()
                alwaysAutoRefresh = false
                autoLoadFromStorage = false
                enableLifecycleCallbacks = false
            }
        }

    private fun storedSession() = UserSession(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresIn = 3_600,
        tokenType = "bearer",
    )

    private class RecordingSessionManager : SessionManager {
        private var session: UserSession? = null
        var deleted = false
            private set

        override suspend fun saveSession(session: UserSession) {
            this.session = session
        }

        override suspend fun loadSession(): UserSession = session ?: error("no session")

        override suspend fun deleteSession() {
            session = null
            deleted = true
        }
    }
}
