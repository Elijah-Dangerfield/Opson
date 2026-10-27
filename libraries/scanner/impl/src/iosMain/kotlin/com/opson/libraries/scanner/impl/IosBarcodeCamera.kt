package com.opson.libraries.scanner.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.opson.libraries.scanner.BarcodeCamera
import com.opson.libraries.scanner.BarcodeFormat
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.FocusPoint
import com.opson.libraries.scanner.ScanArea
import com.opson.libraries.scanner.ScannedCode
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import me.tatarka.inject.annotations.Inject
import platform.AVFoundation.AVCaptureAutoFocusRangeRestrictionNear
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInDualCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInDualWideCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInTripleCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureExposureModeContinuousAutoExposure
import platform.AVFoundation.AVCaptureFocusModeContinuousAutoFocus
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset1920x1080
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureSessionRuntimeErrorNotification
import platform.AVFoundation.AVCaptureSessionWasInterruptedNotification
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeEAN13Code
import platform.AVFoundation.AVMetadataObjectTypeEAN8Code
import platform.AVFoundation.AVMetadataObjectTypeUPCECode
import platform.AVFoundation.autoFocusRangeRestriction
import platform.AVFoundation.constituentDevices
import platform.AVFoundation.deviceType
import platform.AVFoundation.exposureMode
import platform.AVFoundation.exposurePointOfInterest
import platform.AVFoundation.focusMode
import platform.AVFoundation.focusPointOfInterest
import platform.AVFoundation.hasTorch
import platform.AVFoundation.isAutoFocusRangeRestrictionSupported
import platform.AVFoundation.isExposureModeSupported
import platform.AVFoundation.isExposurePointOfInterestSupported
import platform.AVFoundation.isFocusModeSupported
import platform.AVFoundation.isFocusPointOfInterestSupported
import platform.AVFoundation.isSmoothAutoFocusSupported
import platform.AVFoundation.isTorchModeSupported
import platform.AVFoundation.minimumFocusDistance
import platform.AVFoundation.requestAccessForMediaType
import platform.AVFoundation.smoothAutoFocusEnabled
import platform.AVFoundation.torchMode
import platform.AVFoundation.videoZoomFactor
import platform.AVFoundation.virtualDeviceSwitchOverVideoZoomFactors
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * `AVCaptureSession` with an `AVCaptureMetadataOutput`, written against
 * Kotlin/Native's AVFoundation bindings rather than in Swift, so the scanner is
 * one module on both platforms and `iOSApp.swift` passes nothing new in. iOS
 * reports a UPC-A as an EAN-13 with a leading zero, which is the same GTIN, so
 * there is no UPC-A type to ask for.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosBarcodeCamera @Inject constructor() : BarcodeCamera {

    @OptIn(ExperimentalForeignApi::class)
    @Composable
    override fun Viewfinder(
        onCode: (ScannedCode) -> Unit,
        onUnavailable: () -> Unit,
        scanArea: ScanArea,
        torchOn: Boolean,
        onTorchAvailable: (Boolean) -> Unit,
        focusPoint: FocusPoint?,
        modifier: Modifier,
    ) {
        val latestOnCode by rememberUpdatedState(onCode)
        val latestOnUnavailable by rememberUpdatedState(onUnavailable)
        val latestOnTorchAvailable by rememberUpdatedState(onTorchAvailable)
        val session = remember {
            CaptureSession(
                onCode = { code -> latestOnCode(code) },
                onUnavailable = { latestOnUnavailable() },
                onTorchAvailable = { available -> latestOnTorchAvailable(available) },
            )
        }
        SideEffect { session.scanArea = scanArea }
        DisposableEffect(session) {
            session.start()
            onDispose { session.stop() }
        }
        LaunchedEffect(session, torchOn) { session.torch(torchOn) }
        LaunchedEffect(session, focusPoint) { focusPoint?.let(session::focus) }
        UIKitView(
            factory = { session.view },
            modifier = modifier,
            // Taps stay with Compose, which turns them into a FocusPoint and draws the ring.
            properties = UIKitInteropProperties(isInteractive = false, isNativeAccessibilityEnabled = false),
        )
    }

    @Composable
    override fun rememberPermissionRequest(onAnswer: (CameraPermission) -> Unit): () -> Unit {
        val latestOnAnswer by rememberUpdatedState(onAnswer)
        return remember {
            {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    // The answer arrives on an arbitrary queue; state changes belong on the main one.
                    dispatch_async(dispatch_get_main_queue()) {
                        latestOnAnswer(if (granted) CameraPermission.Granted else CameraPermission.Denied(canAskAgain = false))
                    }
                }
            }
        }
    }
}

