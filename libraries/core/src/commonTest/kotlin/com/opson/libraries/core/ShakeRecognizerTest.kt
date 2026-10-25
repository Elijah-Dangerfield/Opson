package com.opson.libraries.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val GRAVITY = 9.80665

// ±7 m/s² on one axis is a 14 m/s² swing between consecutive samples: over the
// 12.0 bar, under the "vigorous" one.
private const val SHAKE_AMPLITUDE = 7.0

class ShakeRecognizerTest {

    private val recognizer = ShakeRecognizer()

    /**
     * One accelerometer sample of a phone being swung along x, alternating
     * direction with [index] so consecutive samples are a full swing apart.
     */
    private fun swing(index: Int, atMs: Long, amplitude: Double = SHAKE_AMPLITUDE): Boolean {
        val x = if (index % 2 == 0) -amplitude else amplitude
        return recognizer.onSample(x = x, y = 0.0, z = GRAVITY, atMs = atMs)
    }

    @Test
    fun firstSample_isOnlyABaseline() {
        assertFalse(recognizer.onSample(x = 0.0, y = 0.0, z = GRAVITY, atMs = 0))
    }

    @Test
    fun singleHardJolt_isNotAShake() {
        // A firm set-down on a table: one large change, then stillness. The
        // inherited tuning let two of these anywhere in the same second count.
        recognizer.onSample(x = 0.0, y = 0.0, z = GRAVITY, atMs = 0)
        assertFalse(recognizer.onSample(x = 0.0, y = 0.0, z = GRAVITY * 4, atMs = 100))
        assertFalse(recognizer.onSample(x = 0.0, y = 0.0, z = GRAVITY, atMs = 200))
        assertFalse(recognizer.onSample(x = 0.0, y = 0.0, z = GRAVITY, atMs = 300))
    }

    @Test
    fun twoJoltsASecondApart_isNotAShake() {
        recognizer.onSample(x = 0.0, y = 0.0, z = GRAVITY, atMs = 0)
        assertFalse(recognizer.onSample(x = 0.0, y = 0.0, z = GRAVITY * 4, atMs = 100))
        assertFalse(recognizer.onSample(x = 0.0, y = 0.0, z = GRAVITY, atMs = 200))
        assertFalse(recognizer.onSample(x = 0.0, y = 0.0, z = GRAVITY * 4, atMs = 900))
        assertFalse(recognizer.onSample(x = 0.0, y = 0.0, z = GRAVITY, atMs = 1000))
    }

    @Test
    fun sustainedReversals_fireOnTheFourthSample() {
        swing(index = 0, atMs = 0)
        assertFalse(swing(index = 1, atMs = 100))
        assertFalse(swing(index = 2, atMs = 200))
        assertFalse(swing(index = 3, atMs = 300))
        assertTrue(swing(index = 4, atMs = 400))
    }

    @Test
    fun burstWindow_doesNotStretchToCollectAStraggler() {
        // Three tight reversals and a fourth just outside the window. Widening
        // the window back out is what this pins: with a 1s window the straggler
        // joins the first three and this fires. The gap is deliberately short
        // enough that the burst does not simply reset under either value.
        swing(index = 0, atMs = 0)
        assertFalse(swing(index = 1, atMs = 100))
        assertFalse(swing(index = 2, atMs = 200))
        assertFalse(swing(index = 3, atMs = 300))
        assertFalse(swing(index = 4, atMs = 900))

        // And the proof that the assertion above is discriminating rather than
        // vacuous: the same samples through a recognizer with the window
        // widened back out do fire.
        val loose = ShakeRecognizer(burstWindowMs = 1000)
        val times = listOf(0L, 100L, 200L, 300L, 900L)
        val fired = times.mapIndexed { index, atMs ->
            val x = if (index % 2 == 0) -SHAKE_AMPLITUDE else SHAKE_AMPLITUDE
            loose.onSample(x = x, y = 0.0, z = GRAVITY, atMs = atMs)
        }
        assertTrue(fired.last())
    }

    @Test
    fun secondShake_waitsOutTheCooldown() {
        swing(index = 0, atMs = 0)
        repeat(3) { assertFalse(swing(index = it + 1, atMs = (it + 1) * 100L)) }
        assertTrue(swing(index = 4, atMs = 400))

        var index = 5
        var atMs = 500L
        while (atMs <= 1900) {
            assertFalse(swing(index = index, atMs = atMs), "fired during the cooldown at $atMs")
            index++
            atMs += 100
        }

        assertTrue(swing(index = index, atMs = atMs))
    }

    @Test
    fun samplesFasterThanTheInterval_doNotMoveTheBaseline() {
        // A 100Hz sensor must not read as a harder shake than a 20Hz one. The
        // dropped samples here are a full swing away; if they advanced the
        // baseline, every kept sample would show a zero delta and nothing would
        // ever qualify.
        swing(index = 0, atMs = 0)
        assertFalse(swing(index = 1, atMs = 50))
        assertFalse(swing(index = 1, atMs = 100))
        assertFalse(swing(index = 0, atMs = 150))
        assertFalse(swing(index = 0, atMs = 200))
        assertFalse(swing(index = 1, atMs = 250))
        assertFalse(swing(index = 1, atMs = 300))
        assertFalse(swing(index = 0, atMs = 350))
        assertTrue(swing(index = 0, atMs = 400))
    }

    @Test
    fun reset_makesTheNextSampleABaseline() {
        // The resume case: sensor delivery stops and restarts, and the first
        // sample back is a whole gravity vector away from the stale baseline.
        // That must not read as a shake the user never performed.
        swing(index = 0, atMs = 0)
        swing(index = 1, atMs = 100)
        swing(index = 2, atMs = 200)

        recognizer.reset()

        assertFalse(recognizer.onSample(x = 0.0, y = 0.0, z = GRAVITY, atMs = 60_000))
        assertFalse(recognizer.onSample(x = 0.0, y = 0.0, z = GRAVITY, atMs = 60_100))
    }

    @Test
    fun intensity_readsThePeakOfTheBurst() {
        swing(index = 0, atMs = 0)
        swing(index = 1, atMs = 100)
        swing(index = 2, atMs = 200)
        swing(index = 3, atMs = 300)
        assertTrue(swing(index = 4, atMs = 400))
        assertEquals(ShakeIntensity.GENTLE, recognizer.lastIntensity)

        val vigorous = ShakeRecognizer()
        val hard = 16.0
        vigorous.onSample(x = -hard, y = 0.0, z = GRAVITY, atMs = 0)
        repeat(4) {
            val x = if (it % 2 == 0) hard else -hard
            vigorous.onSample(x = x, y = 0.0, z = GRAVITY, atMs = (it + 1) * 100L)
        }
        assertEquals(ShakeIntensity.VIGOROUS, vigorous.lastIntensity)
    }
}
