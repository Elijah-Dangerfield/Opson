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
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.microReference
import com.opson.libraries.nutrition.resolveRange
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
    /** The day's Burned with `release.goalsAndEnergy`, what a set deficit or surplus aims at. */
    val burnedKcal: Int? = null,
)

data class NutrientsState(
    val loading: Boolean = true,
    val backLabel: BackLabel = BackLabel.Trends,
    val subtitle: NutrientsSubtitle? = null,
    val range: NutrientsRange = NutrientsRange.Day,
    /** Energy against the range; `null` when calories are hidden. */
    val energy: EnergyRow? = null,
    /** The four macros not hidden; `null` when none shows. */
    val macros: List<NutrientRow>? = null,
    /** `null` unless the micros show. */
    val micros: List<MicroGroupRows>? = null,
    /** Micros hidden while the mode shows something else: the card that links to Modes and numbers. */
    val microsOff: Boolean = false,
    /** Nothing on this page can show in this mode. */
    val nothing: Boolean = false,
    /** Nothing logged on the day, or in the week before today: one card says so instead of a page of zeros. */
    val empty: Boolean = false,
)

/**
 * Energy on a bar with the range as a dashed box. [eaten] carries the fill as its
 * fraction of the bar; [range] is Shown only when calories are, and the box with it.
 */
/** [singleGoal]: the goal is one number, so the row says "· goal" and the bar marks it with a line. */
data class EnergyRow(val eaten: Display, val range: Display, val rangeStart: Float, val rangeEnd: Float, val singleGoal: Boolean = false)

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

/** GoalStyle.Of: `47 of 120 g`. */
data class NutrientRow(val metric: Metric, val label: StringResource, val display: Display)

data class MicroGroupRows(val group: MicroGroup, val title: StringResource, val rows: List<MicroRow>)

/**
 * [display] is a percent of [reference]: `62%`, or `Some` / `Most` / `Plenty` when softened.
 * [isLimit]: the reference is a ceiling (sodium), which the copy says.
 */
data class MicroRow(
    val micro: Micronutrient,
    val label: StringResource,
    val reference: String,
    val isLimit: Boolean,
    val display: Display,
)

/**
 * Builds [NutrientsState]. Day resolves on [Surface.Nutrients], so a Delayed
 * day opens at 9 pm or once it is past; Week average resolves on
 * [Surface.Week], where Later numbers are open, and weekly ones open with their
 * weekly check. There are no nutrition scores (design v3 cut them): a score
 * built from hidden data is a number with no source.
 */
object NutrientsStateBuilder {

    private const val PERCENT = 100.0

    /** The energy bar runs to the top of the range plus this much, so the range box sits short of the end. */
    private const val ENERGY_HEADROOM = 1.15

    fun build(inputs: NutrientsInputs, policy: VisibilityPolicy): NutrientsState {
        val targets = inputs.settings.targets
        val window = TrendsCalendar.weekWindow(inputs.today, inputs.settings.weekStart)
        val surface = if (inputs.range == NutrientsRange.Day) Surface.Nutrients else Surface.Week
        val values: NutrientDay?
        val base: FormatContext
        val logged: Boolean
        when (inputs.range) {
            NutrientsRange.Day -> {
                val day = inputs.days.firstOrNull { it.date == inputs.day } ?: Day.empty(inputs.day)
                values = WeekAverages.ofDay(day)
                base = day.formatContext(inputs.today)
                logged = day.allItems.isNotEmpty()
            }
            NutrientsRange.WeekAverage -> {
                val byDate = inputs.days.associateBy { it.date }
                // Today is still in progress, so it stays out of the average, as on Trends.
                val days = window.days.filter { it < inputs.today }.map { byDate[it] ?: Day.empty(it) }
                values = WeekAverages.of(days)
                logged = days.any { it.allItems.isNotEmpty() }
                // The week's last day, so a weekly reveal's week opens at its weekly check and not before.
                base = FormatContext(day = window.end)
            }
        }

        // The day's own goal on a day, the one the week chart draws for its average.
        val preset = inputs.settings.numbers.preset
        val goal = if (inputs.range == NutrientsRange.Day) {
            targets.rangeFor(inputs.day, preset, inputs.burnedKcal)
        } else {
            targets.spanGoal(preset, inputs.burnedKcal)
        }
        val scale = goal.maxKcal * ENERGY_HEADROOM
        val eaten = policy.resolve(Metric.Calories, values?.kcal, surface, base.copy(of = scale, goal = GoalStyle.None))
        val energy = if (eaten is Display.Hidden) {
            null
        } else {
            EnergyRow(
                eaten = eaten,
                range = policy.resolveRange(Metric.Calories, goal.minKcal.toDouble(), goal.maxKcal.toDouble(), surface, base.copy(bare = true)),
                rangeStart = (goal.minKcal / scale).toFloat(),
                rangeEnd = (goal.maxKcal / scale).toFloat(),
                singleGoal = goal.minKcal == goal.maxKcal,
            )
        }

        val macros = TREND_MACROS.mapNotNull { metric ->
            val display = policy.resolve(metric, values?.of(metric), surface, base.copy(of = targets.goal(metric), goal = GoalStyle.Of))
            if (display is Display.Hidden) null else NutrientRow(metric, metric.label, display)
        }.takeIf { it.isNotEmpty() }

        // What the mode allows, apart from whether there is anything to show: an
        // empty week has no rows, but it is not "no nutrient detail in this mode".
        val macrosAllowed = ENERGY_AND_MACROS.any { policy.effectiveLevel(it, surface, base) != Visibility.Hide }
        val microsShown = policy.effectiveLevel(Metric.Micros, surface, base) != Visibility.Hide
        val percents = values?.microPercents
        val microContext = base.copy(of = PERCENT)

        val micros = if (microsShown && percents != null) {
            MicroGroup.entries.map { group ->
                MicroGroupRows(
                    group = group,
                    title = group.label,
                    rows = Micronutrient.inGroup(group).map { micro ->
                        MicroRow(
                            micro = micro,
                            label = micro.label,
                            reference = policy.microReference(micro),
                            isLimit = micro.isLimit,
                            display = policy.resolve(Metric.Micros, percents[micro], surface, microContext),
                        )
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
            energy = energy.takeIf { logged },
            macros = macros.takeIf { logged },
            micros = micros.takeIf { logged },
            microsOff = !microsShown && macrosAllowed && logged,
            nothing = !microsShown && !macrosAllowed,
            empty = !logged && (microsShown || macrosAllowed),
        )
    }

    /** Today and the days either side spell the weekday out, as the prototype's `dayInfo`. */
    private fun daySubtitle(day: LocalDate, today: LocalDate): String = when (RelativeDay.of(day, today)) {
        is RelativeDay.Date -> DateCopy.weekdayMonthDay(day)
        else -> DateCopy.longWeekdayMonthDay(day)
    }
}
