package com.opson.libraries.scanner.impl

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.SystemClock
import android.util.Size
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.opson.ActivityProvider
import com.opson.libraries.scanner.BarcodeCamera
import com.opson.libraries.scanner.BarcodeFormat
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.FocusPoint
import com.opson.libraries.scanner.ScanArea
import com.opson.libraries.scanner.ScannedCode
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraX for the preview and the frames, ML Kit's bundled barcode model to
 * read them. Bundled rather than the Play services module so a scan works on
 * first launch, offline, and on phones without Play services; see
 * docs/plans/screens/barcode.md "Scanner choices".
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidBarcodeCamera @Inject constructor(
    private val activityProvider: ActivityProvider,
) : BarcodeCamera {

    /**
     * ML Kit takes no region to search, so [scanArea] filters its answers
     * instead: a code whose middle is outside the area is dropped. At
     * [ANALYSIS_SIZE] a whole frame reads in well under a frame's time.
     */
    @Composable
    override fun Viewfinder(
        onCode: (ScannedCode) -> Unit,
        onUnavailable: () -> Unit,
        scanArea: ScanArea,
        torchOn: Boolean,
        onTorchAvailable: (Boolean) -> Unit,
        onLowLight: (Boolean) -> Unit,
        focusPoint: FocusPoint?,
        modifier: Modifier,
    ) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val latestOnCode by rememberUpdatedState(onCode)
        val latestOnUnavailable by rememberUpdatedState(onUnavailable)
        val latestOnTorchAvailable by rememberUpdatedState(onTorchAvailable)
        val latestOnLowLight by rememberUpdatedState(onLowLight)
        val previewView = remember(context) {
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                // A TextureView, so the preview clips and layers like any other composable.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        }
        val session = remember(context, lifecycleOwner, previewView) {
            CameraSession(
                context = context,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                onCode = { code -> latestOnCode(code) },
                onUnavailable = { latestOnUnavailable() },
                onTorchAvailable = { available -> latestOnTorchAvailable(available) },
                onLowLight = { low -> latestOnLowLight(low) },
            )
        }
        SideEffect { session.scanArea = scanArea }
        DisposableEffect(session) {
            session.start()
            onDispose { session.stop() }
        }
        LaunchedEffect(session, torchOn) { session.torch(torchOn) }
        LaunchedEffect(session, focusPoint) { focusPoint?.let(session::focus) }
        AndroidView(factory = { previewView }, modifier = modifier)
    }

    @Composable
    override fun rememberPermissionRequest(onAnswer: (CameraPermission) -> Unit): () -> Unit {
        val latestOnAnswer by rememberUpdatedState(onAnswer)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            latestOnAnswer(
                if (granted) {
                    CameraPermission.Granted
                } else {
                    // False after a denial means "don't ask again" was chosen, or the
                    // system stopped showing the prompt: only Settings can change it now.
                    CameraPermission.Denied(canAskAgain = activityProvider.currentActivity()?.canAskAgain() == true)
                },
            )
        }
        return remember(launcher) { { launcher.launch(Manifest.permission.CAMERA) } }
    }
}

/**
 * One bound camera: preview plus analysis, unbound on [stop]. Both ask for
 * 4:3, so the analysis frame covers exactly what the preview shows before the
 * view crops it, which [insideScanArea] relies on. The analysis used to be
 * 16:9, which on a 4:3 sensor cuts the long sides: in portrait it saw only
 * about three quarters of the preview's width, less than the frame drawn on it.
 */
