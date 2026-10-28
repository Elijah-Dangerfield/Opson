package com.opson.libraries.pdf.impl

import com.opson.libraries.pdf.PdfBlock
import com.opson.libraries.pdf.PdfDocument
import com.opson.libraries.pdf.PdfStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A smoke test on the simulator: the same two-page document as the Android one. */
class IosPdfWriterTest {

    @Test
    fun writesAPdf_withAPagePerScreenful() {
        val blocks = listOf(PdfBlock("Food log summary", PdfStyle.Title), PdfBlock("Oct 1 to Oct 14", PdfStyle.Subtitle)) +
            List(60) { PdfBlock("Breakfast, 8:05 am: porridge with blueberries, coffee", indented = it % 2 == 1) }

        val bytes = IosPdfWriter().write(PdfDocument("Summary", blocks, footer = "Made with Opson"))

        val text = bytes.decodeToString()
        assertTrue(text.startsWith("%PDF-"), "a PDF header, not ${text.take(8)}")
        assertEquals(2, Regex("""/Type\s*/Page\b""").findAll(text).count(), "two pages")
    }
}
