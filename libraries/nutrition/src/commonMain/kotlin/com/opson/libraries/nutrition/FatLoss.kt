package com.opson.libraries.nutrition

import kotlin.math.roundToInt

// TODO(clinician): the limit and the estimate are from published papers, picked by an engineer.
// They only decide which note a pace gets; nothing is refused because of them.
//
// Alpert (2005), "A limit on the energy transfer rate from the human fat store in hypophagia",
// J Theor Biol 233:1-13: fat can supply at most about 31.4 kcal per pound of fat mass a day, which
// is 69.2 kcal per kilogram. A deficit past it is met mostly from lean tissue.
const val FAT_KCAL_PER_KG_A_DAY: Double = 69.2

/** Where the body fat behind a limit came from. */
enum class BodyFatFrom {
    /** Typed on Profile. */
    Profile,

    /** Worked out from BMI, age and sex ([FatLoss.deurenberg]): the screens say so, and invite the real number. */
    Estimated,
}

data class BodyFatEstimate(val percent: Double, val from: BodyFatFrom)

/**
 * The most a day's deficit can be and still come mostly from fat: [kcalPerDay], for [fatMassKg]
 * of fat from [bodyFat].
 */
data class FatLossLimit(val kcalPerDay: Int, val fatMassKg: Double, val bodyFat: BodyFatEstimate)

/** The personal fat-loss limit behind the "Above your estimated fat-loss limit" note (docs/plans/goals-v2.md). */
object FatLoss {

    // Deurenberg's estimate can come out silly for bodies far from the study's; outside this it is
    // held to the edge rather than trusted.
    private const val ESTIMATE_MIN = 5.0
    private const val ESTIMATE_MAX = 60.0
    private const val ADULT_AGE = 18

    /**
     * Body fat to use: the person's own ([profilePercent], in [BodyFat.RANGE]) first, else
     * [deurenberg]'s estimate from weight, height, age and sex. Null when neither can answer.
     */
    fun bodyFat(profilePercent: Double?, weightKg: Double?, heightCm: Double?, ageYears: Int?, sex: BodySex?): BodyFatEstimate? {
        profilePercent?.let(BodyFat::inRange)?.let { return BodyFatEstimate(it, BodyFatFrom.Profile) }
        val bmi = BodyMassIndex.of(weightKg, heightCm) ?: return null
        val age = ageYears?.takeIf { it in AGES } ?: return null
        return BodyFatEstimate(deurenberg(bmi, age, sex).coerceIn(ESTIMATE_MIN, ESTIMATE_MAX), BodyFatFrom.Estimated)
    }

    /**
     * Deurenberg, Weststrate and Seidell (1991), Br J Nutr 65:105-114. Adults: 1.20 × BMI + 0.23 ×
     * age − 10.8 × sex − 5.4; 13 to 17: 1.51 × BMI − 0.70 × age − 3.6 × sex + 1.4, with sex 1 for
     * male and 0 for female. Not said takes the point halfway, as resting energy does.
     */
    fun deurenberg(bmi: Double, ageYears: Int, sex: BodySex?): Double {
        val s = when (sex) {
            BodySex.Male -> 1.0
            BodySex.Female -> 0.0
            BodySex.Unsaid, null -> 0.5
        }
        return if (ageYears >= ADULT_AGE) {
            1.20 * bmi + 0.23 * ageYears - 10.8 * s - 5.4
        } else {
            1.51 * bmi - 0.70 * ageYears - 3.6 * s + 1.4
        }
    }

    /** [weightKg]'s fat at [bodyFat], and the most a day's deficit can draw from it, to 10 kcal. */
    fun limit(weightKg: Double?, bodyFat: BodyFatEstimate?): FatLossLimit? {
        val kg = weightKg?.takeIf { it in WeightGoals.GOAL_KG } ?: return null
        val fat = bodyFat ?: return null
        val fatMass = kg * fat.percent / PERCENT
        return FatLossLimit((fatMass * FAT_KCAL_PER_KG_A_DAY / 10).roundToInt() * 10, fatMass, fat)
    }

    /** [limit] with body fat worked out as [bodyFat] does. */
    fun limitFor(profilePercent: Double?, weightKg: Double?, heightCm: Double?, ageYears: Int?, sex: BodySex?): FatLossLimit? =
        limit(weightKg, bodyFat(profilePercent, weightKg, heightCm, ageYears, sex))

    private val AGES = 13..120
    private const val PERCENT = 100.0
}
