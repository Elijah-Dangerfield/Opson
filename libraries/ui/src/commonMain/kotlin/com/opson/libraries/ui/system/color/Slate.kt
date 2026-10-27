@file:Suppress("MagicNumber")

package com.opson.system.color

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.opson.libraries.ui.system.color.ColorResource

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

/**
 * One theme's raw colors. [Colors] names and wraps them; nothing else reads this.
 *
 * Slate is the app's only scheme (design sync 4, `colors-slate.png`, designRef 13.0). Most values
 * are the sheet's hex; [SlateLight] and [SlateDark] say which are not and where they came from.
 */
internal data class SchemeColors(
    val bg: Color,
    val surface: Color,
    /** The sheet's "Raised": tiles, the select-mode bar, round buttons on a card. */
    val raised: Color,
    val line: Color,
    val ink: Color,
    val inkSoft: Color,
    val muted: Color,
    val accent: Color,
    val accentTint: Color,
    val onAccent: Color,
    val hero: Color,
    /** The sheet's scrim color, already at the alpha a sheet uses. */
    val scrim: Color,
    val restingEnergy: Color,
    val activeEnergy: Color,
    val nutrients: NutrientColors,
)

/**
 * Slate, light. Not on the sheet, so derived:
 *
 * - **inkSoft** is ink to muted in OKLab at 0.61, the fraction the earlier palettes used.
 * - **hero** has no Slate counterpart: Round 4 draws every card on the surface. Hero blocks that
 *   remain (day close, fasting, find support) take the dark scheme's raised, which keeps white
 *   text on them at 12:1.
 * - **scrim** is the sheet's #363D4C at the handoff's 45%.
 * - **Nutrient tints** are unchanged: the light nutrient colors are the same hex as before.
 *
 * WCAG AA, checked by `SlateTest`: ink, inkSoft and muted pass on bg, surface and raised. The
 * accent passes as text on the surface (4.8) but not on bg (4.25) or raised (4.0), so accent text
 * belongs on a card; on bg it is for icons, fills and large text, which need 3:1.
 */
internal val SlateLight = SchemeColors(
    bg = Color(0xFFEEF1F6),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFE7EBF2),
    line = Color(0xFFE0E5ED),
    ink = Color(0xFF18202E),
    inkSoft = Color(0xFF3F485A),
    muted = Color(0xFF5A6478),
    // The design's #1F7F78, one step darker so accent text passes AA on the page, raised and tint too.
    accent = Color(0xFF0F766F),
    accentTint = Color(0xFFD8EEEB),
    onAccent = Color(0xFFFFFFFF),
    hero = Color(0xFF2D3647),
    scrim = Color(0x73363D4C),
    restingEnergy = Color(0xFF7C88A6),
    activeEnergy = Color(0xFF1F7F78),
    nutrients = NutrientColors(
        protein = tone("protein", 0xFFD1477A, 0xFFFAE3EC),
        carbs = tone("carbs", 0xFFE0892B, 0xFFFCEBD8),
        fat = tone("fat", 0xFF3D7FD9, 0xFFDFEAFA),
        fiber = tone("fiber", 0xFF8E6E3A, 0xFFF1E8DC),
    ),
)

/**
 * Slate, dark. Mid blue-grey rather than near-black, so off-white text does not bloom. Not on the
 * sheet, so derived or measured on the Round 4 dark screens:
 *
 * - **inkSoft**, **hero**: as light.
 * - **onAccent** is the near-black teal of the + on the add button (`today-later.png`), not bg.
 * - **scrim** is the sheet's #0B0E14 at 63%, measured under the add sheet (`add-sheet-half.png`).
 * - **Nutrient tints** are the meal tiles (`today-select-v4.png`, `today-later.png`).
 */
internal val SlateDark = SchemeColors(
    bg = Color(0xFF1B2130),
    surface = Color(0xFF242C3C),
    raised = Color(0xFF2D3647),
    line = Color(0xFF323B4D),
    ink = Color(0xFFE6EAF2),
    inkSoft = Color(0xFFB6BECC),
    muted = Color(0xFF98A2B4),
    accent = Color(0xFF6CCBBF),
    accentTint = Color(0xFF22403F),
    onAccent = Color(0xFF0F1E1D),
    hero = Color(0xFF2D3647),
    scrim = Color(0xA10B0E14),
    restingEnergy = Color(0xFF8A96B2),
    activeEnergy = Color(0xFF6CCBBF),
    nutrients = NutrientColors(
        protein = tone("protein", 0xFFEE8BAE, 0xFF43293A),
        carbs = tone("carbs", 0xFFF0AC66, 0xFF43352A),
        fat = tone("fat", 0xFF84AEF0, 0xFF263552),
        fiber = tone("fiber", 0xFFCBA97A, 0xFF3B3329),
    ),
)

private fun tone(name: String, content: Long, container: Long) = ToneColors(
    content = ColorResource.FromColor(Color(content), name),
    container = ColorResource.FromColor(Color(container), "$name-tint"),
)
