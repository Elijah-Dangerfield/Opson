package com.opson.server.domain

import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Where one application to Builders is (`builder_applications.status`, V32). [wire] never changes. */
enum class ApplicationStatus(val wire: String) {
    Pending("pending"),
    Accepted("accepted"),
    Declined("declined"),

    /** The person took it back while it was pending. */
    Withdrawn("withdrawn"),
    ;

    companion object {
        fun fromWire(value: String?): ApplicationStatus? = entries.firstOrNull { it.wire == value }
    }
}

/** How often someone buys packaged food, from the application's one question about it. */
enum class ShoppingFrequency(val wire: String) {
    Weekly("weekly"),
    Monthly("monthly"),
    Rarely("rarely"),
    ;

    companion object {
        fun fromWire(value: String?): ShoppingFrequency? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Where someone shops, by the label format they will mostly photograph: Nutrition Facts per
 * serving in the US and Canada, per 100 g with kJ in the UK and the EU, the panel with both
 * columns in Australia and New Zealand.
 */
enum class LabelRegion(val wire: String) {
    Us("us"),
    Canada("ca"),
    Uk("uk"),
    Eu("eu"),
    AustraliaNz("au_nz"),
    Other("other"),
    ;

    companion object {
        fun fromWire(value: String?): LabelRegion? = entries.firstOrNull { it.wire == value }
    }
}

@OptIn(ExperimentalTime::class)
data class BuilderApplication(
    val id: UUID,
    val userId: UserId,
    val status: ApplicationStatus,
    val createdAt: Instant,
    val termsVersion: String,
    val photoRetention: Boolean,
    val motivation: String?,
    val shopping: ShoppingFrequency?,
    val region: LabelRegion?,
    val via: String?,
    val decidedAt: Instant? = null,
    val decidedBy: String? = null,
    /** The owner's words with a decision, shown to the applicant. */
    val decisionNote: String? = null,
    val withdrawnAt: Instant? = null,
)

/** What an application carries in. [shopping] and [region] are null only from an app from before applications. */
data class NewBuilderApplication(
    val id: UUID,
    val userId: UserId,
    val termsVersion: String,
    val photoRetention: Boolean,
    val motivation: String?,
    val shopping: ShoppingFrequency?,
    val region: LabelRegion?,
    val via: String?,
)

sealed interface ApplyWrite {
    data class Created(val application: BuilderApplication) : ApplyWrite

    /** One was already pending (a retry, or a second tap): that one stands. */
    data class AlreadyPending(val application: BuilderApplication) : ApplyWrite
    data object NoSuchUser : ApplyWrite
}

/**
 * What the owner reads beside an application: how old the account is and how it has been used,
 * what it has sent and how that went, and any moderation. Counts and dates only.
 */
@OptIn(ExperimentalTime::class)
data class ApplicantSummary(
    val email: String?,
    val displayName: String?,
    val isGuest: Boolean,
    val accountCreatedAt: Instant?,
    val entries: Long,
    val daysLogged: Long,
    val lastLogDay: String?,
    val submissionsApproved: Int,
    val submissionsRejected: Int,
    val submissionsPending: Int,
    val reportsAccepted: Int,
    val reportsRejected: Int,
    val reportsOpen: Int,
    val checks: Int,
    val moderationActions: Int,
    val moderationActive: Int,
    /** The most recent action's reason (`spam`, `fake_data`), whether or not it was lifted. */
    val lastModerationReason: String?,
    val earlierApplications: Int,
    val earlierDeclines: Int,
    /** Their `builder_members` status if they were ever a Builder: `active`, `left`, `removed`. */
    val membership: String?,
)

/** `builder_applications` (V32) and the summary the console shows beside one. */
@OptIn(ExperimentalTime::class)
interface BuilderApplicationRepository {
    /** The person's newest application, whatever its status. */
    suspend fun latest(userId: UserId): BuilderApplication?

    suspend fun find(id: UUID): BuilderApplication?

    /** Writes a pending application, or answers with the one already pending. */
    suspend fun submit(application: NewBuilderApplication, now: Instant): ApplyWrite

    /** Takes back the pending one; null when nothing was pending. */
    suspend fun withdraw(userId: UserId, now: Instant): BuilderApplication?

    /** Moves a pending application to [status]; null when it was not pending any more. */
    suspend fun decide(id: UUID, status: ApplicationStatus, actor: String, note: String?, now: Instant): BuilderApplication?

    /** Oldest first, so nobody waits behind people who came later. */
    suspend fun pending(limit: Int): List<BuilderApplication>

    suspend fun pendingCount(): Long

    /** Accepted and declined, newest decision first. */
    suspend fun decided(limit: Int): List<BuilderApplication>

    suspend fun summary(userId: UserId, excluding: UUID?): ApplicantSummary
}
