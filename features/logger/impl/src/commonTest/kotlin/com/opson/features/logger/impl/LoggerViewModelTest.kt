package com.opson.features.logger.impl

import com.opson.features.logger.LogTab
import com.opson.features.logger.LoggerRoute
import com.opson.libraries.core.fixed
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.ui.time.RelativeDay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_toast_added_many
import opson.libraries.resources.generated.resources.logger_toast_added_one
import opson.libraries.resources.generated.resources.logger_toast_added_to
import opson.libraries.resources.generated.resources.logger_toast_ate
import opson.libraries.resources.generated.resources.logger_toast_water
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * [LoggerViewModel], one test per action: what it writes, what it sends, and
 * the debounced search. The state mapping itself is [LoggerStateBuilderTest]'s.
 */
class LoggerViewModelTest : CoroutineTest() {
    private val diary = FakeDiaryRepository(listOf(LoggerFixtures.day(), LoggerFixtures.dayBefore())).apply {
        favorites.value = listOf(LoggerFixtures.banana, LoggerFixtures.yogurt).map { LoggerFixtures.item(it, MealSlot.Snack) }
        recent.value = listOf(LoggerFixtures.rice).map { LoggerFixtures.item(it, MealSlot.Lunch) }
    }
    private val customFoods = FakeCustomFoodRepository(listOf(LoggerFixtures.granola))
    private val savedMeals = FakeSavedMealRepository(LoggerFixtures.savedMeals)
    private val catalog = FakeFoodCatalog(foods = listOf(LoggerFixtures.rice, LoggerFixtures.friedRice, LoggerFixtures.banana))
    private val settings = FakeSettingsRepository()
    private val policySource = FakePolicySource()
    private val basket = BasketStore()
    private val telemetry = RecordingTelemetry()
    private val selectedDay = FakeSelectedDay()

    private fun vm(route: LoggerRoute = LoggerRoute(slot = MealSlot.Dinner), at: Instant = NOON) = LoggerViewModel(
        route = route,
        diary = diary,
        customFoods = customFoods,
        savedMeals = savedMeals,
        foodCatalog = catalog,
        settingsRepository = settings,
        policySource = policySource,
        basketStore = basket,
        calendar = FakeCalendar(),
        selectedDay = selectedDay,
        clock = Clock.fixed(at),
        telemetry = telemetry,
    )

