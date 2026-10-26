package com.opson.libraries.diary

/**
 * Everything the user has logged, as the server's CSV (`GET /v1/export.csv`).
 * The server reads only what has synced, so call `DiarySync.syncNow()` first
 * if queued changes should be in it.
 */
interface DataExport {
    /** The CSV bytes, or a failure when the server cannot be reached. */
    suspend fun csv(): Result<ByteArray>
}