/**
 * One capture session and the view that shows it. It reports itself
 * unavailable when there is no camera to add (the simulator), when it does not
 * come up, and when iOS interrupts it or it fails at runtime (another app
 * holding the camera, a call, Split View).
 *
 * The session is configured and run on [captureQueue], as Apple asks, so
 * opening the scanner never waits on the camera. [device] and [output] are
 * only touched there; [stopped], [scanArea] and the view only on main.
 */
@OptIn(ExperimentalForeignApi::class)
private class CaptureSession(
    onCode: (ScannedCode) -> Unit,
    private val onUnavailable: () -> Unit,
    private val onTorchAvailable: (Boolean) -> Unit,
) {
    private val session = AVCaptureSession()
    private val output = AVCaptureMetadataOutput()
    private var device: AVCaptureDevice? = null
    private var torchWanted = false
    private var stopped = false
    private var reportedUnavailable = false
    private val observers = mutableListOf<NSObjectProtocol>()

    // AVCaptureMetadataOutput holds its delegate weakly, so this is the strong reference.
    private val delegate = MetadataDelegate(onCode)
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }
    val view: UIView = PreviewView(previewLayer) { updateRectOfInterest() }

    var scanArea: ScanArea = ScanArea(1f, 1f)
        set(value) {
            if (field == value) return
            field = value
            updateRectOfInterest()
        }

    /** Call on the main thread. */
    fun start() {
        listOf(AVCaptureSessionRuntimeErrorNotification, AVCaptureSessionWasInterruptedNotification).forEach { name ->
            observers += NSNotificationCenter.defaultCenter.addObserverForName(
                name = name,
                `object` = session,
                queue = NSOperationQueue.mainQueue,
            ) { _ -> unavailable() }
        }
        val fill = scanArea.width.toDouble()
        // Configuring and startRunning both block until the camera answers, so never on the main thread.
        dispatch_async(captureQueue) {
            val camera = configure(fill)
            if (camera != null && !session.running) session.startRunning()
            val running = session.running
            camera?.let { applyTorch(it) }
            val hasTorch = camera?.hasTorch == true
            dispatch_async(dispatch_get_main_queue()) {
                if (!running) {
                    unavailable()
                } else if (!stopped) {
                    onTorchAvailable(hasTorch)
                    // Only a running session knows its video size, which the conversion needs.
                    updateRectOfInterest()
                }
            }
        }
    }

    /** Call on the main thread. */
    fun stop() {
        stopped = true
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observers.clear()
        dispatch_async(captureQueue) {
            // Stopping the session turns the torch off with it.
            if (session.running) session.stopRunning()
        }
    }

    /** Call on the main thread. */
    fun torch(on: Boolean) {
        dispatch_async(captureQueue) {
            torchWanted = on
            device?.let { applyTorch(it) }
        }
    }

    /**
     * Focus and meter at [point], and keep doing so as the pack moves: continuous
     * modes with a point of interest, rather than a one-off focus that a moved
     * pack would leave behind. Call on the main thread.
     */
    fun focus(point: FocusPoint) {
        val bounds = view.bounds.useContents { size.width to size.height }
        if (bounds.first <= 0 || bounds.second <= 0) return
        val devicePoint: CValue<CGPoint> = previewLayer.captureDevicePointOfInterestForPoint(
            CGPointMake(point.x * bounds.first, point.y * bounds.second),
        )
        dispatch_async(captureQueue) {
            device?.configure { pointAt(devicePoint) }
        }
    }

    /** Once per session, and never after the screen has gone. Main thread only. */
    private fun unavailable() {
        if (stopped || reportedUnavailable) return
        reportedUnavailable = true
        onUnavailable()
    }

    /**
     * Picks the camera, adds it and the metadata output, and sets them up for
     * barcodes. Null when there is no camera to use. On [captureQueue].
     */
    private fun configure(fill: Double): AVCaptureDevice? {
        val camera = scanningCamera() ?: return null
        val input = AVCaptureDeviceInput.deviceInputWithDevice(camera, error = null) ?: return null
        session.beginConfiguration()
        // 1080p gives the detector enough pixels per bar for a small or distant code; the default
        // preset can be lower, and 4K costs power and latency for no better read.
        session.sessionPreset = if (session.canSetSessionPreset(AVCaptureSessionPreset1920x1080)) {
            AVCaptureSessionPreset1920x1080
        } else {
            AVCaptureSessionPresetHigh
        }
        val added = session.canAddInput(input) && session.canAddOutput(output)
        if (added) {
            session.addInput(input)
            session.addOutput(output)
            // Results land on their own queue, not main, so a busy frame never holds up the next read.
            output.setMetadataObjectsDelegate(delegate, queue = metadataQueue)
            // Only after the output is in the session: the types it supports depend on the input, and
            // asking for one it does not throws. Fewer types also means less work per frame.
            output.metadataObjectTypes = FORMATS.keys.filter { type -> output.availableMetadataObjectTypes.any { it == type } }
        }
        session.commitConfiguration()
        if (!added) return null
        // After the commit, which is when the preset picks the format that zoom and focus apply to.
        camera.configure {
            if (isFocusModeSupported(AVCaptureFocusModeContinuousAutoFocus)) focusMode = AVCaptureFocusModeContinuousAutoFocus
            // A barcode is always close: searching only the near range makes each refocus quicker.
            if (isAutoFocusRangeRestrictionSupported()) autoFocusRangeRestriction = AVCaptureAutoFocusRangeRestrictionNear
            // Smooth autofocus slows focus changes down so a video doesn't visibly pulse. A scan wants
            // the opposite: sharp as soon as possible.
            if (isSmoothAutoFocusSupported()) smoothAutoFocusEnabled = false
            if (isExposureModeSupported(AVCaptureExposureModeContinuousAutoExposure)) {
                exposureMode = AVCaptureExposureModeContinuousAutoExposure
            }
            videoZoomFactor = recommendedZoom(fill)
        }
        device = camera
        return camera
    }

    /** On [captureQueue]. */
    private fun applyTorch(camera: AVCaptureDevice) {
        val mode = if (torchWanted) AVCaptureTorchModeOn else AVCaptureTorchModeOff
        if (!camera.hasTorch || !camera.isTorchModeSupported(mode) || camera.torchMode == mode) return
        camera.configure { torchMode = mode }
    }

    /**
     * Sets the output to read only inside the scan area, in the output's own
     * coordinates (rotated and cropped from the view's, which the preview layer
     * converts). Main thread; the output is set on [captureQueue].
     */
    private fun updateRectOfInterest() {
        if (!session.running) return
        val area = scanArea
        val rect = view.bounds.useContents {
            if (size.width <= 0 || size.height <= 0) return
            CGRectMake(
                x = size.width * (1 - area.width) / 2,
                y = size.height * (1 - area.height) / 2,
                width = size.width * area.width,
                height = size.height * area.height,
            )
        }
        val interest: CValue<CGRect> = previewLayer.metadataOutputRectOfInterestForRect(rect)
        dispatch_async(captureQueue) { output.rectOfInterest = interest }
    }
}

