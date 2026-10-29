package com.opson.server.routes

import com.opson.server.config.AdminConfig
import com.opson.server.config.DeviceIntegrityConfig
import com.opson.server.config.FoodSourcesConfig
import com.opson.server.db.DatabaseTest
import com.opson.server.db.WaitlistTable
import com.opson.server.di.ServerComponent
import com.opson.server.di.create
import com.opson.server.installApp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.junit.After
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The owner's waitlist routes under `/v1/admin/waitlist`, real graph and real Postgres. */
class WaitlistAdminRoutesTest : DatabaseTest() {

    private val token = "admin-secret"

    @After
    fun cleanTable() {
        database.blockingTransaction { WaitlistTable.deleteAll() }
    }

    @Test
    fun everyRouteNeedsTheAdminToken() = adminApp {
        seed("ada@example.com", "home", "2026-10-01T10:00:00Z")
        listOf<suspend () -> HttpStatusCode>(
            { client.get("/v1/admin/waitlist").status },
            { client.get("/v1/admin/waitlist/export.csv").status },
            { client.get("/v1/admin/waitlist") { header("X-Admin-Token", "wrong") }.status },
            { client.get("/v1/admin/waitlist/export.csv") { header("X-Admin-Token", "wrong") }.status },
            { client.post("/v1/admin/waitlist/remove") { json("""{"email":"ada@example.com"}""") }.status },
        ).forEach { assertEquals(HttpStatusCode.Unauthorized, it()) }
        assertEquals(1, count())
    }

    @Test
    fun theListIsNewestFirstAndPagedWithATotal() = adminApp {
        seed("first@example.com", "home", "2026-10-01T10:00:00Z")
        seed("second@example.com", null, "2026-10-02T10:00:00Z")
        seed("third@example.com", "pricing", "2026-10-03T10:00:00Z")
        seed("gone@example.com", "home", "2026-10-04T10:00:00Z", unsubscribed = true)

        val page = list("?limit=2&offset=0")
        assertEquals(3, page.total)
        assertEquals(listOf("third@example.com", "second@example.com"), page.entries.map { it.email })
        assertEquals(Instant.parse("2026-10-03T10:00:00Z").toEpochMilli(), page.entries.first().createdAtEpochMs)

        assertEquals(listOf("first@example.com"), list("?limit=2&offset=2").entries.map { it.email })
    }

    @Test
    fun theExportIsCsvOldestFirstWithoutUnsubscribed() = adminApp {
        seed("b@example.com", null, "2026-10-02T10:00:00Z")
        seed("a@example.com", "home", "2026-10-01T09:30:00Z")
        seed("gone@example.com", "home", "2026-10-03T10:00:00Z", unsubscribed = true)

        val response = client.get("/v1/admin/waitlist/export.csv") { header("X-Admin-Token", token) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.ContentType].orEmpty().startsWith("text/csv"))
        assertTrue(response.headers[HttpHeaders.ContentDisposition].orEmpty().contains("opson-waitlist.csv"))
        assertEquals(
            "email,source,created_at\r\n" +
                "a@example.com,home,2026-10-01T09:30:00Z\r\n" +
                "b@example.com,,2026-10-02T10:00:00Z\r\n",
            response.bodyAsText(),
        )
    }

    @Test
    fun removeDeletesTheAddressInAnyCase() = adminApp {
        seed("ada@example.com", "home", "2026-10-01T10:00:00Z")
        seed("bob@example.com", "home", "2026-10-01T11:00:00Z")

        val removed = client.post("/v1/admin/waitlist/remove") {
            header("X-Admin-Token", token)
            json("""{"email":" ADA@example.com "}""")
        }
        assertEquals(HttpStatusCode.OK, removed.status)
        assertEquals("""{"removed":true}""", removed.bodyAsText())
        assertEquals(listOf("bob@example.com"), list("").entries.map { it.email })

        val again = client.post("/v1/admin/waitlist/remove") {
            header("X-Admin-Token", token)
            json("""{"email":"ada@example.com"}""")
        }
        assertEquals(HttpStatusCode.NotFound, again.status)

        val blank = client.post("/v1/admin/waitlist/remove") {
            header("X-Admin-Token", token)
            json("""{"email":"  "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, blank.status)
    }

    private fun adminApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val component = ServerComponent::class.create(database, null, FoodSourcesConfig(), Clock.System, DeviceIntegrityConfig())
        application { installApp(component, verification = null, adminConfig = AdminConfig(apiToken = token)) }
        block()
    }

    private suspend fun ApplicationTestBuilder.list(query: String): WaitlistPageResponse =
        Json.decodeFromString(client.get("/v1/admin/waitlist$query") { header("X-Admin-Token", token) }.bodyAsText())

    private fun io.ktor.client.request.HttpRequestBuilder.json(body: String) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun seed(email: String, source: String?, at: String, unsubscribed: Boolean = false) {
        database.blockingTransaction {
            WaitlistTable.insert {
                it[id] = UUID.randomUUID()
                it[WaitlistTable.email] = email
                it[signupSource] = source
                it[createdAt] = Instant.parse(at)
                it[unsubscribedAt] = if (unsubscribed) Instant.parse(at) else null
            }
        }
    }

    private fun count(): Long = database.blockingTransaction { WaitlistTable.selectAll().count() }
}
