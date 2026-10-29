package com.opson.server.domain

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Where someone stands in the Builder program (`builder_members.status`, V30). */
enum class BuilderStatus(val wire: String) {
    Active("active"),

    /** They left. They may join again, and keep the date they first joined. */
    Left("left"),

    /** Taken out for fake or copied data. Only an admin brings them back. */
    Removed("removed"),
    ;

    companion object {
        fun fromWire(value: String?): BuilderStatus? = entries.firstOrNull { it.wire == value }
    }
}

/** One person's row in `builder_members`. */
@OptIn(ExperimentalTime::class)
data class BuilderMembership(
    val userId: UserId,
    val status: BuilderStatus,
    val joinedAt: Instant,
    val termsVersion: String,
    /** They let Opson keep their label photos for up to two years, to test how well it reads labels. */
    val photoRetention: Boolean,
    /** When they last became Trusted; null while they are not. */
    val trustedSince: Instant?,
    /** The owner is holding them at Builder whatever their record says. */
    val trustHold: Boolean,
    val leftAt: Instant? = null,
    val removedAt: Instant? = null,
    val removedBy: String? = null,
    val removedReason: String? = null,
)

/**
 * How far Opson trusts someone's contributions (builder-program.md, "Trust"). Only [Trusted]
 * can be auto-approved, and even then a share of those approvals is audited at random.
 * [wire] is what the app and the console show; never rename one.
 */
enum class TrustLevel(val wire: String) {
    /** Not a Builder: every submission is reviewed, as it always was. */
    Anyone("anyone"),

    /** Joined: every submission reviewed, each accepted one counting toward Pro. */
    Builder("builder"),

    /** A long record of agreeing with reviewers: eligible for auto-approval. */
    Trusted("trusted"),

    /** Removed from the program. */
    Removed("removed"),
}

/**
 * The record trust and Builder Pro are worked out from, read fresh each time so a
 * reviewer's decision counts on the next request. Lists are newest first.
 */
@OptIn(ExperimentalTime::class)
data class ContributionHistory(
    val accountCreatedAt: Instant?,
    /** When each accepted contribution was accepted, newest first, at most [BuilderRules.PRO_THRESHOLD]. */
    val acceptedAt: List<Instant> = emptyList(),
    val accepted30d: Int = 0,
    val approvedSubmissions: Int = 0,
    /** Submissions a person decided, newest first, at most [BuilderRules.AGREEMENT_WINDOW]: did they leave every headline field alone? */
    val reviewerAgreed: List<Boolean> = emptyList(),
    val reviewedTotal: Int = 0,
    /** Rejections coded fake or copied, ever. */
    val fakeOrCopied: Int = 0,
    val decided30d: Int = 0,
    val rejected30d: Int = 0,
    val audits30d: Int = 0,
    val auditsFailed30d: Int = 0,
    /** Auto-approvals since they last became Trusted: the first [BuilderRules.AUDIT_ALL_FIRST] are all audited. */
    val autoApprovedSinceTrusted: Int = 0,
)

/** Why someone can't join yet. */
@OptIn(ExperimentalTime::class)
sealed interface JoinBlock {
    val code: String

    /** A guest account: guests are free to make in bulk. */
    data object Guest : JoinBlock {
        override val code = "guest"
    }

    /** Neither an approved food nor a week-old account yet; [availableAt] is when the age rule lets them. */
    data class TooNew(val availableAt: Instant) : JoinBlock {
        override val code = "too_new"
    }

    data object Removed : JoinBlock {
        override val code = "removed"
    }

    /** The program is at [BuilderPolicy.capacity]. */
    data object Full : JoinBlock {
        override val code = "full"
    }
}

/**
 * The program's numbers, in one place, with the reasons. Starting points from
 * builder-program.md; every lane runs in shadow until reviewers agree with it
 * ([BuilderPolicy.autoApprove]), so these can move once there is data.
 */
