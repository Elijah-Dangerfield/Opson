package com.opson.features.logger.impl

import com.opson.features.logger.FoodSearchRoute
import com.opson.libraries.core.Catching
import com.opson.libraries.core.fixed
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.SearchSort
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.FoodSearchFilter
import com.opson.libraries.foods.FoodSearchResult
import com.opson.libraries.foods.SearchCategory
import com.opson.libraries.foods.SearchOptions
import com.opson.libraries.foods.SearchOrder
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * [FoodSearchViewModel], full-screen search (design sync 4, 13.4c), one test per action: + logs
 * at once and stays, ✓ takes it back, the debounce and the client cache, the scopes, the meal,
 * picking for a meal, and the search options. The state mapping is [FoodSearchStateBuilderTest]'s.
 */
class FoodSearchViewModelTest : CoroutineTest() {
    private val diary = FakeDiaryRepository(listOf(LoggerFixtures.day())).apply {
        recent.value = listOf(LoggerFixtures.banana).map { LoggerFixtures.item(it, MealSlot.Lunch) }
    }
    private val customFoods = FakeCustomFoodRepository(listOf(LoggerFixtures.granola))
    private val catalog = FakeFoodCatalog(foods = listOf(LoggerFixtures.rice, LoggerFixtures.brownRice, LoggerFixtures.banana))
    private val settings = FakeSettingsRepository(Settings())
    private val recentLogEvents = FakeRecentLogEvents()
    private val telemetry = RecordingTelemetry()
    private val mealPicks = FakeMealPicks()
    private val optionsTelemetry = RecordingSearchOptionsTelemetry()

    private fun vm(
        route: FoodSearchRoute = FoodSearchRoute(slot = MealSlot.Dinner),
        scanOn: Boolean = false,
        photoOn: Boolean = false,
        optionsOn: Boolean = false,
    ) = FoodSearchViewModel(
        route = route,
        diary = diary,
        customFoods = customFoods,
        savedMeals = FakeSavedMealRepository(LoggerFixtures.savedMeals),
        foodCatalog = catalog,
        settingsRepository = settings,
        policySource = FakePolicySource(),
        logger = FoodLogger(diary, catalog, recentLogEvents, telemetry, Clock.fixed(NOON)),
        calendar = FakeCalendar(),
        selectedDay = FakeSelectedDay(),
        clock = Clock.fixed(NOON),
        mealPicks = mealPicks,
        scanEnabled = scanFlag(scanOn),
        submitFoodEnabled = submitFoodFlag(photoOn),
        searchOptionsEnabled = searchOptionsFlag(optionsOn),
        optionsTelemetry = optionsTelemetry,
    )

    private fun TestScope.events(vm: FoodSearchViewModel): List<SearchEvent> {
        val events = mutableListOf<SearchEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    private fun TestScope.searched(vm: FoodSearchViewModel, query: String = "rice"): List<FoodRowUi> {
        settle()
        vm.takeAction(SearchAction.SearchTyped(query))
        settle()
        return assertIs<SearchBody.Results>(vm.state.body).rows
    }

    @Test
    fun searchWaitsForTypingToPauseAndSearchesOnce() = runUnitTest {
        val vm = vm()
        settle()

        vm.takeAction(SearchAction.SearchTyped("ri"))
        advanceTimeBy(100)
        vm.takeAction(SearchAction.SearchTyped("rice"))
        assertEquals(SearchBody.Searching, vm.state.body)
        advanceTimeBy(260)
        runCurrent()

        assertEquals(listOf("rice"), catalog.searches)
        assertEquals(listOf("f:rice", "f:brown-rice"), assertIs<SearchBody.Results>(vm.state.body).rows.map { it.key })
    }

    @Test
    fun aQueryTheCatalogRemembersShowsWithoutWaitingOrAsking() = runUnitTest {
        catalog.remembered["rice"] = FoodSearchResult("rice", listOf(LoggerFixtures.rice), fromCache = false)
        val vm = vm()
        settle()

        vm.takeAction(SearchAction.SearchTyped("rice b"))
        vm.takeAction(SearchAction.SearchTyped("rice"))
        runCurrent()

        assertEquals(listOf("f:rice"), assertIs<SearchBody.Results>(vm.state.body).rows.map { it.key })
        advanceTimeBy(1_000)
        assertTrue(catalog.searches.isEmpty(), "no debounce and no round trip")
    }

    @Test
    fun anAnswerForAnOlderQueryIsDropped() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(SearchAction.SearchTyped("rice"))

        vm.takeAction(SearchAction.SearchResultsArrived(SearchOutcome("ric", listOf(LoggerFixtures.banana), offline = false)))

        assertEquals(SearchBody.Searching, vm.state.body)
    }

