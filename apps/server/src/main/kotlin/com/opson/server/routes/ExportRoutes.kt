package com.opson.server.routes

import com.opson.server.domain.ExportRecord
import com.opson.server.domain.ExportRepository
import com.opson.server.domain.Nutrients
import com.opson.server.plugins.EXPORT_LIMIT
import com.opson.server.plugins.SUPABASE_JWT_AUTH
import com.opson.server.plugins.userId
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.io.Writer
import java.time.ZoneOffset

/**
 * `GET /v1/export.csv`: the caller's log entries, weigh-ins and water, as one
 * CSV, streamed row by row. The handoff's Support page links here ("export my
 * data"). Not in it: day notes, feelings and check-ins, meal feelings, fasts,
 * custom foods, saved meals and settings.
 *
 * One `record_type` column (`entry`, `weight`, `water`) and a fixed set of
 * columns, blank where a column does not apply. Times are UTC ISO-8601.
 * Text cells that a spreadsheet would run as a formula are prefixed with `'`.
 */
fun Route.exportRoutes(repository: ExportRepository) {
    authenticate(SUPABASE_JWT_AUTH) {
        rateLimit(RateLimitName(EXPORT_LIMIT)) {
            get("/v1/export.csv") {
                val userId = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "opson-export.csv")
                        .toString(),
                )
                call.respondTextWriter(ContentType.Text.CSV.withParameter("charset", "utf-8")) {
                    writeRow(EXPORT_HEADER)
                    repository.export(userId) { record -> writeRow(record.toCells()) }
                }
            }
        }
    }
}

internal val EXPORT_HEADER = listOf(
    "record_type", "day", "time_utc", "slot", "name", "amount", "grams",
    "kcal", "protein_g", "carbs_g", "fat_g", "fiber_g", "precision", "method", "weight_kg", "water_ml",
)

internal fun ExportRecord.toCells(): List<String?> = when (this) {
    is ExportRecord.Entry -> listOf(
        "entry", day.toString(), loggedAt.toString(), slot.wire, name, amountText, grams.cell(),
    ) + nutrients.cells() + listOf(precision.wire, method.wire, null, null)

    is ExportRecord.Weight -> listOf(
        "weight",
        java.time.Instant.ofEpochMilli(measuredAt.toEpochMilliseconds()).atZone(ZoneOffset.UTC).toLocalDate().toString(),
        measuredAt.toString(),
        null, null, null, null, null, null, null, null, null, null, null,
        kg.cell(),
        null,
    )

    is ExportRecord.Water -> listOf(
        "water", day.toString(), null, null, null, null, null, null, null, null, null, null, null, null, null,
        waterMl.toString(),
    )
}

private fun Nutrients.cells(): List<String?> = all().map { it.cell() }

private fun Double?.cell(): String? = this?.let { if (it == Math.floor(it) && it < 1e15) it.toLong().toString() else it.toString() }

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
