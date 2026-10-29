@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.scanner

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * One line of text read off a photo. The box is in fractions of the image (0..1), origin top-left,
 * so it means the same thing whatever size the photo was. [confidence] is 0..1, and 1 when the
 * engine gives none.
 */
@HiddenFromObjC
data class RecognizedLine(
    val text: String,
    val confidence: Double,
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
)

/**
 * Every line [TextRecognizer] found, in reading order (top to bottom, then left to right, so a
 * label's "Energy" comes just before the "1046 kJ" beside it), and the image's size in pixels.
 * [engine] is "vision" or "mlkit", for the record sent with a submission.
 */
@HiddenFromObjC
data class RecognizedText(
    val lines: List<RecognizedLine>,
    val imageWidth: Int,
    val imageHeight: Int,
    val engine: String,
)

@HiddenFromObjC
sealed interface TextRecognition {
    data class Read(val text: RecognizedText) : TextRecognition

    /**
     * This device can't read text: no engine, or Play services has not downloaded the model yet.
     * The person types the label in instead.
     */
    data object Unavailable : TextRecognition

    /** The engine ran and gave up, or the JPEG could not be decoded. */
    data object Failed : TextRecognition
}

/**
 * Reads the text in a JPEG on the device, for reading a nutrition label off a photo of the pack.
 * Vision on iOS, ML Kit on Android: free, offline, and nothing leaves the phone. Nothing is logged
 * from the image or the text either, only that a read failed. Turning the lines into numbers is
 * someone else's job; this only says what is written where.
 *
 * Safe to call from any coroutine; the work happens off the main thread. Hidden from the iOS
 * header because Swift only implements the bridge underneath it, never this.
 */
@HiddenFromObjC
interface TextRecognizer {
    /** [jpeg] is upright, as [StillShot.jpeg] is. */
    suspend fun recognize(jpeg: ByteArray): TextRecognition
}
