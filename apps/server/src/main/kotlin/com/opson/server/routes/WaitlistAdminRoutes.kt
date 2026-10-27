package com.opson.server.routes

import com.opson.server.config.AdminConfig
import com.opson.server.domain.WaitlistEntry
import com.opson.server.domain.WaitlistRepository
import com.opson.server.plugins.problem
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import kotlin.time.ExperimentalTime

@Serializable
data class WaitlistEntryResponse(
    val id: String,
    val email: String,
    val source: String? = null,
    val createdAtEpochMs: Long,
)

@Serializable
data class WaitlistPageResponse(val total: Long, val entries: List<WaitlistEntryResponse>)

@Serializable
data class WaitlistRemoveRequest(val email: String? = null)

@Serializable
data class WaitlistRemoveResponse(val removed: Boolean)

/**
 * The owner's view of the waitlist, behind the same `X-Admin-Token` gate as
 * the rest of `/v1/admin`:
 *
 *  - `GET  /v1/admin/waitlist?limit=50&offset=0` the list, newest first, with the total
 *  - `GET  /v1/admin/waitlist/export.csv`         `email,source,created_at`, oldest first
 *  - `POST /v1/admin/waitlist/remove {email}`     deletes the address (any case); 404 `not_found`
 *
 * Removal deletes the row rather than marking it unsubscribed: the site's
 * privacy wording promises that a removed address is deleted. Removal takes
 * the address in the body, never the URL, so it stays out of request logs.
 * Rows marked unsubscribed (none are, yet) are left out of all three.
 */
@OptIn(ExperimentalTime::class)
fun Route.waitlistAdminRoutes(config: AdminConfig, repository: WaitlistRepository) {
    val logger = LoggerFactory.getLogger("WaitlistAdminRoutes")

    route("/v1/admin/waitlist") {
        get {
            if (!call.requireAdmin(config)) return@get
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, MAX_PAGE) ?: DEFAULT_PAGE
            val offset = call.request.queryParameters["offset"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0
            val page = repository.page(limit, offset)
            call.respond(WaitlistPageResponse(page.total, page.entries.map { it.toResponse() }))
        }

        get("/export.csv") {
            if (!call.requireAdmin(config)) return@get
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "opson-waitlist.csv")
                    .toString(),
            )
            call.respondTextWriter(ContentType.Text.CSV.withParameter("charset", "utf-8")) {
                writeRow(WAITLIST_CSV_HEADER)
                repository.export { writeRow(it.toCells()) }
            }
        }

        post("/remove") {
            if (!call.requireAdmin(config)) return@post
            val email = call.receive<WaitlistRemoveRequest>().email?.trim()?.takeUnless { it.isEmpty() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_email", "email is required."))
            if (repository.remove(email)) {
                logger.info("waitlist admin outcome=removed")
                call.respond(WaitlistRemoveResponse(removed = true))
            } else {
                call.respond(HttpStatusCode.NotFound, problem("not_found", "That address is not on the waitlist."))
            }
        }
    }
}

internal val WAITLIST_CSV_HEADER = listOf("email", "source", "created_at")

@OptIn(ExperimentalTime::class)
internal fun WaitlistEntry.toCells(): List<String?> = listOf(email, source, createdAt.toString())

@OptIn(ExperimentalTime::class)
private fun WaitlistEntry.toResponse() = WaitlistEntryResponse(
    id = id.toString(),
    email = email,
    source = source,
    createdAtEpochMs = createdAt.toEpochMilliseconds(),
)

private const val DEFAULT_PAGE = 50
private const val MAX_PAGE = 500
