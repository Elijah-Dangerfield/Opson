package com.opson.features.settings

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The release flag for the provider summary ([ProviderSummaryRoute]): Support's "Make a summary
 * for your provider" row, first in Support for someone who said in setup they work with a
 * provider. Off, nothing links to the page. The summary is a PDF the person makes and shares
 * themselves; nothing leaves the phone otherwise, and nothing runs at launch either way.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ProviderSummaryEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Provider summary"

    // A literal on purpose: ConfigManifestRegistryDriftTest reads declared paths from source.
    override val path = "release.providerSummary"
    override val default = false
    override val description = "Support: a PDF summary for a provider, from a range and contents the person picks"
}
