package com.opson.server.routes

import com.opson.server.config.AdminConfig
import com.opson.server.data.CaptureJson
import com.opson.server.data.RoutingJson
import com.opson.server.domain.AuditState
import com.opson.server.domain.RejectCode
import com.opson.server.data.LabelCheckJson
import com.opson.server.data.LabelFactsJson
import com.opson.server.data.LabelReadingJson
import com.opson.server.domain.BarcodeLinkRepository
import com.opson.server.domain.BarcodeLinkSuggestion
import com.opson.server.domain.FoodSubmission
import com.opson.server.domain.FoodSubmissionRepository
import com.opson.server.domain.LabelChecks
import com.opson.server.domain.LinkOutcome
import com.opson.server.domain.LinkStatus
import com.opson.server.domain.PhotoKind
import com.opson.server.domain.ReviewOutcome
import com.opson.server.domain.SubmissionStatus
import com.opson.server.plugins.problem
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.cacheControl
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

@Serializable
data class FoodSubmissionSummaryResponse(
    val id: String,
    val createdAtEpochMs: Long,
    val status: String,
    val origin: String,
    val name: String? = null,
    val brand: String? = null,
    val barcode: String? = null,
    /** Codes of the checks the food fails as it stands. */
    val failedChecks: List<String>,
    val hasPhotos: Boolean,
    /** `review`, `auto` or `owner` (V30); null before routing existed. */
    val lane: String? = null,
    /** What the auto-approval lane would have done: the shadow record reviewers are measured against. */
    val wouldAutoApprove: Boolean = false,
    /** Why it is in the queue, first things first: `not_grounded:fat_g`, `cloud_differs:energy_kcal`. */
    val reasons: List<String> = emptyList(),
    val confidence: Double? = null,
    /** `pending`, `passed` or `failed` for an audited auto-approval. */
    val audit: String? = null,
)

@Serializable
data class FoodSubmissionListResponse(val total: Long, val submissions: List<FoodSubmissionSummaryResponse>)

@Serializable
data class FoodSubmissionDetailResponse(
    val summary: FoodSubmissionSummaryResponse,
    val foodId: String,
    val model: String? = null,
    /** What the model read, kept from the parse. Null when the server no longer had it. */
    val parsed: LabelReadingJson? = null,
    /** The checks on the model's reading, before the person changed anything. */
    val parsedChecks: List<LabelCheckJson> = emptyList(),
    val submitted: LabelFactsJson,
    val reviewed: LabelFactsJson? = null,
    /** The checks on the food as it stands (the reviewer's version once there is one). */
    val checks: List<LabelCheckJson>,
    val reviewedAtEpochMs: Long? = null,
    val reviewedBy: String? = null,
    val rejectReason: String? = null,
    val rejectCode: String? = null,
    /** The routing record (V30): lane, reasons, confidence, trust, the cloud read's verdict, grounding. */
    val routing: RoutingJson? = null,
    /** What the phone read off the label, line by line, and its draft. */
    val capture: CaptureJson? = null,
    val auditNote: String? = null,
)

@Serializable
data class ReviewEditRequest(val food: LabelFactsJson? = null)

/** [code] is one of [RejectCode]'s wires; optional, so an older console still works. */
@Serializable
data class ReviewRejectRequest(val reason: String? = null, val code: String? = null)

@Serializable
data class AuditRequest(val passed: Boolean? = null, val note: String? = null)

@Serializable
data class BarcodeLinkResponse(
    val gtin: String,
    val barcode: String,
    val foodId: String,
    val foodName: String,
    val foodBrand: String? = null,
    val foodBarcode: String? = null,
    val pairings: Int,
    val firstAtEpochMs: Long,
    val lastAtEpochMs: Long,
    val status: String,
)

@Serializable
data class BarcodeLinkListResponse(val links: List<BarcodeLinkResponse>)

@Serializable
data class BarcodeLinkDecisionRequest(val gtin: String? = null, val foodId: String? = null)

