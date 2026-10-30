package com.opson.libraries.ui.qr

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The QR encoder against the standard's own numbers. A camera reading the result is checked by
 * hand (`QrPngFileTest` writes the images, docs/plans/recipes.md says how); these keep the parts
 * from drifting.
 */
class QrCodeTest {

    private val link = "https://www.opsontracker.com/r/abcd2345"

    @Test
    fun levelMHoldsWhatTheStandardSays() {
        assertEquals(listOf(16, 28, 44, 64, 86, 108, 124, 154, 182, 216), (1..10).map(QrCode::dataCodewords))
        assertEquals(208, QrCode.rawDataModules(1))
        assertEquals(1568, QrCode.rawDataModules(7))
    }

    @Test
    fun galoisMultiplicationWrapsAtTheModulus() {
        assertEquals(0x1D, QrCode.gfMultiply(0x80, 2))
        assertEquals(0, QrCode.gfMultiply(0x53, 0))
        assertEquals(0x53, QrCode.gfMultiply(0x53, 1))
    }

    /** HELLO WORLD at 1-M, the worked example everyone checks against. */
    @Test
    fun reedSolomonMatchesTheWorkedExample() {
        val data = intArrayOf(32, 91, 11, 120, 209, 114, 220, 77, 67, 64, 236, 17, 236, 17, 236, 17)
            .map { it.toByte() }.toByteArray()
        val ecc = QrCode.rsRemainder(data, QrCode.rsDivisor(10))
        assertContentEquals(intArrayOf(196, 35, 39, 119, 235, 215, 231, 226, 93, 23), ecc)
    }

    @Test
    fun aShareLinkFitsVersionThree() {
        val code = QrCode.encode(link)
        assertEquals(3, code.version)
        assertEquals(29, code.size)
    }

    @Test
    fun theFindersTimingAndDarkModuleAreWhereAReaderLooks() {
        val code = QrCode.encode(link)
        val last = code.size - 1
        listOf(0 to 0, last - 6 to 0, 0 to last - 6).forEach { (x0, y0) ->
            for (i in 0..6) {
                assertTrue(code.isDark(x0 + i, y0), "finder top edge")
                assertTrue(code.isDark(x0 + i, y0 + 6), "finder bottom edge")
            }
            assertTrue(!code.isDark(x0 + 1, y0 + 1), "finder ring is light")
            assertTrue(code.isDark(x0 + 3, y0 + 3), "finder centre is dark")
        }
        for (i in 8 until code.size - 8) assertEquals(i % 2 == 0, code.isDark(i, 6), "timing at $i")
        assertTrue(code.isDark(8, code.size - 8), "the dark module")
    }

    @Test
    fun theFormatBitsNameLevelMAndTheMaskUsed() {
        val code = QrCode.encode(link)
        var bits = 0
        for (i in 0..5) if (code.isDark(8, i)) bits = bits or (1 shl i)
        if (code.isDark(8, 7)) bits = bits or (1 shl 6)
        if (code.isDark(8, 8)) bits = bits or (1 shl 7)
        if (code.isDark(7, 8)) bits = bits or (1 shl 8)
        for (i in 9 until 15) if (code.isDark(14 - i, 8)) bits = bits or (1 shl i)
        val data = (bits xor 0x5412) ushr 10
        assertEquals(0, data ushr 3, "level M")
        assertEquals(code.mask, data and 7)
    }

    @Test
    fun theSameTextAlwaysMakesTheSameCode() {
        val a = QrCode.encode(link)
        val b = QrCode.encode(link)
        for (y in 0 until a.size) for (x in 0 until a.size) assertEquals(a.isDark(x, y), b.isDark(x, y))
    }

    @Test
    fun longTextTakesALargerVersion_andTooLongIsRefused() {
        assertEquals(10, QrCode.encode("x".repeat(213)).version)
        assertFailsWith<IllegalArgumentException> { QrCode.encode("x".repeat(214)) }
    }

    @Test
    fun thePngIsAOneBitSquareWithItsQuietZone() {
        val code = QrCode.encode(link)
        val png = QrPng.encode(code, scale = 4)
        assertContentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), png.copyOf(8))
        val side = (code.size + 2 * QrPng.QUIET_ZONE) * 4
        fun int(at: Int) = (0..3).fold(0) { acc, i -> (acc shl 8) or (png[at + i].toInt() and 0xFF) }
        assertEquals(side, int(16), "width")
        assertEquals(side, int(20), "height")
        assertEquals(1, png[24].toInt(), "one bit a pixel")
    }
}
