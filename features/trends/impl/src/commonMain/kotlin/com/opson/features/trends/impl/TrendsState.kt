package com.opson.features.trends.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.NewFoods
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.nutrition.DayGoal
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
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
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
    /**
     * Energy expended by day from the watch, for the Expended card. `null` while `release.health`
     * is off, no health app is connected, or the read failed.
     */
    val expended: Map<LocalDate, EnergyExpended>? = null,
    /**
     * Today's Burned with `release.goalsAndEnergy`: what a set deficit or surplus aims at. The
     * chart draws one goal for the span, so today's stands in for each day's.
     */
    val burnedKcal: Int? = null,
)

/** The Trends tab, top to bottom. Each card is `null` when it is off or the mode has nothing to put on it. */
data class TrendsState(
    val loading: Boolean = true,
    val rangeLabel: RangeLabel? = null,
    /** The Week / Month / 3 months control; `null` hides it, when no card on the page follows a range. */
    val range: TrendsRange? = null,
    val weeklyCheck: WeeklyCheck? = null,
    val intake: IntakeCard? = null,
    /** "Your week": the meal-dot grid, leading the page when there is no intake chart. */
    val week: WeekDots? = null,
    val recap: RecapCard? = null,
    val lastCheck: LastCheck? = null,
    val nutrients: NutrientsCard? = null,
    val expended: ExpendedCard? = null,
    val weight: WeightCard? = null,
)

sealed interface RangeLabel {
    /** `Oct 19 to 25`. */
    data class Week(val text: String) : RangeLabel
    data object LastFourWeeks : RangeLabel
    data object LastThreeMonths : RangeLabel
}

/** Coast before its weekly check: when it opens. [tonight] when that is today. */
data class WeeklyCheck(val day: DayOfWeek, val at: LocalTime, val tonight: Boolean)

data class IntakeCard(
    /** Daily average over tracked days, the one still in progress left out; Hidden when there is none. */
    val average: Display,
    val bars: List<IntakeBar>,
    /** The user's range over the chart's scale; its ends meet when the goal is one number. */
    val band: ClosedFloatingPointRange<Float>,
    val caption: IntakeCaption,
    /** The goal is one number (one number, a deficit or a surplus): the caption calls it a line, not a band. */
    val singleGoal: Boolean = false,
)

/** What the caption under the chart explains: the band, and the striped bar when there is one. */
enum class IntakeCaption { Plain, TodayInProgress, WeekInProgress }

/** [meals] is the four meals in order, each the slot when it was done and `null` when not. */
data class RecapDay(val label: String, val meals: List<MealSlot?>, val isToday: Boolean)

data class WeekDots(val days: List<RecapDay>)

/** [titled]: "Recap" with a Patterns link, when the card sits under the chart rather than leading. */
data class RecapCard(val titled: Boolean, val rows: List<RecapRow>)

sealed interface RecapRow {
    data class DaysLogged(val days: Int, val of: Int) : RecapRow
    data class Breakfast(val days: Int, val of: Int) : RecapRow
    data class MealsLogged(val meals: Int) : RecapRow

    /** [names] only when the recap leads the page, as chips under the count. */
    data class NewFoods(val count: Int, val names: List<String>?) : RecapRow
}

/** Coast: last week's check, readable until the next one opens. [weekEnd] is `Oct 18`. */
data class LastCheck(val weekEnd: String, val average: Display, val weight: Display)

data class NutrientsCard(val macros: List<MacroTile>)

data class MacroTile(val metric: Metric, val label: StringResource, val display: Display)

/** "Expended": the daily average from the watch, and each day's total as a line. */
data class ExpendedCard(
    val average: Display,
    /** One height per day with a reading, oldest first, in `0..1`; empty under two. */
    val trend: List<Float>,
)

data class WeightCard(
    val subtitle: WeightSubtitle,
    /** The smoothed weight at Show, the slope word when softened, `null` before any weigh-in. */
    val value: Display?,
    /** The smoothed line over the span; empty under two readings. */
    val trend: List<Float>,
)

enum class WeightSubtitle { Trend, NoWeighIns }

/**
 * Builds [TrendsState]. Pure: the ViewModel, the previews and the leak test all
 * call it, so the leak test checks the real mapping.
 *
 * Every amount resolves on [Surface.Week] with the week window's last day as
 * the context day, so Coast's week stays closed until its weekly check;
 * weight resolves on [Surface.Weight] so the card and the Weight page agree.
 * No layout decision reads the mode: each card follows its metric's level.
 */
object TrendsStateBuilder {

