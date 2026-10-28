package com.opson.server.domain

/**
 * Streams everything the caller has, live rows only (tombstones are not exported), one kind at a
 * time and in this order: log entries by day and time, weigh-ins by time, day records by day,
 * meal notes by day and meal, exercise by start, fasts by day, custom foods and saved meals by
 * name, then the settings document. Rows of one kind always arrive together, so a writer can
 * close one file before it opens the next.
 *
 * Rows are read a page at a time and [sink] runs between pages, outside any transaction, so the
 * export never holds the whole history in memory or a connection while it writes. It is not one
 * snapshot: a row edited mid-export comes out as its page read it.
 */
interface ExportRepository {
    suspend fun export(userId: UserId, sink: suspend (DiaryRow) -> Unit)
}
