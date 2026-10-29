@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.ui.system

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.unit.IntSize
import me.tatarka.inject.annotations.Inject
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = ScreenCapture::class)
class IosScreenCapture : LayerScreenCapture() {
    override fun encode(bitmap: ImageBitmap, size: IntSize): ByteArray? {
        val image = Image.makeFromBitmap(bitmap.asSkiaBitmap())
        val surface = Surface.makeRasterN32Premul(size.width, size.height)
        surface.canvas.drawImageRect(image, Rect.makeWH(size.width.toFloat(), size.height.toFloat()))
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, ScreenCapture.JPEG_QUALITY)?.bytes
    }
}
