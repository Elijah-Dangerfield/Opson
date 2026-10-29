package com.opson.server.routes

import com.opson.server.config.AdminConfig
import com.opson.server.domain.AccessAuditRepository
import com.opson.server.domain.AddGrantOutcome
import com.opson.server.domain.Entitlement
import com.opson.server.domain.EntitlementGrantRepository
import com.opson.server.domain.EntitlementPolicy
import com.opson.server.domain.EntitlementService
import com.opson.server.domain.EntitlementSource
import com.opson.server.domain.GrantEffect
import com.opson.server.domain.GrantKind
import com.opson.server.domain.NewGrant
import com.opson.server.domain.Role
import com.opson.server.domain.SetRoleOutcome
import com.opson.server.domain.UserId
import com.opson.server.domain.UserRoleRepository
import com.opson.server.plugins.problem
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Roles and entitlements for one account, behind the `X-Admin-Token` gate of `/v1/admin`:
 *
 *  - `GET    /v1/admin/users/{id}/access`             role, permissions, resolved entitlements and
 *                                                     where each came from, every grant, the trail
 *  - `PUT    /v1/admin/users/{id}/role {role, reason}` `member` clears it
 *  - `POST   /v1/admin/users/{id}/grants {kind, key, effect, source, reason, expiresAtEpochMs}`
 *  - `DELETE /v1/admin/users/{id}/grants/{grantId}`   marks it removed; the row stays
 *  - `GET    /v1/admin/entitlements`                  known entitlements, roles and the policy now
 *  - `GET    /v1/admin/access/audit?limit=`           every role and grant change, newest first
 *
 * The policy itself (`entitlements.everyone`, `.bundles`, `.roles`) is remote config and is
 * edited through `/v1/admin/config/flags`, which validates it with [EntitlementPolicy.validate].
 * `X-Admin-Actor` is recorded on every change, as with config.
 */
@OptIn(ExperimentalTime::class)
fun Route.accessAdminRoutes(
    config: AdminConfig,
    entitlements: EntitlementService,
    roles: UserRoleRepository,
    grants: EntitlementGrantRepository,
    audit: AccessAuditRepository,
    clock: Clock,
) {
    route("/v1/admin/users/{id}") {
        get("/access") {
            if (!call.requireAdmin(config)) return@get
            val userId = call.pathUser() ?: return@get call.invalidUser()
            val access = entitlements.access(userId)
            val now = clock.now().toEpochMilliseconds()
            call.respond(
                UserAccessResponse(
                    userId = userId.toString(),
                    role = access.role.wire,
                    roleAssignment = access.roleAssignment?.toResponse(),
                    permissions = access.permissions.map { it.key }.sorted(),
                    entitlements = access.entitlements.granted.map { (entitlement, sources) ->
                        AdminEntitlementResponse(
                            key = entitlement.key,
                            description = entitlement.description,
                            sources = sources.map { it.toAdminResponse(now) },
                        )
                    },
                    revoked = access.entitlements.revoked.map { (entitlement, revocations) ->
                        RevokedEntitlementResponse(entitlement.key, revocations.map { it.toResponse(now) })
                    },
                    grants = grants.history(userId).map { it.toResponse(now) },
                    audit = audit.list(userId, USER_AUDIT_LIMIT).map { it.toResponse() },
                ),
            )
        }

        put("/role") {
            if (!call.requireAdmin(config)) return@put
            val userId = call.pathUser() ?: return@put call.invalidUser()
            val body = call.receiveOrNull<SetRoleRequest>() ?: return@put call.bad("invalid_body", "Expected {role, reason}.")
            val role = Role.fromWire(body.role)
                ?: return@put call.bad("invalid_role", "role must be one of ${Role.entries.joinToString { it.wire }}.")
            val reason = body.reason.cleanReason() ?: return@put call.bad("invalid_reason", REASON_RULE)
            when (val outcome = roles.setRole(userId, role, call.accessActor(), reason.value)) {
                is SetRoleOutcome.Set -> call.respond(
                    RoleChangedResponse(role = role.wire, roleAssignment = outcome.assignment?.toResponse()),
                )
                SetRoleOutcome.NoSuchUser -> call.noSuchUser()
            }
        }

        post("/grants") {
            if (!call.requireAdmin(config)) return@post
            val userId = call.pathUser() ?: return@post call.invalidUser()
            val body = call.receiveOrNull<AddGrantRequest>()
                ?: return@post call.bad("invalid_body", "Expected {kind, key, effect, source, reason, expiresAtEpochMs}.")
            val kind = GrantKind.fromWire(body.kind) ?: return@post call.bad("invalid_kind", "kind must be entitlement or bundle.")
            val effect = GrantEffect.fromWire(body.effect) ?: return@post call.bad("invalid_effect", "effect must be grant or revoke.")
            if (effect == GrantEffect.Revoke && kind != GrantKind.Entitlement) {
                return@post call.bad("invalid_effect", "A revoke names one entitlement. To take a bundle away, remove its grant.")
            }
            val key = body.key?.trim().orEmpty()
            when (kind) {
                GrantKind.Entitlement -> if (Entitlement.fromKey(key) == null) {
                    return@post call.bad("unknown_entitlement", "Known entitlements: ${Entitlement.entries.joinToString { it.key }}.")
                }
                GrantKind.Bundle -> {
                    val bundles = entitlements.policy().bundles.keys
                    if (key !in bundles) return@post call.bad("unknown_bundle", "Known bundles: ${bundles.joinToString()}.")
                }
            }
            if (!SOURCE.matches(body.source)) {
                return@post call.bad("invalid_source", "source is a short lowercase tag such as manual, founding, builder or comp.")
            }
            val reason = body.reason.cleanReason() ?: return@post call.bad("invalid_reason", REASON_RULE)
            val expiresAt = body.expiresAtEpochMs?.let(Instant::fromEpochMilliseconds)
            if (expiresAt != null && expiresAt <= clock.now()) {
                return@post call.bad("invalid_expiry", "expiresAtEpochMs must be in the future.")
            }
            val grant = NewGrant(userId, kind, key, effect, body.source, reason.value, expiresAt)
            when (val outcome = grants.add(grant, call.accessActor())) {
                is AddGrantOutcome.Added -> call.respond(HttpStatusCode.Created, outcome.grant.toResponse(clock.now().toEpochMilliseconds()))
                AddGrantOutcome.NoSuchUser -> call.noSuchUser()
            }
        }

        delete("/grants/{grantId}") {
            if (!call.requireAdmin(config)) return@delete
            val userId = call.pathUser() ?: return@delete call.invalidUser()
            val grantId = call.parameters["grantId"]?.let(::uuidOrNull)
                ?: return@delete call.bad("invalid_id", "grantId must be a UUID.")
            if (grants.remove(userId, grantId, call.accessActor())) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, problem("not_found", "No grant in force with that id for this account."))
            }
        }
    }

    get("/v1/admin/entitlements") {
        if (!call.requireAdmin(config)) return@get
        call.respond(
            EntitlementsOverviewResponse(
                entitlements = Entitlement.entries.map { EntitlementInfoResponse(it.key, it.description) },
                roles = Role.entries.map { role -> RoleInfoResponse(role.wire, role.permissions.map { it.key }.sorted()) },
                policy = entitlements.policy().toResponse(),
                paths = mapOf(
                    "everyone" to EntitlementPolicy.PATH_EVERYONE,
                    "bundles" to EntitlementPolicy.PATH_BUNDLES,
                    "roles" to EntitlementPolicy.PATH_ROLES,
                ),
                audit = audit.list(null, OVERVIEW_AUDIT_LIMIT).map { it.toResponse() },
            ),
        )
    }

    get("/v1/admin/access/audit") {
        if (!call.requireAdmin(config)) return@get
        val limit = call.parameters["limit"]?.toIntOrNull() ?: OVERVIEW_AUDIT_LIMIT
        call.respond(AccessAuditListResponse(audit.list(null, limit).map { it.toResponse() }))
    }
}

