package com.opson.server.data

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.opson.server.config.DeviceCheckConfig
import com.opson.server.config.DeviceIntegrityConfig
import com.opson.server.config.PlayIntegrityConfig
import com.opson.server.di.ServerScope
import com.opson.server.domain.DeviceIntegrity
import com.opson.server.domain.DeviceIntegrityVerifier
import com.opson.server.domain.DeviceVerdict
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import me.tatarka.inject.annotations.Inject
import org.slf4j.LoggerFactory
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/** The verifiers [DeviceIntegrityConfig] turns on. Neither exists until its keys are set. */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@OptIn(ExperimentalTime::class)
class ConfiguredDeviceIntegrity(
    private val ios: DeviceIntegrityVerifier?,
    private val android: DeviceIntegrityVerifier?,
) : DeviceIntegrity {

    @Inject
    constructor(config: DeviceIntegrityConfig, clock: Clock) : this(
        ios = config.deviceCheck?.let { HttpDeviceCheckClient(it, clock, engine = null) },
        android = config.playIntegrity?.let { HttpPlayIntegrityClient(it, clock, engine = null) },
    )

    override fun forPlatform(platform: String): DeviceIntegrityVerifier? = when (platform) {
        "ios" -> ios
        "android" -> android
        else -> null
    }

    override val configured: List<String> get() = listOfNotNull(ios?.signal, android?.signal)
}

/**
 * Apple's DeviceCheck server API: `query_two_bits` and `update_two_bits`, authenticated with a
 * short-lived ES256 JWT signed by the DeviceCheck key. Only bit 0 is written ("barred from
 * contributing"); bit 1 is left as it is. A device whose bits were never set answers 200 with
 * a plain-text body, which reads as unmarked.
 */
@OptIn(ExperimentalTime::class)
class HttpDeviceCheckClient(
    private val config: DeviceCheckConfig,
    private val clock: Clock,
    engine: HttpClientEngine?,
) : DeviceIntegrityVerifier {

    override val signal: String = "devicecheck"

    private val logger = LoggerFactory.getLogger(HttpDeviceCheckClient::class.java)
    private val client = httpClient(engine)
    private val signingKey = try {
        HttpAppleSignInClient.parsePrivateKey(config.privateKeyPem)
    } catch (e: Exception) {
        logger.error("APPLE_DEVICECHECK_PRIVATE_KEY isn't a PKCS#8 EC key (the .p8 file's contents); DeviceCheck is off", e)
        null
    }

    override suspend fun read(token: String, requestHash: String?): DeviceVerdict {
        val jwt = jwt() ?: return DeviceVerdict.Failed
        val response = client.post("${config.baseUrl}/v1/query_two_bits") {
            header(HttpHeaders.Authorization, "Bearer $jwt")
            contentType(ContentType.Application.Json)
            setBody(body(token).toString())
        }
        val text = response.bodyAsText()
        return when (response.status.value) {
            200 -> {
                val bits = runCatching { JSON.parseToJsonElement(text).jsonObject }.getOrNull()
                DeviceVerdict.Read(marked = bits?.get("bit0")?.jsonPrimitive?.booleanOrNull == true, trusted = true)
            }
            // A malformed, expired or another team's token.
            400 -> DeviceVerdict.Invalid
            else -> {
                logger.warn("DeviceCheck query answered {}", response.status.value)
                DeviceVerdict.Failed
            }
        }
    }

    override suspend fun writeMarked(token: String, marked: Boolean): Boolean {
        val jwt = jwt() ?: return false
        val response = client.post("${config.baseUrl}/v1/update_two_bits") {
            header(HttpHeaders.Authorization, "Bearer $jwt")
            contentType(ContentType.Application.Json)
            setBody(JsonObject(body(token) + ("bit0" to JsonPrimitive(marked))).toString())
        }
        if (response.status.value != 200) logger.warn("DeviceCheck update answered {}", response.status.value)
        return response.status.value == 200
    }

    private fun body(token: String): JsonObject = buildJsonObject {
        put("device_token", JsonPrimitive(token))
        put("transaction_id", JsonPrimitive(UUID.randomUUID().toString()))
        put("timestamp", JsonPrimitive(clock.now().toEpochMilliseconds()))
    }

    private fun jwt(): String? {
        val key = signingKey ?: return null
        return JWT.create()
            .withKeyId(config.keyId)
            .withIssuer(config.teamId)
            .withIssuedAt(Date.from(clock.now().toJavaInstant()))
            .sign(Algorithm.ECDSA256(null, key))
    }
}

