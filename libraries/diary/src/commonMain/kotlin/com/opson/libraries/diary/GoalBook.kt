package com.opson.libraries.diary

import com.opson.libraries.nutrition.Goal
import com.opson.libraries.nutrition.GoalAveraging
import com.opson.libraries.nutrition.GoalBaseline
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GoalStatus
import com.opson.libraries.nutrition.GoalTargets
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.WeightGoal
import kotlinx.datetime.LocalDate

/**
 * The person's goals as [Settings] holds them (docs/plans/goals-v2.md, "Storage and migration").
 * Read goals through [goals], never [Settings.goals] directly: before any v2 build saved them the
 * list is migrated from [Settings.weightGoal], and a [Settings.weightGoal] an older build changed
 * since is folded back in. Write through [withGoals] or [withGoal], which keep [Settings.weightGoal]
 * in step for those builds.
 */
object GoalBook {

    /** Where a migrated goal with no start date starts: every day counts. */
    private val LONG_AGO = LocalDate.fromEpochDays(0)

    /**
     * The goals, migrated or reconciled. A goal setting the target whose stamp no longer matches
     * Targets (an older build moved the target by hand) reads as overridden.
     */
    fun goals(settings: Settings): List<Goal> {
        val stored = settings.goals
        val goals = when {
            stored == null -> migrate(settings.weightGoal, settings)
            mirror(stored, settings.weightGoal) != settings.weightGoal -> reconcile(stored, settings)
            else -> stored
        }
        return goals.map { goal -> if (goal.drivesTarget && !stampMatches(goal, settings.targets)) goal.copy(setsTarget = false) else goal }
    }

    /** The active goal that sets the calorie target, if any. */
    fun driving(settings: Settings): Goal? = goals(settings).firstOrNull { it.drivesTarget }

    /** The active (or paused) calorie goal, whether or not it is setting the target right now. */
    fun calorieGoal(settings: Settings): Goal? =
        goals(settings).firstOrNull { it.type.setsCalories && it.status != GoalStatus.Done }

    /** The weight goal, active or paused. */
    fun weight(settings: Settings): Goal? = goals(settings).firstOrNull { it.type is GoalType.Weight && it.status != GoalStatus.Done }

    /**
     * [settings] with [goals], and [Settings.weightGoal] written to match so builds that only
     * know it keep working. Only one goal may set the calorie target: the first active one keeps
     * it, and any other active calorie goal is paused.
     */
    fun withGoals(settings: Settings, goals: List<Goal>): Settings {
        val lead = goals.firstOrNull { it.isActive && it.type.setsCalories }
        val single = goals.map { if (it !== lead && it.isActive && it.type.setsCalories) it.copy(status = GoalStatus.Paused) else it }
        return settings.copy(goals = single, weightGoal = mirror(single, settings.weightGoal))
    }

    /** [goal] added, or put in place of the goal with its id. An active calorie goal goes first, so it is the one that leads. */
    fun withGoal(settings: Settings, goal: Goal): Settings {
        val others = goals(settings).filterNot { it.id == goal.id }
        val ordered = if (goal.isActive && goal.type.setsCalories) listOf(goal) + others else others + goal
        return withGoals(settings, ordered)
    }

    /** The goal with [id] removed. */
    fun without(settings: Settings, id: String): Settings = withGoals(settings, goals(settings).filterNot { it.id == id })

    /**
     * [goal]'s target written to Targets, the goal stamped and saved; macros scale with it under
     * [MacroMode.Ratios]. [settings] unchanged when the goal has no target to write yet.
     */
    fun applyTarget(settings: Settings, goal: Goal): Settings {
        val (stamped, targets) = GoalTargets.apply(goal, settings.targets, scaleMacros = settings.macroMode == MacroMode.Ratios) ?: return settings
        return withGoal(settings.copy(targets = targets), stamped)
    }

    /** What [Settings.weightGoal] says for [goals], given what it said before ([previous]). */
    fun mirror(goals: List<Goal>, previous: WeightGoal): WeightGoal {
        val goal = goals.firstOrNull { it.type is GoalType.Weight && it.status != GoalStatus.Done }
        val weight = goal?.type as? GoalType.Weight
        if (goal == null || weight == null) {
            // No weight goal: a goal answered before stays answered, as "just track".
            return if (previous.direction == GoalDirection.None || previous.direction == GoalDirection.JustTrack) previous else WeightGoal(GoalDirection.JustTrack)
        }
        return WeightGoal(
            direction = weight.direction,
            targetKg = weight.targetKg,
            kgPerWeek = weight.kgPerWeek,
            startKg = weight.startKg,
            startDate = goal.startDate,
            targetsKcal = goal.targetsKcal.takeIf { goal.drivesTarget },
        )
    }

    /** The weight goal a pre-v2 [weightGoal] means. Its baseline burn is recovered from the target it wrote. */
    private fun migrate(weightGoal: WeightGoal, settings: Settings): List<Goal> = listOfNotNull(fromWeightGoal(weightGoal, settings, existing = null))

    /** [stored] with its weight goal rebuilt from a [Settings.weightGoal] an older build changed. */
    private fun reconcile(stored: List<Goal>, settings: Settings): List<Goal> {
        val existing = stored.firstOrNull { it.type is GoalType.Weight }
        val rebuilt = fromWeightGoal(settings.weightGoal, settings, existing)
        val rest = stored.filterNot { it === existing }
        return if (rebuilt == null) rest else listOf(rebuilt) + rest.map { if (it.isActive && it.type.setsCalories) it.copy(status = GoalStatus.Paused) else it }
    }

    private fun fromWeightGoal(weightGoal: WeightGoal, settings: Settings, existing: Goal?): Goal? {
        val direction = weightGoal.direction
        if (direction != GoalDirection.Lose && direction != GoalDirection.Gain && direction != GoalDirection.Maintain) return null
        val type = GoalType.Weight(direction, weightGoal.targetKg, weightGoal.kgPerWeek, weightGoal.startKg)
        val stamp = weightGoal.targetsKcal
        // The stamp is the aim the old flow wrote: burn + delta, held at the low-goal line. Taking
        // the delta back off gives a burn that writes the same aim again.
        val recovered = stamp?.let { GoalBaseline(burnKcal = it - (type.deltaKcal ?: 0), sex = settings.bodySex) }
        val keepBaseline = existing?.baseline?.takeIf { stamp == null || GoalTargets.aimKcal(type, it) == stamp }
        return Goal(
            id = existing?.id ?: Goal.WEIGHT_ID,
            type = type,
            startDate = weightGoal.startDate ?: existing?.startDate ?: LONG_AGO,
            status = GoalStatus.Active,
            averaging = existing?.averaging ?: GoalAveraging.Daily,
            toleranceKcal = existing?.toleranceKcal ?: Goal.DEFAULT_TOLERANCE_KCAL,
            // Never stamped: still waiting on details, so it sets the target once it can.
            setsTarget = stamp == null || stampMatches(stamp, settings.targets),
            baseline = keepBaseline ?: recovered,
            declined = existing?.declined,
            targetsKcal = stamp,
        )
    }

    private fun stampMatches(goal: Goal, targets: Targets): Boolean = goal.targetsKcal?.let { stampMatches(it, targets) } ?: true

    private fun stampMatches(stamp: Int, targets: Targets): Boolean = targets.style == TargetStyle.Range && targets.energyKcal == stamp
}

/** [GoalBook.goals]. */
val Settings.allGoals: List<Goal> get() = GoalBook.goals(this)
