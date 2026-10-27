package com.opson.libraries.reminders

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The 0.2 release flag for reminders: the Reminders and celebrations page, its More row, the
 * Water reminders row, and every notification. Off renders none of
 * them and schedules nothing; the coordinator also cancels what it scheduled before. The 0.2
 * release turns it on remotely.
 *
 * Screens resolve it with `invoke()` when they build. The coordinator reads it from each
 * `AppConfigFlow` emission ([isOn]), so a remote flip reaches the schedule without a restart.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class RemindersEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Reminders (0.2)"
    // A literal on purpose: ConfigManifestRegistryDriftTest reads declared paths from source.
    override val path = "release.reminders"
    override val default = false

    companion object {
        /** The flag in one config snapshot. */
        fun isOn(config: AppConfigMap): Boolean = RemindersEnabled(config).value
    }
}
