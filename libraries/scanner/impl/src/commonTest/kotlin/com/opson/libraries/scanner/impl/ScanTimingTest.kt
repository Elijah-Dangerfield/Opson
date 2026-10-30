package com.opson.libraries.scanner.impl

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogId
import com.opson.libraries.core.logging.LogTree
import com.opson.libraries.scanner.BarcodeFormat
import com.opson.libraries.scanner.ScannedCode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What [ScanTiming] writes to the log about a read: its format and length, never the code itself.
 * The timings themselves are not asserted; they are wall-clock.
 */
class ScanTimingTest {
    private val lines = mutableListOf<String>()
    private val tree = object : LogTree() {
        override fun log(entry: LogEntry): LogId? {
            entry.message?.let { lines += it }
            return null
        }
    }

    @BeforeTest
    fun plant() = KLog.plant(tree)

    @AfterTest
    fun uproot() = KLog.uproot(tree)

    @Test
    fun aReadIsLoggedByFormatAndLengthNotByValue() {
        val barcode = "5012345678900"
        val link = "https://www.opsontracker.com/r/abcd2345"

        ScanTiming("test").read(ScannedCode(barcode, BarcodeFormat.Ean13))
        ScanTiming("test").read(ScannedCode(link, BarcodeFormat.Qr))

        assertTrue(lines.any { "Ean13 (13 chars)" in it }, lines.toString())
        assertTrue(lines.any { "Qr (${link.length} chars)" in it }, lines.toString())
        assertFalse(lines.any { barcode in it || link in it || "abcd2345" in it }, lines.toString())
    }
}
