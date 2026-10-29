package com.opson.libraries.scanner.impl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition as MlKitTextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.scanner.RecognizedLine
import com.opson.libraries.scanner.RecognizedText
import com.opson.libraries.scanner.TextRecognition
import com.opson.libraries.scanner.TextRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit's Latin text recognizer, the Play services build: the model is shared with other apps
 * and downloaded at install (the `com.google.mlkit.vision.DEPENDENCIES` meta-data in this module's
 * manifest), so it costs the APK almost nothing. Until that download lands ML Kit answers
 * UNAVAILABLE, which is [TextRecognition.Unavailable] here and typing the label in for the person.
 *
 * A recognizer per read, closed after it: a label read happens a few times a week, and holding
 * one open would keep its native memory for the life of the app.
 */
@ContributesBinding(AppScope::class)
class AndroidTextRecognizer @Inject constructor() : TextRecognizer {

    override suspend fun recognize(jpeg: ByteArray): TextRecognition = withContext(Dispatchers.Default) {
        // The JPEG is the app's own re-encode: already upright, no EXIF to honour.
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            ?: return@withContext TextRecognition.Failed
        val recognizer = MlKitTextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            Catching { recognizer.process(InputImage.fromBitmap(bitmap, 0)).await() }.fold(
                onSuccess = { TextRecognition.Read(it.toRecognizedText(bitmap)) },
                onFailure = { error ->
                    if (error is MlKitException && error.errorCode == MlKitException.UNAVAILABLE) {
                        TextRecognition.Unavailable
                    } else {
                        // The exception only, never the text: a label can carry a name and address.
                        KLog.e(error) { "On-device text recognition failed" }
                        TextRecognition.Failed
                    }
                },
            )
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    private fun Text.toRecognizedText(bitmap: Bitmap): RecognizedText {
        val width = bitmap.width.toDouble()
        val height = bitmap.height.toDouble()
        val lines = textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            RecognizedLine(
                text = line.text,
                // ML Kit gives a confidence per line; anything outside 0..1 is not one.
                confidence = line.confidence.toDouble().takeIf { it in 0.0..1.0 } ?: 1.0,
                left = box.left / width,
                top = box.top / height,
                width = box.width() / width,
                height = box.height() / height,
            )
        }
        return RecognizedText(
            lines = lines.inReadingOrder(),
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            engine = "mlkit",
        )
    }
}

/** A Play services [Task] as a suspend call. Cancelling the coroutine leaves the task to finish. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
