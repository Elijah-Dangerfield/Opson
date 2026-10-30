package com.opson.libraries.nutrition

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * How one day went against one goal. [Near] and [Off] are never shown as failure: [Off] is the
 * calendar's neutral "not this time". Far under a loss goal is only [Near], so eating very little
 * never looks like the best day.
 */
enum class GoalVerdict {
    Met,
    Near,
    Off,

    /** Skipped, nothing logged, or amounts unknown: the day doesn't count either way. */
    NotCounted,

    /** Today, still going: judged once wrapped up. */
    InProgress,
    Future,
}

/**
 * One day against one goal. [targetKcal], [minKcal] and [maxKcal] are the calorie band the day was
 * judged by (the weekly replay's under [GoalAveraging.Weekly]), null for other types.
 */
data class GoalDay(
    val date: LocalDate,
    val verdict: GoalVerdict,
    val targetKcal: Int? = null,
    val minKcal: Int? = null,
    val maxKcal: Int? = null,
)

/** Inputs every verdict may need besides the day: the goal's target and the week's shape. */
data class GoalContext(
    val today: LocalDate,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val target: GoalTarget? = null,
    val floor: BudgetFloor = BudgetFloor(LOW_GOAL_FEMALE_KCAL, null, null),
)

/** On track, day by day, for any goal type: the Trends calendar, the recap and goal-met days. */
object GoalDays {

    // TODO(clinician): how far past a calorie band still reads as near, the calendar's own margin
    // (OnTrack.nearMarginKcal): the larger of 150 kcal and a tenth of the target.
    const val NEAR_MIN_KCAL = 150
    const val NEAR_SHARE = 0.10

    /** A burn or steps goal within this share of the target reads as near. */
    const val NEAR_COUNT_SHARE = 0.9

    /** Each of [dates] against [goal]. [facts] is what is known by date; a missing day has nothing logged. */
    fun verdicts(goal: Goal, dates: List<LocalDate>, facts: Map<LocalDate, GoalDayFacts>, context: GoalContext): List<GoalDay> =
        dates.map { verdict(goal, it, facts, context) }

    /** [date] against [goal]. */
    fun verdict(goal: Goal, date: LocalDate, facts: Map<LocalDate, GoalDayFacts>, context: GoalContext): GoalDay {
        val day = facts[date] ?: GoalDayFacts(date)
        if (date > context.today) return GoalDay(date, GoalVerdict.Future)
        if (date < goal.startDate || day.untracked) return GoalDay(date, GoalVerdict.NotCounted)
        return when (val type = goal.type) {
            is GoalType.Weight, is GoalType.EnergyBalance -> calories(goal, day, facts, context)
            is GoalType.Burn -> {
                val burned = if (type.measure == BurnMeasure.Total) day.burnedKcal else day.activeKcal
                count(day, context.today, burned, type.kcal)
            }
            is GoalType.Steps -> count(day, context.today, day.steps, type.count)
            GoalType.TrackEveryDay -> when {
                day.showedUp -> GoalDay(date, GoalVerdict.Met)
                date == context.today -> GoalDay(date, GoalVerdict.InProgress)
                else -> GoalDay(date, GoalVerdict.Off)
            }
            is GoalType.Meals -> when {
                day.mealsWithFood >= type.count -> GoalDay(date, GoalVerdict.Met)
                date == context.today && !day.closed -> GoalDay(date, GoalVerdict.InProgress)
                day.mealsWithFood == type.count - 1 && day.mealsWithFood > 0 -> GoalDay(date, GoalVerdict.Near)
                !day.logged -> GoalDay(date, GoalVerdict.NotCounted)
                else -> GoalDay(date, GoalVerdict.Off)
            }
            is GoalType.Unknown -> GoalDay(date, GoalVerdict.NotCounted)
        }
    }

    private fun calories(goal: Goal, day: GoalDayFacts, facts: Map<LocalDate, GoalDayFacts>, context: GoalContext): GoalDay {
        val target = context.target ?: return GoalDay(day.date, GoalVerdict.NotCounted)
        if (day.date == context.today && !day.closed) return GoalDay(day.date, GoalVerdict.InProgress)
        val eaten = day.eatenKcal
        if (!day.logged || eaten == null) return GoalDay(day.date, GoalVerdict.NotCounted)
        val (aim, min, max) = if (goal.averaging == GoalAveraging.Weekly) {
            val week = WeeklyBudgets.targetOn(day.date, context.weekStart, target.aimKcal, goal.toleranceKcal, goal.averaging, facts, context.floor)
            Triple(week.todayTargetKcal, week.todayMinKcal, week.todayMaxKcal)
        } else {
            Triple(target.aimKcal, target.minKcal, target.maxKcal)
        }
        val margin = max(NEAR_MIN_KCAL, (aim * NEAR_SHARE).roundToInt())
        val delta = goal.type.deltaKcal ?: 0
        val verdict = when {
            eaten > max + margin -> if (delta > 0) GoalVerdict.Near else GoalVerdict.Off
            eaten > max -> GoalVerdict.Near
            eaten >= min -> GoalVerdict.Met
            eaten >= min - margin -> GoalVerdict.Near
            else -> if (delta < 0) GoalVerdict.Near else GoalVerdict.Off
        }
        return GoalDay(day.date, verdict, aim, min, max)
    }

    private fun count(day: GoalDayFacts, today: LocalDate, value: Int?, target: Int): GoalDay = when {
        value != null && value >= target -> GoalDay(day.date, GoalVerdict.Met)
        day.date == today && !day.closed -> GoalDay(day.date, GoalVerdict.InProgress)
        value == null -> GoalDay(day.date, GoalVerdict.NotCounted)
        value >= target * NEAR_COUNT_SHARE -> GoalDay(day.date, GoalVerdict.Near)
        else -> GoalDay(day.date, GoalVerdict.Off)
    }
}
