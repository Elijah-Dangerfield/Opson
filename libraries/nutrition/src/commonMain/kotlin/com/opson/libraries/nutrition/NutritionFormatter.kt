package com.opson.libraries.nutrition

import com.opson.libraries.core.fillTemplate
import kotlin.concurrent.Volatile
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
 * from [VisibilityPolicy] so the words change language ([NutritionWords],
 * [NutritionCopy]) without touching any visibility rule. Implementations only
 * spell; they never decide whether something is shown, softened or hidden.
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

    /** `1,900 to 2,300`, or `1,900 to 2,300 kcal` with the unit. One number when the ends meet: `2,100`, never `2,100 to 2,100`. */
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

    /** A glass or a daily goal, not an amount drunk: `250 ml`, `2,000 ml`, `8 fl oz`. */
    fun glassSize(amount: Double, unit: WaterUnit): String

    /** A micronutrient's daily reference, [amount] in its unit: `700 µg`, `2.4 µg`, `1,000 mg`, `2,300 mg limit`. */
    fun microReference(micro: Micronutrient, amount: Double): String

    /** An amount of a micronutrient in its unit: `2.1 mg`, `0.89 mg`, `350 µg`, `<0.01 mg`. */
    fun microAmount(micro: Micronutrient, amount: Double): String
}

/**
 * The words a [WordsNutritionFormatter] spells numbers with: whole phrases with positional
 * placeholders (`%1$s`), so a language can put the unit and the "of" where it belongs.
 *
 * The defaults are `strings_nutrition.xml`'s English, which tests and previews read. The app
 * installs the device language's at start ([NutritionCopy.install]); `NutritionWordsDriftTest`
 * fails when the two stop matching, so change the resource and the default together.
 */
data class NutritionWords(
    val energy: String = "%1\$s kcal",
    val grams: String = "%1\$s g",
    val gramsOf: String = "%1\$s of %2\$s g",
    val percent: String = "%1\$s%",
    val weightKg: String = "%1\$s kg",
    val weightLb: String = "%1\$s lb",
    val waterMl: String = "%1\$s of %2\$s ml",
    val waterFlOz: String = "%1\$s of %2\$s fl oz",
    val left: String = "%1\$s left",
    val onTarget: String = "On target",
    val aboveRange: String = "Above your range today",
    val energyOf: String = "%1\$s kcal of %2\$s",
    val energyOfBare: String = "%1\$s of %2\$s",
    val energySlash: String = "%1\$s / %2\$s kcal",
    val gramsSlash: String = "%1\$s / %2\$s g",
    val range: String = "%1\$s to %2\$s",
    val macroLong: String = "%1\$s g %2\$s",
    val protein: String = "protein",
    val carbs: String = "carbs",
    val fat: String = "fat",
    val fiber: String = "fiber",
    val proteinLetter: String = "P",
    val carbsLetter: String = "C",
    val fatLetter: String = "F",
    val fiberLetter: String = "Fb",
    val kcalUnit: String = "kcal",
    val gramsUnit: String = "g",
    val ringOnTargetValue: String = "On",
    val ringOnTargetUnit: String = "target",
    val ringAboveRangeValue: String = "Past",
    val ringAboveRangeUnit: String = "range",
    val glassMl: String = "%1\$s ml",
    val glassFlOz: String = "%1\$s fl oz",
    val microLimit: String = "%1\$s limit",
    val microMg: String = "%1\$s mg",
    val microUg: String = "%1\$s µg",
    val soft: Map<SoftWord, String> = ENGLISH_SOFT,
) {
    companion object {
        val ENGLISH_SOFT: Map<SoftWord, String> = mapOf(
            SoftWord.EarlyInTheDay to "Early in the day",
            SoftWord.PartwayThrough to "Partway through",
            SoftWord.MostOfTheWay to "Most of the way",
            SoftWord.InYourRange to "In your range",
            SoftWord.ABiggerDay to "A bigger day",
            SoftWord.Building to "Building",
            SoftWord.GettingThere to "Getting there",
            SoftWord.PlentyToday to "Plenty today",
            SoftWord.Some to "Some",
            SoftWord.Most to "Most",
            SoftWord.Plenty to "Plenty",
            SoftWord.TrendOnly to "Trend only",
            // TODO(clinician): the slope words, reviewed before 1.0.
            SoftWord.Steady to "Steady",
            SoftWord.RisingSlowly to "Rising slowly",
            SoftWord.FallingSlowly to "Falling slowly",
        )
    }
}

