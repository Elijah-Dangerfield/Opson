package com.opson.features.settings.impl.energy

import com.opson.libraries.diary.Settings
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.RestingChoices
import com.opson.libraries.nutrition.BodyDetail
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.RestingSource
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.LocalDate

/**
 * Resting energy (design sync 4, 13.2d): where Burned's resting energy comes from, a link to the
 * Profile the calculation reads, and whether activity counts. Every amount is energy out,
 * resolved on [Surface.Settings]; a level that hides it leaves the choices without numbers.
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
    /**
     * The Your profile row under "Calculate it for me", with what the calculation still needs
     * (empty when nothing). Null while another source is picked. The details are edited on
     * Profile, never here, so there is one place to change them.
     */
    val profileMissing: Set<BodyDetail>? = null,
    /**
     * What the calculation is given, once nothing is missing: "Male, 28, 5 ft 11 in, 170 lb" under
     * Your profile, so a detail in the wrong unit is easy to spot. Null with another source picked.
     */
    val profileInputs: ProfileInputs? = null,
    /** The connected store for "Steps and workouts", null when none. */
    val activitySource: HealthSource? = null,
    val activityInBurned: Boolean = true,
    /** "Count digesting food": about a tenth of what is eaten added to Burned. */
    val thermicEffectInBurned: Boolean = false,
    /** Everyday activity's pick, the same setting Targets and onboarding edit. */
    val everyday: EverydayActivity = EverydayActivity.Default,
) {
    /**
     * Whether to ask for everyday activity. Not while a health app counts the day's activity: then
     * Burned takes its movement from there and leaves everyday activity out (`burnedFor`), so the
     * pick would change nothing. With activity switched off the baseline counts again, so it is asked.
     */
    val asksEveryday: Boolean get() = activitySource == null || !activityInBurned
}

/** What "Enter my own" is in the middle of, kept by the view model between settings updates. */
data class RestingDrafts(val own: String? = null)

object RestingEnergyStateBuilder {

    fun build(
        settings: Settings,
        choices: RestingChoices,
        policy: VisibilityPolicy,
        drafts: RestingDrafts = RestingDrafts(),
        activitySource: HealthSource? = null,
        today: LocalDate,
        regionHeight: HeightUnit = HeightUnit.Cm,
    ): RestingEnergyState = RestingEnergyState(
        loaded = true,
        source = settings.restingSource,
        calculated = choices.calculatedKcal.bare(policy),
        health = choices.healthSource,
        healthKcal = choices.healthKcal.bare(policy),
        ownText = (drafts.own ?: settings.restingOwnKcal?.toString().orEmpty())
            .takeIf { policy.effectiveLevel(Metric.EnergyOut, Surface.Settings) == Visibility.Show },
        profileMissing = choices.missing.takeIf { settings.restingSource == RestingSource.Calculate },
        profileInputs = ProfileInputsBuilder.build(settings, choices.weightKg, today, policy, regionHeight)
            .takeIf { settings.restingSource == RestingSource.Calculate },
        activitySource = activitySource,
        activityInBurned = settings.activityInBurned,
        thermicEffectInBurned = settings.thermicEffectInBurned,
        everyday = settings.everydayActivity,
    )

    private fun Int?.bare(policy: VisibilityPolicy): Display =
        this?.let { policy.resolve(Metric.EnergyOut, it.toDouble(), Surface.Settings, FormatContext(bare = true)) } ?: Display.Hidden
}
