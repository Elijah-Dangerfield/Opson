@file:Suppress("MagicNumber", "DEPRECATION")

package com.opson.system.color

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.opson.libraries.ui.system.color.ColorResource

/**
 * The brand palette the user picks. It only sets the hero block, the accent (and its fill) and
 * the accent tint. Everything else (base surfaces, nutrient colors) is shared by all four.
 *
 * Pine is the default. The handoff notes the user's last preview pick was Espresso, which is
 * why palette is a setting rather than a constant.
 */
enum class Palette { Pine, Espresso, Clay, Plum }

/** A foreground color and the soft tint it sits on, e.g. an icon tile or a nutrient dot. */
@Immutable
data class ToneColors(
    val content: ColorResource,
    val container: ColorResource,
)

/**
 * The four nutrient colors. They only tell nutrients apart: none of them means over or under,
 * which is why there is no red or green here and nothing should use them as a status.
 */
@Immutable
data class NutrientColors(
    val protein: ToneColors,
    val carbs: ToneColors,
    val fat: ToneColors,
    val fiber: ToneColors,
) {
    /** Water borrows the fat blue, as in the handoff. */
    val water: ToneColors get() = fat
}

/** Which nutrient a colored element belongs to. */
enum class NutrientTone { Protein, Carbs, Fat, Fiber, Water }

/**
 * The meal a tile or chip belongs to. A UI-side mirror of the diary's `MealSlot`, kept here so
 * `:libraries:ui` does not depend on the domain; feature code maps one to the other.
 */
enum class MealTone { Breakfast, Lunch, Snack, Dinner, Uncategorized }

internal data class BrandColors(
    val hero: Color,
    val accent: Color,
    val tint: Color,
)

internal fun Palette.brand(dark: Boolean): BrandColors = when (this) {
    Palette.Pine -> if (dark) {
        BrandColors(hero = Color(0xFF1A4740), accent = Color(0xFF5CC2AE), tint = Color(0xFF183A35))
    } else {
        BrandColors(hero = Color(0xFF143D37), accent = Color(0xFF1D7A6B), tint = Color(0xFFDCEFEA))
    }

    Palette.Espresso -> if (dark) {
        BrandColors(hero = Color(0xFF3A2D26), accent = Color(0xFFE39A6F), tint = Color(0xFF3A2A20))
    } else {
        BrandColors(hero = Color(0xFF2D231E), accent = Color(0xFFA4512D), tint = Color(0xFFF5E4DA))
    }

    Palette.Clay -> if (dark) {
        BrandColors(hero = Color(0xFF5A2A1E), accent = Color(0xFFEE8D6E), tint = Color(0xFF3E2219))
    } else {
        BrandColors(hero = Color(0xFF6B3324), accent = Color(0xFFB0472C), tint = Color(0xFFF7E2DA))
    }

    Palette.Plum -> if (dark) {
        BrandColors(hero = Color(0xFF3A2149), accent = Color(0xFFB79AE6), tint = Color(0xFF33274A))
    } else {
        BrandColors(hero = Color(0xFF2E1A3B), accent = Color(0xFF6B3FA0), tint = Color(0xFFEDE5F6))
    }
}

internal data class BaseColors(
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val track: Color,
    val soft: Color,
    val onAccent: Color,
)

internal val LightBase = BaseColors(
    bg = Color(0xFFF5F2F4),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF1B1520),
    muted = Color(0xFF5E5664),
    line = Color(0xFFE7E1E8),
    track = Color(0xFFE9E3EA),
    soft = Color(0xFFEFE9F1),
    onAccent = Color(0xFFFFFFFF),
)

internal val DarkBase = BaseColors(
    bg = Color(0xFF141016),
    surface = Color(0xFF1F1922),
    ink = Color(0xFFF2EDF4),
    muted = Color(0xFFABA2B0),
    line = Color(0xFF2F2733),
    track = Color(0xFF2F2733),
    soft = Color(0xFF2A222E),
    onAccent = Color(0xFF141016),
)

private fun tone(name: String, content: Long, container: Long) = ToneColors(
    content = ColorResource.FromColor(Color(content), name),
    container = ColorResource.FromColor(Color(container), "$name-tint"),
)

internal val LightNutrients = NutrientColors(
    protein = tone("protein", 0xFFD1477A, 0xFFFAE3EC),
    carbs = tone("carbs", 0xFFE0892B, 0xFFFCEBD8),
    fat = tone("fat", 0xFF3D7FD9, 0xFFDFEAFA),
    fiber = tone("fiber", 0xFF8E6E3A, 0xFFF1E8DC),
)

internal val DarkNutrients = NutrientColors(
    protein = tone("protein", 0xFFEE77A0, 0xFF3D2230),
    carbs = tone("carbs", 0xFFF2A65A, 0xFF3D2C1A),
    fat = tone("fat", 0xFF6FA4EE, 0xFF1C2B40),
    fiber = tone("fiber", 0xFFC29A62, 0xFF33291C),
)
