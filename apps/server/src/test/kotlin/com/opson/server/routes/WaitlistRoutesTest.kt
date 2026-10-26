package com.opson.server.routes

import com.opson.server.config.FoodSourcesConfig
import com.opson.server.config.WaitlistConfig
import com.opson.server.db.DatabaseTest
import com.opson.server.db.WaitlistTable
import com.opson.server.di.ServerComponent
import com.opson.server.di.create
import com.opson.server.domain.WaitlistLimiter
import com.opson.server.installApp
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.time.Clock
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `POST /v1/waitlist` through the real graph and a real Postgres, against the
 * contract in docs/plans/website.md.
 */
class WaitlistRoutesTest : DatabaseTest() {

    private val site = "http://localhost:4321"

    @After
    fun cleanTable() {
        database.blockingTransaction { WaitlistTable.deleteAll() }
    }

    @Test
    fun aSignUpIsStoredLowerCasedWithItsSource() = waitlistApp {
        val response = signUp("""{"email":"  Ada@Example.COM ","source":"home","company":""}""")

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(listOf("ada@example.com" to "home"), rows())
    }

    @Test
    fun aSourceOutsideThePatternIsStoredAsNull() = waitlistApp {
        signUp("""{"email":"a@b.co","source":"Home Page!"}""")
        signUp("""{"email":"c@d.co","source":"${"x".repeat(33)}"}""")
        signUp("""{"email":"e@f.co"}""")

        assertEquals(listOf(null, null, null), rows().map { it.second })
    }

    @Test
    fun anImplausibleAddressIsInvalidEmail() = waitlistApp {
        val bodies = listOf(
            """{"email":"not-an-email"}""",
            """{"email":"a@b"}""",
            """{"email":"a@@b.co"}""",
            """{"email":"two words@b.co"}""",
            """{"email":"=cmd@b.co"}""",
            """{"email":"${"a".repeat(250)}@b.co"}""",
            """{"email":""}""",
            """{"source":"home"}""",
            """not json""",
            """{"email":"a@b.co","pad":"${"x".repeat(5000)}"}""",
        )
        bodies.forEachIndexed { i, body ->
            val response = signUp(body, ip = "203.0.113.${i + 1}")
            assertEquals(HttpStatusCode.BadRequest, response.status, body.take(40))
            assertEquals("""{"error":"invalid_email"}""", response.bodyAsText())
        }
        assertEquals(emptyList(), rows())
    }

    @Test
    fun theHoneypotAnswersSuccessAndStoresNothing() = waitlistApp {
        val response = signUp("""{"email":"bot@spam.co","company":"Acme"}""")

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(emptyList(), rows())
    }

    @Test
    fun aDuplicateIsNoContentAndOneRow() = waitlistApp {
        assertEquals(HttpStatusCode.NoContent, signUp("""{"email":"ada@example.com","source":"home"}""").status)
        assertEquals(HttpStatusCode.NoContent, signUp("""{"email":"ada@example.com","source":"pricing"}""").status)

        assertEquals(listOf("ada@example.com" to "home"), rows())
    }

    @Test
    fun aDuplicateInAnotherCaseIsTheSameAddress() = waitlistApp {
        signUp("""{"email":"ada@example.com"}""")
        val response = signUp("""{"email":"ADA@Example.Com"}""")

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(1, rows().size)
    }

    @Test
    fun theUniqueIndexIgnoresCaseEvenForARowWrittenByHand() {
        database.blockingTransaction {
            org.jetbrains.exposed.sql.transactions.TransactionManager.current()
                .exec("INSERT INTO waitlist (email) VALUES ('Mixed@Case.co')")
        }
        waitlistApp {
            assertEquals(HttpStatusCode.NoContent, signUp("""{"email":"mixed@case.co"}""").status)
            assertEquals(1, rows().size)
        }
    }

    @Test
    fun theSixthTryInTenMinutesIsRateLimitedPerIp() = waitlistApp {
        repeat(WaitlistLimiter.BURST) { i ->
            assertEquals(HttpStatusCode.NoContent, signUp("""{"email":"p$i@example.com"}""", ip = "198.51.100.7").status)
        }
        val limited = signUp("""{"email":"p9@example.com"}""", ip = "198.51.100.7")

        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals("""{"error":"rate_limited"}""", limited.bodyAsText())
        // Another IP is counted on its own.
        assertEquals(HttpStatusCode.NoContent, signUp("""{"email":"q@example.com"}""", ip = "198.51.100.8").status)
        assertEquals(WaitlistLimiter.BURST + 1, rows().size)
    }

    @Test
    fun honeypotAndInvalidTriesCountTowardTheLimit() = waitlistApp {
        repeat(WaitlistLimiter.BURST) {
            signUp("""{"email":"bot@spam.co","company":"x"}""", ip = "198.51.100.9")
        }
        assertEquals(HttpStatusCode.TooManyRequests, signUp("""{"email":"real@example.com"}""", ip = "198.51.100.9").status)
    }

    @Test
    fun preflightFromAnAllowedOriginIsAnswered() = waitlistApp {
        val response = client.options("/v1/waitlist") {
            header(HttpHeaders.Origin, site)
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "content-type")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(site, response.headers[HttpHeaders.AccessControlAllowOrigin])
        assertTrue(response.headers[HttpHeaders.AccessControlAllowHeaders].orEmpty().contains("Content-Type", ignoreCase = true))
    }

    @Test
    fun preflightFromAnotherOriginIsRefused() = waitlistApp {
        val response = client.options("/v1/waitlist") {
            header(HttpHeaders.Origin, "https://evil.example")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "content-type")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun theFormPostFromTheSiteCarriesItsOrigin() = waitlistApp {
        val allowed = signUp("""{"email":"ada@example.com"}""", origin = site)
        assertEquals(HttpStatusCode.NoContent, allowed.status)
        assertEquals(site, allowed.headers[HttpHeaders.AccessControlAllowOrigin])

        val refused = signUp("""{"email":"eve@example.com"}""", origin = "https://evil.example")
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertEquals(listOf("ada@example.com"), rows().map { it.first })
    }

    @Test
    fun theRestOfTheApiKeepsItsOwnCorsPolicy() = waitlistApp {
        val preflight = client.options("/v1/example") {
            header(HttpHeaders.Origin, "https://evil.example")
            header(HttpHeaders.AccessControlRequestMethod, HttpMethod.Put.value)
            header(HttpHeaders.AccessControlRequestHeaders, "x-admin-token")
        }

        assertEquals(HttpStatusCode.OK, preflight.status)
        assertEquals("*", preflight.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    private fun waitlistApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val component = ServerComponent::class.create(database, null, FoodSourcesConfig(), Clock.System)
        application {
            installApp(component, verification = null, waitlistConfig = WaitlistConfig(setOf(site)))
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.signUp(
        body: String,
        ip: String = "192.0.2.1",
        origin: String? = null,
    ): HttpResponse = client.post("/v1/waitlist") {
        header("Fly-Client-IP", ip)
        origin?.let { header(HttpHeaders.Origin, it) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun rows(): List<Pair<String, String?>> = database.blockingTransaction {
        WaitlistTable.selectAll().orderBy(WaitlistTable.createdAt).map {
            it[WaitlistTable.email] to it[WaitlistTable.signupSource]
        }
    }
}
