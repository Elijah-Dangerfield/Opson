package com.opson.server.domain

import com.opson.server.di.ServerScope
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Something a role lets a person do, checked server-side with [Permissions.can]. [key] is the
 * wire and audit name; it never changes once shipped, since clients cache it.
 *
 * No route checks a permission yet: they exist so the food-correction and Builder work can ask
 * one question ("may this person edit the catalog directly?") instead of comparing role names.
 * The open submission flow (`POST /v1/foods/submissions`) is every member's and stays that way;
 * [FoodContribute] is for tools beyond it.
 */
enum class Permission(val key: String) {
    /** Contributor tools beyond the open submission flow, such as the Builder program's. */
    FoodContribute("food.contribute"),

    /** Approve or reject other people's submissions and corrections. */
    FoodReview("food.review"),

    /** Edits go straight into the catalog and count as verification: no second reviewer. */
    FoodEditDirect("food.edit.direct"),
    ;

    companion object {
        fun fromKey(key: String): Permission? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Who someone is to Opson, one per account. [Member] is everyone without a `user_roles` row
 * (V28), so the table only holds the few people with more. [permissions] is the whole mapping,
 * in code on purpose: what a role may do is a product decision that ships with the routes that
 * check it, unlike which entitlements a role brings, which is remote config
 * (`entitlements.roles`, see [EntitlementPolicy]).
 */
enum class Role(val wire: String, val permissions: Set<Permission>) {
    Member("member", emptySet()),
    Builder("builder", setOf(Permission.FoodContribute)),
    Moderator("moderator", setOf(Permission.FoodContribute, Permission.FoodReview)),
    Admin("admin", Permission.entries.toSet()),

    /** The person who runs Opson. The same permissions as [Admin] today; kept apart so it can't be confused with a hire. */
    Owner("owner", Permission.entries.toSet()),
    ;

    companion object {
        fun fromWire(wire: String?): Role? = entries.firstOrNull { it.wire == wire?.trim()?.lowercase() }
    }
}

/** A stored role, with who set it, when and why. Members have none. */
@OptIn(ExperimentalTime::class)
data class RoleAssignment(
    val userId: UserId,
    val role: Role,
    val grantedBy: String,
    val grantedAt: Instant,
    val reason: String?,
)

/** The outcome of [UserRoleRepository.setRole]. */
sealed interface SetRoleOutcome {
    /** [assignment] is null when the account is back to [Role.Member]. */
    data class Set(val assignment: RoleAssignment?) : SetRoleOutcome

    /** No account with that id. */
    data object NoSuchUser : SetRoleOutcome
}

/**
 * Roles, read by [Permissions] and written only by the admin API. Every write appends to
 * `access_audit` in the same transaction.
 */
interface UserRoleRepository {
    /** The account's role, [Role.Member] when it has no row. */
    suspend fun roleOf(userId: UserId): Role = assignment(userId)?.role ?: Role.Member

    suspend fun assignment(userId: UserId): RoleAssignment?

    /** Sets [role] ([Role.Member] deletes the row). [actor] and [reason] go on the row and the audit. */
    suspend fun setRole(userId: UserId, role: Role, actor: String, reason: String?): SetRoleOutcome
}

/**
 * The one way server code asks what someone may do:
 *
 * ```kotlin
 * if (!permissions.can(userId, Permission.FoodEditDirect)) return call.respond(HttpStatusCode.Forbidden, …)
 * ```
 *
 * One indexed read per call; nothing is cached, so a role change applies on the next request.
 */
@SingleIn(ServerScope::class)
@Inject
class Permissions(private val roles: UserRoleRepository) {
    suspend fun of(userId: UserId): Set<Permission> = roles.roleOf(userId).permissions

    suspend fun can(userId: UserId, permission: Permission): Boolean = permission in of(userId)
}
