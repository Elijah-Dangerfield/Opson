package com.opson.libraries.scanner.impl

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.opson.libraries.scanner.ScannedCode
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
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

    @Composable
    override fun Viewfinder(onCode: (ScannedCode) -> Unit, modifier: Modifier) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val latestOnCode by rememberUpdatedState(onCode)
        val previewView = remember(context) {
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                // A TextureView, so the preview clips and layers like any other composable.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        }
        DisposableEffect(lifecycleOwner, previewView) {
            val session = CameraSession(context, lifecycleOwner, previewView) { code -> latestOnCode(code) }
            session.start()
            onDispose { session.stop() }
        }
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

/** One bound camera: preview plus analysis, unbound on [stop]. */
private class CameraSession(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onCode: (ScannedCode) -> Unit,
) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build(),
    )
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null
    private var stopped = false

    fun start() {
        val future = ProcessCameraProvider.getInstance(context)
        val main = ContextCompat.getMainExecutor(context)
        future.addListener(
            {
                // The provider can arrive after the screen has already gone.
                if (stopped) return@addListener
                Catching {
                    val cameraProvider = future.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(analysisExecutor) { proxy -> read(proxy) { code -> main.execute { if (!stopped) onCode(code) } } } }
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    provider = cameraProvider
                    this.preview = preview
                    this.analysis = analysis
                }.logOnFailure("Camera could not start")
            },
            main,
        )
    }

    fun stop() {
        stopped = true
        val bound = listOfNotNull(preview, analysis)
        if (bound.isNotEmpty()) provider?.unbind(*bound.toTypedArray())
        analysis?.clearAnalyzer()
        analysisExecutor.shutdown()
        scanner.close()
    }

    @OptIn(ExperimentalGetImage::class)
    private fun read(proxy: ImageProxy, onCode: (ScannedCode) -> Unit) {
        val image = proxy.image
        if (image == null) {
            proxy.close()
            return
        }
        scanner.process(InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { barcodes -> barcodes.firstNotNullOfOrNull { it.toScannedCode() }?.let(onCode) }
            // Closing the frame is what lets the next one in, success or not.
            .addOnCompleteListener { proxy.close() }
    }
}

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
