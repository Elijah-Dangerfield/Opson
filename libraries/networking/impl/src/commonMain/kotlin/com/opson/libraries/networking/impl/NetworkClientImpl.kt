package com.opson.libraries.networking.impl

import com.opson.libraries.core.AuthGate
import com.opson.libraries.core.AuthRequirement
import com.opson.libraries.core.AuthVerdict
import com.opson.libraries.core.BuildInfo
import com.opson.libraries.core.Catching
import com.opson.libraries.networking.AccessDeniedBus
import com.opson.libraries.networking.SessionRejectionBus
import com.opson.libraries.networking.AuthTokenProvider
import com.opson.libraries.networking.ClientHeaders
import com.opson.libraries.networking.InternalNetworkingApi
import com.opson.libraries.networking.ClientHeadersProvider
import com.opson.libraries.networking.NetworkClient
import com.opson.libraries.networking.NetworkConfig
import com.opson.libraries.networking.NetworkJson
import com.opson.libraries.networking.NetworkReachability
import com.opson.libraries.networking.platformHttpEngineFactory
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
@OptIn(InternalNetworkingApi::class)
class NetworkClientImpl(
    private val config: NetworkConfig,
    private val tokenProvider: AuthTokenProvider,
    private val headersProvider: ClientHeadersProvider,
    private val reachability: NetworkReachability,
    private val accessDeniedBus: AccessDeniedBus,
    private val sessionRejectionBus: SessionRejectionBus,
    // Lazy: AuthGate's impl (identity) reaches NetworkClient through its own
    // deps, so a direct injection would cycle at construction time.
    private val authGate: () -> AuthGate,
) : NetworkClient {

    override val client: HttpClient by lazy {
        HttpClient(platformHttpEngineFactory) {
            applyCommonConfig(config, headersProvider, reachability, accessDeniedBus)
        }
    }

    override val authenticatedClient: HttpClient by lazy {
        HttpClient(platformHttpEngineFactory) {
            applyCommonConfig(config, headersProvider, reachability, accessDeniedBus)
            install(Auth) {
                bearer {
                    // By the time loadTokens runs, authedCall has already
                    // called [awaitAuthReady], so the gateway peek is
                    // synchronous and instant. Null means there's no
                    // session (not signed in yet) — the request goes
                    // unauthed and the server 401s cleanly.
                    loadTokens {
                        val token = tokenProvider.accessToken()
                            ?: return@loadTokens null
                        BearerTokens(accessToken = token, refreshToken = "")
                    }
                    refreshTokens {
                        val token = tokenProvider.refreshAccessToken(rejected = oldTokens?.accessToken)
                            ?: return@refreshTokens null
                        BearerTokens(accessToken = token, refreshToken = "")
                    }
                    sendWithoutRequest { true }
                }
            }
        }
    }

    override suspend fun awaitAuthReady() {
        tokenProvider.awaitReady()
    }

    override suspend fun authVerdict(requirement: AuthRequirement): AuthVerdict =
        authGate().awaitVerdict(requirement)

    override val sessionRejectionEpoch: Long
        get() = sessionRejectionBus.rejectionEpoch
}

