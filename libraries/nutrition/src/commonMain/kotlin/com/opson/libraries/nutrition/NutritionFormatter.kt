package com.opson.libraries.nutrition

import kotlin.math.abs
import kotlin.math.roundToLong

/** The soften vocabulary. The policy picks the word; a [NutritionFormatter] spells it. */
enum class SoftWord {
    EarlyInTheDay,
    PartwayThrough,
    MostOfTheWay,
    InYourRange,
    ABiggerDay,
    Building,
    GettingThere,
    PlentyToday,
    Some,
    Most,
    Plenty,
    TrendOnly,
}

/**
 * Turns numbers the policy has already decided to show into copy. Kept apart
 * from [VisibilityPolicy] so the UI can supply localized strings later without
 * touching any visibility rule. Implementations only spell; they never decide
 * whether something is shown, softened or hidden.
 *
 * Amounts arrive in the unit they are displayed in (fl oz, lb), unrounded.
 */
interface NutritionFormatter {
    fun energy(kcal: Double, bare: Boolean): String
    fun grams(grams: Double, of: Double?): String
    fun percent(percent: Double): String
    fun weight(amount: Double, unit: WeightUnit): String
    fun water(amount: Double, of: Double, unit: WaterUnit): String
    fun left(kcal: Double): String
    fun onTarget(): String
    fun aboveRange(): String
    fun soft(word: SoftWord): String
}

/** The prototype's strings, word for word. */
object EnglishNutritionFormatter : NutritionFormatter {

    override fun energy(kcal: Double, bare: Boolean): String =
        if (bare) wholeNumber(kcal) else "${wholeNumber(kcal)} kcal"

    override fun grams(grams: Double, of: Double?): String =
        if (of == null) "${wholeNumber(grams)} g" else "${wholeNumber(grams)} g of ${wholeNumber(of)} g"

    override fun percent(percent: Double): String = "${wholeNumber(percent)}%"

    override fun weight(amount: Double, unit: WeightUnit): String = when (unit) {
        WeightUnit.Kg -> "${oneDecimal(amount)} kg"
        WeightUnit.Lb -> "${oneDecimal(amount)} lb"
    }

    override fun water(amount: Double, of: Double, unit: WaterUnit): String = when (unit) {
        WaterUnit.Ml -> "${wholeNumber(amount)} of ${wholeNumber(of)} ml"
        WaterUnit.FlOz -> "${wholeNumber(amount)} of ${wholeNumber(of)} fl oz"
    }

    override fun left(kcal: Double): String = "${wholeNumber(kcal)} left"

    override fun onTarget(): String = "On target"

    override fun aboveRange(): String = "Above your range today"

    override fun soft(word: SoftWord): String = when (word) {
        SoftWord.EarlyInTheDay -> "Early in the day"
        SoftWord.PartwayThrough -> "Partway through"
        SoftWord.MostOfTheWay -> "Most of the way"
        SoftWord.InYourRange -> "In your range"
        SoftWord.ABiggerDay -> "A bigger day"
        SoftWord.Building -> "Building"
        SoftWord.GettingThere -> "Getting there"
        SoftWord.PlentyToday -> "Plenty today"
        SoftWord.Some -> "Some"
        SoftWord.Most -> "Most"
        SoftWord.Plenty -> "Plenty"
        SoftWord.TrendOnly -> "Trend only"
    }
}

// Written by hand rather than through a platform locale formatter so output is
// identical on the JVM and on iOS whatever the device locale is. Localized
// formatters can make their own choice.

/** Rounds half up (like JS `Math.round`) and groups thousands with commas: `1,245`. */
internal fun wholeNumber(value: Double): String = groupThousands(value.roundToLong())

/** One decimal place, like JS `toFixed(1)`: `72.6`. */
internal fun oneDecimal(value: Double): String {
    val tenths = (value * 10).roundToLong()
    val sign = if (tenths < 0) "-" else ""
    val magnitude = abs(tenths)
    return "$sign${groupThousands(magnitude / 10)}.${magnitude % 10}"
}

internal fun groupThousands(value: Long): String {
    val digits = abs(value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (value < 0) "-$grouped" else grouped
}
