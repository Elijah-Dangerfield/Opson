package com.opson.features.today.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.MicroPercents
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.microReference
import com.opson.libraries.nutrition.toMicronutrients
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.trends_micro_calcium
import opson.libraries.resources.generated.resources.trends_micro_iron
import opson.libraries.resources.generated.resources.trends_micro_potassium
import opson.libraries.resources.generated.resources.trends_micro_sodium

/**
 * The summary card's last page: a few vitamins and minerals, then See all for the Nutrients
 * screen, which the card's "Nutrients ›" link used to open. Fiber is on page 1 with the macros,
 * and sugars and saturated fat are not tracked (`MicroWireKeys`), so the page is the minerals
 * most people watch: sodium as a limit, then potassium, calcium and iron.
 */
internal object TodayNutrientsPage {
    private const val PERCENT = 100.0

    private val key = listOf(Micronutrient.Sodium, Micronutrient.Potassium, Micronutrient.Calcium, Micronutrient.Iron)

    /** Null when the levels hide micros, so a mode without them has no page that says nothing. */
    fun build(day: Day, policy: VisibilityPolicy, context: FormatContext): NutrientsPage? {
        if (policy.effectiveLevel(Metric.Micros, Surface.Today, context) == Visibility.Hide) return null
        val percents = MicroPercents.of(day.allItems.map { it.micros.orEmpty().toMicronutrients() })
        val rows = key.map { micro ->
            KeyNutrient(
                label = label(micro),
                reference = policy.microReference(micro),
                isLimit = micro.isLimit,
                display = policy.resolve(Metric.Micros, percents[micro], Surface.Today, context.copy(of = PERCENT)),
            )
        }.filter { it.display !is Display.Hidden }
        return rows.takeIf { it.isNotEmpty() }?.let(::NutrientsPage)
    }

    private fun label(micro: Micronutrient) = when (micro) {
        Micronutrient.Sodium -> Res.string.trends_micro_sodium
        Micronutrient.Potassium -> Res.string.trends_micro_potassium
        Micronutrient.Calcium -> Res.string.trends_micro_calcium
        else -> Res.string.trends_micro_iron
    }
}
