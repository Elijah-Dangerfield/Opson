package com.opson.libraries.diary

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * `release.recaps`: the weekly and monthly recaps, the way into them from Trends, "Your week is
 * ready" on Today, and its switch in More › Today screen. Here rather than in Trends so More can
 * read it. Read when a page builds its inputs; nothing runs at launch.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
@ContributesBinding(AppScope::class, boundType = RecapsRelease::class)
class RecapsEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap), RecapsRelease {
    override val name = "Recaps"

    // A literal on purpose: ConfigManifestRegistryDriftTest reads declared paths from source.
    override val path = "release.recaps"
    override val default = false
    override val description = "Weekly and monthly recaps, their way in from Trends, the recap banner on Today and its switch in More"

    override fun isOn(): Boolean = value
}

/** [RecapsEnabled] as a page reads it, so a view model can default it to [Off] and its tests stay as they are. */
fun interface RecapsRelease {
    fun isOn(): Boolean

    companion object {
        val Off: RecapsRelease = RecapsRelease { false }
    }
}
