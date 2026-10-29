@file:OptIn(ExperimentalObjCName::class)

package com.opson.libraries.scanner

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * The Swift half of [TextRecognizer] on iOS: runs Vision's text request on a JPEG Kotlin has
 * already written. Implemented by `IOSTextRecognitionBridge` in the iOS app and passed into the DI
 * graph through `IosAppComponent`'s `create(...)`, like `FileSharePresenter`.
 *
 * Swift gets a path rather than the bytes because a Kotlin `ByteArray` crosses into Swift as
 * `KotlinByteArray`, which can only be copied out a byte at a time. Lines come back one call each
 * rather than as a list, so no Kotlin collection has to cross either.
 */
@ObjCName("TextRecognitionBridge", exact = true)
interface TextRecognitionBridge {
    /**
     * Reads the text in the JPEG at [imagePath] (in the app's temporary directory). Calls
     * [callback] once per line, then [TextRecognitionCallback.finished] exactly once, on any
     * thread. Must not block the caller.
     */
    fun recognize(imagePath: String, callback: TextRecognitionCallback)
}

/** Where [TextRecognitionBridge] reports. Kotlin implements it; Swift only calls it. */
@ObjCName("TextRecognitionCallback", exact = true)
interface TextRecognitionCallback {
    /**
     * One line. The box is in fractions of the image, origin top-left: Swift converts from
     * Vision's bottom-left. [confidence] is 0..1.
     */
    fun line(text: String, confidence: Double, left: Double, top: Double, width: Double, height: Double)

    /**
     * The end of the read, with the image's size in pixels. [error] is null on success,
     * "unavailable" when the device can't run text recognition, anything else for a failure.
     */
    fun finished(imageWidth: Int, imageHeight: Int, error: String?)
}
