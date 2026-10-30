package com.opson.libraries.diary.impl

import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LoggedFood
import com.opson.libraries.diary.LoggedFoods
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * [LoggedFoods] from the newest [SCAN] food entries: enough for the long tail of anyone who logs
 * a few foods a day for a year or more, and a bounded read however long the diary gets.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class LoggedFoodsImpl(
    private val tables: DiaryTables,
    private val settingsRepository: SettingsRepository,
) : LoggedFoods {

    override fun observe(limit: Int): Flow<List<LoggedFood>> =
        combine(
            tables.entries.observeLatestFoods(SCAN),
            settingsRepository.settings.distinctUntilChangedBy { it.hiddenRecentFoods },
        ) { rows, settings -> group(rows.mapNotNull { it.toDomain() }, settings, limit) }

    internal companion object {
        const val SCAN = 5_000

        /** [items] newest first, as distinct foods with their count, keeping the newest entry of each. */
        fun group(items: List<LogItem>, settings: Settings, limit: Int): List<LoggedFood> {
            val byKey = LinkedHashMap<String, MutableList<LogItem>>()
            for (item in items) byKey.getOrPut(item.foodKey()) { mutableListOf() } += item
            return byKey.asSequence()
                .map { (key, entries) -> key to LoggedFood(latest = entries.first(), times = entries.size) }
                .filterNot { (key, food) -> settings.hidesFromRecent(key, food.lastLoggedAt.toEpochMilliseconds()) }
                .map { it.second }
                .take(limit)
                .toList()
        }
    }
}
