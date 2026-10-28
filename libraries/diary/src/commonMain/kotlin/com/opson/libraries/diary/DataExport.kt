package com.opson.libraries.diary

import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.Visibility

/**
 * Everything the user has, as the server's zip (`GET /v1/export.zip`): a CSV per kind of row, a
 * daily summary, the settings and a README. The server reads only what has synced, so call
 * `DiarySync.syncNow()` first if queued changes should be in it.
 */
interface DataExport {
    /** The zip's bytes, or a failure when the server cannot be reached. */
    suspend fun everything(): Result<ByteArray>

    companion object {
        const val FILE_NAME = "opson-export.zip"
        const val MIME_TYPE = "application/zip"

        /** The numbers the export holds, every one of them whatever the mode shows. */
        val METRICS: List<Metric> = listOf(
            Metric.Calories, Metric.EnergyOut, Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber,
            Metric.Micros, Metric.Weight, Metric.Water,
        )
    }
}

/**
 * Whether [numbers] keeps any of [metrics] from being shown outright: hidden, softened to a word
 * or a bar, or held until Later. A file made now holds the exact numbers, so anything short of
 * [Visibility.Show] is a number the person would see for the first time there.
 */
fun NumbersSettings.hidesAnyOf(metrics: Collection<Metric>): Boolean = metrics.any { level(it) != Visibility.Show }
