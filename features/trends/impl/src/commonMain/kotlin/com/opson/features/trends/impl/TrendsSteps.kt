package com.opson.features.trends.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.goals.GoalStatuses
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.Goal
import com.opson.libraries.nutrition.GoalContext
import com.opson.libraries.nutrition.GoalDayFacts
import com.opson.libraries.nutrition.GoalDays
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.GoalVerdict
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.countText
import com.opson.libraries.ui.components.ChartReading
import com.opson.libraries.ui.components.ChartReadout
import com.opson.libraries.ui.components.IntakeBar
import com.opson.libraries.ui.time.DateCopy
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.trends_steps_reading
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * What Trends knows about steps (`release.health`): the platform's store, whether it is connected,
 * whether it would let Opson read more, and each day's count from [StepsHistory.from] to today.
 * [days] is null while not connected or when the store could not be read.
 */
data class TrendsStepsInputs(
    val source: HealthSource,
    val status: HealthStatus,
    val canAskForMore: Boolean,
    val days: Map<LocalDate, Int>?,
)

/**
 * The Steps card. Every state names [source], the store steps come from. Steps are a count, not
 * energy, so they never add to Burned; [explained] opens the (i) that says so.
 */
sealed interface StepsCard {
    val source: HealthSource
    val explained: Boolean

    /** No health app connected: what the card would show, and Connect. [install] when Health Connect needs installing first. */
    data class Connect(override val source: HealthSource, val install: Boolean, override val explained: Boolean = false) : StepsCard

    /** Connected, without steps allowed yet: Allow steps asks again for just what is missing. */
    data class Allow(override val source: HealthSource, override val explained: Boolean = false) : StepsCard

    /** Connected, and no steps in these days: steps off for Opson in the store, or nothing that counts them. */
    data class Empty(override val source: HealthSource, override val explained: Boolean = false) : StepsCard

    /**
     * Each day's steps as a bar, today striped. [average] is the daily average of the days before
     * today that have a count; [best] the day with the most; [today] today's count so far.
     * [goal] is the steps goal's line and how the days went against it, null without one.
     * [axis] is the first and last day under a chart too dense to label every bar.
     */
    data class Chart(
        override val source: HealthSource,
        val range: TrendsRange,
        val bars: List<IntakeBar>,
        val readouts: List<ChartReadout?>,
        val band: ClosedFloatingPointRange<Float>?,
        val average: Int?,
        val best: StepsBest?,
        val today: Int?,
        val goal: StepsGoalLine?,
        val axis: StepsAxis?,
        override val explained: Boolean = false,
    ) : StepsCard
}

/** The day with the most steps in the span: `Tue, Oct 20`, or `Today`. */
data class StepsBest(val day: String, val steps: Int)

/**
 * A steps goal of [steps] a day: [met] of [counted] days in the span reached it (today once it
 * has), and [streak] days in a row up to now, which is shown only from two on.
 */
data class StepsGoalLine(val steps: Int, val met: Int, val counted: Int, val streak: Int)

/** Under a month or three of bars: the first day, and the last (`Today`). */
data class StepsAxis(val start: String, val end: String)

/** Builds [StepsCard]. Pure, so the arithmetic is tested in `TrendsStepsTest` and previews show the real thing. */
object TrendsStepsBuilder {

    /** How far back steps are read: the longest range and some, so a streak can reach past it. */
    const val HISTORY_DAYS = 120

    /**
     * The card, or null: switched off, `release.health` off ([TrendsInputs.steps] null), a device
     * with no health store, or energy out hidden in this mode. Steps are not energy, but they are
     * activity, and a mode that hides activity hides them too ([com.opson.libraries.nutrition.GoalCatalog] offers a steps
     * goal by the same rule).
     */
    fun card(inputs: TrendsInputs, policy: VisibilityPolicy, span: TrendsSpan, dayAt: (LocalDate) -> Day, ui: TrendsUi): StepsCard? {
        val steps = inputs.steps ?: return null
        if (!inputs.settings.trendsCards.steps) return null
        if (policy.effectiveLevel(Metric.EnergyOut, Surface.Week) == Visibility.Hide) return null
        val explained = ui.stepsExplained
        return when (steps.status) {
            HealthStatus.Unsupported -> null
            HealthStatus.NeedsInstall -> StepsCard.Connect(steps.source, install = true, explained = explained)
            HealthStatus.NotConnected -> StepsCard.Connect(steps.source, install = false, explained = explained)
            HealthStatus.Connected -> {
                val days = steps.days ?: return StepsCard.Empty(steps.source, explained)
                val inSpan = span.days.filter { it <= inputs.today }.mapNotNull { date -> days[date]?.let { date to it } }
                when {
                    inSpan.isNotEmpty() -> chart(inputs, steps, span, days, dayAt, explained)
                    steps.canAskForMore -> StepsCard.Allow(steps.source, explained)
                    else -> StepsCard.Empty(steps.source, explained)
                }
            }
        }
    }

