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
 * - [of]: the goal, for "47 g of 120 g", soften thresholds and bar fractions.
 * - [bare]: energy without the "kcal" suffix.
 * - [day] / [dayClosed]: which day the number belongs to, for delayed reveal.
 *   `null` means today.
 * - [eaten] / [min] / [max]: only for [Metric.Remaining], to say "On target".
 */
data class FormatContext(
    val of: Double? = null,
    val bare: Boolean = false,
    val unit: WaterUnit = WaterUnit.Ml,
    val weightUnit: WeightUnit = WeightUnit.Kg,
    val day: LocalDate? = null,
    val dayClosed: Boolean = false,
    val eaten: Double? = null,
    val min: Double? = null,
    val max: Double? = null,
)
