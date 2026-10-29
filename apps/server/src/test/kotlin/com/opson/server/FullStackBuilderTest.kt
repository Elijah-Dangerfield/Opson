package com.opson.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.opson.server.config.AdminConfig
import com.opson.server.config.DeviceIntegrityConfig
import com.opson.server.config.FoodSourcesConfig
import com.opson.server.data.CaptureJson
import com.opson.server.data.CaptureLineJson
import com.opson.server.data.LabelFactsJson
import com.opson.server.db.AppConfigValuesTable
import com.opson.server.db.BuilderMembersTable
import com.opson.server.db.DatabaseTest
import com.opson.server.db.FoodProvenanceTable
import com.opson.server.db.FoodVerificationsTable
import com.opson.server.db.UserRolesTable
import com.opson.server.di.ServerComponent
import com.opson.server.di.create
import com.opson.server.domain.BuilderRules
import com.opson.server.domain.CarbBasis
import com.opson.server.domain.ImageType
import com.opson.server.domain.LabelChecks
import com.opson.server.domain.LabelFacts
import com.opson.server.domain.NewFoodSubmission
import com.opson.server.domain.NutrientKey
import com.opson.server.domain.PackPhotos
import com.opson.server.domain.Role
import com.opson.server.domain.ServingUnit
import com.opson.server.domain.SubmissionOrigin
import com.opson.server.domain.SubmitOutcome
import com.opson.server.domain.UserId
import com.opson.server.plugins.JwtVerification
import com.opson.server.routes.AdminBuilderResponse
import com.opson.server.routes.BuilderJoinRequest
import com.opson.server.routes.BuilderMeResponse
import com.opson.server.routes.FoodSubmissionDetailResponse
import com.opson.server.routes.FoodSubmissionRequest
import com.opson.server.routes.FoodSubmissionResponse
import com.opson.server.routes.MyAccessResponse
import com.opson.server.routes.VerificationRequest
import com.opson.server.routes.VerificationResponse
import com.opson.server.testing.DiaryFixtures
import com.opson.server.testing.TestImages
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.After
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The Builder program end to end, through the real graph and Postgres: joining gives Pro until a
 * date, a Builder's submission is routed and keeps its reasons, the owner's goes straight in, a
 * Trusted Builder's clean scan is auto-approved and audited, a failed audit takes it back out,
 * and a check against the pack counts once.
 */
@OptIn(ExperimentalTime::class)
class FullStackBuilderTest : DatabaseTest() {

    private val issuer = "https://test.supabase.co/auth/v1"
    private val secret = "0123456789abcdef0123456789abcdef0123456789abcdef"
    private val verifier = JWT.require(Algorithm.HMAC256(secret)).withIssuer(issuer).withAudience("authenticated").build()
    private val adminToken = "admin-token-for-tests"
    private val json = Json { ignoreUnknownKeys = true }

    private val granola = LabelFacts(
        name = "Maple granola",
        brand = "Hillside",
        servingSize = 55.0,
        servingUnit = ServingUnit.Gram,
        basis = CarbBasis.Us,
        perServing = mapOf(
            NutrientKey.Energy to 230.0,
            NutrientKey.Fat to 8.0,
            NutrientKey.Carbs to 37.0,
            NutrientKey.Fiber to 4.0,
            NutrientKey.Protein to 3.0,
        ),
    )

    private val panel = listOf(
        "Nutrition Facts", "Serving size 2/3 cup (55g)", "Calories 230", "Total Fat 8g 10%",
        "Total Carbohydrate 37g 13%", "Dietary Fiber 4g", "Protein 3g",
    )

    @After
    fun clean() {
        DiaryFixtures.cleanAll(database)
        database.blockingTransaction {
            FoodVerificationsTable.deleteAll()
            FoodProvenanceTable.deleteAll()
            BuilderMembersTable.deleteAll()
            UserRolesTable.deleteAll()
            AppConfigValuesTable.deleteWhere { path eq AUTO_APPROVE_PATH }
        }
    }

    private fun seedUser(ageDays: Int): UserId {
        val user = seedAuthUser()
        database.blockingTransaction {
            TransactionManager.current().exec("UPDATE auth.users SET created_at = now() - interval '$ageDays days' WHERE id = '${user.value}'")
        }
        return user
    }

    private fun component() = ServerComponent::class.create(database, null, FoodSourcesConfig(), Clock.System, DeviceIntegrityConfig())

    private fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient { install(ContentNegotiation) { json(json) } }

    private fun jwt(userId: UserId): String = JWT.create()
        .withIssuer(issuer)
        .withAudience("authenticated")
        .withSubject(userId.value.toString())
        .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
        .sign(Algorithm.HMAC256(secret))

