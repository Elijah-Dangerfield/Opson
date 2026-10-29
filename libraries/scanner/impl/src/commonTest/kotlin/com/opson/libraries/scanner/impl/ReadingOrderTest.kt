package com.opson.libraries.scanner.impl

import com.opson.libraries.scanner.RecognizedLine
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingOrderTest {

    private fun line(text: String, left: Double, top: Double, height: Double = 0.04) =
        RecognizedLine(text, confidence = 1.0, left = left, top = top, width = 0.2, height = height)

    @Test
    fun aNumberSittingSlightlyHigherThanItsNameStillReadsAfterIt() {
        val lines = listOf(
            line("1046 kJ", left = 0.6, top = 0.198),
            line("Energy", left = 0.1, top = 0.2),
            line("Fat", left = 0.1, top = 0.26),
            line("3.1 g", left = 0.6, top = 0.259),
        )

        assertEquals(listOf("Energy", "1046 kJ", "Fat", "3.1 g"), lines.inReadingOrder().map { it.text })
    }

    @Test
    fun linesMoreThanHalfALineApartAreSeparateRows() {
        val lines = listOf(
            line("of which saturates", left = 0.3, top = 0.33),
            line("Fat", left = 0.1, top = 0.30),
        )

        assertEquals(listOf("Fat", "of which saturates"), lines.inReadingOrder().map { it.text })
    }

    @Test
    fun aSlantedLabelDoesNotChainIntoOneRow() {
        // Each step down is under half a line, but the last is a whole line below the first.
        val lines = listOf(
            line("c", left = 0.1, top = 0.23),
            line("b", left = 0.5, top = 0.215),
            line("a", left = 0.9, top = 0.20),
        )

        assertEquals(listOf("b", "a", "c"), lines.inReadingOrder().map { it.text })
    }
}
