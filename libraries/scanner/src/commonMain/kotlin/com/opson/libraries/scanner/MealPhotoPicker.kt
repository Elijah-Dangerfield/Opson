package com.opson.libraries.scanner

import androidx.compose.runtime.Composable

/**
 * A photo of a meal, already shrunk and re-encoded on the device: a JPEG at
 * most [MAX_EDGE_PX] on its long edge, upright. It only ever lives in memory;
 * nothing here writes it anywhere the app keeps.
 *
 * Equality is by identity, on purpose: two photos are the same photo only if
 * they are the same object, and comparing megabytes on every state change
 * would be waste.
 */
class MealPhoto(val jpeg: ByteArray) {
    companion object {
        /**
         * The long edge the device scales to. Enough for a vision model to
         * tell rice from couscous; anything larger only costs upload time and
         * image tokens.
         */
        const val MAX_EDGE_PX = 1568

        /** JPEG quality for the re-encode. */
        const val JPEG_QUALITY = 0.8
    }
}

/** What came back from the camera or the photo picker. */
sealed interface PhotoPickResult {
    data class Picked(val photo: MealPhoto) : PhotoPickResult

    /** The person backed out of the camera or the picker. */
    data object Cancelled : PhotoPickResult

    /** The camera could not start, or the photo could not be read. */
    data object Failed : PhotoPickResult
}

/**
 * The system camera and the system photo picker, for Photo estimate. Both
 * are launched from composition (an activity result on Android, a presented
 * view controller on iOS), so the view model asks with an event and the
 * answer comes back through [onResult]. The library needs no permission on
 * either platform; the camera uses [CameraPermissions] and
 * [BarcodeCamera.rememberPermissionRequest] like the scanner.
 */
interface MealPhotoPicker {
    /** False where there is no camera to take one with (the iOS simulator, some tablets). */
    val canTakePhoto: Boolean

    @Composable
    fun rememberCamera(onResult: (PhotoPickResult) -> Unit): () -> Unit

    @Composable
    fun rememberLibrary(onResult: (PhotoPickResult) -> Unit): () -> Unit
}
