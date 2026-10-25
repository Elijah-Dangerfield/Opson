package com.opson.libraries.networking.impl

import com.opson.libraries.networking.AccessDeniedBus
import com.opson.libraries.networking.ClientHeaders
import com.opson.libraries.networking.ClientHeadersProvider
import com.opson.libraries.networking.NetworkConfig
import com.opson.libraries.networking.NetworkReachability
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Covers the blank-base-URL guard.
 *
 * The bug it prevents is not a crash but a lie: Ktor resolves a relative path
 * with no base URL against `http://localhost`, the connection is refused, and
 * the witnessed-reachability validator concludes the device is offline. A
 * project that has not set its base URL yet then presents as offline with full
 * signal, and the search starts in the connectivity code.
 *
 * NOT covered here: anything about which base URL is correct. This is only
 * about the blank case being distinguishable from a network failure.
 */
class UnresolvedRelativeRequestTest {

    private class FakeReachability : NetworkReachability {
        var unreachableReports = 0
        var reachableReports = 0
        override val isReachable: StateFlow<Boolean> = MutableStateFlow(true)
        override fun reportReachable() { reachableReports++ }
        override fun reportUnreachable() { unreachableReports++ }
    }

    private class FakeHeaders : ClientHeadersProvider {
        override fun current() = ClientHeaders(
            platform = "android",
            appVersion = "1.0.0",
            buildNumber = "1",
            acceptLanguage = "en-US",
            countryCode = null,
            installId = null,
            sessionId = "test-session",
        )
    }

    private class NoopAccessDeniedBus : AccessDeniedBus {
        override val denials = emptyFlow<AccessDeniedBus.Denial>()
        override fun signalDenied(denial: AccessDeniedBus.Denial) = Unit
    }

    private fun client(baseUrl: String, reachability: NetworkReachability): HttpClient {
        val engine = MockEngine {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return HttpClient(engine) {
            applyCommonConfig(
                config = object : NetworkConfig { override val baseUrl = baseUrl },
                headersProvider = FakeHeaders(),
                reachability = reachability,
                accessDeniedBus = NoopAccessDeniedBus(),
            )
        }
    }

    @Test
    fun blankBaseUrl_relativePath_failsWithAnActionableError() = runTest {
        val reachability = FakeReachability()
        val failure = assertFailsWith<UnresolvedRelativeRequestException> {
            client(baseUrl = "", reachability).get("/v1/me")
        }
        assertTrue(
            failure.message.orEmpty().contains("NetworkConfig"),
            "the message has to name the thing to set, not just report a failure: ${failure.message}",
        )
    }

    @Test
    fun blankBaseUrl_relativePath_doesNotReportOffline() = runTest {
        val reachability = FakeReachability()
        assertFailsWith<UnresolvedRelativeRequestException> {
            client(baseUrl = "", reachability).get("/v1/me")
        }
        assertEquals(
            0,
            reachability.unreachableReports,
            "the request never left the device, so it says nothing about the network",
        )
    }

    @Test
    fun blankBaseUrl_absoluteUrl_stillWorks() = runTest {
        // A client-only app has no base URL of its own and reaches Supabase and
        // third parties by absolute URL. The guard must not touch those.
        val reachability = FakeReachability()
        val response = client(baseUrl = "", reachability).get("https://example.com/v1/thing")
        assertEquals("{}", response.bodyAsText())
        assertEquals(1, reachability.reachableReports)
    }

    @Test
    fun configuredBaseUrl_relativePath_stillWorks() = runTest {
        val reachability = FakeReachability()
        val response = client(baseUrl = "https://api.example.com", reachability).get("/v1/me")
        assertEquals("{}", response.bodyAsText())
        assertEquals(1, reachability.reachableReports)
    }
}
