package com.opson.server.domain

import java.time.LocalDate
import kotlin.time.Instant

/** One line of the caller's `GET /v1/export.csv`. Live rows only; tombstones are not exported. */
sealed interface ExportRecord {
    data class Entry(
        val day: LocalDate,
        val loggedAt: Instant,
        val slot: MealSlot,
        val name: String,
        val amountText: String?,
        val grams: Double?,
        val nutrients: Nutrients,
        val precision: Precision,
        val method: LogMethod,
    ) : ExportRecord

    data class Weight(val measuredAt: Instant, val kg: Double) : ExportRecord

    data class Water(val day: LocalDate, val waterMl: Int) : ExportRecord
}

/**
 * Streams the caller's log: entries by day and time, then
 * weigh-ins, then water per day. [sink] is called once per row inside one
 * read transaction, so the export never holds the whole history in memory.
 */
interface ExportRepository {
    suspend fun export(userId: UserId, sink: suspend (ExportRecord) -> Unit)
}
