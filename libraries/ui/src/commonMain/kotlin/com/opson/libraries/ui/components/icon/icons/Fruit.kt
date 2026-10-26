package com.opson.libraries.ui.components.icon.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A piece of fruit with a leaf, for a food tile. Material's Rounded set has no fruit glyph
 * (Symbols' `nutrition` is not in it), so this is drawn on the same 24 grid.
 */
val Fruit: ImageVector by lazy {
    ImageVector.Builder(
        name = "Fruit",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 8.6f)
            curveTo(10.4f, 7.1f, 4.6f, 6.6f, 5f, 13f)
            curveTo(5.3f, 17.5f, 8f, 21f, 10f, 21f)
            curveTo(11f, 21f, 11.3f, 20.5f, 12f, 20.5f)
            curveTo(12.7f, 20.5f, 13f, 21f, 14f, 21f)
            curveTo(16f, 21f, 18.7f, 17.5f, 19f, 13f)
            curveTo(19.4f, 6.6f, 13.6f, 7.1f, 12f, 8.6f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(12.6f, 7.4f)
            curveTo(12.6f, 5f, 14.1f, 3.4f, 16.6f, 3.4f)
            curveTo(16.6f, 5.9f, 15.1f, 7.4f, 12.6f, 7.4f)
            close()
        }
    }.build()
}
