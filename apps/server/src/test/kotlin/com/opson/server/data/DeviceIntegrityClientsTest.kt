package com.opson.server.data

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.opson.server.config.DeviceCheckConfig
import com.opson.server.config.DeviceIntegrityConfig
import com.opson.server.config.PlayIntegrityConfig
import com.opson.server.domain.DeviceVerdict
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class DeviceIntegrityClientsTest {

    private val now = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val requests = mutableListOf<HttpRequestData>()

    private fun pem(encoded: ByteArray) =
        "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(encoded) + "\n-----END PRIVATE KEY-----\n"

    private fun body(request: HttpRequestData): String = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    // ── DeviceCheck ────────────────────────────────────────────────────────

    private val ecKeys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val deviceCheck = DeviceCheckConfig(teamId = "TEAM123456", keyId = "DCKEY12345", privateKeyPem = pem(ecKeys.private.encoded))

    private fun apple(status: HttpStatusCode, text: String) = HttpDeviceCheckClient(
        deviceCheck, clock,
        MockEngine { request ->
            requests += request
            respond(text, status)
        },
    )

    @Test
    fun deviceCheckReadsBitZeroWithASignedToken() = runTest {
        val verdict = apple(HttpStatusCode.OK, """{"bit0":true,"bit1":false,"last_update_time":"2026-10"}""").read("tok", null)
        assertEquals(DeviceVerdict.Read(marked = true, trusted = true), verdict)

        val request = requests.single()
        assertEquals("https://api.devicecheck.apple.com/v1/query_two_bits", request.url.toString())
        val jwt = request.headers[HttpHeaders.Authorization]!!.removePrefix("Bearer ")
        val decoded = JWT.require(Algorithm.ECDSA256(ecKeys.public as ECPublicKey, null)).withIssuer("TEAM123456").build().verify(jwt)
        assertEquals("DCKEY12345", decoded.keyId)
        assertEquals("tok", Json.parseToJsonElement(body(request)).jsonObject["device_token"]?.let { (it as JsonPrimitive).content })
    }

    @Test
    fun aDeviceNeverMarkedIsUnmarked_andABadTokenIsInvalid() = runTest {
        assertEquals(DeviceVerdict.Read(marked = false, trusted = true), apple(HttpStatusCode.OK, "Failed to find bit state").read("tok", null))
        assertEquals(DeviceVerdict.Invalid, apple(HttpStatusCode.BadRequest, "Missing or incorrectly formatted device token payload").read("x", null))
        assertEquals(DeviceVerdict.Failed, apple(HttpStatusCode.InternalServerError, "").read("x", null))
    }

    @Test
    fun deviceCheckWritesOnlyBitZero() = runTest {
        assertTrue(apple(HttpStatusCode.OK, "").writeMarked("tok", marked = true))
        val sent = Json.parseToJsonElement(body(requests.single())).jsonObject
        assertEquals("https://api.devicecheck.apple.com/v1/update_two_bits", requests.single().url.toString())
        assertEquals(JsonPrimitive(true), sent["bit0"])
        assertNull(sent["bit1"])
    }

    // ── Play Integrity ─────────────────────────────────────────────────────

    private val rsaKeys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val serviceAccount = """
        {"type":"service_account","client_email":"integrity@opson.iam.gserviceaccount.com",
         "private_key":"${pem(rsaKeys.private.encoded).replace("\n", "\\n")}",
         "token_uri":"https://oauth2.googleapis.com/token"}
    """.trimIndent()
    private val play = PlayIntegrityConfig(packageName = "com.opson", serviceAccountJson = serviceAccount, deviceRecall = true)

    private fun verdictJson(hash: String = "sub-1", at: Instant = now, recall: Boolean = false, device: String = "MEETS_DEVICE_INTEGRITY") = """
        {"tokenPayloadExternal":{
          "requestDetails":{"requestPackageName":"com.opson","requestHash":"$hash","timestampMillis":"${at.toEpochMilliseconds()}"},
          "appIntegrity":{"appRecognitionVerdict":"PLAY_RECOGNIZED"},
          "deviceIntegrity":{"deviceRecognitionVerdict":["$device"],"deviceRecall":{"values":{"bitFirst":$recall,"bitSecond":false,"bitThird":false}}}
        }}
    """.trimIndent()

    private fun google(decoded: String, status: HttpStatusCode = HttpStatusCode.OK) = HttpPlayIntegrityClient(
        play, clock,
        MockEngine { request ->
            requests += request
            if (request.url.host == "oauth2.googleapis.com") {
                respond("""{"access_token":"g-token","expires_in":3600}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(decoded, status)
            }
        },
    )

    @Test
    fun playIntegrityDecodesWithAServiceAccountTokenAndReadsDeviceRecall() = runTest {
        val client = google(verdictJson(recall = true))
        assertEquals(DeviceVerdict.Read(marked = true, trusted = true), client.read("tok", "sub-1"))
        assertEquals(DeviceVerdict.Read(marked = true, trusted = true), client.read("tok", "sub-1"))

        // One OAuth exchange, kept for the second call.
        assertEquals(1, requests.count { it.url.host == "oauth2.googleapis.com" })
        val decode = requests.last()
        assertEquals("https://playintegrity.googleapis.com/v1/com.opson:decodeIntegrityToken", decode.url.toString())
        assertEquals("Bearer g-token", decode.headers[HttpHeaders.Authorization])
    }

    @Test
    fun aVerdictForAnotherRequestOrTooOldIsInvalid_andAWeakDeviceIsUntrustedNotBlocked() = runTest {
        assertEquals(DeviceVerdict.Invalid, google(verdictJson(hash = "someone-else")).read("tok", "sub-1"))
        assertEquals(DeviceVerdict.Invalid, google(verdictJson(at = now - 30.minutes)).read("tok", "sub-1"))
        assertEquals(DeviceVerdict.Invalid, google("", HttpStatusCode.BadRequest).read("tok", "sub-1"))
        assertEquals(DeviceVerdict.Read(marked = false, trusted = false), google(verdictJson(device = "MEETS_BASIC_INTEGRITY")).read("tok", "sub-1"))
    }

    @Test
    fun deviceRecallIsWrittenOnlyWhenTurnedOn() = runTest {
        assertTrue(google("{}").writeMarked("tok", marked = true))
        val write = requests.last()
        assertEquals("https://playintegrity.googleapis.com/v1/com.opson/deviceRecall:write", write.url.toString())
        assertEquals(JsonPrimitive(true), Json.parseToJsonElement(body(write)).jsonObject["newValues"]!!.jsonObject["bitFirst"])

        val off = HttpPlayIntegrityClient(play.copy(deviceRecall = false), clock, MockEngine { error("must not call Google") })
        assertFalse(off.writeMarked("tok", marked = true))
    }

    @Test
    fun nothingIsConfiguredUntilItsKeysAre() {
        val none = ConfiguredDeviceIntegrity(DeviceIntegrityConfig(), clock)
        assertEquals(emptyList(), none.configured)
        assertNull(none.forPlatform("ios"))
        assertNull(none.forPlatform("android"))
    }
}