@OptIn(ExperimentalTime::class)
private fun EntitlementSource.toAdminResponse(nowEpochMs: Long): AdminEntitlementSourceResponse = when (this) {
    EntitlementSource.Everyone -> AdminEntitlementSourceResponse(type = "everyone")
    is EntitlementSource.FromRole -> AdminEntitlementSourceResponse(type = "role", bundle = bundle, role = role.wire)
    is EntitlementSource.FromGrant -> AdminEntitlementSourceResponse(type = "grant", bundle = bundle, grant = grant.toResponse(nowEpochMs))
}

/** A reason after trimming: [value] null when blank. Null itself when too long. */
private class Reason(val value: String?)

private fun String?.cleanReason(): Reason? {
    val trimmed = this?.trim()?.takeIf { it.isNotEmpty() }
    return if (trimmed != null && trimmed.length > MAX_REASON) null else Reason(trimmed)
}

private fun ApplicationCall.pathUser(): UserId? = parameters["id"]?.let(UserId::parse)

/** The audited name, cut to what the columns hold. */
private fun ApplicationCall.accessActor(): String =
    (request.header("X-Admin-Actor")?.trim()?.takeUnless { it.isEmpty() } ?: "admin").take(MAX_ACTOR)

private suspend fun ApplicationCall.invalidUser() =
    respond(HttpStatusCode.BadRequest, problem("invalid_user_id", "Not a user id."))

private suspend fun ApplicationCall.noSuchUser() =
    respond(HttpStatusCode.NotFound, problem("not_found", "No account with that id."))

private suspend fun ApplicationCall.bad(code: String, message: String) =
    respond(HttpStatusCode.BadRequest, problem(code, message))

private suspend inline fun <reified T : Any> ApplicationCall.receiveOrNull(): T? = try {
    receive<T>()
} catch (_: BadRequestException) {
    null
}

private fun uuidOrNull(raw: String): UUID? = try {
    UUID.fromString(raw)
} catch (_: IllegalArgumentException) {
    null
}

private val SOURCE = Regex("[a-z][a-z0-9_]{0,31}")
private const val MAX_REASON = 500
private const val MAX_ACTOR = 200
private const val REASON_RULE = "reason must be at most 500 characters."
private const val USER_AUDIT_LIMIT = 50
private const val OVERVIEW_AUDIT_LIMIT = 100
