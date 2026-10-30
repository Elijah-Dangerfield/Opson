package com.opson.libraries.nutrition

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A goal's daily target: [aimKcal], and the band around it that counts as on track. [burnKcal] is
 * the typical day it was worked out against, digesting food included when it counts, which is what
 * a deficit or surplus is measured from.
 */
data class GoalTarget(val aimKcal: Int, val minKcal: Int, val maxKcal: Int, val burnKcal: Int) {
    /** Energy a day against the typical day: negative under it, positive over. */
    val deltaKcal: Int get() = aimKcal - burnKcal
}

/**
 * The one way a goal becomes a calorie target (docs/plans/goals-v2.md, "A goal owns its inputs").
 * Pure: the same goal and baseline give the same target on every phone, every day.
 */
object GoalTargets {

    // TODO(clinician): how far a target may sit from a keep goal's typical day before Targets asks
    // whether to pause the goal. An engineer picked it: six Targets steps.
    const val KEEP_LEEWAY_KCAL: Int = 300

    /**
     * The least a goal's target is ever worked out to, whoever the person is: the lower low-goal
     * line, as for a set deficit (`Targets.workedOutFloorKcal`, 2026-10-29). Between it and the
     * person's own line ([GoalSafety.lowGoalKcal]) the target is set and says it is low
     * ([belowLowGoal]); a big body with a lot to lose isn't held back by a line drawn for an
     * average one.
     */
    val WORKED_OUT_FLOOR_KCAL: Int = max(MIN_GOAL_KCAL, LOW_GOAL_FEMALE_KCAL)

    /** Whether [aimKcal] is under the low-goal line for [sex]: the low-goal note shows with it. */
    fun belowLowGoal(aimKcal: Int, sex: BodySex?): Boolean = GoalSafety.isLowGoal(aimKcal, sex)

    /**
     * The aim for [type] against [baseline]: the burn, plus what pregnancy or breastfeeding adds,
     * plus the type's delta, solved for digesting food when it counts (a tenth of what the target
     * has you eat is burned again). A loss never aims under [WORKED_OUT_FLOOR_KCAL]; rounded to
     * 10. Null for a type that doesn't set a calorie target.
     */
    fun aimKcal(type: GoalType, baseline: GoalBaseline): Int? {
        val delta = type.deltaKcal ?: return null
        val base = (baseline.burnKcal + baseline.extraKcal + delta).toDouble()
        val raw = if (baseline.digesting) base / (1 - THERMIC_EFFECT_OF_FOOD_SHARE) else base
        val floored = if (delta < 0) max(raw, WORKED_OUT_FLOOR_KCAL.toDouble()) else raw
        return (floored / 10).roundToInt() * 10
    }

    /** What a typical day burns with the goal's target eaten: digesting food included when it counts. */
    fun burnKcal(type: GoalType, baseline: GoalBaseline): Int {
        val aim = aimKcal(type, baseline) ?: return baseline.burnKcal
        val digesting = if (baseline.digesting) thermicEffectKcal(aim.toDouble()) else 0
        return baseline.burnKcal + digesting
    }

    /** [goal]'s target and band, or null without a baseline or for a type that sets no target. */
    fun of(goal: Goal): GoalTarget? {
        val baseline = goal.baseline ?: return null
        val aim = aimKcal(goal.type, baseline) ?: return null
        val tolerance = goal.toleranceKcal.coerceAtLeast(0)
        return GoalTarget(
            aimKcal = aim,
            minKcal = max(MIN_GOAL_KCAL, aim - tolerance),
            maxKcal = min(MAX_GOAL_KCAL, aim + tolerance),
            burnKcal = burnKcal(goal.type, baseline),
        )
    }

    /**
     * [targets] set to [goal]'s band, and the goal stamped with the aim it wrote. The style becomes
     * a range of exactly the band, whatever was there before: the width is the goal's, never left
     * over from an earlier range. Macros scale with the aim when [scaleMacros]. Null when the goal
     * has no target to write.
     */
    fun apply(goal: Goal, targets: Targets, scaleMacros: Boolean): Pair<Goal, Targets>? {
        val target = of(goal) ?: return null
        val set = targets.withBand(target.aimKcal, target.minKcal, target.maxKcal, scaleMacros)
        return goal.copy(targetsKcal = set.energyKcal, setsTarget = true) to set
    }

    /**
     * Whether [targets] point against [goal]: for a loss, a target at or over what a typical day
     * burns; for a gain, at or under it; for keeping, more than [KEEP_LEEWAY_KCAL] either side.
     * Judged on the least and most any day asks for ([Targets.spanGoal]) against the baseline's
     * burn. False without a baseline, where nothing can be compared.
     */
    fun contradicts(goal: Goal, targets: Targets, preset: PresetId): Boolean {
        val baseline = goal.baseline ?: return false
        val delta = goal.type.deltaKcal ?: return false
        val burn = burnKcal(goal.type, baseline) + baseline.extraKcal
        val span = targets.spanGoal(preset, burn)
        return when {
            delta < 0 -> span.minKcal >= burn
            delta > 0 -> span.maxKcal <= burn
            else -> abs(span.targetKcal - burn) > KEEP_LEEWAY_KCAL
        }
    }
}
