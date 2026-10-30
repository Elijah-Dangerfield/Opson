package com.opson.libraries.nutrition

import kotlin.math.abs

// TODO(clinician): the pace tiers, picked by an engineer from the usual guidance (about 0.5 to 1%
// of body weight a week, NIH's 1 to 2 lb) and the owner's ask not to hold back people with a lot
// to lose. They only pick a note; the stepper stops at MAX_LOSS_SHARE_A_WEEK and at the low-goal
// line, nowhere else.
const val GENTLE_LOSS_SHARE_A_WEEK: Double = 0.005
const val STEADY_LOSS_SHARE_A_WEEK: Double = 0.01
const val MAX_LOSS_SHARE_A_WEEK: Double = 0.02
const val GENTLE_GAIN_SHARE_A_WEEK: Double = 0.0025
const val STEADY_GAIN_SHARE_A_WEEK: Double = 0.005
const val MAX_GAIN_SHARE_A_WEEK: Double = 0.01

// Without a weight, the tiers go by the day's deficit instead.
const val GENTLE_DEFICIT_KCAL: Int = 500

/**
 * How a pace reads, gentlest first. [AboveFatLimit] wins over the others: a lean body can cross it
 * at a [Steady] pace.
 */
enum class PaceTier {
    Gentle,
    Steady,

    /** Faster than most guidance: the note suggests a doctor and links to Find support. */
    Faster,

    /** The deficit is over the personal fat-loss limit ([FatLoss]): more of it would likely come from muscle. */
    AboveFatLimit,
}

/**
 * One pace, judged. [deficitKcal] is the energy a day under a typical day (0 for a gain);
 * [sharePerWeek] the share of body weight a week, null without a weight; [limit] the personal
 * fat-loss limit when one could be worked out, which the (i) sheet explains.
 */
data class PaceAssessment(
    val tier: PaceTier,
    val deficitKcal: Int,
    val sharePerWeek: Double?,
    val limit: FatLossLimit?,
) {
    /** Faster than most guidance, or past the fat-loss limit: the note links to Find support. */
    val needsCare: Boolean get() = tier == PaceTier.Faster || tier == PaceTier.AboveFatLimit
}

/** Pace tiers and the paces a goal offers (docs/plans/goals-v2.md, "Pace"). */
object GoalPace {
    private const val DAYS_A_WEEK = 7

    /** [kgPerWeek] toward [direction], from [weightKg] when known, against [limit] when known. */
    fun assess(direction: GoalDirection, kgPerWeek: Double, weightKg: Double?, limit: FatLossLimit?): PaceAssessment {
        val pace = abs(kgPerWeek)
        val share = weightKg?.takeIf { it > 0 }?.let { pace / it }
        val deficit = if (direction == GoalDirection.Lose) WeightGoals.kcalPerDay(pace) else 0
        val tier = when (direction) {
            GoalDirection.Lose -> lossTier(deficit, share, limit)
            GoalDirection.Gain -> gainTier(share)
            else -> PaceTier.Gentle
        }
        return PaceAssessment(tier, deficit, share, limit)
    }

    /** A set daily deficit or surplus ([deltaKcal], negative to lose) judged the same way. */
    fun assessDelta(deltaKcal: Int, weightKg: Double?, limit: FatLossLimit?): PaceAssessment {
        val kgPerWeek = abs(deltaKcal).toDouble() * DAYS_A_WEEK / KCAL_PER_KG
        val direction = when {
            deltaKcal < 0 -> GoalDirection.Lose
            deltaKcal > 0 -> GoalDirection.Gain
            else -> GoalDirection.Maintain
        }
        return assess(direction, kgPerWeek, weightKg, limit)
    }

    private fun lossTier(deficit: Int, share: Double?, limit: FatLossLimit?): PaceTier = when {
        limit != null && deficit > limit.kcalPerDay -> PaceTier.AboveFatLimit
        share == null -> when {
            deficit <= GENTLE_DEFICIT_KCAL -> PaceTier.Gentle
            deficit <= FAST_DEFICIT_KCAL -> PaceTier.Steady
            else -> PaceTier.Faster
        }
        share <= GENTLE_LOSS_SHARE_A_WEEK + SLACK -> PaceTier.Gentle
        share <= STEADY_LOSS_SHARE_A_WEEK + SLACK -> PaceTier.Steady
        else -> PaceTier.Faster
    }

    private fun gainTier(share: Double?): PaceTier = when {
        share == null -> PaceTier.Steady
        share <= GENTLE_GAIN_SHARE_A_WEEK + SLACK -> PaceTier.Gentle
        share <= STEADY_GAIN_SHARE_A_WEEK + SLACK -> PaceTier.Steady
        else -> PaceTier.Faster
    }

    /** The fastest pace offered for [direction]: a share of [weightKg], and for a loss never an aim under the low-goal line. */
    fun fastestKgPerWeek(direction: GoalDirection, weightKg: Double, burnKcal: Int?, sex: BodySex?): Double = when (direction) {
        GoalDirection.Lose -> {
            val byShare = weightKg * MAX_LOSS_SHARE_A_WEEK
            val byFloor = burnKcal?.let { maxOf(0, it - GoalSafety.lowGoalKcal(sex)).toDouble() * DAYS_A_WEEK / KCAL_PER_KG }
            listOfNotNull(byShare, byFloor).min()
        }
        GoalDirection.Gain -> weightKg * MAX_GAIN_SHARE_A_WEEK
        else -> 0.0
    }

    private const val SLACK = 1e-9
}
