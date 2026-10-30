package com.opson.libraries.nutrition

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Which way a weight goal points. [None] is never asked; [JustTrack] is a real answer: no goal. */
enum class GoalDirection(val key: String) {
    None("none"),
    Lose("lose"),
    Maintain("maintain"),
    Gain("gain"),
    JustTrack("just_track"),
    ;

    /** A goal with a target weight and a pace. */
    val changesWeight: Boolean get() = this == Lose || this == Gain

    companion object {
        fun fromKey(key: String): GoalDirection? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Pregnant or breastfeeding, picked with sex on Profile and in onboarding. It only changes the
 * energy estimate ([extraKcal]) and keeps weight change out of the plan; the equation itself uses
 * the female constant, as there is no other.
 */
enum class BodyStage(val key: String, val extraKcal: Int) {
    None("none", 0),

    // TODO(clinician): the extra energy each stage needs a day, from the US Dietary Reference
    // Intakes (IOM 2005): about 340 kcal in the second trimester (0 in the first, 450 in the
    // third), and about 330 kcal while breastfeeding in the first six months. Opson doesn't ask
    // the trimester, so pregnancy takes the second trimester's.
    Pregnant("pregnant", 340),
    Breastfeeding("breastfeeding", 330),
    ;

    companion object {
        fun fromKey(key: String): BodyStage? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A weight goal: where the person wants to be ([targetKg]), how fast ([kgPerWeek], always
 * positive), and where they started ([startKg] on [startDate]). Only [GoalDirection.Lose] and
 * [GoalDirection.Gain] carry a target and a pace.
 *
 * [targetsKcal] is the energy aim the goal last wrote to Targets. It is how the app knows the goal
 * still sets the day's target ([drives]): someone who moves their range by hand has a different
 * aim, and Targets then says the goal no longer sets it instead of fighting them for it.
 */
data class WeightGoal(
    val direction: GoalDirection = GoalDirection.None,
    val targetKg: Double? = null,
    val kgPerWeek: Double = 0.0,
    val startKg: Double? = null,
    val startDate: LocalDate? = null,
    val targetsKcal: Int? = null,
) {
    /** Answered, either way: a goal, or "just track". */
    val isSet: Boolean get() = direction != GoalDirection.None

    /** Energy a day under (negative) or over (positive) what a typical day burns. */
    val dailyKcal: Int
        get() = when (direction) {
            GoalDirection.Lose -> -WeightGoals.kcalPerDay(kgPerWeek)
            GoalDirection.Gain -> WeightGoals.kcalPerDay(kgPerWeek)
            else -> 0
        }

    /** Roughly when the target is reached at this pace from the start; null without one. */
    val forecast: LocalDate?
        get() {
            if (!direction.changesWeight) return null
            return WeightGoals.forecast(startKg ?: return null, targetKg ?: return null, kgPerWeek, startDate ?: return null)
        }

    /** Whether [targets] is still the goal's own: a range on the aim it wrote. */
    fun drives(targets: Targets): Boolean =
        targetsKcal != null && targets.style == TargetStyle.Range && targets.energyKcal == targetsKcal

    companion object {
        val None = WeightGoal()
    }
}

/**
 * The arithmetic behind a weight goal. Every number here is an estimate and the screens say so:
 * the 7,700 kcal a kilogram (3,500 a pound) rule overstates how fast weight changes over months,
 * because the body burns less as it gets lighter (Hall et al., Lancet 2011). Paces go past the
 * usual guidance, each with its note ([GoalPace]); only the low-goal line stops the stepper.
 */
object WeightGoals {
    private const val DAYS_A_WEEK = 7

    // TODO(clinician): BMI 18.5 is the WHO's lower line for a healthy weight in adults. It is a
    // population measure that knows nothing of build, so it only ever shows a soft note.
    const val HEALTHY_BMI_LOW: Double = 18.5

    /** Closer than this is already there: a day's water moves the scale more. */
    const val AT_GOAL_KG: Double = 0.5

    /** Goal weights the app takes, as for a weigh-in: anything outside is a typo. */
    val GOAL_KG: ClosedFloatingPointRange<Double> = 25.0..400.0

    /** One tap of the pace stepper: a tenth of a kilogram, or a quarter of a pound, a week. */
    fun rateStep(unit: WeightUnit): Double = if (unit == WeightUnit.Lb) Units.lbToKg(QUARTER_LB) else TENTH_KG

    /** Where the pace starts: half a pound, or 0.2 kg, a week. Slow, and easy to keep. */
    fun defaultRate(unit: WeightUnit): Double = rateStep(unit) * 2

    /** [kgPerWeek] as energy a day, to the nearest 10: 0.5 kg a week is about 550. */
    fun kcalPerDay(kgPerWeek: Double): Int = (abs(kgPerWeek) * KCAL_PER_KG / DAYS_A_WEEK / 10).roundToInt() * 10

    /** The fastest pace the plan offers for [direction] from [weightKg]: [GoalPace.fastestKgPerWeek]. 0 when there is no room at all. */
    fun fastestKgPerWeek(direction: GoalDirection, weightKg: Double, burnKcal: Int?, sex: BodySex?): Double =
        GoalPace.fastestKgPerWeek(direction, weightKg, burnKcal, sex)

    /**
     * The paces the stepper can land on, slowest first: whole steps of [rateStep] up to
     * [fastestKgPerWeek]. Empty when not even one step fits, which the pace step says plainly.
     */
    fun rates(direction: GoalDirection, unit: WeightUnit, weightKg: Double, burnKcal: Int?, sex: BodySex?): List<Double> {
        val step = rateStep(unit)
        // A hair of slack, so 1% of 50 kg (0.5) still allows the fifth 0.1 kg step.
        val count = floor(fastestKgPerWeek(direction, weightKg, burnKcal, sex) / step + SLACK).toInt()
        return (1..count).map { it * step }
    }

    /** [current] moved to the nearest pace in [rates], or the default when it fits, or the fastest. */
    fun nearestRate(current: Double?, rates: List<Double>, unit: WeightUnit): Double? {
        if (rates.isEmpty()) return null
        val wanted = current ?: defaultRate(unit)
        return rates.minBy { abs(it - wanted) }
    }

    /**
     * When [targetKg] is reached from [startKg] at [kgPerWeek], counted from [startDate], to the
     * day. Null for no pace or no distance.
     */
    fun forecast(startKg: Double, targetKg: Double, kgPerWeek: Double, startDate: LocalDate): LocalDate? {
        val distance = abs(targetKg - startKg)
        if (kgPerWeek <= 0 || distance < AT_GOAL_KG) return null
        // The slack keeps ten pounds at half a pound a week at 140 days, not 141, after the unit round trip.
        val days = ceil(distance / kgPerWeek * DAYS_A_WEEK - SLACK).toInt()
        return startDate.plus(DatePeriod(days = days))
    }

    /** The lowest weight in a healthy range for [heightCm], by BMI; null without a usable height. */
    fun lowestHealthyKg(heightCm: Double?): Double? {
        val metres = heightCm?.let(Heights::inRange)?.div(CM_PER_M) ?: return null
        return HEALTHY_BMI_LOW * metres * metres
    }

    /** Whether [targetKg] is under a healthy range for [heightCm]. A soft note, never a block. */
    fun belowHealthyRange(targetKg: Double, heightCm: Double?): Boolean = lowestHealthyKg(heightCm)?.let { targetKg < it } ?: false

    /** Whether [targetKg] points the way [direction] says from [currentKg], by more than [AT_GOAL_KG]. */
    fun pointsTheRightWay(direction: GoalDirection, currentKg: Double, targetKg: Double): Boolean = when (direction) {
        GoalDirection.Lose -> targetKg <= currentKg - AT_GOAL_KG
        GoalDirection.Gain -> targetKg >= currentKg + AT_GOAL_KG
        else -> true
    }

    /**
     * The day's energy aim: a typical day's burn ([burnKcal]), plus what pregnancy or
     * breastfeeding needs, plus the goal's [WeightGoal.dailyKcal]. A loss never takes it under the
     * low-goal line for [sex], whatever the pace says.
     */
    fun aimKcal(goal: WeightGoal, burnKcal: Int, sex: BodySex?, stage: BodyStage = BodyStage.None): Int {
        val aim = burnKcal + stage.extraKcal + goal.dailyKcal
        val floored = if (goal.direction == GoalDirection.Lose) max(aim, GoalSafety.lowGoalKcal(sex)) else aim
        return (floored / 10.0).roundToInt() * 10
    }

    /**
     * [targets] set from [goal]: a range of the same width on the aim ([aimKcal]), macros scaled
     * with it when [scaleMacros], and the goal stamped with the aim it wrote so [WeightGoal.drives]
     * can tell when someone changes it by hand. A fixed range rather than a set deficit, so the
     * day's target is the same morning and evening and in every mode; the deficit it means is said
     * where calories show.
     */
    fun apply(goal: WeightGoal, targets: Targets, aimKcal: Int, scaleMacros: Boolean): Pair<WeightGoal, Targets> {
        val set = targets.useRange().withEnergy(aimKcal, scaleMacros)
        return goal.copy(targetsKcal = set.energyKcal) to set
    }

    /** "0.25 lb" or "0.2 kg": a pace or a distance. Weight is a number: the caller checks it is on show. */
    fun weightText(kg: Double, unit: WeightUnit): String =
        if (unit == WeightUnit.Lb) "${upToTwoDecimals(Units.kgToLb(kg))} lb" else "${upToTwoDecimals(kg)} kg"

    private const val QUARTER_LB = 0.25
    private const val TENTH_KG = 0.1
    private const val SLACK = 1e-6
    private const val CM_PER_M = 100.0
}
