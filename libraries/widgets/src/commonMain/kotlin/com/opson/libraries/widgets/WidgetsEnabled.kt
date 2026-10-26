package com.opson.libraries.widgets

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The 0.2 release flag for home screen widgets: the app writing a snapshot for them, and the
 * Widgets row on More. Off writes nothing, reloads nothing and shows no row. The 0.2 release turns
 * it on remotely.
 *
 * The refresher reads it from each `AppConfigFlow` emission ([isOn]), so a remote flip reaches the
 * widget without a restart. Screens resolve it with `invoke()` when they build.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class WidgetsEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Widgets (0.2)"

    // A literal on purpose: ConfigManifestRegistryDriftTest reads declared paths from source.
    override val path = "release.widgets"
    override val default = false
    override val description = "0.2: home screen widgets, the snapshot the app writes for them, and the More row"

    companion object {
        /** The flag in one config snapshot. */
        fun isOn(config: AppConfigMap): Boolean = WidgetsEnabled(config).value
    }
}
