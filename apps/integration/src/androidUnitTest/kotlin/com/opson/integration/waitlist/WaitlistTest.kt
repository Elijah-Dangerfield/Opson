package com.opson.integration.waitlist

import com.opson.integration.helpers.Harness
import com.opson.integration.helpers.InProcessServer
import com.opson.integration.helpers.IntegrationTest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The website's waitlist form**, against the contract in
 * docs/plans/website.md: 204 for added, already there and honeypot alike, 400
 * `invalid_email`, 429 `rate_limited` past the per-IP limit; CORS for the
 * site's origin only; and the owner's list and CSV behind the admin token.
 */
class WaitlistTest : IntegrationTest() {

    /** Each test posts from its own address, so the per-IP limit is its own. */
    private val ip = "203.0.113.${(1..254).random()}"

    private suspend fun Harness.signUp(body: String, origin: String = InProcessServer.SITE_ORIGIN): HttpResponse =
        http.post("${server.baseUrl}/v1/waitlist") {
            header(HttpHeaders.Origin, origin)
            header("Fly-Client-IP", ip)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private fun email() = "person-${UUID.randomUUID()}@example.com"

    private fun stored(email: String, server: InProcessServer) =
        server.query("SELECT source FROM waitlist WHERE lower(email) = lower(?)", email)

    @Test
    fun theFormsContract() = integration {
        val email = email()

        val added = signUp("""{"email":"$email","source":"home"}""")
        assertEquals(HttpStatusCode.NoContent, added.status)
        assertEquals(InProcessServer.SITE_ORIGIN, added.headers[HttpHeaders.AccessControlAllowOrigin])
        assertEquals(listOf<Any?>("home"), stored(email, server).map { it["source"] })

        val again = signUp("""{"email":"${email.uppercase()}"}""")
        assertEquals(HttpStatusCode.NoContent, again.status, "a repeat looks exactly like a first sign-up")
        assertEquals(1, stored(email, server).size)

        val bot = email()
        assertEquals(HttpStatusCode.NoContent, signUp("""{"email":"$bot","company":"Acme"}""").status)
        assertEquals(emptyList(), stored(bot, server), "the honeypot stores nothing")

        val invalid = signUp("""{"email":"not an address"}""")
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        assertEquals("""{"error":"invalid_email"}""", invalid.bodyAsText())
        assertEquals(HttpStatusCode.BadRequest, signUp("not json").status)
    }

    @Test
    fun corsAllowsTheSiteOnly() = integration {
        val preflight = http.options("${server.baseUrl}/v1/waitlist") {
            header(HttpHeaders.Origin, InProcessServer.SITE_ORIGIN)
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "content-type")
        }
        assertEquals(HttpStatusCode.OK, preflight.status)
        assertEquals(InProcessServer.SITE_ORIGIN, preflight.headers[HttpHeaders.AccessControlAllowOrigin])

        val strangerPreflight = http.options("${server.baseUrl}/v1/waitlist") {
            header(HttpHeaders.Origin, "https://elsewhere.example")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "content-type")
        }
        assertNull(strangerPreflight.headers[HttpHeaders.AccessControlAllowOrigin])

        val email = email()
        val stranger = signUp("""{"email":"$email"}""", origin = "https://elsewhere.example")
        assertFalse(stranger.status == HttpStatusCode.NoContent, "another site cannot post the form")
        assertEquals(emptyList(), stored(email, server))
    }

    @Test
    fun theSixthPostInTenMinutes_isRateLimited() = integration {
        repeat(5) { assertEquals(HttpStatusCode.NoContent, signUp("""{"email":"${email()}"}""").status) }

        val sixth = signUp("""{"email":"${email()}"}""")

        assertEquals(HttpStatusCode.TooManyRequests, sixth.status)
        assertEquals("""{"error":"rate_limited"}""", sixth.bodyAsText())
    }

    @Test
    fun theOwnersListAndCsv_needTheAdminToken() = integration {
        val email = email()
        signUp("""{"email":"$email","source":"footer"}""")

        assertEquals(HttpStatusCode.Unauthorized, http.get("${server.baseUrl}/v1/admin/waitlist").status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            http.get("${server.baseUrl}/v1/admin/waitlist/export.csv") { header("X-Admin-Token", "wrong") }.status,
        )

        val list = http.get("${server.baseUrl}/v1/admin/waitlist?limit=500") { header("X-Admin-Token", InProcessServer.ADMIN_TOKEN) }
        assertEquals(HttpStatusCode.OK, list.status)
        assertTrue(email in list.bodyAsText())

        val csv = http.get("${server.baseUrl}/v1/admin/waitlist/export.csv") { header("X-Admin-Token", InProcessServer.ADMIN_TOKEN) }
        assertEquals(HttpStatusCode.OK, csv.status)
        assertTrue(csv.headers[HttpHeaders.ContentType].orEmpty().startsWith("text/csv"))
        val lines = csv.bodyAsText().lines()
        assertEquals("email,source,created_at", lines.first())
        assertTrue(lines.any { it.startsWith("$email,footer,") }, "the row is in the export")
    }
}
