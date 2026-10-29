package com.opson.server.routes

import com.opson.server.domain.AccessAuditRecord
import com.opson.server.domain.EntitlementGrant
import com.opson.server.domain.EntitlementPolicy
import com.opson.server.domain.EntitlementSource
import com.opson.server.domain.RoleAssignment
import com.opson.server.domain.UserAccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.ExperimentalTime

/**
 * `GET /v1/me/entitlements`: the caller's role, what it lets them do, and what they may use.
 * Clients must ignore keys they don't know, in every list: the server adds entitlements and
 * permissions before the apps learn them.
 */
@Serializable
data class MyAccessResponse(
    val role: String,
    val permissions: List<String>,
    val entitlements: List<MyEntitlementResponse>,
)

/** One entitlement and why the caller has it. No admin notes: those stay in the console. */
@Serializable
data class MyEntitlementResponse(
    val key: String,
    val sources: List<MyEntitlementSourceResponse>,
)

/**
 * [type] is `everyone`, `role` or `grant`. [bundle] names the bundle that brought it, if one
 * did. [expiresAtEpochMs] is set only for a grant that ends, so a client can stop offering
 * the feature at that moment without waiting for its next sync.
 */
@Serializable
data class MyEntitlementSourceResponse(
    val type: String,
    val bundle: String? = null,
    val role: String? = null,
    val expiresAtEpochMs: Long? = null,
)

@OptIn(ExperimentalTime::class)
fun UserAccess.toMyAccessResponse(): MyAccessResponse = MyAccessResponse(
    role = role.wire,
    permissions = permissions.map { it.key }.sorted(),
    entitlements = entitlements.granted.map { (entitlement, sources) ->
        MyEntitlementResponse(
            key = entitlement.key,
            sources = sources.map { source ->
                when (source) {
                    EntitlementSource.Everyone -> MyEntitlementSourceResponse(type = "everyone")
                    is EntitlementSource.FromRole -> MyEntitlementSourceResponse(type = "role", bundle = source.bundle, role = source.role.wire)
                    is EntitlementSource.FromGrant -> MyEntitlementSourceResponse(
                        type = "grant",
                        bundle = source.bundle,
                        expiresAtEpochMs = source.grant.expiresAt?.toEpochMilliseconds(),
                    )
                }
            },
        )
    },
)

// ---------- admin ----------

@Serializable
data class RoleAssignmentResponse(
    val role: String,
    val grantedBy: String,
    val grantedAtEpochMs: Long,
    val reason: String? = null,
)

@Serializable
data class GrantResponse(
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
    /** In force now: not removed, not expired. */
    val active: Boolean,
)

/** Why the account has an entitlement, with the grant behind it when there is one. */
@Serializable
data class AdminEntitlementSourceResponse(
    val type: String,
    val bundle: String? = null,
    val role: String? = null,
    val grant: GrantResponse? = null,
)

@Serializable
data class AdminEntitlementResponse(
    val key: String,
    val description: String,
    val sources: List<AdminEntitlementSourceResponse>,
)

@Serializable
data class RevokedEntitlementResponse(val key: String, val revocations: List<GrantResponse>)

@Serializable
data class AccessAuditResponse(
    val id: String,
    val atEpochMs: Long,
    val actor: String,
    val action: String,
    val userId: String,
    val before: JsonElement? = null,
    val after: JsonElement? = null,
)

/** `GET /v1/admin/users/{id}/access`. Resolved with a neutral client (no platform, version or country). */
@Serializable
data class UserAccessResponse(
    val userId: String,
    val role: String,
    val roleAssignment: RoleAssignmentResponse? = null,
    val permissions: List<String>,
    val entitlements: List<AdminEntitlementResponse>,
    val revoked: List<RevokedEntitlementResponse>,
    val grants: List<GrantResponse>,
    val audit: List<AccessAuditResponse>,
)

@Serializable
data class RoleChangedResponse(val role: String, val roleAssignment: RoleAssignmentResponse? = null)

@Serializable
data class SetRoleRequest(val role: String? = null, val reason: String? = null)

@Serializable
data class AddGrantRequest(
    val kind: String? = null,
    val key: String? = null,
    val effect: String = "grant",
    val source: String = "manual",
    val reason: String? = null,
    val expiresAtEpochMs: Long? = null,
)

@Serializable
data class EntitlementInfoResponse(val key: String, val description: String)

@Serializable
data class RoleInfoResponse(val role: String, val permissions: List<String>)

@Serializable
data class EntitlementPolicyResponse(
    val everyone: List<String>,
    val bundles: Map<String, List<String>>,
    val roles: Map<String, List<String>>,
)

/**
 * `GET /v1/admin/entitlements`: what the server knows, and the policy as it resolves now.
 * [paths] are the config flags that hold the policy; the console edits them through
 * `/v1/admin/config/flags/{path}`, so policy edits share the config audit and guardrails.
 */
@Serializable
data class EntitlementsOverviewResponse(
    val entitlements: List<EntitlementInfoResponse>,
    val roles: List<RoleInfoResponse>,
    val policy: EntitlementPolicyResponse,
    val paths: Map<String, String>,
    val audit: List<AccessAuditResponse>,
)

@Serializable
data class AccessAuditListResponse(val entries: List<AccessAuditResponse>)

@OptIn(ExperimentalTime::class)
fun RoleAssignment.toResponse() = RoleAssignmentResponse(
    role = role.wire,
    grantedBy = grantedBy,
    grantedAtEpochMs = grantedAt.toEpochMilliseconds(),
    reason = reason,
)

@OptIn(ExperimentalTime::class)
fun EntitlementGrant.toResponse(nowEpochMs: Long) = GrantResponse(
    id = id.toString(),
    kind = kind.wire,
    key = key,
    effect = effect.wire,
    source = source,
    reason = reason,
    grantedBy = grantedBy,
    grantedAtEpochMs = grantedAt.toEpochMilliseconds(),
    expiresAtEpochMs = expiresAt?.toEpochMilliseconds(),
    removedAtEpochMs = removedAt?.toEpochMilliseconds(),
    removedBy = removedBy,
    active = removedAt == null && (expiresAt == null || expiresAt.toEpochMilliseconds() > nowEpochMs),
)

fun AccessAuditRecord.toResponse() = AccessAuditResponse(
    id = id.toString(),
    atEpochMs = atEpochMs,
    actor = actor,
    action = action,
    userId = userId.toString(),
    before = before,
    after = after,
)

fun EntitlementPolicy.toResponse() = EntitlementPolicyResponse(
    everyone = everyone.map { it.key },
    bundles = bundles.mapValues { (_, keys) -> keys.map { it.key } },
    roles = roleBundles.entries.associate { (role, names) -> role.wire to names.toList() },
)
