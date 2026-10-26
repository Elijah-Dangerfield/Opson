package com.opson.libraries.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.opson.libraries.core.Catching
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.ui.tooling.preview.Preview

/** The square a photo thumbnail takes, as in the photo estimate frame (3.7). */
val PhotoThumbnailSize = 96.dp

/**
 * A photo the person just took, small and square, cropped to fill. [jpeg] is
 * decoded once per photo; with none (previews, or bytes that do not decode)
 * it draws a quiet camera tile instead, so the layout never jumps.
 */
@Composable
fun PhotoThumbnail(
    jpeg: ByteArray?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(jpeg) { jpeg?.let { bytes -> Catching { bytes.decodeToImageBitmap() }.getOrNull() } }
    Box(
        modifier = modifier
            .size(PhotoThumbnailSize)
            .clip(AppTheme.shapes.card)
            .background(AppTheme.colors.soft)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(PhotoThumbnailSize))
        } else {
            Icon(icon = AppIcons.PhotoCamera, size = 28.dp, color = AppTheme.colors.muted)
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun PhotoThumbnailPreview() {
    PreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PhotoThumbnail(jpeg = null, contentDescription = "Meal photo")
            PhotoThumbnail(jpeg = byteArrayOf(1, 2, 3), contentDescription = "Meal photo")
        }
    }
}
