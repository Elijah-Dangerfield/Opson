package com.opson.features.trends.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GoalStyle
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.components.IntakeBar
import com.opson.libraries.ui.time.DateCopy
import com.opson.libraries.ui.time.RelativeDay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.trends_intake_daily
import opson.libraries.resources.generated.resources.trends_intake_weekly
import opson.libraries.resources.generated.resources.trends_nutrients_sub
import opson.libraries.resources.generated.resources.trends_nutrients_sub_micros
import org.jetbrains.compose.resources.StringResource
import kotlin.math.ceil

/** Everything the Trends tab is built from. [days] are oldest first and may have holes; a missing date is an empty day. */
data class TrendsInputs(
    val today: LocalDate,
    val settings: Settings,
    val days: List<Day>,
    /** Oldest first. */
    val weighIns: List<WeighIn>,
    val timeZone: TimeZone,
)

data class TrendsState(
    val loading: Boolean = true,
    val rangeLabel: RangeLabel? = null,
    val intake: IntakeCard? = null,
    val recap: RecapCard? = null,
    val nutrients: NutrientsCard? = null,
    val weight: WeightCard? = null,
)

sealed interface RangeLabel {
    /** `Oct 19 to 25`. */
    data class Week(val text: String) : RangeLabel
    data object LastFourWeeks : RangeLabel
    data object LastThreeMonths : RangeLabel
}

data class IntakeCard(
    val title: StringResource,
    /** Average over tracked days; Hidden when no day in the range has energy. */
    val average: Display,
    val bars: List<IntakeBar>,
    /** The user's range over the chart's scale. */
    val band: ClosedFloatingPointRange<Float>,
)

data class RecapCard(
    val breakfastDays: Int,
    val newFoods: Int,
    val daysLogged: Int,
    val days: List<RecapDay>,
)

/** [meals] is the four meals in order, each the slot when it was done and `null` when not. */
data class RecapDay(val label: String, val meals: List<MealSlot?>)

data class NutrientsCard(val subtitle: StringResource, val macros: List<MacroTile>)

data class MacroTile(val metric: Metric, val label: StringResource, val display: Display)

data class WeightCard(
    val subtitle: WeightSubtitle,
    /** Only ever a shown amount: at "Trend only" the value slot stays empty. */
    val now: Display?,
    /** The smoothed line over 4 weeks; `null` when weight is hidden, empty under two readings. */
    val trend: List<Float>?,
)

sealed interface WeightSubtitle {
    data object Hidden : WeightSubtitle
    data object NoWeighIns : WeightSubtitle
    data object Trend : WeightSubtitle

    /** When the shown weight was measured: `null` is "This morning", otherwise `Yesterday` or `Thu, Oct 22`. */
    data class Latest(val dayText: String?) : WeightSubtitle
}

/**
 * Builds [TrendsState]. Pure: the ViewModel, the previews and the leak test all
 * call it, so the leak test checks the real mapping.
 *
 * Every amount is resolved on [Surface.Week], where Delayed and Coast numbers
 * are open, except weight, which resolves on [Surface.Weight] as the prototype
 * does so the card and the weight page always agree.
 */
object TrendsStateBuilder {

    /** Weight card trend: the last four weeks. */
    private const val WEIGHT_CARD_DAYS = 28

    fun build(inputs: TrendsInputs, policy: VisibilityPolicy): TrendsState {
        val settings = inputs.settings
        val cards = settings.trendsCards
        val window = TrendsCalendar.weekWindow(inputs.today, settings.weekStart)
        val byDate = inputs.days.associateBy { it.date }
        val dayAt = { date: LocalDate -> byDate[date] ?: Day.empty(date) }
        return TrendsState(
            loading = false,
            rangeLabel = when (settings.trendsRange) {
                TrendsRange.Week -> RangeLabel.Week(DateCopy.range(window.start, window.end))
                TrendsRange.FourWeeks -> RangeLabel.LastFourWeeks
                TrendsRange.ThreeMonths -> RangeLabel.LastThreeMonths
            },
            intake = if (cards.intake) intake(inputs, window, dayAt, policy) else null,
            recap = if (cards.recap) recap(inputs, window, dayAt) else null,
            nutrients = if (cards.nutrients) nutrients(settings.targets, window, dayAt, policy) else null,
            weight = if (cards.weight) weight(inputs, policy) else null,
        )
    }

