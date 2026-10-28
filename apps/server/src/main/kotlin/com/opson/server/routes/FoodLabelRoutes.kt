package com.opson.server.routes

import com.opson.server.data.LabelCheckJson
import com.opson.server.data.LabelFactsJson
import com.opson.server.data.LabelParseCache
import com.opson.server.domain.BarcodeLinkRepository
import com.opson.server.domain.BarcodeLookup
import com.opson.server.domain.FoodLabelLimiter
import com.opson.server.domain.FoodLabelLimits
import com.opson.server.domain.FoodSubmissionRepository
import com.opson.server.domain.ImageType
import com.opson.server.domain.LabelCheckCode
import com.opson.server.domain.LabelChecks
import com.opson.server.domain.LabelFacts
import com.opson.server.domain.LabelParseResult
import com.opson.server.domain.LabelParser
import com.opson.server.domain.NewFoodSubmission
import com.opson.server.domain.NutrientKey
import com.opson.server.domain.PackPhotos
import com.opson.server.domain.ServingUnit
import com.opson.server.domain.SubmissionOrigin
import com.opson.server.domain.SubmitOutcome
import com.opson.server.plugins.BARCODE_LINK_LIMIT
import com.opson.server.plugins.FOOD_LABEL_LIMIT
import com.opson.server.plugins.SUPABASE_JWT_AUTH
import com.opson.server.plugins.problem
import com.opson.server.plugins.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.contentLength
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.Base64
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
data class LabelParseRequest(val front: String? = null, val label: String? = null)

/**
 * What the model read, in the catalog's units, with how sure it was of each
 * field and the checks it failed. [parseId] goes back with the submission so
 * review sees the server's copy of the reading.
 */
@Serializable
data class LabelParseResponse(
    val parseId: String,
    val model: String,
    /** False when the label photo shows no nutrition panel. */
    val readable: Boolean,
    val food: LabelFactsJson,
    val confidence: Map<String, Double>,
    val checks: List<LabelCheckJson>,
)

@Serializable
data class FoodSubmissionRequest(
    val id: String? = null,
    val parseId: String? = null,
    /** `barcode` or `search`: where the person started. */
    val origin: String? = null,
    /** The code the scanner read. Wins over a code typed into the form. */
    val barcode: String? = null,
    val front: String? = null,
    val label: String? = null,
    val food: LabelFactsJson? = null,
)

@Serializable
data class FoodSubmissionResponse(
    val submissionId: String,
    val foodId: String,
    val status: String,
    val checks: List<LabelCheckJson>,
)

@Serializable
data class BarcodeLinkRequest(val barcode: String? = null, val foodId: String? = null)

/**
 * Adding a food from photos of its pack (0.2, behind `release.submitFood`):
 *
 *  - `POST /v1/foods/labels/parse {front, label}` reads both photos with the
 *    model ([LabelParser]) and answers what the label says, field by field,
 *    with the model's confidence and the checks the reading fails. Stores
 *    nothing but the numbers it read, for an hour ([LabelParseCache]).
 *    400 `invalid_image`, 413 `image_too_large`, 429 `rate_limited`,
 *    503 `label_parse_unavailable` (no key), 502 `label_parse_failed`.
 *  - `POST /v1/foods/submissions {id, parseId?, origin, barcode?, front, label, food}`
 *    stores the food as the person corrected it, pending review, with both
 *    photos, and runs the checks again. 201, or 200 for a repeat of the same
 *    id. 400 `invalid_id`, `invalid_origin`, `invalid_barcode`,
 *    `invalid_food`, `invalid_image`; 409 `id_conflict`; 413; 429.
 *  - `POST /v1/foods/barcode-links {barcode, foodId}` counts a barcode the
 *    person missed and then found by searching. 204. No account is stored.
 *
 * Per user, [FoodLabelLimiter] caps reads and submissions; per IP, the
 * [FOOD_LABEL_LIMIT] bucket stops a flood of large bodies before any is read.
 * The log line per call is the outcome, counts and latency: never a name, a
 * number or anything from a photo.
 */
