package com.opson.libraries.identity.impl.auth

import com.opson.libraries.flowroutines.testing.CoroutineTest
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.request.HttpRequestBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Two-step contract:
 *  - [awaitReady] waits for supabase-kt to hydrate any persisted session. It
 *    does NOT create one (the app is session-less until onboarding).
 *  - [accessToken] is a synchronous peek of the gateway's session.
 *
 * Tests pin both halves and the refresh path.
 */
class GatewayAuthTokenProviderTest : CoroutineTest() {

    @Test
    fun accessToken_returnsGatewayToken_afterAwaitReady() = runUnitTest {
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = sampleSession(accessToken = "tok-abc"),
        )
        val provider = GatewayAuthTokenProvider(gateway, FakeSessionRejectionBus())

        provider.awaitReady()
        assertEquals("tok-abc", provider.accessToken())
    }

    @Test
    fun accessToken_returnsNull_whenNoSession() = runUnitTest {
        // No persisted session and we don't create one — the peek is null and
        // the request goes unauthed (correct for onboarding's public calls).
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.NotAuthenticated,
            session = null,
        )
        val provider = GatewayAuthTokenProvider(gateway, FakeSessionRejectionBus())

        provider.awaitReady()
        assertNull(provider.accessToken())
    }

    @Test
    fun accessToken_withoutAwaitReady_isAPurePeek() = runUnitTest {
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.NotAuthenticated,
            session = null,
        )
        val provider = GatewayAuthTokenProvider(gateway, FakeSessionRejectionBus())

        assertNull(provider.accessToken())
    }

    @Test
    fun refreshAccessToken_callsGatewayRefresh_thenReturnsNewToken() = runUnitTest {
        val initialSession = sampleSession(accessToken = "tok-old")
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = initialSession,
        )
        gateway.onRefreshSession = {
            replaceSession(initialSession.copy(accessToken = "tok-new"))
        }
        val provider = GatewayAuthTokenProvider(gateway, FakeSessionRejectionBus())

        assertEquals("tok-new", provider.refreshAccessToken(rejected = "tok-old"))
        assertEquals(1, gateway.refreshSessionCalls)
    }

    @Test
    fun refreshAccessToken_whenTheSessionAlreadyRefreshed_returnsItWithoutAnotherRefresh() = runUnitTest {
        // supabase-kt refreshes on its own before expiry, while Ktor keeps
        // sending the token it loaded first. The 401 for that stale token must
        // pick up the fresh one rather than rotate the session again, which
        // also fails the request outright when the extra refresh can't reach
        // the auth server.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = sampleSession(accessToken = "tok-fresh"),
        )
        gateway.onRefreshSession = { throw HttpRequestException("offline", HttpRequestBuilder()) }
        val provider = GatewayAuthTokenProvider(gateway, FakeSessionRejectionBus())

        assertEquals("tok-fresh", provider.refreshAccessToken(rejected = "tok-stale"))
        assertEquals(0, gateway.refreshSessionCalls)
    }

    @Test
    fun refreshAccessToken_returnsNull_whenGatewayThrows() = runUnitTest {
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = sampleSession(accessToken = "tok-old"),
        )
        gateway.onRefreshSession = { throw IllegalStateException("network down") }
        val provider = GatewayAuthTokenProvider(gateway, FakeSessionRejectionBus())

        assertNull(provider.refreshAccessToken(rejected = "tok-old"))
    }

    @Test
    fun refreshAccessToken_onNetworkFailure_returnsNull_withoutSignalingRejection() = runUnitTest {
        // A transient network failure must NOT boot the session — keep it and let
        // the request go unauthed / the offline path take over.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.Authenticated,
            session = sampleSession(accessToken = "tok-old"),
        )
        gateway.onRefreshSession = { throw HttpRequestException("connection refused", HttpRequestBuilder()) }
        val bus = FakeSessionRejectionBus()
        val provider = GatewayAuthTokenProvider(gateway, bus)

        assertNull(provider.refreshAccessToken(rejected = "tok-old"))
        assertTrue(bus.signaled.isEmpty(), "transient failures must not signal a session rejection")
    }

    @Test
    fun refreshAccessToken_withNoSession_returnsNull_withoutCallingGateway() = runUnitTest {
        // Fresh install before onboarding: no session. Ktor's bearer plugin may
        // still try a refresh on a 401 — we must NOT call the gateway (it would
        // throw "No refresh token found in current session"); return null and go
        // unauthed instead.
        val gateway = FakeSupabaseAuthGateway(
            initialStatus = AuthGatewayStatus.NotAuthenticated,
            session = null,
        )
        val provider = GatewayAuthTokenProvider(gateway, FakeSessionRejectionBus())

        assertNull(provider.refreshAccessToken(rejected = null))
        assertEquals(0, gateway.refreshSessionCalls, "must not attempt a refresh with no session")
    }

    private fun sampleSession(
        userId: String = "user-1",
        accessToken: String = "tok-$userId",
        isAnonymous: Boolean = false,
        email: String? = "user@example.com",
    ): GatewaySession = GatewaySession(
        userId = userId,
        email = email,
        accessToken = accessToken,
        isAnonymous = isAnonymous,
        isEmailConfirmed = !isAnonymous,
    )
}
