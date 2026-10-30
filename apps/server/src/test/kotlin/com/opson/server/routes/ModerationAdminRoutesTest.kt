package com.opson.server.routes

import com.opson.server.config.AdminConfig
import com.opson.server.config.DeviceIntegrityConfig
import com.opson.server.config.FoodSourcesConfig
import com.opson.server.db.DatabaseTest
import com.opson.server.di.ServerComponent
import com.opson.server.di.create
import com.opson.server.domain.UserId
import com.opson.server.installApp
import com.opson.server.plugins.BanGate
import com.opson.server.testing.TestJwt
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.After
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * `/v1/admin/.../moderation` on the real graph and real Postgres, and what each action does to
 * the person's own calls: a restriction refuses contributions with a plain problem, a ban answers
 * everything with the 403 envelope, and lifting either gives the app back.
 */
@OptIn(ExperimentalTime::class)
class ModerationAdminRoutesTest : DatabaseTest() {

    private val token = "admin-secret"
    private val json = Json { ignoreUnknownKeys = true }

    @After
    fun clean() {
        database.blockingTransaction {
            listOf("device_markers", "device_sightings", "moderation_events", "moderation_actions", "contribution_checks")
                .forEach { TransactionManager.current().exec("DELETE FROM $it") }
        }
    }

    @Test
    fun theRoutesNeedTheAdminToken() = app {
        val user = seedAuthUser()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/admin/users/$user/moderation").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/v1/admin/users/$user/moderation") { body("{}") }.status)
    }

    @Test
    fun aRestrictionRefusesContributionsButNotTheApp_andLiftingItGivesThemBack() = app {
        val user = seedAuthUser()
        val created = client.post("/v1/admin/users/$user/moderation") {
            admin()
            body("""{"kind":"restriction","reason":"fake_data","note":"same photo on ten foods"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val action = json.decodeFromString<ModeratedResponse>(created.bodyAsText()).action
        assertEquals("contributions", action.scope)

        assertEquals(HttpStatusCode.OK, client.get("/v1/me") { signedIn(user) }.status)
        val refused = client.post("/v1/foods/submissions") {
            signedIn(user)
            body("""{"id":"${UUID.randomUUID()}"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertTrue("contributions_restricted" in refused.bodyAsText())
        assertFalse("\"reason\"" in refused.bodyAsText(), "not the ban envelope, which would show the blocked screen")

        val detail = json.decodeFromString<UserModerationResponse>(client.get("/v1/admin/users/$user/moderation") { admin() }.bodyAsText())
        assertEquals("restriction", detail.standing?.kind)
        assertFalse(detail.freeProAllowed)
        assertEquals(listOf("restrict"), detail.events.map { it.action })

        assertEquals(HttpStatusCode.OK, client.post("/v1/admin/moderation/${action.id}/lift") { admin(); body("{}") }.status)
        // Past the gate: the submission is now refused for its body instead.
        val after = client.post("/v1/foods/submissions") {
            signedIn(user)
            body("""{"id":"${UUID.randomUUID()}"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, after.status)
        assertNull(json.decodeFromString<UserModerationResponse>(client.get("/v1/admin/users/$user/moderation") { admin() }.bodyAsText()).standing)
    }

    @Test
    fun aBanAnswersEveryCallWithTheEnvelope_andMarksTheDeviceForTheNextAccount() = app {
        val user = seedAuthUser()
        // A contribution from this phone records its install key.
        client.post("/v1/foods/submissions") {
            signedIn(user)
            header(HEADER_DEVICE_KEY, "keychain-1")
            body("""{"id":"${UUID.randomUUID()}"}""")
        }
        val created = client.post("/v1/admin/users/$user/moderation") {
            admin()
            body("""{"kind":"ban","reason":"ban_evasion","markDevices":true}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)

        val me = client.get("/v1/me") { signedIn(user) }
        assertEquals(HttpStatusCode.Forbidden, me.status)
        assertTrue("\"reason\":\"banned\"" in me.bodyAsText())

        val next = seedAuthUser()
        assertEquals(HttpStatusCode.OK, client.get("/v1/me") { signedIn(next) }.status, "logging is never blocked by device")
        val refused = client.post("/v1/foods/submissions") {
            signedIn(next)
            header(HEADER_DEVICE_KEY, "keychain-1")
            body("""{"id":"${UUID.randomUUID()}"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, refused.status)

        val marker = json.decodeFromString<UserModerationResponse>(client.get("/v1/admin/users/$user/moderation") { admin() }.bodyAsText())
            .markers.single()
        assertEquals(HttpStatusCode.NoContent, client.post("/v1/admin/devices/${marker.id}/clear") { admin(); body("{}") }.status)
        val allowed = client.post("/v1/foods/submissions") {
            signedIn(next)
            header(HEADER_DEVICE_KEY, "keychain-1")
            body("""{"id":"${UUID.randomUUID()}"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, allowed.status)
    }

    @Test
    fun badRequestsAreSaidPlainly() = app {
        val user = seedAuthUser()
        suspend fun moderate(body: String) = client.post("/v1/admin/users/$user/moderation") { admin(); body(body) }
        assertTrue("invalid_kind" in moderate("""{"kind":"exile","reason":"spam"}""").bodyAsText())
        assertTrue("invalid_reason" in moderate("""{"kind":"ban","reason":"vibes"}""").bodyAsText())
        assertTrue("invalid_until" in moderate("""{"kind":"suspension","reason":"spam"}""").bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.post("/v1/admin/users/${UUID.randomUUID()}/moderation") {
            admin()
            body("""{"kind":"ban","reason":"spam"}""")
        }.status)
        assertEquals(HttpStatusCode.BadRequest, client.put("/v1/admin/moderation/${UUID.randomUUID()}/appeal") {
            admin()
            body("""{"state":"maybe"}""")
        }.status)
        assertTrue("invalid_reason" in client.post("/v1/admin/users/$user/contributions/rollback") { admin(); body("{}") }.bodyAsText())
        val rolledBack = client.post("/v1/admin/users/$user/contributions/rollback") { admin(); body("""{"reason":"fake data"}""") }
        assertEquals(HttpStatusCode.OK, rolledBack.status)
        assertEquals(setOf("recipe", "report", "submission", "verification"), json.decodeFromString<RollbackResponse>(rolledBack.bodyAsText()).rolledBack.map { it.kind }.toSet())
    }

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val component = ServerComponent::class.create(database, null, FoodSourcesConfig(), Clock.System, DeviceIntegrityConfig())
        application {
            installApp(
                component,
                verification = TestJwt.verification,
                // Steps past the gate's 30-second cache on every read, so a ban bites on the next call.
                banGate = BanGate(component.moderationRepository, appealUrl = null, steppingClock()),
                adminConfig = AdminConfig(apiToken = token),
            )
        }
        block()
    }

    private fun steppingClock() = object : Clock {
        private var at = Clock.System.now()

        override fun now(): Instant {
            at += BanGate.CACHE_TTL + 1.seconds
            return at
        }
    }

    private fun HttpRequestBuilder.admin() {
        header("X-Admin-Token", token)
        header("X-Admin-Actor", "elijah")
    }

    private fun HttpRequestBuilder.signedIn(user: UserId) {
        header(HttpHeaders.Authorization, "Bearer ${TestJwt.token(user)}")
        header("X-Platform", "ios")
    }

    private fun HttpRequestBuilder.body(text: String) {
        contentType(ContentType.Application.Json)
        setBody(text)
    }
}
