package com.opson.features.trends.impl

import com.opson.features.trends.NutrientsRange
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GoalStyle
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.MicroGroup
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.NutritionScore
import com.opson.libraries.nutrition.NutritionScores
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.microReference
import com.opson.libraries.ui.time.DateCopy
import com.opson.libraries.ui.time.RelativeDay
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource

/**
 * What Nutrients detail is built from. [day] is the day it was opened for;
 * [days] holds that day alone on Day, and the Trends week window on Week average.
 */
data class NutrientsInputs(
    val today: LocalDate,
    val day: LocalDate,
    val range: NutrientsRange,
    val fromTrends: Boolean,
    val settings: Settings,
    val days: List<Day>,
)

data class NutrientsState(
    val loading: Boolean = true,
    val backLabel: BackLabel = BackLabel.Trends,
    val subtitle: NutrientsSubtitle? = null,
    val range: NutrientsRange = NutrientsRange.Day,
    /** `null` when all five are hidden. */
    val macros: List<NutrientRow>? = null,
    /** `null` unless the micros show. */
    val scores: List<ScoreRow>? = null,
    val micros: List<MicroGroupRows>? = null,
    /** Micros hidden while the mode shows some macro. */
    val microsOff: MicrosOff? = null,
    /** Nothing on this page can show in this mode. */
    val nothing: Boolean = false,
)

sealed interface BackLabel {
    data object Trends : BackLabel

    /** `Today`, `Yesterday` or `Thu, Oct 22`. */
    data class Day(val text: String) : BackLabel
}

sealed interface NutrientsSubtitle {
    /** `Sunday, Oct 25`, or `Thu, Oct 22` further than a day away. */
    data class Day(val text: String) : NutrientsSubtitle

    /** The window, `Oct 19 to 25`, for "Daily average, Oct 19 to 25". */
    data class WeekAverage(val range: String) : NutrientsSubtitle
}

/** GoalStyle.Of: `1,245 kcal of 2,100`, `47 g of 120 g`. */
data class NutrientRow(val metric: Metric, val label: StringResource, val display: Display)

data class ScoreRow(val score: NutritionScore, val label: StringResource, val display: Display)

data class MicroGroupRows(val group: MicroGroup, val title: StringResource, val rows: List<MicroRow>)

/** [display] is a percent of [reference]: `62%`, or `Some` / `Most` / `Plenty` when softened. */
data class MicroRow(val micro: Micronutrient, val label: StringResource, val reference: String, val display: Display)

/** [canTurnOn]: the micros are off by choice (Hide), not waiting on a reveal. */
data class MicrosOff(val canTurnOn: Boolean)

/**
 * Builds [NutrientsState]. Day resolves on [Surface.Nutrients], so a Delayed
 * day opens at 9 pm or once it is past; Week average resolves on
 * [Surface.Week], where Delayed and Coast numbers are open.
 */
object NutrientsStateBuilder {

    private const val PERCENT = 100.0

    fun build(inputs: NutrientsInputs, policy: VisibilityPolicy): NutrientsState {
        val targets = inputs.settings.targets
        val surface = if (inputs.range == NutrientsRange.Day) Surface.Nutrients else Surface.Week
        val window = TrendsCalendar.weekWindow(inputs.today, inputs.settings.weekStart)
        val values: NutrientDay?
        val base: FormatContext
        when (inputs.range) {
            NutrientsRange.Day -> {
                val day = inputs.days.firstOrNull { it.date == inputs.day } ?: Day.empty(inputs.day)
                values = WeekAverages.ofDay(day)
                base = day.formatContext(inputs.today).copy(goal = GoalStyle.Of)
            }
            NutrientsRange.WeekAverage -> {
                val byDate = inputs.days.associateBy { it.date }
                values = WeekAverages.of(window.days.map { byDate[it] ?: Day.empty(it) })
                base = FormatContext(goal = GoalStyle.Of)
            }
        }

        val macros = ENERGY_AND_MACROS.mapNotNull { metric ->
            val display = policy.resolve(metric, values?.of(metric), surface, base.copy(of = targets.goal(metric)))
            if (display is Display.Hidden) null else NutrientRow(metric, metric.label, display)
        }.takeIf { it.isNotEmpty() }

        // What the mode allows, apart from whether there is anything to show: an
        // empty week has no rows, but it is not "no nutrient detail in this mode".
        val macrosAllowed = ENERGY_AND_MACROS.any { policy.effectiveLevel(it, surface, base) != Visibility.Hide }
        val microsShown = policy.effectiveLevel(Metric.Micros, surface, base) != Visibility.Hide
        val percents = values?.microPercents
        val microContext = base.copy(of = PERCENT)
        fun percent(value: Double?) = policy.resolve(Metric.Micros, value, surface, microContext)

        val scores = if (microsShown && percents != null) {
            NutritionScores.compute(percents).map { (score, value) -> ScoreRow(score, score.label, percent(value)) }
        } else {
            null
        }
        val micros = if (microsShown && percents != null) {
            MicroGroup.entries.map { group ->
                MicroGroupRows(
                    group = group,
                    title = group.label,
                    rows = Micronutrient.inGroup(group).map { micro ->
                        MicroRow(micro, micro.label, policy.microReference(micro), percent(percents[micro]))
                    },
                )
            }
        } else {
            null
        }

        return NutrientsState(
            loading = false,
            backLabel = if (inputs.fromTrends) {
                BackLabel.Trends
            } else {
                BackLabel.Day(DateCopy.label(RelativeDay.of(inputs.day, inputs.today)))
            },
            subtitle = when (inputs.range) {
                NutrientsRange.Day -> NutrientsSubtitle.Day(daySubtitle(inputs.day, inputs.today))
                NutrientsRange.WeekAverage -> NutrientsSubtitle.WeekAverage(DateCopy.range(window.start, window.end))
            },
            range = inputs.range,
            macros = macros,
            scores = scores,
            micros = micros,
            microsOff = if (!microsShown && macrosAllowed) {
                MicrosOff(canTurnOn = policy.level(Metric.Micros) == Visibility.Hide)
            } else {
                null
            },
            nothing = !microsShown && !macrosAllowed,
        )
    }

    /** Today and the days either side spell the weekday out, as the prototype's `dayInfo`. */
    private fun daySubtitle(day: LocalDate, today: LocalDate): String = when (RelativeDay.of(day, today)) {
        is RelativeDay.Date -> DateCopy.weekdayMonthDay(day)
        else -> DateCopy.longWeekdayMonthDay(day)
    }
}