/**
 * The review queue, behind the `X-Admin-Token` gate like the rest of `/v1/admin`.
 * The reviewer is `X-Admin-Actor`, as in the config audit log.
 *
 *  - `GET  /v1/admin/food-submissions?status=pending&limit=50` the queue (pending oldest first; decided newest first)
 *  - `GET  /v1/admin/food-submissions/{id}`                    one submission: the model's reading, what was sent, edits, checks
 *  - `GET  /v1/admin/food-submissions/{id}/photos/{kind}`      `front` or `label`, the bytes, `no-store`
 *  - `PUT  /v1/admin/food-submissions/{id} {food}`             a reviewer's corrections, checks re-run
 *  - `POST /v1/admin/food-submissions/{id}/approve {food?}`    into the catalog for everyone; 409 `incomplete`, `barcode_taken`
 *  - `POST /v1/admin/food-submissions/{id}/reject {reason}`    stays the submitter's alone
 *  - `GET  /v1/admin/barcode-links?status=pending&limit=50`    barcodes suggested by a miss then a search
 *  - `POST /v1/admin/barcode-links/attach {gtin, foodId}`      gives the food the barcode; 409 `barcode_taken`, `food_has_barcode`
 *  - `POST /v1/admin/barcode-links/dismiss {gtin, foodId}`
 *
 * Photos are only ever served here, never by a URL anyone else could open.
 * Each list call also deletes the photos of submissions decided more than
 * [PHOTO_RETENTION_DAYS] days ago (the privacy policy's promise).
 */
