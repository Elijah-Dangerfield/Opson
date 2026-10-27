package com.opson.features.logger.impl

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.diary.SearchDatabase
import com.opson.libraries.diary.SearchSort
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/**
 * The search options' app events (`docs/practices/app-events.md`). Each takes
 * only the value picked, so a query can never ride along.
 */
interface SearchOptionsTelemetry {
    fun sortPicked(sort: SearchSort)

    fun databasePicked(database: SearchDatabase)

    fun tabsToggled(on: Boolean)

    fun tabPicked(tab: SearchTab)
}

@ContributesBinding(AppScope::class)
@Inject
class AppEventsSearchOptionsTelemetry : SearchOptionsTelemetry {
    private val logger = KLog.withTag("SearchOptions")

    override fun sortPicked(sort: SearchSort) {
        logger.logEvent("search.sort_picked", "sort" to sort.wire)
    }

    override fun databasePicked(database: SearchDatabase) {
        logger.logEvent("search.database_picked", "database" to database.wire)
    }

    override fun tabsToggled(on: Boolean) {
        logger.logEvent("search.tabs_toggled", "on" to on)
    }

    override fun tabPicked(tab: SearchTab) {
        logger.logEvent("search.tab_picked", "tab" to tab.wire)
    }
}

/** As stored in settings, so a dashboard reads the same words. */
val SearchSort.wire: String
    get() = when (this) {
        SearchSort.BestMatch -> "best_match"
        SearchSort.MostRecent -> "most_recent"
        SearchSort.AToZ -> "a_to_z"
    }

val SearchDatabase.wire: String
    get() = when (this) {
        SearchDatabase.All -> "all"
        SearchDatabase.Usda -> "usda"
        SearchDatabase.OpenFoodFacts -> "open_food_facts"
        SearchDatabase.MyFoods -> "my_foods"
    }
