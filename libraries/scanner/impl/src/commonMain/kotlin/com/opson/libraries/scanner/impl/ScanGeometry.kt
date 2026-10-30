package com.opson.libraries.scanner.impl

import com.opson.libraries.scanner.ScanArea
import kotlin.math.abs
import kotlin.math.max

/**
 * Whether a code centred at ([x], [y]) in an upright analysis frame of
 * [imageWidth] by [imageHeight] lies inside [area] of a viewfinder
 * [viewWidth] by [viewHeight] that shows the same frame scaled to fill and
 * centred. That is how CameraX's `FILL_CENTER` draws a preview with the
 * analysis frame's aspect ratio, so the two cover the same part of the
 * sensor. Both the visible part and the scan area are centred, so only the
 * distance from the middle matters.
 *
 * True when the view has no size yet: better to read a code than to drop
 * every one while the layout settles.
 */
internal fun insideScanArea(
    x: Float,
    y: Float,
    imageWidth: Int,
    imageHeight: Int,
    viewWidth: Int,
    viewHeight: Int,
    area: ScanArea,
): Boolean {
    if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) return true
    val scale = max(viewWidth.toFloat() / imageWidth, viewHeight.toFloat() / imageHeight)
    val halfWidth = viewWidth / scale * area.width / 2
    val halfHeight = viewHeight / scale * area.height / 2
    return abs(x - imageWidth / 2f) <= halfWidth && abs(y - imageHeight / 2f) <= halfHeight
}

/** An analysis frame as the decoder sees it: turned upright, so a quarter turn swaps its sides. */
internal fun uprightSize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> =
    if (rotationDegrees % 180 == 0) width to height else height to width
