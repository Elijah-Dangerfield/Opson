package com.opson.libraries.diary

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The daily check-in: its sheet (hunger, energy, mood, sleep in the morning), the way in from the
 * + sheet and from Close the day, and what Patterns charts from it. Here rather than in Today so
 * those pages can read it. Read when one of them builds its inputs; nothing runs at launch.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class CheckInEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Check-in"
    override val path = "release.checkIn"
    override val default = false
    override val description = "The check-in sheet, its ways in from the + sheet and Close the day, and Patterns' charts from it"
}
