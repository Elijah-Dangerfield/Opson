package com.opson.server.routes

import com.opson.server.config.AdminConfig
import com.opson.server.data.CorrectionValuesJson
import com.opson.server.data.SnapshotJson
import com.opson.server.data.VerificationPhotosJson
import com.opson.server.data.VerificationProblemJson
import com.opson.server.data.VerificationValuesJson
import com.opson.server.domain.CorrectionValues
import com.opson.server.domain.FoodCatalogRepository
import com.opson.server.domain.FoodSnapshot
import com.opson.server.domain.FoodVerification
import com.opson.server.domain.FoodVerificationLimits
import com.opson.server.domain.FoodVerificationRepository
import com.opson.server.domain.PackSide
import com.opson.server.domain.ReportPhoto
import com.opson.server.domain.ReviewerCount
import com.opson.server.domain.VerificationDecision
import com.opson.server.domain.VerificationField
import com.opson.server.domain.VerificationProblem
import com.opson.server.domain.VerificationValues
import com.opson.server.plugins.problem
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.cacheControl
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

/** One Builder's verification as the console shows it, with the food as served now beside what they saw. */
@Serializable
data class FoodVerificationAdminResponse(
    val id: String,
    val foodId: String,
    val userId: String,
    val createdAtEpochMs: Long,
    /** `confirmed` or `unconfirmed`. */
    val outcome: String,
    val grounded: Int,
    val groundable: Int,
    /** `open`, `accepted`, `rejected`; null for a check decided on arrival. */
    val status: String? = null,
    /** Field codes: `calories`, `macros`, `serving`, `name_brand`, `ingredients`, `barcode`. */
    val wrong: List<String> = emptyList(),
    val missing: List<String> = emptyList(),
    /** What the Builder says is right, for its basis. */
    val values: VerificationValuesJson? = null,
    /** [values] as accepting them would store them: per 100 g (or ml), name and brand. */
    val valuesPer100: CorrectionValuesJson? = null,
    val note: String? = null,
    /** The food as the Builder saw it. */
    val snapshot: SnapshotJson? = null,
    /** The food as served now; null when it is gone from the catalog. */
    val now: FoodNowResponse? = null,
    val hasFrontPhoto: Boolean = false,
    val hasLabelPhoto: Boolean = false,
    val resolvedAtEpochMs: Long? = null,
    val resolvedBy: String? = null,
    val resolution: String? = null,
    val correctionId: String? = null,
)

@Serializable
data class FoodVerificationQueueResponse(val open: Long, val verifications: List<FoodVerificationAdminResponse>)

@Serializable
data class AcceptVerificationRequest(val values: CorrectionValuesJson? = null, val note: String? = null)

@Serializable
data class RejectVerificationRequest(val reason: String? = null)

/** One reviewer's decisions over the window, reports and verifications apart. */
@Serializable
data class ReviewerCountResponse(
    val reviewer: String,
    val reportsAccepted: Int,
    val reportsRejected: Int,
    val reportsDuplicate: Int,
    val verificationsAccepted: Int,
    val verificationsRejected: Int,
)

@Serializable
data class ReviewerCountsResponse(val days: Int, val reviewers: List<ReviewerCountResponse>)

/**
 * Builders' verifications that need a person, beside reports in the console's Review area, behind
 * the `X-Admin-Token` gate. The reviewer is `X-Admin-Actor`.
 *
 *  - `GET  /v1/admin/food-verifications?status=open|decided&limit=50`  open oldest first, or the latest decisions
 *  - `GET  /v1/admin/food-verifications/{id}/photos/{front|label}`     the photo, `no-store`
 *  - `POST /v1/admin/food-verifications/{id}/accept {values?, note?}`  stands behind it; with values, a
 *    correction over the existing food (the V29 overlay). 409 `not_open`.
 *  - `POST /v1/admin/food-verifications/{id}/reject {reason?}`         409 `not_open`
 *  - `GET  /v1/admin/food-review/reviewers?days=30`                   decisions per reviewer, reports and verifications
 *
 * Accepting makes the food Verified for everyone and counts toward the Builder's Pro. Each queue
 * call also deletes the photos of verifications decided more than [PHOTO_RETENTION_DAYS] days ago.
 */
