package com.opson.server.routes

import com.opson.server.data.PhotoDescriber
import com.opson.server.domain.Entitlement
import com.opson.server.domain.EntitlementService
import com.opson.server.domain.ImageType
import com.opson.server.domain.PhotoDescribeResult
import com.opson.server.domain.PhotoEstimateLimiter
import com.opson.server.domain.PhotoLimits
import com.opson.server.http.clientContext
import com.opson.server.plugins.PHOTO_ESTIMATE_LIMIT
import com.opson.server.plugins.SUPABASE_JWT_AUTH
import com.opson.server.plugins.problem
import com.opson.server.plugins.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * `POST /v1/describe/photo {image, note?}`: a meal photo to the same rows
 * `POST /v1/describe` returns ([DescribeResponse]). JWT required.
 *
 *  - 400 `invalid_body` (not the JSON object above),
 *    400 `invalid_image` (not base64, or not JPEG, PNG or WebP by its bytes),
 *    400 `invalid_note` (over [PhotoLimits.MAX_NOTE] characters),
 *    413 `image_too_large` (body or decoded image over the limits; the body
 *    size is checked before it is read, and the read itself stops at the limit),
 *  - 403 `entitlement_required` when the caller lacks [Entitlement.PhotoEstimate]. Everyone
 *    has it today (`entitlements.everyone`, V28). Checked before the body is read. Builds
 *    from before entitlements show their generic "couldn't read that photo" for it,
 *    newer ones the calm "not available" state,
 *  - 429 `rate_limited` from [PhotoEstimateLimiter], per user,
 *  - 503 `photo_estimate_unavailable` when the server has no provider key,
 *  - 502 `photo_estimate_failed` when the provider failed or refused.
 *
 * The image is held for the length of the request and never stored. The one
 * log line per call is the outcome, the item count and the latency: no note,
 * no food names, no amounts.
 */
@OptIn(ExperimentalTime::class)
fun Route.photoRoutes(
    describer: PhotoDescriber,
    limiter: PhotoEstimateLimiter,
    entitlements: EntitlementService,
    clock: Clock,
) {
    val logger = LoggerFactory.getLogger("PhotoRoutes")
    val json = Json { ignoreUnknownKeys = true }

    authenticate(SUPABASE_JWT_AUTH) {
        rateLimit(RateLimitName(PHOTO_ESTIMATE_LIMIT)) {
            post("/v1/describe/photo") {
                val started = clock.now()
                val userId = call.userId()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, problem("unauthorized", "Sign in again"))

                if (!entitlements.has(userId, Entitlement.PhotoEstimate, call.clientContext())) {
                    logger.info("photo estimate outcome=not_entitled items=0 latency_ms=0")
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        problem("entitlement_required", "Photo estimates aren't part of your plan."),
                    )
                }

                val request = when (val read = call.readPhotoRequest(json)) {
                    is PhotoRead.Rejected -> {
                        logger.info("photo estimate outcome={} items=0 latency_ms=0", read.code)
                        return@post call.respond(read.status, problem(read.code, read.message))
                    }
                    is PhotoRead.Ok -> read
                }

                // Fair use, Pro included: every estimate is a paid model call.
                if (!limiter.tryAcquire(userId)) {
                    logger.info("photo estimate outcome=rate_limited items=0 latency_ms=0")
                    return@post call.respond(
                        HttpStatusCode.TooManyRequests,
                        problem("rate_limited", "That's a lot of photos for now. Try again later."),
                    )
                }

                val result = describer.describe(request.image, request.type, request.note)
                val ms = (clock.now() - started).inWholeMilliseconds
                when (result) {
                    is PhotoDescribeResult.Described -> {
                        logger.info("photo estimate outcome=ok items={} matched={} latency_ms={}", result.items.size, result.items.count { it.foodId != null }, ms)
                        call.respond(DescribeResponse(items = result.items.map { it.toDto() }))
                    }
                    PhotoDescribeResult.NotConfigured -> {
                        logger.info("photo estimate outcome=unavailable items=0 latency_ms={}", ms)
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            problem("photo_estimate_unavailable", "Photo estimates aren't available right now."),
                        )
                    }
                    is PhotoDescribeResult.Failed -> {
                        logger.warn("photo estimate outcome=failed reason={} items=0 latency_ms={}", result.reason, ms)
                        call.respond(
                            HttpStatusCode.BadGateway,
                            problem("photo_estimate_failed", "Couldn't read that photo. Try again in a moment."),
                        )
                    }
                }
            }
        }
    }
}

private sealed interface PhotoRead {
    class Ok(val image: ByteArray, val type: ImageType, val note: String?) : PhotoRead
    class Rejected(val status: HttpStatusCode, val code: String, val message: String) : PhotoRead
}

private val tooLarge = PhotoRead.Rejected(HttpStatusCode.PayloadTooLarge, "image_too_large", "The photo is too large")
private val invalidImage = PhotoRead.Rejected(HttpStatusCode.BadRequest, "invalid_image", "image must be a base64 JPEG, PNG or WebP")

/** Reads at most [PhotoLimits.MAX_BODY_BYTES], whatever `Content-Length` claims, then checks every field. */
private suspend fun ApplicationCall.readPhotoRequest(json: Json): PhotoRead {
    if ((request.contentLength() ?: 0L) > PhotoLimits.MAX_BODY_BYTES) return tooLarge
    val body = receiveChannel().readRemaining(PhotoLimits.MAX_BODY_BYTES + 1).readByteArray()
    if (body.size > PhotoLimits.MAX_BODY_BYTES) return tooLarge

    val dto = try {
        json.decodeFromString(PhotoDescribeRequest.serializer(), body.decodeToString())
    } catch (_: IllegalArgumentException) {
        // SerializationException is an IllegalArgumentException too.
        return PhotoRead.Rejected(HttpStatusCode.BadRequest, "invalid_body", "Expected {image, note}")
    }

    val note = dto.note?.trim()?.takeIf { it.isNotEmpty() }
    if (note != null && note.length > PhotoLimits.MAX_NOTE) {
        return PhotoRead.Rejected(HttpStatusCode.BadRequest, "invalid_note", "note must be at most ${PhotoLimits.MAX_NOTE} characters")
    }
    val encoded = dto.image?.takeIf { it.isNotBlank() } ?: return invalidImage
    val image = try {
        Base64.getMimeDecoder().decode(encoded)
    } catch (_: IllegalArgumentException) {
        return invalidImage
    }
    if (image.size > PhotoLimits.MAX_IMAGE_BYTES) return tooLarge
    val type = ImageType.sniff(image) ?: return invalidImage
    return PhotoRead.Ok(image, type, note)
}