    @Test
    fun offlineSearchSaysSo() = runUnitTest {
        catalog.online = false
        val vm = vm()

        searched(vm)

        assertTrue(assertIs<SearchBody.Results>(vm.state.body).offline)
    }

    @Test
    fun clearingTheSearchGoesBackToBrowsing() = runUnitTest {
        val vm = vm()
        searched(vm)

        vm.takeAction(SearchAction.SearchCleared)

        assertEquals("", vm.state.query)
        assertIs<SearchBody.Browse>(vm.state.body)
    }

    @Test
    fun plusLogsTheFoodAtOnceStaysAndOffersUndo() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        val rice = searched(vm).first { it.key == "f:rice" }

        vm.takeAction(SearchAction.AddTapped(rice))
        settle()

        val added = diary.added.single()
        assertEquals(MealSlot.Dinner, added.slot)
        assertEquals(LoggerFixtures.today, added.day)
        assertEquals("Jasmine rice, cooked", added.items.single().name)
        assertEquals(LogMethod.Search, added.items.single().method)
        assertEquals(listOf(LoggerFixtures.rice), catalog.marked, "cached for the next offline search")
        assertEquals(listOf(LogVia.Search to 1), telemetry.events)
        val logged = recentLogEvents.logged.single()
        assertEquals(listOf<SearchEvent>(SearchEvent.Added("Jasmine rice", logged)), events, "and no Close: search stays")
        assertTrue(assertIs<SearchBody.Results>(vm.state.body).rows.first { it.key == "f:rice" }.added, "✓ once it is in")
        assertEquals("rice", vm.state.query, "the search is still there to add the next one")
    }

    @Test
    fun plusUsesTheLastAmountThisPersonLogged() = runUnitTest {
        diary.recent.value = listOf(LoggerFixtures.item(LoggerFixtures.rice, MealSlot.Lunch).copy(amountText = "2 cups", quantity = 2.0, unit = "cup"))
        val vm = vm()
        val rice = searched(vm).first { it.key == "f:rice" }

        vm.takeAction(SearchAction.AddTapped(rice))
        settle()

        val item = diary.added.single().items.single()
        assertEquals("2 cups", item.amountText)
        assertEquals(Precision.Estimate, item.precision, "an amount again is an estimate")
    }

    /** The second tap is queued before the day is read again, so the row has no ✓ yet to take it out. */
    @Test
    fun aDoubleTapOnPlusLogsTheFoodOnce() = runUnitTest {
        val vm = vm()
        val rice = searched(vm).first { it.key == "f:rice" }
        diary.holdWrites = true

        vm.takeAction(SearchAction.AddTapped(rice))
        vm.takeAction(SearchAction.AddTapped(rice))
        settle()
        assertEquals(1, diary.added.size)

        diary.showWrites()
        settle()
        val shown = assertIs<SearchBody.Results>(vm.state.body).rows.first { it.key == "f:rice" }
        assertTrue(shown.added)
        vm.takeAction(SearchAction.AddTapped(shown))
        settle()
        assertEquals(1, diary.removed.size, "once the day shows it, the ✓ takes it back out")
    }

    @Test
    fun theCheckTakesWhatItAddedBackOut() = runUnitTest {
        val vm = vm()
        val rice = searched(vm).first { it.key == "f:rice" }
        vm.takeAction(SearchAction.AddTapped(rice))
        settle()

        vm.takeAction(SearchAction.AddTapped(assertIs<SearchBody.Results>(vm.state.body).rows.first { it.key == "f:rice" }))
        settle()

        assertEquals(1, diary.added.size, "nothing logged twice")
        assertEquals(1, diary.removed.size)
        assertFalse(assertIs<SearchBody.Results>(vm.state.body).rows.first { it.key == "f:rice" }.added)
    }

    @Test
    fun aFoodsNameOpensItsDetailForThisMeal() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        val rice = searched(vm).first()

        vm.takeAction(SearchAction.FoodNameTapped(rice))
        settle()

        assertEquals(listOf<SearchEvent>(SearchEvent.OpenFoodDetail(FoodRef.Catalog("rice"), PickSource.Search, LoggerFixtures.today, MealSlot.Dinner)), events)
        assertTrue(diary.added.isEmpty())
    }

    @Test
    fun theMealChipRetargetsWhatPlusLogs() = runUnitTest {
        val vm = vm()
        val rice = searched(vm).first()

        vm.takeAction(SearchAction.SlotPicked(MealSlot.Lunch))
        vm.takeAction(SearchAction.AddTapped(rice))
        settle()

        assertEquals(MealSlot.Lunch, vm.state.slot)
        assertEquals(MealSlot.Lunch, diary.added.single().slot)
    }

    @Test
    fun withNoMealTheClockPicksOne() = runUnitTest {
        assertEquals(MealSlot.Lunch, vm(FoodSearchRoute()).state.slot)
    }

    @Test
    fun yourFoodsAndSavedMealsNeedNoServer() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(SearchAction.ScopePicked(SearchScope.YourFoods))
        vm.takeAction(SearchAction.SearchTyped("granola"))
        settle()
        assertEquals(listOf("c:granola"), assertIs<SearchBody.Results>(vm.state.body).rows.map { it.key })

        vm.takeAction(SearchAction.ScopePicked(SearchScope.SavedMeals))
        vm.takeAction(SearchAction.SearchTyped("rice"))
        settle()
        assertEquals(listOf("Rice plate"), assertIs<SearchBody.SavedMeals>(vm.state.body).meals.map { it.name })
        assertTrue(catalog.searches.isEmpty())
    }

    @Test
    fun aSavedMealsPlusLogsTheWholeMeal() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()
        vm.takeAction(SearchAction.ScopePicked(SearchScope.SavedMeals))

        vm.takeAction(SearchAction.SavedMealAddTapped("rice-plate"))
        settle()

        val added = diary.added.single()
        assertEquals(listOf("Arroz con pollo", "Side salad"), added.items.map { it.name })
        assertTrue(added.items.all { it.method == LogMethod.Saved })
        assertEquals(SearchEvent.Added("Rice plate", recentLogEvents.logged.single()), events.single())
    }

    @Test
    fun aDoubleTapOnASavedMealsPlusLogsItOnce() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(SearchAction.ScopePicked(SearchScope.SavedMeals))
        diary.holdWrites = true

        vm.takeAction(SearchAction.SavedMealAddTapped("rice-plate"))
        vm.takeAction(SearchAction.SavedMealAddTapped("rice-plate"))
        settle()
        assertEquals(1, diary.added.size)

        diary.showWrites()
        settle()
        vm.takeAction(SearchAction.SavedMealAddTapped("rice-plate"))
        settle()
        assertEquals(2, diary.added.size, "once the day shows it, the meal can be logged again on purpose")
    }

    @Test
    fun noResultsOffersTheTermToDescribeAndAddingTheFoodForThisMeal() = runUnitTest {
        val vm = vm(photoOn = true)
        val events = events(vm)
        settle()
        vm.takeAction(SearchAction.SearchTyped(" kare-kare "))
        settle()
        assertEquals(SearchBody.NoResults("kare-kare", offline = false), vm.state.body)

        vm.takeAction(SearchAction.DescribeTapped)
        vm.takeAction(SearchAction.AddFoodTapped)
        settle()

        assertEquals(
            listOf(
                SearchEvent.OpenDescribe(LoggerFixtures.today, MealSlot.Dinner, "kare-kare"),
                SearchEvent.OpenSubmitFood(LoggerFixtures.today, MealSlot.Dinner),
            ),
            events,
        )
    }

    @Test
    fun withTheAddFoodAndBarcodeFlagsOffTheirTapsDoNothing() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(SearchAction.AddFoodTapped)
        vm.takeAction(SearchAction.BarcodeTapped)
        settle()

        assertTrue(events.isEmpty())
        assertFalse(vm.state.canScan)
    }

    @Test
    fun theBarcodeButtonOpensTheScannerForThisMeal() = runUnitTest {
        val vm = vm(scanOn = true)
        val events = events(vm)
        settle()

        vm.takeAction(SearchAction.BarcodeTapped)
        settle()

        assertEquals(listOf<SearchEvent>(SearchEvent.OpenScanner(LoggerFixtures.today, MealSlot.Dinner)), events)
    }

    @Test
    fun saveTheNameLogsANameOnlyFoodAndClearsTheSearch() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()
        vm.takeAction(SearchAction.SearchTyped("kare-kare"))
        settle()

        vm.takeAction(SearchAction.SaveNameTapped)
        settle()

        val made = customFoods.foods.value.last()
        assertEquals("kare-kare", made.name)
        val logged = diary.added.single().items.single()
        assertEquals(made.id, logged.customFoodId)
        assertEquals(Precision.Named, logged.precision)
        assertEquals("", vm.state.query)
        assertEquals(SearchEvent.Added("kare-kare", recentLogEvents.logged.single()), events.single())

        vm.takeAction(SearchAction.SearchTyped("Kare-Kare"))
        vm.takeAction(SearchAction.SaveNameTapped)
        settle()
        assertEquals(1, customFoods.foods.value.count { it.name.equals("kare-kare", ignoreCase = true) }, "reused, not duplicated")
    }

    @Test
    fun backCloses() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(SearchAction.BackTapped)
        settle()

        assertEquals(listOf<SearchEvent>(SearchEvent.Close), events)
    }

    // Create a meal

    @Test
    fun pickingForAMealPicksInsteadOfLoggingAndHandsThePicksBack() = runUnitTest {
        val vm = vm(FoodSearchRoute(pickForMeal = "meal-1"))
        val events = events(vm)
        val rows = searched(vm)

        vm.takeAction(SearchAction.AddTapped(rows.first { it.key == "f:rice" }))
        vm.takeAction(SearchAction.FoodNameTapped(rows.first { it.key == "f:brown-rice" }))
        vm.takeAction(SearchAction.AddTapped(rows.first { it.key == "f:brown-rice" }))
        vm.takeAction(SearchAction.AddTapped(rows.first { it.key == "f:brown-rice" }))
        settle()
        assertEquals(2, vm.state.picks?.count)

        vm.takeAction(SearchAction.AddPicksTapped)
        vm.takeAction(SearchAction.AddPicksTapped)
        settle()

        assertTrue(diary.added.isEmpty(), "nothing is logged")
        val picks = mealPicks.latest.value!!
        assertEquals("meal-1", picks.requestId)
        assertEquals(listOf("Jasmine rice, cooked", "Brown rice, cooked"), picks.items.map { it.name })
        assertEquals(SearchEvent.Close, events.last())
        assertEquals(1, events.count { it == SearchEvent.Close })
    }

    @Test
    fun pickingForAMealWithNothingPickedHandsNothingBack() = runUnitTest {
        val vm = vm(FoodSearchRoute(pickForMeal = "meal-1"))
        settle()

        vm.takeAction(SearchAction.AddPicksTapped)

        assertEquals(null, mealPicks.latest.value)
    }

    @Test
    fun pickingForAMealSavesANameAsAPick() = runUnitTest {
        val vm = vm(FoodSearchRoute(pickForMeal = "meal-1"))
        settle()
        vm.takeAction(SearchAction.SearchTyped("kare-kare"))
        settle()

        vm.takeAction(SearchAction.SaveNameTapped)
        vm.takeAction(SearchAction.DescribeTapped)
        settle()

        assertTrue(diary.added.isEmpty())
        assertEquals(1, vm.state.picks?.count)
    }

    // release.searchOptions

    @Test
    fun withTheOptionsOffNothingNewIsAskedOrSent() = runUnitTest {
        val vm = vm()
        searched(vm)

        assertEquals(0, catalog.optionsAsked)
        assertEquals(listOf(FoodSearchFilter.None), catalog.filters)
        assertEquals(null, vm.state.options)
    }

    @Test
    fun withTheOptionsOnTheServerIsAskedOnceAndSearchOpensWithItsAnswer() = runUnitTest {
        catalog.options = Catching.success(SearchOptions(openFoodFacts = true))
        val vm = vm(optionsOn = true)
        val events = events(vm)
        settle()

        vm.takeAction(SearchAction.SearchOptionsTapped)
        settle()

        assertEquals(1, catalog.optionsAsked)
        assertEquals(listOf<SearchEvent>(SearchEvent.OpenSearchOptions(openFoodFacts = true)), events)
    }

    @Test
    fun aTabWhileTypingSearchesAgainWithItsCategory() = runUnitTest {
        val vm = vm(optionsOn = true)
        searched(vm)

        vm.takeAction(SearchAction.TabPicked(SearchTab.Restaurant))
        settle()

        assertEquals(SearchCategory.Restaurant, catalog.filters.last().category)
        assertEquals(listOf("tab:restaurant"), optionsTelemetry.events)
    }

    @Test
    fun aSortPickedInTheSheetSearchesAgain() = runUnitTest {
        val vm = vm(optionsOn = true)
        searched(vm)

        settings.state.value = Settings(searchSort = SearchSort.AToZ)
        settle()

        assertEquals(SearchOrder.Name, catalog.filters.last().order)
    }

    @Test
    fun logsGoToTheDayTodayIsShowingWhenNoneIsGiven() = runUnitTest {
        val yesterday = LoggerFixtures.today.minus(1, DateTimeUnit.DAY)
        val vm = FoodSearchViewModel(
            route = FoodSearchRoute(slot = MealSlot.Dinner),
            diary = diary,
            customFoods = customFoods,
            savedMeals = FakeSavedMealRepository(),
            foodCatalog = catalog,
            settingsRepository = settings,
            policySource = FakePolicySource(),
            logger = FoodLogger(diary, catalog, recentLogEvents, telemetry, Clock.fixed(NOON)),
            calendar = FakeCalendar(),
            selectedDay = FakeSelectedDay(yesterday),
            clock = Clock.fixed(NOON),
            mealPicks = mealPicks,
            scanEnabled = scanFlag(false),
            submitFoodEnabled = submitFoodFlag(false),
            searchOptionsEnabled = searchOptionsFlag(false),
            optionsTelemetry = optionsTelemetry,
        )
        val rice = searched(vm).first()

        vm.takeAction(SearchAction.AddTapped(rice))
        settle()

        assertEquals(yesterday, diary.added.single().day)
    }

    // A day closed with Close the day (docs/decisions.md, 2026-10-27)

    @Test
    fun onAClosedDay_theListsGiveWayToTheClosedLine_andReopenBringsThemBack() = runUnitTest {
        diary.close(LoggerFixtures.today)
        val vm = vm(scanOn = true, photoOn = true)
        settle()

        assertEquals(SearchBody.DayClosed(isToday = true), vm.state.body)
        assertFalse(vm.state.canScan || vm.state.canAddFood || vm.state.canDescribe)

        vm.takeAction(SearchAction.ReopenTapped)
        settle()

        assertEquals(listOf(LoggerFixtures.today), diary.reopened)
        assertIs<SearchBody.Browse>(vm.state.body)
    }

    @Test
    fun aPlusOnADayClosedASecondAgo_logsNothing_andOffersNoUndo() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        val rice = searched(vm).first { it.key == "f:rice" }

        diary.close(LoggerFixtures.today)
        vm.takeAction(SearchAction.AddTapped(rice))
        settle()

        assertTrue(diary.added.isEmpty())
        assertTrue(events.none { it is SearchEvent.Added })
        assertEquals(SearchBody.DayClosed(isToday = true), vm.state.body)
    }

    /** A refused + wrote nothing, so nothing is waiting to show and the same + works after Reopen. */
    @Test
    fun aPlusRefusedByAClosedDay_logsOnceTheDayIsReopened() = runUnitTest {
        val vm = vm()
        val rice = searched(vm).first { it.key == "f:rice" }

        diary.close(LoggerFixtures.today)
        vm.takeAction(SearchAction.AddTapped(rice))
        settle()
        vm.takeAction(SearchAction.ReopenTapped)
        settle()
        vm.takeAction(SearchAction.AddTapped(rice))
        settle()

        assertEquals(1, diary.added.single().items.size)
    }

    @Test
    fun aSavedMealRefusedByAClosedDay_logsOnceTheDayIsReopened() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(SearchAction.ScopePicked(SearchScope.SavedMeals))

        diary.close(LoggerFixtures.today)
        vm.takeAction(SearchAction.SavedMealAddTapped("rice-plate"))
        settle()
        vm.takeAction(SearchAction.ReopenTapped)
        settle()
        vm.takeAction(SearchAction.SavedMealAddTapped("rice-plate"))
        settle()

        assertEquals(listOf("Arroz con pollo", "Side salad"), diary.added.single().items.map { it.name })
    }

    @Test
    fun pickingForAMeal_isNotStoppedByAClosedDay() = runUnitTest {
        diary.close(LoggerFixtures.today)
        val vm = vm(route = FoodSearchRoute(slot = MealSlot.Dinner, pickForMeal = "request"))
        settle()

        assertTrue(vm.state.body !is SearchBody.DayClosed, "a meal being put together writes nothing to the day")
    }
}
