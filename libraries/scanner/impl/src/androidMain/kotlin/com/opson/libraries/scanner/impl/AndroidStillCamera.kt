package com.opson.libraries.scanner.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.scanner.CameraFacing
import com.opson.libraries.scanner.StillCamera
import com.opson.libraries.scanner.StillResult
import com.opson.libraries.scanner.StillShot
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * CameraX `Preview` plus `ImageCapture` to memory. The still never touches
 * disk here: it is decoded, turned upright, mirrored for the front camera,
 * scaled and re-encoded in memory, and handed back.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidStillCamera @Inject constructor() : StillCamera {

    @Composable
    override fun Viewfinder(
        facing: CameraFacing,
        shutter: Int,
        onResult: (StillResult) -> Unit,
        onUnavailable: () -> Unit,
        modifier: Modifier,
    ) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val latestOnResult by rememberUpdatedState(onResult)
        val latestOnUnavailable by rememberUpdatedState(onUnavailable)
        val previewView = remember(context) {
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        }
        val session = remember { mutableStateOf<StillSession?>(null) }
        DisposableEffect(lifecycleOwner, previewView, facing) {
            val started = StillSession(
                context = context,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                facing = facing,
                onResult = { latestOnResult(it) },
                onUnavailable = { latestOnUnavailable() },
            )
            started.start()
            session.value = started
            onDispose {
                started.stop()
                session.value = null
            }
        }
        // The first composition is not a press: only a rise in the count takes a photo.
        val armedAt = remember { mutableStateOf(shutter) }
        LaunchedEffect(shutter) {
            if (shutter > armedAt.value) {
                armedAt.value = shutter
                session.value?.capture() ?: latestOnResult(StillResult.Failed)
            }
        }
        AndroidView(factory = { previewView }, modifier = modifier)
    }
}

private class StillSession(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val facing: CameraFacing,
    private val onResult: (StillResult) -> Unit,
    private val onUnavailable: () -> Unit,
) {
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = ContextCompat.getMainExecutor(context)
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var capture: ImageCapture? = null
    private var camera: Camera? = null
    private var stopped = false
    private var reportedUnavailable = false

    private val selector: CameraSelector
        get() = if (facing == CameraFacing.Front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

    fun start() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                if (stopped) return@addListener
                Catching {
                    val cameraProvider = future.get()
                    if (!cameraProvider.hasCamera(selector)) {
                        unavailable()
                        return@addListener
                    }
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    val camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                    provider = cameraProvider
                    this.preview = preview
                    this.capture = capture
                    this.camera = camera
                    camera.cameraInfo.cameraState.observe(lifecycleOwner) { state -> if (state.error != null) unavailable() }
                }.logOnFailure("Still camera could not start").onFailure { unavailable() }
            },
            main,
        )
    }

    fun capture() {
        val capture = capture
        if (capture == null || stopped) {
            onResult(StillResult.Failed)
            return
        }
        capture.takePicture(
            worker,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val shot = Catching { image.use { encode(it, mirror = facing == CameraFacing.Front) } }
                        .logOnFailure("Couldn't read a still")
                        .getOrNull()
                    main.execute { if (!stopped) onResult(shot?.let(StillResult::Taken) ?: StillResult.Failed) }
                }

                override fun onError(exception: ImageCaptureException) {
                    KLog.e(exception) { "Still capture failed" }
                    main.execute { if (!stopped) onResult(StillResult.Failed) }
                }
            },
        )
    }

    private fun unavailable() {
        if (stopped || reportedUnavailable) return
        reportedUnavailable = true
        onUnavailable()
    }

    fun stop() {
        stopped = true
        camera?.cameraInfo?.cameraState?.removeObservers(lifecycleOwner)
        val bound = listOfNotNull(preview, capture)
        if (bound.isNotEmpty()) provider?.unbind(*bound.toTypedArray())
        worker.shutdown()
    }
}

/** Upright, mirrored like the preview for the front camera, scaled, and encoded twice: the photo and its thumbnail. */
private fun encode(image: ImageProxy, mirror: Boolean): StillShot {
    val source = image.toBitmap()
    val upright = transform(source, image.imageInfo.rotationDegrees.toFloat(), mirror, StillShot.MAX_EDGE_PX)
    val thumbnail = transform(upright, 0f, mirror = false, StillShot.THUMBNAIL_EDGE_PX)
    val shot = StillShot(jpeg = upright.toJpeg(), thumbnail = thumbnail.toJpeg())
    if (thumbnail !== upright) thumbnail.recycle()
    if (upright !== source) upright.recycle()
    source.recycle()
    return shot
}

private fun transform(bitmap: Bitmap, rotation: Float, mirror: Boolean, maxEdge: Int): Bitmap {
    val scale = maxEdge.toFloat() / max(bitmap.width, bitmap.height)
    val matrix = Matrix().apply {
        if (scale < 1f) postScale(scale, scale)
        if (rotation != 0f) postRotate(rotation)
        if (mirror) postScale(-1f, 1f)
    }
    if (matrix.isIdentity) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun Bitmap.toJpeg(): ByteArray = ByteArrayOutputStream().use { out ->
    compress(Bitmap.CompressFormat.JPEG, (StillShot.JPEG_QUALITY * PERCENT).roundToInt(), out)
    out.toByteArray()
}

private const val PERCENT = 100
