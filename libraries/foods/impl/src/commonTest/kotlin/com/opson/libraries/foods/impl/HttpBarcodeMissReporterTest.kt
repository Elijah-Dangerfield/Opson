package com.opson.libraries.foods.impl

import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.networking.InternalNetworkingApi
import com.opson.libraries.networking.NetworkClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(InternalNetworkingApi::class)
class HttpBarcodeMissReporterTest : CoroutineTest() {

    private class FakeNetworkClient(handler: MockRequestHandler) : NetworkClient {
        private val http = HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(ContentNegotiation) { json() }
        }
        override val client: HttpClient get() = http
        override val authenticatedClient: HttpClient get() = http
        override suspend fun awaitAuthReady() = Unit
    }

    /** "Search instead" right after a miss closes the scanner, which cancels whoever asked for the report. */
    @Test
    fun cancellingTheCallerDoesNotCancelTheReport() = runUnitTest {
        val sent = CompletableDeferred<Unit>()
        val serverAnswers = CompletableDeferred<Unit>()
        val delivered = CompletableDeferred<Boolean>()
        val network = FakeNetworkClient {
            sent.complete(Unit)
            try {
                serverAnswers.await()
                delivered.complete(true)
            } catch (e: CancellationException) {
                delivered.complete(false)
                throw e
            }
            respond("", HttpStatusCode.Created)
        }
        val appScope = AppCoroutineScope(dispatchers)
        val reporter = HttpBarcodeMissReporter(network, appScope)

        val scanner = launch { reporter.report("4006381333931") }
        sent.await()
        scanner.cancelAndJoin()
        serverAnswers.complete(Unit)

        assertTrue(delivered.await(), "the report outlives the screen that asked for it")
        appScope.cancel()
    }
}
