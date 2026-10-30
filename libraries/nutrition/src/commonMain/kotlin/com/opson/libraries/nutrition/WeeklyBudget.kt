package com.opson.libraries.nutrition

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What the engine knows about one day, for any goal type. [eatenKcal] is null when something
 * logged has no calories, so the day can't be read against a calorie goal. [burnedKcal] and
 * [activeKcal] are the day's Burned and the part above resting; [steps] is the hook for a steps
 * source, null until one exists. [showedUp] is celebrations' rule (`DayMark.showedUp`).
 */
data class GoalDayFacts(
    val date: LocalDate,
    val logged: Boolean = false,
    val untracked: Boolean = false,
    val closed: Boolean = false,
    val eatenKcal: Double? = null,
    val burnedKcal: Int? = null,
    val activeKcal: Int? = null,
    val steps: Int? = null,
    val mealsWithFood: Int = 0,
    val showedUp: Boolean = false,
) {
    /**
     * Whether the day counts toward a calorie week on [today]: a past day with something logged
     * and every amount known, or today once wrapped up. Skipped and empty days don't.
     */
    fun countsOn(today: LocalDate): Boolean =
        !untracked && logged && eatenKcal != null && (date < today || (date == today && closed))
}

/** How a day sits in the week's budget. */
enum class BudgetDayState {
    /** Done and counted. */
    Counted,

    /** Marked as not tracked: left out, and asks nothing of the other days. */
    Skipped,

    /** A past day with nothing logged: left out the same way. */
    NotLogged,

    /** Logged, but some of it has no calories: left out, since it can't be added up. */
    Partial,

    /** Today, still going. */
    Today,

    /** Still to come this week. */
    Ahead,
}

data class BudgetDay(val date: LocalDate, val state: BudgetDayState, val targetKcal: Int, val eatenKcal: Double?)

/**
 * One week of a calorie goal (docs/plans/goals-v2.md, "Weekly averaging"). [plannedKcal] and
 * [eatenKcal] are over the counted days only, and [balanceKcal] is planned less eaten: positive
 * when the counted days came in under their target. [weekTargetKcal] is the target times every
 * day of the week not skipped. [todayTargetKcal] is the aim plus [adjustmentKcal], which is 0
 * for a [GoalAveraging.Daily] goal; [capped] says the caps held it back.
 */
data class WeeklyBudget(
    val start: LocalDate,
    val end: LocalDate,
    val averaging: GoalAveraging,
    val aimKcal: Int,
    val days: List<BudgetDay>,
    val countedDays: Int,
    val plannedKcal: Int,
    val eatenKcal: Double,
    val balanceKcal: Int,
    val weekTargetKcal: Int,
    val remainingDays: Int,
    val adjustmentKcal: Int,
    val todayTargetKcal: Int,
    val todayMinKcal: Int,
    val todayMaxKcal: Int,
    val capped: Boolean,
)

/**
 * The lowest any day may be asked to eat: the low-goal line and, for a loss with a known fat-loss
 * limit, never a deficit past it. [burnKcal] is the typical day's burn.
 */
data class BudgetFloor(val lowGoalKcal: Int, val burnKcal: Int?, val fatLossLimitKcal: Int?) {
    val kcal: Int
        get() {
            val byLimit = if (burnKcal != null && fatLossLimitKcal != null) burnKcal - fatLossLimitKcal else 0
            return max(max(lowGoalKcal, byLimit), MIN_GOAL_KCAL)
        }
}

/**
 * The weekly budget. Pure; tests pin the caps. Nothing is carried from one week to the next, and a
 * balance the caps can't spread is dropped: a big day is okay.
 */
object WeeklyBudgets {

    // TODO(clinician): how far a weekly average may move one day's target. An engineer picked them.
    // Easing (eat more than the target, after days under it) up to a fifth of the target. Tightening
    // (eat less, after days over it) far less: at most a tenth and never 200 kcal, because "eat less
    // after a big day" is the restrict-after-binge loop Opson must never drive.
    const val MAX_EASE_SHARE: Double = 0.20
    const val MAX_TIGHTEN_SHARE: Double = 0.10
    const val MAX_TIGHTEN_KCAL: Int = 200

    private const val DAYS_A_WEEK = 7

    /** The first day of [date]'s week, for a week that starts on [weekStart]. */
    fun weekStartOf(date: LocalDate, weekStart: DayOfWeek): LocalDate {
        val back = (date.dayOfWeek.isoDayNumber - weekStart.isoDayNumber + DAYS_A_WEEK) % DAYS_A_WEEK
        return date.minus(DatePeriod(days = back))
    }

