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
import java.sql.SQLException

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
            val reported = cause.withoutSqlDetail()
            logger.error("Unhandled error", reported)
            captureToSentry(reported, context = "status-pages")
            // Never the exception's message: it can quote SQL, row values or a stack detail.
            call.respond(HttpStatusCode.InternalServerError, problem("internal", "Something went wrong"))
        }
    }
}

/**
 * [this], or for a database error a stand-in that carries only the SQLSTATE
 * and the stack. A JDBC or Exposed exception's message and `toString` quote
 * the failed statement with its bound values, which here are someone's diary.
 */
internal fun Throwable.withoutSqlDetail(): Throwable {
    val sql = generateSequence(this) { it.cause }.filterIsInstance<SQLException>().firstOrNull() ?: return this
    val state = generateSequence(this) { it.cause }.filterIsInstance<SQLException>().firstNotNullOfOrNull { it.sqlState }
    return RedactedSqlException("${sql::class.java.simpleName} sqlState=${state ?: "unknown"}").also {
        it.stackTrace = stackTrace
    }
}

internal class RedactedSqlException(message: String) : RuntimeException(message)
