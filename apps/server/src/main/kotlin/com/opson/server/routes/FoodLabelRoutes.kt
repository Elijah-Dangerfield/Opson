package com.opson.server.routes

import com.opson.server.data.CaptureJson
import com.opson.server.data.LabelCheckJson
import com.opson.server.domain.BuilderRules
import com.opson.server.domain.ContributionRouter
import com.opson.server.domain.ExtraPhoto
import com.opson.server.domain.LabelCapture
import com.opson.server.domain.Permission
import com.opson.server.domain.Permissions
import com.opson.server.data.LabelFactsJson
import com.opson.server.data.LabelParseCache
import com.opson.server.domain.BarcodeLinkRepository
import com.opson.server.domain.BarcodeLookup
import com.opson.server.domain.ContributionGate
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
import com.opson.server.domain.OpenContributionGate
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
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.coroutines.CancellationException
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
    /** The submitter's own copy, a custom food the app saved first. Optional; an app before 0.2.1 leaves it out. */
    val customFoodId: String? = null,
    /** Builder mode (0.3): a photo of the ingredients list, base64 like the others. Optional. */
    val ingredients: String? = null,
    /** Builder mode (0.3): what the phone read off the label before the person edited it. Optional. */
    val capture: CaptureJson? = null,
)

/**
 * [lane] says where the submission went (`review`, `auto`, `owner`), so the app can thank an
 * owner's or an auto-approved food at once. Older apps ignore it.
 */
@Serializable
data class FoodSubmissionResponse(
    val submissionId: String,
    val foodId: String,
    val status: String,
    val checks: List<LabelCheckJson>,
    val lane: String? = null,
)

/**
 * One of the caller's own submissions, for `GET /v1/foods/submissions`. [status] is `pending`,
 * `approved` or `rejected`; [customFoodId] is the app's own copy when it said which.
 */
@Serializable
data class OwnSubmissionResponse(
    val id: String,
    val foodId: String,
    val status: String,
    val createdAt: String,
    val customFoodId: String? = null,
)

