package com.opson.admin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Wire shapes mirroring the server's `AccessDto.kt`.

@Serializable
data class RoleAssignmentDto(
    val role: String,
    val grantedBy: String,
    val grantedAtEpochMs: Long,
    val reason: String? = null,
)

@Serializable
data class GrantDto(
    val id: String,
    val kind: String,
    val key: String,
    val effect: String,
    val source: String,
    val reason: String? = null,
    val grantedBy: String,
    val grantedAtEpochMs: Long,
    val expiresAtEpochMs: Long? = null,
    val removedAtEpochMs: Long? = null,
    val removedBy: String? = null,
    val active: Boolean,
)

@Serializable
data class EntitlementSourceDto(
    val type: String,
    val bundle: String? = null,
    val role: String? = null,
    val grant: GrantDto? = null,
)

@Serializable
data class HeldEntitlementDto(val key: String, val description: String, val sources: List<EntitlementSourceDto>)

@Serializable
data class RevokedEntitlementDto(val key: String, val revocations: List<GrantDto>)

@Serializable
data class AccessAuditDto(
    val id: String,
    val atEpochMs: Long,
    val actor: String,
    val action: String,
    val userId: String,
    val before: JsonElement? = null,
    val after: JsonElement? = null,
)

@Serializable
data class UserAccessDto(
    val userId: String,
    val role: String,
    val roleAssignment: RoleAssignmentDto? = null,
    val permissions: List<String>,
    val entitlements: List<HeldEntitlementDto>,
    val revoked: List<RevokedEntitlementDto>,
    val grants: List<GrantDto>,
    val audit: List<AccessAuditDto>,
)

@Serializable
data class EntitlementInfoDto(val key: String, val description: String)

@Serializable
data class RoleInfoDto(val role: String, val permissions: List<String>)

@Serializable
data class EntitlementPolicyDto(
    val everyone: List<String>,
    val bundles: Map<String, List<String>>,
    val roles: Map<String, List<String>>,
)

@Serializable
data class EntitlementsOverviewDto(
    val entitlements: List<EntitlementInfoDto>,
    val roles: List<RoleInfoDto>,
    val policy: EntitlementPolicyDto,
    val paths: Map<String, String>,
    val audit: List<AccessAuditDto>,
)

@Serializable
private data class SetRoleBody(val role: String, val reason: String? = null)

@Serializable
data class AddGrantBody(
    val kind: String,
    val key: String,
    val effect: String = "grant",
    val source: String = "manual",
    val reason: String? = null,
    val expiresAtEpochMs: Long? = null,
)

/**
 * Roles and entitlements (`/v1/admin/users/{id}/access`, `/v1/admin/entitlements`). Its own
 * client beside [AdminApi], reached as `ctx.api.access`. The policy itself is config, so its
 * edits go through [AdminApi.upsertFlag] and the config audit.
 */
class AccessApi(baseUrl: String, private val token: String, private val actor: String) {
    private val base = baseUrl.trimEnd('/')
    private val client = HttpClient(Js) { install(ContentNegotiation) { json(adminJson) } }

    suspend fun userAccess(userId: String): UserAccessDto =
        client.get("$base/v1/admin/users/${encodeSegment(userId)}/access") { auth() }.require()

    suspend fun setRole(userId: String, role: String, reason: String?) {
        client.put("$base/v1/admin/users/${encodeSegment(userId)}/role") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(SetRoleBody(role, reason))
        }.requireOk()
    }

    suspend fun addGrant(userId: String, grant: AddGrantBody) {
        client.post("$base/v1/admin/users/${encodeSegment(userId)}/grants") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(grant)
        }.requireOk()
    }

    suspend fun removeGrant(userId: String, grantId: String) {
        client.delete("$base/v1/admin/users/${encodeSegment(userId)}/grants/${encodeSegment(grantId)}") { auth() }.requireOk()
    }

    suspend fun overview(): EntitlementsOverviewDto = client.get("$base/v1/admin/entitlements") { auth() }.require()

    private fun HttpRequestBuilder.auth() {
        header("X-Admin-Token", token)
        header("X-Admin-Actor", actor)
    }

    private suspend inline fun <reified T> HttpResponse.require(): T {
        requireOk()
        return body()
    }

    private suspend fun HttpResponse.requireOk() {
        if (status.value in 200..299) return
        val message = Catching {
            val error = (adminJson.parseToJsonElement(bodyAsText()) as? JsonObject)?.get("error") as? JsonObject
            (error?.get("message") as? JsonPrimitive)?.content
        }.getOrNull() ?: "HTTP ${status.value}"
        throw AdminApiException(status, message)
    }
}

private fun encodeSegment(value: String): String = js("encodeURIComponent(value)") as String