    /**
     * [today]'s week for a goal of [aimKcal] with [toleranceKcal] either side. [days] is what is
     * known by date (a missing day is nothing logged). Weekly: the counted days' balance spread
     * over the days left (today unless wrapped up, and the rest, less days already skipped),
     * capped, and held at or above [floor] and at or under [MAX_GOAL_KCAL].
     */
    fun of(
        today: LocalDate,
        weekStart: DayOfWeek,
        aimKcal: Int,
        toleranceKcal: Int,
        averaging: GoalAveraging,
        days: Map<LocalDate, GoalDayFacts>,
        floor: BudgetFloor,
    ): WeeklyBudget {
        val start = weekStartOf(today, weekStart)
        val dates = (0 until DAYS_A_WEEK).map { start.plus(DatePeriod(days = it)) }
        val states = dates.associateWith { stateOf(it, today, days[it]) }
        val counted = dates.filter { states[it] == BudgetDayState.Counted }
        val planned = aimKcal * counted.size
        val eaten = counted.sumOf { days[it]?.eatenKcal ?: 0.0 }
        val balance = (planned - eaten).roundToInt()
        val remaining = dates.count { states[it] == BudgetDayState.Today || states[it] == BudgetDayState.Ahead }
        val (adjustment, capped) = if (averaging == GoalAveraging.Weekly && remaining > 0) {
            adjust(aimKcal, balance / remaining, floor)
        } else {
            0 to false
        }
        val todayTarget = aimKcal + adjustment
        val tolerance = toleranceKcal.coerceAtLeast(0)
        return WeeklyBudget(
            start = start,
            end = dates.last(),
            averaging = averaging,
            aimKcal = aimKcal,
            days = dates.map { date ->
                val state = states.getValue(date)
                val target = if (state == BudgetDayState.Today || state == BudgetDayState.Ahead) todayTarget else aimKcal
                BudgetDay(date, state, target, days[date]?.eatenKcal)
            },
            countedDays = counted.size,
            plannedKcal = planned,
            eatenKcal = eaten,
            balanceKcal = balance,
            weekTargetKcal = aimKcal * dates.count { states[it] != BudgetDayState.Skipped },
            remainingDays = remaining,
            adjustmentKcal = adjustment,
            todayTargetKcal = todayTarget,
            todayMinKcal = max(MIN_GOAL_KCAL, todayTarget - tolerance),
            todayMaxKcal = min(MAX_GOAL_KCAL, todayTarget + tolerance),
            capped = capped,
        )
    }

    /**
     * The target [date] had, under [averaging], replaying the week as it stood that morning: only
     * days before it count. What the calendar and the recap judge a past day by.
     */
    fun targetOn(
        date: LocalDate,
        weekStart: DayOfWeek,
        aimKcal: Int,
        toleranceKcal: Int,
        averaging: GoalAveraging,
        days: Map<LocalDate, GoalDayFacts>,
        floor: BudgetFloor,
    ): WeeklyBudget {
        val before = days.filterKeys { it < date }
        // The day itself is today in the replay: its own untracked mark still leaves it out.
        val own = days[date]?.let { it.copy(closed = false) }
        return of(date, weekStart, aimKcal, toleranceKcal, averaging, if (own == null) before else before + (date to own), floor)
    }

    /** [perDay] capped both ways and held at the floor and ceiling, and whether anything held it back. */
    private fun adjust(aimKcal: Int, perDay: Int, floor: BudgetFloor): Pair<Int, Boolean> {
        val easeCap = (aimKcal * MAX_EASE_SHARE).roundToInt()
        val tightenCap = min((aimKcal * MAX_TIGHTEN_SHARE).roundToInt(), MAX_TIGHTEN_KCAL)
        val capped = perDay.coerceIn(-tightenCap, easeCap)
        val lowest = min(aimKcal, floor.kcal) - aimKcal
        val highest = MAX_GOAL_KCAL - aimKcal
        val held = capped.coerceIn(lowest, max(lowest, highest))
        // Toward zero, so rounding never takes a day past a cap or the floor.
        return held / 10 * 10 to (held != perDay)
    }

    private fun stateOf(date: LocalDate, today: LocalDate, facts: GoalDayFacts?): BudgetDayState = when {
        facts?.untracked == true -> BudgetDayState.Skipped
        date > today -> BudgetDayState.Ahead
        facts != null && facts.countsOn(today) -> BudgetDayState.Counted
        date == today -> BudgetDayState.Today
        facts == null || !facts.logged -> BudgetDayState.NotLogged
        else -> BudgetDayState.Partial
    }
}