@OptIn(ExperimentalCamera2Interop::class)
private class CameraSession(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onCode: (ScannedCode) -> Unit,
    private val onUnavailable: () -> Unit,
    private val onTorchAvailable: (Boolean) -> Unit,
    private val onLowLight: (Boolean) -> Unit,
) {
    private var torchWanted = false
    private val timing = ScanTiming("Android")

    /** Fed only from the camera's capture thread. */
    private val lowLight = LowLightDetector()
    private var lastLightCheck = 0L

    /** Set on main, read on the analysis thread. */
    @Volatile
    var scanArea: ScanArea = ScanArea(1f, 1f)

    @Volatile
    private var viewSize: Pair<Int, Int> = 0 to 0
    private val layoutListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        viewSize = view.width to view.height
    }
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build(),
    )
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private var stopped = false
    private var reportedUnavailable = false

    fun start() {
        previewView.addOnLayoutChangeListener(layoutListener)
        viewSize = previewView.width to previewView.height
        val future = ProcessCameraProvider.getInstance(context)
        val main = ContextCompat.getMainExecutor(context)
        future.addListener(
            {
                // The provider can arrive after the screen has already gone.
                if (stopped) return@addListener
                Catching {
                    val cameraProvider = future.get()
                    if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        unavailable()
                        return@addListener
                    }
                    val previewBuilder = Preview.Builder().setResolutionSelector(
                        ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build(),
                    )
                    // The exposure each frame was taken at, which says how dark the scene is.
                    Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(exposureWatcher(main))
                    val preview = previewBuilder.build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        // CameraX analyses at 640x480 unless asked, too few pixels per bar for a small or
                        // distant code. ML Kit's guidance for barcodes is 1280x720 or more; 1280x960 puts
                        // as many pixels across the preview's width as 1280x720 did, over all of it.
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                                .setResolutionStrategy(
                                    ResolutionStrategy(ANALYSIS_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                                )
                                .build(),
                        )
                        .build()
                        .also { it.setAnalyzer(analysisExecutor) { proxy -> read(proxy) { code -> main.execute { delivered(code) } } } }
                    val camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    provider = cameraProvider
                    this.preview = preview
                    this.analysis = analysis
                    this.camera = camera
                    // Bound is not open: another app holding the camera, a policy that disables it
                    // or a driver fault all arrive later, as an error on the camera's state.
                    camera.cameraInfo.cameraState.observe(lifecycleOwner) { state -> if (state.error != null) unavailable() }
                    val hasTorch = camera.cameraInfo.hasFlashUnit()
                    if (hasTorch && torchWanted) camera.cameraControl.enableTorch(true)
                    onTorchAvailable(hasTorch)
                    timing.cameraUp()
                }.logOnFailure("Camera could not start").onFailure { unavailable() }
            },
            main,
        )
    }

    /** Main thread. Before the camera is bound, remembered and applied when it is. */
    fun torch(on: Boolean) {
        torchWanted = on
        val camera = camera ?: return
        if (camera.cameraInfo.hasFlashUnit()) camera.cameraControl.enableTorch(on)
    }

    /**
     * Focus and meter at [point] once; CameraX goes back to continuous
     * autofocus after its default few seconds, so a moved pack still comes
     * sharp. Main thread.
     */
    fun focus(point: FocusPoint) {
        val camera = camera ?: return
        if (previewView.width == 0 || previewView.height == 0) return
        val metering = previewView.meteringPointFactory.createPoint(point.x * previewView.width, point.y * previewView.height)
        val action = FocusMeteringAction.Builder(metering, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE).build()
        // A camera that cannot focus at a point (fixed focus) fails the future; there is nothing to do about it.
        camera.cameraControl.startFocusAndMetering(action)
    }

    /** Once per session, and never after the screen has gone. Always on the main thread. */
    private fun unavailable() {
        if (stopped || reportedUnavailable) return
        reportedUnavailable = true
        onUnavailable()
    }

    fun stop() {
        if (!stopped) timing.closed()
        stopped = true
        previewView.removeOnLayoutChangeListener(layoutListener)
        camera?.cameraInfo?.cameraState?.removeObservers(lifecycleOwner)
        val bound = listOfNotNull(preview, analysis)
        if (bound.isNotEmpty()) provider?.unbind(*bound.toTypedArray())
        analysis?.clearAnalyzer()
        analysisExecutor.shutdown()
        scanner.close()
    }

    /** Main thread. */
    private fun delivered(code: ScannedCode) {
        if (stopped) return
        timing.read(code.value)
        onCode(code)
    }

    /**
     * Turns each frame's exposure into a light level and says when it has been
     * dark for a moment, or lit again. On the camera's capture thread, a few
     * times a second rather than every frame.
     */
    private fun exposureWatcher(main: Executor) = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastLightCheck < LIGHT_CHECK_MILLIS) return
            lastLightCheck = now
            val aperture = result.get(CaptureResult.LENS_APERTURE) ?: return
            val exposureNanos = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return
            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: return
            val ev = sceneEv100(aperture.toDouble(), exposureNanos / NANOS_PER_SECOND, iso.toDouble()) ?: return
            lowLight.update(ev, now)?.let { low -> main.execute { if (!stopped) onLowLight(low) } }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun read(proxy: ImageProxy, onCode: (ScannedCode) -> Unit) {
        val image = proxy.image
        if (image == null) {
            proxy.close()
            return
        }
        val rotation = proxy.imageInfo.rotationDegrees
        // ML Kit's boxes are in the upright frame's coordinates.
        val (width, height) = uprightSize(proxy.width, proxy.height, rotation)
        val (viewWidth, viewHeight) = viewSize
        val area = scanArea
        val started = SystemClock.elapsedRealtime()
        scanner.process(InputImage.fromMediaImage(image, rotation))
            .addOnSuccessListener { barcodes ->
                timing.frame(SystemClock.elapsedRealtime() - started)
                barcodes
                    .mapNotNull { barcode -> barcode.toScannedCode()?.let { code -> barcode to code } }
                    .filter { (barcode, _) ->
                        val box = barcode.boundingBox ?: return@filter true
                        insideScanArea(box.exactCenterX(), box.exactCenterY(), width, height, viewWidth, viewHeight, area)
                    }
                    // A shelf can put two packs in the frame: read the one being aimed at.
                    .minByOrNull { (barcode, _) ->
                        val box = barcode.boundingBox ?: return@minByOrNull 0f
                        val dx = box.exactCenterX() - width / 2f
                        val dy = box.exactCenterY() - height / 2f
                        dx * dx + dy * dy
                    }
                    ?.let { (_, code) -> onCode(code) }
            }
            // Closing the frame is what lets the next one in, success or not.
            .addOnCompleteListener { proxy.close() }
    }
}

/**
 * The frame size ML Kit reads, 4:3 to match the preview, with the 1280 long
 * side ML Kit recommends for barcodes. Larger frames read a little further
 * away but take longer each, so fewer are read.
 */
private val ANALYSIS_SIZE = Size(1280, 960)

/** How often a frame's exposure is turned into a light level. */
private const val LIGHT_CHECK_MILLIS = 250L

private const val NANOS_PER_SECOND = 1_000_000_000.0

private fun Barcode.toScannedCode(): ScannedCode? {
    val value = rawValue ?: return null
    val format = when (format) {
        Barcode.FORMAT_EAN_13 -> BarcodeFormat.Ean13
        Barcode.FORMAT_EAN_8 -> BarcodeFormat.Ean8
        Barcode.FORMAT_UPC_A -> BarcodeFormat.UpcA
        Barcode.FORMAT_UPC_E -> BarcodeFormat.UpcE
        else -> return null
    }
    return ScannedCode(value, format)
}
