package com.opson.server.routes

import com.opson.server.data.SuggestedJson
import com.opson.server.domain.CatalogSource
import com.opson.server.domain.CorrectionAuthority
import com.opson.server.domain.CorrectionOutcome
import com.opson.server.domain.FoodCatalogRepository
import com.opson.server.domain.FoodReportLimiter
import com.opson.server.domain.FoodReportLimits
import com.opson.server.domain.FoodReportRepository
import com.opson.server.domain.FoodSnapshot
import com.opson.server.domain.FoodSubmissionRepository
import com.opson.server.domain.ImageType
import com.opson.server.domain.NewFoodReport
import com.opson.server.domain.ReportOutcome
import com.opson.server.domain.ReportPhoto
import com.opson.server.domain.ReportReason
import com.opson.server.domain.ReportStatus
import com.opson.server.domain.SuggestedValues
import com.opson.server.domain.UserId
import com.opson.server.plugins.FOOD_REPORT_LIMIT
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.Base64
import java.util.UUID

@Serializable
data class FoodReportRequest(
    /** Made once by the app, so a retry is the same report. */
    val id: String? = null,
    /** `wrong_calories`, `wrong_nutrients`, `wrong_serving`, `wrong_name`, `duplicate`, `not_food` or `other`. */
    val reason: String? = null,
    val suggested: SuggestedJson? = null,
    val note: String? = null,
    /** A photo of the label, base64 JPEG, PNG or WebP. */
    val photo: String? = null,
)

/** [status] is `open`, or `accepted` when the caller may correct foods directly ([CorrectionAuthority]). */
@Serializable
data class FoodReportResponse(val reportId: String, val status: String)

/**
 * "Report a problem" on a food's detail, open to everyone signed in:
 *
 *  - `POST /v1/foods/{id}/reports {id, reason, suggested?, note?, photo?}`
 *    stores what the person says is wrong with a catalog food, with the food
 *    as it was served to them. 201, or 200 for a repeat of the same id.
 *    400 `invalid_id`, `invalid_reason`, `invalid_values`, `invalid_note`,
 *    `invalid_image`, `invalid_body`; 404 `not_found` (no such food, or one
 *    the caller cannot see); 409 `id_conflict`; 413 `image_too_large`;
 *    429 `rate_limited`.
 *
 * A report never changes the food. It waits in the admin console's Review
 * area for someone to accept it (a correction), reject it or mark the food a
 * duplicate. The one exception is [CorrectionAuthority]: a person it trusts
 * has their suggested values applied straight away, which today is nobody.
 *
 * Per user, [FoodReportLimiter]; per IP, the [FOOD_REPORT_LIMIT] bucket. The
 * log line is the outcome and the reason code only: never a value, the note
 * or anything from the photo.
 */
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
fun Route.foodReportRoutes(
    catalog: FoodCatalogRepository,
    reports: FoodReportRepository,
    submissions: FoodSubmissionRepository,
    limiter: FoodReportLimiter,
    authority: CorrectionAuthority,
) {
    val logger = LoggerFactory.getLogger("FoodReportRoutes")
    val json = Json { ignoreUnknownKeys = true }

    authenticate(SUPABASE_JWT_AUTH) {
        rateLimit(RateLimitName(FOOD_REPORT_LIMIT)) {
            post("/v1/foods/{foodId}/reports") {
                val userId = call.userId()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, problem("unauthorized", "Sign in again"))
                val foodId = call.parameters["foodId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_id", "The food id must be a UUID"))
                val request = when (val read = call.readReport(json)) {
                    is ReportRead.Rejected -> return@post call.respond(read.status, problem(read.code, read.message))
                    is ReportRead.Ok -> read.request
                }
                val id = request.id?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_id", "id must be a UUID"))
                val reason = ReportReason.fromPerson(request.reason)
                    ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_reason", "Unknown reason"))
                val suggested = when (val checked = request.suggested?.let { validateSuggested(it.toDomain()) }) {
                    is Checked.Rejected -> return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_values", checked.message))
                    is Checked.Ok -> checked.value.takeUnless(SuggestedValues::isEmpty)
                    null -> null
                }
                val note = request.note?.trim()?.ifEmpty { null }
                if (note != null && note.length > FoodReportLimits.MAX_NOTE) {
                    return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_note", "At most ${FoodReportLimits.MAX_NOTE} characters"))
                }
                val photo = when (val decoded = decodePhoto(request.photo)) {
                    is PhotoDecoded.Rejected -> return@post call.respond(decoded.status, problem(decoded.code, decoded.message))
                    is PhotoDecoded.Ok -> decoded.photo
                }
                val food = catalog.findById(foodId)
                    ?.takeIf { it.source != CatalogSource.UserSubmitted || submissions.isVisible(foodId, userId) }
                    ?: return@post call.respond(HttpStatusCode.NotFound, problem("not_found", "No such food"))
                if (!limiter.tryReport(userId)) {
                    logger.info("food report outcome=rate_limited")
                    return@post call.respond(HttpStatusCode.TooManyRequests, problem("rate_limited", "That's a lot of reports for now. Try again later."))
                }
                val snapshot = FoodSnapshot.of(food)
                val outcome = reports.report(
                    NewFoodReport(id, userId, foodId, reason, suggested, note, snapshot, photo),
                )
                when (outcome) {
                    is ReportOutcome.Created -> {
                        val applied = suggested != null && applyDirectly(authority, reports, catalog, userId, outcome.report.id, foodId, suggested, snapshot)
                        logger.info("food report outcome=created reason={} suggested={} photo={} applied={}", reason.wire, suggested != null, photo != null, applied)
                        call.respond(
                            HttpStatusCode.Created,
                            FoodReportResponse(id.toString(), if (applied) ReportStatus.Accepted.wire else outcome.report.status.wire),
                        )
                    }
                    is ReportOutcome.Existing -> {
                        logger.info("food report outcome=repeat")
                        call.respond(FoodReportResponse(id.toString(), outcome.report.status.wire))
                    }
                    ReportOutcome.Conflict -> call.respond(HttpStatusCode.Conflict, problem("id_conflict", "That id is taken"))
                }
            }
        }
    }
}

