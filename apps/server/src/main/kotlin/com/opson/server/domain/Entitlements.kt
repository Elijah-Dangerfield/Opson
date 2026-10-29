package com.opson.server.domain

import com.opson.server.http.ClientContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A paid (or one day paid) capability, by name. Whether someone has one is answered by
 * [EntitlementService.has], never by asking whether they are Pro: Pro is just a bundle of
 * these (`entitlements.bundles` in remote config), so a feature can move between Free and
 * Pro, or go to one person, without a release.
 *
 * Free features are deliberately absent (barcode, package scanning, fasting, GLP-1, export):
 * `docs/plans/feedback-2026-10-29/pricing-and-pro.md` keeps them free, and an entitlement
 * that everyone always has is a check that can only go wrong.
 *
 * [key] is the wire, config and database name. Never rename one; add a new key instead.
 */
enum class Entitlement(val key: String, val description: String) {
    PhotoEstimate("photo_estimate", "AI meal photo estimates"),
    ShareProfessional("share_professional", "Live sharing with a dietitian or coach"),
    ShareFriends("share_friends", "Sharing with friends"),
    AdaptiveBurn("adaptive_burn", "Adaptive burn from intake and weight"),
    LongRangeReports("long_range_reports", "Long-range trend reports"),
    RecipeImport("recipe_import", "Recipe import from a URL"),
    ;

    companion object {
        fun fromKey(key: String): Entitlement? = entries.firstOrNull { it.key == key }
    }
}

/** What a grant row names: one entitlement, or a bundle of them from [EntitlementPolicy.bundles]. */
enum class GrantKind(val wire: String) {
    Entitlement("entitlement"),
    Bundle("bundle"),
    ;

    companion object {
        fun fromWire(wire: String?): GrantKind? = entries.firstOrNull { it.wire == wire }
    }
}

/** A grant adds; a revoke takes one entitlement away whatever else would give it, "everyone" included. */
enum class GrantEffect(val wire: String) {
    Grant("grant"),
    Revoke("revoke"),
    ;

