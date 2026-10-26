package com.opson.libraries.scanner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Which camera. */
enum class CameraFacing { Front, Back }

/**
 * One still from [StillCamera]: an upright JPEG at most [MAX_EDGE_PX] on its
 * long edge, and a small copy for grids. A front-camera still is mirrored the
 * way its preview was, so what the person lined up is what they get.
 * Equality is by identity, as with [MealPhoto].
 */
class StillShot(val jpeg: ByteArray, val thumbnail: ByteArray) {
    companion object {
        const val MAX_EDGE_PX = 1600
        const val THUMBNAIL_EDGE_PX = 480
        const val JPEG_QUALITY = 0.85
    }
}

sealed interface StillResult {
    data class Taken(val shot: StillShot) : StillResult

    /** The camera did not take it, or the result could not be read. */
    data object Failed : StillResult
}

/**
 * A live camera inside the app with a shutter, for progress photos. In-app on
 * purpose: the system camera app may keep its own copy in the gallery, and it
 * cannot draw a guide over its preview. Nothing here writes anywhere; the
 * caller decides what to keep. Permission is [CameraPermissions] and
 * [BarcodeCamera.rememberPermissionRequest], as for the scanner.
 */
interface StillCamera {
    /**
     * The preview. Each time [shutter] goes up by one, it takes a still and
     * answers through [onResult] on the main thread. [onUnavailable] as in
     * [BarcodeCamera.Viewfinder]. Changing [facing] restarts the camera.
     */
    @Composable
    fun Viewfinder(
        facing: CameraFacing,
        shutter: Int,
        onResult: (StillResult) -> Unit,
        onUnavailable: () -> Unit,
        modifier: Modifier,
    )
}
