package com.opson.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.opson.server.config.FoodSourcesConfig
import com.opson.server.db.DatabaseTest
import com.opson.server.db.ProfilesTable
import com.opson.server.di.ServerComponent
import com.opson.server.di.create
import com.opson.server.domain.UserId
import com.opson.server.plugins.JwtVerification
import com.opson.server.routes.MeResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.deleteAll
import org.junit.After
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Full-stack test: boots the **real DI graph** over **real Postgres** through
 * the same [installApp] seam production uses, with a [JwtVerification.Static]
 * verifier swapped in for the JWKS one. Proves the component constructs against
 * a live DB and that auth + repository + route integrate end-to-end.
 */
@OptIn(kotlin.time.ExperimentalTime::class)
class FullStackMeTest : DatabaseTest() {

    private val issuer = "https://test.supabase.co/auth/v1"
    private val secret = "0123456789abcdef0123456789abcdef0123456789abcdef"
    private val verifier = JWT.require(Algorithm.HMAC256(secret))
        .withIssuer(issuer)
        .withAudience("authenticated")
        .build()

    @After
    fun cleanTables() {
        database.blockingTransaction { ProfilesTable.deleteAll() }
    }

    @Test
    fun getMe_createsAndReturnsProfile_throughRealGraph() = runTest {
        val userId = seedAuthUser()
        val component = ServerComponent::class.create(database, null, FoodSourcesConfig())

        testApplication {
            application { installApp(component, JwtVerification.Static(verifier)) }
            val client = createClient {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }

            val resp = client.get("/v1/me") {
                header(HttpHeaders.Authorization, "Bearer ${jwt(userId)}")
            }

            assertEquals(HttpStatusCode.OK, resp.status)
            assertEquals(userId.toString(), resp.body<MeResponse>().userId)
        }
    }

    private fun jwt(userId: UserId): String = JWT.create()
        .withIssuer(issuer)
        .withAudience("authenticated")
        .withSubject(userId.value.toString())
        .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
        .sign(Algorithm.HMAC256(secret))
}
