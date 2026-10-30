package com.opson.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.opson.server.config.AdminConfig
import com.opson.server.config.DeviceIntegrityConfig
import com.opson.server.config.FoodSourcesConfig
import com.opson.server.data.CaptureJson
import com.opson.server.data.CaptureLineJson
import com.opson.server.data.LabelFactsJson
import com.opson.server.data.VerificationPhotosJson
import com.opson.server.data.VerificationProblemJson
import com.opson.server.data.VerificationValuesJson
import com.opson.server.db.AppConfigValuesTable
import com.opson.server.db.BuilderApplicationsTable
import com.opson.server.db.BuilderMembersTable
import com.opson.server.db.DatabaseTest
import com.opson.server.db.FoodProvenanceTable
import com.opson.server.db.FoodVerificationsTable
import com.opson.server.db.FoodsTable
import com.opson.server.db.UserRolesTable
import com.opson.server.di.ServerComponent
import com.opson.server.di.create
import com.opson.server.domain.ApplicationForm
import com.opson.server.domain.ApplyOutcome
import com.opson.server.domain.BuilderRules
import com.opson.server.domain.DecideOutcome
import com.opson.server.domain.LabelRegion
import com.opson.server.domain.ShoppingFrequency
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
import com.opson.server.routes.AdminApplicationDecisionResponse
import com.opson.server.routes.AdminApplicationListResponse
import com.opson.server.routes.AdminBuilderResponse
import com.opson.server.routes.BuilderApplicationRequest
import com.opson.server.routes.BuilderJoinRequest
import com.opson.server.routes.BuilderMeResponse
import com.opson.server.routes.FoodSubmissionDetailResponse
import com.opson.server.routes.FoodSubmissionRequest
import com.opson.server.routes.FoodSubmissionResponse
import com.opson.server.routes.MyAccessResponse
import com.opson.server.routes.FoodResponse
import com.opson.server.routes.FoodVerificationAdminResponse
import com.opson.server.routes.FoodVerificationQueueResponse
import com.opson.server.routes.ReviewerCountsResponse
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The Builder program end to end, through the real graph and Postgres: applying waits for the
 * owner, whose Accept gives the role and Pro until a date and whose Decline holds a second
 * application off for a while; an older app's Join only applies; a Builder's submission is routed
 * and keeps its reasons, the owner's goes straight in, a Trusted Builder's clean scan is
 * auto-approved and audited, a failed audit takes it back out, and a check against the pack
 * counts once.
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
            TransactionManager.current().exec("DELETE FROM food_corrections")
            BuilderApplicationsTable.deleteAll()
            BuilderMembersTable.deleteAll()
            TransactionManager.current().exec("DELETE FROM moderation_actions")
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

    /** What an app from before applications sends from Join. */
    private suspend fun HttpClient.legacyJoin(user: UserId, accept: Boolean = true) = post("/v1/builders/join") {
        header(HttpHeaders.Authorization, "Bearer ${jwt(user)}")
        contentType(ContentType.Application.Json)
        setBody(BuilderJoinRequest(acceptTerms = accept, termsVersion = BuilderRules.TERMS_VERSION, photoRetention = true))
    }

    private suspend fun HttpClient.sendApplication(user: UserId, accept: Boolean = true, region: String? = "uk", id: String? = null) =
        post("/v1/builders/applications") {
            header(HttpHeaders.Authorization, "Bearer ${jwt(user)}")
            contentType(ContentType.Application.Json)
            setBody(
                BuilderApplicationRequest(
                    id = id,
                    acceptTerms = accept,
                    termsVersion = BuilderRules.TERMS_VERSION,
                    photoRetention = true,
                    motivation = "  I shop every Saturday and the cereal aisle here is missing half its packs.  ",
                    shopping = "weekly",
                    region = region,
                    via = "more",
                ),
            )
        }

    private suspend fun HttpClient.me(user: UserId) =
        get("/v1/builders/me") { header(HttpHeaders.Authorization, "Bearer ${jwt(user)}") }.body<BuilderMeResponse>()

    private suspend fun HttpClient.queue() =
        get("/v1/admin/builders/applications") { header("X-Admin-Token", adminToken) }.body<AdminApplicationListResponse>()

    private suspend fun HttpClient.decide(id: String, decision: String, note: String? = null) =
        post("/v1/admin/builders/applications/$id/$decision") {
            header("X-Admin-Token", adminToken)
            header("X-Admin-Actor", "elijah")
            contentType(ContentType.Application.Json)
            setBody(if (note == null) "{}" else "{\"note\": \"$note\"}")
        }

    /** Applies and is accepted, as the owner would in the console. */
    private suspend fun becomeBuilder(component: ServerComponent, user: UserId) {
        val applied = component.builderService.apply(user, isAnonymous = false, ApplicationForm(true, null, ShoppingFrequency.Weekly, LabelRegion.Us))
        val standing = (applied as ApplyOutcome.Applied).standing
        val decided = component.builderService.accept(checkNotNull(standing.application).id, actor = "test")
        check(decided is DecideOutcome.Decided) { "accept: $decided" }
    }

    private suspend fun HttpClient.detail(id: String) =
        get("/v1/admin/food-submissions/$id") { header("X-Admin-Token", adminToken) }.body<FoodSubmissionDetailResponse>()

    @Test
    fun applyingWaitsForTheOwnerAndAcceptingGivesTheRoleAndProUntilADate() = runTest {
        val user = seedUser(ageDays = 10)
        val component = component()
        testApplication {
            application { installApp(component, JwtVerification.Static(verifier), adminConfig = AdminConfig(adminToken)) }
            val client = jsonClient()

            val before = client.me(user)
            assertEquals("none", before.status)
            assertTrue(before.canJoin)
            assertEquals(null, before.application)

            assertEquals(HttpStatusCode.BadRequest, client.sendApplication(user, accept = false).status)
            assertEquals(HttpStatusCode.BadRequest, client.sendApplication(user, region = "atlantis").status)

            val id = UUID.randomUUID().toString()
            val sent = client.sendApplication(user, id = id)
            assertEquals(HttpStatusCode.Created, sent.status)
            val pending = sent.body<BuilderMeResponse>()
            assertEquals("none", pending.status)
            assertEquals("pending", pending.application?.status)
            assertEquals(id, pending.application?.id)
            assertEquals("pending", pending.applyBlock)
            assertFalse(pending.canJoin)
            // Applying alone gives nothing.
            assertEquals(Role.Member, component.userRoleRepository.roleOf(user))
            val waiting = client.get("/v1/me/entitlements") { header(HttpHeaders.Authorization, "Bearer ${jwt(user)}") }.body<MyAccessResponse>()
            assertFalse(waiting.entitlements.any { it.key == "recipe_import" })
            // A second tap is the same application.
            assertEquals(HttpStatusCode.OK, client.sendApplication(user, id = id).status)
            assertEquals(HttpStatusCode.OK, client.sendApplication(user).status)

            val queue = client.queue()
            assertEquals(1, queue.pending)
            val row = queue.applications.single()
            assertEquals(id, row.id)
            assertEquals("uk", row.region)
            assertEquals("weekly", row.shopping)
            assertEquals("I shop every Saturday and the cereal aisle here is missing half its packs.", row.motivation)
            assertNotNull(row.account.accountCreatedAtEpochMs)
            assertEquals(0, row.account.earlierApplications)

            val accepted = client.decide(id, "accept")
            assertEquals(HttpStatusCode.OK, accepted.status)
            assertEquals(0, accepted.body<AdminApplicationDecisionResponse>().pending)
            assertEquals(HttpStatusCode.Conflict, client.decide(id, "decline").status)
            assertEquals(Role.Builder, component.userRoleRepository.roleOf(user))
            assertEquals("elijah", component.userRoleRepository.assignment(user)?.grantedBy)

            val me = client.me(user)
            assertEquals("active", me.status)
            assertEquals("builder", me.trust)
            assertEquals("accepted", me.application?.status)
            assertTrue(me.photoRetention)
            val proUntil = assertNotNull(me.proUntilEpochMs)

            val access = client.get("/v1/me/entitlements") { header(HttpHeaders.Authorization, "Bearer ${jwt(user)}") }.body<MyAccessResponse>()
            val recipe = access.entitlements.single { it.key == "recipe_import" }
            assertEquals(proUntil, recipe.sources.single { it.type == "role" }.expiresAtEpochMs)

            val left = client.post("/v1/builders/leave") { header(HttpHeaders.Authorization, "Bearer ${jwt(user)}") }.body<BuilderMeResponse>()
            assertEquals("left", left.status)
            // Coming back is another application, not a rejoin.
            assertTrue(left.canJoin)
            assertEquals(Role.Member, component.userRoleRepository.roleOf(user))
            val after = client.get("/v1/me/entitlements") { header(HttpHeaders.Authorization, "Bearer ${jwt(user)}") }.body<MyAccessResponse>()
            assertFalse(after.entitlements.any { it.key == "recipe_import" })
            assertEquals(HttpStatusCode.Created, client.sendApplication(user).status)
            assertEquals(1, client.queue().applications.single().account.earlierApplications)
        }
    }

    @Test
    fun anOlderAppsJoinOnlyAppliesAndHearsBackInCodesItKnows() = runTest {
        val user = seedUser(ageDays = 10)
        val component = component()
        testApplication {
            application { installApp(component, JwtVerification.Static(verifier), adminConfig = AdminConfig(adminToken)) }
            val client = jsonClient()

            val joined = client.legacyJoin(user)
            assertEquals(HttpStatusCode.OK, joined.status)
            val me = joined.body<BuilderMeResponse>()
            assertEquals("none", me.status)
            assertFalse(me.canJoin)
            assertEquals("full", me.joinBlock)
            assertEquals(Role.Member, component.userRoleRepository.roleOf(user))
            assertEquals(HttpStatusCode.OK, client.legacyJoin(user).status)

            val row = client.queue().applications.single()
            assertEquals("join", row.via)
            assertEquals(null, row.region)

            val withdrawn = client.post("/v1/builders/applications/withdraw") { header(HttpHeaders.Authorization, "Bearer ${jwt(user)}") }
                .body<BuilderMeResponse>()
            assertEquals("withdrawn", withdrawn.application?.status)
            assertTrue(withdrawn.canJoin)
            assertEquals(0, client.queue().pending)
        }
    }

    @Test
    fun aDeclineCarriesItsNoteAndHoldsTheNextApplicationOff() = runTest {
        val user = seedUser(ageDays = 10)
        val component = component()
        testApplication {
            application { installApp(component, JwtVerification.Static(verifier), adminConfig = AdminConfig(adminToken)) }
            val client = jsonClient()
            val id = client.sendApplication(user).body<BuilderMeResponse>().application!!.id

            assertEquals(HttpStatusCode.OK, client.decide(id, "decline", note = "We have enough Builders in the UK for now.").status)

            val me = client.me(user)
            assertEquals("declined", me.application?.status)
            assertEquals("We have enough Builders in the UK for now.", me.application?.note)
            assertEquals("declined", me.applyBlock)
            assertEquals("full", me.joinBlock)
            val decidedAt = assertNotNull(me.application?.decidedAtEpochMs)
            val reapply = assertNotNull(me.application?.reapplyAtEpochMs)
            assertEquals(BuilderRules.REAPPLY_AFTER.inWholeMilliseconds, reapply - decidedAt)
            assertEquals(reapply, me.availableAtEpochMs)

            val again = client.sendApplication(user)
            assertEquals(HttpStatusCode.Forbidden, again.status)
            assertTrue(again.body<String>().contains("declined"))
            assertEquals(Role.Member, component.userRoleRepository.roleOf(user))
        }
    }

    @Test
    fun aRestrictedAccountCanNotApplyAndOneRestrictedWhileWaitingIsNotAccepted() = runTest {
        val restricted = seedUser(ageDays = 10)
        val waiting = seedUser(ageDays = 10)
        val component = component()
        testApplication {
            application { installApp(component, JwtVerification.Static(verifier), adminConfig = AdminConfig(adminToken)) }
            val client = jsonClient()
            val id = client.sendApplication(waiting).body<BuilderMeResponse>().application!!.id
            listOf(restricted, waiting).forEach { restrict(it) }

            val refused = client.sendApplication(restricted)
            assertEquals(HttpStatusCode.Forbidden, refused.status)
            assertTrue(refused.body<String>().contains("removed"))

            val accept = client.decide(id, "accept")
            assertEquals(HttpStatusCode.Conflict, accept.status)
            assertTrue(accept.body<String>().contains("restricted"))
            assertEquals(Role.Member, component.userRoleRepository.roleOf(waiting))
            assertEquals("pending", client.queue().applications.single().status)
            assertEquals(1, client.queue().applications.single().account.moderationActive)
        }
    }

    @Test
    fun aBuilderWhoJoinedBeforeApplicationsKeepsTheirPlace() = runTest {
        val user = seedUser(ageDays = 40)
        val component = component()
        database.transaction {
            BuilderMembersTable.insert {
                it[userId] = user.value
                it[status] = "active"
                it[joinedAt] = java.time.Instant.now().minusSeconds(86_400)
                it[termsVersion] = BuilderRules.TERMS_VERSION
                it[photoRetention] = false
                it[trustHold] = false
            }
        }
        component.userRoleRepository.setRole(user, Role.Builder, "builder-program", "Joined the Builder program")
        testApplication {
            application { installApp(component, JwtVerification.Static(verifier)) }
            val me = jsonClient().me(user)

            assertEquals("active", me.status)
            assertEquals(null, me.application)
            assertNotNull(me.proUntilEpochMs)
            assertEquals(HttpStatusCode.OK, jsonClient().sendApplication(user).status)
            assertEquals(null, jsonClient().me(user).application)
        }
    }

    private fun restrict(user: UserId) = database.blockingTransaction {
        TransactionManager.current().exec(
            "INSERT INTO moderation_actions (id, user_id, subject_key, kind, scope, reason, created_by) " +
                "VALUES (gen_random_uuid(), '${user.value}', 'test', 'restriction', 'contributions', 'spam', 'test')",
        )
    }

    @Test
    fun aBrandNewAccountWaitsAndAGuestCanNotJoin() = runTest {
        val fresh = seedUser(ageDays = 0)
        testApplication {
            application { installApp(component(), JwtVerification.Static(verifier)) }
            val response = jsonClient().sendApplication(fresh)

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertTrue(response.body<String>().contains("too_new"))
            assertEquals(HttpStatusCode.Forbidden, jsonClient().legacyJoin(fresh).status)
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
            becomeBuilder(component, builder)

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
            becomeBuilder(component, builder)
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
            becomeBuilder(component, builder)

            val first = check(builder, panel).body<VerificationResponse>()
            assertEquals("confirmed", first.outcome)
            assertEquals("unconfirmed", check(builder, panel).body<VerificationResponse>().outcome)
            val wrong = check(builder, panel.map { it.replace("Protein 3g", "Protein 9g") }).body<VerificationResponse>()
            assertEquals(listOf("protein_g"), wrong.missing)

            val me = client.me(builder)
            assertEquals(1, me.accepted30d)
            assertEquals(1, me.stats.checks)
        }
    }

    /** The granola as USDA Branded would have it: the label's numbers per 100 g, its serving first. */
    private fun seedBrandedGranola(kcal: Float = 418.2f): UUID {
        val id = UUID.randomUUID()
        database.blockingTransaction {
            FoodsTable.insert {
                it[FoodsTable.id] = id
                it[sourceKind] = "usda_branded"
                it[sourceId] = id.toString()
                it[name] = "Maple granola"
                it[brand] = "Hillside"
                it[kcal100g] = kcal
                it[protein100g] = 5.45f
                it[carbs100g] = 67.3f
                it[fat100g] = 14.5f
                it[micros] = "{}"
                it[portions] = """[{"label": "2/3 cup", "grams": 55}]"""
            }
        }
        return id
    }

    private suspend fun HttpClient.verify(user: UserId, food: UUID, request: VerificationRequest) = post("/v1/foods/$food/verifications") {
        header(HttpHeaders.Authorization, "Bearer ${jwt(user)}")
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    private suspend fun HttpClient.food(user: UserId, food: UUID) =
        get("/v1/foods/$food") { header(HttpHeaders.Authorization, "Bearer ${jwt(user)}") }.body<FoodResponse>()

    private suspend fun HttpClient.verificationQueue(status: String = "open") =
        get("/v1/admin/food-verifications?status=$status") { header("X-Admin-Token", adminToken) }.body<FoodVerificationQueueResponse>()

    private suspend fun HttpClient.decideVerification(id: String, decision: String, body: String) =
        post("/v1/admin/food-verifications/$id/$decision") {
            header("X-Admin-Token", adminToken)
            header("X-Admin-Actor", "elijah")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private val photos = VerificationPhotosJson(
        front = Base64.getEncoder().encodeToString(TestImages.jpeg),
        label = Base64.getEncoder().encodeToString(TestImages.jpeg),
    )

    @Test
    fun somethingWrongWaitsForAReviewerWhoseAcceptCorrectsTheSameFoodAndMarksItVerified() = runTest {
        val builder = seedUser(ageDays = 10)
        val component = component()
        val food = seedBrandedGranola()
        becomeBuilder(component, builder)
        testApplication {
            application { installApp(component, JwtVerification.Static(verifier), adminConfig = AdminConfig(adminToken)) }
            val client = jsonClient()
            assertNull(client.food(builder, food).checked, "nobody has checked it")

            val sent = client.verify(
                builder,
                food,
                VerificationRequest(
                    id = UUID.randomUUID().toString(),
                    engine = "vision",
                    lines = panel.map { CaptureLineJson(it, 0.9) },
                    problem = VerificationProblemJson(
                        wrong = listOf("calories"),
                        missing = listOf("ingredients"),
                        values = VerificationValuesJson(basisLabel = "2/3 cup", basisGrams = 55.0, kcal = 250.0),
                        note = "New recipe, the pack says 250",
                    ),
                    photos = photos,
                ),
            )
            assertEquals(HttpStatusCode.Created, sent.status)
            val answer = sent.body<VerificationResponse>()
            assertEquals("open", answer.review)
            assertEquals("unconfirmed", answer.outcome, "nothing counts until a reviewer says so")
            assertNull(client.food(builder, food).checked)

            val queue = client.verificationQueue()
            assertEquals(1, queue.open)
            val waiting = queue.verifications.single()
            assertEquals(listOf("calories"), waiting.wrong)
            assertEquals(listOf("ingredients"), waiting.missing)
            assertEquals(454.55, waiting.valuesPer100?.kcal)
            assertEquals("Maple granola", waiting.snapshot?.name)
            assertTrue(waiting.hasFrontPhoto && waiting.hasLabelPhoto)
            val photo = client.get("/v1/admin/food-verifications/${waiting.id}/photos/front") { header("X-Admin-Token", adminToken) }
            assertEquals(HttpStatusCode.OK, photo.status)

            val accepted = client.decideVerification(waiting.id, "accept", """{"values": {"kcal": 454.55}}""")
            assertEquals(HttpStatusCode.OK, accepted.status)
            val decided = accepted.body<FoodVerificationAdminResponse>()
            assertEquals("accepted", decided.status)
            assertEquals("confirmed", decided.outcome)
            assertNotNull(decided.correctionId)
            assertEquals("elijah", decided.resolvedBy)

            val served = client.food(builder, food)
            assertEquals(food.toString(), served.id, "the same food, corrected; never a new one")
            assertEquals(454.55, served.per100g.kcal)
            assertEquals(true, served.checked)
            assertEquals(1, client.me(builder).accepted30d)
            assertEquals(HttpStatusCode.Conflict, client.decideVerification(waiting.id, "accept", "{}").status)

            val reviewers = client.get("/v1/admin/food-review/reviewers") { header("X-Admin-Token", adminToken) }.body<ReviewerCountsResponse>()
            assertEquals(1, reviewers.reviewers.single { it.reviewer == "elijah" }.verificationsAccepted)
        }
    }

    @Test
    fun aCleanCheckMarksTheFoodVerifiedAndAnUnreadOneWithPhotosWaitsForAPerson() = runTest {
        val builder = seedUser(ageDays = 10)
        val component = component()
        val clean = seedBrandedGranola()
        val unread = seedBrandedGranola()
        val old = seedBrandedGranola()
        becomeBuilder(component, builder)
        testApplication {
            application { installApp(component, JwtVerification.Static(verifier), adminConfig = AdminConfig(adminToken)) }
            val client = jsonClient()
            fun request(lines: List<String>, withPhotos: Boolean) =
                VerificationRequest(UUID.randomUUID().toString(), "vision", lines.map { CaptureLineJson(it, 0.9) }, photos = photos.takeIf { withPhotos })

            val confirmed = client.verify(builder, clean, request(panel, withPhotos = true)).body<VerificationResponse>()
            assertEquals("confirmed", confirmed.outcome)
            assertNull(confirmed.review)
            assertEquals(true, client.food(builder, clean).checked)

            val noProtein = panel.map { it.replace("Protein 3g", "Protein 9g") }
            val waiting = client.verify(builder, unread, request(noProtein, withPhotos = true)).body<VerificationResponse>()
            assertEquals("open", waiting.review)
            // An app from before V33 sends no photos: as before, nothing waits.
            val legacy = client.verify(builder, old, request(noProtein, withPhotos = false)).body<VerificationResponse>()
            assertNull(legacy.review)
            assertEquals(1, client.verificationQueue().open)

            val rejected = client.decideVerification(waiting.id, "reject", """{"reason": "The photo is blurry"}""").body<FoodVerificationAdminResponse>()
            assertEquals("rejected", rejected.status)
            assertNull(client.food(builder, unread).checked)
            assertEquals(0, client.verificationQueue().open)
            assertEquals(listOf(waiting.id), client.verificationQueue("decided").verifications.map { it.id })

            val bad = client.verify(builder, old, request(panel, withPhotos = false).copy(problem = VerificationProblemJson(wrong = listOf("taste"))))
            assertEquals(HttpStatusCode.BadRequest, bad.status)
            val both = client.verify(builder, old, request(panel, withPhotos = false).copy(problem = VerificationProblemJson(wrong = listOf("serving"), missing = listOf("serving"))))
            assertEquals(HttpStatusCode.BadRequest, both.status)
        }
    }

    private companion object {
        const val AUTO_APPROVE_PATH = "builders.autoApprove"
    }
}
