@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.scanner.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.scanner.RecognizedLine
import com.opson.libraries.scanner.RecognizedText
import com.opson.libraries.scanner.TextRecognition
import com.opson.libraries.scanner.TextRecognitionBridge
import com.opson.libraries.scanner.TextRecognitionCallback
import com.opson.libraries.scanner.TextRecognizer
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSLock
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToFile
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.coroutines.resume
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Writes the JPEG to `tmp/` under a name of its own and asks the Swift [TextRecognitionBridge]
 * (Vision) to read it. The file is gone again as soon as the read ends, read or not. Hidden from
 * the header like [TextRecognizer]; Swift only sees the bridge.
 */
@HiddenFromObjC
@ContributesBinding(AppScope::class)
class IosTextRecognizer @Inject constructor(
    private val bridge: TextRecognitionBridge,
) : TextRecognizer {

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun recognize(jpeg: ByteArray): TextRecognition = withContext(Dispatchers.Default) {
        if (jpeg.isEmpty()) return@withContext TextRecognition.Failed
        val path = NSTemporaryDirectory().trimEnd('/') + "/label-${NSUUID().UUIDString}.jpg"
        try {
            val written = Catching { write(jpeg, path) }
                .logOnFailure("Could not write the photo for text recognition")
                .getOrDefault(false)
            if (!written) return@withContext TextRecognition.Failed
            suspendCancellableCoroutine<TextRecognition> { continuation ->
                bridge.recognize(path, Collector(continuation))
            }
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        }
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun write(jpeg: ByteArray, path: String): Boolean {
        val data = jpeg.usePinned { NSData.create(bytes = it.addressOf(0), length = jpeg.size.toULong()) }
        return data.writeToFile(path, atomically = true)
    }

    /**
     * Gathers the lines Swift reports and answers once, at [finished]. Vision calls back on its
     * own queue, so the list sits behind a lock, and a second [finished] (a bridge breaking its
     * contract) is ignored rather than resuming twice.
     */
    private class Collector(
        private val continuation: CancellableContinuation<TextRecognition>,
    ) : TextRecognitionCallback {
        private val lock = NSLock()
        private val lines = mutableListOf<RecognizedLine>()
        private var done = false

        override fun line(text: String, confidence: Double, left: Double, top: Double, width: Double, height: Double) {
            val line = RecognizedLine(text, confidence.coerceIn(0.0, 1.0), left, top, width, height)
            locked { if (!done) lines += line }
        }

        override fun finished(imageWidth: Int, imageHeight: Int, error: String?) {
            val read = locked {
                if (done) return
                done = true
                lines.toList()
            }
            val result = when (error) {
                null -> TextRecognition.Read(
                    RecognizedText(read.inReadingOrder(), imageWidth, imageHeight, engine = "vision"),
                )
                UNAVAILABLE -> TextRecognition.Unavailable
                else -> TextRecognition.Failed
            }
            if (continuation.isActive) continuation.resume(result)
        }

        private inline fun <T> locked(block: () -> T): T {
            lock.lock()
            try {
                return block()
            } finally {
                lock.unlock()
            }
        }
    }

    private companion object {
        /** The [TextRecognitionCallback.finished] error for a device that can't run Vision's text request. */
        const val UNAVAILABLE = "unavailable"
    }
}