/**
 * The back camera that reads barcodes best. On phones with an ultra wide, a
 * virtual device (triple or dual wide) hands over to the ultra wide when the
 * pack is closer than the main camera can focus, as the Camera app does for
 * macro. The plain wide camera, which `defaultDeviceWithMediaType` gives, cannot
 * focus closer than about 20 cm on a Pro, which is where people hold a pack.
 */
@OptIn(ExperimentalForeignApi::class)
private fun scanningCamera(): AVCaptureDevice? = AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
    PREFERRED_CAMERAS,
    mediaType = AVMediaTypeVideo,
    position = AVCaptureDevicePositionBack,
).devices.filterIsInstance<AVCaptureDevice>().let { found ->
    // The session lists devices in no promised order, so pick by preference.
    PREFERRED_CAMERAS.firstNotNullOfOrNull { type -> found.firstOrNull { it.deviceType == type } }
}

/** Best first. */
private val PREFERRED_CAMERAS = listOf(
    AVCaptureDeviceTypeBuiltInTripleCamera,
    AVCaptureDeviceTypeBuiltInDualWideCamera,
    AVCaptureDeviceTypeBuiltInDualCamera,
    AVCaptureDeviceTypeBuiltInWideAngleCamera,
)

/**
 * The zoom to open at. A virtual device's zoom 1 is its widest lens (the ultra
 * wide on a triple or dual wide), so it starts from where the wide lens takes
 * over, which is what the Camera app calls 1x, and stays below the next lens.
 * See [scanZoom] for the rest.
 */
