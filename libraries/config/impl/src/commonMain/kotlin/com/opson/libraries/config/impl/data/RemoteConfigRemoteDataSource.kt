package com.opson.libraries.config.impl.data

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.impl.model.BasicMapAppConfig
import com.opson.libraries.config.impl.serialization.ConfigJsonConverter
import com.opson.libraries.core.Catching
import com.opson.libraries.core.flatMap
import com.opson.libraries.flowroutines.DispatcherProvider
import com.opson.libraries.networking.AuthTokenProvider
import com.opson.libraries.networking.NetworkClient
import com.opson.libraries.networking.retry.RetryPolicy
import com.opson.libraries.networking.unauthedCall
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Fetches the app config tree from the app server.
 *
 * Returns a sparse JSON object whose keys are the dotted [com.opson.libraries.config.ConfiguredValue.path]s
 * that the server wants to override. Anything missing falls back to the
 * client-side default declared on the [com.opson.libraries.config.ConfiguredValue].
 *
 * The call stays **unauthenticated** so the kill-switch / forced-upgrade flags
 * still load when auth is down or before the first sign-in. But when a session
 * token is already on hand we attach it best-effort (a non-blocking peek — no
 * [AuthTokenProvider.awaitReady]), so the server can resolve the user id for
 * per-user targeting + rollout bucketing. No token → no header → the server
 * targets on the install-id / client-context axes instead.
 *
 * A server that refuses the token (401 or 403) is asked once more without it.
 * Today's server serves a token it can't verify anonymously, but one deployed
 * before that answered 401, and a build signed in to the wrong Supabase project
 * then never saw the version floor or the kill switch, the only ways to stop it.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RemoteConfigRemoteDataSource @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val networkClient: NetworkClient,
    private val authTokenProvider: AuthTokenProvider,
    private val converter: ConfigJsonConverter,
) : RemoteConfigDataSource {

    private val logger = KLog.withTag("RemoteConfigDataSource")

    override suspend fun getConfig(): Catching<AppConfigMap> = withContext(dispatcherProvider.io) {
        val token = authTokenProvider.accessToken()
        val first = fetch(token)
        val fetched = if (token != null && first.exceptionOrNull().isRefusedToken()) {
            logger.w { "App config refused the session token; fetching it without one" }
            fetch(token = null)
        } else {
            first
        }
        fetched
            .flatMap { raw -> converter.decodeToMap(raw).map { BasicMapAppConfig(it) } }
            .onSuccess { logger.d { "Fetched remote app config" } }
    }

    private suspend fun fetch(token: String?): Catching<String> = networkClient.unauthedCall(
        description = "appConfig.fetch",
        retry = RetryPolicy.idempotent(),
    ) { client ->
        client.get("/v1/app-config") {
            if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
        }.body<String>()
    }

    private fun Throwable?.isRefusedToken(): Boolean =
        this is ClientRequestException && response.status in REFUSED

    private companion object {
        val REFUSED = setOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden)
    }
}
