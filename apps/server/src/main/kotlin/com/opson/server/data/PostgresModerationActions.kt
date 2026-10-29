package com.opson.server.data

import com.opson.server.db.Database
import com.opson.server.db.boolArg
import com.opson.server.db.execute
import com.opson.server.db.instantArg
import com.opson.server.db.instantOrNull
import com.opson.server.db.intArg
import com.opson.server.db.queryRows
import com.opson.server.db.textArg
import com.opson.server.db.uuidArg
import com.opson.server.db.uuidOrNull
import com.opson.server.di.ServerScope
import com.opson.server.domain.AppealState
import com.opson.server.domain.ContributionSummary
import com.opson.server.domain.DeviceMarker
import com.opson.server.domain.ModerationAction
import com.opson.server.domain.ModerationActions
import com.opson.server.domain.ModerationEvent
import com.opson.server.domain.ModerationKind
import com.opson.server.domain.ModerationOutcome
import com.opson.server.domain.ModerationReason
import com.opson.server.domain.NewModerationAction
import com.opson.server.domain.UserId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.sql.TextColumnType
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.sql.ResultSet
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * `moderation_actions`, `moderation_events` and `device_markers` (V29). Each write and its event
 * share a transaction. Reads by account go through `public.subject_key(id)`, so an account that
 * has since been deleted is still found by the id it had.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