@OptIn(ExperimentalForeignApi::class)
private fun AVCaptureDevice.recommendedZoom(fill: Double): Double {
    val switchOvers = virtualDeviceSwitchOverVideoZoomFactors.mapNotNull { (it as? NSNumber)?.doubleValue }
    val startsUltraWide = deviceType == AVCaptureDeviceTypeBuiltInTripleCamera || deviceType == AVCaptureDeviceTypeBuiltInDualWideCamera
    val base = if (startsUltraWide) switchOvers.firstOrNull() ?: 1.0 else 1.0
    val nextLens = switchOvers.firstOrNull { it > base }
    val wide = if (startsUltraWide) {
        constituentDevices.filterIsInstance<AVCaptureDevice>().firstOrNull { it.deviceType == AVCaptureDeviceTypeBuiltInWideAngleCamera }
    } else {
        this
    }
    return scanZoom(
        baseZoom = base,
        fieldOfViewDegrees = activeFormat.videoFieldOfView.toDouble(),
        minimumFocusMillimetres = (wide ?: this).minimumFocusDistance.toDouble(),
        fill = fill,
        maxZoom = minOf(activeFormat.videoMaxZoomFactor, nextLens?.let { it - NEXT_LENS_MARGIN } ?: Double.MAX_VALUE),
    )
}

/** Locks the device for [change], and skips it when another app holds the lock. On [captureQueue]. */
@OptIn(ExperimentalForeignApi::class)
private inline fun AVCaptureDevice.configure(change: AVCaptureDevice.() -> Unit) {
    if (!lockForConfiguration(null)) return
    try {
        change()
    } finally {
        unlockForConfiguration()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun AVCaptureDevice.pointAt(point: CValue<CGPoint>) {
    if (isFocusPointOfInterestSupported() && isFocusModeSupported(AVCaptureFocusModeContinuousAutoFocus)) {
        focusPointOfInterest = point
        // Setting the point is only acted on when the mode is set after it.
        focusMode = AVCaptureFocusModeContinuousAutoFocus
    }
    if (isExposurePointOfInterestSupported() && isExposureModeSupported(AVCaptureExposureModeContinuousAutoExposure)) {
        exposurePointOfInterest = point
        exposureMode = AVCaptureExposureModeContinuousAutoExposure
    }
}

/** Keeps the preview layer the size of the view as Compose lays it out, and says when it changes. */
@OptIn(ExperimentalForeignApi::class)
private class PreviewView(
    private val previewLayer: AVCaptureVideoPreviewLayer,
    private val onLayout: () -> Unit,
) : UIView(frame = CGRectZero.readValue()) {
    init {
        backgroundColor = UIColor.blackColor
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        previewLayer.setFrame(bounds)
        onLayout()
    }
}

/** Runs on [metadataQueue]; hands each read to main, where the caller's state lives. */
private class MetadataDelegate(private val onCode: (ScannedCode) -> Unit) :
    NSObject(),
    AVCaptureMetadataOutputObjectsDelegateProtocol {

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        val read = didOutputMetadataObjects
            .filterIsInstance<AVMetadataMachineReadableCodeObject>()
            .firstNotNullOfOrNull { code ->
                val value = code.stringValue ?: return@firstNotNullOfOrNull null
                FORMATS[code.type]?.let { ScannedCode(value, it) }
            }
            ?: return
        dispatch_async(dispatch_get_main_queue()) { onCode(read) }
    }
}

/**
 * Where every capture session, this one's and the still camera's, is set up, started and stopped,
 * and where their devices are changed. Serial, so a stop always runs after the start before it. On
 * a concurrent queue, a screen closed (or a camera flipped) while its session was still coming up
 * could have its stop find nothing running yet, and the start then left the camera on with nothing
 * showing it.
 */
internal val captureQueue = dispatch_queue_create("com.opson.scanner.capture", null)

/** Where the metadata output delivers reads: off main, and apart from [captureQueue]'s blocking start. */
private val metadataQueue = dispatch_queue_create("com.opson.scanner.metadata", null)

/** Keeps a virtual device's zoom short of the factor where it would hop to the telephoto. */
private const val NEXT_LENS_MARGIN = 0.05

private val FORMATS: Map<String?, BarcodeFormat> = mapOf(
    AVMetadataObjectTypeEAN13Code to BarcodeFormat.Ean13,
    AVMetadataObjectTypeEAN8Code to BarcodeFormat.Ean8,
    AVMetadataObjectTypeUPCECode to BarcodeFormat.UpcE,
)
