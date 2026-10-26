package com.opson.libraries.widgets

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.PaletteChoice
import com.opson.libraries.diary.ui.tone
import com.opson.system.color.Palette
import com.opson.system.color.appColors

/**
 * The person's palette as widget colours, read off the app's own theme tokens so a widget never
 * keeps a second copy of them.
 */
object WidgetPalette {

    fun colors(palette: PaletteChoice): WidgetColors = WidgetColors(
        light = swatch(palette.toPalette(), dark = false),
        dark = swatch(palette.toPalette(), dark = true),
    )

    private fun swatch(palette: Palette, dark: Boolean): WidgetSwatch {
        val colors = appColors(palette, dark)
        return WidgetSwatch(
            background = colors.bg.color.hex(),
            ink = colors.ink.color.hex(),
            muted = colors.muted.color.hex(),
            track = colors.track.color.hex(),
            bar = colors.ink.color.hex(),
            accent = colors.accent.color.hex(),
            onAccent = colors.onAccent.color.hex(),
            meals = MealSlot.meals.associate { slot ->
                val tone = colors.meal(slot.tone)
                slot.wire to WidgetTone(content = tone.content.color.hex(), container = tone.container.color.hex())
            },
        )
    }

    private fun PaletteChoice.toPalette(): Palette = when (this) {
        PaletteChoice.Pine -> Palette.Pine
        PaletteChoice.Espresso -> Palette.Espresso
        PaletteChoice.Clay -> Palette.Clay
        PaletteChoice.Plum -> Palette.Plum
    }

    @Suppress("MagicNumber")
    private fun Color.hex(): String = "#" + (toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()
}

/** `#RRGGBB` back to a colour, for the Compose lookalike and Glance. */
@Suppress("MagicNumber")
fun widgetColor(hex: String): Color = Color(0xFF000000 or hex.removePrefix("#").toLong(16))
