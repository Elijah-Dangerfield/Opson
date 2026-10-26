@file:Suppress("MagicNumber", "DEPRECATION")

package com.opson.system.color

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.opson.libraries.ui.system.color.ColorResource

/**
 * The brand palette the user picks. It sets the hero block, the accent (and its fill) and the
 * accent tint, and tints the neutrals (page, text, lines) toward its own hue, see [base]. The
 * nutrient colors are shared by all four.
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
    val inkSoft: Color,
    val muted: Color,
    val line: Color,
    val track: Color,
    val soft: Color,
    val onAccent: Color,
)

/**
 * The neutrals, tinted toward each palette (design v3).
 *
 * v3 exports only Pine light. Its neutrals are green-grey, not the old handoff's pink-grey: in
 * OKLCH every one of them sits on Pine's hue. The rule that reproduces them, and that the other
 * palettes and dark mode follow, is:
 *
 * - **Hue** is the palette's light hero hue: Pine 182.2, Espresso 48.0, Clay 36.0, Plum 310.6.
 * - **Light, lightness and chroma** are the ones measured on the v3 Pine images, the same in
 *   every palette:
 *
 *   | token   | Pine hex | L      | C      |
 *   |---------|----------|--------|--------|
 *   | bg      | F2F4F3   | 0.9654 | 0.0025 |
 *   | surface | FFFFFF   | 1      | 0      |
 *   | ink     | 16201E   | 0.2331 | 0.0148 |
 *   | inkSoft | 3E4846   | 0.3920 | 0.0134 |
 *   | muted   | 5A6461   | 0.4936 | 0.0132 |
 *   | line    | E3E7E5   | 0.9243 | 0.0050 |
 *   | track   | E6EAE8   | 0.9334 | 0.0050 |
 *   | soft    | EAEEEC   | 0.9455 | 0.0050 |
 *
 * - **Dark** keeps the handoff's dark lightness for each token and takes its chroma down by the
 *   ratio v3 took the same light token down (v3 C / handoff light C): bg and surface 0.60, ink
 *   0.64, muted 0.54, line and track 0.44, soft 0.40. Dark [BaseColors.inkSoft] sits between ink
 *   and muted at the same lightness fraction as light (0.61).
 *
 * Pine light is the measured hex. Everything else is the rule's output rounded to 8 bits, which
 * `NeutralsTest` checks, along with WCAG AA contrast for every text token on bg and surface.
 * Surface stays white in light, and [BaseColors.onAccent] is white in light and the bg in dark.
 */
internal fun Palette.base(dark: Boolean): BaseColors = when (this) {
    Palette.Pine -> if (dark) {
        base(0xFF0E1312, 0xFF161D1C, 0xFFEAF1EF, 0xFFBBC4C2, 0xFF9EA8A6, 0xFF252C2B, 0xFF252C2B, 0xFF202726, dark = true)
    } else {
        base(0xFFF2F4F3, 0xFFFFFFFF, 0xFF16201E, 0xFF3E4846, 0xFF5A6461, 0xFFE3E7E5, 0xFFE6EAE8, 0xFFEAEEEC, dark = false)
    }

    Palette.Espresso -> if (dark) {
        base(0xFF15110F, 0xFF201A17, 0xFFF3EEEB, 0xFFC8C0BC, 0xFFADA49F, 0xFF2F2926, 0xFF2F2926, 0xFF2A2421, dark = true)
    } else {
        base(0xFFF5F3F2, 0xFFFFFFFF, 0xFF241C18, 0xFF4C443F, 0xFF685F5B, 0xFFE9E5E3, 0xFFECE8E6, 0xFFF0ECEA, dark = false)
    }

    Palette.Clay -> if (dark) {
        base(0xFF15110F, 0xFF211A18, 0xFFF4EEEC, 0xFFC8C0BD, 0xFFADA3A1, 0xFF302927, 0xFF302927, 0xFF2A2422, dark = true)
    } else {
        base(0xFFF5F3F2, 0xFFFFFFFF, 0xFF241B19, 0xFF4C4341, 0xFF695F5C, 0xFFE9E5E4, 0xFFECE8E7, 0xFFF0ECEB, dark = false)
    }

    Palette.Plum -> if (dark) {
        base(0xFF131114, 0xFF1D1A20, 0xFFF0EEF3, 0xFFC4C0C7, 0xFFA8A4AC, 0xFF2C292F, 0xFF2C292F, 0xFF272429, dark = true)
    } else {
        base(0xFFF4F3F5, 0xFFFFFFFF, 0xFF201C23, 0xFF48444B, 0xFF645F67, 0xFFE7E5E8, 0xFFEAE8EB, 0xFFEEECEF, dark = false)
    }
}

@Suppress("LongParameterList")
private fun base(
    bg: Long,
    surface: Long,
    ink: Long,
    inkSoft: Long,
    muted: Long,
    line: Long,
    track: Long,
    soft: Long,
    dark: Boolean,
) = BaseColors(
    bg = Color(bg),
    surface = Color(surface),
    ink = Color(ink),
    inkSoft = Color(inkSoft),
    muted = Color(muted),
    line = Color(line),
    track = Color(track),
    soft = Color(soft),
    onAccent = if (dark) Color(bg) else Color.White,
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
