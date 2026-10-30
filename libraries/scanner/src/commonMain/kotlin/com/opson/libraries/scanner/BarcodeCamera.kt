package com.opson.libraries.scanner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The retail symbologies food packs carry. Nothing else is asked for, which keeps reads fast and false reads rare. */
enum class BarcodeFormat { Ean13, Ean8, UpcA, UpcE }

/** One read from the camera: the digits as decoded, and the symbology. */
data class ScannedCode(val value: String, val format: BarcodeFormat)

/** Where the camera permission stands. */
sealed interface CameraPermission {
    data object Granted : CameraPermission

    /** Not decided yet, so the system prompt can still be shown. */
    data object NotAsked : CameraPermission

    /** Refused. [canAskAgain] is false once only Settings can change it, which on iOS is after the first answer. */
    data class Denied(val canAskAgain: Boolean) : CameraPermission
}

/** The camera permission, read and changed outside composition, so a view model can own the flow. */
interface CameraPermissions {
    fun current(): CameraPermission

    /** The app's page in the system Settings, where a refused camera is turned back on. */
    fun openSettings()
}

/**
 * The part of the viewfinder the decoder looks at, centred, as fractions of
 * its width and height. Matching it to the frame drawn on screen means the
 * decoder searches less of each frame and ignores a neighbouring pack's code.
 */
data class ScanArea(val width: Float, val height: Float) {
    init {
        require(width in 0f..1f && height in 0f..1f) { "A scan area is a fraction of the viewfinder" }
    }
}

/**
 * A tap on the viewfinder: focus and meter there. [x] and [y] are fractions
 * of the viewfinder's width and height. A plain class, not a data class, so
 * each tap is a new request even on the same spot.
 */
class FocusPoint(val x: Float, val y: Float)

/**
 * The parts of scanning that have to live in composition: the live preview,
 * and the system permission prompt (an activity result on Android). Bound per
 * platform in `:libraries:scanner:impl`; previews and tests never touch it,
 * they pass a placeholder for the viewfinder.
 */
interface BarcodeCamera {
    /**
     * The live camera, reading EAN-13, EAN-8, UPC-A and UPC-E inside
     * [scanArea]. Calls [onCode] on the main thread for every read, many times
     * a second for the same pack; the caller decides which to keep. With more
     * than one code in the area, the one nearest the middle is read. Calls
     * [onUnavailable] on the main thread when the camera cannot run even
     * though it is allowed: no back camera (the iOS simulator), a failed
     * start, or another app holding it. The camera stops when this leaves
     * composition, and composing it again tries afresh.
     *
     * [onTorchAvailable] says, once the camera is up, whether it has a torch
     * for [torchOn] to light. A new [focusPoint] focuses and meters there, and
     * keeps focusing there as the pack moves.
     *
     * [onLowLight] says, on the main thread, when the scene has stayed too dark
     * to read well for a moment (true) and when it is lit again (false). It is
     * judged from the camera's own exposure, so a lit torch counts as light.
     */
    @Composable
    fun Viewfinder(
        onCode: (ScannedCode) -> Unit,
        onUnavailable: () -> Unit,
        scanArea: ScanArea,
        torchOn: Boolean,
        onTorchAvailable: (Boolean) -> Unit,
        onLowLight: (Boolean) -> Unit,
        focusPoint: FocusPoint?,
        modifier: Modifier,
    )

    /** A function that shows the system camera prompt; [onAnswer] gets the result. */
    @Composable
    fun rememberPermissionRequest(onAnswer: (CameraPermission) -> Unit): () -> Unit
}