@OptIn(ExperimentalTime::class)
@Suppress("LongParameterList")
fun Route.foodLabelRoutes(
    parser: LabelParser,
    submissions: FoodSubmissionRepository,
    links: BarcodeLinkRepository,
    cache: LabelParseCache,
    limiter: FoodLabelLimiter,
    clock: Clock,
) {
    val logger = LoggerFactory.getLogger("FoodLabelRoutes")
    val json = Json { ignoreUnknownKeys = true }

    authenticate(SUPABASE_JWT_AUTH) {
        rateLimit(RateLimitName(FOOD_LABEL_LIMIT)) {
            post("/v1/foods/labels/parse") {
                val started = clock.now()
                val userId = call.userId()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, problem("unauthorized", "Sign in again"))
                val request = when (val read = call.readBounded(json, LabelParseRequest.serializer())) {
                    is Bounded.Rejected -> return@post call.respond(read.status, problem(read.code, read.message))
                    is Bounded.Ok -> read.value
                }
                val photos = when (val decoded = decodePhotos(request.front, request.label)) {
                    is Decoded.Rejected -> return@post call.respond(decoded.status, problem(decoded.code, decoded.message))
                    is Decoded.Ok -> decoded.photos
                }
                if (!limiter.tryParse(userId)) {
                    logger.info("label parse outcome=rate_limited")
                    return@post call.respond(HttpStatusCode.TooManyRequests, problem("rate_limited", "That's a lot of labels for now. Try again later."))
                }
                val result = parser.parse(photos)
                val ms = (clock.now() - started).inWholeMilliseconds
                when (result) {
                    is LabelParseResult.Read -> {
                        val checks = result.reading.checks
                        logger.info("label parse outcome=ok readable={} checks={} latency_ms={}", result.reading.readable, checks.size, ms)
                        call.respond(
                            LabelParseResponse(
                                parseId = cache.put(userId, result.reading, result.model).toString(),
                                model = result.model,
                                readable = result.reading.readable,
                                food = LabelFactsJson.of(result.reading.facts),
                                confidence = result.reading.confidence,
                                checks = checks.map(LabelCheckJson::of),
                            ),
                        )
                    }
                    LabelParseResult.NotConfigured -> {
                        logger.info("label parse outcome=unavailable latency_ms={}", ms)
                        call.respond(HttpStatusCode.ServiceUnavailable, problem("label_parse_unavailable", "Reading labels isn't available right now."))
                    }
                    is LabelParseResult.Failed -> {
                        logger.warn("label parse outcome=failed reason={} latency_ms={}", result.reason, ms)
                        call.respond(HttpStatusCode.BadGateway, problem("label_parse_failed", "Couldn't read that label. Try again in a moment."))
                    }
                }
            }

            post("/v1/foods/submissions") {
                val userId = call.userId()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, problem("unauthorized", "Sign in again"))
                val request = when (val read = call.readBounded(json, FoodSubmissionRequest.serializer())) {
                    is Bounded.Rejected -> return@post call.respond(read.status, problem(read.code, read.message))
                    is Bounded.Ok -> read.value
                }
                val id = request.id?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_id", "id must be a UUID"))
                val origin = SubmissionOrigin.fromWire(request.origin)
                    ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_origin", "origin is barcode or search"))
                val dto = request.food
                    ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_food", "food is required"))
                val facts = when (val validated = validate(dto)) {
                    is Validated.Rejected -> return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_food", validated.message))
                    is Validated.Ok -> validated.facts
                }
                val barcode = (request.barcode?.trim()?.ifEmpty { null } ?: facts.barcode)
                if (barcode != null && !BarcodeLookup.isValidBarcode(barcode)) {
                    return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_barcode", "A barcode is 8 to 14 digits"))
                }
                val photos = when (val decoded = decodePhotos(request.front, request.label)) {
                    is Decoded.Rejected -> return@post call.respond(decoded.status, problem(decoded.code, decoded.message))
                    is Decoded.Ok -> decoded.photos
                }
                if (!limiter.trySubmit(userId)) {
                    logger.info("food submission outcome=rate_limited")
                    return@post call.respond(HttpStatusCode.TooManyRequests, problem("rate_limited", "That's a lot of foods for now. Try again later."))
                }
                val parsed = request.parseId?.let { runCatching { UUID.fromString(it) }.getOrNull() }?.let { cache.get(it, userId) }
                val withBarcode = facts.copy(barcode = barcode)
                val checks = LabelChecks.check(withBarcode)
                val outcome = submissions.submit(
                    NewFoodSubmission(
                        id = id,
                        userId = userId,
                        barcode = barcode,
                        origin = origin,
                        facts = withBarcode,
                        checks = checks,
                        acknowledged = dto.acknowledged.mapNotNull(LabelCheckCode::fromWire).distinct(),
                        parsed = parsed?.reading,
                        model = parsed?.model,
                        photos = photos,
                    ),
                )
                when (outcome) {
                    is SubmitOutcome.Created -> {
                        logger.info("food submission outcome=created origin={} checks={} parsed={}", origin.wire, checks.size, parsed != null)
                        call.respond(HttpStatusCode.Created, outcome.submission.toResponse())
                    }
                    is SubmitOutcome.Existing -> {
                        logger.info("food submission outcome=repeat")
                        call.respond(outcome.submission.toResponse())
                    }
                    SubmitOutcome.Conflict -> call.respond(HttpStatusCode.Conflict, problem("id_conflict", "That id is taken"))
                }
            }
        }

        rateLimit(RateLimitName(BARCODE_LINK_LIMIT)) {
            post("/v1/foods/barcode-links") {
                val request = call.receive<BarcodeLinkRequest>()
                val code = request.barcode?.trim().orEmpty()
                if (!BarcodeLookup.isValidBarcode(code)) {
                    return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_barcode", "A barcode is 8 to 14 digits"))
                }
                val foodId = request.foodId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_id", "foodId must be a UUID"))
                val counted = links.record(code, foodId)
                logger.info("barcode link outcome={}", if (counted) "counted" else "ignored")
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun com.opson.server.domain.FoodSubmission.toResponse() = FoodSubmissionResponse(
    submissionId = id.toString(),
    foodId = foodId.toString(),
    status = status.wire,
    checks = checks.map(LabelCheckJson::of),
)

private sealed interface Bounded<out T> {
    class Ok<T>(val value: T) : Bounded<T>
    class Rejected(val status: HttpStatusCode, val code: String, val message: String) : Bounded<Nothing>
}

/** Reads at most [FoodLabelLimits.MAX_BODY_BYTES], whatever `Content-Length` claims, then decodes. */
private suspend fun <T> ApplicationCall.readBounded(json: Json, serializer: KSerializer<T>): Bounded<T> {
    val tooLarge = Bounded.Rejected(HttpStatusCode.PayloadTooLarge, "image_too_large", "The photos are too large")
    if ((request.contentLength() ?: 0L) > FoodLabelLimits.MAX_BODY_BYTES) return tooLarge
    val body = receiveChannel().readRemaining(FoodLabelLimits.MAX_BODY_BYTES + 1).readByteArray()
    if (body.size > FoodLabelLimits.MAX_BODY_BYTES) return tooLarge
    return try {
        Bounded.Ok(json.decodeFromString(serializer, body.decodeToString()))
    } catch (_: SerializationException) {
        Bounded.Rejected(HttpStatusCode.BadRequest, "invalid_body", "The request could not be read")
    } catch (_: IllegalArgumentException) {
        Bounded.Rejected(HttpStatusCode.BadRequest, "invalid_body", "The request could not be read")
    }
}

private sealed interface Decoded {
    class Ok(val photos: PackPhotos) : Decoded
    class Rejected(val status: HttpStatusCode, val code: String, val message: String) : Decoded
}

private fun decodePhotos(front: String?, label: String?): Decoded {
    val invalid = Decoded.Rejected(HttpStatusCode.BadRequest, "invalid_image", "front and label must be base64 JPEG, PNG or WebP")
    val tooLarge = Decoded.Rejected(HttpStatusCode.PayloadTooLarge, "image_too_large", "The photos are too large")
    fun decode(encoded: String?): Pair<ByteArray, ImageType>? {
        val text = encoded?.takeIf { it.isNotBlank() } ?: return null
        val bytes = try {
            Base64.getMimeDecoder().decode(text)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return ImageType.sniff(bytes)?.let { bytes to it }
    }
    val frontImage = decode(front) ?: return invalid
    val labelImage = decode(label) ?: return invalid
    if (frontImage.first.size > FoodLabelLimits.MAX_IMAGE_BYTES || labelImage.first.size > FoodLabelLimits.MAX_IMAGE_BYTES) return tooLarge
    return Decoded.Ok(PackPhotos(frontImage.first, frontImage.second, labelImage.first, labelImage.second))
}

internal sealed interface Validated {
    class Ok(val facts: LabelFacts) : Validated
    class Rejected(val message: String) : Validated
}

/**
 * The food as the person sent it, bounded: a name, sizes in range, amounts
 * that are numbers a label could print. Messages name the field and never
 * echo the value. Missing nutrients are allowed (a person whose mode hides
 * numbers may send what the model read without seeing it); approval is what
 * requires them.
 */
internal fun validate(dto: LabelFactsJson): Validated {
    val name = dto.name?.trim()?.takeIf { it.isNotEmpty() } ?: return Validated.Rejected("name is required")
    if (name.length > FoodLabelLimits.MAX_NAME) return Validated.Rejected("name is too long")
    val brand = dto.brand?.trim()?.ifEmpty { null }
    if (brand != null && brand.length > FoodLabelLimits.MAX_BRAND) return Validated.Rejected("brand is too long")
    val barcode = dto.barcode?.trim()?.ifEmpty { null }
    if (barcode != null && !BarcodeLookup.isValidBarcode(barcode)) return Validated.Rejected("barcode must be 8 to 14 digits")
    val size = dto.servingSize
    if (size != null && (!size.isFinite() || size <= 0 || size > FoodLabelLimits.MAX_SERVING_SIZE)) {
        return Validated.Rejected("servingSize is out of range")
    }
    val unit = dto.servingUnit?.let { ServingUnit.fromWire(it) ?: return Validated.Rejected("servingUnit is g or ml") }
    if (size != null && unit == null) return Validated.Rejected("servingUnit is required with servingSize")
    val servingLabel = dto.servingLabel?.trim()?.ifEmpty { null }
    if (servingLabel != null && servingLabel.length > FoodLabelLimits.MAX_SERVING_LABEL) return Validated.Rejected("servingLabel is too long")
    val perContainer = dto.servingsPerContainer
    if (perContainer != null && (!perContainer.isFinite() || perContainer <= 0 || perContainer > FoodLabelLimits.MAX_SERVINGS_PER_CONTAINER)) {
        return Validated.Rejected("servingsPerContainer is out of range")
    }
    if (dto.acknowledged.size > FoodLabelLimits.MAX_ACKNOWLEDGED) return Validated.Rejected("acknowledged has too many entries")
    val columns = listOf(dto.perServing, dto.per100)
    for (column in columns) {
        for ((key, value) in column) {
            if (NutrientKey.fromWire(key) == null) continue
            if (!value.isFinite() || value < 0 || value > FoodLabelLimits.MAX_AMOUNT) return Validated.Rejected("$key is out of range")
        }
    }
    dto.per100[NutrientKey.Energy.wire]?.let { if (it > FoodLabelLimits.MAX_KCAL_PER_100) return Validated.Rejected("energy_kcal is out of range") }
    val facts = dto.copy(name = name, brand = brand, barcode = barcode, servingLabel = servingLabel).toFacts()
    return Validated.Ok(facts)
}
