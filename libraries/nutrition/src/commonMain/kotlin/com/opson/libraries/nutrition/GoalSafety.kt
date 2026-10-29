package com.opson.libraries.nutrition

import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

// TODO(clinician): every line in this file was picked by an engineer from what the big trackers
// publish (docs/research/goals-ux.md) and must be reviewed before launch. They only decide when a
// soft note shows, never what anyone can set: MIN_GOAL_KCAL is the one hard limit.
//
// Lose It warns, and MyFitnessPal stops calculating, under 1,200 kcal a day for women and 1,500
// for men. Someone who hasn't said, or would rather not, gets the point halfway between, as
// resting energy and the vitamin references already do for them.
const val LOW_GOAL_FEMALE_KCAL: Int = 1200
const val LOW_GOAL_MALE_KCAL: Int = 1500

// The usual ceiling on a healthy pace of loss: about 1,000 kcal a day under what you burn, which
// is roughly 1 kg (2 lb) a week, or 1% of body weight a week for a smaller body. Gaining has its
// own, lower line, since past about half a percent a week most of the extra is fat.
const val FAST_DEFICIT_KCAL: Int = 1000
const val FAST_LOSS_SHARE_A_WEEK: Double = 0.01
const val FAST_GAIN_SHARE_A_WEEK: Double = 0.005

// The energy in a kilogram of body weight lost or gained, the usual rule of thumb (3,500 kcal a
// pound). Real change is slower than this predicts, so the pace it gives is, if anything, gentle.
const val KCAL_PER_KG: Int = 7700

/**
 * When a goal is lower, or a pace faster, than most people should go without a doctor's
 * guidance. Pure, so Targets and a future weight goal screen ask the same question the same way.
 * Nothing here refuses a goal: it decides when a gentle note shows.
 */
object GoalSafety {

    /** The line under which a day's goal gets the low-goal note, for [sex] (null: not said). */
    fun lowGoalKcal(sex: BodySex?): Int = when (sex) {
        BodySex.Female -> LOW_GOAL_FEMALE_KCAL
        BodySex.Male -> LOW_GOAL_MALE_KCAL
        BodySex.Unsaid, null -> (LOW_GOAL_FEMALE_KCAL + LOW_GOAL_MALE_KCAL) / 2
    }

    /** Whether [kcal], the least a day's goal asks for, is under [lowGoalKcal]. */
    fun isLowGoal(kcal: Int, sex: BodySex?): Boolean = kcal < lowGoalKcal(sex)

    /**
     * Whether eating [deficitKcal] a day under what you burn is faster than a healthy pace: over
     * [FAST_DEFICIT_KCAL], or over [FAST_LOSS_SHARE_A_WEEK] of [weightKg] a week when the weight
     * is known (which matters for a smaller body, where 1,000 a day is well over 1%).
     */
    fun isFastDeficit(deficitKcal: Int, weightKg: Double? = null): Boolean {
        if (deficitKcal <= 0) return false
        if (deficitKcal > FAST_DEFICIT_KCAL) return true
        val weekly = weightKg?.takeIf { it > 0 } ?: return false
        return deficitKcal * DAYS_A_WEEK.toDouble() / KCAL_PER_KG > weekly * FAST_LOSS_SHARE_A_WEEK
    }

    /**
     * What reaching [goalKg] from [currentKg] by [by] asks of each day from [today]: the daily
     * deficit (negative) or surplus, the kilograms a week, and whether that pace is gentle. Null
     * when [by] is not after [today], since no pace can get there. A future weight goal screen
     * shows [WeightGoalPace.pace]'s note; nothing here is shown as a target in its own right.
     */
    fun weightGoalPace(currentKg: Double, goalKg: Double, today: LocalDate, by: LocalDate): WeightGoalPace? {
        if (currentKg <= 0 || goalKg <= 0) return null
        val days = (by.toEpochDays() - today.toEpochDays()).toInt()
        if (days <= 0) return null
        val changeKg = goalKg - currentKg
        val kgPerWeek = changeKg / days * DAYS_A_WEEK
        val dailyKcal = (changeKg * KCAL_PER_KG / days).roundToInt()
        val share = abs(kgPerWeek) / currentKg
        val pace = when {
            abs(changeKg) < AT_GOAL_KG -> Pace.AtGoal
            changeKg < 0 && (-dailyKcal > FAST_DEFICIT_KCAL || share > FAST_LOSS_SHARE_A_WEEK) -> Pace.TooFast
            changeKg > 0 && share > FAST_GAIN_SHARE_A_WEEK -> Pace.TooFast
            else -> Pace.Gentle
        }
        return WeightGoalPace(dailyKcal = dailyKcal, kgPerWeek = kgPerWeek, pace = pace)
    }

    private const val DAYS_A_WEEK = 7

    /** Closer than this is already there: a day's water moves the scale more. */
    private const val AT_GOAL_KG = 0.5
}

/**
 * What a weight goal with a date asks of each day. [dailyKcal] is negative for a deficit and
 * positive for a surplus; [kgPerWeek] the same way.
 */
data class WeightGoalPace(val dailyKcal: Int, val kgPerWeek: Double, val pace: Pace)

enum class Pace {
    /** Already within half a kilogram. */
    AtGoal,

    /** A pace most guidance calls healthy. */
    Gentle,

    /** Faster than that: the goal screen shows the healthy-pace note. */
    TooFast,
}
