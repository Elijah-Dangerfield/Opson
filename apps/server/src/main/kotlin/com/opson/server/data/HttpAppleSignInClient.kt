package com.opson.server.data

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.opson.server.config.AppleSignInConfig
import com.opson.server.config.SupabaseConfig
import com.opson.server.di.ServerScope
import com.opson.server.domain.AppleExchangeResult
import com.opson.server.domain.AppleRevokeResult
import com.opson.server.domain.AppleSignInClient
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Inject
import org.slf4j.LoggerFactory
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

/**
 * `POST <baseUrl>/auth/token` and `/auth/revoke`, authenticated by a client secret: a
 * short-lived ES256 JWT signed with the team's Sign in with Apple key, as Apple's
 * "Creating a client secret" describes. A fresh one per call, since calls are rare.
 *
 * With no [AppleSignInConfig], or a key that doesn't parse, every call answers
 * NotConfigured and boot logs why.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@OptIn(ExperimentalTime::class)
class HttpAppleSignInClient(
    private val config: AppleSignInConfig?,
    private val clock: Clock,
    engine: HttpClientEngine?,
) : AppleSignInClient {

    @Inject
    constructor(supabase: SupabaseConfig?, clock: Clock) : this(supabase?.appleSignIn, clock, engine = null)

    private val logger = LoggerFactory.getLogger(HttpAppleSignInClient::class.java)

    private val client: HttpClient by lazy {
        val configure: HttpClientConfig<*>.() -> Unit = {
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MS
                connectTimeoutMillis = TIMEOUT_MS
                socketTimeoutMillis = TIMEOUT_MS
            }
            expectSuccess = false
        }
        if (engine != null) HttpClient(engine, configure) else HttpClient(CIO, configure)
    }

    private val signingKey: ECPrivateKey? = config?.let { cfg ->
        try {
            parsePrivateKey(cfg.privateKeyPem)
        } catch (e: Exception) {
            logger.error("APPLE_PRIVATE_KEY isn't a PKCS#8 EC key (the .p8 file's contents); Apple tokens won't be revoked", e)
            null
        }
    }

    init {
        if (config == null) {
            logger.warn(
                "APPLE_TEAM_ID, APPLE_KEY_ID or APPLE_PRIVATE_KEY is not set: account deletion " +
                    "won't revoke Sign in with Apple tokens, which App Review requires.",
            )
        }
    }

    override suspend fun exchange(authorizationCode: String): AppleExchangeResult {
        val cfg = config ?: return AppleExchangeResult.NotConfigured
        val secret = clientSecret(cfg) ?: return AppleExchangeResult.NotConfigured
        return try {
            val response = client.submitForm(
                url = "${cfg.baseUrl}/auth/token",
                formParameters = parameters {
                    append("client_id", cfg.clientId)
                    append("client_secret", secret)
                    append("code", authorizationCode)
                    append("grant_type", "authorization_code")
                },
            )
            when (response.status.value) {
                in 200..299 -> {
                    val token = JSON.decodeFromString<TokenResponse>(response.bodyAsText()).refreshToken
                    if (token.isNullOrBlank()) {
                        AppleExchangeResult.Failure(response.status.value, IllegalStateException("No refresh_token in Apple's reply"))
                    } else {
                        AppleExchangeResult.Success(token)
                    }
                }
                // invalid_grant: the code expired, was used, or belongs to another client.
                400 -> AppleExchangeResult.Rejected
                else -> AppleExchangeResult.Failure(response.status.value, null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppleExchangeResult.Failure(statusCode = null, cause = e)
        }
    }

    override suspend fun revoke(refreshToken: String): AppleRevokeResult {
        val cfg = config ?: return AppleRevokeResult.NotConfigured
        val secret = clientSecret(cfg) ?: return AppleRevokeResult.NotConfigured
        return try {
            val response = client.submitForm(
                url = "${cfg.baseUrl}/auth/revoke",
                formParameters = parameters {
                    append("client_id", cfg.clientId)
                    append("client_secret", secret)
                    append("token", refreshToken)
                    append("token_type_hint", "refresh_token")
                },
            )
            if (response.status.value in 200..299) {
                AppleRevokeResult.Success
            } else {
                AppleRevokeResult.Failure(response.status.value, null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppleRevokeResult.Failure(statusCode = null, cause = e)
        }
    }

    private fun clientSecret(cfg: AppleSignInConfig): String? {
        val key = signingKey ?: return null
        val now = clock.now()
        return JWT.create()
            .withKeyId(cfg.keyId)
            .withIssuer(cfg.teamId)
            .withSubject(cfg.clientId)
            .withAudience(APPLE_AUDIENCE)
            .withIssuedAt(Date.from(now.toJavaInstant()))
            .withExpiresAt(Date.from((now + SECRET_LIFETIME).toJavaInstant()))
            .sign(Algorithm.ECDSA256(null, key))
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("refresh_token") val refreshToken: String? = null,
    )

    companion object {
        private const val TIMEOUT_MS = 5_000L
        private const val APPLE_AUDIENCE = "https://appleid.apple.com"
        private val SECRET_LIFETIME = 5.minutes
        private val JSON = Json { ignoreUnknownKeys = true }

        internal fun parsePrivateKey(pem: String): ECPrivateKey {
            val base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .filterNot { it.isWhitespace() }
            val spec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64))
            return KeyFactory.getInstance("EC").generatePrivate(spec) as ECPrivateKey
        }
    }
}
