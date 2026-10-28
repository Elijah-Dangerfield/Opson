package com.opson.libraries.identity.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.networking.NetworkClient
import com.opson.libraries.networking.authedCall
import com.opson.libraries.networking.retry.RetryPolicy
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Thin HTTP layer for our own server's profile endpoint. Defined as an
 * interface so impl-module tests can fake it trivially.
 *
 * Uses [NetworkClient.authenticatedClient] — the Bearer token is the
 * Supabase JWT produced by `AuthRepository.accessToken()`. The server
 * validates that JWT and treats us as the user whose `sub` claim it
 * carries.
 */
interface ProfileApi {
    /** `GET /v1/me` — server is get-or-create. */
    suspend fun me(): MeDto

    /**
     * `PATCH /v1/me` — partial update. Returns the new profile on 2xx;
     * throws `ClientRequestException` on 4xx so the repository can map
     * 409 to "name taken," 400 to validation, etc.
     */
    suspend fun patchMe(request: PatchMeRequest): MeDto

    /**
     * `DELETE /v1/me` — permanent account deletion. Returns on 204; throws
     * `ResponseException` otherwise (401 session gone, 503 with
     * `delete_not_configured` or `delete_failed`), whose [problemCode] the
     * repository maps to an outcome.
     */
    suspend fun deleteMe()

    /**
     * `POST /v1/me/apple-authorization`: the one-use code from a native Sign in with
     * Apple, which the server trades for the token that account deletion revokes.
     * Throws on a non-2xx (503 when the server has no Apple key).
     */
    suspend fun sendAppleAuthorization(authorizationCode: String)
}

@Serializable
data class PatchMeRequest(
    val displayName: String? = null,
)

@Serializable
data class AppleAuthorizationRequest(
    val authorizationCode: String,
)

/**
 * The stable `error.code` of the server's problem envelope
 * (`{ "error": { "code": "…", "message": "…" } }`), or null when the body is
 * something else (a proxy's error page, an empty body).
 */
internal suspend fun ResponseException.problemCode(): String? =
    Catching { problemJson.decodeFromString<ProblemEnvelope>(response.bodyAsText()).error.code }.getOrNull()

private val problemJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class ProblemEnvelope(val error: Problem) {
    @Serializable
    data class Problem(val code: String)
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class HttpProfileApi(
    private val networkClient: NetworkClient,
) : ProfileApi {

    // GET /v1/me is server-side get-or-create — same userId returns the
    // same row, replays are no-ops. Safe to retry. This is the call that
    // hung for 30s on Fly cold-boot in production logs and dumped users
    // into Profile.Fallback; the retry covers that case.
    override suspend fun me(): MeDto =
        networkClient.authedCall("me.fetch", retry = RetryPolicy.idempotent()) { client ->
            client.get("/v1/me").body<MeDto>()
        }.getOrThrow()

    // PATCH is a write — leave retry at None. ClientRequestException on
    // 4xx still surfaces so the repository can map 409 → name-taken etc.
    override suspend fun patchMe(request: PatchMeRequest): MeDto =
        networkClient.authedCall("me.patch") { client ->
            client.patch("/v1/me") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<MeDto>()
        }.getOrThrow()

    // One-shot: a retry after the server deleted the account would only 401.
    override suspend fun deleteMe() {
        networkClient.authedCall("me.delete") { client ->
            client.delete("/v1/me")
        }.getOrThrow()
    }

    // The code works once, so a retry after the server used it would only be refused.
    override suspend fun sendAppleAuthorization(authorizationCode: String) {
        networkClient.authedCall("me.apple_authorization") { client ->
            client.post("/v1/me/apple-authorization") {
                contentType(ContentType.Application.Json)
                setBody(AppleAuthorizationRequest(authorizationCode))
            }
        }.getOrThrow()
    }
}
