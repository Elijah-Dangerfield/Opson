package com.opson.libraries.ui.components.icon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.Radii
import com.opson.system.thenIf
import com.opson.libraries.ui.Elevation
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.components.Surface
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.opson.libraries.ui.system.minTouchTarget

@Composable
fun CircleIcon(
    icon: IconResource?,
    iconSize: IconSize,
    modifier: Modifier = Modifier,
    padding: Dp = 0.dp,
    backgroundColor: ColorResource = AppTheme.colors.surfacePrimary,
    contentColor: ColorResource = AppTheme.colors.onSurfacePrimary,
    elevation: Elevation = Elevation.None,
    onClick: (() -> Unit)? = null
) {
    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        contentPadding = PaddingValues(padding),
        elevation = elevation,
        radius = Radii.Round,
        modifier = modifier
            .thenIf(onClick != null) {
                minTouchTarget().clickable { onClick?.invoke() }
            }
    ) {
        icon?.let {
            Icon(
                icon = icon,
                size = iconSize
            )
        } ?: Box(modifier = Modifier.size(iconSize.dp))
    }
}

@Preview
@Composable
private fun CircularIconPreview() {
    PreviewContent(backgroundColor = null) {
        com.opson.libraries.ui.components.icon.CircleIcon(
            icon = Icons.Check("Test"),
            iconSize = IconSize.Large,
            padding = Dimension.D400,
            backgroundColor = AppTheme.colors.background,
            contentColor = AppTheme.colors.onBackground
        )
    }
}
