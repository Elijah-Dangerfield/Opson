package com.opson.server.domain

/**
 * Streams everything the caller has, live rows only (tombstones are not exported), one kind at a
 * time and in this order: log entries by day and time, weigh-ins by time, day records by day,
 * meal notes by day and meal, exercise by start, fasts by day, custom foods and saved meals by
 * name, then the settings document. Rows of one kind always arrive together, so a writer can
 * close one file before it opens the next.
 *
 * [sink] is called once per row inside one read transaction, so the export never holds the
 * whole history in memory.
 */
interface ExportRepository {
    suspend fun export(userId: UserId, sink: suspend (DiaryRow) -> Unit)
}
