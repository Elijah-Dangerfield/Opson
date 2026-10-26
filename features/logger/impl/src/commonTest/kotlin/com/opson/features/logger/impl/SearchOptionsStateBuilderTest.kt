package com.opson.features.logger.impl

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.SearchDatabase
import com.opson.libraries.diary.SearchSort
import com.opson.libraries.diary.Settings
import com.opson.libraries.foods.FoodSearchFilter
import com.opson.libraries.foods.SearchCategory
import com.opson.libraries.foods.SearchOrder
import com.opson.libraries.foods.SearchSource
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.PresetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The search options in [LoggerStateBuilder] (`release.searchOptions`): tabs, sorts, the
 * database filter, badges, and that with the flag off nothing of it shows. Rows still go
 * through the policy, so Blind is checked too.
 */
class SearchOptionsStateBuilderTest {
    private val full = policy(PresetId.Full)
    private val blind = policy(PresetId.Blind)

    private fun results(state: LoggerState) = assertIs<LoggerBody.Results>(state.body).rows

    private fun choices(
        tab: SearchTab = SearchTab.All,
        query: String = "rice",
        openFoodFacts: Boolean = true,
        on: Boolean = true,
    ) = LoggerChoices(slot = MealSlot.Dinner, query = query, searchOptions = on, tab = tab, openFoodFacts = openFoodFacts)

    @Test
    fun withTheFlagOffThereAreNoOptionsNoBadgesAndNoFilter() {
        val state = LoggerFixtures.searchState(full)
        assertNull(state.options)
        assertTrue(results(state).all { it.badge == null && !it.verified })
        assertEquals(FoodSearchFilter.None, LoggerStateBuilder.catalogFilter(LoggerFixtures.optionsInputs(), choices(on = false)))
    }

    @Test
    fun everyRowNamesItsSource() {
        val rows = results(LoggerFixtures.optionsState(full)).associateBy { it.key }
        assertEquals(SourceBadge.YourFood, rows.getValue("c:rice-bowl").badge)
        assertFalse(rows.getValue("c:rice-bowl").verified, "your own food is yours, not verified")
        assertEquals(SourceBadge.Usda to true, rows.getValue("f:rice").badge to rows.getValue("f:rice").verified)
        assertEquals(SourceBadge.UsdaBranded to true, rows.getValue("f:rice-branded").let { it.badge to it.verified })
        assertEquals(SourceBadge.OpenFoodFacts to false, rows.getValue("f:rice-off").let { it.badge to it.verified })
    }

    @Test
    fun openFoodFactsRowsBringTheLicenceLine() {
        assertTrue(assertIs<LoggerBody.Results>(LoggerFixtures.optionsState(full).body).openFoodFacts)
        val usdaOnly = LoggerFixtures.optionsState(full, settings = Settings(searchDatabase = SearchDatabase.Usda))
        assertFalse(assertIs<LoggerBody.Results>(usdaOnly.body).openFoodFacts)
    }

    @Test
    fun rowsFromTheDiaryFindTheirSourceInWhatTheDeviceKnows() {
        val browse = assertIs<LoggerBody.Browse>(LoggerFixtures.optionsState(full, query = "").body)
        val recent = browse.recent.associateBy { it.key }
        assertEquals(SourceBadge.Usda, recent.getValue("f:banana").badge, "banana is cached, and FNDDS")
        assertNull(recent.getValue("f:poutine").badge, "a food the device doesn't hold gets no badge rather than a guess")
    }

    @Test
    fun blindHidesTheNumbersButKeepsTheBadges() {
        val rows = results(LoggerFixtures.optionsState(blind))
        assertTrue(rows.all { it.calories == Display.Hidden && it.macros.isEmpty() })
        assertTrue(rows.all { it.badge != null })
    }

