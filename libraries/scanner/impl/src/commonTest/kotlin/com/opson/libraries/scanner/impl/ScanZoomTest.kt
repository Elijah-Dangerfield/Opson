package com.opson.libraries.scanner.impl

import kotlin.test.Test
import kotlin.test.assertEquals

class ScanZoomTest {

    @Test
    fun aLensThatFocusesFarZoomsInSoTheCodeFillsTheFrameWhereItIsSharp() {
        // A lens that only focuses from 6 cm, with a 60 degree view: tan(30) = 0.577, so a 20 mm code
        // fills 80% of the width at 20 / 0.8 / 0.577 = 43.3 mm. Zooming 60 / 43.3 = 1.39 moves that to 6 cm.
        val zoom = scanZoom(baseZoom = 1.0, fieldOfViewDegrees = 60.0, minimumFocusMillimetres = 60.0, fill = 0.8, maxZoom = 10.0)

        assertEquals(1.386, zoom, 0.001)
    }

    @Test
    fun aLensThatFocusesCloseEnoughStaysAtItsOwnView() {
        assertEquals(1.0, scanZoom(baseZoom = 1.0, fieldOfViewDegrees = 60.0, minimumFocusMillimetres = 30.0, fill = 0.8, maxZoom = 10.0))
    }

    @Test
    fun aVirtualDeviceStartsFromTheWideLensAndNeverZoomsMoreThanTwiceIt() {
        // A Pro's triple camera: zoom 1 is the ultra wide, the wide lens takes over at 2, and focuses from 20 cm.
        val zoom = scanZoom(baseZoom = 2.0, fieldOfViewDegrees = 106.0, minimumFocusMillimetres = 200.0, fill = 0.78, maxZoom = 5.9)

        assertEquals(4.0, zoom)
    }

    @Test
    fun theZoomStaysBelowTheNextLens() {
        assertEquals(3.0, scanZoom(baseZoom = 2.0, fieldOfViewDegrees = 106.0, minimumFocusMillimetres = 200.0, fill = 0.78, maxZoom = 3.0))
    }

    @Test
    fun anUnknownFocusDistanceKeepsTheLensItsOwnView() {
        assertEquals(2.0, scanZoom(baseZoom = 2.0, fieldOfViewDegrees = 106.0, minimumFocusMillimetres = -1.0, fill = 0.78, maxZoom = 6.0))
    }
}
