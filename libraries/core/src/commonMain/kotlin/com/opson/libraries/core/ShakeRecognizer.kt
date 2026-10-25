package com.opson.libraries.core

import kotlin.math.sqrt

/**
 * The shake gesture itself, with no sensor API anywhere near it.
 *
 * Platform detectors used to each carry their own copy of this logic, and the
 * copies drifted: one divided the delta by elapsed time and measured m/s², the
 * other measured raw multiples of gravity and never divided, so the same
 * physical shake needed roughly twice the effort on one platform. Neither could
 * be unit tested, because a test cannot construct a `SensorEvent` or a
 * `CMAccelerometerData` — which is why a baseline bug in this logic survived as
 * long as it did. Feed it plain numbers instead, and the rules become assertions.
 *
 * Callers hand it accelerometer samples in **m/s², including gravity** — the
 * unit conversion is the platform's job. It tracks the change between
 * consecutive samples, so a constant gravity vector cancels out and only
 * movement counts.
 *
 * ### Why the thresholds are what they are
 *
 * The inherited tuning was two qualifying samples anywhere inside a full
 * second, over a bar of 8.0 m/s² of change. Both halves were too loose. 8.0 is
 * under 1g of *change*, which a firm set-down on a table clears on its own, and
 * giving two of them a whole second to find each other means any two unrelated
 * bumps in the same second read as a shake.
 *
 * [REQUIRED_SAMPLES] qualifying samples inside [BURST_WINDOW_MS] asks for
 * roughly 6Hz of sustained direction reversal — what a hand shaking a phone
 * does, and what nothing else in ordinary handling does. Widening the window or
 * dropping the count brings the phantom dialogs back.
 */
class ShakeRecognizer(
    private val sampleIntervalMs: Long = SAMPLE_INTERVAL_MS,
    private val deltaThreshold: Double = DELTA_THRESHOLD,
    private val requiredSamples: Int = REQUIRED_SAMPLES,
    private val burstWindowMs: Long = BURST_WINDOW_MS,
    private val cooldownMs: Long = COOLDOWN_MS,
) {
    private var hasBaseline = false
    private var lastX = 0.0
    private var lastY = 0.0
    private var lastZ = 0.0
    private var lastSampleAtMs = 0L
    private var lastShakeAtMs: Long? = null
    private var peakDelta = 0.0
    private val burst = ArrayDeque<Long>()

    /**
     * The intensity of the most recent shake [onSample] returned true for.
     * Meaningless before the first one; read it only in response to a true.
     */
    var lastIntensity: ShakeIntensity = ShakeIntensity.NORMAL
        private set

    /**
     * Feeds one accelerometer sample. Returns true exactly on the sample that
     * completes a shake.
     *
     * @param x acceleration along the device x axis, m/s², gravity included
     * @param y acceleration along the device y axis, m/s², gravity included
     * @param z acceleration along the device z axis, m/s², gravity included
     * @param atMs monotonic-enough wall clock for this sample
     */
    fun onSample(x: Double, y: Double, z: Double, atMs: Long): Boolean {
        if (!hasBaseline) {
            takeBaseline(x, y, z, atMs)
            return false
        }

        // Downsample to a fixed cadence so the gesture means the same thing on
        // a 20Hz sensor and a 100Hz one. Dropped samples must not move the
        // baseline, or the deltas shrink with the sensor's rate.
        if (atMs - lastSampleAtMs < sampleIntervalMs) return false

        val delta = sqrt(
            (x - lastX) * (x - lastX) +
                (y - lastY) * (y - lastY) +
                (z - lastZ) * (z - lastZ),
        )
        takeBaseline(x, y, z, atMs)

        if (delta <= deltaThreshold) return false

        while (burst.isNotEmpty() && atMs - burst.first() > burstWindowMs) {
            burst.removeFirst()
        }
        burst.addLast(atMs)
        peakDelta = maxOf(peakDelta, delta)

        if (burst.size < requiredSamples) return false

        val sinceLastShake = lastShakeAtMs?.let { atMs - it }
        if (sinceLastShake != null && sinceLastShake <= cooldownMs) return false

        lastShakeAtMs = atMs
        lastIntensity = intensityOf(peakDelta)
        burst.clear()
        peakDelta = 0.0
        return true
    }

    /**
     * Drops the baseline and any partial burst. Call this whenever sensor
     * delivery stops and restarts: the first sample after a gap is a full
     * gravity vector away from a stale baseline, which reads as a violent
     * shake the user never performed.
     */
    fun reset() {
        hasBaseline = false
        burst.clear()
        peakDelta = 0.0
        lastShakeAtMs = null
    }

    private fun takeBaseline(x: Double, y: Double, z: Double, atMs: Long) {
        lastX = x
        lastY = y
        lastZ = z
        lastSampleAtMs = atMs
        hasBaseline = true
    }

    private fun intensityOf(delta: Double): ShakeIntensity = when {
        delta > deltaThreshold * VIGOROUS_MULTIPLE -> ShakeIntensity.VIGOROUS
        delta > deltaThreshold * NORMAL_MULTIPLE -> ShakeIntensity.NORMAL
        else -> ShakeIntensity.GENTLE
    }

    companion object {
        const val SAMPLE_INTERVAL_MS = 100L
        const val DELTA_THRESHOLD = 12.0
        const val REQUIRED_SAMPLES = 4
        const val BURST_WINDOW_MS = 700L
        const val COOLDOWN_MS = 1500L

        private const val NORMAL_MULTIPLE = 1.5
        private const val VIGOROUS_MULTIPLE = 2.5
    }
}
