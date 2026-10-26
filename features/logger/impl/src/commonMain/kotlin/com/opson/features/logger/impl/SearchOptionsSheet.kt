package com.opson.features.logger.impl

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.SearchDatabase
import com.opson.libraries.diary.SearchSort
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.navigation.Route
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

/**
 * The search options sheet, over the Logger. Opened only by the options
 * button, which is behind `release.searchOptions`. [openFoodFacts] is whether
 * the server offers Open Food Facts as a database, as the Logger last heard.
 */
@Serializable
data class SearchOptionsRoute(val openFoodFacts: Boolean = false) : Route()

/** What the sheet draws. No numbers anywhere, so nothing here goes through the policy. */
data class SearchOptionsState(
    val sort: SearchSort = SearchSort.BestMatch,
    val database: SearchDatabase = SearchDatabase.All,
    val databases: List<SearchDatabase> = listOf(SearchDatabase.All, SearchDatabase.Usda, SearchDatabase.MyFoods),
    val tabs: Boolean = true,
) {
    companion object {
        /** Open Food Facts is offered only while the server searches it; a stored choice of it reads as All. */
        fun of(settings: Settings, openFoodFacts: Boolean): SearchOptionsState = SearchOptionsState(
            sort = settings.searchSort,
            database = settings.effectiveDatabase(openFoodFacts),
            databases = SearchDatabase.entries.filter { it != SearchDatabase.OpenFoodFacts || openFoodFacts },
            tabs = settings.searchTabs,
        )
    }
}

sealed interface SearchOptionsAction {
    data class SettingsArrived(val settings: Settings) : SearchOptionsAction
    data class SortPicked(val sort: SearchSort) : SearchOptionsAction
    data class DatabasePicked(val database: SearchDatabase) : SearchOptionsAction
    data class TabsToggled(val on: Boolean) : SearchOptionsAction
    data object DoneTapped : SearchOptionsAction
}

sealed interface SearchOptionsEvent {
    data object Dismiss : SearchOptionsEvent
}

/**
 * Each pick is saved at once, as a setting (top-level keys, so they sync), and
 * the Logger underneath re-searches when the settings it reads change. Done
 * only closes the sheet.
 */
@Inject
class SearchOptionsViewModel(
    @Assisted route: SearchOptionsRoute,
    private val settings: SettingsRepository,
    private val telemetry: SearchOptionsTelemetry,
) : SEAViewModel<SearchOptionsState, SearchOptionsEvent, SearchOptionsAction>(
    initialStateArg = SearchOptionsState.of(settings.settings.value, route.openFoodFacts),
) {
    private val openFoodFacts = route.openFoodFacts

    init {
        viewModelScope.launch { settings.settings.collect { takeAction(SearchOptionsAction.SettingsArrived(it)) } }
    }

    override suspend fun handleAction(action: SearchOptionsAction) {
        when (action) {
            is SearchOptionsAction.SettingsArrived -> action.updateState { SearchOptionsState.of(action.settings, openFoodFacts) }
            is SearchOptionsAction.SortPicked -> if (action.sort != state.sort) {
                telemetry.sortPicked(action.sort)
                action.save { it.copy(searchSort = action.sort) }
            }
            is SearchOptionsAction.DatabasePicked -> if (action.database != state.database && action.database in state.databases) {
                telemetry.databasePicked(action.database)
                action.save { it.copy(searchDatabase = action.database) }
            }
            is SearchOptionsAction.TabsToggled -> if (action.on != state.tabs) {
                telemetry.tabsToggled(action.on)
                action.save { it.copy(searchTabs = action.on) }
            }
            SearchOptionsAction.DoneTapped -> sendEvent(SearchOptionsEvent.Dismiss)
        }
    }

    private suspend fun SearchOptionsAction.save(change: (Settings) -> Settings) {
        val saved = settings.update(stampNumbersChange = false, transform = change)
        updateState { SearchOptionsState.of(saved, openFoodFacts) }
    }
}
