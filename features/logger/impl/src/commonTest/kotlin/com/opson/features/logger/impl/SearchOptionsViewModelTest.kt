package com.opson.features.logger.impl

import com.opson.libraries.diary.SearchDatabase
import com.opson.libraries.diary.SearchSort
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [SearchOptionsViewModel]: every pick saves a setting at once, says so in telemetry, and Done closes. */
class SearchOptionsViewModelTest : CoroutineTest() {
    private val settings = FakeSettingsRepository(Settings(searchSort = SearchSort.MostRecent))
    private val telemetry = RecordingSearchOptionsTelemetry()

    private fun vm(openFoodFacts: Boolean = true) =
        SearchOptionsViewModel(SearchOptionsRoute(openFoodFacts), settings, telemetry)

    private fun TestScope.events(vm: SearchOptionsViewModel): List<SearchOptionsEvent> {
        val events = mutableListOf<SearchOptionsEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    @Test
    fun opensOnWhatIsSaved() = runUnitTest {
        val vm = vm()
        runCurrent()
        assertEquals(SearchSort.MostRecent, vm.state.sort)
        assertEquals(SearchDatabase.All, vm.state.database)
        assertEquals(SearchDatabase.entries, vm.state.databases)
        assertTrue(vm.state.tabs)
    }

    @Test
    fun openFoodFactsIsOfferedOnlyWhenTheServerSearchesIt() = runUnitTest {
        settings.state.value = Settings(searchDatabase = SearchDatabase.OpenFoodFacts)
        val vm = vm(openFoodFacts = false)
        runCurrent()
        assertEquals(listOf(SearchDatabase.All, SearchDatabase.Usda, SearchDatabase.MyFoods), vm.state.databases)
        assertEquals(SearchDatabase.All, vm.state.database, "a stored choice of it reads as All")

        vm.takeAction(SearchOptionsAction.DatabasePicked(SearchDatabase.OpenFoodFacts))
        runCurrent()
        assertEquals(SearchDatabase.OpenFoodFacts, settings.state.value.searchDatabase, "nothing written")
        assertTrue(telemetry.events.isEmpty())
    }

    @Test
    fun aSortPickSavesAndIsCounted() = runUnitTest {
        val vm = vm()
        vm.takeAction(SearchOptionsAction.SortPicked(SearchSort.AToZ))
        runCurrent()
        assertEquals(SearchSort.AToZ, settings.state.value.searchSort)
        assertEquals(SearchSort.AToZ, vm.state.sort)

        vm.takeAction(SearchOptionsAction.SortPicked(SearchSort.AToZ))
        runCurrent()
        assertEquals(listOf("sort:a_to_z"), telemetry.events, "picking what is already picked is not a change")
    }

    @Test
    fun aDatabasePickSavesAndIsCounted() = runUnitTest {
        val vm = vm()
        vm.takeAction(SearchOptionsAction.DatabasePicked(SearchDatabase.MyFoods))
        runCurrent()
        assertEquals(SearchDatabase.MyFoods, settings.state.value.searchDatabase)
        assertEquals(listOf("database:my_foods"), telemetry.events)
    }

    @Test
    fun theTabsSwitchSavesAndIsCounted() = runUnitTest {
        val vm = vm()
        vm.takeAction(SearchOptionsAction.TabsToggled(false))
        runCurrent()
        assertEquals(false, settings.state.value.searchTabs)
        assertEquals(false, vm.state.tabs)
        assertEquals(listOf("tabs:false"), telemetry.events)
    }

    @Test
    fun doneCloses() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        vm.takeAction(SearchOptionsAction.DoneTapped)
        runCurrent()
        assertEquals(listOf<SearchOptionsEvent>(SearchOptionsEvent.Dismiss), events)
    }
}
