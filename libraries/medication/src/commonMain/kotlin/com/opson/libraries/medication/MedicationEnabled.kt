package com.opson.libraries.medication

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The release flag for medication tracking (`docs/plans/feedback-2026-10-29/glp1-research.md`).
 * Off means nothing about medication renders or runs: no More row, no Medication page, no Today
 * card, no dose reminder, no weight ticks and no provider summary section. What was logged stays
 * on the phone either way.
 *
 * Screens read [value] when they build. The reminder planner reads it from each config emission
 * ([isOn]), so a remote flip reaches the schedule without a restart.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class MedicationEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Medication"

    // A literal on purpose: ConfigManifestRegistryDriftTest reads declared paths from source.
    override val path = "release.medication"
    override val default = false
    override val description = "Medication tracking: More › Medication, the dose sheet, the Today card, the dose reminder, weight ticks and the provider summary section"

    companion object {
        /** The flag in one config snapshot. */
        fun isOn(config: AppConfigMap): Boolean = MedicationEnabled(config).value
    }
}