/** Spells numbers with [words]. The digits themselves are grouped by hand; see [wholeNumber]. */
class WordsNutritionFormatter(private val words: NutritionWords) : NutritionFormatter {

    override fun energy(kcal: Double, bare: Boolean): String =
        if (bare) wholeNumber(kcal) else words.energy.fill(wholeNumber(kcal))

    override fun grams(grams: Double, of: Double?): String =
        if (of == null) words.grams.fill(wholeNumber(grams)) else words.gramsOf.fill(wholeNumber(grams), wholeNumber(of))

    override fun percent(percent: Double): String = words.percent.fill(wholeNumber(percent))

    override fun weight(amount: Double, unit: WeightUnit, bare: Boolean): String = when {
        bare -> oneDecimal(amount)
        unit == WeightUnit.Kg -> words.weightKg.fill(oneDecimal(amount))
        else -> words.weightLb.fill(oneDecimal(amount))
    }

    override fun water(amount: Double, of: Double, unit: WaterUnit): String = when (unit) {
        WaterUnit.Ml -> words.waterMl.fill(wholeNumber(amount), wholeNumber(of))
        WaterUnit.FlOz -> words.waterFlOz.fill(wholeNumber(amount), wholeNumber(of))
    }

    override fun left(kcal: Double): String = words.left.fill(wholeNumber(kcal))

    override fun onTarget(): String = words.onTarget

    override fun aboveRange(): String = words.aboveRange

    override fun soft(word: SoftWord): String = words.soft[word] ?: NutritionWords.ENGLISH_SOFT.getValue(word)

    override fun energyOf(kcal: Double, of: Double, bare: Boolean): String =
        (if (bare) words.energyOfBare else words.energyOf).fill(wholeNumber(kcal), wholeNumber(of))

    // The bare slash is two numbers and a glyph, the same in every language.
    override fun energySlash(kcal: Double, of: Double, bare: Boolean): String =
        if (bare) "${wholeNumber(kcal)} / ${wholeNumber(of)}" else words.energySlash.fill(wholeNumber(kcal), wholeNumber(of))

    override fun gramsSlash(grams: Double, of: Double): String = words.gramsSlash.fill(wholeNumber(grams), wholeNumber(of))

    override fun energyRange(min: Double, max: Double, bare: Boolean): String {
        val amount = if (wholeNumber(min) == wholeNumber(max)) wholeNumber(min) else words.range.fill(wholeNumber(min), wholeNumber(max))
        return if (bare) amount else words.energy.fill(amount)
    }

    override fun macroLong(metric: Metric, grams: Double): String = words.macroLong.fill(wholeNumber(grams), macroWord(metric))

    override fun macroCompact(metric: Metric, grams: Double): String = "${wholeNumber(grams)}${macroLetter(metric)}"

    override fun ringEnergy(kcal: Double): RingValue = RingValue(wholeNumber(kcal), words.kcalUnit)

    override fun ringGrams(grams: Double): RingValue = RingValue(wholeNumber(grams), words.gramsUnit)

    override fun ringOnTarget(): RingValue = RingValue(words.ringOnTargetValue, words.ringOnTargetUnit)

    override fun ringAboveRange(): RingValue = RingValue(words.ringAboveRangeValue, words.ringAboveRangeUnit)

    override fun glassSize(amount: Double, unit: WaterUnit): String = when (unit) {
        WaterUnit.Ml -> words.glassMl.fill(wholeNumber(amount))
        WaterUnit.FlOz -> words.glassFlOz.fill(wholeNumber(amount))
    }

    override fun microReference(micro: Micronutrient, amount: Double): String {
        val text = microAmount(micro, amount)
        return if (micro.isLimit) words.microLimit.fill(text) else text
    }

    override fun microAmount(micro: Micronutrient, amount: Double): String {
        // A trace that rounds to nothing is still something the food lists, so it is not "0".
        val number = if (amount > 0.0 && (amount * HUNDREDTHS).roundToLong() == 0L) "<0.01" else upToTwoDecimals(amount)
        return when (micro.unit) {
            MicroUnit.Mg -> words.microMg.fill(number)
            MicroUnit.Ug -> words.microUg.fill(number)
        }
    }

    private fun macroWord(metric: Metric): String = when (metric) {
        Metric.Protein -> words.protein
        Metric.Carbs -> words.carbs
        Metric.Fat -> words.fat
        Metric.Fiber -> words.fiber
        else -> throw IllegalArgumentException("$metric is not a macro")
    }