    companion object {
        fun fromWire(wire: String?): GrantEffect? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * One row of `entitlement_grants` (V28). [source] says where it came from, as a short tag
 * (`manual`, `founding`, `builder`, `comp`, later `app_store` / `play`), and [reason] is the
 * admin's note. A removed grant keeps its row ([removedAt]) so the history stays readable.
 */
@OptIn(ExperimentalTime::class)
data class EntitlementGrant(
    val id: UUID,
    val userId: UserId,
    val kind: GrantKind,
    val key: String,
    val effect: GrantEffect,
    val source: String,
    val reason: String?,
    val grantedBy: String,
    val grantedAt: Instant,
    val expiresAt: Instant?,
    val removedAt: Instant? = null,
    val removedBy: String? = null,
) {
    fun isActive(now: Instant): Boolean = removedAt == null && (expiresAt == null || expiresAt > now)
}

/** A grant before it has an id and a time. */
@OptIn(ExperimentalTime::class)
data class NewGrant(
    val userId: UserId,
    val kind: GrantKind,
    val key: String,
    val effect: GrantEffect,
    val source: String,
    val reason: String?,
    val expiresAt: Instant?,
)

sealed interface AddGrantOutcome {
    data class Added(val grant: EntitlementGrant) : AddGrantOutcome
    data object NoSuchUser : AddGrantOutcome
}

/** Per-user grants and revocations. Written only by the admin API (later also store webhooks); every write is audited. */
interface EntitlementGrantRepository {
    /** Grants and revocations in force at [now]. */
    @OptIn(ExperimentalTime::class)
    suspend fun active(userId: UserId, now: Instant): List<EntitlementGrant>

    /** Every row for [userId], removed and expired included, newest first. */
    suspend fun history(userId: UserId): List<EntitlementGrant>

    suspend fun add(grant: NewGrant, actor: String): AddGrantOutcome

    /** Marks a grant removed. False when [userId] has no such grant, or it was already removed. */
    suspend fun remove(userId: UserId, grantId: UUID, actor: String): Boolean
}

/**
 * The remote-config half of entitlements, read from the resolved app-config tree:
 *
 *  - `entitlements.everyone`: entitlements every account has (`["photo_estimate"]`).
 *  - `entitlements.bundles`: bundle name to entitlements (`{"pro": [...]}`).
 *  - `entitlements.roles`: role to the bundles it brings (`{"builder": ["pro"]}`).
 *
 * They are ordinary config flags, so they are edited, targeted (a rule can widen `everyone` to
 * a rollout percentage, or to one platform) and audited like any other. A path with no row
 * falls back to [DEFAULT], which matches what V28 seeds, so a missing row never takes a
 * feature away. A malformed value is refused by the admin API ([validate]); one that got in
 * anyway reads as its default. Unknown entitlement keys are ignored.
 */
data class EntitlementPolicy(
    val everyone: Set<Entitlement>,
    val bundles: Map<String, Set<Entitlement>>,
    val roleBundles: Map<Role, Set<String>>,
) {
    fun bundle(name: String): Set<Entitlement> = bundles[name].orEmpty()

    companion object {
        const val PATH_EVERYONE = "entitlements.everyone"
        const val PATH_BUNDLES = "entitlements.bundles"
        const val PATH_ROLES = "entitlements.roles"
        const val PRO = "pro"

        /** Today's behaviour: photo estimates are free for everyone, Pro is everything, staff and builders get Pro. */
        val DEFAULT = EntitlementPolicy(
            everyone = setOf(Entitlement.PhotoEstimate),
            bundles = mapOf(PRO to Entitlement.entries.toSet()),
            roleBundles = mapOf(Role.Owner to setOf(PRO), Role.Admin to setOf(PRO), Role.Builder to setOf(PRO)),
        )

        /** Reads the policy out of a resolved app-config tree (`AppConfigSource.read`). */
        fun from(tree: JsonObject): EntitlementPolicy {
            val node = tree["entitlements"] as? JsonObject ?: return DEFAULT
            return EntitlementPolicy(
                everyone = node["everyone"]?.let(::entitlementSet) ?: DEFAULT.everyone,
                bundles = node["bundles"]?.let(::bundleMap) ?: DEFAULT.bundles,
                roleBundles = node["roles"]?.let(::roleMap) ?: DEFAULT.roleBundles,
            )
        }

        /**
         * Null when [value] is a valid value for [path], else why not. Paths outside
         * `entitlements.` always pass. Unknown entitlement keys and bundle names are refused,
         * because a typo here would quietly take a feature away from people.
         */
        fun validate(path: String, value: JsonElement): String? {
            if (!path.startsWith("entitlements.")) return null
            return when (path) {
                PATH_EVERYONE -> keysError(value, "$PATH_EVERYONE must be a list of entitlement keys")
                PATH_BUNDLES -> {
                    val obj = value as? JsonObject ?: return "$PATH_BUNDLES must be an object of bundle name to entitlement keys"
                    obj.entries.firstNotNullOfOrNull { (name, keys) ->
                        nameError(name) ?: keysError(keys, "bundle \"$name\" must be a list of entitlement keys")
                    }
                }
                PATH_ROLES -> {
                    val obj = value as? JsonObject ?: return "$PATH_ROLES must be an object of role to bundle names"
                    obj.entries.firstNotNullOfOrNull { (role, bundles) ->
                        if (Role.fromWire(role) == null) return@firstNotNullOfOrNull "unknown role \"$role\""
                        val names = strings(bundles) ?: return@firstNotNullOfOrNull "role \"$role\" must be a list of bundle names"
                        names.firstNotNullOfOrNull(::nameError)
                    }
                }
                else -> "unknown entitlements setting $path; use $PATH_EVERYONE, $PATH_BUNDLES or $PATH_ROLES"
            }
        }

        private fun keysError(value: JsonElement, shape: String): String? {
            val keys = strings(value) ?: return shape
            val unknown = keys.filter { Entitlement.fromKey(it) == null }
            return if (unknown.isEmpty()) {
                null
            } else {
                "unknown entitlement ${unknown.joinToString()}; known: ${Entitlement.entries.joinToString { it.key }}"
            }
        }

        private fun nameError(name: String): String? =
            if (BUNDLE_NAME.matches(name)) null else "bundle name \"$name\" must be lowercase letters, digits or _"

        private val BUNDLE_NAME = Regex("[a-z][a-z0-9_]{0,31}")

        private fun strings(value: JsonElement): List<String>? {
            val array = value as? JsonArray ?: return null
            return array.map { item -> (item as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null }
        }

        private fun entitlementSet(value: JsonElement): Set<Entitlement>? =
            strings(value)?.mapNotNull(Entitlement::fromKey)?.toSet()

        private fun bundleMap(value: JsonElement): Map<String, Set<Entitlement>>? {
            val obj = value as? JsonObject ?: return null
            return obj.mapNotNull { (name, keys) -> entitlementSet(keys)?.let { name to it } }.toMap()
        }

        private fun roleMap(value: JsonElement): Map<Role, Set<String>>? {
            val obj = value as? JsonObject ?: return null
            return obj.mapNotNull { (wire, names) ->
                val role = Role.fromWire(wire) ?: return@mapNotNull null
                strings(names)?.let { role to it.toSet() }
            }.toMap()
        }
    }
}

/** Why someone has an entitlement. One entitlement can have several. */
@OptIn(ExperimentalTime::class)
sealed interface EntitlementSource {
    /** `entitlements.everyone`. */
    data object Everyone : EntitlementSource

    /** Their [role] brings [bundle] (`entitlements.roles`), until [expiresAt] when the role's bundles end ([RoleWindow]). */
    data class FromRole(val role: Role, val bundle: String, val expiresAt: Instant? = null) : EntitlementSource

    /** A grant row: the entitlement itself, or a [bundle] holding it. */
    data class FromGrant(val grant: EntitlementGrant, val bundle: String?) : EntitlementSource
}

/** Resolved entitlements, and the revocations that took any away. */
data class ResolvedEntitlements(
    val granted: Map<Entitlement, List<EntitlementSource>>,
    val revoked: Map<Entitlement, List<EntitlementGrant>>,
) {
    fun has(entitlement: Entitlement): Boolean = entitlement in granted

    companion object {
        /**
         * The union of everyone, the role's bundles and the active grants, minus active
         * revocations. Pure: [grants] must already be the ones in force.
         */
        fun resolve(
            policy: EntitlementPolicy,
            role: Role,
            grants: List<EntitlementGrant>,
            roleWindow: RoleWindow = RoleWindow.ALWAYS,
        ): ResolvedEntitlements {
            val sources = LinkedHashMap<Entitlement, MutableList<EntitlementSource>>()
            fun add(entitlement: Entitlement, source: EntitlementSource) {
                sources.getOrPut(entitlement) { mutableListOf() } += source
            }
            policy.everyone.forEach { add(it, EntitlementSource.Everyone) }
            if (roleWindow.active) {
                policy.roleBundles[role].orEmpty().forEach { bundle ->
                    policy.bundle(bundle).forEach { add(it, EntitlementSource.FromRole(role, bundle, roleWindow.until)) }
                }
            }
            val revoked = LinkedHashMap<Entitlement, MutableList<EntitlementGrant>>()
            for (grant in grants) {
                when (grant.effect) {
                    GrantEffect.Grant -> when (grant.kind) {
                        GrantKind.Entitlement -> Entitlement.fromKey(grant.key)?.let { add(it, EntitlementSource.FromGrant(grant, null)) }
                        GrantKind.Bundle -> policy.bundle(grant.key).forEach { add(it, EntitlementSource.FromGrant(grant, grant.key)) }
                    }
                    GrantEffect.Revoke -> Entitlement.fromKey(grant.key)?.let { revoked.getOrPut(it) { mutableListOf() } += grant }
                }
            }
            val granted = sources.filterKeys { it !in revoked }
            // Declaration order, so every answer lists them the same way.
            return ResolvedEntitlements(
                granted = Entitlement.entries.filter { it in granted }.associateWith { granted.getValue(it) },
                revoked = revoked,
            )
        }
    }
}

/**
 * Whether a role's bundles hold right now, and until when. Most roles' always do. A Builder who
 * joined through the app has them while they keep contributing ([BuilderRules.proUntil]), so
 * Builder Pro lapses on its own when someone stops, with no job to run.
 */
@OptIn(ExperimentalTime::class)
data class RoleWindow(val active: Boolean, val until: Instant?) {
    companion object {
        val ALWAYS = RoleWindow(active = true, until = null)
        val LAPSED = RoleWindow(active = false, until = null)
    }
}

/** Everything the server knows about what one account may use and do. */
data class UserAccess(
    val role: Role,
    val roleAssignment: RoleAssignment?,
    val entitlements: ResolvedEntitlements,
) {
    val permissions: Set<Permission> get() = role.permissions
}

/**
 * The one way server code asks whether someone may use a paid feature:
 *
 * ```kotlin
 * if (!entitlements.has(userId, Entitlement.PhotoEstimate, call.clientContext())) {
 *     return call.respond(HttpStatusCode.Forbidden, problem("entitlement_required", "…"))
 * }
 * ```
 *
 * [context] matters because the policy is remote config and can carry targeting rules
 * (a rollout percentage, one platform). Callers outside a request pass [NEUTRAL_CONTEXT].
 */
interface EntitlementService {
    suspend fun access(userId: UserId, context: ClientContext = NEUTRAL_CONTEXT): UserAccess

    suspend fun has(userId: UserId, entitlement: Entitlement, context: ClientContext = NEUTRAL_CONTEXT): Boolean =
        access(userId, context).entitlements.has(entitlement)

    /** The policy as [context] sees it, for the admin console. */
    suspend fun policy(context: ClientContext = NEUTRAL_CONTEXT): EntitlementPolicy

    companion object {
        /** No platform, version, country or install: what base values and user-id rules give. */
        val NEUTRAL_CONTEXT = ClientContext(
            platform = ClientContext.Platform.Other,
            appVersion = null,
            buildNumber = null,
            preferredLocales = listOf("en"),
            countryCode = null,
        )
    }
}
