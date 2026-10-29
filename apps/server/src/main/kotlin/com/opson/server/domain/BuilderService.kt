package com.opson.server.domain

import com.opson.server.di.ServerScope
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Everything the app's Builder page and the routing need about one person, as of now. */
@OptIn(ExperimentalTime::class)
data class BuilderStanding(
    val role: Role,
    val membership: BuilderMembership?,
    val trust: TrustLevel,
    val history: ContributionHistory,
    /** When Builder Pro runs out; null when they get no Pro from the program. */
    val proUntil: Instant?,
    /** Why they can't join right now, when they aren't an active Builder. */
    val joinBlock: JoinBlock?,
) {
    val isActive: Boolean get() = membership?.status == BuilderStatus.Active
}

sealed interface JoinOutcome {
    data class Joined(val standing: BuilderStanding) : JoinOutcome
    data class Blocked(val block: JoinBlock) : JoinOutcome
    data object NoSuchUser : JoinOutcome
}

/**
 * The Builder program's rules applied to one account: join and leave, the trust level, and
 * when Builder Pro runs out. Joining sets the `builder` role (whose bundles bring Pro through
 * `entitlements.roles`) unless the person already holds a role above it; leaving or removal
 * clears it again. Nothing is cached: a reviewer's decision counts on the next request.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(ServerScope::class)
@Inject
class BuilderService(
    private val builders: BuilderRepository,
    private val roles: UserRoleRepository,
    private val config: AppConfigSource,
    private val clock: Clock,
) {
    suspend fun policy(): BuilderPolicy = BuilderPolicy.from(config.read(EntitlementService.NEUTRAL_CONTEXT, userId = null))

    suspend fun standing(userId: UserId, isAnonymous: Boolean = false): BuilderStanding {
        val now = clock.now()
        val role = roles.roleOf(userId)
        val membership = builders.membership(userId)
        val history = builders.history(userId, now)
        val trust = BuilderRules.trust(role, membership, history, now)
        val recorded = recordTrust(membership, trust, now)
        val proUntil = recorded?.takeIf { it.status == BuilderStatus.Active }?.let { BuilderRules.proUntil(it.joinedAt, history.acceptedAt, now) }
        val block = if (recorded?.status == BuilderStatus.Active) null else BuilderRules.joinBlock(isAnonymous, recorded, history, now)
        return BuilderStanding(role, recorded, trust, history, proUntil, block)
    }

    suspend fun join(userId: UserId, isAnonymous: Boolean, photoRetention: Boolean): JoinOutcome {
        val current = standing(userId, isAnonymous)
        if (current.isActive) return JoinOutcome.Joined(current)
        current.joinBlock?.let { return JoinOutcome.Blocked(it) }
        if (builders.activeCount() >= policy().capacity) return JoinOutcome.Blocked(JoinBlock.Full)
        return when (builders.join(userId, BuilderRules.TERMS_VERSION, photoRetention, clock.now())) {
            JoinWrite.NoSuchUser -> JoinOutcome.NoSuchUser
            is JoinWrite.Joined -> {
                if (current.role == Role.Member) roles.setRole(userId, Role.Builder, actor = ACTOR, reason = "Joined the Builder program")
                JoinOutcome.Joined(standing(userId, isAnonymous))
            }
        }
    }

    suspend fun leave(userId: UserId): Boolean {
        val left = builders.leave(userId, clock.now())
        if (left) dropRole(userId, "Left the Builder program")
        return left
    }

    suspend fun setPhotoRetention(userId: UserId, keep: Boolean): Boolean = builders.setPhotoRetention(userId, keep)

    suspend fun remove(userId: UserId, actor: String, reason: String?): Boolean {
        val removed = builders.remove(userId, actor, reason, clock.now())
        if (removed) dropRole(userId, reason ?: "Removed from the Builder program")
        return removed
    }

    suspend fun restore(userId: UserId, actor: String): Boolean {
        val restored = builders.restore(userId)
        if (restored && roles.roleOf(userId) == Role.Member) roles.setRole(userId, Role.Builder, actor, "Restored to the Builder program")
        return restored
    }

    /**
     * How long the role's bundles hold, for [EntitlementService]. Only a Builder who joined
     * through the app is limited, to while they are active; a `builder` role an admin set by hand
     * (a closed alpha, say) brings its bundles with no end, as V28 had it.
     */
    suspend fun roleWindow(userId: UserId, role: Role): RoleWindow {
        if (role != Role.Builder) return RoleWindow.ALWAYS
        val membership = builders.membership(userId) ?: return RoleWindow.ALWAYS
        if (membership.status != BuilderStatus.Active) return RoleWindow.LAPSED
        val now = clock.now()
        val until = BuilderRules.proUntil(membership.joinedAt, builders.history(userId, now).acceptedAt, now)
        return RoleWindow(active = until > now, until = until)
    }

    private suspend fun dropRole(userId: UserId, reason: String) {
        if (roles.roleOf(userId) == Role.Builder) roles.setRole(userId, Role.Member, actor = ACTOR, reason = reason)
    }

    /** Keeps `trusted_since` in step with the level, so the first audits after a promotion can be counted. */
    private suspend fun recordTrust(membership: BuilderMembership?, trust: TrustLevel, now: Instant): BuilderMembership? {
        membership ?: return null
        val isTrusted = trust == TrustLevel.Trusted
        if (isTrusted == (membership.trustedSince != null)) return membership
        val since = if (isTrusted) now else null
        builders.setTrustedSince(membership.userId, since)
        return membership.copy(trustedSince = since)
    }

    companion object {
        const val ACTOR = "builder-program"
    }
}
