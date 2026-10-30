package com.opson.features.today.impl.dayclose

import com.opson.features.today.impl.GoalLabel
import com.opson.features.today.impl.TodayGoals
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.Settings
import com.opson.libraries.goals.GoalOutcome
import com.opson.libraries.goals.GoalWrapUp
import com.opson.libraries.nutrition.BurnMeasure
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GoalAveraging
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.LocalDate

/**
 * Wrap up the day's goal card (owner feedback, 2026-10-30: "when you close your day you see
 * whether you hit your goal"): a row for each active goal, how the day went in kind words, the week
 * so far for a weekly average, and the goal's streak where one is on.
 */
data class GoalWrapCard(val rows: List<GoalWrapRow>)

/** One goal: what it is ([label]), its [lines], and its run of days met when a goal streak is on. */
data class GoalWrapRow(val goalId: String, val label: GoalLabel, val lines: List<GoalWrapLine>, val streak: Int? = null)

/** A sentence on the goal card. None asks a later day to make up for this one. */
sealed interface GoalWrapLine {
    /** A calorie goal: [outcome] in words, with the week's comfort when [weekly] and [weekOnTrack]. */
    data class Calories(val outcome: GoalOutcome, val weekly: Boolean, val weekOnTrack: Boolean) : GoalWrapLine

    /** A weekly average with nothing else to say: the week stands where it should. */
    data object WeekOnTrack : GoalWrapLine

    /** A week that leaves room: about [kcal] a day for the days left. Only ever more, never less. */
    data class WeekEase(val kcal: Display) : GoalWrapLine

    data object ShowedUp : GoalWrapLine

    data class Meals(val done: Int, val goal: Int, val met: Boolean) : GoalWrapLine

    /** [burned] of [goal] when the goal wasn't reached; both null once it was. */
    data class Burn(val met: Boolean, val burned: Display.Shown? = null, val goal: Display.Shown? = null) : GoalWrapLine

    data object StepsMet : GoalWrapLine
}

/** [GoalWrapCard] from the engine's wrap-ups, every amount through the policy. */
object DayCloseGoals {

    /**
     * The card, or null with nothing to say. A calorie goal's line needs calories in full here
     * ([caloriesShown]); a burn goal's amounts need energy out in full, and without them it says
     * only that it was met. A streak shows only where celebrations say numbers.
     */
    fun card(
        goals: List<GoalWrapUp>,
        day: Day,
        today: LocalDate,
        settings: Settings,
        policy: VisibilityPolicy,
        caloriesShown: Boolean,
    ): GoalWrapCard? {
        if (day.untracked) return null
        val context = day.formatContext(today)
        val counts = settings.celebrationCounts ?: !settings.onboarding.hard
        val rows = goals.mapNotNull { wrap ->
            val label = TodayGoals.label(wrap.goal.type) ?: return@mapNotNull null
            val lines = lines(wrap, policy, context, caloriesShown)
            if (lines.isEmpty()) return@mapNotNull null
            GoalWrapRow(wrap.goal.id, label, lines, wrap.streak?.takeIf { counts && it > 0 })
        }
        return rows.takeIf { it.isNotEmpty() }?.let(::GoalWrapCard)
    }

    private fun lines(wrap: GoalWrapUp, policy: VisibilityPolicy, context: FormatContext, caloriesShown: Boolean): List<GoalWrapLine> {
        val outcome = wrap.outcome
        if (outcome == GoalOutcome.NotCounted) return emptyList()
        return when (val type = wrap.goal.type) {
            is GoalType.Weight, is GoalType.EnergyBalance -> if (caloriesShown) calories(wrap, policy, context) else emptyList()
            GoalType.TrackEveryDay -> if (outcome == GoalOutcome.OnPlan) listOf(GoalWrapLine.ShowedUp) else emptyList()
            is GoalType.Meals -> listOf(GoalWrapLine.Meals(wrap.facts.mealsWithFood, type.count, outcome == GoalOutcome.OnPlan))
            is GoalType.Burn -> {
                val met = outcome == GoalOutcome.OnPlan
                val burned = if (type.measure == BurnMeasure.Total) wrap.facts.burnedKcal else wrap.facts.activeKcal
                val shownBurned = burned?.let { policy.resolve(Metric.EnergyOut, it.toDouble(), Surface.DayClose, context) } as? Display.Shown
                val shownGoal = policy.resolve(Metric.EnergyOut, type.kcal.toDouble(), Surface.DayClose, context) as? Display.Shown
                when {
                    met -> listOf(GoalWrapLine.Burn(met = true))
                    shownBurned != null && shownGoal != null -> listOf(GoalWrapLine.Burn(met = false, burned = shownBurned, goal = shownGoal))
                    else -> emptyList()
                }
            }
            is GoalType.Steps -> if (outcome == GoalOutcome.OnPlan) listOf(GoalWrapLine.StepsMet) else emptyList()
            is GoalType.Unknown -> emptyList()
        }
    }

    private fun calories(wrap: GoalWrapUp, policy: VisibilityPolicy, context: FormatContext): List<GoalWrapLine> {
        val week = wrap.week?.takeIf { it.averaging == GoalAveraging.Weekly }
        val onTrack = wrap.weekOnTrack == true
        val main = GoalWrapLine.Calories(wrap.outcome, weekly = week != null, weekOnTrack = onTrack)
        if (week == null) return listOf(main)
        // The week's own line: on track, or room to eat more. A tighter week is never spelled out
        // here: after a big day that would be a day asked to make up for it.
        val folded = wrap.outcome == GoalOutcome.ABitOver || wrap.outcome == GoalOutcome.ABitUnder || wrap.outcome == GoalOutcome.BigDay
        val extra = when {
            week.remainingDays > 0 && week.adjustmentKcal > 0 ->
                (policy.resolve(Metric.Calories, week.todayTargetKcal.toDouble(), Surface.DayClose, context) as? Display.Shown)?.let(GoalWrapLine::WeekEase)
            onTrack && !folded -> GoalWrapLine.WeekOnTrack
            else -> null
        }
        return listOfNotNull(main, extra)
    }
}