/**
 * Where "who can apply a correction directly" is decided: [CorrectionAuthority].
 * For a person it trusts, the report's suggested values become a correction at
 * once, recorded under their account id, and the report closes as accepted.
 */
@Suppress("LongParameterList")
private suspend fun applyDirectly(
    authority: CorrectionAuthority,
    reports: FoodReportRepository,
    catalog: FoodCatalogRepository,
    userId: UserId,
    reportId: UUID,
    foodId: UUID,
    suggested: SuggestedValues,
    base: FoodSnapshot,
): Boolean {
    if (!authority.canApplyDirectly(userId)) return false
    val outcome = reports.applyCorrection(foodId, suggested.toCorrection(), base, listOf(reportId), by = "user:${userId.value}")
    if (outcome is CorrectionOutcome.Applied) catalog.forgetCachedSearches()
    return outcome is CorrectionOutcome.Applied
}

internal sealed interface Checked<out T> {
    class Ok<T>(val value: T) : Checked<T>
    class Rejected(val message: String) : Checked<Nothing>
}

/**
 * Numbers a pack could print, and per 100 nothing a food can hold: a basis
 * of a few grams with a typed can's worth of sugar is a typo, not a report.
 * Messages name the field and never echo the value.
 */
internal fun validateSuggested(v: SuggestedValues): Checked<SuggestedValues> {
    val amounts = mapOf("kcal" to v.kcal, "protein" to v.protein, "carbs" to v.carbs, "fat" to v.fat)
    for ((key, value) in amounts) {
        if (value != null && (!value.isFinite() || value < 0 || value > FoodReportLimits.MAX_AMOUNT)) return Checked.Rejected("$key is out of range")
    }
    for ((key, grams) in mapOf("basisGrams" to v.basisGrams, "servingGrams" to v.servingGrams)) {
        if (grams != null && (!grams.isFinite() || grams <= 0 || grams > FoodReportLimits.MAX_GRAMS)) return Checked.Rejected("$key is out of range")
    }
    val labels = mapOf("basisLabel" to v.basisLabel, "servingLabel" to v.servingLabel)
    if (labels.values.any { (it?.length ?: 0) > FoodReportLimits.MAX_LABEL }) return Checked.Rejected("a label is too long")
    val per100 = v.toCorrection()
    if ((per100.kcal ?: 0.0) > FoodReportLimits.MAX_KCAL_PER_100) return Checked.Rejected("kcal is more than any food holds")
    val macros = listOf(per100.protein, per100.carbs, per100.fat)
    if (macros.any { (it ?: 0.0) > FoodReportLimits.MAX_MACRO_PER_100 }) return Checked.Rejected("a macro is more than the food weighs")
    return Checked.Ok(
        v.copy(basisLabel = v.basisLabel?.trim()?.ifEmpty { null }, servingLabel = v.servingLabel?.trim()?.ifEmpty { null }),
    )
}

private sealed interface ReportRead {
    class Ok(val request: FoodReportRequest) : ReportRead
    class Rejected(val status: HttpStatusCode, val code: String, val message: String) : ReportRead
}

/** Reads at most [FoodReportLimits.MAX_BODY_BYTES], whatever `Content-Length` claims, then decodes. */
private suspend fun ApplicationCall.readReport(json: Json): ReportRead {
    val tooLarge = ReportRead.Rejected(HttpStatusCode.PayloadTooLarge, "image_too_large", "The photo is too large")
    if ((request.contentLength() ?: 0L) > FoodReportLimits.MAX_BODY_BYTES) return tooLarge
    val body = receiveChannel().readRemaining(FoodReportLimits.MAX_BODY_BYTES + 1).readByteArray()
    if (body.size > FoodReportLimits.MAX_BODY_BYTES) return tooLarge
    return try {
        ReportRead.Ok(json.decodeFromString(FoodReportRequest.serializer(), body.decodeToString()))
    } catch (_: SerializationException) {
        ReportRead.Rejected(HttpStatusCode.BadRequest, "invalid_body", "The request could not be read")
    } catch (_: IllegalArgumentException) {
        ReportRead.Rejected(HttpStatusCode.BadRequest, "invalid_body", "The request could not be read")
    }
}

private sealed interface PhotoDecoded {
    class Ok(val photo: ReportPhoto?) : PhotoDecoded
    class Rejected(val status: HttpStatusCode, val code: String, val message: String) : PhotoDecoded
}

private fun decodePhoto(encoded: String?): PhotoDecoded {
    val text = encoded?.takeIf { it.isNotBlank() } ?: return PhotoDecoded.Ok(null)
    val invalid = PhotoDecoded.Rejected(HttpStatusCode.BadRequest, "invalid_image", "photo must be base64 JPEG, PNG or WebP")
    val bytes = try {
        Base64.getMimeDecoder().decode(text)
    } catch (_: IllegalArgumentException) {
        return invalid
    }
    val type: ImageType = ImageType.sniff(bytes) ?: return invalid
    if (bytes.size > FoodReportLimits.MAX_IMAGE_BYTES) {
        return PhotoDecoded.Rejected(HttpStatusCode.PayloadTooLarge, "image_too_large", "The photo is too large")
    }
    return PhotoDecoded.Ok(ReportPhoto(bytes, type))
}
