package com.opson.server.routes

import com.opson.server.data.CatalogFoodDescriber
import com.opson.server.data.PhotoDescriber
import com.opson.server.domain.EstimateResult
import com.opson.server.domain.EstimatedFood
import com.opson.server.domain.ImageType
import com.opson.server.domain.PhotoEstimateLimiter
import com.opson.server.domain.PhotoLimits
import com.opson.server.domain.Portion
import com.opson.server.domain.UserId
import com.opson.server.plugins.ProblemResponse
import com.opson.server.plugins.installAuthentication
import com.opson.server.plugins.installRateLimits
import com.opson.server.plugins.installSerialization
import com.opson.server.plugins.installStatusPages
import com.opson.server.testing.FakeEstimateProvider
import com.opson.server.testing.FakeFoodCatalog
import com.opson.server.testing.FakeFoodCatalog.Companion.food
import com.opson.server.testing.TestImages
import com.opson.server.testing.TestJwt
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class PhotoRoutesTest {

    private val userId = UserId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
    private val otherUser = UserId(UUID.fromString("44444444-4444-4444-4444-444444444444"))
    private val broccoli = food("broccoli, steamed", kcal = 35.0, portions = listOf(Portion("1 cup", 156.0)))
    private val provider = FakeEstimateProvider(
        EstimateResult.Estimated(
            listOf(
                EstimatedFood("broccoli, steamed", 2.0, "cup", 300.0),
                EstimatedFood("mystery sauce", 2.0, "tbsp", 30.0),
            ),
        ),
    )

    private suspend fun withApp(block: suspend (HttpClient) -> Unit) {
        val describer = PhotoDescriber(provider, CatalogFoodDescriber(FakeFoodCatalog(listOf(broccoli))))
        testApplication {
            application {
                installSerialization()
                installRateLimits()
                installStatusPages()
                installAuthentication(TestJwt.verification)
                routing { photoRoutes(describer, PhotoEstimateLimiter(Clock.System), Clock.System) }
            }
            block(createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } })
        }
    }

    private fun HttpRequestBuilder.auth(user: UserId = userId) = header(HttpHeaders.Authorization, TestJwt.bearer(user))

    private suspend fun HttpClient.photo(
        image: ByteArray? = TestImages.jpeg,
        note: String? = null,
        user: UserId = userId,
    ): HttpResponse = post("/v1/describe/photo") {
        auth(user)
        contentType(ContentType.Application.Json)
        setBody(PhotoDescribeRequest(image?.let { Base64.getEncoder().encodeToString(it) }, note))
    }

    private suspend fun HttpResponse.code(): String = body<ProblemResponse>().error.code

    @Test
    fun requiresAuth() = runTest {
        withApp { client ->
            assertEquals(HttpStatusCode.Unauthorized, client.post("/v1/describe/photo").status)
            assertTrue(provider.calls.isEmpty())
        }
    }

    @Test
    fun answersWithTheDescribeRowShape() = runTest {
        withApp { client ->
            val response = client.photo(note = "  steamed, no butter ")
            assertEquals(HttpStatusCode.OK, response.status)
            val items = response.body<DescribeResponse>().items
            assertEquals(listOf(broccoli.id.toString(), null), items.map { it.foodId })
            assertEquals(312.0, items[0].grams)
            assertEquals("cup", items[0].unit)
            assertEquals("mystery sauce", items[1].name)
            assertEquals(Triple(TestImages.jpeg.size, ImageType.Jpeg, "steamed, no butter"), provider.calls.single())
        }
    }

    @Test
    fun badImagesAreRejectedBeforeTheProvider() = runTest {
        withApp { client ->
            assertEquals("invalid_image", client.photo(image = null).code())
            assertEquals("invalid_image", client.photo(image = TestImages.gif).code())
            val notBase64 = client.post("/v1/describe/photo") {
                auth()
                contentType(ContentType.Application.Json)
                setBody(PhotoDescribeRequest(image = "%%% not base64 %%%"))
            }
            assertEquals(HttpStatusCode.BadRequest, notBase64.status)
            assertEquals("invalid_image", notBase64.code())
            val notJson = client.post("/v1/describe/photo") {
                auth()
                contentType(ContentType.Application.Json)
                setBody("{")
            }
            assertEquals("invalid_body", notJson.code())
            assertTrue(provider.calls.isEmpty())
        }
    }

    @Test
    fun aLongNoteIsRejected() = runTest {
        withApp { client ->
            val response = client.photo(note = "a".repeat(PhotoLimits.MAX_NOTE + 1))
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("invalid_note", response.code())
        }
    }

    @Test
    fun tooLargeIs413() = runTest {
        withApp { client ->
            val bigImage = TestImages.jpeg + ByteArray(PhotoLimits.MAX_IMAGE_BYTES)
            val response = client.photo(image = bigImage)
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
            assertEquals("image_too_large", response.code())

            val hugeBody = client.post("/v1/describe/photo") {
                auth()
                contentType(ContentType.Application.Json)
                setBody("\"" + "a".repeat(PhotoLimits.MAX_BODY_BYTES.toInt() + 10) + "\"")
            }
            assertEquals(HttpStatusCode.PayloadTooLarge, hugeBody.status)
            assertTrue(provider.calls.isEmpty())
        }
    }

    @Test
    fun theLimitIsPerUser() = runTest {
        withApp { client ->
            repeat(PhotoEstimateLimiter.BURST) { assertEquals(HttpStatusCode.OK, client.photo().status) }
            val limited = client.photo()
            assertEquals(HttpStatusCode.TooManyRequests, limited.status)
            assertEquals("rate_limited", limited.code())
            assertEquals(HttpStatusCode.OK, client.photo(user = otherUser).status)
        }
    }

    @Test
    fun noProviderKeyIs503() = runTest {
        provider.result = EstimateResult.NotConfigured
        withApp { client ->
            val response = client.photo()
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals("photo_estimate_unavailable", response.code())
        }
    }

    @Test
    fun aProviderFailureIs502() = runTest {
        provider.result = EstimateResult.Failed("refusal")
        withApp { client ->
            val response = client.photo(image = TestImages.webp)
            assertEquals(HttpStatusCode.BadGateway, response.status)
            assertEquals("photo_estimate_failed", response.code())
        }
    }
}
