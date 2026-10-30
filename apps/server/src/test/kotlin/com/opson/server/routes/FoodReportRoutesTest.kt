package com.opson.server.routes

import com.opson.server.config.AdminConfig
import com.opson.server.data.CorrectionValuesJson
import com.opson.server.data.SuggestedJson
import com.opson.server.domain.CatalogSource
import com.opson.server.domain.CorrectionAuthority
import com.opson.server.domain.Food
import com.opson.server.domain.FoodReportLimiter
import com.opson.server.domain.Nutrients
import com.opson.server.domain.Portion
import com.opson.server.domain.ReportReason
import com.opson.server.domain.ReportStatus
import com.opson.server.domain.ServedFood
import com.opson.server.domain.UserId
import com.opson.server.plugins.ProblemResponse
import com.opson.server.plugins.installAuthentication
import com.opson.server.plugins.installRateLimits
import com.opson.server.plugins.installSerialization
import com.opson.server.plugins.installStatusPages
import com.opson.server.testing.FakeFoodCatalog
import com.opson.server.testing.FakeFoodReportRepository
import com.opson.server.testing.FakeFoodSubmissionRepository
import com.opson.server.testing.TestImages
import com.opson.server.testing.TestJwt
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** `POST /v1/foods/{id}/reports` and the admin queue behind it. */
@OptIn(ExperimentalTime::class)
class FoodReportRoutesTest {

    private val alice = UserId(UUID.fromString("55555555-5555-5555-5555-555555555555"))
    private val bob = UserId(UUID.fromString("66666666-6666-6666-6666-666666666666"))
    private val token = "admin-secret"

    /** The Monster row as the catalog serves it now: the guard's estimate, flagged. */
    private val monster = ServedFood.of(
        Food(
            id = UUID.fromString("0f0f0f0f-0000-0000-0000-000000000001"),
            source = CatalogSource.UsdaBranded,
            sourceId = "2680587",
            name = "Energy Drink",
            brand = "Monster",
            barcode = "00070847012474",
            per100g = Nutrients(kcal = 0.0, protein = 0.0, carbs = 0.83, fat = 0.0),
            micros = emptyMap(),
            portions = listOf(Portion("8.0 fl oz (240 ml)", null)),
        ),
        correction = null,
    )
    private val oats = Food(
        id = UUID.fromString("0f0f0f0f-0000-0000-0000-000000000002"),
        source = CatalogSource.UsdaSr,
        sourceId = "oats",
        name = "Oats",
        brand = null,
        barcode = null,
        per100g = Nutrients(kcal = 379.0, protein = 13.2, carbs = 67.7, fat = 6.5),
        micros = emptyMap(),
        portions = listOf(Portion("1 cup", 81.0)),
    )
    private val catalog = FakeFoodCatalog(listOf(monster, oats))
    private val reports = FakeFoodReportRepository()
    private val submissions = FakeFoodSubmissionRepository()

