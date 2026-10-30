package com.opson.libraries.scanner.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanLightTest {

    @Test
    fun exposureValueFollowsApertureShutterAndGain() {
        // f/2 for a quarter second at ISO 100: log2(4 / 0.25) = 4.
        assertEquals(4.0, sceneEv100(aperture = 2.0, exposureSeconds = 0.25, iso = 100.0)!!, 1e-9)
        // Four times the gain for the same picture means a scene two stops darker.
        assertEquals(2.0, sceneEv100(aperture = 2.0, exposureSeconds = 0.25, iso = 400.0)!!, 1e-9)
    }

    @Test
    fun aPhoneInALitKitchenAndInADarkRoom() {
        // f/1.8 at 1/60 s and ISO 200: about 6.6. At 1/30 s and ISO 2000: about 2.3.
        assertEquals(6.6, sceneEv100(aperture = 1.8, exposureSeconds = 1.0 / 60, iso = 200.0)!!, 0.05)
        assertEquals(2.3, sceneEv100(aperture = 1.8, exposureSeconds = 1.0 / 30, iso = 2000.0)!!, 0.05)
    }

    @Test
    fun aCameraThatReportsNothingUsableHasNoEstimate() {
        assertNull(sceneEv100(aperture = 0.0, exposureSeconds = 0.01, iso = 100.0))
        assertNull(sceneEv100(aperture = 1.8, exposureSeconds = 0.0, iso = 100.0))
        assertNull(sceneEv100(aperture = 1.8, exposureSeconds = 0.01, iso = 0.0))
    }

    @Test
    fun darkOnlyAfterStayingDarkForTheHold() {
        val detector = LowLightDetector(darkBelow = 3.0, lightAbove = 4.5, holdMillis = 1000)

        assertNull(detector.update(2.0, nowMillis = 0))
        assertNull(detector.update(2.0, nowMillis = 900))
        assertEquals(true, detector.update(2.0, nowMillis = 1000))
        assertTrue(detector.isLow)
        assertNull(detector.update(2.0, nowMillis = 1500), "says it once")
    }

    @Test
    fun aMomentOfShadowDoesNotCount() {
        val detector = LowLightDetector(darkBelow = 3.0, lightAbove = 4.5, holdMillis = 1000)

        detector.update(2.0, nowMillis = 0)
        detector.update(6.0, nowMillis = 500)
        assertNull(detector.update(2.0, nowMillis = 1000), "the hold starts again")
        assertFalse(detector.isLow)
    }

    @Test
    fun litAgainOnlyClearlyAboveTheDarkLine() {
        val detector = LowLightDetector(darkBelow = 3.0, lightAbove = 4.5, holdMillis = 1000)
        detector.update(2.0, nowMillis = 0)
        detector.update(2.0, nowMillis = 1000)

        assertNull(detector.update(4.0, nowMillis = 2000), "between the lines stays dark")
        assertNull(detector.update(4.0, nowMillis = 4000))
        assertNull(detector.update(5.0, nowMillis = 5000))
        assertEquals(false, detector.update(5.0, nowMillis = 6000))
    }
}
