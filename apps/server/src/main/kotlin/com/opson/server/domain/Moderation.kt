package com.opson.server.domain

import kotlinx.serialization.json.JsonElement
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** How much an action takes away. [Full] is the 403 ban envelope on every call; [Contributions] only the contribution routes. */
enum class ModerationScope(val wire: String) {
    Full("full"),
    Contributions("contributions"),
}

/**
 * What an admin did to an account (`moderation_actions.kind`, V29). A ban and a suspension are
 * [ModerationScope.Full]; a suspension always ends. A restriction keeps the app working and stops
 * the person contributing: no food submissions, reports or corrections, no Builder perks.
 */
enum class ModerationKind(val wire: String, val scope: ModerationScope) {
    Ban("ban", ModerationScope.Full),
    Suspension("suspension", ModerationScope.Full),
    Restriction("restriction", ModerationScope.Contributions),
    ;

    /** Builder perks go with a ban or a restriction. A suspension is a cooling-off and leaves them. */
    val removesBuilderPerks: Boolean get() = this != Suspension

    companion object {
        fun fromWire(wire: String?): ModerationKind? = entries.firstOrNull { it.wire == wire?.trim()?.lowercase() }
    }
}

/** A short fixed list, so the board can count them. The note says the rest. */
enum class ModerationReason(val wire: String) {
    FakeData("fake_data"),
    CopiedData("copied_data"),
    Spam("spam"),
    Abuse("abuse"),
    BanEvasion("ban_evasion"),
    Other("other"),
    ;

    companion object {
        fun fromWire(wire: String?): ModerationReason? = entries.firstOrNull { it.wire == wire?.trim()?.lowercase() }
    }
}

/** Appeals arrive by email (the 403 envelope's `appealUrl`); this is where one stands. */
enum class AppealState(val wire: String) {
    None("none"),
    Open("open"),
    Upheld("upheld"),
    Overturned("overturned"),
    ;

    companion object {
        fun fromWire(wire: String?): AppealState? = entries.firstOrNull { it.wire == wire?.trim()?.lowercase() }
    }
}

@OptIn(ExperimentalTime::class)
data class ModerationAction(
    val id: UUID,
    /** Null once the account is deleted; the row stays, found by its subject key. */
    val userId: UserId?,
    val kind: ModerationKind,
    val reason: ModerationReason,
    val note: String?,
    val createdAt: Instant,
    val createdBy: String,
    val until: Instant?,
    val markDevices: Boolean,
    val liftedAt: Instant? = null,
    val liftedBy: String? = null,
    val liftNote: String? = null,
    val appeal: AppealState = AppealState.None,
    val appealNote: String? = null,
    val appealUpdatedAt: Instant? = null,
    val appealUpdatedBy: String? = null,
) {
    val scope: ModerationScope get() = kind.scope

    fun isActive(now: Instant): Boolean = liftedAt == null && (until == null || until > now)
}

@OptIn(ExperimentalTime::class)
data class NewModerationAction(
    val userId: UserId,
    val kind: ModerationKind,
    val reason: ModerationReason,
    val note: String?,
    val until: Instant?,
    val markDevices: Boolean,
)

sealed interface ModerationOutcome {
    data class Done(val action: ModerationAction) : ModerationOutcome
    data object NoSuchUser : ModerationOutcome
    data object NotFound : ModerationOutcome

    /** Lifting something already lifted. */
    data object AlreadyLifted : ModerationOutcome
}

/** One row of `moderation_events`: who did what, with the row before and after. */
data class ModerationEvent(
    val id: UUID,
    val atEpochMs: Long,
    val actor: String,
    val action: String,
    val actionId: UUID?,
    val userId: UserId?,
    val before: JsonElement?,
    val after: JsonElement?,
)

/** A device marker (`device_markers`): blocks contributions and free Pro from one install key, never logging. */
@OptIn(ExperimentalTime::class)
data class DeviceMarker(
    val id: UUID,
    val platform: String,
    val actionId: UUID?,
    val userId: UserId?,
    val createdAt: Instant,
    val createdBy: String,
    /** `pending`, `set`, `clear_pending` or `cleared`: whether DeviceCheck or device recall carries it yet. */
    val remoteState: String,
    val clearedAt: Instant?,
    val clearedBy: String?,
)

/** What the admin console shows about one account's contributions and devices. */
data class ContributionSummary(
    val accepted: Int,
    val rolledBack: Int,
    val pending: Int,
    val devices: Int,
    val markers: List<DeviceMarker>,
)

/**
 * The admin half of moderation. Every write and its `moderation_events` row share a transaction.
 * Reads and rollbacks work by the account's subject key, so they still find the record after the
 * account is deleted, given the id it had.
 */