    private fun submission(withCapture: Boolean) = FoodSubmissionRequest(
        id = UUID.randomUUID().toString(),
        origin = "barcode",
        barcode = "0${(10_000_000_000L..99_999_999_999L).random()}",
        front = Base64.getEncoder().encodeToString(TestImages.jpeg),
        label = Base64.getEncoder().encodeToString(TestImages.jpeg),
        food = LabelFactsJson.of(granola),
        capture = if (withCapture) {
            CaptureJson(
                engine = "vision",
                source = "camera",
                lines = panel.map { CaptureLineJson(it, 0.96) },
                draft = LabelFactsJson.of(granola.copy(name = null, brand = null)),
                score = 0.97,
            )
        } else {
            null
        },
    )

    private suspend fun HttpClient.submit(user: UserId, request: FoodSubmissionRequest) = post("/v1/foods/submissions") {
        header(HttpHeaders.Authorization, "Bearer ${jwt(user)}")
        contentType(ContentType.Application.Json)
        setBody(json.encodeToString(FoodSubmissionRequest.serializer(), request))
    }

    private suspend fun HttpClient.join(user: UserId, accept: Boolean = true) = post("/v1/builders/join") {
        header(HttpHeaders.Authorization, "Bearer ${jwt(user)}")
        contentType(ContentType.Application.Json)
        setBody(BuilderJoinRequest(acceptTerms = accept, termsVersion = BuilderRules.TERMS_VERSION, photoRetention = true))
    }

    private suspend fun HttpClient.detail(id: String) =
        get("/v1/admin/food-submissions/$id") { header("X-Admin-Token", adminToken) }.body<FoodSubmissionDetailResponse>()

    @Test
    fun joiningGivesTheBuilderRoleAndProUntilADate() = runTest {
        val user = seedUser(ageDays = 10)
        val component = component()
        testApplication {
            application { installApp(component, JwtVerification.Static(verifier)) }
            val client = jsonClient()

            val before = client.get("/v1/builders/me") { header(HttpHeaders.Authorization, "Bearer ${jwt(user)}") }.body<BuilderMeResponse>()
            assertEquals("none", before.status)
            assertTrue(before.canJoin)

            assertEquals(HttpStatusCode.BadRequest, client.join(user, accept = false).status)

            val joined = client.join(user)
            assertEquals(HttpStatusCode.OK, joined.status)
            val me = joined.body<BuilderMeResponse>()
            assertEquals("active", me.status)
            assertEquals("builder", me.trust)
            assertTrue(me.photoRetention)
            val proUntil = assertNotNull(me.proUntilEpochMs)
            assertEquals(Role.Builder, component.userRoleRepository.roleOf(user))

            val access = client.get("/v1/me/entitlements") { header(HttpHeaders.Authorization, "Bearer ${jwt(user)}") }.body<MyAccessResponse>()
            val recipe = access.entitlements.single { it.key == "recipe_import" }
            assertEquals(proUntil, recipe.sources.single { it.type == "role" }.expiresAtEpochMs)

            val left = client.post("/v1/builders/leave") { header(HttpHeaders.Authorization, "Bearer ${jwt(user)}") }.body<BuilderMeResponse>()
            assertEquals("left", left.status)
            assertTrue(left.canJoin)
            assertEquals(Role.Member, component.userRoleRepository.roleOf(user))
            val after = client.get("/v1/me/entitlements") { header(HttpHeaders.Authorization, "Bearer ${jwt(user)}") }.body<MyAccessResponse>()
            assertFalse(after.entitlements.any { it.key == "recipe_import" })
        }
    }

    @Test
    fun aBrandNewAccountWaitsAndAGuestCanNotJoin() = runTest {
        val fresh = seedUser(ageDays = 0)
        testApplication {
            application { installApp(component(), JwtVerification.Static(verifier)) }
            val response = jsonClient().join(fresh)

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertTrue(response.body<String>().contains("too_new"))
        }
    }

    @Test
    fun aBuildersSubmissionGoesToReviewWithItsReasonsAndTheOwnersGoesStraightIn() = runTest {
        val builder = seedUser(ageDays = 10)
        val owner = seedUser(ageDays = 100)
        val component = component()
        component.userRoleRepository.setRole(owner, Role.Owner, "test", null)
        testApplication {
            application { installApp(component, JwtVerification.Static(verifier), adminConfig = AdminConfig(adminToken)) }
            val client = jsonClient()
            client.join(builder)

            val sent = client.submit(builder, submission(withCapture = true))
            assertEquals(HttpStatusCode.Created, sent.status)
            val body = sent.body<FoodSubmissionResponse>()
            assertEquals("review", body.lane)
            val routing = assertNotNull(client.detail(body.submissionId).routing)
            assertEquals(listOf("not_trusted"), routing.reasons)
            assertEquals(5, routing.grounding?.grounded)
            assertNotNull(client.detail(body.submissionId).capture)

            val owners = client.submit(owner, submission(withCapture = false)).body<FoodSubmissionResponse>()
            assertEquals("owner", owners.lane)
            assertEquals("approved", owners.status)
            val lanes = database.transaction { FoodProvenanceTable.selectAll().map { it[FoodProvenanceTable.lane] } }
            assertEquals(listOf("owner"), lanes)
        }
    }

