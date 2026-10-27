package com.opson.system.typography

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import opson.libraries.resources.generated.resources.atkinson_hyperlegible_next_bold
import opson.libraries.resources.generated.resources.atkinson_hyperlegible_next_extrabold
import opson.libraries.resources.generated.resources.atkinson_hyperlegible_next_medium
import opson.libraries.resources.generated.resources.atkinson_hyperlegible_next_regular
import opson.libraries.resources.generated.resources.atkinson_hyperlegible_next_semibold
import opson.libraries.resources.generated.resources.young_serif_regular
import opson.libraries.resources.generated.resources.Res as SharedRes

/**
 * Atkinson Hyperlegible Next (SIL OFL 1.1, license next to the files in `:libraries:resources`),
 * the app's text face. Static instances of the five weights the handoff uses rather than the
 * variable font, so every platform resolves the same glyphs without relying on variation support.
 */
val AppFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(resource = SharedRes.font.atkinson_hyperlegible_next_regular, weight = FontWeight.Normal),
        Font(resource = SharedRes.font.atkinson_hyperlegible_next_medium, weight = FontWeight.Medium),
        Font(resource = SharedRes.font.atkinson_hyperlegible_next_semibold, weight = FontWeight.SemiBold),
        Font(resource = SharedRes.font.atkinson_hyperlegible_next_bold, weight = FontWeight.Bold),
        Font(resource = SharedRes.font.atkinson_hyperlegible_next_extrabold, weight = FontWeight.ExtraBold),
    )

/**
 * The wordmark's face: Young Serif (SIL OFL 1.1, license next to the file in `:libraries:resources`),
 * the logo lockup's typeface. It has one weight, so every Brand style sets Normal.
 */
val BrandFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(resource = SharedRes.font.young_serif_regular, weight = FontWeight.Normal),
    )
