package com.opson.libraries.scanner.impl

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.scanner.MealPhoto
import com.opson.libraries.scanner.MealPhotoPicker
import com.opson.libraries.scanner.PhotoPickResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The system camera app (`TakePicture`) and the system photo picker
 * (`PickVisualMedia`, which needs no storage permission and falls back to the
 * document picker where the photo picker is missing). The camera writes into
 * one file under the cache, behind [MealPhotoFileProvider]; it is read, shrunk
 * and deleted at once, so no photo outlives the estimate.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidMealPhotoPicker @Inject constructor(
    private val context: Context,
) : MealPhotoPicker {

    override val canTakePhoto: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    @Composable
    override fun rememberCamera(onResult: (PhotoPickResult) -> Unit): () -> Unit {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val latestOnResult by rememberUpdatedState(onResult)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            val file = captureFile(context)
            if (!saved) {
                file.delete()
                latestOnResult(PhotoPickResult.Cancelled)
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val photo = withContext(Dispatchers.Default) {
                    Catching { shrink(context, Uri.fromFile(file)) }.logOnFailure("Couldn't read the camera's photo").getOrNull()
                }
                file.delete()
                latestOnResult(photo?.let(PhotoPickResult::Picked) ?: PhotoPickResult.Failed)
            }
        }
        return remember(launcher) {
            {
                val file = captureFile(context).apply {
                    parentFile?.mkdirs()
                    delete()
                }
                val uri = FileProvider.getUriForFile(context, MealPhotoFileProvider.authority(context), file)
                try {
                    launcher.launch(uri)
                } catch (_: ActivityNotFoundException) {
                    latestOnResult(PhotoPickResult.Failed)
                }
            }
        }
    }

    @Composable
    override fun rememberLibrary(onResult: (PhotoPickResult) -> Unit): () -> Unit {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val latestOnResult by rememberUpdatedState(onResult)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) {
                latestOnResult(PhotoPickResult.Cancelled)
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val photo = withContext(Dispatchers.Default) {
                    Catching { shrink(context, uri) }.logOnFailure("Couldn't read the picked photo").getOrNull()
                }
                latestOnResult(photo?.let(PhotoPickResult::Picked) ?: PhotoPickResult.Failed)
            }
        }
        return remember(launcher) {
            {
                try {
                    launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } catch (_: ActivityNotFoundException) {
                    latestOnResult(PhotoPickResult.Failed)
                }
            }
        }
    }

    private fun captureFile(context: Context): File =
        File(File(context.cacheDir, MealPhotoFileProvider.DIRECTORY), CAPTURE_NAME)

    private companion object {
        const val CAPTURE_NAME = "capture.jpg"
    }
}

/**
 * Decodes [uri] at a size near [MealPhoto.MAX_EDGE_PX], turns it upright from
 * its EXIF orientation, scales the long edge down to the limit and re-encodes
 * it as JPEG. Sampling while decoding keeps a 50 MP photo from ever being
 * held at full size.
 */
private fun shrink(context: Context, uri: Uri): MealPhoto {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    val longEdge = max(bounds.outWidth, bounds.outHeight)
    require(longEdge > 0) { "not an image" }

    var sample = 1
    while (longEdge / (sample * 2) >= MealPhoto.MAX_EDGE_PX) sample *= 2
    val decoded = resolver.openInputStream(uri).use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("not an image")

    val orientation = Catching {
        resolver.openInputStream(uri).use { stream ->
            ExifInterface(checkNotNull(stream)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

    val scale = MealPhoto.MAX_EDGE_PX.toFloat() / max(decoded.width, decoded.height)
    val matrix = Matrix().apply {
        if (scale < 1f) postScale(scale, scale)
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(QUARTER)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(HALF)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(THREE_QUARTERS)
        }
    }
    val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    val jpeg = ByteArrayOutputStream().use { out ->
        upright.compress(Bitmap.CompressFormat.JPEG, (MealPhoto.JPEG_QUALITY * PERCENT).roundToInt(), out)
        out.toByteArray()
    }
    if (upright !== decoded) upright.recycle()
    decoded.recycle()
    return MealPhoto(jpeg)
}

private const val QUARTER = 90f
private const val HALF = 180f
private const val THREE_QUARTERS = 270f
private const val PERCENT = 100

/**
 * The camera app writes its photo through a content URI with a one-off write
 * grant, so Opson needs no storage permission. Only one cache subdirectory is
 * exposed. The authority must match this module's manifest.
 */
class MealPhotoFileProvider : FileProvider() {
    internal companion object {
        /** Under the cache dir, matching the `cache-path` in `meal_photo_paths.xml`. */
        const val DIRECTORY = "meal_photo"

        fun authority(context: Context): String = "${context.packageName}.mealphoto"
    }
}
