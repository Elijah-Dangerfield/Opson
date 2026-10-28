package com.opson.libraries.nutrition

import kotlin.math.abs
import kotlin.math.max

/** A typed energy and what its macros come to at 4/4/9, when the two don't agree. [over] when the macros come to more. */
data class EnergyMismatch(val kcal: Double, val macroKcal: Double) {
    val over: Boolean get() = macroKcal > kcal
}

/**
 * Whether an energy someone typed and the grams they typed with it agree, for numbers copied off a
 * label by hand.
 *
 * A label's energy rarely equals 4/4/9 of its grams. Every value on it is rounded (grams to the
 * gram, energy to 5 or 10), and fiber is counted three ways: inside carbs at 4 kcal a gram, inside
 * carbs at 2 (US), or outside carbs at 2 (EU). So the macros agree when any of those readings
 * lands within [tolerance], 15 kcal or 12% of the energy, whichever is more. Anything closer is
 * how labels are printed, not a typo worth stopping someone for.
 */
object MacroEnergyCheck {
    const val ABS_TOLERANCE_KCAL: Double = 15.0
    const val REL_TOLERANCE: Double = 0.12
    private const val FIBER_KCAL_PER_G = 2.0

    fun tolerance(kcal: Double): Double = max(ABS_TOLERANCE_KCAL, REL_TOLERANCE * kcal)

    /** Protein, carbs and fat in [grams] at 4/4/9; null with none of the three. Fiber adds nothing. */
    fun macroKcal(grams: Map<Metric, Double>): Double? =
        macroEnergy(grams[Metric.Protein], grams[Metric.Carbs], grams[Metric.Fat]).values.takeIf { it.isNotEmpty() }?.sum()

    /**
     * How [kcal] and [grams] disagree, or null when they agree or there is nothing to compare.
     * With protein, carbs and fat all given the energy can be off either way. With some missing,
     * the missing ones could make up any shortfall, so only macros that already come to more than
     * [kcal] disagree.
     */
    fun mismatch(kcal: Double, grams: Map<Metric, Double>): EnergyMismatch? {
        val macroKcal = macroKcal(grams) ?: return null
        val readings = readings(macroKcal, grams)
        val tolerance = tolerance(kcal)
        val complete = SplitMacros.all { it in grams }
        val agrees = if (complete) readings.any { abs(it - kcal) <= tolerance } else readings.min() <= kcal + tolerance
        return if (agrees) null else EnergyMismatch(kcal, macroKcal)
    }

    /**
     * Protein, carbs and fat in [grams] scaled together until they come to [kcal] at 4/4/9, keeping
     * their split; fiber and anything else as given. Unchanged when they have no energy to scale.
     */
    fun fitted(kcal: Double, grams: Map<Metric, Double>): Map<Metric, Double> {
        val macroKcal = macroKcal(grams)?.takeIf { it > 0.0 } ?: return grams
        val factor = kcal / macroKcal
        return grams.mapValues { (metric, value) -> if (metric in SplitMacros) value * factor else value }
    }

    /** The energy at 4/4/9, and with fiber at 2 kcal a gram inside carbs (US) or outside them (EU). */
    private fun readings(macroKcal: Double, grams: Map<Metric, Double>): List<Double> {
        val fiber = grams[Metric.Fiber]?.takeIf { it > 0.0 && Metric.Carbs in grams } ?: return listOf(macroKcal)
        val atTwo = fiber * FIBER_KCAL_PER_G
        return listOf(macroKcal, macroKcal - atTwo, macroKcal + atTwo)
    }
}
