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

/**
 * How a day went against the person's own plan, on Trends' on-track calendar: the only colors in
 * the app that mean on or off. Each is a fill with ink on it, so the day number stays readable.
 * [off] is a soft clay, not an alarm red: a day over plan is information, not a failure, and the
 * calendar's words ("Over plan", never "Bad day") say the same. [logged] is the calendar where
 * amounts are hidden, which only says a day had something in it.
 */
@Immutable
data class PlanColors(
    val on: ColorResource,
    val near: ColorResource,
    val off: ColorResource,
    val logged: ColorResource,
)

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
    val plan: PlanColors,
)

/**
 * Slate, light. Not on the sheet, so derived:
 *
 * - **inkSoft** is ink to muted in OKLab at 0.61, the fraction the earlier palettes used.
 * - **hero** has no Slate counterpart: Round 4 draws every card on the surface. Hero blocks that
 *   remain (day close, fasting, find support) take the dark scheme's raised, which keeps white
 *   text on them at 12:1.
 * - **scrim** is the sheet's #363D4C at the handoff's 45%.
 * - **Nutrient tints** are unchanged: the light nutrient colors are the same hex as before, except
 *   carbs.
 * - **carbs** was the squash. With the accent now orange it moved to a wheat gold, #C4920A on
 *   #FBF0D0, so the Eaten bar, the breakfast tile and the logger's note never pass for the accent.
 * - **accent** is the logo's squash (#E0892B) burnt down to #A3500A. The squash itself is 2.7:1 on
 *   white and white on it is 2.7:1, so it can carry neither text nor a white label. The burnt one
 *   is text on surface (5.7), bg (5.0), raised (4.7) and its tint (4.7), and white on it is 5.7, so
 *   buttons keep white labels. It sits between the danger brick and the fiber brown: yellower
 *   than one, fuller than the other.
 * - **accentTint** is a pale peach, not the carbs tint, so a selected row never reads as breakfast.
 *
 * WCAG AA, checked by `SlateTest`: ink, inkSoft, muted and the accent pass on bg, surface and raised.
 */
internal val SlateLight = SchemeColors(
    bg = Color(0xFFEEF1F6),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFE7EBF2),
    line = Color(0xFFE0E5ED),
    ink = Color(0xFF18202E),
    inkSoft = Color(0xFF3F485A),
    muted = Color(0xFF5A6478),
    accent = Color(0xFFA3500A),
    accentTint = Color(0xFFFCE7D8),
    onAccent = Color(0xFFFFFFFF),
    hero = Color(0xFF2D3647),
    scrim = Color(0x73363D4C),
    restingEnergy = Color(0xFF7C88A6),
    activeEnergy = Color(0xFF1F7F78),
    nutrients = NutrientColors(
        protein = tone("protein", 0xFFD1477A, 0xFFFAE3EC),
        carbs = tone("carbs", 0xFFC4920A, 0xFFFBF0D0),
        fat = tone("fat", 0xFF3D7FD9, 0xFFDFEAFA),
        fiber = tone("fiber", 0xFF8E6E3A, 0xFFF1E8DC),
    ),
    plan = plan(on = 0xFFA8D8B4, near = 0xFFF3D782, off = 0xFFF2B5A0, logged = 0xFFFCE7D8),
)

/**
 * Slate, dark. Mid blue-grey rather than near-black, so off-white text does not bloom. Not on the
 * sheet, so derived or measured on the Round 4 dark screens:
 *
 * - **bg** is a step below the sheet's #1B2130 (#161B27), and the accent and data colors a step
 *   brighter than the sheet's, after the owner tried dark mode on a phone (2026-10-27): the page
 *   read flat against the cards and the macro colors read dull. Cards keep the sheet's surface.
 * - **inkSoft**, **hero**: as light.
 * - **accent** is a lit pumpkin, #FA9E5C: text on bg (8.3), surface (6.7), raised (5.8) and its
 *   tint (6.1). Redder and fuller than the logo's egg (#F0AC66), so it reads as the same orange as
 *   the light theme's and stays clear of the carbs color.
 * - **onAccent** is a near-black brown (8.8 on the accent), as the + on the add button was a
 *   near-black teal (`today-later.png`), not bg.
 * - **activeEnergy** stays a bowl teal. Burned is cool on purpose (see [Colors]).
 * - **scrim** is the sheet's #0B0E14 at 63%, measured under the add sheet (`add-sheet-half.png`).
 * - **Nutrient colors** are at least 6.9:1 on the surface. Their tints are the meal tiles
 *   (`today-select-v4.png`, `today-later.png`), carbs gold with the light theme's: #F7D67A on #3F3822.
 */
internal val SlateDark = SchemeColors(
    bg = Color(0xFF161B27),
    surface = Color(0xFF242C3C),
    raised = Color(0xFF2D3647),
    line = Color(0xFF323B4D),
    ink = Color(0xFFE6EAF2),
    inkSoft = Color(0xFFB6BECC),
    muted = Color(0xFF98A2B4),
    accent = Color(0xFFFA9E5C),
    accentTint = Color(0xFF4A2C17),
    onAccent = Color(0xFF1F120A),
    hero = Color(0xFF2D3647),
    scrim = Color(0xA10B0E14),
    restingEnergy = Color(0xFF9AA6C1),
    activeEnergy = Color(0xFF7FD8CC),
    nutrients = NutrientColors(
        protein = tone("protein", 0xFFF59CBD, 0xFF43293A),
        carbs = tone("carbs", 0xFFF7D67A, 0xFF3F3822),
        fat = tone("fat", 0xFF97BCF7, 0xFF263552),
        fiber = tone("fiber", 0xFFD9B98C, 0xFF3B3329),
    ),
    plan = plan(on = 0xFF2E6646, near = 0xFF6B5819, off = 0xFF7A3A2B, logged = 0xFF4A2C17),
)

private fun tone(name: String, content: Long, container: Long) = ToneColors(
    content = ColorResource.FromColor(Color(content), name),
    container = ColorResource.FromColor(Color(container), "$name-tint"),
)

private fun plan(on: Long, near: Long, off: Long, logged: Long) = PlanColors(
    on = ColorResource.FromColor(Color(on), "plan-on"),
    near = ColorResource.FromColor(Color(near), "plan-near"),
    off = ColorResource.FromColor(Color(off), "plan-off"),
    logged = ColorResource.FromColor(Color(logged), "plan-logged"),
)
