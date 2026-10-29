package com.opson.libraries.diary

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** Whether repeats are on, read where it is used. */
fun interface RepeatsRelease {
    fun isOn(): Boolean
}

/**
 * Repeating foods. Off, the Foods tab has no Repeating card, Today's food sheet offers no
 * Repeat this, and nothing logs itself. Rules already saved stay saved and come back with the
 * flag. Here rather than in Foods because the diary is what does the logging. Also the kill
 * switch: a repeat writes to the person's log unasked, so it has to be stoppable remotely.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
@ContributesBinding(AppScope::class, boundType = RepeatsRelease::class)
class RepeatsReleaseFlag(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap), RepeatsRelease {
    override val name = "Repeating foods"
    override val path = "release.repeats"
    override val default = false
    override val description = "Repeating on the Foods tab: foods and meals that log themselves on the days picked"

    override fun isOn(): Boolean = value
}
