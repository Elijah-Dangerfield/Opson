package com.opson.server.data

import com.opson.server.config.SupabaseConfig
import com.opson.server.di.ServerScope
import com.opson.server.domain.DeleteUserResult
import com.opson.server.domain.SupabaseAdminClient
import com.opson.server.domain.UpdateDisplayNameResult
import com.opson.server.domain.UserId
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Inject
import org.slf4j.LoggerFactory
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Talks to `<projectUrl>/auth/v1/admin/users/<id>` using the service-role
 * JWT. Lazily constructs the underlying Ktor client so an unconfigured
 * server (no `SUPABASE_SERVICE_ROLE_KEY`) never opens a connection — every
 * call short-circuits to [DeleteUserResult.NotConfigured].
 *
 * The CIO engine is fine here — small payload, no streaming, no need for
 * connection pooling beyond the default. Tests inject [HttpClientEngine]
 * via the secondary constructor to swap in Ktor's `MockEngine`.
 *
 * **Never log the service-role key.** The boot warning + this kdoc are the
 * only places the constant should be referenced.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
class HttpSupabaseAdminClient(
    private val config: SupabaseConfig?,
    engine: HttpClientEngine?,
) : SupabaseAdminClient {

    @Inject
    constructor(config: SupabaseConfig?) : this(config = config, engine = null)

    private val client: HttpClient by lazy {
        if (engine != null) {
            HttpClient(engine) { install(ContentNegotiation) { json(JSON_LENIENT) } }
        } else {
            HttpClient(CIO) { install(ContentNegotiation) { json(JSON_LENIENT) } }
        }
    }

    init {
        if (config?.serviceRoleKey.isNullOrBlank()) {
            LoggerFactory.getLogger(HttpSupabaseAdminClient::class.java).warn(
                "SUPABASE_SERVICE_ROLE_KEY is not set — admin-gated routes (e.g. DELETE /v1/me) will respond 503.",
            )
        }
    }

    override suspend fun deleteUser(userId: UserId): DeleteUserResult {
        val cfg = config ?: return DeleteUserResult.NotConfigured
        val key = cfg.serviceRoleKey.takeUnless { it.isNullOrBlank() }
            ?: return DeleteUserResult.NotConfigured

        return try {
            val response = client.delete("${cfg.projectUrl}/auth/v1/admin/users/${userId.value}") {
                header(HttpHeaders.Authorization, "Bearer $key")
                // Supabase Auth requires both Authorization AND the apikey
                // header on admin calls; they validate the apikey against
                // their project list before unpacking the bearer.
                header("apikey", key)
                // GoTrue soft-deletes by default, leaving the auth.users row
                // signable-in and never firing the ON DELETE CASCADE; the body
                // forces a hard delete so the credential and cascaded data go.
                contentType(ContentType.Application.Json)
                setBody(DeleteUserRequest(shouldSoftDelete = false))
            }
            when (response.status.value) {
                in 200..299 -> DeleteUserResult.Success
                404 -> DeleteUserResult.AlreadyGone
                else -> DeleteUserResult.Failure(response.status.value, null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DeleteUserResult.Failure(statusCode = null, cause = e)
        }
    }

    override suspend fun updateUserDisplayName(
        userId: UserId,
        displayName: String,
    ): UpdateDisplayNameResult {
        val cfg = config ?: return UpdateDisplayNameResult.NotConfigured
        val key = cfg.serviceRoleKey.takeUnless { it.isNullOrBlank() }
            ?: return UpdateDisplayNameResult.NotConfigured

        return try {
            val response = client.put("${cfg.projectUrl}/auth/v1/admin/users/${userId.value}") {
                header(HttpHeaders.Authorization, "Bearer $key")
                header("apikey", key)
                contentType(ContentType.Application.Json)
                // GoTrue merges user_metadata keys, so this only touches
                // display_name and leaves anything else (future keys) alone.
                setBody(UpdateUserMetadataRequest(UserMetadata(displayName = displayName)))
            }
            when (response.status.value) {
                in 200..299 -> UpdateDisplayNameResult.Success
                else -> UpdateDisplayNameResult.Failure(response.status.value, null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UpdateDisplayNameResult.Failure(statusCode = null, cause = e)
        }
    }

    @Serializable
    private data class DeleteUserRequest(
        @SerialName("should_soft_delete") val shouldSoftDelete: Boolean,
    )

    @Serializable
    private data class UpdateUserMetadataRequest(
        @SerialName("user_metadata") val userMetadata: UserMetadata,
    )

    @Serializable
    private data class UserMetadata(
        @SerialName("display_name") val displayName: String,
    )

    companion object {
        private val JSON_LENIENT = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
}
