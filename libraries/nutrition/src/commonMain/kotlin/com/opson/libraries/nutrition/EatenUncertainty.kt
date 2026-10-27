package com.opson.libraries.nutrition

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * How a food's energy was arrived at, and so how far off it may be (design sync 4): [share] is
 * one standard deviation as a share of the food's energy.
 */
enum class LoggedAs(val share: Double) {
    Weighed(0.05),
    Label(0.10),
    Estimate(0.25),
    QuickAdd(0.15),

    /** A meal eaten with no details: its share is of the usual size of that meal, not of a logged amount. */
    AteNoDetails(0.40),
    ;

    /** Not weighed and not read off a label. */
    val isEstimate: Boolean get() = this != Weighed && this != Label
}

/** One food, or one meal eaten with no details, as the day's ± sees it: its energy and how it was logged. */
data class EatenEntry(val kcal: Double, val loggedAs: LoggedAs)

/**
 * How sure the day's eaten energy is. [plusMinusKcal] is one standard deviation of the total,
 * √Σσ², rounded to 10. [estimates] of [foods] were neither weighed nor from a label ("5 of 7
 * foods are estimates").
 */
data class EatenUncertainty(val plusMinusKcal: Int, val estimates: Int, val foods: Int) {
    companion object {
        val None = EatenUncertainty(0, 0, 0)

        fun of(entries: List<EatenEntry>): EatenUncertainty {
            val variance = entries.sumOf { entry ->
                val sigma = entry.kcal.coerceAtLeast(0.0).takeIf { it.isFinite() }?.times(entry.loggedAs.share) ?: 0.0
                sigma * sigma
            }
            return EatenUncertainty(
                plusMinusKcal = (sqrt(variance) / 10).roundToInt() * 10,
                estimates = entries.count { it.loggedAs.isEstimate },
                foods = entries.size,
            )
        }
    }
}
