package com.opson.server.routes

import com.opson.server.domain.BarcodeMiss
import com.opson.server.domain.BarcodeMissRepository
import com.opson.server.domain.UserId
import com.opson.server.http.ClientContext
import com.opson.server.plugins.ProblemResponse
import com.opson.server.plugins.installAuthentication
import com.opson.server.plugins.installRateLimits
import com.opson.server.plugins.installSerialization
import com.opson.server.plugins.installStatusPages
import com.opson.server.testing.TestJwt
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BarcodeMissRoutesTest {

    private val userId = UserId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
    private val missId = UUID.fromString("44444444-4444-4444-4444-444444444444")

    private class FakeMisses : BarcodeMissRepository {
        val recorded = mutableListOf<BarcodeMiss>()
        override suspend fun record(miss: BarcodeMiss) {
            recorded += miss
        }
    }

    private suspend fun withApp(misses: FakeMisses = FakeMisses(), block: suspend (HttpClient) -> Unit) {
        testApplication {
            application {
                installSerialization()
                installRateLimits()
                installStatusPages()
                installAuthentication(TestJwt.verification)
                routing { barcodeMissRoutes(misses) }
            }
            block(createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } })
        }
    }

    private fun HttpRequestBuilder.auth() = header(HttpHeaders.Authorization, TestJwt.bearer(userId))

    private fun HttpRequestBuilder.miss(id: String? = missId.toString(), barcode: String? = "049000028911") {
        contentType(ContentType.Application.Json)
        setBody(BarcodeMissRequest(id = id, barcode = barcode))
    }

    @Test
    fun requiresAuth() = runTest {
        withApp { client ->
            assertEquals(HttpStatusCode.Unauthorized, client.post("/v1/foods/barcode-misses") { miss() }.status)
        }
    }

    @Test
    fun recordsTheMissWithTheHeadersContextAndNoUser() = runTest {
        val misses = FakeMisses()
        withApp(misses) { client ->
            val response = client.post("/v1/foods/barcode-misses") {
                auth()
                miss()
                header(ClientContext.HEADER_PLATFORM, "iOS")
                header(ClientContext.HEADER_APP_VERSION, "0.2.0")
                header(ClientContext.HEADER_COUNTRY_CODE, "gb")
                header(ClientContext.HEADER_INSTALL_ID, "55555555-5555-5555-5555-555555555555")
            }
            assertEquals(HttpStatusCode.NoContent, response.status)
        }
        val recorded = misses.recorded.single()
        assertEquals(
            BarcodeMiss(
                id = missId,
                barcode = "049000028911",
                gtin = "00049000028911",
                country = "GB",
                platform = "ios",
                appVersion = "0.2.0",
            ),
            recorded,
        )
        assertTrue(userId.value.toString() !in recorded.toString(), "a miss never carries the caller")
    }

    @Test
    fun anOldClientWithoutHeadersStillRecords() = runTest {
        val misses = FakeMisses()
        withApp(misses) { client ->
            assertEquals(HttpStatusCode.NoContent, client.post("/v1/foods/barcode-misses") { auth(); miss() }.status)
        }
        val recorded = misses.recorded.single()
        assertEquals("other", recorded.platform)
        assertEquals(null, recorded.country)
        assertEquals(null, recorded.appVersion)
    }

    @Test
    fun aBadIdOrBarcodeIsA400() = runTest {
        val misses = FakeMisses()
        withApp(misses) { client ->
            val badId = client.post("/v1/foods/barcode-misses") { auth(); miss(id = "not-a-uuid") }
            assertEquals(HttpStatusCode.BadRequest, badId.status)
            assertEquals("invalid_id", badId.body<ProblemResponse>().error.code)

            val noId = client.post("/v1/foods/barcode-misses") { auth(); miss(id = null) }
            assertEquals("invalid_id", noId.body<ProblemResponse>().error.code)

            listOf("1234567", "123456789012345", "12ab5678", null).forEach { code ->
                val bad = client.post("/v1/foods/barcode-misses") { auth(); miss(barcode = code) }
                assertEquals(HttpStatusCode.BadRequest, bad.status, "barcode $code")
                assertEquals("invalid_barcode", bad.body<ProblemResponse>().error.code)
            }
        }
        assertEquals(emptyList(), misses.recorded)
    }
}
