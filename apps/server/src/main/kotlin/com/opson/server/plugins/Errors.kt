package com.opson.server.plugins

import com.opson.server.db.DatabaseBusyException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.response.header
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

/** Seconds a client is asked to wait after a 503 `busy`. */
private const val BUSY_RETRY_AFTER_SECONDS = 2

fun Application.installStatusPages() {
    install(StatusPages) {
        // A body that doesn't decode (`call.receive<T>()`) is the caller's
        // fault, not a 500. The message names the target type, never the body.
        exception<BadRequestException> { call, _ ->
            call.respond(
                HttpStatusCode.BadRequest,
                problem("bad_request", "Request body is missing or malformed"),
            )
        }
        exception<UnsupportedMediaTypeException> { call, _ ->
            call.respond(
                HttpStatusCode.UnsupportedMediaType,
                problem("unsupported_media_type", "Send application/json"),
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                problem("bad_request", cause.message ?: "Bad request"),
            )
        }
        exception<DatabaseBusyException> { call, _ ->
            logger.warn("Database busy, answered 503 to {}", call.request.httpMethod.value)
            call.response.header(HttpHeaders.RetryAfter, BUSY_RETRY_AFTER_SECONDS)
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                problem("busy", "The server is busy. Try again in a moment."),
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
