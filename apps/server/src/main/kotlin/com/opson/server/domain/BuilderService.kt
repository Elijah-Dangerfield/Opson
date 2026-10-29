package com.opson.server.domain

import com.opson.server.di.ServerScope
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.UUID
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
    /** Why they can't apply right now, when they aren't an active Builder. */
    val joinBlock: JoinBlock?,
    /** Their newest application, whatever became of it. */
    val application: BuilderApplication? = null,
) {
    val isActive: Boolean get() = membership?.status == BuilderStatus.Active
}

/** The outcome of applying. [Applied] covers a retry too: the application already pending stands. */
sealed interface ApplyOutcome {
    /** [created] is false when one was already pending: a retry, or a second tap. */
    data class Applied(val standing: BuilderStanding, val created: Boolean) : ApplyOutcome

    /** Already an active Builder: nothing to apply for. */
    data class AlreadyBuilder(val standing: BuilderStanding) : ApplyOutcome
    data class Blocked(val block: JoinBlock) : ApplyOutcome
    data object NoSuchUser : ApplyOutcome
}

/** The outcome of the owner deciding an application. */
sealed interface DecideOutcome {
    data class Decided(val application: BuilderApplication) : DecideOutcome
    data object NotFound : DecideOutcome

    /** Decided or withdrawn already. */
    data object NotPending : DecideOutcome

    /** Banned, suspended, restricted or on a marked device since applying: no free Pro. */
    data object Restricted : DecideOutcome

    /** At [BuilderPolicy.capacity] active Builders. */
    data class Full(val capacity: Int) : DecideOutcome
}

/** What the person wrote and chose, as the application route has checked it. */
data class ApplicationForm(
    val photoRetention: Boolean,
    val motivation: String? = null,
    val shopping: ShoppingFrequency? = null,
    val region: LabelRegion? = null,
    val via: String? = null,
)

/**
 * The Builder program's rules applied to one account: apply, withdraw and leave, the owner's
 * accept and decline, the trust level, and when Builder Pro runs out. Only [accept] (and an
 * admin's [restore]) makes someone a Builder: it writes the membership and sets the `builder`
 * role (whose bundles bring Pro through `entitlements.roles`) unless the person already holds a
 * role above it; leaving or removal clears it again. Nothing is cached: a reviewer's decision
 * counts on the next request.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(ServerScope::class)
@Inject
class BuilderService(
    private val builders: BuilderRepository,
    private val applications: BuilderApplicationRepository,
    private val roles: UserRoleRepository,
    private val config: AppConfigSource,
    private val gate: ContributionGate,
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
        val application = applications.latest(userId)
        val block = if (recorded?.status == BuilderStatus.Active) null else BuilderRules.applyBlock(isAnonymous, recorded, history, application, now)
        return BuilderStanding(role, recorded, trust, history, proUntil, block, application)
    }

    /**
     * Sends an application for the owner to decide. The same gates as joining had (a claimed
     * account, an approved food or a week of age, never removed) plus one pending at a time and
     * the wait after a decline; and a ban, a restriction or a marked device (V30) rules it out, as
     * it rules out the free Pro the program brings. Nobody becomes a Builder here.
     */
    suspend fun apply(userId: UserId, isAnonymous: Boolean, form: ApplicationForm, id: UUID = UUID.randomUUID()): ApplyOutcome {
        val current = standing(userId, isAnonymous)
        if (current.isActive) return ApplyOutcome.AlreadyBuilder(current)
        when (val block = current.joinBlock) {
            null -> Unit
            // A retry of the same Apply: the one waiting stands.
            JoinBlock.Pending -> return ApplyOutcome.Applied(current, created = false)
            else -> return ApplyOutcome.Blocked(block)
        }
        if (!gate.mayReceiveFreePro(userId)) return ApplyOutcome.Blocked(JoinBlock.Removed)
        val new = NewBuilderApplication(
            id = id,
            userId = userId,
            termsVersion = BuilderRules.TERMS_VERSION,
            photoRetention = form.photoRetention,
            motivation = form.motivation,
            shopping = form.shopping,
            region = form.region,
            via = form.via,
        )
        return when (applications.submit(new, clock.now())) {
            ApplyWrite.NoSuchUser -> ApplyOutcome.NoSuchUser
            is ApplyWrite.Created -> ApplyOutcome.Applied(standing(userId, isAnonymous), created = true)
            is ApplyWrite.AlreadyPending -> ApplyOutcome.Applied(standing(userId, isAnonymous), created = false)
        }
    }

    /** Takes back a pending application. False when nothing was pending. */
    suspend fun withdraw(userId: UserId): Boolean = applications.withdraw(userId, clock.now()) != null

    /**
     * The owner says yes: the membership, with the rules version and photo choice from the
     * application, and the `builder` role, audited under [actor]. Asks the gate again, since a
     * ban can land while an application waits, and holds to `builders.capacity`.
     */
    suspend fun accept(applicationId: UUID, actor: String, note: String? = null): DecideOutcome {
        val application = applications.find(applicationId) ?: return DecideOutcome.NotFound
        if (application.status != ApplicationStatus.Pending) return DecideOutcome.NotPending
        if (!gate.mayReceiveFreePro(application.userId)) return DecideOutcome.Restricted
        val capacity = policy().capacity
        if (builders.activeCount() >= capacity) return DecideOutcome.Full(capacity)
        val decided = applications.decide(applicationId, ApplicationStatus.Accepted, actor, note, clock.now())
            ?: return DecideOutcome.NotPending
        when (builders.join(application.userId, application.termsVersion, application.photoRetention, clock.now())) {
            JoinWrite.NoSuchUser -> return DecideOutcome.NotFound
            is JoinWrite.Joined -> if (roles.roleOf(application.userId) == Role.Member) {
                roles.setRole(application.userId, Role.Builder, actor = actor, reason = "Accepted into the Builder program")
            }
        }
        return DecideOutcome.Decided(decided)
    }

    /** The owner says no, with an optional note the applicant sees. They may apply again after [BuilderRules.REAPPLY_AFTER]. */
    suspend fun decline(applicationId: UUID, actor: String, note: String?): DecideOutcome {
        val application = applications.find(applicationId) ?: return DecideOutcome.NotFound
        if (application.status != ApplicationStatus.Pending) return DecideOutcome.NotPending
        return applications.decide(applicationId, ApplicationStatus.Declined, actor, note, clock.now())
            ?.let(DecideOutcome::Decided)
            ?: DecideOutcome.NotPending
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
        if (!gate.mayReceiveFreePro(userId)) return RoleWindow.LAPSED
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