    private fun TestScope.events(vm: LoggerViewModel): List<LoggerEvent> {
        val events = mutableListOf<LoggerEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    @Test
    fun withNoSlotTheTimeOfDayPicksOne() = runUnitTest {
        assertEquals(MealSlot.Breakfast, vm(LoggerRoute(), at = Instant.parse("2026-10-25T08:00:00Z")).state.slot)
        assertEquals(MealSlot.Dinner, vm(LoggerRoute(), at = Instant.parse("2026-10-25T19:00:00Z")).state.slot)
    }

    @Test
    fun withNoDayItLogsToTheDayTodayIsShowing() = runUnitTest {
        selectedDay.select(LoggerFixtures.today.minus(1, DateTimeUnit.DAY))
        val vm = vm()
        val events = events(vm)

        assertEquals(RelativeDay.Yesterday, vm.state.otherDay)
        vm.takeAction(LoggerAction.WaterTapped)
        assertEquals(LoggerFixtures.today.minus(1, DateTimeUnit.DAY), diary.water.single().first)
        assertTrue(events.isNotEmpty())
    }

    @Test
    fun slotPickedMovesTheChips() = runUnitTest {
        val vm = vm()

        vm.takeAction(LoggerAction.SlotPicked(MealSlot.Lunch))

        assertEquals(MealSlot.Lunch, vm.state.slot)
        assertTrue(vm.state.methods.none { it is LoggerMethod.Yesterday }, "yesterday only had a dinner")
    }

    @Test
    fun searchWaitsForTypingToPauseAndSearchesOnce() = runUnitTest {
        val vm = vm()

        vm.takeAction(LoggerAction.SearchTyped("ri"))
        advanceTimeBy(100)
        vm.takeAction(LoggerAction.SearchTyped("rice"))
        assertEquals(LoggerBody.Searching, vm.state.body)
        advanceTimeBy(260)
        runCurrent()

        assertEquals(listOf("rice"), catalog.searches)
        val results = assertIs<LoggerBody.Results>(vm.state.body)
        assertEquals(listOf("f:rice", "f:sinangag"), results.rows.map { it.key })
    }

    @Test
    fun anAnswerForAnOlderQueryIsDropped() = runUnitTest {
        val vm = vm()
        vm.takeAction(LoggerAction.SearchTyped("rice"))

        vm.takeAction(LoggerAction.SearchResultsArrived(SearchOutcome("ric", listOf(LoggerFixtures.banana), offline = false)))

        assertEquals(LoggerBody.Searching, vm.state.body)
    }

    @Test
    fun offlineSearchSaysSo() = runUnitTest {
        catalog.online = false
        val vm = vm()

        vm.takeAction(LoggerAction.SearchTyped("rice"))
        advanceTimeBy(260)
        runCurrent()

        assertTrue(assertIs<LoggerBody.Results>(vm.state.body).offline)
    }

    @Test
    fun clearingTheSearchGoesBackToBrowsing() = runUnitTest {
        val vm = vm()
        vm.takeAction(LoggerAction.SearchTyped("rice"))

        vm.takeAction(LoggerAction.SearchTyped("  "))

        assertIs<LoggerBody.Browse>(vm.state.body)
    }

    @Test
    fun theToggleAddsAndRemovesAFood() = runUnitTest {
        val vm = vm()
        val banana = assertIs<LoggerBody.Browse>(vm.state.body).favorites.first { it.key == "f:banana" }

        vm.takeAction(LoggerAction.FoodToggleTapped(banana))
        assertEquals(listOf("f:banana"), basket.basket.value.map { it.key })
        assertEquals(LogMethod.Favorite, basket.basket.value.single().draft.method)
        assertEquals(1, vm.state.basket?.count)

        vm.takeAction(LoggerAction.FoodToggleTapped(banana))
        assertTrue(basket.basket.value.isEmpty())
        assertEquals(null, vm.state.basket)
    }

    @Test
    fun tappingANameOpensTheAmountSheet() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        val rice = assertIs<LoggerBody.Browse>(vm.state.body).recent.single()

        vm.takeAction(LoggerAction.FoodNameTapped(rice))

        assertEquals(listOf<LoggerEvent>(LoggerEvent.OpenAmount(FoodRef.Catalog("rice"), PickSource.Recent)), events)
    }

    @Test
    fun addingTheBasketWritesOnceMergesTheTagsAndCloses() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        LoggerFixtures.pickedBasket().forEachIndexed { index, item ->
            basket.upsert(if (index == 0) item.copy(hunger = Hunger.Peckish) else item)
        }

        vm.takeAction(LoggerAction.AddToMealTapped)

