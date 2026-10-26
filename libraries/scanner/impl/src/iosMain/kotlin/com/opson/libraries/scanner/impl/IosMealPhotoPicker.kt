package com.opson.libraries.scanner.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.scanner.MealPhoto
import com.opson.libraries.scanner.MealPhotoPicker
import com.opson.libraries.scanner.PhotoPickResult
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import me.tatarka.inject.annotations.Inject
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.math.max

/**
 * `UIImagePickerController` for the camera and `PHPickerViewController` for
 * the library, in Kotlin/Native like the barcode camera. PHPicker runs out of
 * process and hands back only the photo the person chose, so the app needs no
 * photo-library permission and declares no `NSPhotoLibraryUsageDescription`.
 * Both are presented over the top view controller and dismissed before the
 * answer is delivered. The photo is shrunk off the main thread and only ever
 * held in memory.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosMealPhotoPicker @Inject constructor() : MealPhotoPicker {

    override val canTakePhoto: Boolean
        get() = UIImagePickerController.isSourceTypeAvailable(
            UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
        )

    @Composable
    override fun rememberCamera(onResult: (PhotoPickResult) -> Unit): () -> Unit {
        val latestOnResult by rememberUpdatedState(onResult)
        // The delegate is held here because UIKit keeps only a weak reference to it.
        val delegate = remember { CameraDelegate { result -> latestOnResult(result) } }
        return remember(delegate) {
            {
                val presenter = topViewController()
                if (presenter == null || !canTakePhoto) {
                    latestOnResult(PhotoPickResult.Failed)
                } else {
                    val picker = UIImagePickerController().apply {
                        sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                        this.delegate = delegate
                    }
                    presenter.presentViewController(picker, animated = true, completion = null)
                }
            }
        }
    }

    @Composable
    override fun rememberLibrary(onResult: (PhotoPickResult) -> Unit): () -> Unit {
        val latestOnResult by rememberUpdatedState(onResult)
        val delegate = remember { LibraryDelegate { result -> latestOnResult(result) } }
        return remember(delegate) {
            {
                val presenter = topViewController()
                if (presenter == null) {
                    latestOnResult(PhotoPickResult.Failed)
                } else {
                    val configuration = PHPickerConfiguration().apply {
                        filter = PHPickerFilter.imagesFilter
                        selectionLimit = 1
                    }
                    val picker = PHPickerViewController(configuration).apply { this.delegate = delegate }
                    presenter.presentViewController(picker, animated = true, completion = null)
                }
            }
        }
    }
}

private class CameraDelegate(
    private val onResult: (PhotoPickResult) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true) {
            if (image == null) onResult(PhotoPickResult.Failed) else shrinkInBackground({ image }, onResult)
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true) { onResult(PhotoPickResult.Cancelled) }
    }
}

private class LibraryDelegate(
    private val onResult: (PhotoPickResult) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider
        picker.dismissViewControllerAnimated(true) {
            if (provider == null) {
                onResult(PhotoPickResult.Cancelled)
                return@dismissViewControllerAnimated
            }
            // Delivered on a background queue; any image type the system can decode.
            provider.loadDataRepresentationForTypeIdentifier(IMAGE_TYPE) { data, _ ->
                if (data == null) {
                    dispatch_async(dispatch_get_main_queue()) { onResult(PhotoPickResult.Failed) }
                } else {
                    shrinkInBackground({ UIImage.imageWithData(data) }, onResult)
                }
            }
        }
    }
}

/** Any image type the system can decode (UTType.image). */
private const val IMAGE_TYPE = "public.image"

/** Shrinks off the main thread and answers on it. */
private fun shrinkInBackground(image: () -> UIImage?, onResult: (PhotoPickResult) -> Unit) {
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
        val photo = Catching { image()?.let(::shrink) }.logOnFailure("Couldn't read the photo").getOrNull()
        dispatch_async(dispatch_get_main_queue()) {
            onResult(photo?.let(PhotoPickResult::Picked) ?: PhotoPickResult.Failed)
        }
    }
}

/**
 * Redraws [image] with its long edge at most [MealPhoto.MAX_EDGE_PX] pixels.
 * Drawing applies the photo's orientation, so the JPEG comes out upright.
 */
@OptIn(ExperimentalForeignApi::class)
private fun shrink(image: UIImage): MealPhoto? {
    val (width, height) = image.size.useContents { width to height }
    if (width <= 0.0 || height <= 0.0) return null
    val scale = minOf(1.0, MealPhoto.MAX_EDGE_PX / max(width, height))
    val target = CGSizeMake(width * scale, height * scale)
    val format = UIGraphicsImageRendererFormat().apply { this.scale = 1.0 }
    val drawn = UIGraphicsImageRenderer(target, format).imageWithActions { _ ->
        image.drawInRect(CGRectMake(0.0, 0.0, width * scale, height * scale))
    }
    val data = UIImageJPEGRepresentation(drawn, MealPhoto.JPEG_QUALITY) ?: return null
    return MealPhoto(data.toByteArray())
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}

/** The controller on top, so a picker opened over a sheet appears over it. */
private fun topViewController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