    @Test
    fun tabsFollowTheSettingAndAllIsFirst() {
        val on = LoggerFixtures.optionsState(full)
        assertEquals(SearchTab.entries, on.options?.tabs)
        assertEquals(SearchTab.All, on.options?.tab)

        val off = LoggerFixtures.optionsState(full, tab = SearchTab.Favorites, settings = Settings(searchTabs = false))
        assertNull(off.options?.tabs)
        assertEquals(SearchTab.All, off.options?.tab, "with the tabs off a picked tab no longer applies")
        assertIs<LoggerBody.Results>(off.body)
    }

    @Test
    fun theMethodChipsStayOnAll() {
        assertTrue(LoggerFixtures.optionsState(full, query = "").methods.isNotEmpty())
        assertTrue(LoggerFixtures.optionsState(full, tab = SearchTab.Favorites, query = "").methods.isEmpty())
    }

    @Test
    fun favoritesListWhatIsLoggedMostAndFilterByName() {
        val listed = assertIs<LoggerBody.Listed>(LoggerFixtures.optionsState(full, tab = SearchTab.Favorites, query = "").body)
        assertEquals(listOf("f:arroz", "f:banana", "f:yogurt"), listed.rows.map { it.key })
        assertTrue(listed.rows.all { it.source == PickSource.Favorite })

        val typed = results(LoggerFixtures.optionsState(full, tab = SearchTab.Favorites, query = "ban"))
        assertEquals(listOf("f:banana"), typed.map { it.key })
    }

    @Test
    fun anEmptyFavoritesOrCustomTabSaysWhatGoesThere() {
        val inputs = LoggerFixtures.optionsInputs().copy(favorites = emptyList(), customFoods = emptyList())
        assertEquals(
            LoggerBody.TabEmpty(SearchTab.Favorites),
            LoggerStateBuilder.build(inputs, full, choices(tab = SearchTab.Favorites, query = "")).body,
        )
        assertEquals(
            LoggerBody.TabEmpty(SearchTab.Custom),
            LoggerStateBuilder.build(inputs, full, choices(tab = SearchTab.Custom, query = "")).body,
        )
    }

    @Test
    fun customListsYourFoodsWithNoServerCall() {
        val listed = assertIs<LoggerBody.Listed>(LoggerFixtures.optionsState(full, tab = SearchTab.Custom, query = "").body)
        assertEquals(listOf("c:granola", "c:rice-bowl"), listed.rows.map { it.key })
        assertNull(LoggerStateBuilder.catalogFilter(LoggerFixtures.optionsInputs(), choices(tab = SearchTab.Custom)))
    }

    @Test
    fun catalogTabsAskForTheirCategoryAndNeedWordsFirst() {
        assertEquals(
            LoggerBody.TabHint(SearchTab.Restaurant),
            LoggerFixtures.optionsState(full, tab = SearchTab.Restaurant, query = "").body,
        )
        val inputs = LoggerFixtures.optionsInputs()
        assertEquals(SearchCategory.Restaurant, LoggerStateBuilder.catalogFilter(inputs, choices(tab = SearchTab.Restaurant))?.category)
        assertEquals(SearchCategory.Beverage, LoggerStateBuilder.catalogFilter(inputs, choices(tab = SearchTab.Beverages))?.category)
        assertEquals(SearchCategory.Common, LoggerStateBuilder.catalogFilter(inputs, choices(tab = SearchTab.Common))?.category)

        val common = results(LoggerFixtures.optionsState(full, tab = SearchTab.Common))
        assertTrue(common.none { it.badge == SourceBadge.YourFood }, "a catalog tab is the catalog's foods only")
    }

    @Test
    fun theDatabaseNarrowsAll() {
        fun filter(settings: Settings, openFoodFacts: Boolean = true) =
            LoggerStateBuilder.catalogFilter(LoggerFixtures.optionsInputs(settings), choices(openFoodFacts = openFoodFacts))

        assertEquals(FoodSearchFilter(), filter(Settings()))
        assertEquals(FoodSearchFilter(source = SearchSource.Usda), filter(Settings(searchDatabase = SearchDatabase.Usda)))
        assertEquals(
            FoodSearchFilter(source = SearchSource.OpenFoodFacts),
            filter(Settings(searchDatabase = SearchDatabase.OpenFoodFacts)),
        )
        assertEquals(
            FoodSearchFilter(),
            filter(Settings(searchDatabase = SearchDatabase.OpenFoodFacts), openFoodFacts = false),
            "Open Food Facts the server no longer offers reads as All",
        )
        assertNull(filter(Settings(searchDatabase = SearchDatabase.MyFoods)))

        val usda = results(LoggerFixtures.optionsState(full, settings = Settings(searchDatabase = SearchDatabase.Usda)))
        assertTrue(usda.none { it.badge == SourceBadge.YourFood }, "USDA means USDA, not your foods too")
    }

