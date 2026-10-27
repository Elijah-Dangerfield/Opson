package com.opson.features.onboarding.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The release flag for onboarding's setup steps after the starting mode (docs/plans/onboarding-v2.md):
 * the goal in Full numbers, resting energy details for a set deficit or surplus, and meal times
 * for No numbers and for someone eating regularly. Off, onboarding goes from the starting mode
 * to the account step, as 0.1 does. Read when onboarding opens; nothing runs at launch.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class OnboardingSetupEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Onboarding setup steps"

    // A literal on purpose: ConfigManifestRegistryDriftTest reads declared paths from source.
    override val path = "release.onboardingSetup"
    override val default = false
    override val description = "Onboarding: the goal, resting energy details and meal times after the starting mode"
}
