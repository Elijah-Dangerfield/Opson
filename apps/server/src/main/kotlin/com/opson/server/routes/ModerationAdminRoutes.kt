package com.opson.server.routes

import com.opson.server.config.AdminConfig
import com.opson.server.domain.AppealState
import com.opson.server.domain.BanReason
import com.opson.server.domain.ContributionGate
import com.opson.server.domain.DeviceIntegrity
import com.opson.server.domain.ModerationActions
import com.opson.server.domain.ModerationKind
import com.opson.server.domain.ModerationOutcome
import com.opson.server.domain.ModerationReason
import com.opson.server.domain.ModerationRepository
import com.opson.server.domain.ModerationScope
import com.opson.server.domain.ModerationService
import com.opson.server.domain.NewModerationAction
import com.opson.server.domain.UserId
import com.opson.server.plugins.problem
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Removing bad actors, behind the `X-Admin-Token` gate of `/v1/admin`:
 *
 *  - `GET  /v1/admin/users/{id}/moderation`              standing, every action, contributions, devices, the trail
 *  - `POST /v1/admin/users/{id}/moderation {kind, reason, note?, untilEpochMs?, markDevices?, rollback?}`
 *        `kind` is `ban`, `suspension` (needs `untilEpochMs`) or `restriction` (contributions only)
 *  - `POST /v1/admin/users/{id}/contributions/rollback {reason}`  undo what they contributed
 *  - `POST /v1/admin/moderation/{actionId}/lift {note?}`
 *  - `PUT  /v1/admin/moderation/{actionId}/appeal {state, note?}`  `none`, `open`, `upheld`, `overturned`
 *  - `POST /v1/admin/devices/{markerId}/clear {note?}`     the reset path for a device someone else marked
 *  - `GET  /v1/admin/moderation?active=true&limit=100`    actions across everyone, newest first
 *
 * `X-Admin-Actor` goes on every row and event. An account id that has since been deleted still
 * finds its actions and can still be rolled back: everything is looked up by its subject key.
 */
