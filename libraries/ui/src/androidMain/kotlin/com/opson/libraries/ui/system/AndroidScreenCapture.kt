package com.opson.libraries.ui.system

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.io.ByteArrayOutputStream

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = ScreenCapture::class)
class AndroidScreenCapture : LayerScreenCapture() {
    override fun encode(bitmap: ImageBitmap, size: IntSize): ByteArray {
        val drawn = bitmap.asAndroidBitmap()
        // A layer's bitmap can live on the GPU, and scaling and compressing need its pixels in memory.
        val readable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawn.config == Bitmap.Config.HARDWARE) {
            drawn.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            drawn
        }
        val scaled = if (readable.width == size.width && readable.height == size.height) {
            readable
        } else {
            Bitmap.createScaledBitmap(readable, size.width, size.height, true)
        }
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, ScreenCapture.JPEG_QUALITY, out)
            out.toByteArray()
        }
    }
}