@OptIn(ExperimentalTime::class)
interface ModerationActions {
    suspend fun act(action: NewModerationAction, actor: String): ModerationOutcome

    suspend fun lift(actionId: UUID, actor: String, note: String?): ModerationOutcome

    suspend fun setAppeal(actionId: UUID, state: AppealState, note: String?, actor: String): ModerationOutcome

    /** Every action on the account, newest first. */
    suspend fun history(userId: UserId): List<ModerationAction>

    /** Newest first, across everyone; [activeOnly] leaves out lifted and ended ones. */
    suspend fun recent(activeOnly: Boolean, limit: Int): List<ModerationAction>

    suspend fun events(userId: UserId, limit: Int): List<ModerationEvent>

    suspend fun summary(userId: UserId): ContributionSummary

    /** Clears a marker; the DeviceCheck or recall bit is reset the next time that device contributes. */
    suspend fun clearMarker(markerId: UUID, actor: String, note: String?): Boolean

    /** Writes one `moderation_events` row, for work done outside these methods (a rollback). */
    suspend fun record(actor: String, action: String, userId: UserId, actionId: UUID?, after: JsonElement?)
}

/** Where a contribution request came from, as its headers say. [deviceKey] is the app's random install key. */
data class ContributionSignals(
    val platform: String,
    val deviceKey: String?,
    val integrityToken: String?,
    /** What the app bound the Play Integrity token to: the submission's id. */
    val requestHash: String?,
)

sealed interface ContributionVerdict {
    data object Allowed : ContributionVerdict

    /** [by] is for logs and the ledger; the response never says which. */
    data class Restricted(val by: RestrictedBy) : ContributionVerdict
}

enum class RestrictedBy { Account, Device }

/**
 * The one question a contribution route asks: may this account, from this device, add to the
 * catalog? Never asked by anything that logs food. The Builder program and founding grants ask
 * [mayReceiveFreePro] before granting anything.
 */
interface ContributionGate {
    suspend fun check(userId: UserId, signals: ContributionSignals): ContributionVerdict

    /** False while the account is banned, suspended or restricted, or any device it contributed from is marked. */
    suspend fun mayReceiveFreePro(userId: UserId): Boolean
}

/** What DeviceCheck or Play Integrity said about a device. */
sealed interface DeviceVerdict {
    /** [marked]: Opson's contribution bit is set. [trusted]: a genuine app on a genuine device, as far as the service says. */
    data class Read(val marked: Boolean, val trusted: Boolean) : DeviceVerdict

    /** The token didn't verify: malformed, expired, another app's, or not bound to this request. */
    data object Invalid : DeviceVerdict

    /** The service couldn't be asked. Never a reason to block. */
    data object Failed : DeviceVerdict
}

/**
 * Apple's DeviceCheck or Google's Play Integrity, for one platform. Bit 0 (`bitFirst` on Android)
 * means "barred from contributing". Bit 1 is left for the free Pro programs to say "a free grant
 * was claimed on this device".
 */
interface DeviceIntegrityVerifier {
    /** The ledger's `signal`: `devicecheck` or `play_integrity`. */
    val signal: String

    suspend fun read(token: String, requestHash: String?): DeviceVerdict

    /** Sets or clears the contribution bit. False when the service refused or couldn't be reached. */
    suspend fun writeMarked(token: String, marked: Boolean): Boolean
}

/** The configured verifiers. Inert (null) until their keys are set: see "Moderation" in apps/server/README.md. */
interface DeviceIntegrity {
    fun forPlatform(platform: String): DeviceIntegrityVerifier?

    /** For the boot log. */
    val configured: List<String>
}

/**
 * Undoes one kind of contribution for a person. Contributed by multibinding (submissions,
 * reports and their corrections), so a new kind of contribution brings its own. Accepted
 * contributions are found through `food_provenance` by the account's subject key, so they are
 * found after the account is deleted too; contributions still waiting are closed as rejected.
 * Each records nothing itself: the caller writes the trail.
 */
interface ContributionReverter {
    val kind: String

    suspend fun revert(userId: UserId, actor: String, reason: String, actionId: UUID?): RevertedContributions
}

/** [undone]: accepted ones taken back. [closed]: waiting ones rejected. [skipped]: accepted but no longer undoable (a later edit replaced it). */
data class RevertedContributions(val kind: String, val undone: Int, val closed: Int, val skipped: Int = 0)

/** No gate: everything is allowed. The default for route tests that aren't about moderation. */
object OpenContributionGate : ContributionGate {
    override suspend fun check(userId: UserId, signals: ContributionSignals): ContributionVerdict = ContributionVerdict.Allowed

    override suspend fun mayReceiveFreePro(userId: UserId): Boolean = true
}
