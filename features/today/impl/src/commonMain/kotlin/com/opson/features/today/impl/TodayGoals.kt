package com.opson.features.today.impl

import com.opson.libraries.goals.GoalToday
import com.opson.libraries.nutrition.BurnMeasure
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GoalAveraging
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/*
 * Today with a goal (owner feedback, 2026-10-30: "almost like a mode you enter"). The card names
 * the goal and opens it, a weekly average says what today asks of the week, and the burned bar
 * marks a usual day's burn and a burn goal. Nothing here formats a number itself: every amount
 * resolves through the policy, on the card that only exists while calories are on show.
 */

/** The goal the card is about, by [goalId], and the words its chip uses. */
data class GoalChip(val goalId: String, val label: GoalLabel)

/** What a goal chip calls the goal: its type, and for a calorie goal which way it points. */
enum class GoalLabel { Lose, Keep, Gain, Deficit, Surplus, Balance, Burn, Steps, TrackEveryDay, Meals }

/**
 * A weekly average's line on the card: [OnTrack] when the week asks nothing different of today,
 * else about how much today ([Eat]) or, for an energy balance goal set as a deficit, about how big
 * a deficit ([Deficit]). Never framed as making up for a day.
 */
sealed interface WeekLine {
    data object OnTrack : WeekLine
    data class Eat(val kcal: Display) : WeekLine
    data class Deficit(val kcal: Display) : WeekLine
}

/** A mark on the burned bar: [kcal] as the legend says it, at [fraction] of the card's scale. */
data class BurnMark(val kcal: Display, val fraction: Float)

/** The goal parts of Today's card, as pure functions of [TodayInputs]. */
object TodayGoals {

    /** The chip: the goal setting the target, else the first active goal. Null without one. */
    fun chip(goals: List<GoalToday>): GoalChip? {
        val goal = goals.firstOrNull()?.goal ?: return null
        return GoalChip(goal.id, label(goal.type) ?: return null)
    }

    fun label(type: GoalType): GoalLabel? = when (type) {
        is GoalType.Weight -> when (type.direction) {
            GoalDirection.Lose -> GoalLabel.Lose
            GoalDirection.Gain -> GoalLabel.Gain
            else -> GoalLabel.Keep
        }
        is GoalType.EnergyBalance -> when {
            type.kcalPerDay < 0 -> GoalLabel.Deficit
            type.kcalPerDay > 0 -> GoalLabel.Surplus
            else -> GoalLabel.Balance
        }
        is GoalType.Burn -> GoalLabel.Burn
        is GoalType.Steps -> GoalLabel.Steps
        GoalType.TrackEveryDay -> GoalLabel.TrackEveryDay
        is GoalType.Meals -> GoalLabel.Meals
        is GoalType.Unknown -> null
    }

    /**
     * Today's line for a goal averaged over the week, on today only and until it is wrapped up.
     * After "numbers have been hard" a week that asks for less today says nothing: that is the
     * restrict-after-a-big-day loop, and the target already carries the engine's capped move.
     */
    fun weekLine(inputs: TodayInputs, policy: VisibilityPolicy, context: FormatContext): WeekLine? {
        val day = inputs.day
        if (day.date != inputs.today || day.closedByUser || day.untracked) return null
        val status = inputs.goals.firstOrNull { it.goal.drivesTarget } ?: return null
        val week = status.week?.takeIf { it.averaging == GoalAveraging.Weekly } ?: return null
        if (week.adjustmentKcal == 0) return WeekLine.OnTrack
        if (week.adjustmentKcal < 0 && inputs.settings.onboarding.hard) return null
        val plain = context.copy(of = null, bare = false)
        val type = status.goal.type
        val burn = status.target?.burnKcal
        if (type is GoalType.EnergyBalance && type.kcalPerDay < 0 && burn != null && burn - week.todayTargetKcal >= 1) {
            val deficit = policy.resolve(Metric.EnergyOut, (burn - week.todayTargetKcal).toDouble(), Surface.Today, plain)
            if (deficit is Display.Shown) return WeekLine.Deficit(deficit)
        }
        val eat = policy.resolve(Metric.Calories, week.todayTargetKcal.toDouble(), Surface.Today, plain)
        return if (eat is Display.Shown) WeekLine.Eat(eat) else null
    }

    /** A burn goal's line on [burned]'s scale: its kcal, or resting plus its kcal for an active-only goal. */
    fun burnGoalKcal(goals: List<GoalToday>, burned: Burned): Int? {
        val type = goals.firstNotNullOfOrNull { it.goal.type as? GoalType.Burn } ?: return null
        return if (type.measure == BurnMeasure.Total) type.kcal else burned.resting.kcal + type.kcal
    }
}

/**
 * What a usual whole day burns: the median of the recent days' Burned (owner feedback,
 * 2026-10-30: "a line for how much I usually burn on average, to meet or cross"). The median, so a
 * marathon or a sick day doesn't move it. Today is never in it: it is still going.
 */
object UsualBurn {
    /** The whole days it reads, ending yesterday. */
    const val DAYS = 14

    /** Fewer days than this and there is no usual yet. */
    const val MIN_DAYS = 4

    fun of(byDay: Map<LocalDate, Burned>, today: LocalDate): Int? {
        val totals = byDay.filterKeys { it < today }.values.map { it.totalKcal }.filter { it > 0 }.sorted()
        if (totals.size < MIN_DAYS) return null
        val middle = totals.size / 2
        val median = if (totals.size % 2 == 1) totals[middle].toDouble() else (totals[middle - 1] + totals[middle]) / 2.0
        return (median / 10).roundToInt() * 10
    }
}
