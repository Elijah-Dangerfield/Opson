@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.entitlements

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * A paid (or one day paid) capability, mirrored from the server's `Entitlement`
 * (`apps/server/.../domain/Entitlements.kt`). Ask [EntitlementRepository.has], never whether
 * someone is Pro: Pro is a bundle of these in the server's remote config, and a feature can
 * move between Free and Pro, or go to one person, without a release.
 *
 * Free features have none. A key the server sends that this build doesn't know is ignored.
 */
@HiddenFromObjC
enum class Entitlement(val key: String) {
    PhotoEstimate("photo_estimate"),
    ShareProfessional("share_professional"),
    ShareFriends("share_friends"),
    AdaptiveBurn("adaptive_burn"),
    LongRangeReports("long_range_reports"),
    RecipeImport("recipe_import"),
    ;

    companion object {
        fun fromKey(key: String): Entitlement? = entries.firstOrNull { it.key == key }

        /**
         * What every account has until the server says otherwise: the server's seeded
         * `entitlements.everyone`. Used before the first answer, offline before one, and against
         * a server that predates entitlements, so nothing that was free disappears while the
         * app doesn't know.
         */
        val DEFAULTS: Set<Entitlement> = setOf(PhotoEstimate)
    }
}

/** Who someone is to Opson. Everyone without a role on the server is [Member]. */
@HiddenFromObjC
enum class Role(val wire: String) {
    Member("member"),
    Builder("builder"),
    Moderator("moderator"),
    Admin("admin"),
    Owner("owner"),
    ;

    companion object {
        fun fromWire(wire: String): Role = entries.firstOrNull { it.wire == wire } ?: Member
    }
}

/** What a role lets someone do, mirrored from the server's `Permission`. The server checks every one itself. */
@HiddenFromObjC
enum class Permission(val key: String) {
    FoodContribute("food.contribute"),
    FoodReview("food.review"),

    /** Edits go straight into the catalog and count as verification. */
    FoodEditDirect("food.edit.direct"),
    ;

    companion object {
        fun fromKey(key: String): Permission? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The signed-in account's entitlements, from `GET /v1/me/entitlements`, cached on the phone
 * for offline use and refreshed on sign-in, foreground and reconnect. Until the first answer
 * it holds [Entitlement.DEFAULTS].
 *
 * This only decides what the app offers. The server checks the same entitlement on every
 * gated route, so a stale cache can show a feature the server then refuses, never the reverse.
 */
@HiddenFromObjC
interface EntitlementRepository {
    /** The entitlements held now. */
    val entitlements: StateFlow<Set<Entitlement>>

    /** Whether [entitlement] is held right now, a grant's expiry included. */
    fun has(entitlement: Entitlement): Boolean = entitlement in entitlements.value

    fun observe(entitlement: Entitlement): Flow<Boolean> = entitlements.map { entitlement in it }.distinctUntilChanged()
}

/** The signed-in account's role and what it may do. [Role.Member] with nothing until the server says otherwise. */
@HiddenFromObjC
interface RoleRepository {
    val role: StateFlow<Role>
    val permissions: StateFlow<Set<Permission>>

    fun can(permission: Permission): Boolean = permission in permissions.value
}
