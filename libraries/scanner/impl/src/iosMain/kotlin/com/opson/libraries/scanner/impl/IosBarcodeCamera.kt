package com.opson.libraries.scanner.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.opson.libraries.scanner.BarcodeCamera
import com.opson.libraries.scanner.BarcodeFormat
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.ScannedCode
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import me.tatarka.inject.annotations.Inject
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeEAN13Code
import platform.AVFoundation.AVMetadataObjectTypeEAN8Code
import platform.AVFoundation.AVMetadataObjectTypeUPCECode
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
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
    override fun Viewfinder(onCode: (ScannedCode) -> Unit, modifier: Modifier) {
        val latestOnCode by rememberUpdatedState(onCode)
        val session = remember { CaptureSession { code -> latestOnCode(code) } }
        DisposableEffect(session) {
            session.start()
            onDispose { session.stop() }
        }
        UIKitView(
            factory = { session.view },
            modifier = modifier,
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

/** One capture session and the view that shows it. On the simulator there is no camera, so the view stays black. */
@OptIn(ExperimentalForeignApi::class)
private class CaptureSession(onCode: (ScannedCode) -> Unit) {
    private val session = AVCaptureSession()

    // AVCaptureMetadataOutput holds its delegate weakly, so this is the strong reference.
    private val delegate = MetadataDelegate(onCode)
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }
    val view: UIView = PreviewView(previewLayer)

    init {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, error = null) }
        if (input != null && session.canAddInput(input)) {
            session.addInput(input)
            val output = AVCaptureMetadataOutput()
            if (session.canAddOutput(output)) {
                session.addOutput(output)
                output.setMetadataObjectsDelegate(delegate, queue = dispatch_get_main_queue())
                // Only types this output supports may be set, or it throws; the list depends on the input.
                output.metadataObjectTypes = FORMATS.keys.filter { type -> output.availableMetadataObjectTypes.any { it == type } }
            }
        }
    }

    fun start() {
        // startRunning blocks until the camera is up, so never on the main thread.
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0uL)) {
            if (!session.running && session.inputs.isNotEmpty()) session.startRunning()
        }
    }

    fun stop() {
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0uL)) {
            if (session.running) session.stopRunning()
        }
    }
}

/** Keeps the preview layer the size of the view as Compose lays it out. */
@OptIn(ExperimentalForeignApi::class)
private class PreviewView(private val previewLayer: AVCaptureVideoPreviewLayer) : UIView(frame = CGRectZero.readValue()) {
    init {
        backgroundColor = UIColor.blackColor
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        previewLayer.setFrame(bounds)
    }
}

private class MetadataDelegate(private val onCode: (ScannedCode) -> Unit) :
    NSObject(),
    AVCaptureMetadataOutputObjectsDelegateProtocol {

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        didOutputMetadataObjects
            .filterIsInstance<AVMetadataMachineReadableCodeObject>()
            .firstNotNullOfOrNull { code ->
                val value = code.stringValue ?: return@firstNotNullOfOrNull null
                FORMATS[code.type]?.let { ScannedCode(value, it) }
            }
            ?.let(onCode)
    }
}

private val FORMATS: Map<String?, BarcodeFormat> = mapOf(
    AVMetadataObjectTypeEAN13Code to BarcodeFormat.Ean13,
    AVMetadataObjectTypeEAN8Code to BarcodeFormat.Ean8,
    AVMetadataObjectTypeUPCECode to BarcodeFormat.UpcE,
)
