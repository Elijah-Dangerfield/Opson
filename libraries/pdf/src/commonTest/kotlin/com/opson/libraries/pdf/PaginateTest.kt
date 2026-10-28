package com.opson.libraries.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class PaginateTest {

    private val line = 20f

    @Test
    fun blocksStackDownThePage_withTheirStylesGap() {
        val blocks = listOf(PdfBlock("Title", PdfStyle.Title), PdfBlock("Day", PdfStyle.Heading), PdfBlock("Breakfast"))

        val pages = paginate(blocks) { line }

        assertEquals(1, pages.size)
        assertEquals(
            listOf(
                Placed(0, PdfPage.MARGIN),
                Placed(1, PdfPage.MARGIN + line + PdfStyle.Heading.spaceBefore),
                Placed(2, PdfPage.MARGIN + 2 * line + PdfStyle.Heading.spaceBefore + PdfStyle.Body.spaceBefore),
            ),
            pages.single(),
        )
    }

    @Test
    fun aBlockThatWouldCrossTheFooter_startsTheNextPage_withNoGapAbove() {
        val perPage = ((PdfPage.TEXT_BOTTOM - PdfPage.MARGIN) / (line + PdfStyle.Body.spaceBefore)).toInt()
        val blocks = List(perPage + 3) { PdfBlock("Line $it") }

        val pages = paginate(blocks) { line }

        assertEquals(2, pages.size)
        assertEquals(blocks.indices.toList(), pages.flatten().map { it.index }, "every block once, in order")
        assertEquals(PdfPage.MARGIN, pages[1].first().top)
        pages.flatten().forEach { assert(it.top + line <= PdfPage.TEXT_BOTTOM) { "block ${it.index} runs into the footer" } }
    }

    @Test
    fun anEmptyDocument_isOneBlankPage() {
        assertEquals(listOf(emptyList()), paginate(emptyList()) { line })
    }
}
