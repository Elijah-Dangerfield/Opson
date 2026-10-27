package com.opson.libraries.health

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** Whether exercise logged by hand counts, read where it is used. */
fun interface ExerciseRelease {
    fun isOn(): Boolean
}

/**
 * Exercise logged by hand (docs/research/energy-expenditure.md): the add sheet's Exercise, the
 * exercise sheet, Today's Burned section, and logged exercise counted in Burned. It builds on
 * Burned, so it is only on with `release.goalsAndEnergy` on too. Everyday activity is not behind
 * it: Burned counts that whenever no health app reads activity. Off, nothing logged by hand
 * counts. Read when something opens; nothing runs at launch.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
@ContributesBinding(AppScope::class, boundType = ExerciseRelease::class)
class ExerciseReleaseFlag(
    appConfigMap: AppConfigMap,
    private val goalsAndEnergy: GoalsAndEnergyEnabled,
) : FlagConfigValue(appConfigMap), ExerciseRelease {
    override val name = "Exercise"

    // A literal on purpose: ConfigManifestRegistryDriftTest reads declared paths from source.
    override val path = "release.exercise"
    override val default = false
    override val description = "Exercise logged by hand, counted in Burned; needs goalsAndEnergy"

    override fun isOn(): Boolean = value && goalsAndEnergy()
}
