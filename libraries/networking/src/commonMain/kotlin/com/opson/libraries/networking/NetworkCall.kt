package com.opson.libraries.networking

import com.opson.libraries.core.AuthReason
import com.opson.libraries.core.AuthRequirement
import com.opson.libraries.core.AuthUnready
import com.opson.libraries.core.AuthVerdict
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.core.mapFailure
import com.opson.libraries.networking.retry.RetryPolicy
import com.opson.libraries.networking.retry.withRetry
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode

private val networkCallLogger = KLog.withTag("NetworkCall")

/**
 * Authenticated HTTP call wrapper.
 *
 * Consults [NetworkClient.authVerdict] first: a Blocked verdict (no session,
 * offline, an expired session) short-circuits to a typed [AuthUnready] failure
 * without touching the wire — no phantom 401s. Callers ignore it with
 * `getOrNull()`; a call that does nothing special is already correct.
 * [requirement] defaults to [AuthRequirement.Account].
 *
 * A Ready verdict awaits [NetworkClient.awaitAuthReady], hands the
 * authenticated [HttpClient] to [block], wraps each attempt in [Catching], and
 * emits a structured failure log keyed by [description]. One post-flight remap:
 * a 401 whose token refresh the auth server *rejected* mid-call (see
 * [SessionRejectionBus.rejectionEpoch]) becomes [AuthUnready] with
 * [AuthReason.SessionExpired], so the discovery moment of a dead session speaks
 * the same vocabulary as the pre-flight gate. A 401 without a confirmed
 * rejection (transient refresh failure while holding a session) stays an
 * ordinary [ResponseException].
 *
 * Why pre-flight await: Ktor's per-request timeout starts the moment the
 * call enters the bearer plugin, *including* any wait the plugin does for
 * a token. On a slow first-launch auth bootstrap, that ate most of the
 * 30s budget before the actual server roundtrip even started. Doing the
 * await here means the timeout clock only runs against the real network
 * request.
 *
 * [description] is a short, stable identifier for the call — used as the
 * log message prefix. Pick `"diary.sync"` or `"foods.search"`, not a
 * sentence; logs aggregate by description.
 *
 * [retry] defaults to [RetryPolicy.None] — opting in to retry is explicit
 * so non-idempotent POSTs can't silently inherit a retry that
 * double-spends. See [RetryPolicy] header for the idempotency tradeoffs.
 *
 * Cancellation is preserved via [Catching] — `CancellationException` is
 * re-thrown rather than swallowed, and the retry loop's `delay` is
 * cancellable.
 */
@OptIn(InternalNetworkingApi::class)
suspend fun <T> NetworkClient.authedCall(
    description: String,
    requirement: AuthRequirement = AuthRequirement.Account,
    retry: RetryPolicy = RetryPolicy.None,
    block: suspend (HttpClient) -> T,
): Catching<T> {
    shortCircuitOrNull<T>(description, requirement)?.let { return it }
    awaitAuthReady()
    val epochBefore = sessionRejectionEpoch
    return withRetry(retry) {
        Catching { block(authenticatedClient) }
    }
        .mapFailure { throwable ->
            if (throwable.isUnauthorized() && sessionRejectionEpoch > epochBefore) {
                AuthUnready(AuthReason.SessionExpired, cause = throwable)
            } else {
                throwable
            }
        }
        .logFailure(description)
}

/**
 * Unauthenticated counterpart to [authedCall]. Same retry/logging contract,
 * routes through [NetworkClient.client] for public endpoints (app-config
 * fetch, healthcheck, anything pre-session). Does NOT gate on auth — public
 * endpoints don't need it, and waiting for it would defeat the purpose of
 * having an unauthenticated client at all.
 */
@OptIn(InternalNetworkingApi::class)
suspend fun <T> NetworkClient.unauthedCall(
    description: String,
    retry: RetryPolicy = RetryPolicy.None,
    block: suspend (HttpClient) -> T,
): Catching<T> = withRetry(retry) {
    Catching { block(client) }
}.logFailure(description)

/** A Blocked verdict as a ready-made [AuthUnready] failure, or null when Ready. */
private suspend fun <T> NetworkClient.shortCircuitOrNull(
    description: String,
    requirement: AuthRequirement,
): Catching<T>? = when (val verdict = authVerdict(requirement)) {
    AuthVerdict.Ready -> null
    is AuthVerdict.Blocked -> {
        // info, not warn: an unready gate is an expected state (offline guest,
        // mid-onboarding), and the whole point is keeping phantoms out of the
        // failure telemetry.
        networkCallLogger.i { "$description short-circuited: auth unready (${verdict.reason})" }
        Catching.failure(AuthUnready(verdict.reason))
    }
}

private fun <T> Catching<T>.logFailure(description: String): Catching<T> = onFailure { throwable ->
    when {
        throwable is AuthUnready ->
            networkCallLogger.i { "$description failed: auth unready (${throwable.reason})" }
        // Expected while the device has no route — info like AuthUnready, and no
        // backend_unreachable event: AppState already records the offline edge
        // once via net.offline_banner, so per-call events would only add noise.
        throwable.isOfflineError() ->
            networkCallLogger.i { "$description failed: device offline (${throwable.classifyForLog()})" }
        else -> {
            networkCallLogger.w(throwable) { "$description failed (${throwable.classifyForLog()})" }
            // A ResponseException means the backend answered (an HTTP status IS
            // reachability); anything else — timeout, DNS, refused connection —
            // is the "client couldn't reach us at all" class the app-event
            // taxonomy exists to catch (docs/practices/app-events.md).
            if (throwable !is ResponseException) {
                networkCallLogger.logEvent(
                    "net.backend_unreachable",
                    "operation" to description,
                    "error_kind" to throwable.classifyForLog(),
                )
            }
        }
    }
}

private fun Throwable.isUnauthorized(): Boolean =
    this is ResponseException && response.status == HttpStatusCode.Unauthorized

private fun Throwable.classifyForLog(): String = when (this) {
    is HttpRequestTimeoutException -> "timeout"
    is ResponseException -> "http ${response.status.value}"
    else -> this::class.simpleName ?: "unknown"
}
