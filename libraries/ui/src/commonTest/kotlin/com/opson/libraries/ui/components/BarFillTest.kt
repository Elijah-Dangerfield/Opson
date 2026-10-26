package com.opson.libraries.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/** Design v3: bars stop at 100%. Over a target is a full bar, not a longer or recoloured one. */
class BarFillTest {

    @Test
    fun aBiggerDayIsAFullBar() {
        assertEquals(1f, barFill(1.4f))
        assertEquals(1f, barFill(Float.POSITIVE_INFINITY))
    }

    @Test
    fun belowZeroAndNaNAreEmpty() {
        assertEquals(0f, barFill(-0.2f))
        assertEquals(0f, barFill(Float.NaN))
        assertEquals(0f, barFill(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun inRangePassesThrough() {
        assertEquals(0.62f, barFill(0.62f))
    }

    @Test
    fun ringSegmentsNeverAddUpPastTheCircle() {
        val segments = RingSegments.split(1.3f, listOf(1f to ColorResourceForTest, 3f to ColorResourceForTest))
        assertEquals(1f, segments.sumOf { it.fraction.toDouble() }.toFloat(), 0.0001f)
    }
}

private val ColorResourceForTest = com.opson.libraries.ui.system.color.ColorResource.White
