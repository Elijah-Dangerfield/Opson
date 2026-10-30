package com.opson.libraries.goals

import com.opson.libraries.diary.GoalBook
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.Goal
import com.opson.libraries.nutrition.GoalAveraging
import com.opson.libraries.nutrition.GoalDay
import com.opson.libraries.nutrition.GoalDayFacts
import com.opson.libraries.nutrition.GoalDays
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.GoalVerdict
import com.opson.libraries.nutrition.WeeklyBudget
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * How a wrapped-up day went for one goal, in words a screen picks its line by. None of them is a
 * failure: [WellUnder] is never the best outcome, and [NotThisTime] is a count goal's neutral
 * "not today".
 */
enum class GoalOutcome {
    /** Inside the calorie band, or a count goal met. */
    OnPlan,

    /** Over the band by no more than the near margin. */
    ABitOver,

    /** Under the band by no more than the near margin. */
    ABitUnder,

    /** Well over the band. */
    BigDay,

    /** Well under the band: eating too little is not the plan either. */
    WellUnder,

    /** A count goal (meals, burn, steps) that wasn't reached. */
    NotThisTime,

    /** Skipped, nothing logged, amounts unknown, or no data yet. */
    NotCounted,
}

/**
 * One goal on a day as it is wrapped up: [day] judged as if the day were closed, what the day was
 * made of ([facts]), and for a calorie goal its [week] after the day counts, so "the week so far"
 * includes it. [streak] is the goal's run of days met, only for [GoalStreaks.eligible] goals and
 * only with `goalStreakEnabled` on; null otherwise.
 */
data class GoalWrapUp(
    val goal: Goal,
    val day: GoalDay,
    val facts: GoalDayFacts,
    val week: WeeklyBudget?,
    val streak: Int? = null,
) {
    val outcome: GoalOutcome get() = GoalOutcomes.of(goal, day, facts)

    /**
     * Whether the week as a whole is on track after this day: the counted days within the band's
     * tolerance of what they planned, or the days left able to take the rest without the caps.
     * Null for a daily goal or without a week.
     */
    val weekOnTrack: Boolean?
        get() {
            val week = week?.takeIf { it.averaging == GoalAveraging.Weekly } ?: return null
            val within = abs(week.balanceKcal) <= goal.toleranceKcal * max(1, week.countedDays)
            return within || (week.remainingDays > 0 && !week.capped)
        }
}

/** [GoalOutcome] from a verdict and the day's facts. Pure: the recap, Wrap up the day and tests share it. */
object GoalOutcomes {
    fun of(goal: Goal, day: GoalDay, facts: GoalDayFacts): GoalOutcome {
        if (day.verdict == GoalVerdict.NotCounted || day.verdict == GoalVerdict.Future) return GoalOutcome.NotCounted
        val min = day.minKcal
        val max = day.maxKcal
        val eaten = facts.eatenKcal
        if (!goal.type.setsCalories || min == null || max == null || eaten == null) {
            return when (day.verdict) {
                GoalVerdict.Met -> GoalOutcome.OnPlan
                GoalVerdict.InProgress -> GoalOutcome.NotCounted
                else -> GoalOutcome.NotThisTime
            }
        }
        val margin = max(GoalDays.NEAR_MIN_KCAL, ((day.targetKcal ?: max) * GoalDays.NEAR_SHARE).roundToInt())
        return when {
            eaten > max + margin -> GoalOutcome.BigDay
            eaten > max -> GoalOutcome.ABitOver
            eaten >= min -> GoalOutcome.OnPlan
            eaten >= min - margin -> GoalOutcome.ABitUnder
            else -> GoalOutcome.WellUnder
        }
    }
}

/**
 * Goal streaks (docs/decisions.md, 2026-10-30, "A goal streak only for goals where met isn't
 * eating less"). The showing-up streak stays as it is; this is a second, opt-in run for a goal
 * whose "met" can never mean eating less or weighing less: tracking every day, meals, burn and
 * steps. Never a weight or energy balance goal.
 *
 * It forgives the way showing up does: a skipped or untracked day is a rest, one day not met
 * between two met ones is forgiven, two in a row end the run, and today never breaks it.
 */
object GoalStreaks {

    /** How far back a run is counted. A run longer than this reads as this long. */
    const val WINDOW_DAYS = 120

    /** Days not met in a row that end a run. One is forgiven. */
    const val MISSES_TO_END = 2

    /** Whether [type] may have a streak: only where met isn't restriction. */
    fun eligible(type: GoalType): Boolean = when (type) {
        GoalType.TrackEveryDay, is GoalType.Meals, is GoalType.Burn, is GoalType.Steps -> true
        is GoalType.Weight, is GoalType.EnergyBalance, is GoalType.Unknown -> false
    }

    /** The first day a run ending on [date] is read from. */
    fun from(date: LocalDate, goal: Goal): LocalDate = maxOf(goal.startDate, date.minus(DatePeriod(days = WINDOW_DAYS - 1)))

    /** The run of days met ending at the last of [days] (oldest first, consecutive). */
    fun run(days: List<GoalDay>): Int {
        var run = 0
        var misses = 0
        for (day in days) {
            when (day.verdict) {
                GoalVerdict.Met -> {
                    run += 1
                    misses = 0
                }
                GoalVerdict.Near, GoalVerdict.Off -> if (run > 0) {
                    misses += 1
                    if (misses >= MISSES_TO_END) {
                        run = 0
                        misses = 0
                    }
                }
                // Skipped or empty days are rests, and a day still going never breaks a run.
                GoalVerdict.NotCounted, GoalVerdict.InProgress, GoalVerdict.Future -> Unit
            }
        }
        return run
    }
}

/** How far a weekly average moves the day's target, for [GoalToday]'s goal; 0 for a daily goal or without a week. */
val GoalToday.weekAdjustmentKcal: Int
    get() {
        val week = week?.takeIf { it.averaging == GoalAveraging.Weekly } ?: return 0
        // Once wrapped up, the day keeps the target it had that morning (the verdict's replay).
        val dayTarget = day.targetKcal ?: week.todayTargetKcal
        return dayTarget - week.aimKcal
    }

/** Wrapping a day up: [GoalStatuses.wrapUp] and the streak. */
internal object GoalWrapUpBuilder {

    fun build(settings: Settings, facts: Map<LocalDate, GoalDayFacts>, date: LocalDate, today: LocalDate, weightKg: Double?): List<GoalWrapUp> {
        val limit = GoalStatuses.limit(settings, weightKg, today)
        val own = (facts[date] ?: GoalDayFacts(date)).copy(closed = true)
        val closed = facts + (date to own)
        return GoalBook.goals(settings).filter { it.isActive }.sortedByDescending { it.drivesTarget }.map { goal ->
            // The day is judged as its own "today": closed, so it counts, and later days don't.
            val context = GoalStatuses.context(goal, settings, date, limit)
            val day = GoalDays.verdict(goal, date, closed, context)
            val streak = if (settings.goalStreakEnabled && GoalStreaks.eligible(goal.type)) {
                val from = GoalStreaks.from(date, goal)
                val dates = generateSequence(from) { it.plus(DatePeriod(days = 1)) }.takeWhile { it <= date }.toList()
                GoalStreaks.run(GoalDays.verdicts(goal, dates, closed, context))
            } else {
                null
            }
            GoalWrapUp(goal, day, own, GoalStatuses.week(goal, context, closed), streak)
        }
    }
}