    @Test
    fun myFoodsIsYourFoodsAndWhatYouLogged() {
        val settings = Settings(searchDatabase = SearchDatabase.MyFoods)
        val listed = assertIs<LoggerBody.Listed>(LoggerFixtures.optionsState(full, query = "", settings = settings).body)
        assertEquals("c:granola", listed.rows.first().key)
        assertTrue("f:sinangag" in listed.rows.map { it.key })

        val typed = results(LoggerFixtures.optionsState(full, query = "rice", settings = settings))
        assertEquals(setOf("c:rice-bowl", "f:sinangag"), typed.map { it.key }.toSet())
    }

    @Test
    fun mostRecentPutsWhatYouLoggedFirst() {
        val settings = Settings(searchSort = SearchSort.MostRecent)
        val browse = assertIs<LoggerBody.Browse>(LoggerFixtures.optionsState(full, query = "", settings = settings).body)
        assertTrue(browse.usual.isEmpty())
        assertEquals(listOf("f:sinangag", "f:banana", "f:arroz", "f:yogurt", "f:kabsa"), browse.recent.map { it.key })

        val typed = results(LoggerFixtures.optionsState(full, settings = settings))
        assertEquals("f:sinangag", typed.first().key, "garlic fried rice was logged an hour ago")
        assertEquals(PickSource.Recent, typed.first().source)
    }

    @Test
    fun aToZOrdersByNameAndAsksTheServerToo() {
        val settings = Settings(searchSort = SearchSort.AToZ)
        val names = results(LoggerFixtures.optionsState(full, settings = settings)).map { it.name }
        assertEquals(names.sortedBy { it.lowercase() }, names)
        assertEquals(
            SearchOrder.Name,
            LoggerStateBuilder.catalogFilter(LoggerFixtures.optionsInputs(settings), choices())?.order,
        )
    }

    @Test
    fun theButtonLightsUpWhenTheSortOrDatabaseIsNotTheDefault() {
        assertEquals(false, LoggerFixtures.optionsState(full).options?.active)
        assertEquals(true, LoggerFixtures.optionsState(full, settings = Settings(searchSort = SearchSort.AToZ)).options?.active)
        assertEquals(
            true,
            LoggerFixtures.optionsState(full, settings = Settings(searchDatabase = SearchDatabase.Usda)).options?.active,
        )
        assertEquals(
            false,
            LoggerFixtures.optionsState(full, settings = Settings(searchDatabase = SearchDatabase.OpenFoodFacts), openFoodFacts = false)
                .options?.active,
        )
    }

    @Test
    fun anAnswerForAnotherFilterIsStale() {
        val inputs = LoggerFixtures.optionsInputs()
        val stale = SearchOutcome("rice", listOf(LoggerFixtures.usdaRice), offline = false, filter = FoodSearchFilter(source = SearchSource.Usda))
        assertEquals(LoggerBody.Searching, LoggerStateBuilder.build(inputs, full, choices().copy(search = stale)).body)
    }

    @Test
    fun aTappedRowIsFoundOnEveryTab() {
        val inputs = LoggerFixtures.optionsInputs()
        assertEquals(
            listOf("f:arroz", "f:banana", "f:yogurt"),
            LoggerStateBuilder.picks(inputs, choices(tab = SearchTab.Favorites, query = "")).map { it.key },
        )
        assertTrue(LoggerStateBuilder.picks(inputs, choices(tab = SearchTab.Restaurant, query = "")).isEmpty())
    }
}