/**
 * Google Play Integrity, standard requests: `decodeIntegrityToken` on Google's server, and
 * device recall's `deviceRecall:write` when [PlayIntegrityConfig.deviceRecall] is on. The call is
 * authorised with an OAuth token minted from the service account (RS256 JWT bearer grant), kept
 * until a minute before it expires.
 *
 * A verdict is [DeviceVerdict.Invalid] when it names another package, isn't bound to this request
 * (`requestHash`, the submission id), or is more than [MAX_TOKEN_AGE] old. `trusted` is a
 * Play-recognised app on a device meeting device integrity; the gate counts the rest, it never
 * blocks on it. `marked` is device recall's first bit.
 *
 * Device recall is a beta: the field names follow Google's documentation as of 2026-10, and
 * should be checked against it when Opson is enrolled.
 */
@OptIn(ExperimentalTime::class)
class HttpPlayIntegrityClient(
    private val config: PlayIntegrityConfig,
    private val clock: Clock,
    engine: HttpClientEngine?,
) : DeviceIntegrityVerifier {

    override val signal: String = "play_integrity"

    private val logger = LoggerFactory.getLogger(HttpPlayIntegrityClient::class.java)
    private val client = httpClient(engine)
    private val account: ServiceAccount? = try {
        ServiceAccount.parse(config.serviceAccountJson)
    } catch (e: Exception) {
        logger.error("PLAY_INTEGRITY_SERVICE_ACCOUNT_JSON isn't a service account key; Play Integrity is off", e)
        null
    }
    private val tokenLock = Mutex()
    private var accessToken: Pair<String, Instant>? = null

    override suspend fun read(token: String, requestHash: String?): DeviceVerdict {
        val bearer = bearer() ?: return DeviceVerdict.Failed
        val response = client.post("$API/v1/${config.packageName}:decodeIntegrityToken") {
            header(HttpHeaders.Authorization, "Bearer $bearer")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("integrity_token", JsonPrimitive(token)) }.toString())
        }
        val text = response.bodyAsText()
        return when (response.status.value) {
            200 -> verdict(text, requestHash)
            400 -> DeviceVerdict.Invalid
            else -> {
                logger.warn("Play Integrity decode answered {}", response.status.value)
                DeviceVerdict.Failed
            }
        }
    }

    override suspend fun writeMarked(token: String, marked: Boolean): Boolean {
        if (!config.deviceRecall) return false
        val bearer = bearer() ?: return false
        val response = client.post("$API/v1/${config.packageName}/deviceRecall:write") {
            header(HttpHeaders.Authorization, "Bearer $bearer")
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("integrityToken", JsonPrimitive(token))
                    put("newValues", buildJsonObject { put("bitFirst", JsonPrimitive(marked)) })
                }.toString(),
            )
        }
        if (response.status.value != 200) logger.warn("Play Integrity device recall write answered {}", response.status.value)
        return response.status.value == 200
    }

    internal fun verdict(text: String, requestHash: String?): DeviceVerdict {
        val payload = runCatching { JSON.parseToJsonElement(text).jsonObject["tokenPayloadExternal"]?.jsonObject }.getOrNull()
            ?: return DeviceVerdict.Invalid
        val request = payload["requestDetails"]?.jsonObject
        val packageName = request?.get("requestPackageName")?.jsonPrimitive?.contentOrNull
        val hash = request?.get("requestHash")?.jsonPrimitive?.contentOrNull
        val at = request?.get("timestampMillis")?.jsonPrimitive?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
        if (packageName != config.packageName) return DeviceVerdict.Invalid
        if (requestHash != null && hash != requestHash) return DeviceVerdict.Invalid
        if (at == null || clock.now() - Instant.fromEpochMilliseconds(at) > MAX_TOKEN_AGE) return DeviceVerdict.Invalid
        val app = payload["appIntegrity"]?.jsonObject?.get("appRecognitionVerdict")?.jsonPrimitive?.contentOrNull
        val device = payload["deviceIntegrity"]?.jsonObject
        val labels = (device?.get("deviceRecognitionVerdict") as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        val recall = device?.get("deviceRecall")?.jsonObject?.get("values")?.jsonObject
        return DeviceVerdict.Read(
            marked = recall?.get("bitFirst")?.jsonPrimitive?.booleanOrNull == true,
            trusted = app == "PLAY_RECOGNIZED" && "MEETS_DEVICE_INTEGRITY" in labels,
        )
    }

    private suspend fun bearer(): String? = tokenLock.withLock {
        val now = clock.now()
        accessToken?.takeIf { it.second - 1.minutes > now }?.let { return@withLock it.first }
        val sa = account ?: return@withLock null
        val assertion = JWT.create()
            .withIssuer(sa.clientEmail)
            .withAudience(sa.tokenUri)
            .withClaim("scope", SCOPE)
            .withIssuedAt(Date.from(now.toJavaInstant()))
            .withExpiresAt(Date.from((now + 60.minutes).toJavaInstant()))
            .sign(Algorithm.RSA256(null, sa.privateKey))
        val response = client.submitForm(
            url = sa.tokenUri,
            formParameters = parameters {
                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("assertion", assertion)
            },
        )
        if (response.status.value != 200) {
            logger.warn("Google OAuth token answered {}", response.status.value)
            return@withLock null
        }
        val body = JSON.parseToJsonElement(response.bodyAsText()).jsonObject
        val token = body["access_token"]?.jsonPrimitive?.contentOrNull ?: return@withLock null
        val ttl = body["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600
        accessToken = token to (now + ttl.seconds)
        token
    }

    internal class ServiceAccount(val clientEmail: String, val privateKey: RSAPrivateKey, val tokenUri: String) {
        companion object {
            fun parse(json: String): ServiceAccount {
                val obj = JSON.parseToJsonElement(json).jsonObject
                val pem = checkNotNull(obj["private_key"]?.jsonPrimitive?.contentOrNull) { "no private_key" }
                val base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
                    .replace("\\n", "").filterNot { it.isWhitespace() }
                val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)))
                return ServiceAccount(
                    clientEmail = checkNotNull(obj["client_email"]?.jsonPrimitive?.contentOrNull) { "no client_email" },
                    privateKey = key as RSAPrivateKey,
                    tokenUri = obj["token_uri"]?.jsonPrimitive?.contentOrNull ?: "https://oauth2.googleapis.com/token",
                )
            }
        }
    }

    companion object {
        private const val API = "https://playintegrity.googleapis.com"
        private const val SCOPE = "https://www.googleapis.com/auth/playintegrity"

        /** A token older than this was not made for this request. */
        val MAX_TOKEN_AGE = 10.minutes
    }
}

private val JSON = Json { ignoreUnknownKeys = true }

private const val TIMEOUT_MS = 5_000L

private fun httpClient(engine: HttpClientEngine?): HttpClient {
    val configure: HttpClientConfig<*>.() -> Unit = {
        install(HttpTimeout) {
            requestTimeoutMillis = TIMEOUT_MS
            connectTimeoutMillis = TIMEOUT_MS
            socketTimeoutMillis = TIMEOUT_MS
        }
        expectSuccess = false
    }
    return if (engine != null) HttpClient(engine, configure) else HttpClient(CIO, configure)
}
