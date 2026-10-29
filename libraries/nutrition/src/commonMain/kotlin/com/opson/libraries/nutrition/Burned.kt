package com.opson.libraries.nutrition

import kotlin.math.roundToInt

/**
 * What the Mifflin–St Jeor equation is told about the body. The equation has one constant for
 * each of two sexes; [Unsaid] takes the point halfway between them, so nobody has to answer to
 * get a number.
 */
enum class BodySex { Female, Male, Unsaid }

/** Where the person wants resting energy to come from (Resting energy, design sync 4, 13.2d). */
enum class RestingSource {
    /** Mifflin–St Jeor from age, height, the latest weigh-in and sex. The default. */
    Calculate,

    /** The phone's health store, falling back to [Calculate] when it has nothing. */
    Health,

    /** A number the person trusts, from a lab test or anywhere else. */
    Own,
}

/** Where the resting energy in use came from, which can differ from the [RestingSource] picked. */
enum class RestingFrom { Calculated, Health, Own }

/** Resting energy for a whole day, in kcal, and where it came from. */
data class RestingEnergy(val kcal: Int, val from: RestingFrom)

/** What the calculation needs. Any of them can be missing; [RestingEnergies.calculate] then has no answer. */
data class BodyProfile(
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val ageYears: Int? = null,
    val sex: BodySex? = null,
) {
    /** What is still needed before resting energy can be calculated. */
    val missing: Set<BodyDetail>
        get() = buildSet {
            if (weightKg == null) add(BodyDetail.Weight)
            if (heightCm == null) add(BodyDetail.Height)
            if (ageYears == null) add(BodyDetail.Age)
            if (sex == null) add(BodyDetail.Sex)
        }
}

enum class BodyDetail { Weight, Height, Age, Sex }

object RestingEnergies {
    // Inputs outside these are a typo, not a body: they give no answer rather than a wild one.
    private val WEIGHT_KG = 25.0..400.0
    private val HEIGHT_CM = 100.0..250.0
    private val AGE_YEARS = 13..120

    private const val MALE = 5.0
    private const val FEMALE = -161.0

    /** The most anyone can enter as their own, a guard against a stray extra digit. */
    const val OWN_MAX_KCAL = 5000
    const val OWN_MIN_KCAL = 500

    /**
     * Mifflin–St Jeor: 10 × kg + 6.25 × cm − 5 × age + 5 (male) or − 161 (female), rounded to 10.
     * Null while anything is missing or out of range.
     */
    fun calculate(profile: BodyProfile): Int? {
        val kg = profile.weightKg?.takeIf { it in WEIGHT_KG } ?: return null
        val cm = profile.heightCm?.takeIf { it in HEIGHT_CM } ?: return null
        val age = profile.ageYears?.takeIf { it in AGE_YEARS } ?: return null
        val constant = when (profile.sex ?: return null) {
            BodySex.Male -> MALE
            BodySex.Female -> FEMALE
            BodySex.Unsaid -> (MALE + FEMALE) / 2
        }
        return roundTo10(10 * kg + 6.25 * cm - 5 * age + constant)
    }

    /**
     * The resting energy to use. [Health][RestingSource.Health] falls back to the calculation, and
     * so does [Own][RestingSource.Own] while nothing valid is entered; null when nothing can answer.
     */
    fun resolve(source: RestingSource, calculatedKcal: Int?, healthKcal: Int?, ownKcal: Int?): RestingEnergy? {
        val calculated = calculatedKcal?.let { RestingEnergy(it, RestingFrom.Calculated) }
        return when (source) {
            RestingSource.Calculate -> calculated
            RestingSource.Health -> healthKcal?.takeIf { it > 0 }?.let { RestingEnergy(roundTo10(it.toDouble()), RestingFrom.Health) } ?: calculated
            RestingSource.Own -> ownKcal?.takeIf { it in OWN_MIN_KCAL..OWN_MAX_KCAL }?.let { RestingEnergy(it, RestingFrom.Own) } ?: calculated
        }
    }

    private fun roundTo10(kcal: Double): Int = (kcal / 10).roundToInt() * 10
}

