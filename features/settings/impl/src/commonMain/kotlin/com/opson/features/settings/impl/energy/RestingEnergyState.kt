package com.opson.features.settings.impl.energy

import com.opson.libraries.diary.Settings
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.RestingChoices
import com.opson.libraries.nutrition.BodyDetail
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.RestingSource
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WeightUnit
import kotlin.math.roundToInt

/**
 * Resting energy (design sync 4, 13.2d): where Burned's resting energy comes from, the details
 * the calculation needs, and whether activity counts. Every amount is energy out, resolved on
 * [Surface.Settings]; a level that hides it leaves the choices without numbers.
 */
data class RestingEnergyState(
    val loaded: Boolean = false,
    val source: RestingSource = RestingSource.Calculate,
    /** "1,610", bare; Hidden while the calculation is missing something, or energy out is hidden. */
    val calculated: Display = Display.Hidden,
    /** The health app's row, with `release.health` on; null without. [healthKcal] is Hidden without a reading. */
    val health: HealthSource? = null,
    val healthKcal: Display = Display.Hidden,
    /**
     * What the person typed for their own number, as typed. Null while energy out is not on show:
     * the field is a resting energy amount, so it goes with the other numbers.
     */
    val ownText: String? = null,
    /** The details the calculation still needs; shown while "Calculate" is picked and any are missing. */
    val details: BodyDetails? = null,
    /** The connected store for "Steps and workouts", null when none. */
    val activitySource: HealthSource? = null,
    val activityInBurned: Boolean = true,
    /** Everyday activity's pick (`release.exercise`); null hides the choice. */
    val everyday: EverydayActivity? = null,
)

/**
 * The calculation's inputs as fields: typed text, so a half-typed year is not lost. [heightUnit]
 * follows the weight unit: centimetres with kilograms, inches with pounds. [weightMissing] says
 * to log a weight, which lives in Trends rather than here.
 */
data class BodyDetails(
    val birthYearText: String,
    val heightText: String,
    val heightUnit: HeightUnit,
    val sex: BodySex?,
    val weightMissing: Boolean,
)

enum class HeightUnit { Cm, In }

/** What a typed field is still in the middle of, kept by the view model between settings updates. */
data class RestingDrafts(val own: String? = null, val birthYear: String? = null, val height: String? = null)

object RestingEnergyStateBuilder {
    private const val CM_PER_INCH = 2.54

    fun build(
        settings: Settings,
        choices: RestingChoices,
        policy: VisibilityPolicy,
        drafts: RestingDrafts = RestingDrafts(),
        activitySource: HealthSource? = null,
        exerciseReleased: Boolean = false,
    ): RestingEnergyState {
        val heightUnit = if (settings.weightUnit == WeightUnit.Lb) HeightUnit.In else HeightUnit.Cm
        return RestingEnergyState(
            loaded = true,
            source = settings.restingSource,
            calculated = choices.calculatedKcal.bare(policy),
            health = choices.healthSource,
            healthKcal = choices.healthKcal.bare(policy),
            ownText = (drafts.own ?: settings.restingOwnKcal?.toString().orEmpty())
                .takeIf { policy.effectiveLevel(Metric.EnergyOut, Surface.Settings) == Visibility.Show },
            details = if (settings.restingSource == RestingSource.Calculate && choices.missing.isNotEmpty()) {
                BodyDetails(
                    birthYearText = drafts.birthYear ?: settings.birthYear?.toString().orEmpty(),
                    heightText = drafts.height ?: settings.heightCm?.let { heightText(it, heightUnit) }.orEmpty(),
                    heightUnit = heightUnit,
                    sex = settings.bodySex,
                    weightMissing = BodyDetail.Weight in choices.missing,
                )
            } else {
                null
            },
            activitySource = activitySource,
            activityInBurned = settings.activityInBurned,
            everyday = settings.everydayActivity.takeIf { exerciseReleased },
        )
    }

    /** Centimetres from what was typed in [unit]; null when it is not a number. */
    fun heightCm(text: String, unit: HeightUnit): Double? {
        val value = text.toDoubleOrNull() ?: return null
        return if (unit == HeightUnit.In) value * CM_PER_INCH else value
    }

    private fun heightText(cm: Double, unit: HeightUnit): String =
        (if (unit == HeightUnit.In) cm / CM_PER_INCH else cm).roundToInt().toString()

    private fun Int?.bare(policy: VisibilityPolicy): Display =
        this?.let { policy.resolve(Metric.EnergyOut, it.toDouble(), Surface.Settings, FormatContext(bare = true)) } ?: Display.Hidden
}
