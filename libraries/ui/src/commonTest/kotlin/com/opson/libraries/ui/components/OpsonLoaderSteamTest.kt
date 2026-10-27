package com.opson.libraries.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The steam matches the brand export's loader frames (24 at 15 fps): a curl starts 8 units low and
 * invisible, passes its resting place fully opaque half way through, and ends 8 units high and
 * invisible again, so the loop has no seam.
 */
class OpsonLoaderSteamTest {

    @Test
    fun aCurlRisesSixteenUnitsThroughItsRestingPlace() {
        assertEquals(8f, steamRise(0f), Tolerance)
        assertEquals(0f, steamRise(0.5f), Tolerance)
        assertEquals(-8f, steamRise(1f), Tolerance)
    }

    @Test
    fun theRiseEasesInAndOutAsTheFramesDo() {
        // Frame 6 of 24: the curl's top measured 5.8 units below rest.
        assertEquals(5.66f, steamRise(0.25f), 0.01f)
    }

    @Test
    fun aCurlFadesInOverTheFirstThirtyPercentAndOutOverTheLast() {
        assertEquals(0f, steamAlpha(0f), Tolerance)
        assertEquals(0.5f, steamAlpha(0.15f), Tolerance)
        assertEquals(1f, steamAlpha(0.3f), Tolerance)
        assertEquals(1f, steamAlpha(0.5f), Tolerance)
        assertEquals(0.5f, steamAlpha(0.85f), Tolerance)
        assertEquals(0f, steamAlpha(1f), Tolerance)
    }
}

private const val Tolerance = 0.0001f
