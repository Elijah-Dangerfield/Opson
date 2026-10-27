package com.opson.libraries.diary

import com.opson.libraries.nutrition.EatenEntry
import com.opson.libraries.nutrition.EatenUncertainty
import com.opson.libraries.nutrition.LoggedAs

/**
 * How this item's energy was arrived at, for the day's ±. A barcode or a "From label" amount reads
 * a label; a quick number is typed; a weighed amount is weighed; anything else is an estimate.
 */
val LogItem.loggedAs: LoggedAs
    get() = when {
        method == LogMethod.Barcode || precision == Precision.Label -> LoggedAs.Label
        method == LogMethod.QuickNumber -> LoggedAs.QuickAdd
        precision == Precision.Weighed -> LoggedAs.Weighed
        else -> LoggedAs.Estimate
    }

/**
 * The day's eaten energy ± (design sync 4): each food by how it was logged, and each meal marked
 * "ate, no details" at the usual size of that meal ([usualMealKcal], see [UsualMealSize]).
 */
fun Day.eatenUncertainty(usualMealKcal: (MealSlot) -> Double): EatenUncertainty =
    EatenUncertainty.of(
        allItems.map { EatenEntry(it.kcal ?: 0.0, it.loggedAs) } +
            ateNoDetails.map { EatenEntry(usualMealKcal(it), LoggedAs.AteNoDetails) },
    )

/**
 * How big a meal usually is, for a meal eaten with no details: the median energy of that meal
 * over the days in [history] that logged it, or, with none, the meal's share of the aim.
 */
object UsualMealSize {
    // TODO(clinician): an engineer split the aim across the meals for someone with no history.
    // Only the ± of a meal eaten with no details reads it.
    private val SHARE_OF_AIM = mapOf(
        MealSlot.Breakfast to 0.25,
        MealSlot.Lunch to 0.30,
        MealSlot.Snack to 0.10,
        MealSlot.Dinner to 0.35,
        MealSlot.Uncategorized to 0.25,
    )

    fun of(history: List<Day>, slot: MealSlot, aimKcal: Int): Double {
        val sizes = history
            .filterNot { it.untracked }
            .map { day -> day.items(slot).sumOf { it.kcal ?: 0.0 } }
            .filter { it > 0.0 }
            .sorted()
        if (sizes.isEmpty()) return aimKcal * SHARE_OF_AIM.getValue(slot)
        val middle = sizes.size / 2
        return if (sizes.size % 2 == 1) sizes[middle] else (sizes[middle - 1] + sizes[middle]) / 2
    }

    /** [of] for every meal, as the function [eatenUncertainty] takes. */
    fun from(history: List<Day>, aimKcal: Int): (MealSlot) -> Double {
        val sizes = MealSlot.entries.associateWith { of(history, it, aimKcal) }
        return sizes::getValue
    }
}
