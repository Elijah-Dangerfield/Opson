package com.opson.libraries.widgets

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The 0.2 release flag for the Widgets row on More. Off shows no row. The 0.2 release turns it on
 * remotely.
 *
 * It does not gate the snapshot the app writes for the widget. The iOS extension ships in every
 * build, so it is in the widget gallery whatever this says, and a placed widget the app never
 * wrote for would say "Open Opson" for good. Screens resolve it with `invoke()` when they build.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class WidgetsEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Widgets (0.2)"

    // A literal on purpose: ConfigManifestRegistryDriftTest reads declared paths from source.
    override val path = "release.widgets"
    override val default = false
    override val description = "0.2: the Widgets row on More. The snapshot for the widget is written whatever this says"

    companion object {
        /** The flag in one config snapshot. */
        fun isOn(config: AppConfigMap): Boolean = WidgetsEnabled(config).value
    }
}