@OptIn(ExperimentalTime::class)
class PostgresModerationActions(
    private val database: Database,
    private val clock: Clock,
) : ModerationActions {

    override suspend fun act(action: NewModerationAction, actor: String): ModerationOutcome = database.transaction {
        val exists = queryRows("SELECT 1 FROM auth.users WHERE id = ?", listOf(uuidArg(action.userId.value))) { true }.isNotEmpty()
        if (!exists) return@transaction ModerationOutcome.NoSuchUser
        val now = clock.now()
        val stored = ModerationAction(
            id = UUID.randomUUID(),
            userId = action.userId,
            kind = action.kind,
            reason = action.reason,
            note = action.note,
            createdAt = now,
            createdBy = actor,
            until = action.until,
            markDevices = action.markDevices,
        )
        execute(
            "INSERT INTO moderation_actions (id, user_id, kind, scope, reason, note, created_at, created_by, until, mark_devices) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            listOf(
                uuidArg(stored.id), uuidArg(action.userId.value), textArg(stored.kind.wire), textArg(stored.scope.wire),
                textArg(stored.reason.wire), textArg(stored.note), instantArg(now), textArg(actor), instantArg(stored.until),
                boolArg(stored.markDevices),
            ),
        )
        val marked = if (action.markDevices) markDevicesOf(action.userId, stored.id, actor) else 0
        writeEvent(now, actor, stored.kind.eventName, stored.id, action.userId, before = null, after = stored.snapshot(marked))
        ModerationOutcome.Done(stored)
    }

    override suspend fun lift(actionId: UUID, actor: String, note: String?): ModerationOutcome = database.transaction {
        val before = find(actionId, forUpdate = true) ?: return@transaction ModerationOutcome.NotFound
        if (before.liftedAt != null) return@transaction ModerationOutcome.AlreadyLifted
        val now = clock.now()
        execute(
            "UPDATE moderation_actions SET lifted_at = ?, lifted_by = ?, lift_note = ? WHERE id = ?",
            listOf(instantArg(now), textArg(actor), textArg(note), uuidArg(actionId)),
        )
        // A device marked for this action is cleared with it; a bit already written is reset on the device's next contact.
        execute(
            "UPDATE device_markers SET cleared_at = ?, cleared_by = ?, clear_note = ?, " +
                "remote_state = CASE WHEN remote_state = 'set' THEN 'clear_pending' ELSE 'cleared' END " +
                "WHERE action_id = ? AND cleared_at IS NULL",
            listOf(instantArg(now), textArg(actor), textArg(note ?: "action lifted"), uuidArg(actionId)),
        )
        val after = before.copy(liftedAt = now, liftedBy = actor, liftNote = note)
        writeEvent(now, actor, "lift", actionId, before.userId, before.snapshot(), after.snapshot())
        ModerationOutcome.Done(after)
    }

    override suspend fun setAppeal(actionId: UUID, state: AppealState, note: String?, actor: String): ModerationOutcome =
        database.transaction {
            val before = find(actionId, forUpdate = true) ?: return@transaction ModerationOutcome.NotFound
            val now = clock.now()
            execute(
                "UPDATE moderation_actions SET appeal_state = ?, appeal_note = ?, appeal_updated_at = ?, appeal_updated_by = ? WHERE id = ?",
                listOf(textArg(state.wire), textArg(note), instantArg(now), textArg(actor), uuidArg(actionId)),
            )
            val after = before.copy(appeal = state, appealNote = note, appealUpdatedAt = now, appealUpdatedBy = actor)
            writeEvent(now, actor, "appeal", actionId, before.userId, before.snapshot(), after.snapshot())
            ModerationOutcome.Done(after)
        }

    override suspend fun history(userId: UserId): List<ModerationAction> = database.transaction {
        queryRows(
            "$ACTION_SELECT WHERE subject_key = public.subject_key(?) ORDER BY created_at DESC",
            listOf(uuidArg(userId.value)),
        ) { it.toAction() }
    }

    override suspend fun recent(activeOnly: Boolean, limit: Int): List<ModerationAction> = database.transaction {
        val filter = if (activeOnly) "WHERE lifted_at IS NULL AND (until IS NULL OR until > ?)" else ""
        val args = if (activeOnly) listOf(instantArg(clock.now()), intArg(limit.coerceIn(1, MAX_LIMIT))) else listOf(intArg(limit.coerceIn(1, MAX_LIMIT)))
        queryRows("$ACTION_SELECT $filter ORDER BY created_at DESC LIMIT ?", args) { it.toAction() }
    }

    override suspend fun events(userId: UserId, limit: Int): List<ModerationEvent> = database.transaction {
        queryRows(
            "SELECT id, at, actor, action, action_id, user_id, before_jsonb::text AS before, after_jsonb::text AS after " +
                "FROM moderation_events WHERE subject_key = public.subject_key(?) ORDER BY at DESC, seq DESC LIMIT ?",
            listOf(uuidArg(userId.value), intArg(limit.coerceIn(1, MAX_LIMIT))),
        ) { rs ->
            ModerationEvent(
                id = rs.getObject("id", UUID::class.java),
                atEpochMs = rs.instantOrNull("at")!!.toEpochMilliseconds(),
                actor = rs.getString("actor"),
                action = rs.getString("action"),
                actionId = rs.uuidOrNull("action_id"),
                userId = rs.uuidOrNull("user_id")?.let(::UserId),
                before = rs.getString("before")?.let(eventJson::parseToJsonElement),
                after = rs.getString("after")?.let(eventJson::parseToJsonElement),
            )
        }
    }

    override suspend fun summary(userId: UserId): ContributionSummary = database.transaction {
        val id = uuidArg(userId.value)
        val counts = queryRows(
            """
            SELECT
                (SELECT count(*) FROM food_provenance WHERE subject_key = public.subject_key(?) AND rolled_back_at IS NULL) AS accepted,
                (SELECT count(*) FROM food_provenance WHERE subject_key = public.subject_key(?) AND rolled_back_at IS NOT NULL) AS rolled_back,
                (SELECT count(*) FROM food_submissions WHERE user_id = ? AND status = 'pending') AS pending,
                (SELECT count(*) FROM device_sightings WHERE user_id = ?) AS devices
            """.trimIndent(),
            listOf(id, id, id, id),
        ) { rs -> listOf(rs.getInt("accepted"), rs.getInt("rolled_back"), rs.getInt("pending"), rs.getInt("devices")) }.single()
        val markers = queryRows(
            "$MARKER_SELECT WHERE subject_key = public.subject_key(?) " +
                "OR device_hash IN (SELECT device_hash FROM device_sightings WHERE user_id = ?) ORDER BY created_at DESC",
            listOf(id, id),
        ) { it.toMarker() }
        ContributionSummary(accepted = counts[0], rolledBack = counts[1], pending = counts[2], devices = counts[3], markers = markers)
    }

    override suspend fun clearMarker(markerId: UUID, actor: String, note: String?): Boolean = database.transaction {
        val now = clock.now()
        val cleared = queryRows(
            "UPDATE device_markers SET cleared_at = ?, cleared_by = ?, clear_note = ?, " +
                "remote_state = CASE WHEN remote_state = 'set' THEN 'clear_pending' ELSE 'cleared' END " +
                "WHERE id = ? AND cleared_at IS NULL RETURNING user_id, action_id, remote_state",
            listOf(instantArg(now), textArg(actor), textArg(note), uuidArg(markerId)),
        ) { rs -> Triple(rs.uuidOrNull("user_id"), rs.uuidOrNull("action_id"), rs.getString("remote_state")) }.singleOrNull()
            ?: return@transaction false
        writeEvent(
            now, actor, "clear_device", cleared.second, cleared.first?.let(::UserId), before = null,
            after = buildJsonObject {
                put("markerId", JsonPrimitive(markerId.toString()))
                put("remoteState", JsonPrimitive(cleared.third))
                put("note", note?.let(::JsonPrimitive) ?: JsonNull)
            },
        )
        true
    }

    override suspend fun record(actor: String, action: String, userId: UserId, actionId: UUID?, after: JsonElement?) {
        database.transaction { writeEvent(clock.now(), actor, action, actionId, userId, before = null, after = after) }
    }

    /** Marks every install key the account contributed from or signed in with. Returns how many were new. */
    private fun markDevicesOf(userId: UserId, actionId: UUID, actor: String): Int = queryRows(
        """
        INSERT INTO device_markers (id, device_hash, platform, action_id, user_id, created_by)
        SELECT gen_random_uuid(), d.device_hash, d.platform, ?, ?, ?
        FROM (
            SELECT DISTINCT ON (device_hash) device_hash, platform FROM (
                SELECT device_hash, platform FROM device_sightings WHERE user_id = ?
                UNION ALL
                SELECT public.device_key_hash(install_id),
                       CASE WHEN platform IN ('ios', 'android') THEN platform ELSE 'other' END
                FROM user_installs WHERE user_id = ? AND install_id <> ''
            ) all_devices
        ) d
        ON CONFLICT (device_hash) WHERE cleared_at IS NULL DO NOTHING
        RETURNING id
        """.trimIndent(),
        listOf(uuidArg(actionId), uuidArg(userId.value), textArg(actor), uuidArg(userId.value), uuidArg(userId.value)),
    ) { true }.size

    private fun find(actionId: UUID, forUpdate: Boolean): ModerationAction? = queryRows(
        "$ACTION_SELECT WHERE id = ?" + if (forUpdate) " FOR UPDATE" else "",
        listOf(uuidArg(actionId)),
    ) { it.toAction() }.singleOrNull()

    private fun writeEvent(
        now: Instant,
        actor: String,
        action: String,
        actionId: UUID?,
        userId: UserId?,
        before: JsonElement?,
        after: JsonElement?,
    ) {
        // A deleted account has no user id left (a rollback after deletion): the event keeps its subject key.
        execute(
            "INSERT INTO moderation_events (id, at, actor, action, action_id, user_id, subject_key, before_jsonb, after_jsonb) " +
                "VALUES (?, ?, ?, ?, ?, (SELECT id FROM auth.users WHERE id = ?), " +
                "coalesce((SELECT subject_key FROM moderation_actions WHERE id = ?), public.subject_key(?)), " +
                "CAST(? AS jsonb), CAST(? AS jsonb))",
            listOf(
                uuidArg(UUID.randomUUID()), instantArg(now), textArg(actor), textArg(action), uuidArg(actionId), uuidArg(userId?.value),
                uuidArg(actionId), uuidArg(userId?.value),
                TextColumnType() to before?.let { eventJson.encodeToString(JsonElement.serializer(), it) },
                TextColumnType() to after?.let { eventJson.encodeToString(JsonElement.serializer(), it) },
            ),
        )
    }

    private val ModerationKind.eventName: String
        get() = when (this) {
            ModerationKind.Ban -> "ban"
            ModerationKind.Suspension -> "suspend"
            ModerationKind.Restriction -> "restrict"
        }

    private fun ModerationAction.snapshot(devicesMarked: Int? = null): JsonObject = buildJsonObject {
        put("kind", JsonPrimitive(kind.wire))
        put("scope", JsonPrimitive(scope.wire))
        put("reason", JsonPrimitive(reason.wire))
        put("note", note?.let(::JsonPrimitive) ?: JsonNull)
        put("untilEpochMs", until?.let { JsonPrimitive(it.toEpochMilliseconds()) } ?: JsonNull)
        put("markDevices", JsonPrimitive(markDevices))
        devicesMarked?.let { put("devicesMarked", JsonPrimitive(it)) }
        put("liftedBy", liftedBy?.let(::JsonPrimitive) ?: JsonNull)
        put("appeal", JsonPrimitive(appeal.wire))
        put("appealNote", appealNote?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun ResultSet.toAction() = ModerationAction(
        id = getObject("id", UUID::class.java),
        userId = uuidOrNull("user_id")?.let(::UserId),
        kind = ModerationKind.fromWire(getString("kind")) ?: ModerationKind.Ban,
        reason = ModerationReason.fromWire(getString("reason")) ?: ModerationReason.Other,
        note = getString("note"),
        createdAt = instantOrNull("created_at")!!,
        createdBy = getString("created_by"),
        until = instantOrNull("until"),
        markDevices = getBoolean("mark_devices"),
        liftedAt = instantOrNull("lifted_at"),
        liftedBy = getString("lifted_by"),
        liftNote = getString("lift_note"),
        appeal = AppealState.fromWire(getString("appeal_state")) ?: AppealState.None,
        appealNote = getString("appeal_note"),
        appealUpdatedAt = instantOrNull("appeal_updated_at"),
        appealUpdatedBy = getString("appeal_updated_by"),
    )

    private fun ResultSet.toMarker() = DeviceMarker(
        id = getObject("id", UUID::class.java),
        platform = getString("platform"),
        actionId = uuidOrNull("action_id"),
        userId = uuidOrNull("user_id")?.let(::UserId),
        createdAt = instantOrNull("created_at")!!,
        createdBy = getString("created_by"),
        remoteState = getString("remote_state"),
        clearedAt = instantOrNull("cleared_at"),
        clearedBy = getString("cleared_by"),
    )

    private companion object {
        const val MAX_LIMIT = 500
        const val ACTION_SELECT =
            "SELECT id, user_id, kind, reason, note, created_at, created_by, until, mark_devices, lifted_at, lifted_by, lift_note, " +
                "appeal_state, appeal_note, appeal_updated_at, appeal_updated_by FROM moderation_actions"
        const val MARKER_SELECT =
            "SELECT id, platform, action_id, user_id, created_at, created_by, remote_state, cleared_at, cleared_by FROM device_markers"
        val eventJson = Json { ignoreUnknownKeys = true }
    }
}
