package com.opson.libraries.nutrition

import kotlinx.datetime.LocalDate

/**
 * What a screen renders for one number. Screens never format a nutrition
 * value themselves; they get one of these from [VisibilityPolicy.resolve].
 *
 * `fraction` is value over goal, unclamped (a bigger day is above 1), and
 * drives fill bars. It is `null` when there is no goal to fill towards.
 */
sealed interface Display {

    /** Render nothing, the label included. */
    data object Hidden : Display

    /** A word instead of an amount. [text] is `null` for water, which is a bar with no words. */
    data class Soft(val text: String?, val fraction: Float?) : Display

    data class Shown(val text: String, val fraction: Float? = null) : Display
}

/**
 * Everything [VisibilityPolicy.resolve] needs besides the value.
 *
 * - [of]: the goal, for "47 of 120 g", soften thresholds and bar fractions.
 * - [bare]: energy without the "kcal" suffix, weight without "kg" or "lb".
 * - [day] / [dayClosed]: which day the number belongs to, for delayed reveal.
 *   `null` means today.
 * - [eaten] / [min] / [max]: only for [Metric.Remaining], to say "On target".
 * - [trendKgPerWeek]: only for a softened [Metric.Weight], the slope of the
 *   smoothed line, which picks the word (steady, rising slowly, falling
 *   slowly). Without it a softened weight says "Trend only".
 * - [goal]: how a shown amount spells [of]. It never changes soften words or
 *   `fraction`, which read [of] in every style.
 */
data class FormatContext(
    val of: Double? = null,
    val bare: Boolean = false,
    val goal: GoalStyle = GoalStyle.Default,
    val unit: WaterUnit = WaterUnit.Ml,
    val weightUnit: WeightUnit = WeightUnit.Kg,
    val day: LocalDate? = null,
    val dayClosed: Boolean = false,
    val eaten: Double? = null,
    val min: Double? = null,
    val max: Double? = null,
    val trendKgPerWeek: Double? = null,
)

/**
 * How a shown energy or macro amount mentions its goal, for the surfaces that
 * draw it differently: the Today grid shows the bare amount under a bar, the
 * logger strip uses a slash, the nutrients detail spells "of".
 *
 * Only calories, energy out and the four macros read it. Water always says
 * "of" and the rest have no goal to spell.
 */
enum class GoalStyle {
    /** Macros say "47 of 120 g" when there is a goal; energy never mentions one. */
    Default,

    /** `47 g`, `1,245 kcal`: the goal only drives the bar. */
    None,

    /** `47 of 120 g`, `1,245 kcal of 2,305`. */
    Of,

    /** `47 / 120 g`, `1,245 / 2,305`. */
    Slash,
}
