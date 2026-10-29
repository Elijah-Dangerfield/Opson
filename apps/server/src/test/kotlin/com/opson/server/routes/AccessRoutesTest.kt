package com.opson.server.routes

import com.opson.server.config.AdminConfig
import com.opson.server.config.FoodSourcesConfig
import com.opson.server.db.DatabaseTest
import com.opson.server.di.ServerComponent
import com.opson.server.di.create
import com.opson.server.domain.Entitlement
import com.opson.server.domain.UserId
import com.opson.server.installApp
import com.opson.server.plugins.ProblemResponse
import com.opson.server.testing.TestImages
import com.opson.server.testing.TestJwt
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Roles and entitlements end to end on the real graph and the V28 schema: the seeded policy,
 * the admin API that changes one account, `GET /v1/me/entitlements` that reports it, and the
 * photo route that enforces it.
 */
@OptIn(ExperimentalTime::class)
class AccessRoutesTest : DatabaseTest() {

    private class MutableClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private val token = "admin-secret"
    private val json = Json { ignoreUnknownKeys = true }
    private val clock = MutableClock(Clock.System.now())

    @After
    fun clean() {
        database.blockingTransaction {
            val tx = TransactionManager.current()
            tx.exec("DELETE FROM access_audit")
            tx.exec("DELETE FROM entitlement_grants")
            tx.exec("DELETE FROM user_roles")
            tx.exec("UPDATE app_config_values SET value_jsonb = '[\"photo_estimate\"]'::jsonb WHERE path = 'entitlements.everyone'")
        }
    }

    @Test
    fun aMemberHasWhatEveryoneHas_photoEstimatesToday() = app {
        val user = seedAuthUser()

        val me = me(user)

        assertEquals("member", me.role)
        assertEquals(emptyList(), me.permissions)
        assertEquals(listOf("photo_estimate"), me.entitlements.map { it.key })
        assertEquals("everyone", me.entitlements.single().sources.single().type)
    }

    @Test
    fun aProGrantGivesTheWholeBundle_andItsRemovalTakesItBack() = app {
        val user = seedAuthUser()

        val added = client.post("/v1/admin/users/$user/grants") {
            asAdmin()
            json("""{"kind":"bundle","key":"pro","source":"founding","reason":"founding member"}""")
        }
        assertEquals(HttpStatusCode.Created, added.status)
        val grant = json.decodeFromString<GrantResponse>(added.bodyAsText())

        val me = me(user)
        assertEquals(Entitlement.entries.map { it.key }, me.entitlements.map { it.key })
        val friends = me.entitlements.single { it.key == "share_friends" }.sources.single()
        assertEquals("grant" to "pro", friends.type to friends.bundle)

        assertEquals(HttpStatusCode.NoContent, client.delete("/v1/admin/users/$user/grants/${grant.id}") { asAdmin() }.status)
        assertEquals(listOf("photo_estimate"), me(user).entitlements.map { it.key })
    }

    @Test
    fun aRevocationShutsThePhotoRoute_evenThoughEveryoneHasIt() = app {
        val user = seedAuthUser()
        // Entitled, the route gets as far as the provider, which this test server lacks.
        assertEquals(HttpStatusCode.ServiceUnavailable, photo(user).status)

        client.post("/v1/admin/users/$user/grants") {
            asAdmin()
            json("""{"kind":"entitlement","key":"photo_estimate","effect":"revoke","reason":"abuse"}""")
        }

        val denied = photo(user)
        assertEquals(HttpStatusCode.Forbidden, denied.status)
        assertEquals("entitlement_required", json.decodeFromString<ProblemResponse>(denied.bodyAsText()).error.code)
        assertTrue(me(user).entitlements.isEmpty())

        val access = access(user)
        assertEquals(listOf("photo_estimate"), access.revoked.map { it.key })
        assertEquals("abuse", access.revoked.single().revocations.single().reason)
    }

    @Test
    fun aBuilderGetsTheirPermissionAndProThroughTheRole() = app {
        val user = seedAuthUser()

        val set = setRole(user, """{"role":"builder","reason":"builder program"}""")
        assertEquals(HttpStatusCode.OK, set.status)

        val me = me(user)
        assertEquals("builder", me.role)
        assertEquals(listOf("food.contribute"), me.permissions)
        val source = me.entitlements.single { it.key == "recipe_import" }.sources.single()
        assertEquals(Triple("role", "builder", "pro"), Triple(source.type, source.role, source.bundle))

        val access = access(user)
        assertEquals("builder program", access.roleAssignment?.reason)
        assertEquals(listOf("set_role"), access.audit.map { it.action })
        assertEquals("test-admin", access.audit.single().actor)

        setRole(user, """{"role":"member"}""")
        assertEquals("member", me(user).role)
    }

