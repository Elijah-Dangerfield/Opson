package com.opson.libraries.nutrition

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.ceil

/** One week of a projection: where weight would be on [date] eating at the band's low end, the target, and the high end. */
data class ProjectionPoint(val date: LocalDate, val lowKcalKg: Double, val targetKg: Double, val highKcalKg: Double)

/**
 * A weight goal's projection (docs/plans/goals-v2.md, "Range or one number"): one weekly series for
 * eating at each of the band's low end, the target and the high end. For a loss the low end is the
 * best case and the high end the worst; for a gain the other way round. Each series stops at the
 * goal weight. [reached] is the date each series gets there, null when it doesn't within [points].
 *
 * The 7,700 kcal a kilogram rule overstates change over months (Hall et al., Lancet 2011), so this
 * is drawn as a band and called a rough projection, never a promise.
 */
data class WeightProjection(
    val points: List<ProjectionPoint>,
    val lowKcalReached: LocalDate?,
    val targetReached: LocalDate?,
    val highKcalReached: LocalDate?,
) {
    /** The date the best case gets there: the low end for a loss, the high end for a gain. */
    fun bestReached(direction: GoalDirection): LocalDate? = if (direction == GoalDirection.Gain) highKcalReached else lowKcalReached

    fun worstReached(direction: GoalDirection): LocalDate? = if (direction == GoalDirection.Gain) lowKcalReached else highKcalReached
}

object GoalProjections {
    private const val DAYS_A_WEEK = 7

    /** Two years: past it, the projection says nothing useful. */
    const val MAX_WEEKS = 104

    /**
     * [goal]'s projection from [fromKg] on [from] (the latest weigh-in, or the goal's start), or null
     * when it isn't a weight goal with a target weight and a target. Points are weekly, until the
     * slowest series that gets there does, at most [MAX_WEEKS].
     */
    fun weight(goal: Goal, fromKg: Double, from: LocalDate): WeightProjection? {
        val type = goal.weight ?: return null
        val targetKg = type.targetKg ?: return null
        val baseline = goal.baseline ?: return null
        val target = GoalTargets.of(goal) ?: return null
        fun kgPerDay(intakeKcal: Int): Double {
            // What a day burns at that intake: digesting food is a tenth of what is eaten.
            val burn = baseline.burnKcal + baseline.extraKcal + if (baseline.digesting) thermicEffectKcal(intakeKcal.toDouble()) else 0
            return (intakeKcal - burn).toDouble() / KCAL_PER_KG
        }
        val rates = listOf(kgPerDay(target.minKcal), kgPerDay(target.aimKcal), kgPerDay(target.maxKcal))
        val reached = rates.map { rate -> daysTo(fromKg, targetKg, rate)?.let { from.plus(DatePeriod(days = it)) } }
        val lastDays = rates.mapNotNull { daysTo(fromKg, targetKg, it) }.maxOrNull()
        val weeks = ((lastDays ?: (MAX_WEEKS * DAYS_A_WEEK)) + DAYS_A_WEEK - 1) / DAYS_A_WEEK
        val points = (0..weeks.coerceAtMost(MAX_WEEKS)).map { week ->
            val days = week * DAYS_A_WEEK
            val (low, mid, high) = rates.map { at(fromKg, targetKg, it, days) }
            ProjectionPoint(from.plus(DatePeriod(days = days)), low, mid, high)
        }
        return WeightProjection(points, reached[0], reached[1], reached[2])
    }

    /** Weight after [days] at [kgPerDay], held at [targetKg] once reached. */
    private fun at(fromKg: Double, targetKg: Double, kgPerDay: Double, days: Int): Double {
        val moved = fromKg + kgPerDay * days
        val towards = targetKg - fromKg
        return if ((towards < 0 && moved < targetKg) || (towards > 0 && moved > targetKg)) targetKg else moved
    }

    /** Whole days from [fromKg] to [targetKg] at [kgPerDay]; null when it moves the wrong way or not at all. */
    private fun daysTo(fromKg: Double, targetKg: Double, kgPerDay: Double): Int? {
        val distance = targetKg - fromKg
        if (abs(distance) < WeightGoals.AT_GOAL_KG) return 0
        if (kgPerDay == 0.0 || (distance < 0) != (kgPerDay < 0)) return null
        val days = ceil(distance / kgPerDay - SLACK).toInt()
        return days.takeIf { it <= MAX_WEEKS * DAYS_A_WEEK }
    }

    private const val SLACK = 1e-6
}