    fun build(inputs: TrendsInputs, policy: VisibilityPolicy): TrendsState {
        val settings = inputs.settings
        val cards = settings.trendsCards
        val window = TrendsCalendar.weekWindow(inputs.today, settings.weekStart)
        val weekContext = FormatContext(day = window.end)
        val byDate = inputs.days.associateBy { it.date }
        val dayAt = { date: LocalDate -> byDate[date] ?: Day.empty(date) }

        val checkPending = policy.revealsWeekly &&
            policy.level(Metric.Calories) == Visibility.Delay &&
            !policy.isWeekOpen(window.end)

        val ranged = !checkPending && (
            (cards.intake && showsIntake(policy, weekContext)) ||
                (cards.nutrients && showsNutrients(policy, weekContext)) ||
                (cards.expended && inputs.expended != null && showsExpended(policy, weekContext)) ||
                (cards.weight && showsWeight(policy))
            )
        val range = if (ranged) settings.trendsRange else TrendsRange.Week
        val span = TrendsCalendar.span(inputs.today, settings.weekStart, range)

        val intake = if (cards.intake && !checkPending) intake(inputs, span, dayAt, policy, weekContext) else null
        val leads = intake == null && !checkPending

        return TrendsState(
            loading = false,
            rangeLabel = when (range) {
                TrendsRange.Week -> RangeLabel.Week(DateCopy.range(window.start, window.end))
                TrendsRange.FourWeeks -> RangeLabel.LastFourWeeks
                TrendsRange.ThreeMonths -> RangeLabel.LastThreeMonths
            },
            range = if (ranged) range else null,
            weeklyCheck = if (checkPending) {
                WeeklyCheck(window.end.dayOfWeek, policy.weekOpensAt(window.end).time, tonight = window.end == inputs.today)
            } else {
                null
            },
            intake = intake,
            week = if (cards.recap && leads) WeekDots(window.days.map { recapDay(dayAt(it), inputs.today) }) else null,
            recap = if (cards.recap) recap(inputs, span, dayAt, leads, checkPending) else null,
            lastCheck = if (checkPending) lastCheck(inputs, window, dayAt, policy) else null,
            nutrients = if (cards.nutrients && !checkPending) nutrients(inputs, span, dayAt, policy, weekContext) else null,
            expended = if (cards.expended && !checkPending) expended(inputs, span, dayAt, policy, weekContext) else null,
            weight = if (cards.weight && !checkPending) weight(inputs, span, policy) else null,
        )
    }

    /** Calories effectively shown on the week view: a soft word on a bar chart would still read as an amount. */
    fun showsIntake(policy: VisibilityPolicy, context: FormatContext = FormatContext()): Boolean =
        policy.effectiveLevel(Metric.Calories, Surface.Week, context) == Visibility.Show

    /** Any macro not hidden on the week view. */
    fun showsNutrients(policy: VisibilityPolicy, context: FormatContext = FormatContext()): Boolean =
        TREND_MACROS.any { policy.effectiveLevel(it, Surface.Week, context) != Visibility.Hide }

    /**
     * Energy out shown on the week view. Soft is as good as hidden here: energy out has no honest
     * soft word, so the policy hides it, and the card goes with it.
     */
    fun showsExpended(policy: VisibilityPolicy, context: FormatContext = FormatContext()): Boolean =
        policy.effectiveLevel(Metric.EnergyOut, Surface.Week, context) == Visibility.Show

    /** Weight not hidden: the card is removed, not greyed, when it is. */
    fun showsWeight(policy: VisibilityPolicy): Boolean =
        policy.effectiveLevel(Metric.Weight, Surface.Weight) != Visibility.Hide

