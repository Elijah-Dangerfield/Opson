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

    // Which way the smoothed weight line is going. TODO(clinician): review the words.
    Steady,
    RisingSlowly,
    FallingSlowly,
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
    /** `72.6 kg`, or `72.6` when [bare], for a figure that sets its unit apart. */
    fun weight(amount: Double, unit: WeightUnit, bare: Boolean = false): String
    fun water(amount: Double, of: Double, unit: WaterUnit): String
    fun left(kcal: Double): String
    fun onTarget(): String
    fun aboveRange(): String
    fun soft(word: SoftWord): String

    /** `1,245 kcal of 2,305`, bare `1,245 of 2,305`. */
    fun energyOf(kcal: Double, of: Double, bare: Boolean): String

    /** `1,245 / 2,305`, or `1,245 / 2,305 kcal` with the unit. */
    fun energySlash(kcal: Double, of: Double, bare: Boolean): String

    /** `47 / 120 g`. */
    fun gramsSlash(grams: Double, of: Double): String

    /** `1,900 to 2,300`, or `1,900 to 2,300 kcal` with the unit. */
    fun energyRange(min: Double, max: Double, bare: Boolean): String

    /** `47 g protein`. [metric] is one of the four macros. */
    fun macroLong(metric: Metric, grams: Double): String

    /** `47P`. [metric] is one of the four macros. */
    fun macroCompact(metric: Metric, grams: Double): String

    /** A ring's energy label: `1,245` over `kcal`. */
    fun ringEnergy(kcal: Double): RingValue

    /** A ring's macro label: `47` over `g`. */
    fun ringGrams(grams: Double): RingValue

    /** The remaining ring inside the range: `On` over `target`. */
    fun ringOnTarget(): RingValue

    /** The remaining ring above the range: `Past` over `range`. */
    fun ringAboveRange(): RingValue

    /** The balance ring: `−950` or `+120` over `kcal`. */
    fun ringBalance(kcal: Double): RingValue

    /** A glass or a daily goal, not an amount drunk: `250 ml`, `2,000 ml`, `8 fl oz`. */
    fun glassSize(amount: Double, unit: WaterUnit): String

    /** A micronutrient's daily reference: `700 µg`, `2.4 µg`, `1,000 mg`, `2,300 mg limit`. */
    fun microReference(micro: Micronutrient): String
}

/** The prototype's strings, word for word. */
object EnglishNutritionFormatter : NutritionFormatter {

    override fun energy(kcal: Double, bare: Boolean): String =
        if (bare) wholeNumber(kcal) else "${wholeNumber(kcal)} kcal"

    override fun grams(grams: Double, of: Double?): String =
        if (of == null) "${wholeNumber(grams)} g" else "${wholeNumber(grams)} g of ${wholeNumber(of)} g"

    override fun percent(percent: Double): String = "${wholeNumber(percent)}%"

    override fun weight(amount: Double, unit: WeightUnit, bare: Boolean): String = when {
        bare -> oneDecimal(amount)
        unit == WeightUnit.Kg -> "${oneDecimal(amount)} kg"
        else -> "${oneDecimal(amount)} lb"
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
        // TODO(clinician): the slope words, reviewed before 1.0.
        SoftWord.Steady -> "Steady"
        SoftWord.RisingSlowly -> "Rising slowly"
        SoftWord.FallingSlowly -> "Falling slowly"
    }

    override fun energyOf(kcal: Double, of: Double, bare: Boolean): String =
        if (bare) "${wholeNumber(kcal)} of ${wholeNumber(of)}" else "${wholeNumber(kcal)} kcal of ${wholeNumber(of)}"

    override fun energySlash(kcal: Double, of: Double, bare: Boolean): String =
        if (bare) "${wholeNumber(kcal)} / ${wholeNumber(of)}" else "${wholeNumber(kcal)} / ${wholeNumber(of)} kcal"

    override fun gramsSlash(grams: Double, of: Double): String = "${wholeNumber(grams)} / ${wholeNumber(of)} g"

    override fun energyRange(min: Double, max: Double, bare: Boolean): String =
        if (bare) "${wholeNumber(min)} to ${wholeNumber(max)}" else "${wholeNumber(min)} to ${wholeNumber(max)} kcal"

    override fun macroLong(metric: Metric, grams: Double): String = "${wholeNumber(grams)} g ${macroWord(metric)}"

    override fun macroCompact(metric: Metric, grams: Double): String = "${wholeNumber(grams)}${macroLetter(metric)}"

    override fun ringEnergy(kcal: Double): RingValue = RingValue(wholeNumber(kcal), "kcal")

    override fun ringGrams(grams: Double): RingValue = RingValue(wholeNumber(grams), "g")

    override fun ringOnTarget(): RingValue = RingValue("On", "target")

    override fun ringAboveRange(): RingValue = RingValue("Past", "range")

    override fun ringBalance(kcal: Double): RingValue {
        val rounded = kcal.roundToLong()
        // A true minus sign, as the prototype, so it does not read as a hyphen.
        val sign = if (rounded < 0) "\u2212" else "+"
        return RingValue("$sign${groupThousands(abs(rounded))}", "kcal")
    }

    override fun glassSize(amount: Double, unit: WaterUnit): String = when (unit) {
        WaterUnit.Ml -> "${wholeNumber(amount)} ml"
        WaterUnit.FlOz -> "${wholeNumber(amount)} fl oz"
    }

    override fun microReference(micro: Micronutrient): String {
        val unit = when (micro.unit) {
            MicroUnit.Mg -> "mg"
            MicroUnit.Ug -> "\u00B5g"
        }
        val amount = "${trimmedDecimal(micro.reference)} $unit"
        return if (micro.isLimit) "$amount limit" else amount
    }

    private fun macroWord(metric: Metric): String = when (metric) {
        Metric.Protein -> "protein"
        Metric.Carbs -> "carbs"
        Metric.Fat -> "fat"
        Metric.Fiber -> "fiber"
        else -> throw IllegalArgumentException("$metric is not a macro")
    }

    private fun macroLetter(metric: Metric): String = when (metric) {
        Metric.Protein -> "P"
        Metric.Carbs -> "C"
        Metric.Fat -> "F"
        Metric.Fiber -> "Fb"
        else -> throw IllegalArgumentException("$metric is not a macro")
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

/** A whole number when there is no fraction, else one decimal place: `700`, `2.4`, `1,000`. */
internal fun trimmedDecimal(value: Double): String {
    val tenths = (value * 10).roundToLong()
    return if (tenths % 10 == 0L) groupThousands(tenths / 10) else oneDecimal(value)
}

internal fun groupThousands(value: Long): String {
    val digits = abs(value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (value < 0) "-$grouped" else grouped
}
