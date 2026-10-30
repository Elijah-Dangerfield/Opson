package com.opson.libraries.scanner.impl

import com.opson.libraries.scanner.ScanArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScanGeometryTest {

    // A 1280x960 frame turned upright for a portrait phone, shown in a window 1000 wide and 870 tall.
    // Filling the width scales it by 1000 / 960, so the window shows all 960 columns and the middle
    // 835 rows. A scan area of 90% by 60% is then 864 columns by 501 rows around the middle (480, 640).
    private val area = ScanArea(width = 0.9f, height = 0.6f)

    private fun inside(x: Float, y: Float) =
        insideScanArea(x, y, imageWidth = 960, imageHeight = 1280, viewWidth = 1000, viewHeight = 870, area = area)

    @Test
    fun aCodeInTheMiddleIsRead() {
        assertTrue(inside(480f, 640f))
    }

    @Test
    fun aCodeNearTheEdgeOfTheAreaIsRead() {
        assertTrue(inside(60f, 640f))
        assertTrue(inside(480f, 640f + 240f))
    }

    @Test
    fun aCodeOnScreenButOutsideTheAreaIsDropped() {
        assertFalse(inside(30f, 640f), "past the side of the area")
        assertFalse(inside(480f, 640f + 300f), "below the area, still on screen")
    }

    @Test
    fun aCodeTheWindowCropsOffIsDropped() {
        // The window shows rows 222 to 1058 only: a pack at the top of the frame is not on screen.
        assertFalse(inside(480f, 100f))
    }

    @Test
    fun beforeTheViewHasASizeEveryCodeIsRead() {
        assertTrue(insideScanArea(0f, 0f, imageWidth = 960, imageHeight = 1280, viewWidth = 0, viewHeight = 0, area = area))
    }

    @Test
    fun aQuarterTurnSwapsTheFramesSides() {
        assertEquals(960 to 1280, uprightSize(1280, 960, rotationDegrees = 90))
        assertEquals(960 to 1280, uprightSize(1280, 960, rotationDegrees = 270))
        assertEquals(1280 to 960, uprightSize(1280, 960, rotationDegrees = 0))
        assertEquals(1280 to 960, uprightSize(1280, 960, rotationDegrees = 180))
    }
}