    private fun chart(
        inputs: TrendsInputs,
        steps: TrendsStepsInputs,
        span: TrendsSpan,
        days: Map<LocalDate, Int>,
        dayAt: (LocalDate) -> Day,
        explained: Boolean,
    ): StepsCard.Chart {
        val today = inputs.today
        val goal = GoalStatuses.stepsGoal(inputs.settings)
        val goalSteps = (goal?.type as? GoalType.Steps)?.count
        val shown = span.days.filter { span.range == TrendsRange.Week || it <= today }
        val counts = shown.filter { it <= today }.mapNotNull { date -> days[date]?.let { date to it } }
        val scale = scale(counts.maxOfOrNull { it.second } ?: 0, goalSteps)
        val labelled = span.range == TrendsRange.Week
        val bars = shown.map { date ->
            val count = days[date]?.takeIf { date <= today }
            IntakeBar(
                label = if (labelled) TrendsCalendar.weekdayLabel(date) else "",
                fraction = count?.let { (it / scale).toFloat() },
                isCurrent = date == today,
            )
        }
        val readouts = shown.map { date ->
            days[date]?.takeIf { date <= today }?.let { count ->
                ChartReadout(TrendsReadouts.day(date, today), listOf(ChartReading(value = countText(count), label = Res.string.trends_steps_reading)))
            }
        }
        val before = counts.filter { it.first < today }.map { it.second }
        val best = counts.maxByOrNull { it.second }
        return StepsCard.Chart(
            source = steps.source,
            range = span.range,
            bars = bars,
            readouts = readouts,
            band = goalSteps?.let { (it / scale).toFloat().let { line -> line..line } },
            average = if (before.isEmpty()) null else before.average().roundToInt(),
            best = best?.takeIf { counts.size > 1 }?.let { (date, count) -> StepsBest(TrendsReadouts.day(date, today), count) },
            today = days[today],
            goal = goal?.let { goalLine(it, goalSteps ?: return@let null, shown, days, dayAt, inputs) },
            axis = if (labelled || shown.isEmpty()) null else StepsAxis(DateCopy.monthDay(shown.first()), TrendsReadouts.day(shown.last(), today)),
            explained = explained,
        )
    }

    /**
     * How the span went against the goal, through the goal engine so the card and the goal screens
     * agree: a day is met at or past the count, today as soon as it gets there. The streak reads
     * every day read ([HISTORY_DAYS]), not only the span's, so a week view still shows the run.
     */
    private fun goalLine(
        goal: Goal,
        goalSteps: Int,
        shown: List<LocalDate>,
        days: Map<LocalDate, Int>,
        dayAt: (LocalDate) -> Day,
        inputs: TrendsInputs,
    ): StepsGoalLine {
        val today = inputs.today
        val history = generateSequence(today.minus(HISTORY_DAYS - 1, DateTimeUnit.DAY)) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= today }.toList()
        val facts = (history + shown).distinct().associateWith { date -> GoalDayFacts(date, untracked = dayAt(date).untracked, steps = days[date]) }
        val context = GoalContext(today = today, weekStart = inputs.settings.weekStart)
        val inSpan = GoalDays.verdicts(goal, shown.filter { it <= today }, facts, context)
        return StepsGoalLine(
            steps = goalSteps,
            met = inSpan.count { it.verdict == GoalVerdict.Met },
            counted = inSpan.count { it.verdict == GoalVerdict.Met || it.verdict == GoalVerdict.Near || it.verdict == GoalVerdict.Off },
            streak = GoalStatuses.streak(GoalDays.verdicts(goal, history, facts, context)),
        )
    }

    /** The bars' top: the most steps or the goal, whichever is higher, plus a little room, rounded up to a thousand. */
    internal fun scale(most: Int, goal: Int?): Double {
        val top = maxOf(most, goal ?: 0, MIN_SCALE_STEPS) * HEADROOM
        return ceil(top / SCALE_STEP) * SCALE_STEP
    }

    private const val HEADROOM = 1.15
    private const val SCALE_STEP = 1000.0

    /** A day of a few hundred steps still draws as a short bar, not a full one. */
    private const val MIN_SCALE_STEPS = 4000
}
