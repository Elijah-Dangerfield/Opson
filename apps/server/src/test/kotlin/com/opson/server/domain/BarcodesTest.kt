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
    fun anInvalidCodeIsOnlyItself() {
        assertEquals(listOf("12ab"), Barcodes.equivalents("12ab"))
    }
}
