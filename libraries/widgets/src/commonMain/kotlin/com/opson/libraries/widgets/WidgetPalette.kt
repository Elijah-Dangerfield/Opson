package com.opson.libraries.widgets

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.tone
import com.opson.system.color.appColors

/**
 * The app's colours as widget colours, read off its own theme tokens so a widget never keeps a
 * second copy of them.
 */
object WidgetPalette {

    val colors: WidgetColors = WidgetColors(light = swatch(dark = false), dark = swatch(dark = true))

    private fun swatch(dark: Boolean): WidgetSwatch {
        val colors = appColors(dark)
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

    @Suppress("MagicNumber")
    private fun Color.hex(): String = "#" + (toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()
}

/** `#RRGGBB` back to a colour, for the Compose lookalike and Glance. */
@Suppress("MagicNumber")
fun widgetColor(hex: String): Color = Color(0xFF000000 or hex.removePrefix("#").toLong(16))