@OptIn(ExperimentalTime::class)
@Suppress("LongMethod")
fun Route.foodVerificationAdminRoutes(
    config: AdminConfig,
    verifications: FoodVerificationRepository,
    catalog: FoodCatalogRepository,
    clock: Clock,
) {
    val logger = LoggerFactory.getLogger("FoodVerificationAdminRoutes")

    get("/v1/admin/food-verifications") {
        if (!call.requireAdmin(config)) return@get
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, MAX_LIST) ?: DEFAULT_LIST
        val purged = verifications.purgeDecidedPhotos(clock.now() - PHOTO_RETENTION_DAYS.days)
        if (purged > 0) logger.info("food verification photos purged count={}", purged)
        val list = when (call.request.queryParameters["status"] ?: "open") {
            "open" -> verifications.open(limit)
            "decided" -> verifications.decided(limit)
            else -> return@get call.respond(HttpStatusCode.BadRequest, problem("invalid_status", "open or decided"))
        }
        call.respond(FoodVerificationQueueResponse(verifications.countOpen(), list.map { it.toAdmin(catalog) }))
    }

    get("/v1/admin/food-verifications/{id}/photos/{side}") {
        if (!call.requireAdmin(config)) return@get
        val id = call.uuidParam("id") ?: return@get
        val side = PackSide.fromWire(call.parameters["side"])
            ?: return@get call.respond(HttpStatusCode.BadRequest, problem("invalid_side", "front or label"))
        val photo = verifications.photo(id, side) ?: return@get call.respond(HttpStatusCode.NotFound, problem("not_found", "No such photo."))
        call.response.cacheControl(CacheControl.NoStore(CacheControl.Visibility.Private))
        call.response.header(HttpHeaders.ContentDisposition, "inline")
        call.respondBytes(photo.bytes, ContentType.parse(photo.mediaType))
    }

    post("/v1/admin/food-verifications/{id}/accept") {
        if (!call.requireAdmin(config)) return@post
        val id = call.uuidParam("id") ?: return@post
        val request = call.receive<AcceptVerificationRequest>()
        val values: CorrectionValues? = request.values?.toDomain()?.takeUnless { it.isEmpty }?.let { raw ->
            when (val checked = validateCorrection(raw)) {
                is Checked.Rejected -> return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_values", checked.message))
                is Checked.Ok -> checked.value
            }
        }
        val note = request.note?.trim()?.ifEmpty { null }
        if ((note?.length ?: 0) > MAX_NOTE) return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_note", "At most $MAX_NOTE characters."))
        val found = verifications.get(id) ?: return@post call.respond(HttpStatusCode.NotFound, problem("not_found", "No such verification."))
        val base = catalog.findById(found.foodId)?.let(FoodSnapshot::of)
        if (values != null && base == null && found.snapshot == null) {
            return@post call.respond(HttpStatusCode.NotFound, problem("not_found", "The food is gone."))
        }
        when (val decision = verifications.accept(id, values, base, call.reviewer(), note)) {
            is VerificationDecision.Accepted -> {
                catalog.forgetCachedSearches()
                logger.info("food verification accepted corrected={}", decision.correction != null)
                call.respond(decision.verification.toAdmin(catalog))
            }
            VerificationDecision.NotOpen -> call.respond(HttpStatusCode.Conflict, problem("not_open", "Decided already. Reload."))
            VerificationDecision.NotFound -> call.respond(HttpStatusCode.NotFound, problem("not_found", "No such verification."))
            is VerificationDecision.Rejected -> error("accept answered rejected")
        }
    }

    post("/v1/admin/food-verifications/{id}/reject") {
        if (!call.requireAdmin(config)) return@post
        val id = call.uuidParam("id") ?: return@post
        val reason = call.receive<RejectVerificationRequest>().reason?.trim()?.ifEmpty { null }
        if ((reason?.length ?: 0) > MAX_NOTE) return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_reason", "At most $MAX_NOTE characters."))
        when (val decision = verifications.reject(id, reason, call.reviewer())) {
            is VerificationDecision.Rejected -> {
                logger.info("food verification rejected")
                call.respond(decision.verification.toAdmin(catalog))
            }
            VerificationDecision.NotOpen -> call.respond(HttpStatusCode.Conflict, problem("not_open", "Decided already. Reload."))
            VerificationDecision.NotFound -> call.respond(HttpStatusCode.NotFound, problem("not_found", "No such verification."))
            is VerificationDecision.Accepted -> error("reject answered accepted")
        }
    }

    get("/v1/admin/food-review/reviewers") {
        if (!call.requireAdmin(config)) return@get
        val days = call.request.queryParameters["days"]?.toIntOrNull()?.coerceIn(1, MAX_DAYS) ?: DEFAULT_DAYS
        val counts = verifications.reviewerCounts(clock.now() - days.days)
        call.respond(ReviewerCountsResponse(days, counts.map { it.toResponse() }))
    }
}

/**
 * A Builder's "Something was wrong", checked: known field codes, a field either wrong or missing,
 * values a pack could print (the report's bounds), a note of at most [FoodVerificationLimits.MAX_NOTE].
 * Something has to be said. Messages name the field and never echo a value.
 */
