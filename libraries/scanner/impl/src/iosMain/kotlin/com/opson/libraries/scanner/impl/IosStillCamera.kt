package com.opson.libraries.scanner.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.scanner.CameraFacing
import com.opson.libraries.scanner.StillCamera
import com.opson.libraries.scanner.StillResult
import com.opson.libraries.scanner.StillShot
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import me.tatarka.inject.annotations.Inject
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureSessionRuntimeErrorNotification
import platform.AVFoundation.AVCaptureSessionWasInterruptedNotification
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.fileDataRepresentation
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIView
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.math.max

/**
 * `AVCaptureSession` with an `AVCapturePhotoOutput`, in Kotlin/Native like
 * the barcode camera. The still stays in memory: it is redrawn upright and
 * scaled, re-encoded, and handed back. The front camera's connection is
 * mirrored like its preview.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosStillCamera @Inject constructor() : StillCamera {

    @OptIn(ExperimentalForeignApi::class)
    @Composable
    override fun Viewfinder(
        facing: CameraFacing,
        shutter: Int,
        onResult: (StillResult) -> Unit,
        onUnavailable: () -> Unit,
        modifier: Modifier,
    ) {
        val latestOnResult by rememberUpdatedState(onResult)
        val latestOnUnavailable by rememberUpdatedState(onUnavailable)
        val session = remember(facing) {
            StillCaptureSession(facing, onResult = { latestOnResult(it) }, onUnavailable = { latestOnUnavailable() })
        }
        DisposableEffect(session) {
            session.start()
            onDispose { session.stop() }
        }
        val armedAt = remember { mutableStateOf(shutter) }
        LaunchedEffect(shutter) {
            if (shutter > armedAt.value) {
                armedAt.value = shutter
                session.capture()
            }
        }
        UIKitView(
            factory = { session.view },
            update = {},
            modifier = modifier,
            properties = UIKitInteropProperties(isInteractive = false, isNativeAccessibilityEnabled = false),
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private class StillCaptureSession(
    private val facing: CameraFacing,
    private val onResult: (StillResult) -> Unit,
    private val onUnavailable: () -> Unit,
) {
    private val session = AVCaptureSession()
    private val output = AVCapturePhotoOutput()
    private var usable = false
    private var stopped = false
    private var reportedUnavailable = false
    private val observers = mutableListOf<NSObjectProtocol>()

    // AVCapturePhotoOutput holds its delegate only for the capture, so one per press is kept here.
    private val pending = mutableListOf<PhotoDelegate>()
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }
    val view: UIView = StillPreviewView(previewLayer)

    init {
        val position = if (facing == CameraFacing.Front) AVCaptureDevicePositionFront else AVCaptureDevicePositionBack
        val device = AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
            listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
            mediaType = AVMediaTypeVideo,
            position = position,
        ).devices.firstOrNull() as? AVCaptureDevice
        val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, error = null) }
        if (input != null && session.canAddInput(input) && session.canAddOutput(output)) {
            session.sessionPreset = AVCaptureSessionPresetPhoto
            session.addInput(input)
            session.addOutput(output)
            output.connectionWithMediaType(AVMediaTypeVideo)?.let { connection ->
                if (connection.isVideoOrientationSupported()) connection.videoOrientation = AVCaptureVideoOrientationPortrait
                if (facing == CameraFacing.Front && connection.isVideoMirroringSupported()) {
                    connection.automaticallyAdjustsVideoMirroring = false
                    connection.videoMirrored = true
                }
            }
            usable = true
        }
    }

    fun start() {
        if (!usable) {
            dispatch_async(dispatch_get_main_queue()) { unavailable() }
            return
        }
        listOf(AVCaptureSessionRuntimeErrorNotification, AVCaptureSessionWasInterruptedNotification).forEach { name ->
            observers += NSNotificationCenter.defaultCenter.addObserverForName(
                name = name,
                `object` = session,
                queue = NSOperationQueue.mainQueue,
            ) { _ -> unavailable() }
        }
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0uL)) {
            if (!session.running) session.startRunning()
            if (!session.running) dispatch_async(dispatch_get_main_queue()) { unavailable() }
        }
    }

    /** Main thread. */
    fun capture() {
        if (!usable || stopped || !session.running) {
            onResult(StillResult.Failed)
            return
        }
        lateinit var delegate: PhotoDelegate
        delegate = PhotoDelegate { data ->
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0uL)) {
                val shot = data?.let { bytes -> Catching { encode(bytes) }.logOnFailure("Couldn't read a still").getOrNull() }
                dispatch_async(dispatch_get_main_queue()) {
                    pending.remove(delegate)
                    if (!stopped) onResult(shot?.let(StillResult::Taken) ?: StillResult.Failed)
                }
            }
        }
        pending += delegate
        output.capturePhotoWithSettings(AVCapturePhotoSettings.photoSettings(), delegate = delegate)
    }

    private fun unavailable() {
        if (stopped || reportedUnavailable) return
        reportedUnavailable = true
        onUnavailable()
    }

    fun stop() {
        stopped = true
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observers.clear()
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0uL)) {
            if (session.running) session.stopRunning()
        }
    }
}

private class PhotoDelegate(private val onData: (NSData?) -> Unit) : NSObject(), AVCapturePhotoCaptureDelegateProtocol {
    override fun captureOutput(output: AVCapturePhotoOutput, didFinishProcessingPhoto: AVCapturePhoto, error: NSError?) {
        onData(if (error == null) didFinishProcessingPhoto.fileDataRepresentation() else null)
    }
}

/** Redraws the still upright at both sizes; drawing applies its orientation. */
@OptIn(ExperimentalForeignApi::class)
private fun encode(data: NSData): StillShot? {
    val image = UIImage.imageWithData(data) ?: return null
    val photo = redraw(image, StillShot.MAX_EDGE_PX) ?: return null
    val thumbnail = redraw(image, StillShot.THUMBNAIL_EDGE_PX) ?: return null
    return StillShot(jpeg = photo, thumbnail = thumbnail)
}

@OptIn(ExperimentalForeignApi::class)
private fun redraw(image: UIImage, maxEdge: Int): ByteArray? {
    val (width, height) = image.size.useContents { width to height }
    if (width <= 0.0 || height <= 0.0) return null
    val scale = minOf(1.0, maxEdge / max(width, height))
    val format = UIGraphicsImageRendererFormat().apply { this.scale = 1.0 }
    val drawn = UIGraphicsImageRenderer(CGSizeMake(width * scale, height * scale), format).imageWithActions { _ ->
        image.drawInRect(CGRectMake(0.0, 0.0, width * scale, height * scale))
    }
    return UIImageJPEGRepresentation(drawn, StillShot.JPEG_QUALITY)?.toBytes()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toBytes(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}

@OptIn(ExperimentalForeignApi::class)
private class StillPreviewView(private val previewLayer: AVCaptureVideoPreviewLayer) : UIView(frame = CGRectZero.readValue()) {
    init {
        backgroundColor = UIColor.blackColor
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        previewLayer.setFrame(bounds)
    }
}
