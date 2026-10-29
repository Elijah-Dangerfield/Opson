package com.opson.libraries.scanner.impl

import com.opson.libraries.scanner.RecognizedLine
import kotlin.math.abs
import kotlin.math.min

/**
 * Top to bottom, then left to right. Neither engine promises an order, and sorting on `top` alone
 * would put "1046 kJ" above "Energy" whenever the number sits a pixel higher. So lines whose
 * middles are within half a line of each other count as one row, and a row reads left to right.
 * Each line is compared with the first line of the row, so a slanted photo can't chain the whole
 * label into one row.
 */
internal fun List<RecognizedLine>.inReadingOrder(): List<RecognizedLine> {
    val rows = mutableListOf<MutableList<RecognizedLine>>()
    for (line in sortedBy { it.middle }) {
        val row = rows.lastOrNull()
        if (row != null && row.first().sharesRowWith(line)) row += line else rows += mutableListOf(line)
    }
    return rows.flatMap { row -> row.sortedBy { it.left } }
}

private val RecognizedLine.middle: Double get() = top + height / 2

private fun RecognizedLine.sharesRowWith(other: RecognizedLine): Boolean =
    abs(middle - other.middle) < min(height, other.height) / 2
