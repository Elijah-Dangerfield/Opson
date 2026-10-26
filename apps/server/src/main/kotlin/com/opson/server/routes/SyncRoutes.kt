package com.opson.server.routes

import com.opson.server.domain.DiaryRules
import com.opson.server.domain.SyncMutation
import com.opson.server.domain.SyncRepository
import com.opson.server.plugins.SUPABASE_JWT_AUTH
import com.opson.server.plugins.problem
import com.opson.server.plugins.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import kotlin.time.Clock

/**
 * `POST /v1/sync`: push the outbox and pull what changed, in one round trip
 * (docs/system-design.md §6). Always 200 for a well-formed request, even with
 * no mutations; the response is the source of truth the client merges.
 *
 * Each mutation is decoded by its `entity` and checked against [DiaryRules]
 * here. One that fails lands in `rejected` with a stable code and does not
 * stop the others:
 *  - `unknown_entity`: the entity string is not one the server knows;
 *  - `invalid_row`: the row could not be read, or broke a rule.
 * The rest go to the repository in one transaction and come back in `applied`.
 *
 * The user is always the JWT `sub`; a row never names its owner.
 *
 * Logs carry counts only. Rows are health data.
 */
fun Route.syncRoutes(repository: SyncRepository, clock: Clock) {
    val logger = LoggerFactory.getLogger("SyncRoutes")
    authenticate(SUPABASE_JWT_AUTH) {
        post("/v1/sync") {
            val userId = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val request = call.receive<SyncRequest>()
            if (request.mutations.size > DiaryRules.MAX_MUTATIONS) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    problem(
                        "too_many_mutations",
                        "At most ${DiaryRules.MAX_MUTATIONS} mutations per request; send the rest in the next call.",
                    ),
                )
            }
            if (request.cursor < 0) {
                return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_cursor", "cursor must be >= 0"))
            }

            val now = clock.now()
            val accepted = mutableListOf<SyncMutation>()
            val rejected = mutableListOf<SyncRejectionDto>()
            for (mutation in request.mutations) {
                val entity = SyncEntity.fromWire(mutation.entity)
                if (entity == null) {
                    rejected += SyncRejectionDto(mutation.id, "unknown_entity", "Unknown entity '${mutation.entity.take(40)}'")
                    continue
                }
                val row = try {
                    entity.decodeRow(mutation.row)
                } catch (e: IllegalArgumentException) {
                    // SerializationException is an IllegalArgumentException too.
                    rejected += SyncRejectionDto(mutation.id, "invalid_row", rowMessage(e))
                    continue
                }
                val error = DiaryRules.validate(row, now)
                if (error != null) {
                    rejected += SyncRejectionDto(mutation.id, "invalid_row", error)
                } else {
                    accepted += SyncMutation(mutation.id, row)
                }
            }

            val result = repository.sync(userId, request.cursor, accepted)
            if (rejected.isNotEmpty()) {
                logger.warn("sync user={} applied={} rejected={}", userId, result.applied.size, rejected.size)
            }
            call.respond(result.toResponse(rejected))
        }
    }
}

/**
 * Serialization messages quote the offending JSON, which is health data, so
 * they are reduced to the field name the library reports.
 */
private fun rowMessage(e: IllegalArgumentException): String {
    if (e !is SerializationException) return e.message ?: "row is invalid"
    val field = Regex("""(?:key|field) '([A-Za-z]+)'""").find(e.message.orEmpty())?.groupValues?.get(1)
        ?: Regex("""Field '([A-Za-z]+)'""").find(e.message.orEmpty())?.groupValues?.get(1)
    return if (field != null) "$field is missing or has the wrong type" else "row does not match the entity's shape"
}