@Suppress("ReturnCount", "CyclomaticComplexMethod")
internal fun validateProblem(raw: VerificationProblemJson): Checked<VerificationProblem> {
    val codes = raw.wrong + raw.missing
    if (codes.any { VerificationField.fromWire(it) == null }) return Checked.Rejected("Unknown field")
    val wrong = raw.wrong.mapNotNullTo(LinkedHashSet(), VerificationField::fromWire)
    val missing = raw.missing.mapNotNullTo(LinkedHashSet(), VerificationField::fromWire)
    if (wrong.any { it in missing }) return Checked.Rejected("A field is either wrong or missing")
    val note = raw.note?.trim()?.ifEmpty { null }
    if ((note?.length ?: 0) > FoodVerificationLimits.MAX_NOTE) return Checked.Rejected("note is too long")
    val values = raw.values?.let { json ->
        val name = json.name?.trim()?.ifEmpty { null }
        val brand = json.brand?.trim()?.ifEmpty { null }
        if ((name?.length ?: 0) > FoodVerificationLimits.MAX_NAME || (brand?.length ?: 0) > FoodVerificationLimits.MAX_NAME) {
            return Checked.Rejected("name or brand is too long")
        }
        val barcode = json.barcode?.filterNot(Char::isWhitespace)?.ifEmpty { null }
        if (barcode != null && (barcode.length > FoodVerificationLimits.MAX_BARCODE || !barcode.all(Char::isDigit))) {
            return Checked.Rejected("barcode must be digits")
        }
        val suggested = when (val checked = validateSuggested(json.toDomain().suggested)) {
            is Checked.Rejected -> return Checked.Rejected(checked.message)
            is Checked.Ok -> checked.value
        }
        VerificationValues(name, brand, barcode, suggested).takeUnless { it.isEmpty }
    }
    if (wrong.isEmpty() && missing.isEmpty() && values == null && note == null) return Checked.Rejected("Say what was wrong")
    return Checked.Ok(VerificationProblem(wrong, missing, values, note))
}

internal sealed interface PhotosDecoded {
    class Ok(val photos: Map<PackSide, ReportPhoto>) : PhotosDecoded
    class Rejected(val status: HttpStatusCode, val code: String, val message: String) : PhotosDecoded
}

/** The front and the label, each like a report's photo. */
internal fun decodeVerificationPhotos(photos: VerificationPhotosJson?): PhotosDecoded {
    photos ?: return PhotosDecoded.Ok(emptyMap())
    val decoded = mutableMapOf<PackSide, ReportPhoto>()
    for ((side, encoded) in listOf(PackSide.Front to photos.front, PackSide.Label to photos.label)) {
        when (val one = decodePhoto(encoded)) {
            is PhotoDecoded.Rejected -> return PhotosDecoded.Rejected(one.status, one.code, one.message)
            is PhotoDecoded.Ok -> one.photo?.let { decoded[side] = it }
        }
    }
    return PhotosDecoded.Ok(decoded)
}

@OptIn(ExperimentalTime::class)
private suspend fun FoodVerification.toAdmin(catalog: FoodCatalogRepository) = FoodVerificationAdminResponse(
    id = id.toString(),
    foodId = foodId.toString(),
    userId = userId.value.toString(),
    createdAtEpochMs = createdAt.toEpochMilliseconds(),
    outcome = outcome.wire,
    grounded = grounded,
    groundable = groundable,
    status = status?.wire,
    wrong = problem?.wrong.orEmpty().map { it.wire },
    missing = problem?.missing.orEmpty().map { it.wire },
    values = problem?.values?.let(VerificationValuesJson::of),
    valuesPer100 = problem?.values?.toCorrection()?.takeUnless { it.isEmpty }?.let(CorrectionValuesJson::of),
    note = problem?.note,
    snapshot = snapshot?.let(SnapshotJson::of),
    now = catalog.findById(foodId)?.toNow(),
    hasFrontPhoto = PackSide.Front in photos,
    hasLabelPhoto = PackSide.Label in photos,
    resolvedAtEpochMs = resolvedAt?.toEpochMilliseconds(),
    resolvedBy = resolvedBy,
    resolution = resolution,
    correctionId = correctionId?.toString(),
)

private fun ReviewerCount.toResponse() = ReviewerCountResponse(
    reviewer = reviewer,
    reportsAccepted = reportsAccepted,
    reportsRejected = reportsRejected,
    reportsDuplicate = reportsDuplicate,
    verificationsAccepted = verificationsAccepted,
    verificationsRejected = verificationsRejected,
)

private const val DEFAULT_LIST = 50
private const val MAX_LIST = 200
private const val MAX_NOTE = 500
private const val DEFAULT_DAYS = 30
private const val MAX_DAYS = 365
