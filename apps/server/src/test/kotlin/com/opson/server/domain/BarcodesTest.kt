package com.opson.server.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BarcodesTest {

    @Test
    fun aUpcAAndItsEan13AndGtin14FormsAreEquivalent() {
        val forms = Barcodes.equivalents("049000028911")
        assertEquals("049000028911", forms.first(), "the code as scanned comes first")
        assertTrue(forms.containsAll(listOf("0049000028911", "00049000028911")))
        assertTrue(Barcodes.equivalents("00049000028911").contains("049000028911"))
    }

    @Test
    fun anEightDigitCodeIsTriedAsEan8AndAsUpcE() {
        val forms = Barcodes.equivalents("04252614")
        assertTrue("00000004252614" in forms, "EAN-8, padded")
        assertTrue("00042100005264" in forms, "UPC-E expanded to its UPC-A")
    }

    @Test
    fun upcEExpansionChecksTheCheckDigit() {
        assertEquals("042100005264", Barcodes.upcEToUpcA("04252614"))
        assertNull(Barcodes.upcEToUpcA("04252615"))
        assertNull(Barcodes.upcEToUpcA("94252614"))
    }

    @Test
    fun checkDigit() {
        assertEquals(1, Barcodes.checkDigit("04900002891"))
        assertEquals(3, Barcodes.checkDigit("301762042200"))
    }

    @Test
    fun gtin14PadsEveryValidFormToFourteenDigits() {
        assertEquals("00049000028911", Barcodes.gtin14("049000028911"), "UPC-A")
        assertEquals("00049000028911", Barcodes.gtin14("0049000028911"), "its EAN-13")
        assertEquals("00049000028911", Barcodes.gtin14("00049000028911"), "its GTIN-14")
        assertEquals("04006381333931", Barcodes.gtin14("4006381333931"), "EAN-13")
        assertEquals("00000096385074", Barcodes.gtin14("96385074"), "EAN-8 that is not a UPC-E")
    }

    @Test
    fun gtin14ReadsAnEightDigitCodeAsUpcEFirst() {
        // As scripts/import_off_foods.py: an 8-digit code that expands to a valid UPC-A is that UPC-A.
        assertEquals("00042100005264", Barcodes.gtin14("04252614"))
    }

    @Test
    fun gtin14RejectsBadCheckDigitsPlaceholdersAndJunk() {
        assertNull(Barcodes.gtin14("4006381333932"), "check digit")
        assertNull(Barcodes.gtin14("00000000000000"), "placeholder")
        assertNull(Barcodes.gtin14("00000000123457"), "too few significant digits")
        assertNull(Barcodes.gtin14("1234567890"), "10 digits is not a scanned GTIN form")
        assertNull(Barcodes.gtin14("12ab5678"))
    }

    @Test
    fun anInvalidCodeIsOnlyItself() {
        assertEquals(listOf("12ab"), Barcodes.equivalents("12ab"))
    }
}
