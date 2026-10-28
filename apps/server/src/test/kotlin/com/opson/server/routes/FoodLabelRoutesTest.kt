package com.opson.server.routes

import com.opson.server.data.LabelFactsJson
import com.opson.server.data.LabelParseCache
import com.opson.server.domain.CarbBasis
import com.opson.server.domain.FoodLabelLimiter
import com.opson.server.domain.LabelCheckCode
import com.opson.server.domain.LabelFacts
import com.opson.server.domain.LabelParseResult
import com.opson.server.domain.LabelReading
import com.opson.server.domain.NutrientKey
import com.opson.server.domain.ServingUnit
import com.opson.server.domain.SubmissionOrigin
import com.opson.server.domain.UserId
import com.opson.server.plugins.ProblemResponse
import com.opson.server.plugins.installAuthentication
import com.opson.server.plugins.installRateLimits
import com.opson.server.plugins.installSerialization
import com.opson.server.plugins.installStatusPages
import com.opson.server.testing.FakeBarcodeLinkRepository
import com.opson.server.testing.FakeFoodSubmissionRepository
import com.opson.server.testing.FakeLabelParser
import com.opson.server.testing.TestImages
import com.opson.server.testing.TestJwt
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class FoodLabelRoutesTest {

    private val alice = UserId(UUID.fromString("55555555-5555-5555-5555-555555555555"))
    private val bob = UserId(UUID.fromString("66666666-6666-6666-6666-666666666666"))

    private val bar = LabelFacts(
        name = "Granola bar",
        servingSize = 60.0,
        servingUnit = ServingUnit.Gram,
        basis = CarbBasis.Us,
        perServing = mapOf(NutrientKey.Energy to 270.0, NutrientKey.Protein to 10.0, NutrientKey.Carbs to 35.0, NutrientKey.Fat to 6.0),
    )
    private val parser = FakeLabelParser(LabelParseResult.Read(LabelReading(bar, mapOf("name" to 0.9), readable = true), "claude-fable-5-1"))
    private val submissions = FakeFoodSubmissionRepository()
    private val links = FakeBarcodeLinkRepository()
    private val cache = LabelParseCache(Clock.System)

    private suspend fun withApp(limiter: FoodLabelLimiter = FoodLabelLimiter(Clock.System), block: suspend (HttpClient) -> Unit) {
        testApplication {
            application {
                installSerialization()
                installRateLimits()
                installStatusPages()
                installAuthentication(TestJwt.verification)
                routing { foodLabelRoutes(parser, submissions, links, cache, limiter, Clock.System) }
            }
            block(createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } })
        }
    }

    private val front = Base64.getEncoder().encodeToString(TestImages.jpeg)
    private val label = Base64.getEncoder().encodeToString(TestImages.png)

    private suspend fun HttpClient.parse(user: UserId = alice, front: String? = this@FoodLabelRoutesTest.front, label: String? = this@FoodLabelRoutesTest.label) =
        post("/v1/foods/labels/parse") {
            header(HttpHeaders.Authorization, TestJwt.bearer(user))
            contentType(ContentType.Application.Json)
            setBody(LabelParseRequest(front, label))
        }

    private suspend fun HttpClient.submit(request: FoodSubmissionRequest, user: UserId = alice): HttpResponse =
        post("/v1/foods/submissions") {
            header(HttpHeaders.Authorization, TestJwt.bearer(user))
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    private fun request(
        id: String = UUID.randomUUID().toString(),
        parseId: String? = null,
        food: LabelFactsJson? = LabelFactsJson.of(bar, listOf(LabelCheckCode.EnergyMacrosMismatch)),
        barcode: String? = "012345678905",
        origin: String = "barcode",
    ) = FoodSubmissionRequest(id = id, parseId = parseId, origin = origin, barcode = barcode, front = front, label = label, food = food)

    private suspend fun HttpResponse.code(): String = body<ProblemResponse>().error.code

    @Test
    fun parseAnswersTheReadingWithConfidenceAndTheChecksItFails() = runTest {
        withApp { client ->
            val response = client.parse()
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<LabelParseResponse>()
            assertEquals("Granola bar", body.food.name)
            assertEquals(270.0, body.food.perServing["energy_kcal"])
            assertEquals(0.9, body.confidence["name"])
            assertEquals(listOf("energy_macros_mismatch"), body.checks.map { it.code })
            assertEquals(234.0, body.checks.single().expected)
            assertNotNull(cache.get(UUID.fromString(body.parseId), alice))
            assertNull(cache.get(UUID.fromString(body.parseId), bob), "a reading is only its reader's")
            assertContentEquals(TestImages.png, parser.seen.single().label)
        }
    }

    @Test
    fun parseRefusesAnythingButTwoImages() = runTest {
        withApp { client ->
            assertEquals("invalid_image", client.parse(label = null).code())
            assertEquals("invalid_image", client.parse(front = Base64.getEncoder().encodeToString(TestImages.gif)).code())
            assertEquals("invalid_image", client.parse(front = "not base64!").code())
            assertTrue(parser.seen.isEmpty())
        }
    }

    @Test
    fun parseWithNoKeyIsUnavailableAndAFailureIsBadGateway() = runTest {
        withApp { client ->
            parser.result = LabelParseResult.NotConfigured
            val unavailable = client.parse()
            assertEquals(HttpStatusCode.ServiceUnavailable, unavailable.status)
            assertEquals("label_parse_unavailable", unavailable.code())
            parser.result = LabelParseResult.Failed("refusal")
            val failed = client.parse()
            assertEquals(HttpStatusCode.BadGateway, failed.status)
            assertEquals("label_parse_failed", failed.code())
        }
    }

    @Test
    fun parsesAreLimitedPerPerson() = runTest {
        withApp { client ->
            repeat(FoodLabelLimiter.PARSE_BURST) { assertEquals(HttpStatusCode.OK, client.parse().status) }
            val limited = client.parse()
            assertEquals(HttpStatusCode.TooManyRequests, limited.status)
            assertEquals("rate_limited", limited.code())
            assertEquals(HttpStatusCode.OK, client.parse(user = bob).status, "another person has their own allowance")
        }
    }

    @Test
    fun aSubmissionIsStoredPendingWithTheServersOwnReadingAndItsChecks() = runTest {
        withApp { client ->
            val parseId = client.parse().body<LabelParseResponse>().parseId
            val id = UUID.randomUUID().toString()

            val response = client.submit(request(id = id, parseId = parseId))

            assertEquals(HttpStatusCode.Created, response.status)
            val body = response.body<FoodSubmissionResponse>()
            assertEquals("pending", body.status)
            assertEquals(listOf("energy_macros_mismatch"), body.checks.map { it.code })
            val stored = submissions.submissions.getValue(UUID.fromString(id))
            assertEquals("claude-fable-5-1", stored.model)
            assertEquals(0.9, stored.parsed?.confidence?.get("name"))
            assertEquals(listOf(LabelCheckCode.EnergyMacrosMismatch), stored.acknowledged)
            assertEquals("012345678905", stored.barcode)
            assertEquals(SubmissionOrigin.Barcode, stored.origin)
            assertTrue(stored.hasPhotos)

            val again = client.submit(request(id = id, parseId = parseId))
            assertEquals(HttpStatusCode.OK, again.status)
            assertEquals(body.foodId, again.body<FoodSubmissionResponse>().foodId)
            assertEquals("id_conflict", client.submit(request(id = id), user = bob).code())
        }
    }

    @Test
    fun aSubmissionFromSearchMayHaveNoBarcodeOrOneTypedIntoTheForm() = runTest {
        withApp { client ->
            val none = client.submit(request(barcode = null, origin = "search"))
            assertEquals(HttpStatusCode.Created, none.status)
            val typed = client.submit(request(barcode = null, origin = "search", food = LabelFactsJson.of(bar.copy(barcode = "4006381333931"))))
            val stored = submissions.submissions.getValue(UUID.fromString(typed.body<FoodSubmissionResponse>().submissionId))
            assertEquals("4006381333931", stored.barcode)
        }
    }

    @Test
    fun aReadingFromAnotherPersonOrNoneLeavesParsedEmpty() = runTest {
        withApp { client ->
            val bobsParse = client.parse(user = bob).body<LabelParseResponse>().parseId
            val response = client.submit(request(parseId = bobsParse)).body<FoodSubmissionResponse>()
            assertNull(submissions.submissions.getValue(UUID.fromString(response.submissionId)).parsed)
        }
    }

    @Test
    fun aSubmissionIsValidatedFieldByField() = runTest {
        withApp { client ->
            assertEquals("invalid_id", client.submit(request(id = "nope")).code())
            assertEquals("invalid_origin", client.submit(request(origin = "photo")).code())
            assertEquals("invalid_food", client.submit(request(food = null)).code())
            assertEquals("invalid_food", client.submit(request(food = LabelFactsJson.of(bar.copy(name = "  ")))).code())
            assertEquals("invalid_food", client.submit(request(food = LabelFactsJson.of(bar).copy(servingUnit = "cup"))).code())
            assertEquals("invalid_food", client.submit(request(food = LabelFactsJson.of(bar).copy(servingSize = -1.0))).code())
            assertEquals(
                "invalid_food",
                client.submit(request(food = LabelFactsJson.of(bar).copy(perServing = mapOf("protein_g" to Double.MAX_VALUE)))).code(),
            )
            assertEquals("invalid_barcode", client.submit(request(barcode = "12ab")).code())
            assertTrue(submissions.submissions.isEmpty())
        }
    }

    @Test
    fun missingNumbersAreAcceptedForReview() = runTest {
        withApp { client ->
            val response = client.submit(request(food = LabelFactsJson.of(bar.copy(perServing = emptyMap()))))
            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals(listOf("missing_required"), response.body<FoodSubmissionResponse>().checks.map { it.code })
        }
    }

    @Test
    fun submissionsAreLimitedPerPerson() = runTest {
        withApp { client ->
            repeat(FoodLabelLimiter.SUBMIT_BURST) { assertEquals(HttpStatusCode.Created, client.submit(request()).status) }
            assertEquals("rate_limited", client.submit(request()).code())
        }
    }

    @Test
    fun aBarcodeLinkIsCountedWithNothingButTheCodeAndTheFood() = runTest {
        withApp { client ->
            val food = UUID.randomUUID()
            val response = client.post("/v1/foods/barcode-links") {
                header(HttpHeaders.Authorization, TestJwt.bearer(alice))
                contentType(ContentType.Application.Json)
                setBody(BarcodeLinkRequest("012345678905", food.toString()))
            }
            assertEquals(HttpStatusCode.NoContent, response.status)
            assertEquals(listOf("012345678905" to food), links.recorded)

            val bad = client.post("/v1/foods/barcode-links") {
                header(HttpHeaders.Authorization, TestJwt.bearer(alice))
                contentType(ContentType.Application.Json)
                setBody(BarcodeLinkRequest("abc", food.toString()))
            }
            assertEquals("invalid_barcode", bad.code())
        }
    }

    @Test
    fun everyRouteNeedsASignIn() = runTest {
        withApp { client ->
            assertEquals(HttpStatusCode.Unauthorized, client.post("/v1/foods/labels/parse").status)
            assertEquals(HttpStatusCode.Unauthorized, client.post("/v1/foods/submissions").status)
            assertEquals(HttpStatusCode.Unauthorized, client.post("/v1/foods/barcode-links").status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/foods/submissions").status)
        }
    }

    @Test
    fun aSubmissionKeepsTheSubmittersOwnCopyAndAMalformedOneIsDropped() = runTest {
        withApp { client ->
            val customFoodId = UUID.randomUUID().toString()
            val linked = client.submit(request(barcode = null, origin = "search").copy(customFoodId = customFoodId))
            assertEquals(HttpStatusCode.Created, linked.status)
            val stored = submissions.submissions.getValue(UUID.fromString(linked.body<FoodSubmissionResponse>().submissionId))
            assertEquals(customFoodId, stored.customFoodId.toString())

            val malformed = client.submit(request(barcode = null, origin = "search").copy(customFoodId = "not-a-uuid"))
            assertEquals(HttpStatusCode.Created, malformed.status, "the food matters more than the mark")
            assertNull(submissions.submissions.getValue(UUID.fromString(malformed.body<FoodSubmissionResponse>().submissionId)).customFoodId)
        }
    }

    @Test
    fun ownSubmissionsListOnlyTheCallersNewestFirstWithTheirStatus() = runTest {
        withApp { client ->
            val customFoodId = UUID.randomUUID().toString()
            val first = client.submit(request(origin = "barcode")).body<FoodSubmissionResponse>()
            val second = client.submit(request(barcode = null, origin = "search").copy(customFoodId = customFoodId)).body<FoodSubmissionResponse>()
            client.submit(request(barcode = null, origin = "search"), user = bob)

            val response = client.get("/v1/foods/submissions") { header(HttpHeaders.Authorization, TestJwt.bearer(alice)) }
            assertEquals(HttpStatusCode.OK, response.status)
            val mine = response.body<OwnSubmissionsResponse>().submissions
            assertEquals(listOf(second.submissionId, first.submissionId), mine.map { it.id })
            assertEquals(listOf(second.foodId, first.foodId), mine.map { it.foodId })
            assertEquals(listOf("pending", "pending"), mine.map { it.status })
            assertEquals(listOf(customFoodId, null), mine.map { it.customFoodId })
        }
    }
}
