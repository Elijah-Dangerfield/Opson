package com.opson.libraries.identity.impl

import com.opson.libraries.networking.NetworkClient
import com.opson.libraries.networking.authedCall
import com.opson.libraries.networking.retry.RetryPolicy
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
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
     * `DELETE /v1/me` — permanent account deletion. Returns the raw
     * response so the repository can branch on 204 (success), 503
     * (delete_not_configured), 401 (session gone), etc.
     */
    suspend fun deleteMe(): HttpResponse
}

@Serializable
data class PatchMeRequest(
    val displayName: String? = null,
)

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

    // DELETE /v1/me is one-shot. No retry; the auth repo branches on the
    // raw HttpResponse status. Caller wraps in its own Catching to do that.
    override suspend fun deleteMe(): HttpResponse =
        networkClient.authedCall("me.delete") { client ->
            client.delete("/v1/me")
        }.getOrThrow()
}
