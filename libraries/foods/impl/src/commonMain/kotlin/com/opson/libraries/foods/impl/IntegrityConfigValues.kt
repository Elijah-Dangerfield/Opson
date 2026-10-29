package com.opson.libraries.foods.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.LongConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Sends a DeviceCheck token with contribution requests on iOS. Off until the server has a
 * DeviceCheck key (`APPLE_DEVICECHECK_*`): a token the server can't read is data sent for nothing.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class DeviceCheckEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "DeviceCheck on contributions"
    override val path = "integrity.deviceCheck"
    override val default = false
}

/**
 * The Google Cloud project number linked to the app in Play Console, which Play Integrity needs
 * to issue a token. 0 sends none; set it once the server has `PLAY_INTEGRITY_SERVICE_ACCOUNT_JSON`.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class PlayIntegrityCloudProject(appConfigMap: AppConfigMap) : LongConfigValue(appConfigMap) {
    override val name = "Play Integrity cloud project number"
    override val path = "integrity.playCloudProjectNumber"
    override val default = 0L
}