@OptIn(ExperimentalTime::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun Route.foodReviewAdminRoutes(
    config: AdminConfig,
    submissions: FoodSubmissionRepository,
    links: BarcodeLinkRepository,
    clock: Clock,
) {
    val logger = LoggerFactory.getLogger("FoodReviewAdminRoutes")

    route("/v1/admin/food-submissions") {
        get {
            if (!call.requireAdmin(config)) return@get
            val status = call.request.queryParameters["status"]?.let { SubmissionStatus.fromWire(it) } ?: SubmissionStatus.Pending
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, MAX_LIMIT) ?: DEFAULT_LIMIT
            val now = clock.now()
            val purged = submissions.purgeDecidedPhotos(now - PHOTO_RETENTION_DAYS.days, retainedBefore = now - RETAINED_PHOTO_DAYS.days)
            if (purged > 0) logger.info("food submission photos purged count={}", purged)
            if (call.request.queryParameters["audit"] == AuditState.Pending.wire) {
                return@get call.respond(
                    FoodSubmissionListResponse(total = submissions.auditCount(), submissions = submissions.audits(limit).map { it.toSummary() }),
                )
            }
            call.respond(
                FoodSubmissionListResponse(
                    total = submissions.count(status),
                    submissions = submissions.list(status, limit).map { it.toSummary() },
                ),
            )
        }

        get("/{id}") {
            if (!call.requireAdmin(config)) return@get
            val id = call.submissionId() ?: return@get
            val submission = submissions.get(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, problem("not_found", "No such submission."))
            call.respond(submission.toDetail())
        }

        get("/{id}/photos/{kind}") {
            if (!call.requireAdmin(config)) return@get
            val id = call.submissionId() ?: return@get
            val kind = PhotoKind.fromWire(call.parameters["kind"])
                ?: return@get call.respond(HttpStatusCode.BadRequest, problem("invalid_kind", "front, label or ingredients"))
            val photo = submissions.photo(id, kind)
                ?: return@get call.respond(HttpStatusCode.NotFound, problem("not_found", "No such photo."))
            call.response.cacheControl(CacheControl.NoStore(CacheControl.Visibility.Private))
            call.response.header(HttpHeaders.ContentDisposition, "inline")
            call.respondBytes(photo.bytes, ContentType.parse(photo.mediaType))
        }

        put("/{id}") {
            if (!call.requireAdmin(config)) return@put
            val id = call.submissionId() ?: return@put
            val dto = call.receive<ReviewEditRequest>().food
                ?: return@put call.respond(HttpStatusCode.BadRequest, problem("invalid_food", "food is required"))
            val facts = when (val validated = validate(dto)) {
                is Validated.Rejected -> return@put call.respond(HttpStatusCode.BadRequest, problem("invalid_food", validated.message))
                is Validated.Ok -> validated.facts
            }
            call.respondReview(submissions.edit(id, facts, call.actor()))
        }

        post("/{id}/approve") {
            if (!call.requireAdmin(config)) return@post
            val id = call.submissionId() ?: return@post
            val facts = call.receive<ReviewEditRequest>().food?.let { dto ->
                when (val validated = validate(dto)) {
                    is Validated.Rejected -> return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_food", validated.message))
                    is Validated.Ok -> validated.facts
                }
            }
            val outcome = submissions.approve(id, facts, call.actor())
            if (outcome is ReviewOutcome.Done) logger.info("food submission approved")
            call.respondReview(outcome)
        }

        post("/{id}/reject") {
            if (!call.requireAdmin(config)) return@post
            val id = call.submissionId() ?: return@post
            val request = call.receive<ReviewRejectRequest>()
            val reason = request.reason?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_reason", "Say why."))
            if (reason.length > MAX_REASON) {
                return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_reason", "At most $MAX_REASON characters."))
            }
            val code = request.code?.let {
                RejectCode.fromWire(it) ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_code", "Unknown reject code."))
            }
            val outcome = submissions.reject(id, reason, call.actor(), code)
            if (outcome is ReviewOutcome.Done) logger.info("food submission rejected code={}", code?.wire ?: "none")
            call.respondReview(outcome)
        }

        post("/{id}/audit") {
            if (!call.requireAdmin(config)) return@post
            val id = call.submissionId() ?: return@post
            val request = call.receive<AuditRequest>()
            val passed = request.passed
                ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_audit", "passed is required"))
            val note = request.note?.trim()?.ifEmpty { null }
            if (!passed && note == null) return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_reason", "Say what was wrong."))
            if ((note?.length ?: 0) > MAX_REASON) {
                return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_reason", "At most $MAX_REASON characters."))
            }
            val outcome = submissions.closeAudit(id, passed, note, call.actor())
            if (outcome is ReviewOutcome.Done) logger.info("food submission audit passed={}", passed)
            call.respondReview(outcome)
        }
    }

    route("/v1/admin/barcode-links") {
        get {
            if (!call.requireAdmin(config)) return@get
            val status = call.request.queryParameters["status"]?.let { LinkStatus.fromWire(it) } ?: LinkStatus.Pending
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, MAX_LIMIT) ?: DEFAULT_LIMIT
            call.respond(BarcodeLinkListResponse(links.list(status, limit).map { it.toResponse() }))
        }

        post("/attach") {
            if (!call.requireAdmin(config)) return@post
            val (gtin, foodId) = call.linkKey() ?: return@post
            call.respondLink(links.attach(gtin, foodId))
        }

        post("/dismiss") {
            if (!call.requireAdmin(config)) return@post
            val (gtin, foodId) = call.linkKey() ?: return@post
            call.respondLink(links.dismiss(gtin, foodId))
        }
    }
}

private suspend fun ApplicationCall.submissionId(): UUID? {
    val id = parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (id == null) respond(HttpStatusCode.BadRequest, problem("invalid_id", "Not a submission id."))
    return id
}

private suspend fun ApplicationCall.linkKey(): Pair<String, UUID>? {
    val request = receive<BarcodeLinkDecisionRequest>()
    val gtin = request.gtin?.takeIf { it.length == GTIN_LENGTH && it.all(Char::isDigit) }
    val foodId = request.foodId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (gtin == null || foodId == null) {
        respond(HttpStatusCode.BadRequest, problem("invalid_link", "gtin (14 digits) and foodId are required"))
        return null
    }
    return gtin to foodId
}

/** Who is reviewing, from the console's `X-Admin-Actor`. */
private fun ApplicationCall.actor(): String = request.header("X-Admin-Actor")?.trim()?.take(MAX_ACTOR)?.ifEmpty { null } ?: "admin"

