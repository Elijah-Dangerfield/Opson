package com.opson.libraries.foods.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.networking.InternalNetworkingApi
import com.opson.libraries.networking.NetworkClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.errors.IOException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How a search goes over the wire: what it sends, and when it asks twice. */
@OptIn(InternalNetworkingApi::class)
class HttpFoodApiSearchTest : CoroutineTest() {

    private class FakeNetworkClient(handler: MockRequestHandler) : NetworkClient {
        private val http = HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(HttpTimeout) { requestTimeoutMillis = 30_000 }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        override val client: HttpClient get() = http
        override val authenticatedClient: HttpClient get() = http
        override suspend fun awaitAuthReady() = Unit
    }

    private val urls = mutableListOf<String>()

    private fun api(vararg answers: () -> HttpStatusCode) = HttpFoodApi(
        FakeNetworkClient { request ->
            urls += request.url.encodedPathAndQuery
            val status = answers[(urls.size - 1).coerceAtMost(answers.size - 1)]()
            respond(
                if (status == HttpStatusCode.OK) """{"query":"rice","foods":[]}""" else """{"error":{"code":"busy","message":"busy"}}""",
                status,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        },
    )

    @Test
    fun anUnfilteredSearchIsThe01Call() = runUnitTest {
        api({ HttpStatusCode.OK }).search("rice", 25)
        assertEquals(listOf("/v1/foods/search?q=rice&limit=25"), urls)
    }

    @Test
    fun aBusyServerIsNotAskedAgain() = runUnitTest {
        val result = api({ HttpStatusCode.ServiceUnavailable }).search("rice", 25)
        assertTrue(result.isFailure)
        assertEquals(1, urls.size, "the catalog falls back to offline results; the next keystroke asks again")
    }

    @Test
    fun aDroppedConnectionIsRetriedOnce() = runUnitTest {
        val result = api({ throw IOException("reset") }, { HttpStatusCode.OK }).search("rice", 25)
        assertTrue(result.isSuccess)
        assertEquals(2, urls.size)
    }

    @Test
    fun aConnectionThatKeepsDroppingGivesUpAfterTwoTries() = runUnitTest {
        val result = api({ throw IOException("reset") }).search("rice", 25)
        assertTrue(result.isFailure)
        assertEquals(2, urls.size)
    }
}