    @Test
    fun aTrustedBuildersCleanScanIsAutoApprovedAuditedAndAFailedAuditTakesItBackOut() = runTest {
        val builder = seedUser(ageDays = 60)
        val component = component()
        database.transaction {
            AppConfigValuesTable.insert {
                it[path] = AUTO_APPROVE_PATH
                it[valueJsonb] = "\"on\""
                it[updatedAt] = java.time.Instant.now()
            }
        }
        testApplication {
            application { installApp(component, JwtVerification.Static(verifier), adminConfig = AdminConfig(adminToken)) }
            val client = jsonClient()
            client.join(builder)
            // Fifty foods a reviewer approved unchanged: the record Trusted asks for.
            repeat(50) {
                val stored = component.foodSubmissionRepository.submit(
                    NewFoodSubmission(
                        id = UUID.randomUUID(),
                        userId = builder,
                        barcode = null,
                        origin = SubmissionOrigin.Search,
                        facts = granola,
                        checks = LabelChecks.check(granola),
                        acknowledged = emptyList(),
                        parsed = null,
                        model = null,
                        photos = PackPhotos(TestImages.jpeg, ImageType.Jpeg, TestImages.jpeg, ImageType.Jpeg),
                    ),
                ) as SubmitOutcome.Created
                component.foodSubmissionRepository.approve(stored.submission.id, null, "reviewer")
            }

            val me = client.get("/v1/builders/me") { header(HttpHeaders.Authorization, "Bearer ${jwt(builder)}") }.body<BuilderMeResponse>()
            assertEquals("trusted", me.trust)

            val sent = client.submit(builder, submission(withCapture = true)).body<FoodSubmissionResponse>()
            assertEquals("auto", sent.lane)
            assertEquals("approved", sent.status)
            val audited = client.detail(sent.submissionId)
            assertEquals("pending", audited.summary.audit)
            assertTrue(audited.reviewedBy.orEmpty().startsWith("auto:"))

            val failed = client.post("/v1/admin/food-submissions/${sent.submissionId}/audit") {
                header("X-Admin-Token", adminToken)
                contentType(ContentType.Application.Json)
                setBody("""{"passed": false, "note": "The fat was per 100 g"}""")
            }
            assertEquals(HttpStatusCode.OK, failed.status)
            val after = failed.body<FoodSubmissionDetailResponse>()
            assertEquals("pending", after.summary.status)
            assertEquals("failed", after.summary.audit)
            val rolledBack = database.transaction {
                FoodProvenanceTable.selectAll().where { FoodProvenanceTable.refId eq UUID.fromString(sent.submissionId) }.single()[FoodProvenanceTable.rolledBackAt]
            }
            assertNotNull(rolledBack)

            val admin = client.get("/v1/admin/users/${builder.value}/builder") { header("X-Admin-Token", adminToken) }.body<AdminBuilderResponse>()
            assertEquals(1, admin.auditsFailed30d)
        }
    }

    @Test
    fun aCheckAgainstThePackCountsOnceAndOnlyWhenTheNumbersAreThere() = runTest {
        val builder = seedUser(ageDays = 10)
        val owner = seedUser(ageDays = 100)
        val component = component()
        component.userRoleRepository.setRole(owner, Role.Owner, "test", null)
        testApplication {
            application { installApp(component, JwtVerification.Static(verifier)) }
            val client = jsonClient()
            val food = client.submit(owner, submission(withCapture = false)).body<FoodSubmissionResponse>().foodId

            suspend fun check(user: UserId, lines: List<String>) = client.post("/v1/foods/$food/verifications") {
                header(HttpHeaders.Authorization, "Bearer ${jwt(user)}")
                contentType(ContentType.Application.Json)
                setBody(VerificationRequest(UUID.randomUUID().toString(), "vision", lines.map { CaptureLineJson(it, 0.9) }))
            }

            assertEquals(HttpStatusCode.Forbidden, check(builder, panel).status)
            client.join(builder)

            val first = check(builder, panel).body<VerificationResponse>()
            assertEquals("confirmed", first.outcome)
            assertEquals("unconfirmed", check(builder, panel).body<VerificationResponse>().outcome)
            val wrong = check(builder, panel.map { it.replace("Protein 3g", "Protein 9g") }).body<VerificationResponse>()
            assertEquals(listOf("protein_g"), wrong.missing)

            val me = client.get("/v1/builders/me") { header(HttpHeaders.Authorization, "Bearer ${jwt(builder)}") }.body<BuilderMeResponse>()
            assertEquals(1, me.accepted30d)
        }
    }

    private companion object {
        const val AUTO_APPROVE_PATH = "builders.autoApprove"
    }
}
