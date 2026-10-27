package com.opson.libraries.diary

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The daily check-in (design sync 2, 4.1): the card on Close the day, its switch in Reminders and
 * the hunger it feeds on Patterns. Here rather than in Today so those pages can read it. Read
 * when one of them builds its inputs; nothing runs at launch.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class CheckInEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Day close check-in"
    override val path = "release.checkIn"
    override val default = false
    override val description = "The once-a-day check-in on Close the day, its Reminders switch and Patterns' hunger from it"
}
