package com.opson.server.routes

import com.opson.server.domain.DayRecord
import com.opson.server.domain.DiaryRow
import com.opson.server.domain.ExportRepository
import com.opson.server.domain.LogEntry
import com.opson.server.domain.Nutrients
import com.opson.server.domain.WeighIn
import com.opson.server.plugins.EXPORT_LIMIT
import com.opson.server.plugins.SUPABASE_JWT_AUTH
import com.opson.server.plugins.userId
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.io.Writer
import java.time.DateTimeException
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Clock

/** The IANA time zone the phone is in (`Europe/London`), for the export's local times. */
const val TIME_ZONE_HEADER = "X-Time-Zone"

internal const val EXPORT_ARCHIVE_NAME = "opson-export.zip"

/**
 * The caller's data, two ways. Both stream, and share one rate limit.
 *
 * `GET /v1/export.zip` is everything: one CSV per kind of row, a daily summary, the settings as
 * JSON and a README, laid out by [ExportArchiveWriter]. Local times follow [TIME_ZONE_HEADER],
 * and UTC when it is missing or not a zone.
 *
 * `GET /v1/export.csv` is what 0.1 phones call: log entries, weigh-ins and water as one CSV with a
 * `record_type` column (`entry`, `weight`, `water`) and a fixed set of columns, blank where one
 * does not apply. Times are UTC ISO-8601. Kept as it was so their Export button keeps working.
 *
 * Text cells that a spreadsheet would run as a formula are prefixed with `'` in both.
 */
fun Route.exportRoutes(repository: ExportRepository, clock: Clock = Clock.System) {
    authenticate(SUPABASE_JWT_AUTH) {
        rateLimit(RateLimitName(EXPORT_LIMIT)) {
            get("/v1/export.zip") {
                val userId = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val zone = call.exportZone()
                call.attachment(EXPORT_ARCHIVE_NAME)
                call.respondOutputStream(ContentType.Application.Zip) {
                    val archive = ExportArchiveWriter(this, zone, clock.now())
                    repository.export(userId) { row -> archive.write(row) }
                    archive.finish()
                }
            }
            get("/v1/export.csv") {
                val userId = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.attachment("opson-export.csv")
                call.respondTextWriter(ContentType.Text.CSV.withParameter("charset", "utf-8")) {
                    writeRow(EXPORT_HEADER)
                    repository.export(userId) { row -> row.toLegacyCells()?.let { writeRow(it) } }
                }
            }
        }
    }
}

private fun ApplicationCall.attachment(fileName: String) {
    response.header(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, fileName).toString(),
    )
}

private fun ApplicationCall.exportZone(): ZoneId {
    val id = request.header(TIME_ZONE_HEADER)?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_ZONE_ID }
        ?: return UTC
    return try {
        ZoneId.of(id)
    } catch (_: DateTimeException) {
        UTC
    }
}

private const val MAX_ZONE_ID = 64

/** Named rather than `ZoneOffset.UTC`, whose id is `Z`, so the time zone column reads `UTC`. */
private val UTC: ZoneId = ZoneId.of("UTC")

internal val EXPORT_HEADER = listOf(
    "record_type", "day", "time_utc", "slot", "name", "amount", "grams",
    "kcal", "protein_g", "carbs_g", "fat_g", "fiber_g", "precision", "method", "weight_kg", "water_ml",
)

/** A row of the 0.1 CSV, or null for the kinds it never held. */
internal fun DiaryRow.toLegacyCells(): List<String?>? = when (this) {
    is LogEntry -> listOf(
        "entry", day.toString(), loggedAt.toString(), slot.wire, name, amountText, grams.cell(),
    ) + nutrients.cells() + listOf(precision.wire, method.wire, null, null)

    is WeighIn -> listOf(
        "weight",
        java.time.Instant.ofEpochMilli(measuredAt.toEpochMilliseconds()).atZone(ZoneOffset.UTC).toLocalDate().toString(),
        measuredAt.toString(),
        null, null, null, null, null, null, null, null, null, null, null,
        kg.cell(),
        null,
    )

    is DayRecord -> waterMl?.let { water ->
        listOf(
            "water", day.toString(), null, null, null, null, null, null, null, null, null, null, null, null, null,
            water.toString(),
        )
    }

    else -> null
}

internal fun Nutrients.cells(): List<String?> = all().map { it.cell() }

internal fun Double?.cell(): String? = this?.let { if (it == Math.floor(it) && it < 1e15) it.toLong().toString() else it.toString() }

internal fun Writer.writeRow(cells: List<String?>) {
    write(cells.joinToString(",") { csvCell(it) })
    write("\r\n")
}

internal fun csvCell(value: String?): String {
    if (value.isNullOrEmpty()) return ""
    val safe = if (value.first() in "=+-@\t\r") "'$value" else value
    return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + safe.replace("\"", "\"\"") + "\""
    } else {
        safe
    }
}