    private fun intake(
        inputs: TrendsInputs,
        span: TrendsSpan,
        dayAt: (LocalDate) -> Day,
        policy: VisibilityPolicy,
        context: FormatContext,
    ): IntakeCard? {
        if (!showsIntake(policy, context)) return null
        val goal = inputs.settings.targets.spanGoal(inputs.settings.numbers.preset, inputs.burnedKcal)
        val scale = chartScale(goal)
        val tracked = mutableListOf<Double>()
        var inProgress = IntakeCaption.Plain
        val bars = when (span.range) {
            TrendsRange.Week -> span.days.map { date ->
                val current = date == inputs.today
                val day = dayAt(date)
                val kcal = day.intakeKcal()?.takeIf { opens(Metric.Calories, day, inputs, policy) }
                if (current) inProgress = IntakeCaption.TodayInProgress else kcal?.let(tracked::add)
                IntakeBar(TrendsCalendar.weekdayLabel(date), kcal?.let { (it / scale).toFloat() }, isCurrent = current)
            }
            TrendsRange.FourWeeks, TrendsRange.ThreeMonths -> {
                val count = TrendsCalendar.barCount(span.range)
                val starts = TrendsCalendar.weekStarts(inputs.today, inputs.settings.weekStart, count)
                starts.mapIndexed { index, start ->
                    val current = index == starts.lastIndex
                    val week = (0 until TrendsCalendar.DAYS_IN_WEEK).map { start.plus(it, DateTimeUnit.DAY) }
                        .filter { it <= inputs.today }
                        .map(dayAt)
                        .filter { opens(Metric.Calories, it, inputs, policy) }
                    val days = week.mapNotNull { it.intakeKcal() }
                    if (current) inProgress = IntakeCaption.WeekInProgress else tracked.addAll(days)
                    val labelled = span.range == TrendsRange.FourWeeks || (starts.lastIndex - index) % THREE_MONTHS_LABEL_EVERY == 0
                    IntakeBar(
                        label = if (labelled) "${start.month.number}/${start.day}" else "",
                        fraction = days.averageOrNull()?.let { (it / scale).toFloat() },
                        isCurrent = current,
                    )
                }
            }
        }
        return IntakeCard(
            average = policy.resolve(Metric.Calories, tracked.averageOrNull(), Surface.Week, context),
            bars = bars,
            band = (goal.minKcal / scale).toFloat()..(goal.maxKcal / scale).toFloat(),
            caption = inProgress,
            singleGoal = goal.minKcal == goal.maxKcal,
        )
    }

    private fun recapDay(day: Day, today: LocalDate) = RecapDay(
        label = TrendsCalendar.weekdayLabel(day.date),
        // An untracked day is a gap: no dots, and never a day with one meal.
        meals = MealSlot.meals.map { slot -> slot.takeIf { !day.untracked && day.isMealDone(it) } },
        isToday = day.date == today,
    )

    private fun recap(
        inputs: TrendsInputs,
        span: TrendsSpan,
        dayAt: (LocalDate) -> Day,
        leads: Boolean,
        checkPending: Boolean,
    ): RecapCard {
        // Untracked days are left out of every count and of the "of", as Patterns does, so a holiday
        // never reads as a day with nothing logged.
        val counted = span.days.filter { it <= inputs.today }.map(dayAt).filterNot { it.untracked }
        val of = span.dayCount - span.days.count { dayAt(it).untracked }
        val daysLogged = RecapRow.DaysLogged(counted.count { it.countsInTrends }, of)
        if (checkPending) {
            return RecapCard(titled = false, rows = listOf(daysLogged, RecapRow.MealsLogged(counted.sumOf { it.doneMealCount })))
        }
        val newFoods = NewFoods.between(inputs.days, span.start, span.end, TrendsCalendar.NEW_FOOD_LOOKBACK_DAYS)
        return RecapCard(
            titled = !leads,
            rows = listOf(
                daysLogged,
                RecapRow.Breakfast(counted.count { it.isMealDone(MealSlot.Breakfast) }, of),
                RecapRow.NewFoods(newFoods.size, newFoods.takeIf { leads }),
            ),
        )
    }

    private fun lastCheck(inputs: TrendsInputs, window: WeekWindow, dayAt: (LocalDate) -> Day, policy: VisibilityPolicy): LastCheck {
        val last = TrendsCalendar.previousWeek(window)
        val average = last.days.mapNotNull { dayAt(it).intakeKcal() }.averageOrNull()
        val curve = WeightCurves.curve(inputs.weighIns, last.end, WEIGHT_CHECK_DAYS, inputs.timeZone)
        return LastCheck(
            weekEnd = DateCopy.monthDay(last.end),
            average = policy.resolve(Metric.Calories, average, Surface.Week, FormatContext(day = last.end)),
            weight = policy.resolve(
                Metric.Weight,
                curve.trendKg,
                Surface.Weight,
                FormatContext(weightUnit = inputs.settings.weightUnit, trendKgPerWeek = curve.kgPerWeek),
            ),
        )
    }

