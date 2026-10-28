package com.opson.libraries.pdf.impl

import com.opson.libraries.pdf.PdfBlock
import com.opson.libraries.pdf.PdfDocument
import com.opson.libraries.pdf.PdfPage
import com.opson.libraries.pdf.PdfStyle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Android's half of the smoke test, as far as the host JVM reaches: the blocks measured by real
 * `StaticLayout`s (Robolectric's native graphics), wrapped and placed on pages. Robolectric
 * cannot open an `android.graphics.pdf.PdfDocument` (it is native, and throws "document is
 * closed"), so the bytes themselves are `IosPdfWriterTest` on the simulator and a look on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidPdfWriterTest {

    private val writer = AndroidPdfWriter()

    @Test
    fun aLongDocument_wrapsAndRunsOntoASecondPage() {
        val long = "Breakfast, 8:05 AM: porridge with blueberries and a spoon of honey, coffee with oat milk, " +
            "toast with butter and a boiled egg, half a grapefruit"
        val blocks = listOf(PdfBlock("Food log summary", PdfStyle.Title), PdfBlock("Oct 1 to 14", PdfStyle.Subtitle)) +
            List(40) { PdfBlock(long, indented = it % 2 == 1) }

        val pages = writer.pages(PdfDocument("Summary", blocks, footer = "Made with Opson"))

        assertTrue(pages.size > 1, "40 long paragraphs run past one page")
        assertEquals(blocks.indices.toList(), pages.flatten().map { it.index })
        val first = pages[0]
        assertTrue(first[2].top - first[1].top < first[3].top - first[2].top, "a long paragraph wraps to more than one line")
        assertTrue(pages.flatten().all { it.top < PdfPage.TEXT_BOTTOM })
    }
}
