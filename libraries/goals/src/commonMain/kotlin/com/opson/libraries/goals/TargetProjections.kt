package com.opson.libraries.goals

import com.opson.libraries.nutrition.GoalBaseline
import com.opson.libraries.nutrition.GoalTargets
import com.opson.libraries.nutrition.Goal
import com.opson.libraries.nutrition.KCAL_PER_KG
import com.opson.libraries.nutrition.ProjectionPoint
import com.opson.libraries.nutrition.WeightProjection
import com.opson.libraries.nutrition.thermicEffectKcal
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Where weight would go eating a calorie target with no goal weight behind it: an energy balance
 * goal, or a target the person set themselves, against a typical day's burn (owner feedback,
 * 2026-10-30: "if I choose a surplus or deficit and we have the user's weight, chart it").
 *
 * The same rough rule as [com.opson.libraries.nutrition.GoalProjections.weight] (7,700 kcal a
 * kilogram, digesting food a tenth of what is eaten where it counts), drawn for [WEEKS] weeks as a
 * band: eating at the low end, the aim and the high end. Nothing is ever "reached", so each
 * series simply runs on; the chart calls it a rough projection, never a promise.
 */
object TargetProjections {

    /** Three months: far enough to see a direction, near enough not to promise a body. */
    const val WEEKS = 12

    private const val DAYS_A_WEEK = 7

    /**
     * Weekly points from [fromKg] on [from], eating [minKcal], [aimKcal] and [maxKcal] against
     * [baseline]. Null when the target sits within a rounding of the burn at every end, where
     * the line would be flat and say nothing.
     */
    fun weight(
        baseline: GoalBaseline,
        fromKg: Double,
        from: LocalDate,
        minKcal: Int,
        aimKcal: Int,
        maxKcal: Int,
        weeks: Int = WEEKS,
    ): WeightProjection? {
        if (fromKg <= 0 || weeks <= 0) return null
        fun kgPerDay(intakeKcal: Int): Double {
            val burn = baseline.burnKcal + baseline.extraKcal + if (baseline.digesting) thermicEffectKcal(intakeKcal.toDouble()) else 0
            return (intakeKcal - burn).toDouble() / KCAL_PER_KG
        }
        val rates = listOf(kgPerDay(minKcal), kgPerDay(aimKcal), kgPerDay(maxKcal))
        if (rates.all { kotlin.math.abs(it * DAYS_A_WEEK * weeks) < FLAT_KG }) return null
        val points = (0..weeks).map { week ->
            val days = week * DAYS_A_WEEK
            ProjectionPoint(from.plus(DatePeriod(days = days)), fromKg + rates[0] * days, fromKg + rates[1] * days, fromKg + rates[2] * days)
        }
        return WeightProjection(points, lowKcalReached = null, targetReached = null, highKcalReached = null)
    }

    /** An energy balance goal's projection from [fromKg] on [from]; null without a baseline or for any other type. */
    fun of(goal: Goal, fromKg: Double, from: LocalDate): WeightProjection? {
        val baseline = goal.baseline ?: return null
        if (goal.type.deltaKcal == null) return null
        val target = GoalTargets.of(goal) ?: return null
        return weight(baseline, fromKg, from, target.minKcal, target.aimKcal, target.maxKcal)
    }

    /** Less than this over the whole span reads as no change: a day's water moves the scale more. */
    private const val FLAT_KG = 0.25
}
