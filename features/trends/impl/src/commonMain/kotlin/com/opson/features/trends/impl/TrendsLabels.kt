package com.opson.features.trends.impl

import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.MicroGroup
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.color.Colors
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.trends_carbs
import opson.libraries.resources.generated.resources.trends_energy
import opson.libraries.resources.generated.resources.trends_fat
import opson.libraries.resources.generated.resources.trends_fiber
import opson.libraries.resources.generated.resources.trends_protein
import org.jetbrains.compose.resources.StringResource

/** The energy and macro rows, in the prototype's order. */
internal val ENERGY_AND_MACROS = listOf(Metric.Calories, Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber)

/** The macro tiles on the Trends card: no energy, which is the intake chart's job. */
internal val TREND_MACROS = listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber)

internal val Metric.label: StringResource
    get() = when (this) {
        Metric.Calories -> Res.string.trends_energy
        Metric.Protein -> Res.string.trends_protein
        Metric.Carbs -> Res.string.trends_carbs
        Metric.Fat -> Res.string.trends_fat
        Metric.Fiber -> Res.string.trends_fiber
        else -> error("$this has no nutrient row on Trends")
    }

/** A nutrient's colour. They only tell nutrients apart; none means over or under. */
internal fun Colors.of(metric: Metric): ColorResource = when (metric) {
    Metric.Protein -> nutrients.protein.content
    Metric.Carbs -> nutrients.carbs.content
    Metric.Fat -> nutrients.fat.content
    Metric.Fiber -> nutrients.fiber.content
    else -> accent
}

/** Vitamins in the fiber colour and minerals in fat, as the prototype's `MICROS` table. */
internal fun Colors.of(group: MicroGroup): ColorResource = when (group) {
    MicroGroup.Vitamins -> nutrients.fiber.content
    MicroGroup.Minerals -> nutrients.fat.content
}
