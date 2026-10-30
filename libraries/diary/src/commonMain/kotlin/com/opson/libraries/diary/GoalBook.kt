package com.opson.libraries.diary

import com.opson.libraries.nutrition.BaselineEnvironment
import com.opson.libraries.nutrition.BaselineInputs
import com.opson.libraries.nutrition.BodyStage
import com.opson.libraries.nutrition.Goal
import com.opson.libraries.nutrition.GoalAveraging
import com.opson.libraries.nutrition.GoalBaseline
import com.opson.libraries.nutrition.GoalBaselines
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GoalRecalculated
import com.opson.libraries.nutrition.GoalStatus
import com.opson.libraries.nutrition.GoalTargets
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.HealthReadings
import com.opson.libraries.nutrition.Recalculation
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
        // While pregnant or breastfeeding Opson plans no weight change, so a calorie goal set before
        // stops setting the target and reads as the person's own; Use my goal brings it back after.
        val stage = settings.bodyStage != BodyStage.None
        return goals.map { goal ->
            if (goal.drivesTarget && (stage || !stampMatches(goal, settings.targets))) goal.copy(setsTarget = false) else goal
        }
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

    /**
     * [recalculation] accepted: the goal's baseline is the new one, nothing declined is kept, and
     * the new target is written. [settings] unchanged when the goal is gone.
     */
    fun accept(settings: Settings, recalculation: Recalculation, on: LocalDate? = null): Settings {
        val goal = goals(settings).firstOrNull { it.id == recalculation.goalId } ?: return settings
        return applyTarget(settings, goal.copy(baseline = recalculation.newBaseline, declined = null).record(recalculation, on, accepted = true))
    }

    /** [recalculation] declined: the target stays, and these inputs are never offered again. */
    fun keep(settings: Settings, recalculation: Recalculation, on: LocalDate? = null): Settings {
        val goal = goals(settings).firstOrNull { it.id == recalculation.goalId } ?: return settings
        val inputs = recalculation.newBaseline.inputs ?: return settings
        return withGoal(settings, goal.copy(declined = inputs).record(recalculation, on, accepted = false))
    }

    /** The answer kept in the goal's history, when the caller knows the day it was given [on]. */
    private fun Goal.record(recalculation: Recalculation, on: LocalDate?, accepted: Boolean): Goal =
        if (on == null) this else recorded(GoalRecalculated(on, recalculation.changes, recalculation.oldAimKcal, recalculation.newAimKcal, accepted))

    /**
     * "Set my own target": the goal setting the target stops setting it and is kept; Targets is
     * the person's. [settings] unchanged when no goal sets it.
     */
    fun override(settings: Settings): Settings {
        val goal = driving(settings) ?: return settings
        return withGoal(settings, goal.copy(setsTarget = false))
    }

    /**
     * "Use my goal": the calorie goal sets the target again, from the baseline it already has, so
     * the target is the one it had before. A paused goal is made active. Without a baseline yet,
     * it is marked to set the target and waits for one.
     */
    fun resume(settings: Settings): Settings {
        val goal = calorieGoal(settings) ?: return settings
        val active = goal.copy(status = GoalStatus.Active, setsTarget = true)
        return if (active.baseline == null) withGoal(settings, active) else applyTarget(settings, active)
    }

    /** Pauses the goal with [id]: kept with everything it had, counting for nothing until resumed. */
    fun pause(settings: Settings, id: String): Settings {
        val goal = goals(settings).firstOrNull { it.id == id } ?: return settings
        return withGoal(settings, goal.copy(status = GoalStatus.Paused))
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

/** What a goal baseline reads from these settings, with what the health app has read. */
fun Settings.baselineEnvironment(health: HealthReadings): BaselineEnvironment = BaselineEnvironment(
    restingSource = restingSource,
    ownRestingKcal = restingOwnKcal,
    health = health,
    activityCounted = activityInBurned,
    digesting = thermicEffectInBurned,
)

/**
 * A goal baseline's inputs for these settings, the latest weigh-in ([weightKg]) and the health
 * app's [health] readings on [today]. Null while resting energy can't be worked out.
 */
fun Settings.baselineInputs(weightKg: Double?, today: LocalDate, health: HealthReadings): BaselineInputs? =
    GoalBaselines.inputs(baselineEnvironment(health), bodyProfile(weightKg, today), everydayActivity, bodyStage, bodyFat)
