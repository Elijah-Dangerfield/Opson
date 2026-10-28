package com.opson.libraries.ui.system

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.color.Colors
import com.opson.system.typography.TypeScale
import com.opson.system.typography.Typography

val LocalColors = compositionLocalOf<Colors> {
    error("Theme wasn't applied")
}

val LocalContentColor = compositionLocalOf<ColorResource> {
    error("Theme wasn't applied")
}

val LocalTypography = compositionLocalOf<Typography> {
    error("Theme wasn't applied")
}

/** Defaults to the system font at the normal base so a composable outside the theme still lays out. */
val LocalTypeScale = staticCompositionLocalOf<TypeScale> { TypeScale.Fallback }
