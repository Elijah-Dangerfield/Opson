package com.opson.features.settings

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The release flag for two more goal styles on Targets: one number and a set surplus, in the goal
 * style picker and as a day group's shape under "different by day". Off, the picker offers what
 * 0.1 did. A style already saved still shows as it is, so a phone with the flag off never
 * misreads a goal set on one with it on. Read when Targets opens; nothing runs at launch.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class GoalStylesEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "More goal styles"

    // A literal on purpose: ConfigManifestRegistryDriftTest reads declared paths from source.
    override val path = "release.goalStyles"
    override val default = false
    override val description = "Targets: one number and a set surplus, as goal styles and day group shapes"
}