    /** Calories effectively shown on the week view: a soft word on a bar chart would still read as an amount. */
    fun showsIntake(policy: VisibilityPolicy): Boolean =
        policy.effectiveLevel(Metric.Calories, Surface.Week) == Visibility.Show

    /** Any macro or the micros not hidden on the week view. */
    fun showsNutrients(policy: VisibilityPolicy): Boolean =
        (TREND_MACROS + Metric.Micros).any { policy.effectiveLevel(it, Surface.Week) != Visibility.Hide }

    private fun intake(
        inputs: TrendsInputs,
        window: WeekWindow,
        dayAt: (LocalDate) -> Day,
        policy: VisibilityPolicy,
    ): IntakeCard? {
        if (!showsIntake(policy)) return null
        val targets = inputs.settings.targets
        val scale = chartScale(targets)
        val range = inputs.settings.trendsRange
        val tracked = mutableListOf<Double>()
        val bars = when (range) {
            TrendsRange.Week -> window.days.map { date ->
                val kcal = dayAt(date).intakeKcal()?.also(tracked::add)
                IntakeBar(
                    label = TrendsCalendar.weekdayLabel(date),
                    fraction = kcal?.let { (it / scale).toFloat() },
                    isCurrent = date == inputs.today,
                )
            }
            TrendsRange.FourWeeks, TrendsRange.ThreeMonths -> {
                val starts = TrendsCalendar.weekStarts(inputs.today, inputs.settings.weekStart, TrendsCalendar.barCount(range))
                starts.mapIndexed { index, start ->
                    val week = (0 until DAYS_IN_WEEK).map { start.plus(it, DateTimeUnit.DAY) }.filter { it <= inputs.today }
                    val kcal = week.mapNotNull { dayAt(it).intakeKcal() }.also(tracked::addAll).averageOrNull()
                    val fromCurrent = starts.lastIndex - index
                    val labelled = range == TrendsRange.FourWeeks || fromCurrent % THREE_MONTHS_LABEL_EVERY == 0
                    IntakeBar(
                        label = if (labelled) "${start.month.number}/${start.day}" else "",
                        fraction = kcal?.let { (it / scale).toFloat() },
                        isCurrent = index == starts.lastIndex,
                    )
                }
            }
        }
        return IntakeCard(
            title = if (range == TrendsRange.Week) Res.string.trends_intake_daily else Res.string.trends_intake_weekly,
            average = policy.resolve(Metric.Calories, tracked.averageOrNull(), Surface.Week),
            bars = bars,
            band = (targets.minKcal / scale).toFloat()..(targets.maxKcal / scale).toFloat(),
        )
    }

    private fun recap(inputs: TrendsInputs, window: WeekWindow, dayAt: (LocalDate) -> Day): RecapCard {
        val week = window.days.map(dayAt)
        return RecapCard(
            breakfastDays = week.count { it.isMealDone(MealSlot.Breakfast) },
            newFoods = newFoods(inputs.days, window),
            daysLogged = week.count { it.hasLogged },
            days = week.map { day ->
                RecapDay(
                    label = TrendsCalendar.weekdayLabel(day.date),
                    meals = MealSlot.meals.map { slot -> slot.takeIf { day.isMealDone(it) } },
                )
            },
        )
    }