@OptIn(ExperimentalTime::class)
object BuilderRules {
    /** Accepted contributions in [PRO_WINDOW] that keep Pro on. A threshold, not a per-food credit, so junk earns nothing. */
    const val PRO_THRESHOLD = 10
    val PRO_WINDOW: Duration = 30.days

    /** A new Builder has Pro for this long while they get going. Kept on a rejoin, so leaving and coming back renews nothing. */
    val START_WINDOW: Duration = 30.days

    /** An account this old may join without an approved food yet. */
    val MIN_ACCOUNT_AGE: Duration = 7.days

    const val TRUSTED_MIN_REVIEWED = 50
    const val AGREEMENT_WINDOW = 50
    const val TRUSTED_MIN_AGREEMENT = 0.97

    /** Below this a Trusted Builder drops back; the gap to [TRUSTED_MIN_AGREEMENT] stops a level that flaps. */
    const val TRUSTED_KEEP_AGREEMENT = 0.95
    const val MAX_REJECT_RATE = 0.05
    const val MAX_AUDIT_FAIL_RATE = 0.05
    val TRUSTED_MIN_ACCOUNT_AGE: Duration = 30.days

    /** Every auto-approval of a newly Trusted Builder is audited until this many. */
    const val AUDIT_ALL_FIRST = 20

    /** Daily submissions for a Builder, up from [FoodLabelLimiter.DAILY_SUBMISSIONS]. */
    const val BUILDER_DAILY_SUBMISSIONS = 60

    const val TERMS_VERSION = "2026-10-29"

    /**
     * When Builder Pro runs out, as the nightly rule in builder-program.md would leave it, worked
     * out in closed form so no job has to run: while ten or more were accepted in the last 30
     * days it is always 30 days away; after that it stops moving, 30 days after the window last
     * held ten. A new Builder gets [START_WINDOW] from joining whatever their record.
     */
    fun proUntil(joinedAt: Instant, acceptedNewestFirst: List<Instant>, now: Instant): Instant {
        val start = joinedAt + START_WINDOW
        val tenth = acceptedNewestFirst.getOrNull(PRO_THRESHOLD - 1) ?: return start
        val lastHeld = minOf(now, tenth + PRO_WINDOW)
        return maxOf(start, lastHeld + PRO_WINDOW)
    }

    fun joinBlock(isAnonymous: Boolean, membership: BuilderMembership?, history: ContributionHistory, now: Instant): JoinBlock? {
        if (membership?.status == BuilderStatus.Removed) return JoinBlock.Removed
        if (isAnonymous) return JoinBlock.Guest
        if (membership != null || history.approvedSubmissions > 0) return null
        val created = history.accountCreatedAt ?: return null
        val availableAt = created + MIN_ACCOUNT_AGE
        return if (now < availableAt) JoinBlock.TooNew(availableAt) else null
    }

    /**
     * The level, with hysteresis: getting to Trusted takes [TRUSTED_MIN_AGREEMENT], staying takes
     * [TRUSTED_KEEP_AGREEMENT]. Any fake or copied rejection ends it for good; the owner's hold
     * keeps someone at Builder.
     */
    fun trust(role: Role, membership: BuilderMembership?, history: ContributionHistory, now: Instant): TrustLevel {
        if (membership?.status == BuilderStatus.Removed) return TrustLevel.Removed
        if (Permission.FoodContribute !in role.permissions) return TrustLevel.Anyone
        if (membership == null || membership.status != BuilderStatus.Active || membership.trustHold) return TrustLevel.Builder
        if (history.fakeOrCopied > 0) return TrustLevel.Builder
        val agreement = history.reviewerAgreed.takeIf { it.isNotEmpty() }?.let { list -> list.count { it }.toDouble() / list.size } ?: 0.0
        val rejectRate = if (history.decided30d == 0) 0.0 else history.rejected30d.toDouble() / history.decided30d
        if (rejectRate >= MAX_REJECT_RATE) return TrustLevel.Builder
        val wasTrusted = membership.trustedSince != null
        val trusted = if (wasTrusted) {
            val auditFailRate = if (history.audits30d == 0) 0.0 else history.auditsFailed30d.toDouble() / history.audits30d
            agreement >= TRUSTED_KEEP_AGREEMENT && auditFailRate <= MAX_AUDIT_FAIL_RATE
        } else {
            val oldEnough = history.accountCreatedAt?.let { now - it >= TRUSTED_MIN_ACCOUNT_AGE } ?: false
            history.reviewedTotal >= TRUSTED_MIN_REVIEWED && agreement >= TRUSTED_MIN_AGREEMENT && oldEnough
        }
        return if (trusted) TrustLevel.Trusted else TrustLevel.Builder
    }
}

