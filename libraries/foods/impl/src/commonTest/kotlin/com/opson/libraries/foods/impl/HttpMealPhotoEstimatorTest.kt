package com.opson.libraries.foods.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.PhotoEstimate
import com.opson.libraries.networking.InternalNetworkingApi
import com.opson.libraries.networking.NetworkClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(InternalNetworkingApi::class, ExperimentalEncodingApi::class)
class HttpMealPhotoEstimatorTest : CoroutineTest() {

    private class FakeNetworkClient(handler: MockRequestHandler) : NetworkClient {
        private val http = HttpClient(MockEngine(handler)) {
            expectSuccess = true
            // As the app's client: every timeout at the 30 s default.
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        override val client: HttpClient get() = http
        override val authenticatedClient: HttpClient get() = http
        override suspend fun awaitAuthReady() = Unit
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun estimator(status: HttpStatusCode, body: String = "{}", seen: MutableList<String>? = null) =
        HttpMealPhotoEstimator(
            FakeNetworkClient { request ->
                seen?.add(request.url.encodedPath)
                seen?.add((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )

    @Test
    fun aSuccessIsTheDescribeRowShape() = runUnitTest {
        val seen = mutableListOf<String>()
        val body = """{"items":[{"text":"1 cup broccoli","name":"Broccoli, steamed","foodId":"f1","quantity":1.0,
            "unit":"cup","amountText":"1 cup","portionLabel":"1 cup","grams":156.0,"kcal":55.0,"confidence":"high"}]}"""
        val result = estimator(HttpStatusCode.OK, body, seen).estimate(byteArrayOf(1, 2, 3), note = "  no butter ")

        val items = assertIs<PhotoEstimate.Items>(result).items
        assertEquals("Broccoli, steamed", items.single().name)
        assertEquals(156.0, items.single().grams)
        assertEquals("/v1/describe/photo", seen[0])
        val sent = json.decodeFromString(PhotoEstimateRequest.serializer(), seen[1])
        assertEquals(Base64.encode(byteArrayOf(1, 2, 3)), sent.image)
        assertEquals("no butter", sent.note)
    }

    @Test
    fun aBlankNoteIsNotSent() = runUnitTest {
        val seen = mutableListOf<String>()
        estimator(HttpStatusCode.OK, """{"items":[]}""", seen).estimate(byteArrayOf(1), note = "   ")
        assertEquals(null, json.decodeFromString(PhotoEstimateRequest.serializer(), seen[1]).note)
    }

    @Test
    fun anEmptyListIsStillAnAnswer() = runUnitTest {
        val result = estimator(HttpStatusCode.OK, """{"items":[]}""").estimate(byteArrayOf(1), null)
        assertEquals(PhotoEstimate.Items(emptyList()), result)
    }

    @Test
    fun statusesMapToCalmOutcomes() = runUnitTest {
        assertEquals(PhotoEstimate.Unavailable, estimator(HttpStatusCode.ServiceUnavailable).estimate(byteArrayOf(1), null))
        assertEquals(PhotoEstimate.Unavailable, estimator(HttpStatusCode.NotFound).estimate(byteArrayOf(1), null))
        assertEquals(PhotoEstimate.TooMany, estimator(HttpStatusCode.TooManyRequests).estimate(byteArrayOf(1), null))
        assertEquals(PhotoEstimate.Failed(offline = false), estimator(HttpStatusCode.BadGateway).estimate(byteArrayOf(1), null))
        assertEquals(PhotoEstimate.Failed(offline = false), estimator(HttpStatusCode.PayloadTooLarge).estimate(byteArrayOf(1), null))
    }

    @Test
    fun theWaitForTheModelOutlastsTheClientsDefaultTimeouts() = runUnitTest {
        var timeouts: HttpTimeoutConfig? = null
        HttpMealPhotoEstimator(
            FakeNetworkClient { request ->
                timeouts = request.getCapabilityOrNull(HttpTimeoutCapability)
                respond("""{"items":[]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ).estimate(byteArrayOf(1), null)

        // Nothing comes back until the model answers, so a 30 s socket timeout would cut it off.
        assertEquals(HttpMealPhotoEstimator.TIMEOUT_MS, timeouts?.requestTimeoutMillis)
        assertEquals(HttpMealPhotoEstimator.TIMEOUT_MS, timeouts?.socketTimeoutMillis)
    }

    @Test
    fun aTransportFailureIsFailed() = runUnitTest {
        val estimator = HttpMealPhotoEstimator(FakeNetworkClient { throw IllegalStateException("boom") })
        assertIs<PhotoEstimate.Failed>(estimator.estimate(byteArrayOf(1), null))
    }
}