@OptIn(ExperimentalTime::class)
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
fun Route.moderationAdminRoutes(
    config: AdminConfig,
    service: ModerationService,
    actions: ModerationActions,
    bans: ModerationRepository,
    gate: ContributionGate,
    integrity: DeviceIntegrity,
    clock: Clock,
) {
    val logger = LoggerFactory.getLogger("ModerationAdminRoutes")

    get("/v1/admin/users/{id}/moderation") {
        if (!call.requireAdmin(config)) return@get
        val userId = call.pathUser() ?: return@get call.invalidUser()
        val now = clock.now()
        val history = actions.history(userId)
        val summary = actions.summary(userId)
        val ban = bans.banStatusFor(userId)
        val standing = when {
            ban != null -> StandingResponse(
                kind = if (ban.reason == BanReason.Suspended) ModerationKind.Suspension.wire else ModerationKind.Ban.wire,
                scope = ModerationScope.Full.wire,
                untilEpochMs = ban.until?.toEpochMilliseconds(),
            )
            else -> history.filter { it.isActive(now) && it.scope == ModerationScope.Contributions }
                .let { active ->
                    if (active.isEmpty()) {
                        null
                    } else {
                        StandingResponse(
                            kind = ModerationKind.Restriction.wire,
                            scope = ModerationScope.Contributions.wire,
                            untilEpochMs = if (active.any { it.until == null }) null else active.mapNotNull { it.until }.max().toEpochMilliseconds(),
                        )
                    }
                }
        }
        call.respond(
            UserModerationResponse(
                userId = userId.toString(),
                standing = standing,
                freeProAllowed = gate.mayReceiveFreePro(userId),
                actions = history.map { it.toResponse(now) },
                contributions = summary.toResponse(),
                markers = summary.markers.map { it.toResponse() },
                events = actions.events(userId, EVENT_LIMIT).map { it.toResponse() },
                deviceChecks = integrity.configured,
                reasons = ModerationReason.entries.map { it.wire },
            ),
        )
    }

    post("/v1/admin/users/{id}/moderation") {
        if (!call.requireAdmin(config)) return@post
        val userId = call.pathUser() ?: return@post call.invalidUser()
        val body = call.receiveOrNull<ModerateRequest>() ?: return@post call.bad("invalid_body", "Expected {kind, reason, note, untilEpochMs}.")
        val kind = ModerationKind.fromWire(body.kind)
            ?: return@post call.bad("invalid_kind", "kind must be ban, suspension or restriction.")
        val reason = ModerationReason.fromWire(body.reason)
            ?: return@post call.bad("invalid_reason", "reason must be one of ${ModerationReason.entries.joinToString { it.wire }}.")
        val note = body.note?.trim()?.ifEmpty { null }
        if (note != null && note.length > MAX_NOTE) return@post call.bad("invalid_note", "note must be at most $MAX_NOTE characters.")
        val until = body.untilEpochMs?.let(Instant::fromEpochMilliseconds)
        if (until != null && until <= clock.now()) return@post call.bad("invalid_until", "untilEpochMs must be in the future.")
        if (kind == ModerationKind.Suspension && until == null) return@post call.bad("invalid_until", "A suspension needs untilEpochMs.")
        val acted = service.act(
            NewModerationAction(userId, kind, reason, note, until, body.markDevices),
            call.moderationActor(),
            rollback = body.rollback,
        )
        when (val outcome = acted.outcome) {
            is ModerationOutcome.Done -> {
                logger.info(
                    "moderation outcome={} reason={} mark_devices={} perks_removed={} rollback={}",
                    kind.wire, reason.wire, body.markDevices, acted.perksRemoved.size, body.rollback,
                )
                call.respond(
                    HttpStatusCode.Created,
                    ModeratedResponse(
                        action = outcome.action.toResponse(clock.now()),
                        perksRemoved = acted.perksRemoved,
                        rolledBack = acted.rolledBack?.map { it.toResponse() },
                    ),
                )
            }
            ModerationOutcome.NoSuchUser -> call.noSuchUser()
            ModerationOutcome.NotFound, ModerationOutcome.AlreadyLifted -> call.noSuchUser()
        }
    }

    post("/v1/admin/users/{id}/contributions/rollback") {
        if (!call.requireAdmin(config)) return@post
        val userId = call.pathUser() ?: return@post call.invalidUser()
        val body = call.receiveOrNull<RollbackRequest>() ?: RollbackRequest()
        val reason = body.reason?.trim()?.ifEmpty { null } ?: return@post call.bad("invalid_reason", "Say why (audited).")
        if (reason.length > MAX_NOTE) return@post call.bad("invalid_reason", "reason must be at most $MAX_NOTE characters.")
        val results = service.rollback(userId, call.moderationActor(), reason, actionId = null)
        logger.info("moderation outcome=rollback {}", results.joinToString(" ") { "${it.kind}=${it.undone}/${it.closed}/${it.skipped}" })
        call.respond(RollbackResponse(results.map { it.toResponse() }))
    }

    post("/v1/admin/moderation/{actionId}/lift") {
        if (!call.requireAdmin(config)) return@post
        val actionId = call.pathUuid("actionId") ?: return@post call.bad("invalid_id", "actionId must be a UUID.")
        val note = (call.receiveOrNull<LiftRequest>() ?: LiftRequest()).note?.trim()?.ifEmpty { null }
        if (note != null && note.length > MAX_NOTE) return@post call.bad("invalid_note", "note must be at most $MAX_NOTE characters.")
        when (val outcome = actions.lift(actionId, call.moderationActor(), note)) {
            is ModerationOutcome.Done -> {
                logger.info("moderation outcome=lift kind={}", outcome.action.kind.wire)
                call.respond(outcome.action.toResponse(clock.now()))
            }
            ModerationOutcome.AlreadyLifted -> call.respond(HttpStatusCode.Conflict, problem("already_lifted", "That action was lifted already."))
            ModerationOutcome.NotFound, ModerationOutcome.NoSuchUser ->
                call.respond(HttpStatusCode.NotFound, problem("not_found", "No action with that id."))
        }
    }

    put("/v1/admin/moderation/{actionId}/appeal") {
        if (!call.requireAdmin(config)) return@put
        val actionId = call.pathUuid("actionId") ?: return@put call.bad("invalid_id", "actionId must be a UUID.")
        val body = call.receiveOrNull<AppealRequest>() ?: return@put call.bad("invalid_body", "Expected {state, note}.")
        val state = AppealState.fromWire(body.state)
            ?: return@put call.bad("invalid_state", "state must be one of ${AppealState.entries.joinToString { it.wire }}.")
        val note = body.note?.trim()?.ifEmpty { null }
        if (note != null && note.length > MAX_NOTE) return@put call.bad("invalid_note", "note must be at most $MAX_NOTE characters.")
        when (val outcome = actions.setAppeal(actionId, state, note, call.moderationActor())) {
            is ModerationOutcome.Done -> call.respond(outcome.action.toResponse(clock.now()))
            else -> call.respond(HttpStatusCode.NotFound, problem("not_found", "No action with that id."))
        }
    }

    post("/v1/admin/devices/{markerId}/clear") {
        if (!call.requireAdmin(config)) return@post
        val markerId = call.pathUuid("markerId") ?: return@post call.bad("invalid_id", "markerId must be a UUID.")
        val note = (call.receiveOrNull<ClearMarkerRequest>() ?: ClearMarkerRequest()).note?.trim()?.ifEmpty { null }
        if (actions.clearMarker(markerId, call.moderationActor(), note?.take(MAX_NOTE))) {
            logger.info("moderation outcome=clear_device")
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.NotFound, problem("not_found", "No marker in force with that id."))
        }
    }

    get("/v1/admin/moderation") {
        if (!call.requireAdmin(config)) return@get
        val active = call.parameters["active"]?.lowercase() == "true"
        val limit = call.parameters["limit"]?.toIntOrNull() ?: LIST_LIMIT
        val now = clock.now()
        call.respond(ModerationListResponse(actions.recent(active, limit).map { it.toResponse(now) }))
    }
}

private fun ApplicationCall.pathUser(): UserId? = parameters["id"]?.let(UserId::parse)

private fun ApplicationCall.pathUuid(name: String): UUID? = parameters[name]?.let {
    try {
        UUID.fromString(it)
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun ApplicationCall.moderationActor(): String =
    (request.header("X-Admin-Actor")?.trim()?.takeUnless { it.isEmpty() } ?: "admin").take(MAX_ACTOR)

private suspend fun ApplicationCall.invalidUser() =
    respond(HttpStatusCode.BadRequest, problem("invalid_user_id", "Not a user id."))

private suspend fun ApplicationCall.noSuchUser() =
    respond(HttpStatusCode.NotFound, problem("not_found", "No account with that id."))

private suspend fun ApplicationCall.bad(code: String, message: String) =
    respond(HttpStatusCode.BadRequest, problem(code, message))

private suspend inline fun <reified T : Any> ApplicationCall.receiveOrNull(): T? = try {
    receive<T>()
} catch (_: BadRequestException) {
    null
}

private const val MAX_NOTE = 1_000
private const val MAX_ACTOR = 200
private const val EVENT_LIMIT = 100
private const val LIST_LIMIT = 100
