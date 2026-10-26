package com.opson.libraries.health

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * 0.2's watch and health apps: the More row, the Integrations page and Expended so far on
 * Today. Off means none of it renders and no health store is touched, exactly like 0.1. The
 * 0.2 release turns it on remotely.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class HealthReleaseFlag(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Watch and health apps"
    override val path = "release.health"
    override val default = false
}