    /**
     * Foods logged in [window] that were not logged in the lookback before it.
     * There is no "first ever" query, so 90 days of absence is what new means.
     */
    internal fun newFoods(days: List<Day>, window: WeekWindow): Int {
        val lookbackStart = window.start.minus(TrendsCalendar.NEW_FOOD_LOOKBACK_DAYS, DateTimeUnit.DAY)
        fun keys(filter: (LocalDate) -> Boolean) = days.asSequence()
            .filter { filter(it.date) }
            .flatMap { it.allItems }
            .filter { it.method != LogMethod.QuickNumber }
            .mapNotNull { item ->
                item.foodId?.let { "food:$it" }
                    ?: item.customFoodId?.let { "custom:$it" }
                    ?: item.name.trim().lowercase().takeIf(String::isNotEmpty)?.let { "name:$it" }
            }
            .toSet()
        val before = keys { it >= lookbackStart && it < window.start }
        val during = keys { it >= window.start && it <= window.end }
        return (during - before).size
    }

    private fun nutrients(
        targets: Targets,
        window: WeekWindow,
        dayAt: (LocalDate) -> Day,
        policy: VisibilityPolicy,
    ): NutrientsCard? {
        if (!showsNutrients(policy)) return null
        val averages = WeekAverages.of(window.days.map(dayAt))
        val microsShown = policy.effectiveLevel(Metric.Micros, Surface.Week) != Visibility.Hide
        val tiles = TREND_MACROS.mapNotNull { metric ->
            val context = FormatContext(of = targets.goal(metric), goal = GoalStyle.None)
            val display = policy.resolve(metric, averages?.of(metric), Surface.Week, context)
            if (display is Display.Hidden) null else MacroTile(metric, metric.label, display)
        }
        return NutrientsCard(
            subtitle = if (microsShown) Res.string.trends_nutrients_sub_micros else Res.string.trends_nutrients_sub,
            macros = tiles,
        )
    }

    private fun weight(inputs: TrendsInputs, policy: VisibilityPolicy): WeightCard {
        val level = policy.effectiveLevel(Metric.Weight, Surface.Weight)
        val latest = inputs.weighIns.lastOrNull()
        val subtitle = when {
            level == Visibility.Hide -> WeightSubtitle.Hidden
            latest == null -> WeightSubtitle.NoWeighIns
            level == Visibility.Show -> WeightSubtitle.Latest(weighInDayText(latest, inputs.today, inputs.timeZone))
            else -> WeightSubtitle.Trend
        }
        val now = latest?.let {
            policy.resolve(Metric.Weight, it.kg, Surface.Weight, FormatContext(weightUnit = inputs.settings.weightUnit))
        }
        return WeightCard(
            subtitle = subtitle,
            now = now as? Display.Shown,
            trend = if (level == Visibility.Hide) {
                null
            } else {
                WeightCurves.curve(inputs.weighIns, inputs.today, WEIGHT_CARD_DAYS, inputs.timeZone).trend
            },
        )
    }

    /** Bars are drawn against the top of the range plus a fifth, rounded up to the next 100. */
    internal fun chartScale(targets: Targets): Double =
        ceil(targets.maxKcal * SCALE_HEADROOM / SCALE_STEP) * SCALE_STEP

    private const val DAYS_IN_WEEK = 7
    private const val THREE_MONTHS_LABEL_EVERY = 3
    private const val SCALE_HEADROOM = 1.2
    private const val SCALE_STEP = 100.0
}

/** `null` for "This morning" (weighed today), else `Yesterday` or `Thu, Oct 22`. */
internal fun weighInDayText(weighIn: WeighIn, today: LocalDate, timeZone: TimeZone): String? {
    val date = weighIn.measuredAt.toLocalDateTime(timeZone).date
    return if (date == today) null else DateCopy.label(RelativeDay.of(date, today))
}

internal fun Targets.goal(metric: Metric): Double? = when (metric) {
    Metric.Calories -> energyKcal.toDouble()
    Metric.Protein -> proteinG.toDouble()
    Metric.Carbs -> carbsG.toDouble()
    Metric.Fat -> fatG.toDouble()
    Metric.Fiber -> fiberG.toDouble()
    else -> null
}

internal fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