// `internal`, not private: NetworkClientImpl itself needs a DI graph to
// construct, so the only way to test this configuration is to apply it to a
// bare client over a MockEngine.
internal fun HttpClientConfig<*>.applyCommonConfig(
    config: NetworkConfig,
    headersProvider: ClientHeadersProvider,
    reachability: NetworkReachability,
    accessDeniedBus: AccessDeniedBus,
) {
    install(ContentNegotiation) {
        json(NetworkJson)
    }
    // Witnessed reachability: a response (any status, even 4xx/5xx) means the
    // round-trip worked; a failure *without* a response (timeout / IO / DNS /
    // captive portal) means it didn't. This is what lets the offline banner
    // reflect "actually online" rather than just the OS's "there's a path."
    HttpResponseValidator {
        validateResponse { reachability.reportReachable() }
        handleResponseExceptionWithRequest { cause, _ ->
            // Never left the device, so it says nothing about the network. Left
            // to fall through it would raise the offline banner on a
            // misconfiguration, which is the whole failure this guards.
            if (cause is UnresolvedRelativeRequestException) {
                return@handleResponseExceptionWithRequest
            }
            // A ResponseException means the server answered (a 4xx/5xx) — the
            // network is fine. Anything else never reached the server.
            if (cause !is ResponseException) {
                reachability.reportUnreachable()
                return@handleResponseExceptionWithRequest
            }
            if (cause.response.status == HttpStatusCode.Forbidden) {
                signalAccessDeniedIfEnveloped(cause.response, accessDeniedBus)
            }
        }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = config.requestTimeoutMillis
        connectTimeoutMillis = config.connectTimeoutMillis
        socketTimeoutMillis = config.requestTimeoutMillis
    }
    // Only when there is no base URL: with one configured, DefaultRequest has
    // already resolved every relative path and the plugin would have nothing to
    // catch. See RejectUnresolvedRelativeRequests for why this is not left to
    // fail on its own.
    if (config.baseUrl.isBlank()) install(RejectUnresolvedRelativeRequests)
    install(DefaultRequest) {
        if (config.baseUrl.isNotBlank()) url(config.baseUrl)
        headers.append(HttpHeaders.Accept, "application/json")
        headers.append(HttpHeaders.ContentType, "application/json")
        // Per-request: re-read from the provider on every call so locale
        // changes flow through immediately. The provider caches its
        // build-info bits, so this is cheap.
        val h = headersProvider.current()
        headers.append(HttpHeaders.AcceptLanguage, h.acceptLanguage)
        headers.append(ClientHeaders.HEADER_PLATFORM, h.platform)
        headers.append(ClientHeaders.HEADER_APP_VERSION, h.appVersion)
        headers.append(ClientHeaders.HEADER_BUILD_NUMBER, h.buildNumber)
        h.countryCode?.let { headers.append(ClientHeaders.HEADER_COUNTRY_CODE, it) }
        h.installId?.let { headers.append(ClientHeaders.HEADER_INSTALL_ID, it) }
        headers.append(ClientHeaders.HEADER_SESSION_ID, h.sessionId)
    }
    if (BuildInfo.isDebug) {
        // WiretapKMP — captures every request/response through this client
        // into the on-device inspector (shake → "Network inspector"). Debug
        // builds link the real plugin; release builds link the noop (and
        // never enter this branch anyway). Applied to both the plain and
        // authenticated clients since they share this config. Platform-gated
        // (see installNetworkInspector) so host-JVM unit tests, where
        // Wiretap's DI isn't bootstrapped, don't install + crash on it.
        installNetworkInspector()
    }
    expectSuccess = true
}

/**
 * Server's `403 AccessDeniedResponse` wire shape — duplicated here (not shared)
 * because the server class lives in `:apps:server` which the client must not
 * depend on. Locked machine-readable data only, no copy: the client localizes
 * off [reason]. `internal` so the impl module's test can construct an
 * envelope-shaped response without re-deriving the shape; the public surface
 * outside the module is [AccessDeniedBus.Denial].
 */
@Serializable
internal data class AccessDeniedWire(
    val reason: String,
    val until: String? = null,
    val appealUrl: String? = null,
)

/**
 * On a `403`, attempt to read the response body as the locked [AccessDeniedWire]
 * envelope and signal the access-denied bus. We don't throw or swallow — the
 * caller's existing failure flow gets the same `ClientRequestException` it
 * always did. A 403 *without* our envelope (a route-level deny, an upstream
 * proxy 403) doesn't decode and is skipped silently — the [Catching] wrap is
 * the safety net there.
 *
 * Run from `handleResponseExceptionWithRequest` rather than `validateResponse`
 * because the `ResponseException` Ktor throws after validation holds a *saved*
 * copy of the response — reading the body here doesn't race the caller's own
 * body decoding.
 */
internal suspend fun signalAccessDeniedIfEnveloped(
    response: HttpResponse,
    accessDeniedBus: AccessDeniedBus,
) {
    Catching { response.body<AccessDeniedWire>() }
        .onSuccess { wire ->
            accessDeniedBus.signalDenied(
                AccessDeniedBus.Denial(
                    reason = wire.reason,
                    until = wire.until,
                    appealUrl = wire.appealUrl,
                ),
            )
        }
}