    private fun macroLetter(metric: Metric): String = when (metric) {
        Metric.Protein -> words.proteinLetter
        Metric.Carbs -> words.carbsLetter
        Metric.Fat -> words.fatLetter
        Metric.Fiber -> words.fiberLetter
        else -> throw IllegalArgumentException("$metric is not a macro")
    }

    private fun String.fill(vararg args: String): String = fillTemplate(*args)

    private companion object {
        const val HUNDREDTHS = 100.0
    }
}

/** The prototype's strings, word for word: [NutritionWords]' English defaults. */
object EnglishNutritionFormatter : NutritionFormatter by WordsNutritionFormatter(NutritionWords())

/**
 * The formatter every [VisibilityPolicy] spells with unless it is handed another: English until
 * the app installs the device language's words, then those. Looked up on every call rather than
 * captured, so a policy built at boot, before the words load, speaks the language once they have.
 */
object NutritionCopy : NutritionFormatter {
    @Volatile
    private var current: NutritionFormatter = EnglishNutritionFormatter

    fun install(words: NutritionWords) {
        current = WordsNutritionFormatter(words)
    }

    override fun energy(kcal: Double, bare: Boolean) = current.energy(kcal, bare)
    override fun grams(grams: Double, of: Double?) = current.grams(grams, of)
    override fun percent(percent: Double) = current.percent(percent)
    override fun weight(amount: Double, unit: WeightUnit, bare: Boolean) = current.weight(amount, unit, bare)
    override fun water(amount: Double, of: Double, unit: WaterUnit) = current.water(amount, of, unit)
    override fun left(kcal: Double) = current.left(kcal)
    override fun onTarget() = current.onTarget()
    override fun aboveRange() = current.aboveRange()
    override fun soft(word: SoftWord) = current.soft(word)
    override fun energyOf(kcal: Double, of: Double, bare: Boolean) = current.energyOf(kcal, of, bare)
    override fun energySlash(kcal: Double, of: Double, bare: Boolean) = current.energySlash(kcal, of, bare)
    override fun gramsSlash(grams: Double, of: Double) = current.gramsSlash(grams, of)
    override fun energyRange(min: Double, max: Double, bare: Boolean) = current.energyRange(min, max, bare)
    override fun macroLong(metric: Metric, grams: Double) = current.macroLong(metric, grams)
    override fun macroCompact(metric: Metric, grams: Double) = current.macroCompact(metric, grams)
    override fun ringEnergy(kcal: Double) = current.ringEnergy(kcal)
    override fun ringGrams(grams: Double) = current.ringGrams(grams)
    override fun ringOnTarget() = current.ringOnTarget()
    override fun ringAboveRange() = current.ringAboveRange()
    override fun glassSize(amount: Double, unit: WaterUnit) = current.glassSize(amount, unit)
    override fun microReference(micro: Micronutrient, amount: Double) = current.microReference(micro, amount)
    override fun microAmount(micro: Micronutrient, amount: Double) = current.microAmount(micro, amount)
}

// Written by hand rather than through a platform locale formatter so output is
// identical on the JVM and on iOS whatever the device locale is. The words around
// the digits are localized (NutritionWords); the grouping is not yet.

/** Rounds half up (like JS `Math.round`) and groups thousands with commas: `1,245`. */
internal fun wholeNumber(value: Double): String = groupThousands(value.roundToLong())

/** One decimal place, like JS `toFixed(1)`: `72.6`. */
internal fun oneDecimal(value: Double): String {
    val tenths = (value * 10).roundToLong()
    val sign = if (tenths < 0) "-" else ""
    val magnitude = abs(tenths)
    return "$sign${groupThousands(magnitude / 10)}.${magnitude % 10}"
}

/**
 * Up to two decimal places, trailing zeros dropped: `700`, `2.4`, `0.89`, `1,000`. Micronutrient
 * amounts need the second place: copper for a teenager is 0.89 mg, and a food's share of it
 * smaller still.
 */
internal fun upToTwoDecimals(value: Double): String {
    val hundredths = (value * 100).roundToLong()
    val sign = if (hundredths < 0) "-" else ""
    val magnitude = abs(hundredths)
    val fraction = (magnitude % 100).toString().padStart(2, '0').trimEnd('0')
    val whole = groupThousands(magnitude / 100)
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
}

internal fun groupThousands(value: Long): String {
    val digits = abs(value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (value < 0) "-$grouped" else grouped
}