    private fun nutrients(
        inputs: TrendsInputs,
        span: TrendsSpan,
        dayAt: (LocalDate) -> Day,
        policy: VisibilityPolicy,
        context: FormatContext,
    ): NutrientsCard? {
        if (!showsNutrients(policy, context)) return null
        // Today is still in progress, so it stays out of the average like the chart's striped bar.
        // A day a delayed macro hasn't opened on yet stays out too.
        val delayed = TREND_MACROS.filter { policy.level(it) == Visibility.Delay }
        val averages = WeekAverages.of(
            span.days.filter { it < inputs.today }.map(dayAt).filter { day -> delayed.all { opens(it, day, inputs, policy) } },
        )
        val targets = inputs.settings.targets
        val tiles = TREND_MACROS.mapNotNull { metric ->
            val display = policy.resolve(metric, averages?.of(metric), Surface.Week, context.copy(of = targets.goal(metric), goal = GoalStyle.Slash))
            if (display is Display.Hidden) null else MacroTile(metric, metric.label, display)
        }
        return NutrientsCard(tiles)
    }

    /**
     * The watch's daily average over the span. Today is still in progress, so it stays out, as it
     * does from the intake average; so does a day whose energy out hasn't opened. A day the watch
     * has nothing for is a gap, never a zero.
     */
    private fun expended(
        inputs: TrendsInputs,
        span: TrendsSpan,
        dayAt: (LocalDate) -> Day,
        policy: VisibilityPolicy,
        context: FormatContext,
    ): ExpendedCard? {
        val energy = inputs.expended ?: return null
        if (!showsExpended(policy, context)) return null
        val totals = span.days
            .filter { it < inputs.today && opens(Metric.EnergyOut, dayAt(it), inputs, policy) }
            .mapNotNull { energy[it]?.totalKcal }
        val average = policy.resolve(Metric.EnergyOut, totals.averageOrNull(), Surface.Week, context)
        if (average is Display.Hidden) return null
        return ExpendedCard(average, trend = heights(totals))
    }

    /** Each value's height between the lowest and highest, with a little room, so the line has no scale. */
    private fun heights(values: List<Double>): List<Float> {
        if (values.size < 2) return emptyList()
        val pad = (values.max() - values.min()).coerceAtLeast(MIN_LINE_SPAN_KCAL) * LINE_PAD
        val low = values.min() - pad
        val high = values.max() + pad
        return values.map { ((it - low) / (high - low)).toFloat() }
    }

    private fun weight(inputs: TrendsInputs, span: TrendsSpan, policy: VisibilityPolicy): WeightCard? {
        if (!showsWeight(policy)) return null
        val curve = WeightCurves.curve(inputs.weighIns, inputs.today, span.dayCount, inputs.timeZone)
        return WeightCard(
            subtitle = if (inputs.weighIns.isEmpty()) WeightSubtitle.NoWeighIns else WeightSubtitle.Trend,
            value = curve.trendKg?.let {
                policy.resolve(
                    Metric.Weight,
                    it,
                    Surface.Weight,
                    FormatContext(weightUnit = inputs.settings.weightUnit, trendKgPerWeek = curve.kgPerWeek),
                ).takeUnless { display -> display is Display.Hidden }
            },
            trend = curve.trend,
        )
    }

    /**
     * Whether [day]'s [metric] may go into the chart and the averages. The week view itself is
     * open in Delayed all day, but a day it covers may not be: today before the reveal or day
     * close, or, for a weekly reveal (Coast), any day of a week whose check has not come. The
     * month and three-month spans reach past the week window into exactly those days.
     */
    private fun opens(metric: Metric, day: Day, inputs: TrendsInputs, policy: VisibilityPolicy): Boolean {
        if (policy.level(metric) != Visibility.Delay) return true
        if (!policy.revealsWeekly) {
            return policy.effectiveLevel(metric, Surface.Today, day.formatContext(inputs.today)) == Visibility.Show
        }
        val weekEnd = TrendsCalendar.weekStartOf(day.date, inputs.settings.weekStart)
            .plus(TrendsCalendar.DAYS_IN_WEEK - 1, DateTimeUnit.DAY)
        return policy.isWeekOpen(weekEnd)
    }

    /**
     * Bars are drawn against the top of the goal plus a fifth, rounded up to the next 100. The
     * goal is the one the chart draws, every day group's under "different by day".
     */
    internal fun chartScale(goal: DayGoal): Double =
        ceil(goal.maxKcal * SCALE_HEADROOM / SCALE_STEP) * SCALE_STEP

    /** Coast's weight check looks at the smoothed line over the four weeks to the check. */
    private const val WEIGHT_CHECK_DAYS = 28
    private const val THREE_MONTHS_LABEL_EVERY = 3
    private const val SCALE_HEADROOM = 1.2
    private const val SCALE_STEP = 100.0

    /** A flat week still draws as a gentle line rather than filling the card. */
    private const val MIN_LINE_SPAN_KCAL = 200.0
    private const val LINE_PAD = 0.15
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
