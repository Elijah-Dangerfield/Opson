package com.opson.features.upgrade

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.IntConfigValue
import com.opson.libraries.config.QaConfigValue
import com.opson.libraries.config.StringConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/*
 * The levers the app guard reads. 0.1 shipped without any of them, so every
 * value here only reaches 0.2 and later; the admin console's kill-switch panel
 * says so. [AppGuardState.from] resolves them against the streamed config map
 * rather than these singletons, so a change lands on the next config refresh
 * without a restart.
 */

/** Builds below this version code see the blocking update screen. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class MinSupportedVersionCode(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Min supported version code"
    override val path = "upgrade.minSupportedVersionCode"
    override val default = 1
    override val description = "Below this build code, force upgrade"
}

/**
 * Builds below this version code get a dismissable "a new version is ready" banner. 0, the
 * default, offers nothing, so a store release that has not reached everyone yet is not announced
 * until the operator says so.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class LatestVersionCode(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Latest version code"
    override val path = "upgrade.latestVersionCode"
    override val default = 0
    override val description = "Below this build code, offer a dismissable update banner"
}

/** `off`, `banner` or `blocking`. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class MaintenanceMode(appConfigMap: AppConfigMap) : StringConfigValue(appConfigMap) {
    override val name = "Maintenance mode"
    override val path = "upgrade.maintenanceMode"
    override val default = MODE_OFF
    override val description = "Maintenance gate"
    override val allowedValues = listOf(MODE_OFF, MODE_BANNER, MODE_BLOCKING)

    companion object {
        const val MODE_OFF = "off"
        const val MODE_BANNER = "banner"
        const val MODE_BLOCKING = "blocking"
    }
}

/** What the banner and the maintenance screen say. Written by the operator, shown as is. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class MaintenanceMessage(appConfigMap: AppConfigMap) : StringConfigValue(appConfigMap) {
    override val name = "Maintenance message"
    override val path = "upgrade.maintenanceMessage"
    override val default = "We're updating the servers, back in a moment."
    override val description = "Maintenance banner/blocking copy"
}
