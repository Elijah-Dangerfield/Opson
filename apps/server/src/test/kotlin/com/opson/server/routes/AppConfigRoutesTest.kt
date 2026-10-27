package com.opson.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.opson.server.domain.AppConfigSource
import com.opson.server.domain.UserId
import com.opson.server.plugins.BearerVerifier
import com.opson.server.plugins.JwtVerification
import com.opson.server.plugins.SUPABASE_JWT_AUTH
import com.opson.server.plugins.installAuthentication
import com.opson.server.plugins.installSerialization
import com.opson.server.plugins.userId
import com.opson.server.testing.TestJwt
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `GET /v1/app-config` carries the kill switch and the version floor, so it must answer every
 * build. A token that doesn't verify (expired while the phone slept, or minted by another
 * Supabase project, as 0.1.0 and the first 0.2 TestFlight build were) used to get a 401
 * there, and those builds never saw a kill switch.
 */
class AppConfigRoutesTest {
    private val user = UserId(UUID.fromString("22222222-2222-2222-2222-222222222222"))

    /** Answers with who the source was told the caller is. */
    private val source = AppConfigSource { _, userId -> buildJsonObject { put("user", userId?.toString() ?: "anonymous") } }

    @Test
    fun noToken_isServedTheAnonymousTree() = app(TestJwt.verification) {
        assertServed("anonymous", client.get("/v1/app-config"))
    }

    @Test
    fun aValidToken_resolvesTheUser() = app(TestJwt.verification) {
        assertServed(user.toString(), client.get("/v1/app-config") { header(HttpHeaders.Authorization, TestJwt.bearer(user)) })
    }

    @Test
    fun anExpiredToken_isServedTheAnonymousTree() = app(TestJwt.verification) {
        val expired = hs256Token(TEST_SECRET, expiresAt = Date(System.currentTimeMillis() - 60_000))
        assertServed("anonymous", client.get("/v1/app-config") { header(HttpHeaders.Authorization, "Bearer $expired") })
    }

    @Test
    fun aTokenFromAnotherProject_isServedTheAnonymousTree() = app(TestJwt.verification) {
        val foreign = hs256Token("another-project-secret-another-project-secret-01")
        assertServed("anonymous", client.get("/v1/app-config") { header(HttpHeaders.Authorization, "Bearer $foreign") })
    }

    @Test
    fun aMalformedHeader_isServedTheAnonymousTree() = app(TestJwt.verification) {
        assertServed("anonymous", client.get("/v1/app-config") { header(HttpHeaders.Authorization, "Bearer not-a-jwt") })
        assertServed("anonymous", client.get("/v1/app-config") { header(HttpHeaders.Authorization, "Basic dXNlcjpwYXNz") })
    }

    @Test
    fun withoutSupabase_everyoneIsAnonymous() = testApplication {
        application {
            installSerialization()
            routing { appConfigRoutes(source, bearer = null) }
        }
        assertServed("anonymous", client.get("/v1/app-config") { header(HttpHeaders.Authorization, TestJwt.bearer(user)) })
    }

    /** The production path: ES256 keys from the project's JWKS, for both protected routes and app config. */
    @Test
    fun jwksKeys_verifyForProtectedRoutesAndAppConfig() {
        val issuer = "https://jwks-project.supabase.co/auth/v1"
        val ours = ecKeyPair()
        val theirs = ecKeyPair()
        val jwks = File.createTempFile("jwks", ".json").apply {
            deleteOnExit()
            writeText("""{"keys":[${ours.jwk("our-key")}]}""")
        }
        val verification = JwtVerification.Jwks(jwks.toURI().toString(), issuer)
        val good = es256Token(ours, "our-key", issuer)
        val forged = es256Token(theirs, "our-key", issuer)
        val wrongIssuer = es256Token(ours, "our-key", "https://other.supabase.co/auth/v1")

        app(verification) {
            assertServed(user.toString(), client.get("/v1/app-config") { header(HttpHeaders.Authorization, "Bearer $good") })
            assertServed("anonymous", client.get("/v1/app-config") { header(HttpHeaders.Authorization, "Bearer $forged") })
            assertServed("anonymous", client.get("/v1/app-config") { header(HttpHeaders.Authorization, "Bearer $wrongIssuer") })

            val protected = client.get("/protected") { header(HttpHeaders.Authorization, "Bearer $good") }
            assertEquals(HttpStatusCode.OK, protected.status)
            assertEquals(user.toString(), protected.bodyAsText())
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.get("/protected") { header(HttpHeaders.Authorization, "Bearer $forged") }.status,
            )
        }
    }

    private fun app(verification: JwtVerification, block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            installSerialization()
            installAuthentication(verification)
            routing {
                appConfigRoutes(source, BearerVerifier(verification))
                authenticate(SUPABASE_JWT_AUTH) {
                    get("/protected") { call.respondText(call.userId().toString()) }
                }
            }
        }
        block()
    }

    private suspend fun assertServed(expectedUser: String, response: io.ktor.client.statement.HttpResponse) {
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"user":"$expectedUser"}""", response.bodyAsText())
    }

    private fun hs256Token(secret: String, expiresAt: Date = Date(System.currentTimeMillis() + 60_000)): String =
        JWT.create()
            .withIssuer(TEST_ISSUER)
            .withAudience("authenticated")
            .withSubject(user.toString())
            .withExpiresAt(expiresAt)
            .sign(Algorithm.HMAC256(secret))

    private fun es256Token(keys: java.security.KeyPair, keyId: String, issuer: String): String =
        JWT.create()
            .withKeyId(keyId)
            .withIssuer(issuer)
            .withAudience("authenticated")
            .withSubject(user.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
            .sign(Algorithm.ECDSA256(keys.public as ECPublicKey, keys.private as ECPrivateKey))

    private fun ecKeyPair(): java.security.KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun java.security.KeyPair.jwk(keyId: String): String {
        val point = (public as ECPublicKey).w
        fun coordinate(value: java.math.BigInteger): String {
            val bytes = value.toByteArray().let { if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else ByteArray(32 - it.size) + it }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
        return """{"kty":"EC","crv":"P-256","alg":"ES256","use":"sig","kid":"$keyId",""" +
            """"x":"${coordinate(point.affineX)}","y":"${coordinate(point.affineY)}"}"""
    }

    private companion object {
        // TestJwt's issuer and secret, so these tokens differ from its own only where a test says.
        const val TEST_ISSUER = "https://test-project.supabase.co/auth/v1"
        const val TEST_SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
