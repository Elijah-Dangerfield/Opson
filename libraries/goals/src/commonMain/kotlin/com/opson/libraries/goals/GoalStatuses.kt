package com.opson.libraries.goals

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.GoalBook
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.BudgetFloor
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.FatLoss
import com.opson.libraries.nutrition.FatLossLimit
import com.opson.libraries.nutrition.Goal
import com.opson.libraries.nutrition.GoalContext
import com.opson.libraries.nutrition.GoalDay
import com.opson.libraries.nutrition.GoalDayFacts
import com.opson.libraries.nutrition.GoalDays
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GoalPace
import com.opson.libraries.nutrition.GoalProjections
import com.opson.libraries.nutrition.GoalSafety
import com.opson.libraries.nutrition.GoalTarget
import com.opson.libraries.nutrition.GoalTargets
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.PaceAssessment
import com.opson.libraries.nutrition.WeeklyBudget
import com.opson.libraries.nutrition.WeeklyBudgets
import com.opson.libraries.nutrition.WeightProjection
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * One goal today. [target] is the calorie goal's band (null for other types, or before a
 * baseline); [week] its week, with today's target under a weekly average; [day] how today stands
 * so far; [facts] what today is made of (eaten, burned, meals) for a progress line; [pace] the
 * weight or energy balance goal's tier, against [limit], the personal fat-loss limit.
 */
data class GoalToday(
    val goal: Goal,
    val target: GoalTarget?,
    val week: WeeklyBudget?,
    val day: GoalDay,
    val facts: GoalDayFacts,
    val pace: PaceAssessment?,
    val limit: FatLossLimit?,
)

/**
 * The engine put together for settings and the log: pure, so [GoalRepository]'s flows and a
 * preview or test build the same answers from the same inputs.
 */
object GoalStatuses {

    /**
     * What [day] says to a goal, with that day's [burned] and [steps] when known. [steps] is the
     * health app's count ([com.opson.libraries.health.StepsRepository]); null when it has none.
     */
    fun facts(day: Day, burned: Burned?, steps: Int? = null): GoalDayFacts {
        val kcal = day.totals().kcal
        return GoalDayFacts(
            date = day.date,
            logged = day.hasLogged,
            untracked = day.untracked,
            closed = day.closedByUser,
            eatenKcal = kcal.value?.takeUnless { kcal.isPartial },
            burnedKcal = burned?.totalKcal,
            activeKcal = burned?.realActivityKcal,
            steps = steps,
            mealsWithFood = day.doneMealCount,
            showedUp = !day.untracked && (day.hasLogged || day.closedByUser || day.checkIn != null),
        )
    }

    /** The personal fat-loss limit for these settings and the latest weigh-in ([weightKg]) on [today]. */
    fun limit(settings: Settings, weightKg: Double?, today: LocalDate): FatLossLimit? {
        val profile = settings.bodyProfile(weightKg, today)
        return FatLoss.limitFor(settings.bodyFat, profile.weightKg, profile.heightCm, profile.ageYears, profile.sex)
    }

    /** The lowest a weekly average may ask a day to eat for [goal]. */
    fun floor(goal: Goal, settings: Settings, limit: FatLossLimit?): BudgetFloor {
        val target = GoalTargets.of(goal)
        val losing = (goal.type.deltaKcal ?: 0) < 0
        return BudgetFloor(GoalSafety.lowGoalKcal(settings.bodySex), target?.burnKcal, limit?.kcalPerDay?.takeIf { losing })
    }

    fun context(goal: Goal, settings: Settings, today: LocalDate, limit: FatLossLimit?): GoalContext =
        GoalContext(today = today, weekStart = settings.weekStart, target = GoalTargets.of(goal), floor = floor(goal, settings, limit))

    /** The pace tier of a weight or energy balance goal; null for other types. */
    fun pace(goal: Goal, weightKg: Double?, limit: FatLossLimit?): PaceAssessment? = when (val type = goal.type) {
        is GoalType.Weight -> if (type.direction == GoalDirection.Maintain) null else GoalPace.assess(type.direction, type.kgPerWeek, weightKg, limit)
        is GoalType.EnergyBalance -> GoalPace.assessDelta(type.kcalPerDay, weightKg, limit)
        else -> null
    }

    /** Every active goal on [today], the one setting the target first. [days] is this week's facts and more. */
    fun today(settings: Settings, days: Map<LocalDate, GoalDayFacts>, today: LocalDate, weightKg: Double?): List<GoalToday> {
        val limit = limit(settings, weightKg, today)
        return GoalBook.goals(settings).filter { it.isActive }.sortedByDescending { it.drivesTarget }.map { goal ->
            val context = context(goal, settings, today, limit)
            GoalToday(
                goal = goal,
                target = context.target,
                week = week(goal, context, days),
                day = GoalDays.verdict(goal, today, days, context),
                facts = days[today] ?: GoalDayFacts(today),
                pace = pace(goal, weightKg, limit),
                limit = limit,
            )
        }
    }

    /**
     * [goal]'s week on [GoalContext.today]: only for a calorie goal with a target. Days before the
     * goal started aren't in its budget: a goal set on Thursday doesn't answer for Monday.
     */
    fun week(goal: Goal, context: GoalContext, days: Map<LocalDate, GoalDayFacts>): WeeklyBudget? {
        val target = context.target ?: return null
        val since = days.filterKeys { it >= goal.startDate }
        return WeeklyBudgets.of(context.today, context.weekStart, target.aimKcal, goal.toleranceKcal, goal.averaging, since, context.floor)
    }

    /** Each active goal's day from [from] to [to], by goal id. */
    fun days(settings: Settings, days: Map<LocalDate, GoalDayFacts>, from: LocalDate, to: LocalDate, today: LocalDate, weightKg: Double?): Map<String, List<GoalDay>> {
        val limit = limit(settings, weightKg, today)
        val dates = generateSequence(from) { it.plus(DatePeriod(days = 1)) }.takeWhile { it <= to }.toList()
        return GoalBook.goals(settings).filter { it.isActive }.associate { goal ->
            goal.id to GoalDays.verdicts(goal, dates, days, context(goal, settings, today, limit))
        }
    }

    /** The first active steps goal, the one Trends' Steps card draws its line and streak from. */
    fun stepsGoal(settings: Settings): Goal? = GoalBook.goals(settings).firstOrNull { it.isActive && it.type is GoalType.Steps }

    /**
     * Every active goal on [date] as it is wrapped up (Wrap up the day, the recap): judged as if
     * the day were closed, with its week after the day counts and, where `goalStreakEnabled` is on
     * and the goal may have one, its streak. [days] reaches back to the start of [date]'s week, and
     * to [GoalStreaks.from] for a streak.
     */
    fun wrapUp(settings: Settings, days: Map<LocalDate, GoalDayFacts>, date: LocalDate, today: LocalDate, weightKg: Double?): List<GoalWrapUp> =
        GoalWrapUpBuilder.build(settings, days, date, today, weightKg)

    /** The weight goal's projection from [weightKg] on [today], else from where it started. */
    fun projection(settings: Settings, weightKg: Double?, today: LocalDate): WeightProjection? {
        val goal = GoalBook.weight(settings) ?: return null
        val from = weightKg ?: goal.weight?.startKg ?: return null
        return GoalProjections.weight(goal, from, if (weightKg != null) today else goal.startDate)
    }
}
