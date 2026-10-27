package com.opson.libraries.health

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The release flag for goals and energy (design sync 4, 13.2): one number, a set deficit and a
 * set surplus on Targets, as goal styles and as day group shapes; Burned, its Resting energy page
 * and Energy today. Off, Targets offers a range and different by day, and nothing reads Burned,
 * so a deficit would only ever be the range. A style already saved still shows as it is, so a
 * phone with the flag off never misreads a goal set on one with it on. Read when a page opens;
 * nothing runs at launch.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class GoalsAndEnergyEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Goals and energy"

    // A literal on purpose: ConfigManifestRegistryDriftTest reads declared paths from source.
    override val path = "release.goalsAndEnergy"
    override val default = false
    override val description = "Targets: one number, a set deficit and surplus; Burned, Resting energy and Energy today"
}
