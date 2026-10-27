package com.opson.server.data

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.opson.server.config.AppleSignInConfig
import com.opson.server.domain.AppleExchangeResult
import com.opson.server.domain.AppleRevokeResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class HttpAppleSignInClientTest {

    private val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val config = AppleSignInConfig(
        teamId = "TEAM123456",
        keyId = "KEY1234567",
        privateKeyPem = "-----BEGIN PRIVATE KEY-----\n" +
            Base64.getMimeEncoder().encodeToString(keys.private.encoded) +
            "\n-----END PRIVATE KEY-----\n",
        clientId = "com.example.app",
        baseUrl = "https://apple.test",
    )
    // Real time, whole seconds: the verifier below checks iat and exp against its own clock.
    private val now = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun exchangeSendsTheCodeWithASignedClientSecretAndKeepsTheRefreshToken() = runTest {
        val client = client {
            json("""{"access_token":"a","token_type":"Bearer","expires_in":3600,"refresh_token":"r-123","id_token":"i"}""")
        }

        assertEquals(AppleExchangeResult.Success("r-123"), client.exchange("code-1"))

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://apple.test/auth/token", request.url.toString())
        val form = request.form()
        assertEquals("com.example.app", form["client_id"])
        assertEquals("code-1", form["code"])
        assertEquals("authorization_code", form["grant_type"])
        assertClientSecretIsValid(form["client_secret"])
    }

    @Test
    fun anExpiredOrUsedCodeIsRejected() = runTest {
        val client = client { json("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest) }

        assertEquals(AppleExchangeResult.Rejected, client.exchange("used"))
    }

    @Test
    fun anAppleOutageIsAFailure() = runTest {
        val client = client { json("", HttpStatusCode.ServiceUnavailable) }

        assertEquals(503, assertIs<AppleExchangeResult.Failure>(client.exchange("code")).statusCode)
    }

    @Test
    fun revokeSendsTheRefreshToken() = runTest {
        val client = client { json("") }

        assertEquals(AppleRevokeResult.Success, client.revoke("r-123"))

        val request = requests.single()
        assertEquals("https://apple.test/auth/revoke", request.url.toString())
        val form = request.form()
        assertEquals("com.example.app", form["client_id"])
        assertEquals("r-123", form["token"])
        assertEquals("refresh_token", form["token_type_hint"])
        assertClientSecretIsValid(form["client_secret"])
    }

    @Test
    fun aRefusedRevokeIsAFailure() = runTest {
        val client = client { json("""{"error":"invalid_client"}""", HttpStatusCode.BadRequest) }

        assertEquals(400, assertIs<AppleRevokeResult.Failure>(client.revoke("r")).statusCode)
    }

    @Test
    fun withoutAKeyNothingIsSent() = runTest {
        val client = HttpAppleSignInClient(config = null, clock = clock, engine = MockEngine { error("must not call Apple") })

        assertEquals(AppleExchangeResult.NotConfigured, client.exchange("code"))
        assertEquals(AppleRevokeResult.NotConfigured, client.revoke("r"))
    }

    @Test
    fun aKeyThatDoesNotParseCountsAsNotConfigured() = runTest {
        val client = HttpAppleSignInClient(
            config = config.copy(privateKeyPem = "-----BEGIN PRIVATE KEY-----\nbm90IGEga2V5\n-----END PRIVATE KEY-----"),
            clock = clock,
            engine = MockEngine { error("must not call Apple") },
        )

        assertEquals(AppleRevokeResult.NotConfigured, client.revoke("r"))
    }

    @Test
    fun theKeyIsNeverInToString() {
        assertEquals(false, config.toString().contains("PRIVATE KEY"))
    }

    private fun client(reply: MockRequestHandleScope.() -> HttpResponseData) = HttpAppleSignInClient(
        config = config,
        clock = clock,
        engine = MockEngine { request ->
            requests += request
            reply()
        },
    )

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.form(): Parameters = (body as FormDataContent).formData

    private fun assertClientSecretIsValid(secret: String?) {
        val jwt = JWT.require(Algorithm.ECDSA256(keys.public as ECPublicKey, null))
            .withIssuer("TEAM123456")
            .withSubject("com.example.app")
            .withAudience("https://appleid.apple.com")
            .build()
            .verify(secret)
        assertEquals("KEY1234567", jwt.keyId)
        assertEquals(now.epochSeconds, jwt.issuedAtAsInstant.epochSecond)
        // Apple caps a client secret at six months; this one lives minutes.
        assertEquals(now.epochSeconds + 300, jwt.expiresAtAsInstant.epochSecond)
    }
}
