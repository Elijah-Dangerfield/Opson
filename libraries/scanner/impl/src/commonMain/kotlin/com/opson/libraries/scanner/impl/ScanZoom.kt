package com.opson.libraries.scanner.impl

import kotlin.math.PI
import kotlin.math.tan

/**
 * The zoom to scan at, after Apple's AVCamBarcode sample: zoom in until a
 * small code fills the scan area at a distance the camera can still focus.
 * Without it, someone fills the frame by bringing the phone closer than the
 * lens can focus (about 20 cm on a Pro's main camera) and the code stays
 * blurred however long it is held there.
 *
 * [baseZoom] is the zoom at which the lens being used shows its own field of
 * view: 1 for a single camera, the wide lens's switch-over for a virtual
 * device that starts at the ultra wide. [fieldOfViewDegrees] is the device's
 * horizontal field of view at zoom 1. [minimumFocusMillimetres] is the lens's
 * closest focus, or a negative number when iOS does not know it. [fill] is
 * the share of the preview's width the scan area takes.
 *
 * The extra zoom is capped at [MAX_EXTRA_ZOOM], so the preview never looks
 * like a close-up, and the result at [maxZoom], below the next lens (a
 * virtual device would hop to the telephoto, which focuses further still).
 */
internal fun scanZoom(
    baseZoom: Double,
    fieldOfViewDegrees: Double,
    minimumFocusMillimetres: Double,
    fill: Double,
    maxZoom: Double,
): Double {
    val base = baseZoom.coerceAtLeast(1.0)
    if (minimumFocusMillimetres <= 0 || fieldOfViewDegrees <= 0 || fill <= 0) return base.coerceAtMost(maxZoom)
    val halfAngle = fieldOfViewDegrees / 2 * PI / 180
    // The lens's own field of view: the device's narrowed by the zoom that reaches it.
    val halfWidthPerMillimetre = tan(halfAngle) / base
    val closestThatFills = MIN_CODE_MILLIMETRES / fill / halfWidthPerMillimetre
    val extra = (minimumFocusMillimetres / closestThatFills).coerceIn(1.0, MAX_EXTRA_ZOOM)
    return (base * extra).coerceAtMost(maxZoom).coerceAtLeast(1.0)
}

/** The smallest code to plan for: Apple's sample uses 20 mm, about a short EAN-8. */
internal const val MIN_CODE_MILLIMETRES = 20.0

/** Twice the lens's own view at most, so the preview still reads as the pack in hand. */
internal const val MAX_EXTRA_ZOOM = 2.0
