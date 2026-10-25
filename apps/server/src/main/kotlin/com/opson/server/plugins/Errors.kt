package com.opson.server.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Every error response from this server has this shape. Defining it once means
 * the client only ever has one error envelope to deserialize and the server only
 * has one place to add fields (e.g. a trace ID later).
 *
 * ```json
 * { "error": { "code": "unknown", "message": "…" } }
 * ```
 *
 * Build one in a route with `call.respond(status, problem("code", "message"))`.
 */
@Serializable
data class ProblemResponse(
    val error: Problem,
) {
    @Serializable
    data class Problem(
        val code: String,
        val message: String,
    )
}

fun problem(code: String, message: String): ProblemResponse =
    ProblemResponse(ProblemResponse.Problem(code, message))

private val logger = LoggerFactory.getLogger("StatusPages")

fun Application.installStatusPages() {
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                problem("bad_request", cause.message ?: "Bad request"),
            )
        }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled error", cause)
            captureToSentry(cause, context = "status-pages")
            call.respond(
                HttpStatusCode.InternalServerError,
                problem("internal", cause.message ?: "unknown"),
            )
        }
    }
}
