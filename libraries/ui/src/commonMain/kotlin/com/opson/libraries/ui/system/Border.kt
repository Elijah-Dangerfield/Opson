package com.opson.libraries.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.system.AppTheme
import com.opson.libraries.ui.system.color.ColorResource

@Immutable
data class Border(val color: ColorResource, val width: Dp = StandardBorderWidth) {
    companion object {
        val Standard: Border
            @ReadOnlyComposable
            @Composable
            get() = Border(AppTheme.colors.border)
    }
}

val StandardBorderWidth = 1.dp
