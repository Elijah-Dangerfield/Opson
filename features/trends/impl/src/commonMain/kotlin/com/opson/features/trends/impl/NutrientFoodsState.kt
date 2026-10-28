package com.opson.features.trends.impl

import com.opson.features.trends.NutrientsRange
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.contributions
import com.opson.libraries.diary.ui.label
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.MicroCoverage
import com.opson.libraries.nutrition.MicroPercents
import com.opson.libraries.nutrition.Nutrient
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.resolveContribution
import com.opson.libraries.nutrition.resolveShare
import com.opson.libraries.nutrition.toMicronutrients
import com.opson.libraries.ui.time.DateCopy
import com.opson.libraries.ui.time.RelativeDay
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource

/** What the foods sheet is built from: the nutrient, the day it was opened for, and that day or the Trends week. */
data class NutrientFoodsInputs(
    val today: LocalDate,
    val day: LocalDate,
    val range: NutrientsRange,
    val nutrient: Nutrient,
    val settings: Settings,
    val days: List<Day>,
)

/**
 * Which foods gave one nutrient (Cronometer's "highlight a nutrient"). Ranked, with each one's
 * amount and share only where the mode shows that nutrient's numbers; a softened mode (Macros
 * only) gets the ranking alone. Never framed as foods to cut: there is no "too much", no
 * colour per row, and sodium reads like any other.
 */
data class NutrientFoodsState(
    val loading: Boolean = true,
    val title: StringResource? = null,
    /** `Today`, `Thu, Oct 22`, or the week `Oct 19 to 25`. */
    val subtitle: String = "",
    val rows: List<FoodShare> = emptyList(),
    /** Foods past the ones listed. */
    val more: Int = 0,
    /** Amounts and shares are hidden in this mode, so the caption says the order is by amount. */
    val rankedOnly: Boolean = false,
    /** Some of the foods do not list it (micros only): the amounts are a floor, which the caption says. */
    val coverage: MicroCoverage? = null,
    /** No food gave any of it. */
    val none: Boolean = false,
    /** The mode hides this nutrient: the sheet has nothing to show of it, not even the food names. */
    val hidden: Boolean = false,
)

/** One food: its name, and `2.1 mg` with `40%` where the mode shows them. [share] carries the bar's fill. */
data class FoodShare(val name: String, val amount: Display, val share: Display)

object NutrientFoodsStateBuilder {

    /** Enough to answer the question; the rest are counted, not listed. */
    const val MAX_ROWS = 8

    fun build(inputs: NutrientFoodsInputs, policy: VisibilityPolicy): NutrientFoodsState {
        val nutrient = inputs.nutrient
        val window = TrendsCalendar.weekWindow(inputs.today, inputs.settings.weekStart)
        val surface: Surface
        val context: FormatContext
        val days: List<Day>
        when (inputs.range) {
            NutrientsRange.Day -> {
                val day = inputs.days.firstOrNull { it.date == inputs.day } ?: Day.empty(inputs.day)
                surface = Surface.Nutrients
                context = day.formatContext(inputs.today)
                days = listOf(day)
            }
            NutrientsRange.WeekAverage -> {
                val byDate = inputs.days.associateBy { it.date }
                surface = Surface.Week
                // The week's last day, so a weekly reveal opens with its weekly check, as on Nutrients.
                context = FormatContext(day = window.end)
                // The days the week average is made of: before today, and counted in Trends.
                days = window.days.filter { it < inputs.today }.mapNotNull { byDate[it] }.filter { it.countsInTrends }
            }
        }
        val subtitle = when (inputs.range) {
            NutrientsRange.Day -> DateCopy.label(RelativeDay.of(inputs.day, inputs.today))
            NutrientsRange.WeekAverage -> DateCopy.range(window.start, window.end)
        }
        val title = when (nutrient) {
            is Nutrient.Macro -> nutrient.metric.label
            is Nutrient.Micro -> nutrient.micro.label
        }
        val level = policy.effectiveLevel(nutrient.metric, surface, context)
        if (level == Visibility.Hide) return NutrientFoodsState(loading = false, title = title, subtitle = subtitle, hidden = true)

        val items = days.flatMap { it.allItems }
        // A week's amounts are per day, so they read against the page's daily average.
        val perDay = if (inputs.range == NutrientsRange.WeekAverage) days.size.coerceAtLeast(1).toDouble() else 1.0
        val ranked = items.contributions(nutrient)
        val coverage = (nutrient as? Nutrient.Micro)?.let { micro ->
            MicroPercents.coverage(items.map { it.micros.orEmpty().toMicronutrients() }).getValue(micro.micro)
        }
        return NutrientFoodsState(
            loading = false,
            title = title,
            subtitle = subtitle,
            rows = ranked.take(MAX_ROWS).map { food ->
                FoodShare(
                    name = food.name,
                    amount = policy.resolveContribution(nutrient, food.amount / perDay, surface, context),
                    share = policy.resolveShare(nutrient, food.percent, surface, context),
                )
            },
            more = (ranked.size - MAX_ROWS).coerceAtLeast(0),
            rankedOnly = level != Visibility.Show,
            coverage = coverage?.takeIf { it.isPartial },
            none = ranked.isEmpty(),
        )
    }
}