        val added = diary.added.single()
        assertEquals(MealSlot.Dinner, added.slot)
        assertEquals(listOf("Banana, raw", "Greek yogurt, plain"), added.items.map { it.name })
        assertEquals(Triple(LoggerFixtures.today, MealSlot.Dinner, MealFeeling(hunger = Hunger.Peckish)), diary.feelings.single())
        assertEquals(listOf(LoggerFixtures.banana, LoggerFixtures.yogurt), catalog.marked)
        assertTrue(basket.basket.value.isEmpty())
        assertEquals(
            listOf(
                LoggerEvent.ShowToast(Toast(Res.string.logger_toast_added_many, listOf(2, MealSlot.Dinner.sentenceName))),
                LoggerEvent.Dismiss,
            ),
            events,
        )
        assertEquals(listOf(LogVia.Favorite to 2), telemetry.events)
    }

    @Test
    fun oneFoodIsNamedInTheToast() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        basket.upsert(LoggerFixtures.pickedBasket().last())

        vm.takeAction(LoggerAction.AddToMealTapped)

        assertEquals(
            LoggerEvent.ShowToast(Toast(Res.string.logger_toast_added_one, listOf("Greek yogurt", MealSlot.Dinner.sentenceName))),
            events.first(),
        )
        assertTrue(diary.feelings.isEmpty(), "no tags, no feeling written")
    }

    @Test
    fun anEmptyBasketAddsNothing() = runUnitTest {
        val vm = vm()

        vm.takeAction(LoggerAction.AddToMealTapped)

        assertTrue(diary.added.isEmpty())
    }

    @Test
    fun waterIsAddedAndTheSheetStaysOpen() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(LoggerAction.WaterTapped)

        assertEquals(listOf(LoggerFixtures.today to 250), diary.water)
        assertEquals(listOf<LoggerEvent>(LoggerEvent.ShowToast(Toast(Res.string.logger_toast_water))), events)
    }

    @Test
    fun yesterdaysMealIsCopiedAndTheSheetCloses() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(LoggerAction.YesterdayTapped)

        assertEquals(Triple(LoggerFixtures.today.minus(1, DateTimeUnit.DAY), LoggerFixtures.today, MealSlot.Dinner), diary.copied.single())
        assertEquals(
            listOf(LoggerEvent.ShowToast(Toast(Res.string.logger_toast_added_to, listOf(MealSlot.Dinner.sentenceName))), LoggerEvent.Dismiss),
            events,
        )
        assertEquals(listOf(LogVia.Yesterday to 2), telemetry.events)
    }

    @Test
    fun ateNoDetailsMarksTheMeal() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(LoggerAction.AteNoDetailsTapped)

        assertEquals(listOf(LoggerFixtures.today to MealSlot.Dinner), diary.ateNoDetails)
        assertEquals(LoggerEvent.ShowToast(Toast(Res.string.logger_toast_ate, listOf(MealSlot.Dinner.sentenceName))), events.first())
        assertEquals(LoggerEvent.Dismiss, events.last())
    }

    @Test
    fun methodChipsOpenTheirScreens() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(LoggerAction.DescribeItTapped)
        vm.takeAction(LoggerAction.SavedMealsTapped)
        vm.takeAction(LoggerAction.JustANumberTapped)
        vm.takeAction(LoggerAction.WeighInTapped)
        vm.takeAction(LoggerAction.AddANoteTapped)
        vm.takeAction(LoggerAction.CloseTapped)

        val today = LoggerFixtures.today
        assertEquals(
            listOf(
                LoggerEvent.OpenLog(today, MealSlot.Dinner, LogTab.Describe),
                LoggerEvent.OpenLog(today, MealSlot.Dinner, LogTab.Saved),
                LoggerEvent.OpenLog(today, MealSlot.Dinner, LogTab.Describe, focusNumber = true),
                LoggerEvent.OpenWeight,
                LoggerEvent.OpenMealNote(today, MealSlot.Dinner),
                LoggerEvent.Dismiss,
            ),
            events,
        )
    }

    @Test
    fun aFoodPickedOnTheFoodsTabArrivesOnce() = runUnitTest {
        val route = LoggerRoute(prePickFoodId = "banana")

        vm(route)

        assertEquals(listOf("f:banana"), basket.basket.value.map { it.key })
        assertEquals(PickSource.Foods, basket.basket.value.single().source)
        vm(route)
        assertEquals(1, basket.basket.value.size, "the same food again replaces, it does not stack")
    }

    @Test
    fun aCustomFoodAndASavedMealCanBePrePicked() = runUnitTest {
        vm(LoggerRoute(prePickFoodId = "granola", prePickIsCustom = true))
        vm(LoggerRoute(prePickSavedMealId = "sunday"))

        assertEquals(listOf("c:granola", "f:yogurt", "f:banana"), basket.basket.value.map { it.key })
        assertEquals(LogMethod.Custom, basket.basket.value.first().draft.method)
        assertEquals(LogMethod.Saved, basket.basket.value.last().draft.method)
    }

    @Test
    fun aDifferentModeRebuildsWithoutReopening() = runUnitTest {
        val vm = vm()
        assertTrue(vm.state.totals != null)

        policySource.policy.value = policy(PresetId.Blind)

        assertEquals(null, vm.state.totals)
        assertTrue(vm.state.mealChecks != null)
    }
}
