package com.opson.features.settings.impl.energy

import com.opson.libraries.diary.Settings
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.RestingChoices
import com.opson.libraries.nutrition.BodyDetail
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.HeightInput
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.RestingSource
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy

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
    /** "Count digesting food": about a tenth of what is eaten added to Burned. */
    val thermicEffectInBurned: Boolean = false,
    /** Everyday activity's pick, the same setting Targets and onboarding edit. */
    val everyday: EverydayActivity = EverydayActivity.Default,
)

/**
 * The calculation's inputs as fields: typed text, so a half-typed year is not lost. [height] is
 * in the unit the person picked, or else [Settings.heightUnitOr]'s. [weightMissing] says to log
 * a weight, which lives in Trends rather than here.
 */
data class BodyDetails(
    val birthYearText: String,
    val height: HeightInput,
    val sex: BodySex?,
    val weightMissing: Boolean,
)

/**
 * What a typed field is still in the middle of, kept by the view model between settings updates.
 * While a detail is being typed the fields stay up, even once nothing is missing: five feet is a
 * whole height before the inches are typed, and the inches field must not vanish under them.
 */
data class RestingDrafts(val own: String? = null, val birthYear: String? = null, val height: HeightInput? = null) {
    val editingDetails: Boolean get() = birthYear != null || height != null
}

object RestingEnergyStateBuilder {

    fun build(
        settings: Settings,
        choices: RestingChoices,
        policy: VisibilityPolicy,
        drafts: RestingDrafts = RestingDrafts(),
        activitySource: HealthSource? = null,
        regionHeight: HeightUnit = HeightUnit.Cm,
    ): RestingEnergyState {
        val calculating = settings.restingSource == RestingSource.Calculate
        return RestingEnergyState(
            loaded = true,
            source = settings.restingSource,
            calculated = choices.calculatedKcal.bare(policy),
            health = choices.healthSource,
            healthKcal = choices.healthKcal.bare(policy),
            ownText = (drafts.own ?: settings.restingOwnKcal?.toString().orEmpty())
                .takeIf { policy.effectiveLevel(Metric.EnergyOut, Surface.Settings) == Visibility.Show },
            details = if (calculating && (choices.missing.isNotEmpty() || drafts.editingDetails)) {
                BodyDetails(
                    birthYearText = drafts.birthYear ?: settings.birthYear?.toString().orEmpty(),
                    height = height(settings, drafts, regionHeight),
                    sex = settings.bodySex,
                    weightMissing = BodyDetail.Weight in choices.missing,
                )
            } else {
                null
            },
            activitySource = activitySource,
            activityInBurned = settings.activityInBurned,
            thermicEffectInBurned = settings.thermicEffectInBurned,
            everyday = settings.everydayActivity,
        )
    }

    /** The height fields: what is being typed, or else the stored height in the unit to show. */
    fun height(settings: Settings, drafts: RestingDrafts, regionHeight: HeightUnit): HeightInput =
        drafts.height ?: HeightInput.of(settings.heightCm, settings.heightUnitOr(regionHeight))

    private fun Int?.bare(policy: VisibilityPolicy): Display =
        this?.let { policy.resolve(Metric.EnergyOut, it.toDouble(), Surface.Settings, FormatContext(bare = true)) } ?: Display.Hidden
}
