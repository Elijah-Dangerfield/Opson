package com.opson.libraries.scanner.impl

import kotlin.math.log2

/**
 * How bright the scene is, as the exposure value at ISO 100 that the camera's
 * own auto exposure settled on: log2(N² / t) - log2(ISO / 100). A lit kitchen
 * is around 6 to 7, a dim room 3 to 4. When auto exposure has run out of
 * shutter and gain the real scene is darker still, so the estimate errs bright,
 * never dark. Null when the camera reports nothing usable.
 */
internal fun sceneEv100(aperture: Double, exposureSeconds: Double, iso: Double): Double? {
    if (aperture <= 0 || exposureSeconds <= 0 || iso <= 0) return null
    return log2(aperture * aperture / exposureSeconds) - log2(iso / ISO_BASE)
}

/**
 * Says when the scene has been too dark to read a barcode well for long enough
 * to suggest the light, and when it has come back. Two thresholds and a hold,
 * so a hand passing over the lens or a pack's shadow doesn't flicker the hint.
 * Not thread safe: feed it from one thread.
 */
internal class LowLightDetector(
    private val darkBelow: Double = DARK_BELOW_EV,
    private val lightAbove: Double = LIGHT_ABOVE_EV,
    private val holdMillis: Long = HOLD_MILLIS,
) {
    var isLow: Boolean = false
        private set
    private var since: Long? = null

    /** The latest estimate at [nowMillis]. Returns the new answer when it changes, null otherwise. */
    fun update(ev: Double, nowMillis: Long): Boolean? {
        val crossing = if (isLow) ev >= lightAbove else ev < darkBelow
        if (!crossing) {
            since = null
            return null
        }
        val started = since ?: nowMillis.also { since = it }
        if (nowMillis - started < holdMillis) return null
        isLow = !isLow
        since = null
        return isLow
    }
}

private const val ISO_BASE = 100.0

/**
 * Below this a phone camera is at or near its slowest shutter and highest
 * gain: frames are noisy and a moving pack blurs, which is what costs reads.
 */
internal const val DARK_BELOW_EV = 3.0

/** Clearly lit again. Apart from [DARK_BELOW_EV] so a room on the edge doesn't flip back and forth. */
internal const val LIGHT_ABOVE_EV = 4.5

/** How long the scene has to stay past a threshold before the answer changes. */
internal const val HOLD_MILLIS = 1_000L
