package com.opson.features.trends.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.MicroPercents
import com.opson.libraries.nutrition.toMicronutrients

/**
 * One day's worth of energy, macros and micros: a single day's totals, or a
 * daily average over several. Micros are percents of each daily reference.
 */
data class NutrientDay(
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val fiber: Double?,
    val microPercents: Map<Micronutrient, Double>,
) {
    fun of(metric: Metric): Double? = when (metric) {
        Metric.Calories -> kcal
        Metric.Protein -> protein
        Metric.Carbs -> carbs
        Metric.Fat -> fat
        Metric.Fiber -> fiber
        else -> null
    }
}

object WeekAverages {

    /**
     * [day]'s own totals. An empty day is a real zero for macros; energy is
     * `null` when only name-only items were logged, so it hides rather than
     * reading "0 kcal". Entries without micro data count as none.
     */
    fun ofDay(day: Day): NutrientDay {
        val totals = day.totals()
        return NutrientDay(
            kcal = totals.kcal.value,
            protein = totals.protein.value,
            carbs = totals.carbs.value,
            fat = totals.fat.value,
            fiber = totals.fiber.value,
            microPercents = MicroPercents.of(day.allItems.map { it.micros.orEmpty().toMicronutrients() }),
        )
    }

    /**
     * The daily average over the days in [days] with anything logged, or `null`
     * when none has. Untracked days are left out rather than counted as zero, so
     * a skipped day never drags the average down.
     */
    fun of(days: List<Day>): NutrientDay? {
        val tracked = days.filter { it.hasLogged }
        if (tracked.isEmpty()) return null
        val count = tracked.size.toDouble()
        val percents = MicroPercents.of(tracked.flatMap { day -> day.allItems.map { it.micros.orEmpty().toMicronutrients() } })
        return NutrientDay(
            kcal = tracked.mapNotNull { it.intakeKcal() }.averageOrNull(),
            protein = tracked.sumOf { it.totals().protein.known } / count,
            carbs = tracked.sumOf { it.totals().carbs.known } / count,
            fat = tracked.sumOf { it.totals().fat.known } / count,
            fiber = tracked.sumOf { it.totals().fiber.known } / count,
            microPercents = percents.mapValues { (_, percent) -> percent / count },
        )
    }
}