/**
 * The thermic effect of food: the energy the body spends digesting what is eaten, as a share of
 * it. 10% of intake is the common approximation (measured values run from about 8 to 15%, higher
 * on a protein-heavy day), and the one Cronometer and most textbooks use. Neither health store
 * counts it: Apple Health's and Health Connect's resting and active energy are both modelled
 * from the body, never from food, so adding it to either can't count it twice.
 */
const val THERMIC_EFFECT_OF_FOOD_SHARE = 0.10

/** [THERMIC_EFFECT_OF_FOOD_SHARE] of [eatenKcal], in whole kcal. Nothing eaten, or a total that makes no sense, is 0. */
fun thermicEffectKcal(eatenKcal: Double): Int =
    if (!eatenKcal.isFinite() || eatenKcal <= 0.0) 0 else (eatenKcal * THERMIC_EFFECT_OF_FOOD_SHARE).roundToInt()

/**
 * What the person burns on a day (design sync 4): resting energy for the whole day, plus the
 * activity their health app has synced so far when they count it ([activityCounted]). A set
 * deficit or surplus is aimed at [totalKcal] ([Targets.rangeFor]'s `burnKcal`), so it moves
 * as activity syncs.
 *
 * [foodKcal] is the thermic effect of food ([thermicEffectKcal] of the day's eaten), with
 * "Count digesting food" on and null with it off, the default. It is neither resting energy nor
 * activity, so [activityKcal] leaves it out and "Add activity to Burned" switched off keeps it.
 * A deficit aimed at a Burned that includes it moves a little as the day's food is logged; that
 * converges (each 100 kcal eaten moves the target 10), and is the person's choice to make.
 *
 * [movementKcal] and [workoutKcal] are null when nothing reads them (no health app, or
 * `release.health` off), which is not the same as a synced zero. [everydayKcal] and
 * [exerciseKcal] are the manual side: everyday activity, which stands in for movement whenever
 * nothing reads it so a deficit works without a health app, and logged exercise
 * (`release.exercise`), null when not counted. [burnedFor] decides which side a day takes, so the
 * two never add up.
 */
data class Burned(
    val resting: RestingEnergy,
    val movementKcal: Int? = null,
    val workoutKcal: Int? = null,
    val activityCounted: Boolean = true,
    val everydayKcal: Int? = null,
    val exerciseKcal: Int? = null,
    val foodKcal: Int? = null,
) {
    /** Whether the day's everyday movement is the [EverydayActivity] estimate, not a health app's reading. */
    val movementEstimated: Boolean get() = everydayKcal != null

    /**
     * Everything above resting as counted. Switched off, only the [everydayKcal] baseline is left
     * ([burnedFor] explains why); nothing read or logged is zero.
     */
    val activityKcal: Int
        get() {
            val counted = if (activityCounted) listOf(movementKcal, workoutKcal, everydayKcal, exerciseKcal) else listOf(everydayKcal)
            return counted.sumOf { (it ?: 0).coerceAtLeast(0) }
        }

    val totalKcal: Int get() = resting.kcal + activityKcal + (foodKcal ?: 0).coerceAtLeast(0)

    /** Everything on top of resting energy: activity, and digesting food when it counts. */
    val aboveRestingKcal: Int get() = totalKcal - resting.kcal
}

/**
 * This Burned as [surface] may show it. Digesting food is a tenth of what was eaten, so a screen
 * that shows energy out with calories short of Show would give the day's eaten away with one
 * multiplication, as its own line or as the step it adds to the total. There it is left out of
 * every amount. A target aimed at Burned still counts it: a calorie target only shows where
 * calories do.
 */
fun Burned.shownOn(policy: VisibilityPolicy, surface: Surface, context: FormatContext = FormatContext()): Burned =
    if (foodKcal == null || digestingShown(policy, surface, context)) this else copy(foodKcal = null)

/** Whether digesting food may count in an energy out amount on [surface]: only where calories show in full. */
fun digestingShown(policy: VisibilityPolicy, surface: Surface, context: FormatContext = FormatContext()): Boolean =
    policy.effectiveLevel(Metric.Calories, surface, context) == Visibility.Show
