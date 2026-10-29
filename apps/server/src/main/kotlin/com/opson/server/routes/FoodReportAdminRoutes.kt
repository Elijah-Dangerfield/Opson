package com.opson.server.routes

import com.opson.server.config.AdminConfig
import com.opson.server.data.CorrectionValuesJson
import com.opson.server.data.Per100Json
import com.opson.server.data.ServingJson
import com.opson.server.data.SnapshotJson
import com.opson.server.data.SuggestedJson
import com.opson.server.domain.CorrectionOutcome
import com.opson.server.domain.CorrectionValues
import com.opson.server.domain.Food
import com.opson.server.domain.FoodCatalogRepository
import com.opson.server.domain.FoodCorrection
import com.opson.server.domain.FoodReport
import com.opson.server.domain.FoodReportLimits
import com.opson.server.domain.FoodReportRepository
import com.opson.server.domain.FoodSnapshot
import com.opson.server.domain.RevertOutcome
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
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

/** One report as the console shows it. */
@Serializable
data class FoodReportAdminResponse(
    val id: String,
    val createdAtEpochMs: Long,
    val reason: String,
    /** False for the server's own energy check. */
    val fromPerson: Boolean,
    val suggested: SuggestedJson? = null,
    /** [suggested] per 100 g (or ml), as accepting it would store it. */
    val suggestedPer100: CorrectionValuesJson? = null,
    val note: String? = null,
    /** The food as the reporter saw it. */
    val snapshot: SnapshotJson,
    val status: String,
    val hasPhoto: Boolean,
    val resolvedAtEpochMs: Long? = null,
    val resolvedBy: String? = null,
    val resolution: String? = null,
    val correctionId: String? = null,
    val duplicateOf: String? = null,
)

@Serializable
data class FoodCorrectionResponse(
    val id: String,
    val createdAtEpochMs: Long,
    val createdBy: String,
    val values: CorrectionValuesJson,
    val base: SnapshotJson,
    val live: Boolean,
    val revertedAtEpochMs: Long? = null,
    val revertedBy: String? = null,
    val supersededBy: String? = null,
)

/** The food's values as served now, a correction included. Null fields are unknown. */
@Serializable
data class FoodNowResponse(
    val name: String,
    val brand: String? = null,
    val source: String,
    val barcode: String? = null,
    val per100g: Per100Json,
    val serving: ServingJson? = null,
    /** What the energy guard did on the way out (`zero_with_macros`, `from_macros`, `energy_without_macros`). */
    val energyFlags: List<String> = emptyList(),
)

/** The open reports on one food, for the queue. */
@Serializable
data class FoodReportGroupResponse(
    val foodId: String,
    val name: String,
    val brand: String? = null,
    val source: String,
    val reportCount: Int,
    val oldestAtEpochMs: Long,
    val reasons: Map<String, Int>,
    val hasPhoto: Boolean,
    /** Null when the food is gone from the catalog (an Open Food Facts row the monthly reload dropped). */
    val now: FoodNowResponse? = null,
    val correction: FoodCorrectionResponse? = null,
    val reports: List<FoodReportAdminResponse>,
)

@Serializable
data class FoodReportQueueResponse(val openReports: Long, val foods: List<FoodReportGroupResponse>)

/** Everything about one food: every report, decided ones included, and every correction. */
@Serializable
data class FoodReportFoodResponse(
    val foodId: String,
    val now: FoodNowResponse? = null,
    val reports: List<FoodReportAdminResponse>,
    val corrections: List<FoodCorrectionResponse>,
)

@Serializable
data class AcceptReportsRequest(val reportIds: List<String> = emptyList(), val values: CorrectionValuesJson? = null)

@Serializable
data class CloseReportsRequest(val reportIds: List<String> = emptyList(), val reason: String? = null, val duplicateOf: String? = null)

@Serializable
data class ClosedReportsResponse(val closed: Int)