    private suspend fun withApp(
        limiter: FoodReportLimiter = FoodReportLimiter(Clock.System),
        authority: CorrectionAuthority = CorrectionAuthority { false },
        block: suspend (HttpClient) -> Unit,
    ) {
        testApplication {
            application {
                installSerialization()
                installRateLimits()
                installStatusPages()
                installAuthentication(TestJwt.verification)
                routing {
                    foodReportRoutes(catalog, reports, submissions, limiter, authority)
                    foodReportAdminRoutes(AdminConfig(apiToken = token), reports, catalog, Clock.System)
                }
            }
            block(createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } })
        }
    }

    private fun request(
        id: String = UUID.randomUUID().toString(),
        reason: String = "wrong_calories",
        suggested: SuggestedJson? = SuggestedJson(basisLabel = "1 can", basisGrams = 473.0, kcal = 10.0, servingLabel = "1 can (473 ml)", servingGrams = 473.0),
        note: String? = "The can says 10 calories",
        photo: String? = Base64.getEncoder().encodeToString(TestImages.jpeg),
    ) = FoodReportRequest(id = id, reason = reason, suggested = suggested, note = note, photo = photo)

    private suspend fun HttpClient.report(body: FoodReportRequest, food: UUID = monster.id, user: UserId = alice): HttpResponse =
        post("/v1/foods/$food/reports") {
            header(HttpHeaders.Authorization, TestJwt.bearer(user))
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private fun HttpRequestBuilder.admin() {
        header("X-Admin-Token", token)
        header("X-Admin-Actor", "Elijah")
    }

    private suspend fun HttpResponse.code(): String = body<ProblemResponse>().error.code

    @Test
    fun aReportIsStoredWithTheFoodAsServedAndChangesNothing() = runTest {
        withApp { client ->
            val body = request()
            val response = client.report(body)
            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals(FoodReportResponse(body.id!!, "open"), response.body<FoodReportResponse>())

            val stored = reports.reports.single()
            assertEquals(ReportReason.WrongCalories, stored.reason)
            assertEquals(alice, stored.userId)
            assertEquals(2.1, stored.snapshot.per100g.kcal, "what the person saw")
            assertEquals(10.0, stored.suggested?.kcal)
            assertTrue(stored.hasPhoto)
            assertTrue(reports.corrections.isEmpty(), "a report never changes the food by itself")

            // The same id again is the same report; someone else's use of it is a conflict.
            assertEquals(HttpStatusCode.OK, client.report(body).status)
            assertEquals(HttpStatusCode.Conflict, client.report(body, user = bob).status)
            assertEquals(1, reports.reports.size)
        }
    }

    @Test
    fun everythingButTheReasonIsOptional() = runTest {
        withApp { client ->
            val response = client.report(request(reason = "duplicate", suggested = null, note = null, photo = null), food = oats.id)
            assertEquals(HttpStatusCode.Created, response.status)
            assertNull(reports.reports.single().suggested)
        }
    }

    @Test
    fun badInputIsRefusedByName() = runTest {
        withApp { client ->
            assertEquals("invalid_reason", client.report(request(reason = "energy_check")).code(), "only the server files those")
            assertEquals("invalid_reason", client.report(request(reason = "tasty")).code())
            assertEquals("invalid_id", client.report(request(id = "nope")).code())
            assertEquals("invalid_values", client.report(request(suggested = SuggestedJson(kcal = -1.0))).code())
            // 50 g of carbs in a 10 g basis is more than the food weighs: a typo.
            assertEquals("invalid_values", client.report(request(suggested = SuggestedJson(basisGrams = 10.0, carbs = 50.0))).code())
            assertEquals("invalid_note", client.report(request(note = "x".repeat(1_001))).code())
            assertEquals("invalid_image", client.report(request(photo = Base64.getEncoder().encodeToString(TestImages.gif))).code())
            assertEquals("not_found", client.report(request(), food = UUID.randomUUID()).code())
            val noAuth = client.post("/v1/foods/${monster.id}/reports") {
                contentType(ContentType.Application.Json)
                setBody(request())
            }
            assertEquals(HttpStatusCode.Unauthorized, noAuth.status)
            assertTrue(reports.reports.isEmpty())
        }
    }

    @Test
    fun aPersonIsLimited() = runTest {
        withApp(limiter = FoodReportLimiter(Clock.System)) { client ->
            repeat(FoodReportLimiter.BURST) { assertEquals(HttpStatusCode.Created, client.report(request()).status) }
            assertEquals("rate_limited", client.report(request()).code())
            assertEquals(HttpStatusCode.Created, client.report(request(), user = bob).status, "per person")
        }
    }

    /** Owners and admins may edit foods directly; a moderator reviews, and a member's report waits. */
    @Test
    fun theRoleBackedAuthorityTrustsOwnersAndAdminsOnly() = runTest {
        val roles = object : com.opson.server.domain.UserRoleRepository {
            val held = mutableMapOf(alice to com.opson.server.domain.Role.Owner, bob to com.opson.server.domain.Role.Moderator)
            override suspend fun assignment(userId: UserId) = held[userId]?.let {
                com.opson.server.domain.RoleAssignment(userId, it, "test", kotlin.time.Instant.parse("2026-10-29T00:00:00Z"), null)
            }
            override suspend fun setRole(userId: UserId, role: com.opson.server.domain.Role, actor: String, reason: String?) =
                throw UnsupportedOperationException()
        }
        val authority = com.opson.server.domain.RoleCorrectionAuthority(com.opson.server.domain.Permissions(roles))
        assertTrue(authority.canApplyDirectly(alice))
        assertEquals(false, authority.canApplyDirectly(bob))
        assertEquals(false, authority.canApplyDirectly(UserId(UUID.randomUUID())))
        roles.held[bob] = com.opson.server.domain.Role.Admin
        assertTrue(authority.canApplyDirectly(bob), "a role change applies on the next report")
    }

    @Test
    fun someoneTheAuthorityTrustsHasTheirValuesAppliedAtOnce() = runTest {
        withApp(authority = { it == alice }) { client ->
            assertEquals("accepted", client.report(request()).body<FoodReportResponse>().status)
            val correction = reports.corrections.single()
            assertEquals("user:${alice.value}", correction.createdBy)
            // 10 kcal a 473 ml can is 2.11 per 100.
            assertEquals(2.11, correction.values.kcal)
            assertEquals("1 can (473 ml)", correction.values.servingLabel)
            assertEquals(ReportStatus.Accepted, reports.reports.single().status)

            assertEquals("open", client.report(request(), user = bob).body<FoodReportResponse>().status, "everyone else waits for review")
        }
    }

    @Test
    fun theQueueGroupsPerFoodMostReportedFirstAndAcceptingAppliesTheReviewersValues() = runTest {
        withApp { client ->
            client.report(request(reason = "other", suggested = null), food = oats.id)
            client.report(request())
            client.report(request(), user = bob)
            reports.flagEnergy(monster)

            val queue = client.get("/v1/admin/food-reports") { admin() }.body<FoodReportQueueResponse>()
            assertEquals(4, queue.openReports)
            assertEquals(listOf(monster.id.toString(), oats.id.toString()), queue.foods.map { it.foodId })
            val group = queue.foods.first()
            assertEquals(3, group.reportCount)
            assertEquals(mapOf("wrong_calories" to 2, "energy_check" to 1), group.reasons)
            assertEquals(listOf("zero_with_macros"), group.now?.energyFlags)
            assertEquals(2.11, group.reports.first { it.fromPerson }.suggestedPer100?.kcal)

            val ids = group.reports.map { it.id }
            val accepted = client.post("/v1/admin/food-reports/foods/${monster.id}/accept") {
                admin()
                contentType(ContentType.Application.Json)
                setBody(AcceptReportsRequest(ids, CorrectionValuesJson(kcal = 2.1, servingLabel = "1 can (473 ml)", servingGrams = 473.0)))
            }
            assertEquals(HttpStatusCode.OK, accepted.status)
            val correction = accepted.body<FoodCorrectionResponse>()
            assertEquals("Elijah", correction.createdBy)
            assertEquals(2.1, correction.base.per100g.kcal)

            // Those reports are closed now.
            val again = client.post("/v1/admin/food-reports/foods/${monster.id}/accept") {
                admin()
                contentType(ContentType.Application.Json)
                setBody(AcceptReportsRequest(ids, CorrectionValuesJson(kcal = 2.0)))
            }
            assertEquals("not_open", again.code())

            val reverted = client.post("/v1/admin/food-corrections/${correction.id}/revert") { admin() }
            assertEquals(HttpStatusCode.OK, reverted.status)
            assertEquals("not_live", client.post("/v1/admin/food-corrections/${correction.id}/revert") { admin() }.code())

            val detail = client.get("/v1/admin/food-reports/foods/${monster.id}") { admin() }.body<FoodReportFoodResponse>()
            assertEquals(3, detail.reports.size)
            assertEquals(listOf(false), detail.corrections.map { it.live })
        }
    }

    @Test
    fun rejectAndDuplicateCloseReports_andThePhotoNeedsTheToken() = runTest {
        withApp { client ->
            client.report(request())
            client.report(request(reason = "duplicate", suggested = null, photo = null), food = oats.id)
            val (calories, duplicate) = reports.reports.map { it.id.toString() }

            val rejected = client.post("/v1/admin/food-reports/reject") {
                admin()
                contentType(ContentType.Application.Json)
                setBody(CloseReportsRequest(listOf(calories), reason = "The label says 0"))
            }
            assertEquals(1, rejected.body<ClosedReportsResponse>().closed)
            val marked = client.post("/v1/admin/food-reports/duplicate") {
                admin()
                contentType(ContentType.Application.Json)
                setBody(CloseReportsRequest(listOf(duplicate), duplicateOf = monster.id.toString()))
            }
            assertEquals(1, marked.body<ClosedReportsResponse>().closed)
            assertEquals(monster.id, reports.reports.last().duplicateOf)
            assertEquals(0, client.get("/v1/admin/food-reports") { admin() }.body<FoodReportQueueResponse>().openReports)

            assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/admin/food-reports/$calories/photo").status)
            val photo = client.get("/v1/admin/food-reports/$calories/photo") { admin() }
            assertEquals(HttpStatusCode.OK, photo.status)
            assertEquals("image/jpeg", photo.headers[HttpHeaders.ContentType])
        }
    }

    @Test
    fun severalReasonsWinOverTheOneAndBothSidesOfThePackAreKept() = runTest {
        withApp { client ->
            val jpeg = Base64.getEncoder().encodeToString(TestImages.jpeg)
            // What the app sends since V34: the reasons, with the first in a code an older server knows.
            val body = request(reason = "wrong_nutrients", photo = jpeg)
                .copy(reasons = listOf("missing_info", "wrong_numbers", "missing_info"), frontPhoto = jpeg)
            assertEquals(HttpStatusCode.Created, client.report(body).status)

            val stored = reports.reports.single()
            assertEquals(listOf(ReportReason.MissingInfo, ReportReason.WrongNumbers), stored.reasons)
            assertEquals(ReportReason.MissingInfo, stored.reason)
            assertTrue(stored.hasPhoto)
            assertTrue(stored.hasFrontPhoto)

            val queue = client.get("/v1/admin/food-reports") { admin() }.body<FoodReportQueueResponse>()
            val shown = queue.foods.single().reports.single()
            assertEquals(listOf("missing_info", "wrong_numbers"), shown.reasons)
            assertTrue(shown.hasFrontPhoto)
            assertEquals(mapOf("missing_info" to 1, "wrong_numbers" to 1), queue.foods.single().reasons)
            assertEquals(HttpStatusCode.OK, client.get("/v1/admin/food-reports/${stored.id}/photo?kind=front") { admin() }.status)
            assertEquals(HttpStatusCode.BadRequest, client.get("/v1/admin/food-reports/${stored.id}/photo?kind=side") { admin() }.status)

            val unknown = client.report(request().copy(reasons = listOf("wrong_numbers", "tasty")))
            assertEquals(HttpStatusCode.BadRequest, unknown.status)
            assertEquals("invalid_reason", unknown.code())
            val serverOnly = client.report(request().copy(reasons = listOf("energy_check")))
            assertEquals("invalid_reason", serverOnly.code())
        }
    }
}
