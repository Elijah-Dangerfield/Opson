package com.opson.server.routes

import com.opson.server.domain.ContributionSummary
import com.opson.server.domain.DeviceMarker
import com.opson.server.domain.ModerationAction
import com.opson.server.domain.ModerationEvent
import com.opson.server.domain.RevertedContributions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
data class ModerationActionResponse(
    val id: String,
    val userId: String? = null,
    val kind: String,
    val scope: String,
    val reason: String,
    val note: String? = null,
    val createdAtEpochMs: Long,
    val createdBy: String,
    val untilEpochMs: Long? = null,
    val markDevices: Boolean,
    val liftedAtEpochMs: Long? = null,
    val liftedBy: String? = null,
    val liftNote: String? = null,
    val appealState: String,
    val appealNote: String? = null,
    val appealUpdatedAtEpochMs: Long? = null,
    val appealUpdatedBy: String? = null,
    val active: Boolean,
)

@Serializable
data class ModerationEventResponse(
    val id: String,
    val atEpochMs: Long,
    val actor: String,
    val action: String,
    val actionId: String? = null,
    val before: JsonElement? = null,
    val after: JsonElement? = null,
)

@Serializable
data class DeviceMarkerResponse(
    val id: String,
    val platform: String,
    val actionId: String? = null,
    val createdAtEpochMs: Long,
    val createdBy: String,
    val remoteState: String,
    val clearedAtEpochMs: Long? = null,
    val clearedBy: String? = null,
)

@Serializable
data class ContributionSummaryResponse(
    val accepted: Int,
    val rolledBack: Int,
    val pending: Int,
    val devices: Int,
)

/** The standing the gates apply now: [scope] is `full` (the 403 ban envelope) or `contributions`. */
@Serializable
data class StandingResponse(val kind: String, val scope: String, val untilEpochMs: Long? = null)

@Serializable
data class UserModerationResponse(
    val userId: String,
    /** The strictest active action, or null in good standing. */
    val standing: StandingResponse? = null,
    val freeProAllowed: Boolean,
    val actions: List<ModerationActionResponse>,
    val contributions: ContributionSummaryResponse,
    val markers: List<DeviceMarkerResponse>,
    val events: List<ModerationEventResponse>,
    /** Which device checks this server runs: `devicecheck`, `play_integrity`. Empty means install keys only. */
    val deviceChecks: List<String>,
    val reasons: List<String>,
)

@Serializable
data class ModerateRequest(
    val kind: String? = null,
    val reason: String? = null,
    val note: String? = null,
    val untilEpochMs: Long? = null,
    val markDevices: Boolean = false,
    /** Also roll back what the person contributed, in the same call. */
    val rollback: Boolean = false,
)

@Serializable
data class RevertedResponse(val kind: String, val undone: Int, val closed: Int, val skipped: Int)

@Serializable
data class ModeratedResponse(
    val action: ModerationActionResponse,
    val perksRemoved: List<String>,
    val rolledBack: List<RevertedResponse>? = null,
)

@Serializable
data class LiftRequest(val note: String? = null)

@Serializable
data class AppealRequest(val state: String? = null, val note: String? = null)

@Serializable
data class RollbackRequest(val reason: String? = null)

@Serializable
data class RollbackResponse(val rolledBack: List<RevertedResponse>)

@Serializable
data class ClearMarkerRequest(val note: String? = null)

@Serializable
data class ModerationListResponse(val actions: List<ModerationActionResponse>)

@OptIn(ExperimentalTime::class)
internal fun ModerationAction.toResponse(now: Instant) = ModerationActionResponse(
    id = id.toString(),
    userId = userId?.toString(),
    kind = kind.wire,
    scope = scope.wire,
    reason = reason.wire,
    note = note,
    createdAtEpochMs = createdAt.toEpochMilliseconds(),
    createdBy = createdBy,
    untilEpochMs = until?.toEpochMilliseconds(),
    markDevices = markDevices,
    liftedAtEpochMs = liftedAt?.toEpochMilliseconds(),
    liftedBy = liftedBy,
    liftNote = liftNote,
    appealState = appeal.wire,
    appealNote = appealNote,
    appealUpdatedAtEpochMs = appealUpdatedAt?.toEpochMilliseconds(),
    appealUpdatedBy = appealUpdatedBy,
    active = isActive(now),
)

internal fun ModerationEvent.toResponse() = ModerationEventResponse(
    id = id.toString(),
    atEpochMs = atEpochMs,
    actor = actor,
    action = action,
    actionId = actionId?.toString(),
    before = before,
    after = after,
)

@OptIn(ExperimentalTime::class)
internal fun DeviceMarker.toResponse() = DeviceMarkerResponse(
    id = id.toString(),
    platform = platform,
    actionId = actionId?.toString(),
    createdAtEpochMs = createdAt.toEpochMilliseconds(),
    createdBy = createdBy,
    remoteState = remoteState,
    clearedAtEpochMs = clearedAt?.toEpochMilliseconds(),
    clearedBy = clearedBy,
)

internal fun ContributionSummary.toResponse() = ContributionSummaryResponse(accepted, rolledBack, pending, devices)

internal fun RevertedContributions.toResponse() = RevertedResponse(kind, undone, closed, skipped)