    @Test
    fun theOwnerCanEditTheCatalogDirectly() = app {
        val user = seedAuthUser()
        setRole(user, """{"role":"owner","reason":"Opson owner"}""")

        assertTrue("food.edit.direct" in me(user).permissions)
    }

    @Test
    fun theAdminApiRefusesWhatItCannotStore() = app {
        val user = seedAuthUser()
        suspend fun code(response: HttpResponse) =
            response.status to json.decodeFromString<ProblemResponse>(response.bodyAsText()).error.code
        suspend fun grant(body: String) = code(
            client.post("/v1/admin/users/$user/grants") {
                asAdmin()
                json(body)
            },
        )

        assertEquals(HttpStatusCode.BadRequest to "unknown_entitlement", grant("""{"kind":"entitlement","key":"teleport"}"""))
        assertEquals(HttpStatusCode.BadRequest to "unknown_bundle", grant("""{"kind":"bundle","key":"plus"}"""))
        assertEquals(HttpStatusCode.BadRequest to "invalid_effect", grant("""{"kind":"bundle","key":"pro","effect":"revoke"}"""))
        assertEquals(HttpStatusCode.BadRequest to "invalid_kind", grant("""{"kind":"plan","key":"pro"}"""))
        assertEquals(HttpStatusCode.BadRequest to "invalid_source", grant("""{"kind":"bundle","key":"pro","source":"App Store"}"""))
        val past = clock.now().toEpochMilliseconds() - 1
        assertEquals(HttpStatusCode.BadRequest to "invalid_expiry", grant("""{"kind":"bundle","key":"pro","expiresAtEpochMs":$past}"""))
        assertEquals(HttpStatusCode.BadRequest to "invalid_role", code(setRole(user, """{"role":"wizard"}""")))
        val nobody = UserId(UUID.randomUUID())
        assertEquals(HttpStatusCode.NotFound to "not_found", code(setRole(nobody, """{"role":"admin"}""")))
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/admin/users/$user/access").status)
    }

    @Test
    fun thePolicyIsConfig_validatedOnTheWayIn_andLiveOnTheNextRefresh() = app {
        val user = seedAuthUser()

        assertEquals(HttpStatusCode.BadRequest, setEveryone("""["photo_estimates"]""").status)

        val overview = json.decodeFromString<EntitlementsOverviewResponse>(
            client.get("/v1/admin/entitlements") { asAdmin() }.bodyAsText(),
        )
        assertEquals(listOf("photo_estimate"), overview.policy.everyone)
        assertEquals("entitlements.everyone", overview.paths["everyone"])
        assertEquals(listOf("pro"), overview.policy.roles["builder"])

        assertEquals(HttpStatusCode.OK, setEveryone("[]").status)
        clock.instant += 31.seconds

        assertTrue(me(user).entitlements.isEmpty())
        assertEquals(HttpStatusCode.Forbidden, photo(user).status)
    }

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val component = ServerComponent::class.create(database, null, FoodSourcesConfig(), clock)
        application {
            installApp(component, verification = TestJwt.verification, adminConfig = AdminConfig(apiToken = token))
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.me(user: UserId): MyAccessResponse {
        val response = client.get("/v1/me/entitlements") { header(HttpHeaders.Authorization, TestJwt.bearer(user)) }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.access(user: UserId): UserAccessResponse =
        json.decodeFromString(client.get("/v1/admin/users/$user/access") { asAdmin() }.bodyAsText())

    private suspend fun ApplicationTestBuilder.setRole(user: UserId, body: String): HttpResponse =
        client.put("/v1/admin/users/$user/role") {
            asAdmin()
            json(body)
        }

    private suspend fun ApplicationTestBuilder.setEveryone(value: String): HttpResponse =
        client.put("/v1/admin/config/flags/entitlements.everyone") {
            asAdmin()
            json("""{"value":$value}""")
        }

    private suspend fun ApplicationTestBuilder.photo(user: UserId): HttpResponse = client.post("/v1/describe/photo") {
        header(HttpHeaders.Authorization, TestJwt.bearer(user))
        json("""{"image":"${Base64.getEncoder().encodeToString(TestImages.jpeg)}"}""")
    }

    private fun HttpRequestBuilder.asAdmin() {
        header("X-Admin-Token", token)
        header("X-Admin-Actor", "test-admin")
    }

    private fun HttpRequestBuilder.json(text: String) {
        contentType(ContentType.Application.Json)
        setBody(text)
    }
}
