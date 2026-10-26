package com.opson.server.routes

import com.opson.server.domain.WaitlistLimiter
import com.opson.server.domain.WaitlistRepository
import com.opson.server.domain.WaitlistSignup
import com.opson.server.plugins.clientIp
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/** `POST /v1/waitlist`. All optional on the wire so a missing field is a validation answer, not a decode error. */
@Serializable
data class WaitlistRequest(
    val email: String? = null,
    val source: String? = null,
    /** The honeypot: hidden on the site, so a person leaves it empty. */
    val company: String? = null,
)

/** The site's error body, `{"error":"invalid_email"}`. Flat on purpose: docs/plans/website.md fixes this shape. */
@Serializable
data class WaitlistError(val error: String)

/**
 * The website's waitlist form, per the contract in docs/plans/website.md. No
 * auth.
 *
 *  - 204 when the address is added, when it was already on the list, and when
 *    the honeypot `company` has a value (then nothing is stored). The answer
 *    never says whether an address is on the list.
 *  - 400 `{"error":"invalid_email"}` when there is no plausible address,
 *    including a body that is not JSON or is over [MAX_BODY_BYTES].
 *  - 429 `{"error":"rate_limited"}` past [WaitlistLimiter]'s limits per IP.
 *
 * CORS here comes from `WAITLIST_ALLOWED_ORIGINS` ([allowedOrigins]) and
 * replaces the API-wide policy for this path only. The log line per call is
 * the outcome; the address never reaches a log.
 */
fun Route.waitlistRoutes(
    repository: WaitlistRepository,
    limiter: WaitlistLimiter,
    allowedOrigins: Set<String>,
) {
    val logger = LoggerFactory.getLogger("WaitlistRoutes")
    val json = Json { ignoreUnknownKeys = true }

    route("/v1/waitlist") {
        // A route-scoped CORS install shadows the one at the routing root for
        // this subtree, and adds its own OPTIONS handler for the preflight.
        install(CORS) {
            // POST needs no allowMethod: Ktor allows the simple methods already.
            allowOrigins { it in allowedOrigins }
            allowHeader(HttpHeaders.ContentType)
            // The site posts application/json, which is not a "simple" type.
            allowNonSimpleContentTypes = true
            maxAgeInSeconds = PREFLIGHT_MAX_AGE_SECONDS
        }

        post {
            if (!limiter.tryAcquire(call.clientIp())) {
                logger.info("waitlist outcome=rate_limited")
                return@post call.respond(HttpStatusCode.TooManyRequests, WaitlistError("rate_limited"))
            }

            val request = call.readWaitlistRequest(json)
            if (request != null && !request.company.isNullOrEmpty()) {
                logger.info("waitlist outcome=honeypot")
                return@post call.respond(HttpStatusCode.NoContent)
            }
            val email = WaitlistSignup.normalizeEmail(request?.email)
            if (request == null || email == null) {
                logger.info("waitlist outcome=invalid_email")
                return@post call.respond(HttpStatusCode.BadRequest, WaitlistError("invalid_email"))
            }

            val added = repository.add(WaitlistSignup(email, WaitlistSignup.normalizeSource(request.source)))
            logger.info("waitlist outcome={}", if (added) "added" else "duplicate")
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

/** The body as a [WaitlistRequest], or null when it is too big or not the JSON object the form sends. */
private suspend fun io.ktor.server.application.ApplicationCall.readWaitlistRequest(json: Json): WaitlistRequest? {
    request.contentLength()?.let { if (it > MAX_BODY_BYTES) return null }
    val bytes = receiveChannel().readRemaining(MAX_BODY_BYTES + 1L).readByteArray()
    if (bytes.size > MAX_BODY_BYTES) return null
    return runCatching { json.decodeFromString<WaitlistRequest>(bytes.decodeToString()) }.getOrNull()
}

/** A real form body is under 400 bytes; this leaves room and stops anything bigger before it is held. */
private const val MAX_BODY_BYTES = 4 * 1024
private const val PREFLIGHT_MAX_AGE_SECONDS = 600L
