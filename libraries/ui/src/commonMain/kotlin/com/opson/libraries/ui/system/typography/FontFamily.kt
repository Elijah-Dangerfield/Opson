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
import opson.libraries.resources.generated.resources.Res as SharedRes
import opson.libraries.ui.generated.resources.Res
import opson.libraries.ui.generated.resources.lust_script_regular

/**
 * Atkinson Hyperlegible Next (SIL OFL 1.1, license next to the files in `:libraries:resources`),
 * the app's only text face. Static instances of the five weights the handoff uses rather than the
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

val BrandFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(
            resource = Res.font.lust_script_regular, weight = FontWeight.Normal
        ),
    )
