package com.opson.libraries.networking.impl

import com.opson.libraries.networking.AccessDeniedBus
import com.opson.libraries.networking.ClientHeaders
import com.opson.libraries.networking.ClientHeadersProvider
import com.opson.libraries.networking.NetworkConfig
import com.opson.libraries.networking.NetworkReachability
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the engine is told to wait for. A connection that never opens (a dead
 * Wi-Fi the OS still reports as up) used to hold a request for the whole
 * 30 s budget before the offline state or an error could show.
 */
class CommonConfigTimeoutTest {

    @Test
    fun aConnectionThatWillNotOpen_isGivenUpOnBeforeTheRequestBudget() = runTest {
        var connect: Long? = null
        var request: Long? = null
        val engine = MockEngine { call ->
            val timeouts = call.getCapabilityOrNull(HttpTimeoutCapability)
            connect = timeouts?.connectTimeoutMillis
            request = timeouts?.requestTimeoutMillis
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = HttpClient(engine) {
            applyCommonConfig(
                config = object : NetworkConfig { override val baseUrl = "https://api.example" },
                headersProvider = FixedHeaders,
                reachability = AlwaysReachable,
                accessDeniedBus = NoDenials,
            )
        }

        client.get("/v1/ping")

        assertEquals(10_000L, connect)
        assertEquals(30_000L, request)
    }

    private object FixedHeaders : ClientHeadersProvider {
        override fun current() = ClientHeaders("android", "1.0.0", "1", "en-US", null, null, "session")
    }

    private object AlwaysReachable : NetworkReachability {
        override val isReachable: StateFlow<Boolean> = MutableStateFlow(true)
        override fun reportReachable() = Unit
        override fun reportUnreachable() = Unit
    }

    private object NoDenials : AccessDeniedBus {
        override val denials = emptyFlow<AccessDeniedBus.Denial>()
        override fun signalDenied(denial: AccessDeniedBus.Denial) = Unit
    }
}
