package com.opson.libraries.diary

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The summary card's macros page, stats row, collapse and sticky bar, long-press to customise,
 * the stats on More › Today screen, and the Weight row. Here rather than in Today so More › Today
 * screen, which arranges all of it, can read it too.
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
