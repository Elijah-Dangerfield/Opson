package com.opson.features.today.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.ui.label
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.MicroPercents
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.microReference
import com.opson.libraries.nutrition.toMicronutrients
import kotlinx.datetime.LocalDate

/**
 * The summary card's last page: the four vitamins and minerals the person picked
 * ([Settings.todayMicronutrients], sodium, potassium, calcium and iron unless they changed it),
 * each against their own reference, then See all for the Nutrients screen. A row opens the foods
 * that gave it; one no food lists says "Not listed" rather than 0%.
 */
internal object TodayNutrientsPage {
    private const val PERCENT = 100.0

    /** Null when the levels hide micros, so a mode without them has no page that says nothing. */
    fun build(day: Day, settings: Settings, today: LocalDate, policy: VisibilityPolicy, context: FormatContext): NutrientsPage? {
        if (policy.effectiveLevel(Metric.Micros, Surface.Today, context) == Visibility.Hide) return null
        val references = settings.microReferences(today)
        val micros = day.allItems.map { it.micros.orEmpty().toMicronutrients() }
        val percents = MicroPercents.of(micros, references)
        val coverage = MicroPercents.coverage(micros)
        val rows = settings.todayMicronutrients.map { micro ->
            val percent = percents[micro]
            KeyNutrient(
                micro = micro,
                label = micro.label,
                reference = policy.microReference(micro, references),
                isLimit = micro.isLimit,
                display = policy.resolve(Metric.Micros, percent, Surface.Today, context.copy(of = PERCENT)),
                notListed = coverage.getValue(micro).isNone,
                opensFoods = (percent ?: 0.0) > 0.0,
            )
        }.filter { it.notListed || it.display !is Display.Hidden }
        return rows.takeIf { it.isNotEmpty() }?.let(::NutrientsPage)
    }
}
