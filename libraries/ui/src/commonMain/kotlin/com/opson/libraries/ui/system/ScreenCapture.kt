@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.ui.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.native.HiddenFromObjC
import kotlin.time.Duration.Companion.seconds

/**
 * A picture of what the app shows, for a bug report. Shaking the phone takes one before the shake
 * dialog covers the screen and [hold]s it; the bug report that shake opens [takeHeld]s it, shows
 * it, and the person can take it off before sending.
 *
 * Only the app's own Compose content: a bottom sheet or dialog is a window of its own on Android
 * and is not in the picture, and nor is a camera preview. Nothing here touches disk.
 *
 * Hidden from the iOS header, as Swift never uses it (see `MoreRowContribution` for why that matters).
 */
@HiddenFromObjC
interface ScreenCapture {
    /**
     * A JPEG of the app as it is drawn now, at most [MAX_EDGE_PX] on its long edge, or null when
     * there is nothing to capture: no [screenCaptureSource] on screen, or the encode failed.
     */
    suspend fun capture(): ByteArray?

    /** Keeps [jpeg] for the next [takeHeld], replacing whatever was held. */
    fun hold(jpeg: ByteArray?)

    /** The held picture, once: taking it lets it go. */
    fun takeHeld(): ByteArray?

    companion object {
        /** Enough to read every word on a phone screen, a few hundred KB as a JPEG. */
        const val MAX_EDGE_PX = 1600f
        const val JPEG_QUALITY = 80
    }
}

/**
 * [ScreenCapture] drawn by [screenCaptureSource]: a capture asks for one frame, the root records
 * that frame into a layer as it draws, and the platform encodes the layer's bitmap. Recording
 * only when asked means the app pays nothing for this between reports.
 */
@HiddenFromObjC
abstract class LayerScreenCapture : ScreenCapture {

    internal var request: CompletableDeferred<GraphicsLayer>? by mutableStateOf(null)
        private set

    private var held: ByteArray? = null

    /** [bitmap] scaled to [size] and encoded as a JPEG at [ScreenCapture.JPEG_QUALITY], off the main thread. */
    protected abstract fun encode(bitmap: ImageBitmap, size: IntSize): ByteArray?

    override suspend fun capture(): ByteArray? = withContext(Dispatchers.Main) {
        // One at a time: a second shake while the first is encoding gets nothing rather than a race.
        if (request != null) return@withContext null
        val asked = CompletableDeferred<GraphicsLayer>()
        request = asked
        try {
            // A frame, normally. No source on screen (a preview, a test) never answers.
            val layer = withTimeoutOrNull(FRAME_WAIT) { asked.await() } ?: return@withContext null
            val bitmap = layer.toImageBitmap()
            withContext(Dispatchers.Default) {
                Catching { encode(bitmap, scaledToMaxEdge(bitmap.width, bitmap.height)) }
                    .logOnFailure("Couldn't encode the screenshot")
                    .getOrNull()
            }
        } finally {
            request = null
        }
    }

    override fun hold(jpeg: ByteArray?) {
        held = jpeg
    }

    override fun takeHeld(): ByteArray? = held.also { held = null }

    private companion object {
        val FRAME_WAIT = 1.seconds
    }
}

/** [width] by [height] shrunk to fit [ScreenCapture.MAX_EDGE_PX], never grown. */
internal fun scaledToMaxEdge(width: Int, height: Int): IntSize {
    val factor = min(1f, ScreenCapture.MAX_EDGE_PX / max(width, height).coerceAtLeast(1))
    return IntSize((width * factor).roundToInt().coerceAtLeast(1), (height * factor).roundToInt().coerceAtLeast(1))
}

/**
 * Put once on the app's root: draws the content as usual, and on the frame after
 * [ScreenCapture.capture] asks, draws it through a layer the capture then reads. Any other
 * [ScreenCapture] (a fake) leaves the content untouched.
 */
@Composable
fun Modifier.screenCaptureSource(capture: ScreenCapture): Modifier {
    val source = capture as? LayerScreenCapture ?: return this
    val layer = rememberGraphicsLayer()
    return drawWithContent {
        // Read in draw, so asking invalidates exactly one draw of the root and nothing recomposes.
        val asked = source.request
        if (asked == null || asked.isCompleted || size.width <= 0f || size.height <= 0f) {
            drawContent()
            return@drawWithContent
        }
        // The documented shape: record the content into the layer, then draw the layer in its
        // place, so the frame on screen is the frame captured and the content is drawn once.
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
        asked.complete(layer)
    }
}
