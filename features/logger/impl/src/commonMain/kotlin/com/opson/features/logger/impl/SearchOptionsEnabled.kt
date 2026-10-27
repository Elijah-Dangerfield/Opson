package com.opson.features.logger.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The 0.2 release flag for the Logger's search options
 * (docs/plans/screens/search-options.md). Off, the search bar has no options
 * button, there are no category tabs or source badges, and search sends no
 * new parameters, exactly as 0.1 shipped. Read when the Logger opens; nothing
 * runs at launch either way.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class SearchOptionsEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Search options"
    override val path = "release.searchOptions"
    override val default = false
    override val description = "0.2: the Logger's search options sheet, category tabs and source badges"
}
