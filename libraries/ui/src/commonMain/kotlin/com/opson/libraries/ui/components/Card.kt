package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.Radii
import com.opson.libraries.ui.Elevation
import com.opson.libraries.ui.system.color.ColorResource

@Composable
fun Card(
    modifier: Modifier = Modifier,
    color: ColorResource? = AppTheme.colors.surfacePrimary,
    contentColor: ColorResource = AppTheme.colors.onSurfacePrimary,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = color,
        contentColor = contentColor,
        onClick = onClick ?: {},
        bounceScale = if (onClick != null) 0.9f else 1f,
        elevation = Elevation.None,
        radius = Radii.Card,
        contentPadding = EdgeCardDefaults.ContentPadding
    ) {
        content()
    }
}


@Composable
fun CardSecondary(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = AppTheme.colors.surfaceSecondary,
        contentColor = AppTheme.colors.onSurfaceSecondary,
        onClick = onClick ?: {},
        bounceScale = if (onClick != null) 0.9f else 1f,
        elevation = Elevation.None,
        radius = Radii.Card,
        contentPadding = EdgeCardDefaults.ContentPadding
    ) {
        content()
    }
}
