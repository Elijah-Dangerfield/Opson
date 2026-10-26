package com.opson.features.today.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/*
 * The design sync 2 release flags for Today and Day close (docs/plans/screens/today.md, "Flags").
 * Off, each screen is the refined design with none of the new behaviour. All three are read when
 * Today or Day close builds its inputs; nothing runs at launch.
 */

/**
 * The summary card's macros page, stats row, collapse and sticky bar, long-press to customise,
 * the stats in Edit Today's cards, and the Weight row.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class TodaySummaryEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Today summary card"
    override val path = "release.todaySummary"
    override val default = false
    override val description = "Today's summary pages, stats, collapse and sticky bar, and the weight row"
}

/** Long-press to select foods with the select panel, swipe to delete, and Move to… on Uncategorized. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class TodaySelectEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Select and swipe on Today"
    override val path = "release.todaySelect"
    override val default = false
    override val description = "Select foods on Today, swipe a food to delete it, and file Uncategorized foods"
}

/** Day close's daily check-in: hunger through the day, energy, sleep and moods. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class CheckInEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Day close check-in"
    override val path = "release.checkIn"
    override val default = false
    override val description = "The once-a-day check-in on Close the day"
}