/**
 * The reports queue, next to submissions in the console's Review area, behind
 * the `X-Admin-Token` gate. The reviewer is `X-Admin-Actor`.
 *
 *  - `GET  /v1/admin/food-reports?limit=50`                   open reports grouped per food, most reported first, then oldest
 *  - `GET  /v1/admin/food-reports/foods/{foodId}`             every report and correction on one food
 *  - `GET  /v1/admin/food-reports/{id}/photo`                 the label photo, `no-store`
 *  - `POST /v1/admin/food-reports/foods/{foodId}/accept {reportIds, values}`  a correction over the food; 409 `not_open`
 *  - `POST /v1/admin/food-reports/reject {reportIds, reason?}`
 *  - `POST /v1/admin/food-reports/duplicate {reportIds, duplicateOf?}`
 *  - `POST /v1/admin/food-corrections/{id}/revert`            undoes the live correction; 409 `not_live`
 *
 * A correction is laid over the food wherever it is served (`food_corrections`,
 * V28), so a re-import of the source never undoes it, and reverting it brings
 * back whatever it replaced. Each queue call also deletes the photos of
 * reports decided more than [PHOTO_RETENTION_DAYS] days ago.
 */
@OptIn(ExperimentalTime::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun Route.foodReportAdminRoutes(
    config: AdminConfig,
    reports: FoodReportRepository,
    catalog: FoodCatalogRepository,
    clock: Clock,
) {
    val logger = LoggerFactory.getLogger("FoodReportAdminRoutes")

    get("/v1/admin/food-reports") {
        if (!call.requireAdmin(config)) return@get
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, MAX_GROUPS) ?: DEFAULT_GROUPS
        val purged = reports.purgeDecidedPhotos(clock.now() - PHOTO_RETENTION_DAYS.days)
        if (purged > 0) logger.info("food report photos purged count={}", purged)
        val open = reports.open(MAX_OPEN_READ)
        val groups = ReportQueue.groups(open).take(limit)
        val corrections = reports.liveCorrections(groups.map { it.first().foodId })
        call.respond(
            FoodReportQueueResponse(
                openReports = reports.countOpen(),
                foods = groups.map { group ->
                    val first = group.first()
                    FoodReportGroupResponse(
                        foodId = first.foodId.toString(),
                        name = first.snapshot.name,
                        brand = first.snapshot.brand,
                        source = first.snapshot.source,
                        reportCount = group.size,
                        oldestAtEpochMs = group.minOf { it.createdAt }.toEpochMilliseconds(),
                        reasons = group.groupingBy { it.reason.wire }.eachCount(),
                        hasPhoto = group.any { it.hasPhoto },
                        now = catalog.findById(first.foodId)?.toNow(),
                        correction = corrections[first.foodId]?.toResponse(),
                        reports = group.map { it.toAdmin() },
                    )
                },
            ),
        )
    }

    get("/v1/admin/food-reports/foods/{foodId}") {
        if (!call.requireAdmin(config)) return@get
        val foodId = call.uuidParam("foodId") ?: return@get
        call.respond(
            FoodReportFoodResponse(
                foodId = foodId.toString(),
                now = catalog.findById(foodId)?.toNow(),
                reports = reports.forFood(foodId).map { it.toAdmin() },
                corrections = reports.corrections(foodId).map { it.toResponse() },
            ),
        )
    }

    get("/v1/admin/food-reports/{id}/photo") {
        if (!call.requireAdmin(config)) return@get
        val id = call.uuidParam("id") ?: return@get
        val photo = reports.photo(id) ?: return@get call.respond(HttpStatusCode.NotFound, problem("not_found", "No such photo."))
        call.response.cacheControl(CacheControl.NoStore(CacheControl.Visibility.Private))
        call.response.header(HttpHeaders.ContentDisposition, "inline")
        call.respondBytes(photo.bytes, ContentType.parse(photo.mediaType))
    }

    post("/v1/admin/food-reports/foods/{foodId}/accept") {
        if (!call.requireAdmin(config)) return@post
        val foodId = call.uuidParam("foodId") ?: return@post
        val request = call.receive<AcceptReportsRequest>()
        val ids = call.reportIds(request.reportIds) ?: return@post
        val values = when (val checked = request.values?.toDomain()?.let(::validateCorrection)) {
            null -> return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_values", "values are required"))
            is Checked.Rejected -> return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_values", checked.message))
            is Checked.Ok -> checked.value
        }
        val food = catalog.findById(foodId)
            ?: return@post call.respond(HttpStatusCode.NotFound, problem("not_found", "No such food."))
        when (val outcome = reports.applyCorrection(foodId, values, FoodSnapshot.of(food), ids, call.reviewer())) {
            is CorrectionOutcome.Applied -> {
                catalog.forgetCachedSearches()
                logger.info("food correction applied reports={}", ids.size)
                call.respond(outcome.correction.toResponse())
            }
            CorrectionOutcome.NotOpen -> call.respond(HttpStatusCode.Conflict, problem("not_open", "A report is not open on this food. Reload."))
            CorrectionOutcome.NothingToChange -> call.respond(HttpStatusCode.BadRequest, problem("invalid_values", "Nothing to change."))
        }
    }

    post("/v1/admin/food-reports/reject") {
        if (!call.requireAdmin(config)) return@post
        val request = call.receive<CloseReportsRequest>()
        val ids = call.reportIds(request.reportIds) ?: return@post
        val reason = request.reason?.trim()?.ifEmpty { null }
        if (reason != null && reason.length > MAX_REASON) {
            return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_reason", "At most $MAX_REASON characters."))
        }
        call.respond(ClosedReportsResponse(reports.reject(ids, reason, call.reviewer())))
    }

    post("/v1/admin/food-reports/duplicate") {
        if (!call.requireAdmin(config)) return@post
        val request = call.receive<CloseReportsRequest>()
        val ids = call.reportIds(request.reportIds) ?: return@post
        val duplicateOf = request.duplicateOf?.trim()?.ifEmpty { null }?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_id", "duplicateOf must be a food id"))
        }
        call.respond(ClosedReportsResponse(reports.markDuplicate(ids, duplicateOf, call.reviewer())))
    }

    post("/v1/admin/food-corrections/{id}/revert") {
        if (!call.requireAdmin(config)) return@post
        val id = call.uuidParam("id") ?: return@post
        when (val outcome = reports.revert(id, call.reviewer())) {
            is RevertOutcome.Reverted -> {
                catalog.forgetCachedSearches()
                logger.info("food correction reverted restored={}", outcome.restored != null)
                call.respond(outcome.correction.toResponse())
            }
            RevertOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, problem("not_found", "No such correction."))
            RevertOutcome.NotLive -> call.respond(HttpStatusCode.Conflict, problem("not_live", "Only the correction in force can be undone."))
        }
    }
}