private suspend fun ApplicationCall.respondReview(outcome: ReviewOutcome) = when (outcome) {
    is ReviewOutcome.Done -> respond(outcome.submission.toDetail())
    ReviewOutcome.NotFound -> respond(HttpStatusCode.NotFound, problem("not_found", "No such submission."))
    ReviewOutcome.AlreadyDecided -> respond(HttpStatusCode.Conflict, problem("already_decided", "This one has been decided."))
    is ReviewOutcome.Incomplete ->
        respond(HttpStatusCode.Conflict, problem("incomplete", "Fill in ${outcome.fields.joinToString(", ")} first."))
    is ReviewOutcome.BarcodeTaken ->
        respond(HttpStatusCode.Conflict, problem("barcode_taken", "Food ${outcome.foodId} already has this barcode."))
}

private suspend fun ApplicationCall.respondLink(outcome: LinkOutcome) = when (outcome) {
    LinkOutcome.Done -> respond(HttpStatusCode.NoContent)
    LinkOutcome.NotFound -> respond(HttpStatusCode.NotFound, problem("not_found", "No pending suggestion like that."))
    is LinkOutcome.BarcodeTaken ->
        respond(HttpStatusCode.Conflict, problem("barcode_taken", "Food ${outcome.foodId} already has this barcode."))
    LinkOutcome.FoodHasBarcode -> respond(HttpStatusCode.Conflict, problem("food_has_barcode", "That food already has a barcode."))
}

@OptIn(ExperimentalTime::class)
private fun FoodSubmission.toSummary() = FoodSubmissionSummaryResponse(
    id = id.toString(),
    createdAtEpochMs = createdAt.toEpochMilliseconds(),
    status = status.wire,
    origin = origin.wire,
    name = current.name,
    brand = current.brand,
    barcode = barcode,
    failedChecks = checks.map { it.code.wire }.distinct(),
    hasPhotos = hasPhotos,
    lane = lane?.wire,
    wouldAutoApprove = routing?.wouldAutoApprove == true,
    reasons = routing?.reasons.orEmpty(),
    confidence = routing?.confidence,
    audit = audit?.wire,
)

@OptIn(ExperimentalTime::class)
private fun FoodSubmission.toDetail() = FoodSubmissionDetailResponse(
    summary = toSummary(),
    foodId = foodId.toString(),
    model = model,
    parsed = parsed?.let(LabelReadingJson::of),
    parsedChecks = parsed?.checks.orEmpty().map(LabelCheckJson::of),
    submitted = LabelFactsJson.of(submitted, acknowledged),
    reviewed = reviewed?.let { LabelFactsJson.of(it) },
    checks = LabelChecks.check(current).map(LabelCheckJson::of),
    reviewedAtEpochMs = reviewedAt?.toEpochMilliseconds(),
    reviewedBy = reviewedBy,
    rejectReason = rejectReason,
    rejectCode = rejectCode?.wire,
    routing = routing?.let(RoutingJson::of),
    capture = capture?.let(CaptureJson::of),
    auditNote = auditNote,
)

@OptIn(ExperimentalTime::class)
private fun BarcodeLinkSuggestion.toResponse() = BarcodeLinkResponse(
    gtin = gtin,
    barcode = barcode,
    foodId = foodId.toString(),
    foodName = foodName,
    foodBrand = foodBrand,
    foodBarcode = foodBarcode,
    pairings = pairings,
    firstAtEpochMs = firstAt.toEpochMilliseconds(),
    lastAtEpochMs = lastAt.toEpochMilliseconds(),
    status = status.wire,
)

/** Photos are deleted this long after the review decision; the privacy policy says the same. */
const val PHOTO_RETENTION_DAYS = 30

/** An active Builder who said yes keeps their label photos this long after the decision, for the labelled set. */
const val RETAINED_PHOTO_DAYS = 730
private const val DEFAULT_LIMIT = 50
private const val MAX_LIMIT = 200
private const val MAX_REASON = 500
private const val MAX_ACTOR = 100
private const val GTIN_LENGTH = 14
