package com.opson.libraries.scanner.impl

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.scanner.ScannedCode
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Debug-level timings for one camera session, so a scan on a real phone can be
 * measured from the log rather than by feel: how long the camera took to come
 * up, how long until the first code was read, and on Android how long the
 * decoder spends on each frame. Two or three lines a session, never one a
 * frame. Filter the log for "Scanner".
 *
 * [cameraUp] and [read] on the main thread; [frame] from the analysis thread,
 * which only ever adds to two counters.
 */
internal class ScanTiming(private val platform: String) {
    private val opened: TimeMark = TimeSource.Monotonic.markNow()
    private var running: TimeMark? = null
    private var firstReadLogged = false

    @kotlin.concurrent.Volatile
    private var frames = 0

    @kotlin.concurrent.Volatile
    private var decodeMillis = 0L

    fun cameraUp() {
        running = TimeSource.Monotonic.markNow()
        log { "camera up ${opened.elapsedNow().inWholeMilliseconds} ms after opening" }
    }

    fun frame(millis: Long) {
        frames += 1
        decodeMillis += millis
    }

    fun read(code: ScannedCode) {
        if (firstReadLogged) return
        firstReadLogged = true
        val sinceUp = running?.elapsedNow()?.inWholeMilliseconds
        log { "first read ${describe(code)} ${sinceUp ?: "?"} ms after the camera came up, ${opened.elapsedNow().inWholeMilliseconds} ms after opening${decodeSummary()}" }
    }

    fun closed() {
        log { "closed after ${opened.elapsedNow().inWholeMilliseconds} ms${decodeSummary()}" }
    }

    private fun decodeSummary(): String {
        val count = frames
        if (count == 0) return ""
        return ", $count frames decoded at ${decodeMillis / count} ms each"
    }

    private inline fun log(message: () -> String) {
        KLog.withTag(TAG).d("$platform ${message()}")
    }

    internal companion object {
        private const val TAG = "Scanner"

        /**
         * A read as the log may carry it: its format and length, never its value. A barcode says
         * what someone eats, and a QR code can be a private link.
         */
        fun describe(code: ScannedCode): String = "${code.format.name} (${code.value.length} chars)"
    }
}