/** The queue's order: the most reported foods first, then the longest waiting. */
@OptIn(ExperimentalTime::class)
internal object ReportQueue {
    fun groups(open: List<FoodReport>): List<List<FoodReport>> =
        open.groupBy { it.foodId }.values
            .map { group -> group.sortedBy { it.createdAt } }
            .sortedWith(compareByDescending<List<FoodReport>> { it.size }.thenBy { it.first().createdAt })
}

/** A reviewer's values: the same bounds as a report's, per 100 g. */
internal fun validateCorrection(v: CorrectionValues): Checked<CorrectionValues> {
    val name = v.name?.trim()?.ifEmpty { null }
    val brand = v.brand?.trim()?.ifEmpty { null }
    if ((name?.length ?: 0) > FoodReportLimits.MAX_NAME || (brand?.length ?: 0) > FoodReportLimits.MAX_NAME) {
        return Checked.Rejected("name or brand is too long")
    }
    if ((v.kcal ?: 0.0) !in 0.0..FoodReportLimits.MAX_KCAL_PER_100 || v.kcal?.isFinite() == false) return Checked.Rejected("kcal is out of range")
    for ((key, value) in mapOf("protein" to v.protein, "carbs" to v.carbs, "fat" to v.fat)) {
        if (value != null && (!value.isFinite() || value !in 0.0..FoodReportLimits.MAX_MACRO_PER_100)) return Checked.Rejected("$key is out of range")
    }
    val label = v.servingLabel?.trim()?.ifEmpty { null }
    if ((label?.length ?: 0) > FoodReportLimits.MAX_LABEL) return Checked.Rejected("servingLabel is too long")
    val grams = v.servingGrams
    if (grams != null && (!grams.isFinite() || grams <= 0 || grams > FoodReportLimits.MAX_GRAMS)) return Checked.Rejected("servingGrams is out of range")
    if (grams != null && label == null) return Checked.Rejected("servingGrams needs a servingLabel")
    val cleaned = v.copy(name = name, brand = brand, servingLabel = label)
    return if (cleaned.isEmpty) Checked.Rejected("Nothing to change.") else Checked.Ok(cleaned)
}

