package com.opson.features.settings

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The release flag for Find support ([FindSupportRoute]): the page, Support's "Find support near
 * you" and crisis row, and the pause sheet's "Talk to someone". Off, nothing links to the page
 * and Support keeps its 0.1 helpline links. Other features that link to the page (onboarding,
 * the big-day card) check it too. Nothing runs at launch either way.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class FindSupportEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Find support"

    // A literal on purpose: ConfigManifestRegistryDriftTest reads declared paths from source.
    override val path = "release.findSupport"
    override val default = false
    override val description = "Find support: helplines by country, Support's crisis row, and the links from the pause sheet and Targets' low-goal note"
}