/** Whether the auto-approval lane acts, only says what it would have done, or is off. */
enum class AutoApproveMode(val wire: String) {
    Off("off"),

    /** The default: every decision is recorded and everything still goes to a person, until reviewers agree with the lane on 99% of 300 or more. */
    Shadow("shadow"),
    On("on"),
    ;

    companion object {
        fun fromWire(value: String?): AutoApproveMode? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * The program's remote config, under `builders.` (server-only, like `entitlements.`):
 * `builders.autoApprove` (`off`, `shadow`, `on`), `builders.capacity` (how many may be active;
 * joining past it answers `full`), `builders.auditRate` (the share of Trusted auto-approvals
 * audited at random). A missing or malformed value reads as its default.
 */
data class BuilderPolicy(
    val autoApprove: AutoApproveMode = AutoApproveMode.Shadow,
    val capacity: Int = DEFAULT_CAPACITY,
    val auditRate: Double = DEFAULT_AUDIT_RATE,
) {
    companion object {
        /** Bounded so review stays bounded (builder-program.md, rollout phase 4). Set lower for a closed alpha. */
        const val DEFAULT_CAPACITY = 500
        const val DEFAULT_AUDIT_RATE = 0.10

        fun from(tree: JsonObject): BuilderPolicy {
            val node = tree["builders"] as? JsonObject ?: return BuilderPolicy()
            fun primitive(key: String) = node[key] as? JsonPrimitive
            return BuilderPolicy(
                autoApprove = primitive("autoApprove")?.takeIf { it.isString }?.content?.let(AutoApproveMode::fromWire) ?: AutoApproveMode.Shadow,
                capacity = primitive("capacity")?.intOrNull?.takeIf { it >= 0 } ?: DEFAULT_CAPACITY,
                auditRate = primitive("auditRate")?.doubleOrNull?.takeIf { it in 0.0..1.0 } ?: DEFAULT_AUDIT_RATE,
            )
        }
    }
}

sealed interface JoinWrite {
    data class Joined(val membership: BuilderMembership) : JoinWrite
    data object NoSuchUser : JoinWrite
}

/** `builder_members` and the reads trust is worked out from. */
@OptIn(ExperimentalTime::class)
interface BuilderRepository {
    suspend fun membership(userId: UserId): BuilderMembership?

    /** Joins, or rejoins after leaving with the first join date kept. Never brings back someone removed. */
    suspend fun join(userId: UserId, termsVersion: String, photoRetention: Boolean, now: Instant): JoinWrite

    suspend fun leave(userId: UserId, now: Instant): Boolean

    suspend fun setPhotoRetention(userId: UserId, keep: Boolean): Boolean

    suspend fun remove(userId: UserId, actor: String, reason: String?, now: Instant): Boolean

    /** An admin undoes a removal; they are a Builder again, at Builder level. */
    suspend fun restore(userId: UserId): Boolean

    suspend fun setTrustHold(userId: UserId, hold: Boolean): Boolean

    /** Written when the level is worked out and changes: [since] on becoming Trusted, null on dropping back. */
    suspend fun setTrustedSince(userId: UserId, since: Instant?)

    suspend fun activeCount(): Long

    suspend fun history(userId: UserId, now: Instant): ContributionHistory
}
