package com.opson.features.trends.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.KCAL_PER_KG
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WeightUnit
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.abs

/** What the running total adds up over: the last week, the last 30 days, or since the goal began. */
enum class RunningSpan(val days: Int?) {
    Week(7),
    Month(30),

    /** From the goal's start date, offered only once there is one. */
    SinceStart(null),
}

/** Which side of what was burned the total landed on. */
enum class Balance { Under, Over, Even }

/** Which way the scale's smoothed trend moved over the same days. */
enum class ScaleDirection { Down, Up, Steady }

/** The weight trend over the running total's days, to set the estimate beside. [change] is its size. */
data class ScaleChange(val change: Display, val direction: ScaleDirection)

/**
 * The running total: every counted day's burned less eaten, added up. [balance] is its size and
 * [side] which way; [weight] the rough body weight it comes to, and [scale] what the weight trend
 * did over the same days, so the estimate sits beside the scale. [days] of [of] were counted:
 * a day with nothing logged, an untracked day, one with a meal without amounts, or one Burned
 * couldn't be worked out for is left out rather than guessed. Today is in progress, so it waits
 * until it is closed.
 */
data class RunningTotalCard(
    val spans: List<RunningSpan>,
    val span: RunningSpan,
    val balance: Display,
    val side: Balance,
    val days: Int,
    val of: Int,
    val weight: Display?,
    val scale: ScaleChange?,
    val unit: WeightUnit,
    val explained: Boolean = false,
) {
    val empty: Boolean get() = days == 0
}

/** Builds [RunningTotalCard]. Pure; the arithmetic is tested in `RunningTotalTest`. */
object RunningTotals {

    // Closer to zero than this reads as about even: a day's estimate is rougher than that anyway.
    const val EVEN_KCAL = 50.0

    /** A trend that moved less than this is steady: a day's water moves the scale more. */
    private const val STEADY_KG = 0.1

    /** One counted day's balance, burned less eaten. Positive is a deficit. */
    data class DayBalance(val date: LocalDate, val burnedKcal: Int, val eatenKcal: Double) {
        val kcal: Double get() = burnedKcal - eatenKcal
    }

    /** The span's first and last day: [today] counts only once it is closed. */
    fun window(span: RunningSpan, today: Day, goalStart: LocalDate?): Pair<LocalDate, LocalDate>? {
        val end = if (today.closedByUser) today.date else today.date.minus(1, DateTimeUnit.DAY)
        val start = when (span) {
            RunningSpan.SinceStart -> goalStart ?: return null
            else -> end.minus(requireNotNull(span.days) - 1, DateTimeUnit.DAY)
        }
        return if (start > end) end to end else start to end
    }

    /** Adds up [balances]: the sum, positive for a deficit. */
    fun total(balances: List<DayBalance>): Double = balances.sumOf { it.kcal }

    fun side(totalKcal: Double): Balance = when {
        abs(totalKcal) < EVEN_KCAL -> Balance.Even
        totalKcal > 0 -> Balance.Under
        else -> Balance.Over
    }

    /** The body weight [totalKcal] comes to by the 7,700 kcal a kilogram rule (3,500 a pound), in kg. */
    fun weightKg(totalKcal: Double): Double = abs(totalKcal) / KCAL_PER_KG

    /** The spans on offer: since start only once the goal has a start date. */
    fun spans(goalStart: LocalDate?): List<RunningSpan> =
        if (goalStart == null) listOf(RunningSpan.Week, RunningSpan.Month) else RunningSpan.entries

    /**
     * The card, or `null` when it can't be honest: switched off, no Burned (release.goalsAndEnergy
     * off, or nothing to work it out from), calories or energy out short of Show on the week view,
     * or someone who said numbers have been hard. A deficit's worth in pounds is the number most
     * likely to turn tracking into a chase, so it stays out of their Trends whatever the mode.
     */
    fun card(
        inputs: TrendsInputs,
        policy: VisibilityPolicy,
        dayAt: (LocalDate) -> Day,
        opens: (Metric, Day) -> Boolean,
        ui: TrendsUi,
    ): RunningTotalCard? {
        val settings = inputs.settings
        val burned = inputs.burnedByDay ?: return null
        if (!settings.trendsCards.runningTotal || settings.onboarding.hard) return null
        val context = FormatContext(day = inputs.today)
        if (policy.effectiveLevel(Metric.Calories, Surface.Week, context) != Visibility.Show) return null
        if (policy.effectiveLevel(Metric.EnergyOut, Surface.Week, context) != Visibility.Show) return null

        val spans = spans(inputs.goalStart)
        val span = ui.runningSpan?.takeIf { it in spans } ?: if (inputs.goalStart != null) RunningSpan.SinceStart else RunningSpan.Month
        val (start, end) = window(span, dayAt(inputs.today), inputs.goalStart) ?: return null
        val dates = generateSequence(start) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= end }.toList()
        val balances = dates.mapNotNull { date ->
            val day = dayAt(date)
            val kcal = day.totals().kcal
            val eaten = kcal.value
            val burn = burned[date]?.totalKcal
            if (!day.countsInTrends || kcal.isPartial || eaten == null || burn == null) return@mapNotNull null
            if (!opens(Metric.Calories, day) || !opens(Metric.EnergyOut, day)) return@mapNotNull null
            DayBalance(date, burn, eaten)
        }
        val total = total(balances)
        val unit = settings.weightUnit
        val weightShown = policy.effectiveLevel(Metric.Weight, Surface.Weight) == Visibility.Show
        return RunningTotalCard(
            spans = spans,
            span = span,
            balance = if (balances.isEmpty()) Display.Hidden else policy.resolve(Metric.Calories, abs(total), Surface.Week, context),
            side = side(total),
            days = balances.size,
            of = dates.size,
            weight = if (weightShown && balances.isNotEmpty() && side(total) != Balance.Even) {
                policy.resolve(Metric.Weight, weightKg(total), Surface.Weight, FormatContext(weightUnit = unit))
            } else {
                null
            },
            scale = if (weightShown) scale(inputs, start, end, policy) else null,
            unit = unit,
            explained = ui.runningExplained,
        )
    }

    /** The smoothed weight's move from the first weigh-in in the window to the last; `null` under two. */
    private fun scale(inputs: TrendsInputs, start: LocalDate, end: LocalDate, policy: VisibilityPolicy): ScaleChange? {
        val days = (end.toEpochDays() - start.toEpochDays()).toInt() + 1
        val points = WeightCurves.curve(inputs.weighIns, end, days, inputs.timeZone).points
        if (points.size < 2) return null
        val change = points.last().trendKg - points.first().trendKg
        val direction = when {
            abs(change) < STEADY_KG -> ScaleDirection.Steady
            change < 0 -> ScaleDirection.Down
            else -> ScaleDirection.Up
        }
        val display = policy.resolve(Metric.Weight, abs(change), Surface.Weight, FormatContext(weightUnit = inputs.settings.weightUnit))
        return ScaleChange(display, direction)
    }
}