private suspend fun ApplicationCall.uuidParam(name: String): UUID? {
    val id = parameters[name]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (id == null) respond(HttpStatusCode.BadRequest, problem("invalid_id", "Not an id."))
    return id
}

private suspend fun ApplicationCall.reportIds(raw: List<String>): List<UUID>? {
    val ids = raw.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }.distinct()
    if (ids.isEmpty() || ids.size != raw.distinct().size || ids.size > MAX_IDS) {
        respond(HttpStatusCode.BadRequest, problem("invalid_ids", "reportIds must be 1 to $MAX_IDS report ids"))
        return null
    }
    return ids
}

private fun ApplicationCall.reviewer(): String = request.header("X-Admin-Actor")?.trim()?.take(MAX_ACTOR)?.ifEmpty { null } ?: "admin"

private fun Food.toNow() = FoodNowResponse(
    name = name,
    brand = brand,
    source = source.wire,
    barcode = barcode,
    per100g = Per100Json.of(per100g),
    serving = portions.firstOrNull()?.let { ServingJson(it.label, it.grams) },
    energyFlags = energyFlags.map { it.wire }.sorted(),
)

@OptIn(ExperimentalTime::class)
private fun FoodReport.toAdmin() = FoodReportAdminResponse(
    id = id.toString(),
    createdAtEpochMs = createdAt.toEpochMilliseconds(),
    reason = reason.wire,
    fromPerson = userId != null,
    suggested = suggested?.let(SuggestedJson::of),
    suggestedPer100 = suggested?.toCorrection()?.let(CorrectionValuesJson::of),
    note = note,
    snapshot = SnapshotJson.of(snapshot),
    status = status.wire,
    hasPhoto = hasPhoto,
    resolvedAtEpochMs = resolvedAt?.toEpochMilliseconds(),
    resolvedBy = resolvedBy,
    resolution = resolution,
    correctionId = correctionId?.toString(),
    duplicateOf = duplicateOf?.toString(),
)

@OptIn(ExperimentalTime::class)
private fun FoodCorrection.toResponse() = FoodCorrectionResponse(
    id = id.toString(),
    createdAtEpochMs = createdAt.toEpochMilliseconds(),
    createdBy = createdBy,
    values = CorrectionValuesJson.of(values),
    base = SnapshotJson.of(base),
    live = live,
    revertedAtEpochMs = revertedAt?.toEpochMilliseconds(),
    revertedBy = revertedBy,
    supersededBy = supersededBy?.toString(),
)

private const val DEFAULT_GROUPS = 50
private const val MAX_GROUPS = 200

/** Open reports read to group; far more than a day's queue. */
private const val MAX_OPEN_READ = 2_000
private const val MAX_IDS = 200
private const val MAX_REASON = 500
private const val MAX_ACTOR = 100