@Serializable
data class OwnSubmissionsResponse(val submissions: List<OwnSubmissionResponse>)

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
 *    `customFoodId` (optional) names the submitter's own copy of the food.
 *  - `GET /v1/foods/submissions` lists the caller's own submissions, newest
 *    first, with where review stands, so the app can mark its copy of each
 *    food unverified until a reviewer approves it. Nobody else's.
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
    contributionGate: ContributionGate = OpenContributionGate,
    router: ContributionRouter? = null,
    permissions: Permissions? = null,
) {
    val logger = LoggerFactory.getLogger("FoodLabelRoutes")
    val json = Json { ignoreUnknownKeys = true }

    authenticate(SUPABASE_JWT_AUTH) {
        get("/v1/foods/submissions") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, problem("unauthorized", "Sign in again"))
            val mine = submissions.mine(userId, OWN_SUBMISSIONS_LIMIT)
            call.respond(OwnSubmissionsResponse(mine.map { it.toOwnResponse() }))
        }

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
                if (call.refuseIfRestricted(contributionGate, userId, id.toString(), logger, "food submission")) return@post
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
                val ingredients = when (val extra = decodeExtra(request.ingredients)) {
                    is ExtraDecoded.Rejected -> return@post call.respond(extra.status, problem(extra.code, extra.message))
                    is ExtraDecoded.Ok -> extra.photo
                }
                val capture = when (val read = request.capture?.let(::validateCapture)) {
                    is CaptureValidated.Rejected -> return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_capture", read.message))
                    is CaptureValidated.Ok -> read.capture
                    null -> null
                }
                val daily = if (permissions?.can(userId, Permission.FoodContribute) == true) {
                    BuilderRules.BUILDER_DAILY_SUBMISSIONS
                } else {
                    FoodLabelLimiter.DAILY_SUBMISSIONS
                }
                if (!limiter.trySubmit(userId, daily)) {
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
                        // A malformed id is dropped rather than refused: the food matters more than the mark.
                        customFoodId = request.customFoodId?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                        ingredients = ingredients,
                        capture = capture,
                    ),
                )
                when (outcome) {
                    is SubmitOutcome.Created -> {
                        // The submission is stored whatever routing does: a failure here leaves it pending for a person.
                        val routed = try {
                            router?.route(outcome.submission, photos)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logger.warn("food submission routing failed reason={}", e::class.simpleName)
                            null
                        }
                        val stored = routed?.let { submissions.get(id) } ?: outcome.submission
                        logger.info(
                            "food submission outcome=created origin={} checks={} parsed={} capture={} lane={} would_auto={}",
                            origin.wire, checks.size, parsed != null, capture != null, routed?.lane?.wire ?: "review", routed?.wouldAutoApprove == true,
                        )
                        call.respond(HttpStatusCode.Created, stored.toResponse())
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
    lane = lane?.wire,
)

private sealed interface ExtraDecoded {
    class Ok(val photo: ExtraPhoto?) : ExtraDecoded
    class Rejected(val status: HttpStatusCode, val code: String, val message: String) : ExtraDecoded
}

private fun decodeExtra(encoded: String?): ExtraDecoded {
    val text = encoded?.takeIf { it.isNotBlank() } ?: return ExtraDecoded.Ok(null)
    val invalid = ExtraDecoded.Rejected(HttpStatusCode.BadRequest, "invalid_image", "ingredients must be base64 JPEG, PNG or WebP")
    val bytes = try {
        Base64.getMimeDecoder().decode(text)
    } catch (_: IllegalArgumentException) {
        return invalid
    }
    val type = ImageType.sniff(bytes) ?: return invalid
    if (bytes.size > FoodLabelLimits.MAX_IMAGE_BYTES) return ExtraDecoded.Rejected(HttpStatusCode.PayloadTooLarge, "image_too_large", "The photos are too large")
    return ExtraDecoded.Ok(ExtraPhoto(bytes, type))
}

internal sealed interface CaptureValidated {
    class Ok(val capture: LabelCapture) : CaptureValidated
    class Rejected(val message: String) : CaptureValidated
}

/**
 * The phone's reading, bounded: a panel's worth of lines, each a line's worth of text, and a
 * draft that passes the same checks as the food. A capture is evidence for routing, so a
 * malformed one is refused rather than quietly dropped.
 */
internal fun validateCapture(dto: CaptureJson): CaptureValidated {
    if (dto.lines.size > FoodLabelLimits.MAX_CAPTURE_LINES) return CaptureValidated.Rejected("capture has too many lines")
    if (dto.lines.any { it.text.length > FoodLabelLimits.MAX_CAPTURE_LINE }) return CaptureValidated.Rejected("a capture line is too long")
    if (dto.lines.any { !it.confidence.isFinite() }) return CaptureValidated.Rejected("a capture confidence is not a number")
    if ((dto.engine?.length ?: 0) > FoodLabelLimits.MAX_CAPTURE_ENGINE) return CaptureValidated.Rejected("engine is too long")
    if (dto.score != null && (!dto.score.isFinite() || dto.score !in 0.0..1.0)) return CaptureValidated.Rejected("score is out of range")
    if ((dto.signals?.toString()?.length ?: 0) > FoodLabelLimits.MAX_CAPTURE_SIGNALS) return CaptureValidated.Rejected("signals are too large")
    val draft = dto.draft?.let { draft ->
        // The draft has no name (a label panel shows none), so it is checked with a stand-in one.
        when (val checked = validate(draft.copy(name = draft.name ?: "draft"))) {
            is Validated.Rejected -> return CaptureValidated.Rejected("draft: ${checked.message}")
            is Validated.Ok -> checked.facts.copy(name = draft.name)
        }
    }
    return CaptureValidated.Ok(dto.copy(draft = null).toCapture().copy(draft = draft))
}

@OptIn(ExperimentalTime::class)
private fun com.opson.server.domain.FoodSubmission.toOwnResponse() = OwnSubmissionResponse(
    id = id.toString(),
    foodId = foodId.toString(),
    status = status.wire,
    createdAt = createdAt.toString(),
    customFoodId = customFoodId?.toString(),
)

/** More than anyone submits in weeks at the daily cap; the app only needs the recent ones to mark. */
private const val OWN_SUBMISSIONS_LIMIT = 500

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
