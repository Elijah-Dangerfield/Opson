package com.opson.server.routes

import com.opson.server.domain.PlayerReportRepository
import com.opson.server.domain.UserId
import com.opson.server.plugins.PLAYER_REPORT_LIMIT
import com.opson.server.plugins.SUPABASE_JWT_AUTH
import com.opson.server.plugins.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

/**
 * Trust-and-safety endpoint. `POST /v1/reports` files a report against
 * another user and writes a row to `player_reports` via [reports]. Google
 * Play's UGC policy requires this path for apps with user-visible content.
 *
 * Rate-limited: reporting targets another user, so it's an abuse surface. No
 * auto-ban and no moderation-review UI — a human reads the reports later.
 * A self-report is rejected with `400`; the caller can't report themselves.
 */
fun Route.playerReportRoutes(reports: PlayerReportRepository) {
    authenticate(SUPABASE_JWT_AUTH) {
        rateLimit(RateLimitName(PLAYER_REPORT_LIMIT)) {
            post("/v1/reports") {
                val me = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val body = call.receive<PlayerReportBody>()
                val reported = body.reportedUserId.takeIf { it.isNotBlank() }?.let(UserId::parse)
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        reportProblem("invalid_user_id", "Reported user id must be a valid UUID."),
                    )
                if (me == reported) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        reportProblem("self_report", "You can't report yourself."),
                    )
                }

                val categories = body.categories
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { it.take(CATEGORY_MAX_LENGTH) }
                    .distinct()
                    .take(MAX_CATEGORIES)
                reports.record(
                    reporter = me,
                    reported = reported,
                    context = body.context?.trim()?.takeIf { it.isNotBlank() }?.take(CONTEXT_MAX_LENGTH),
                    reason = body.reason?.trim()?.takeIf { it.isNotBlank() }?.take(REASON_MAX_LENGTH),
                    categories = categories,
                )
                call.respond(HttpStatusCode.OK, PlayerReportResult(status = "received"))
            }
        }
    }
}

private const val REASON_MAX_LENGTH = 500
private const val CONTEXT_MAX_LENGTH = 100
private const val CATEGORY_MAX_LENGTH = 40
private const val MAX_CATEGORIES = 10

private fun reportProblem(code: String, message: String): Map<String, Map<String, String>> =
    mapOf("error" to mapOf("code" to code, "message" to message))
