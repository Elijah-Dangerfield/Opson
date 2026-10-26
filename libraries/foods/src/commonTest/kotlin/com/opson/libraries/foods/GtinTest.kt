package com.opson.libraries.foods

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The same cases as the server's `BarcodesTest.gtin14*`, so the two sides cannot drift apart unnoticed. */
class GtinTest {

    @Test
    fun everyValidFormBecomesTheSameFourteenDigits() {
        assertEquals("00049000028911", Gtin.normalize("049000028911"), "UPC-A")
        assertEquals("00049000028911", Gtin.normalize("0049000028911"), "its EAN-13, as iOS reports a UPC-A")
        assertEquals("00049000028911", Gtin.normalize("00049000028911"), "its GTIN-14")
        assertEquals("04006381333931", Gtin.normalize("4006381333931"), "EAN-13")
        assertEquals("00000096385074", Gtin.normalize("96385074"), "EAN-8 that is not a UPC-E")
    }

    @Test
    fun anEightDigitCodeIsReadAsUpcEFirst() {
        assertEquals("00042100005264", Gtin.normalize("04252614"))
        assertEquals("042100005264", Gtin.upcEToUpcA("04252614"))
        assertNull(Gtin.upcEToUpcA("04252615"), "check digit")
        assertNull(Gtin.upcEToUpcA("94252614"), "number system 9 is not UPC-E")
    }

    @Test
    fun misreadsPlaceholdersAndJunkHaveNoGtin() {
        assertNull(Gtin.normalize("4006381333932"), "check digit")
        assertNull(Gtin.normalize("00000000000000"), "placeholder")
        assertNull(Gtin.normalize("00000000123457"), "too few significant digits")
        assertNull(Gtin.normalize("1234567890"), "10 digits is not a scanned form")
        assertNull(Gtin.normalize("1234567"), "too short")
        assertNull(Gtin.normalize("12ab5678"))
        assertNull(Gtin.normalize(""))
    }

    @Test
    fun sixDigitUpcEGetsItsNumberSystemAndCheckDigit() {
        assertEquals("04252614", Gtin.completeUpcE("425261"))
        assertEquals("04252614", Gtin.completeUpcE("04252614"), "already complete")
        assertEquals("abc", Gtin.completeUpcE("abc"))
    }

    @Test
    fun checkDigit() {
        assertEquals(1, Gtin.checkDigit("04900002891"))
        assertEquals(3, Gtin.checkDigit("301762042200"))
    }
}
