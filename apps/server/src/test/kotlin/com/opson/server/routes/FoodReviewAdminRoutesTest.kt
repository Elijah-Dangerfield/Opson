package com.opson.server.routes

import com.opson.server.config.AdminConfig
import com.opson.server.data.LabelFactsJson
import com.opson.server.domain.BarcodeLinkSuggestion
import com.opson.server.domain.CarbBasis
import com.opson.server.domain.ImageType
import com.opson.server.domain.LabelChecks
import com.opson.server.domain.LabelFacts
import com.opson.server.domain.LinkOutcome
import com.opson.server.domain.LinkStatus
import com.opson.server.domain.NewFoodSubmission
import com.opson.server.domain.NutrientKey
import com.opson.server.domain.PackPhotos
import com.opson.server.domain.ServingUnit
import com.opson.server.domain.SubmissionOrigin
import com.opson.server.domain.SubmissionStatus
import com.opson.server.domain.UserId
import com.opson.server.plugins.ProblemResponse
import com.opson.server.plugins.installSerialization
import com.opson.server.plugins.installStatusPages
import com.opson.server.testing.FakeBarcodeLinkRepository
import com.opson.server.testing.FakeFoodSubmissionRepository
import com.opson.server.testing.TestImages
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class FoodReviewAdminRoutesTest {

    private val token = "admin-secret"
    private val submissions = FakeFoodSubmissionRepository()
    private val links = FakeBarcodeLinkRepository()
    private val user = UserId(UUID.fromString("77777777-7777-7777-7777-777777777777"))

    private val bar = LabelFacts(
        name = "Granola bar",
        servingSize = 40.0,
        servingUnit = ServingUnit.Gram,
        basis = CarbBasis.Us,
        perServing = mapOf(NutrientKey.Energy to 190.0, NutrientKey.Protein to 4.0, NutrientKey.Carbs to 29.0, NutrientKey.Fat to 7.0),
    )

    private suspend fun seed(facts: LabelFacts = bar): UUID {
        val id = UUID.randomUUID()
        submissions.submit(
            NewFoodSubmission(
                id = id, userId = user, barcode = "012345678905", origin = SubmissionOrigin.Barcode, facts = facts,
                checks = LabelChecks.check(facts), acknowledged = emptyList(), parsed = null, model = null,
                photos = PackPhotos(TestImages.jpeg, ImageType.Jpeg, TestImages.png, ImageType.Png),
            ),
        )
        return id
    }

    private suspend fun withApp(block: suspend (HttpClient) -> Unit) {
        testApplication {
            application {
                installSerialization()
                installStatusPages()
                routing { foodReviewAdminRoutes(AdminConfig(apiToken = token), submissions, links, Clock.System) }
            }
            block(createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } })
        }
    }

    private fun HttpRequestBuilder.admin() {
        header("X-Admin-Token", token)
        header("X-Admin-Actor", "Elijah")
    }

    private fun HttpRequestBuilder.json(body: Any) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpResponse.code(): String = body<ProblemResponse>().error.code

    @Test
    fun everyRouteNeedsTheAdminToken() = runTest {
        val id = seed()
        withApp { client ->
            listOf(
                client.get("/v1/admin/food-submissions"),
                client.get("/v1/admin/food-submissions/$id"),
                client.get("/v1/admin/food-submissions/$id/photos/front"),
                client.post("/v1/admin/food-submissions/$id/approve") { json(ReviewEditRequest()) },
                client.get("/v1/admin/barcode-links") { header("X-Admin-Token", "wrong") },
            ).forEach { assertEquals(HttpStatusCode.Unauthorized, it.status) }
        }
    }

    @Test
    fun theQueueListsPendingSubmissionsWithTheirChecksAndPurgesOldPhotos() = runTest {
        val clean = seed()
        val off = seed(bar.copy(perServing = bar.perServing + (NutrientKey.Energy to 270.0)))
        withApp { client ->
            val list = client.get("/v1/admin/food-submissions") { admin() }.body<FoodSubmissionListResponse>()
            assertEquals(2, list.total)
            assertEquals(listOf(clean.toString(), off.toString()), list.submissions.map { it.id })
            assertEquals(listOf(emptyList(), listOf("energy_macros_mismatch")), list.submissions.map { it.failedChecks })
            val purgedBefore = assertNotNull(submissions.purgedBefore)
            assertTrue(Clock.System.now() - purgedBefore >= kotlin.time.Duration.parse("29d"))
        }
    }

    @Test
    fun photosAreServedOnlyHereAndNeverCached() = runTest {
        val id = seed()
        withApp { client ->
            val response = client.get("/v1/admin/food-submissions/$id/photos/label") { admin() }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Image.PNG, response.headers[HttpHeaders.ContentType]?.let(ContentType::parse))
            assertTrue(response.headers[HttpHeaders.CacheControl].orEmpty().contains("no-store"))
            assertContentEquals(TestImages.png, response.readRawBytes())
            assertEquals("invalid_kind", client.get("/v1/admin/food-submissions/$id/photos/back") { admin() }.code())
            assertEquals(HttpStatusCode.NotFound, client.get("/v1/admin/food-submissions/${UUID.randomUUID()}/photos/front") { admin() }.status)
        }
    }

    @Test
    fun aReviewerEditsThenApprovesAndTheActorIsRecorded() = runTest {
        val id = seed(bar.copy(perServing = bar.perServing + (NutrientKey.Energy to 270.0)))
        withApp { client ->
            val edited = client.put("/v1/admin/food-submissions/$id") {
                admin()
                json(ReviewEditRequest(LabelFactsJson.of(bar)))
            }.body<FoodSubmissionDetailResponse>()
            assertEquals(270.0, edited.submitted.perServing["energy_kcal"])
            assertEquals(190.0, edited.reviewed?.perServing?.get("energy_kcal"))
            assertEquals(emptyList(), edited.checks)

            val approved = client.post("/v1/admin/food-submissions/$id/approve") {
                admin()
                json(ReviewEditRequest())
            }.body<FoodSubmissionDetailResponse>()
            assertEquals("approved", approved.summary.status)
            assertEquals("Elijah", approved.reviewedBy)
            assertEquals("already_decided", client.post("/v1/admin/food-submissions/$id/reject") {
                admin()
                json(ReviewRejectRequest("late"))
            }.code())
        }
    }

    @Test
    fun approvingWithoutTheRequiredNumbersIsRefused() = runTest {
        val id = seed(bar.copy(perServing = emptyMap()))
        withApp { client ->
            val response = client.post("/v1/admin/food-submissions/$id/approve") {
                admin()
                json(ReviewEditRequest())
            }
            assertEquals(HttpStatusCode.Conflict, response.status)
            assertEquals("incomplete", response.code())
            assertEquals(SubmissionStatus.Pending, submissions.submissions.getValue(id).status)
        }
    }

    @Test
    fun rejectingNeedsAReason() = runTest {
        val id = seed()
        withApp { client ->
            assertEquals("invalid_reason", client.post("/v1/admin/food-submissions/$id/reject") {
                admin()
                json(ReviewRejectRequest(" "))
            }.code())
            val rejected = client.post("/v1/admin/food-submissions/$id/reject") {
                admin()
                json(ReviewRejectRequest("The label photo is of another product"))
            }.body<FoodSubmissionDetailResponse>()
            assertEquals("rejected", rejected.summary.status)
            assertEquals("The label photo is of another product", rejected.rejectReason)
        }
    }

    @Test
    fun barcodeLinksAreListedAttachedAndDismissed() = runTest {
        val food = UUID.randomUUID()
        links.suggestions += BarcodeLinkSuggestion(
            gtin = "00012345678905", barcode = "012345678905", foodId = food, foodName = "Rolled oats", foodBrand = null,
            foodBarcode = null, pairings = 3, firstAt = Instant.parse("2026-10-20T00:00:00Z"),
            lastAt = Instant.parse("2026-10-27T00:00:00Z"), status = LinkStatus.Pending,
        )
        withApp { client ->
            val list = client.get("/v1/admin/barcode-links") { admin() }.body<BarcodeLinkListResponse>()
            assertEquals(listOf(3), list.links.map { it.pairings })

            val attach = client.post("/v1/admin/barcode-links/attach") {
                admin()
                json(BarcodeLinkDecisionRequest("00012345678905", food.toString()))
            }
            assertEquals(HttpStatusCode.NoContent, attach.status)
            links.attachOutcome = LinkOutcome.BarcodeTaken(UUID.randomUUID())
            assertEquals("barcode_taken", client.post("/v1/admin/barcode-links/attach") {
                admin()
                json(BarcodeLinkDecisionRequest("00012345678905", food.toString()))
            }.code())
            assertEquals(HttpStatusCode.NoContent, client.post("/v1/admin/barcode-links/dismiss") {
                admin()
                json(BarcodeLinkDecisionRequest("00012345678905", food.toString()))
            }.status)
            assertEquals("invalid_link", client.post("/v1/admin/barcode-links/dismiss") {
                admin()
                json(BarcodeLinkDecisionRequest("123", food.toString()))
            }.code())
            assertEquals(listOf("attach", "attach", "dismiss"), links.decided.map { it.first })
        }
    }
}
