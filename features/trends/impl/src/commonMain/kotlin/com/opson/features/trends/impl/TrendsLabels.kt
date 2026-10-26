package com.opson.features.trends.impl

import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.MicroGroup
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.color.Colors
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.trends_carbs
import opson.libraries.resources.generated.resources.trends_energy
import opson.libraries.resources.generated.resources.trends_fat
import opson.libraries.resources.generated.resources.trends_fiber
import opson.libraries.resources.generated.resources.trends_micro_calcium
import opson.libraries.resources.generated.resources.trends_micro_folate
import opson.libraries.resources.generated.resources.trends_micro_iron
import opson.libraries.resources.generated.resources.trends_micro_magnesium
import opson.libraries.resources.generated.resources.trends_micro_potassium
import opson.libraries.resources.generated.resources.trends_micro_sodium
import opson.libraries.resources.generated.resources.trends_micro_vitamin_a
import opson.libraries.resources.generated.resources.trends_micro_vitamin_b12
import opson.libraries.resources.generated.resources.trends_micro_vitamin_c
import opson.libraries.resources.generated.resources.trends_micro_vitamin_d
import opson.libraries.resources.generated.resources.trends_micro_vitamin_e
import opson.libraries.resources.generated.resources.trends_micro_vitamin_k
import opson.libraries.resources.generated.resources.trends_micro_zinc
import opson.libraries.resources.generated.resources.trends_micros_minerals
import opson.libraries.resources.generated.resources.trends_micros_vitamins
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

internal val Micronutrient.label: StringResource
    get() = when (this) {
        Micronutrient.VitaminA -> Res.string.trends_micro_vitamin_a
        Micronutrient.VitaminB12 -> Res.string.trends_micro_vitamin_b12
        Micronutrient.VitaminC -> Res.string.trends_micro_vitamin_c
        Micronutrient.VitaminD -> Res.string.trends_micro_vitamin_d
        Micronutrient.VitaminE -> Res.string.trends_micro_vitamin_e
        Micronutrient.VitaminK -> Res.string.trends_micro_vitamin_k
        Micronutrient.Folate -> Res.string.trends_micro_folate
        Micronutrient.Calcium -> Res.string.trends_micro_calcium
        Micronutrient.Iron -> Res.string.trends_micro_iron
        Micronutrient.Magnesium -> Res.string.trends_micro_magnesium
        Micronutrient.Potassium -> Res.string.trends_micro_potassium
        Micronutrient.Sodium -> Res.string.trends_micro_sodium
        Micronutrient.Zinc -> Res.string.trends_micro_zinc
    }

internal val MicroGroup.label: StringResource
    get() = when (this) {
        MicroGroup.Vitamins -> Res.string.trends_micros_vitamins
        MicroGroup.Minerals -> Res.string.trends_micros_minerals
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
