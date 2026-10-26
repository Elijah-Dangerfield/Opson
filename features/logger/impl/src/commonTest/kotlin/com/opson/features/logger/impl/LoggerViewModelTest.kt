package com.opson.features.logger.impl

import com.opson.features.logger.LogRoute
import com.opson.features.logger.LogTab
import com.opson.features.logger.LoggerRoute
import com.opson.features.logger.LoggerStart
import com.opson.libraries.core.Catching
import com.opson.libraries.core.fixed
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.nutrition.NumbersSettings
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
import opson.libraries.resources.generated.resources.logger_toast_ate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * [LoggerViewModel], one test per action: what it writes, what it sends, the
 * panes, and the debounced search and Describe. The state mapping itself is
 * [LoggerStateBuilderTest]'s.
 */
class LoggerViewModelTest : CoroutineTest() {
    private val diary = FakeDiaryRepository(listOf(LoggerFixtures.day(), LoggerFixtures.dayBefore())).apply {
        recent.value = listOf(LoggerFixtures.rice, LoggerFixtures.banana).map { LoggerFixtures.item(it, MealSlot.Lunch) }
    }
    private val customFoods = FakeCustomFoodRepository(listOf(LoggerFixtures.granola))
    private val savedMeals = FakeSavedMealRepository(LoggerFixtures.savedMeals)
    private val catalog = FakeFoodCatalog(foods = listOf(LoggerFixtures.rice, LoggerFixtures.sinangag, LoggerFixtures.banana))
    private val settings = FakeSettingsRepository(Settings(askTags = false))
    private val policySource = FakePolicySource()
    private val basket = BasketStore()
    private val telemetry = RecordingTelemetry()
    private val selectedDay = FakeSelectedDay()
    private val recentLogEvents = FakeRecentLogEvents()
    private val appState = FakeAppState()
    private val mealPicks = FakeMealPicks()

    private fun vm(
        route: LoggerRoute = LoggerRoute(slot = MealSlot.Dinner),
        at: Instant = NOON,
        scanOn: Boolean = false,
        photoOn: Boolean = false,
    ) = LoggerViewModel(
        route = route,
        diary = diary,
        customFoods = customFoods,
        savedMeals = savedMeals,
        foodCatalog = catalog,
        settingsRepository = settings,
        policySource = policySource,
        basketStore = basket,
        committer = BasketCommitter(diary, catalog, basket, recentLogEvents, telemetry, Clock.fixed(at)),
        calendar = FakeCalendar(),
        selectedDay = selectedDay,
        clock = Clock.fixed(at),
        appState = appState,
        telemetry = telemetry,
        mealPicks = mealPicks,
        scanEnabled = scanFlag(scanOn),
        photoEnabled = photoFlag(photoOn),
    )

    private fun TestScope.events(vm: LoggerViewModel): List<LoggerEvent> {
        val events = mutableListOf<LoggerEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    @Test
    fun barcodeTappedOpensTheScannerForThisDayAndMealWhenTheFlagIsOn() = runUnitTest {
        val vm = vm(scanOn = true)
        val events = events(vm)
        settle()
        assertTrue(vm.state.canScan)

        vm.takeAction(LoggerAction.BarcodeTapped)
        settle()

        assertEquals(listOf<LoggerEvent>(LoggerEvent.OpenScanner(LoggerFixtures.today, MealSlot.Dinner)), events)
    }

    /** The scanner is pushed over the sheet, so closing it (or "Search instead") lands back here, on search. */
    @Test
    fun theScannerOpensOverTheSearchPane() = runUnitTest {
        val vm = vm(scanOn = true)
        events(vm)
        settle()

        vm.takeAction(LoggerAction.BarcodeTapped)
        settle()

        assertEquals(LoggerPane.Search, vm.state.pane)
        assertEquals("", vm.state.query)
        assertTrue(vm.state.canScan)
    }

    @Test
    fun photoTappedOpensThePhotoSheetForThisDayAndMealWhenTheFlagIsOn() = runUnitTest {
        val vm = vm(photoOn = true)
        val events = events(vm)
        settle()
        assertEquals(LoggerMethod.Photo, vm.state.methods.first())

        vm.takeAction(LoggerAction.PhotoTapped)
        settle()

        assertEquals(listOf<LoggerEvent>(LoggerEvent.OpenPhoto(LoggerFixtures.today, MealSlot.Dinner)), events)
    }

    @Test
    fun withThePhotoFlagOffThereIsNoChipAndTheTapDoesNothing() = runUnitTest {
        val vm = vm(photoOn = false)
        val events = events(vm)
        settle()
        assertFalse(LoggerMethod.Photo in vm.state.methods)

        vm.takeAction(LoggerAction.PhotoTapped)
        settle()

        assertTrue(events.isEmpty())
    }

    @Test
    fun pickingForAMealHasNoPhotoChip() = runUnitTest {
        val vm = vm(LoggerRoute(pickForMeal = "meal-1"), photoOn = true)
        val events = events(vm)
        settle()
        assertTrue(vm.state.methods.isEmpty())

        vm.takeAction(LoggerAction.PhotoTapped)
        settle()

        assertTrue(events.isEmpty())
    }

    @Test
    fun withTheFlagOffThereIsNoScannerAndTheTapDoesNothing() = runUnitTest {
        val vm = vm(scanOn = false)
        val events = events(vm)
        settle()
        assertFalse(vm.state.canScan)

        vm.takeAction(LoggerAction.BarcodeTapped)
        settle()

        assertTrue(events.isEmpty())
    }

    @Test
    fun pickingForAMealHasNoScanner() = runUnitTest {
        val vm = vm(LoggerRoute(pickForMeal = "meal-1"), scanOn = true)
        val events = events(vm)
        settle()
        assertFalse(vm.state.canScan)

        vm.takeAction(LoggerAction.BarcodeTapped)
        settle()

        assertTrue(events.isEmpty())
    }

    @Test
    fun withNoSlotTheTimeOfDayPicksOne() = runUnitTest {
        assertEquals(MealSlot.Breakfast, vm(LoggerRoute(), at = Instant.parse("2026-10-25T08:00:00Z")).state.slot)
        assertEquals(MealSlot.Dinner, vm(LoggerRoute(), at = Instant.parse("2026-10-25T19:00:00Z")).state.slot)
    }

    @Test
    fun withNoDayItLogsToTheDayTodayIsShowing() = runUnitTest {
        val yesterday = LoggerFixtures.today.minus(1, DateTimeUnit.DAY)
        selectedDay.select(yesterday)
        val vm = vm()

        assertEquals(RelativeDay.Yesterday, vm.state.day)
        vm.takeAction(LoggerAction.AteNoDetailsTapped)
        assertEquals(yesterday, diary.ateNoDetails.single().first)
    }

    @Test
    fun slotPickedRetargetsTheSheet() = runUnitTest {
        val vm = vm()
        basket.upsert(LoggerFixtures.pickedBasket().first())

        vm.takeAction(LoggerAction.SlotPicked(MealSlot.Lunch))

        assertEquals(MealSlot.Lunch, vm.state.slot)
        assertEquals(MealSlot.Lunch, vm.state.basket?.slot)
        assertTrue(vm.state.methods.none { it is LoggerMethod.Yesterday }, "yesterday only had a dinner")
        vm.takeAction(LoggerAction.AddToMealTapped)
        assertEquals(MealSlot.Lunch, diary.added.single().slot)
    }

    @Test
    fun searchWaitsForTypingToPauseAndSearchesOnce() = runUnitTest {
        val vm = vm()

        vm.takeAction(LoggerAction.SearchTyped("ri"))
        advanceTimeBy(100)
        vm.takeAction(LoggerAction.SearchTyped("rice"))
        assertEquals(LoggerBody.Searching, vm.state.body)
        assertTrue(vm.state.methods.isEmpty(), "the chips hide while typing")
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
        settle()

        assertTrue(assertIs<LoggerBody.Results>(vm.state.body).offline)
    }

    @Test
    fun clearingTheSearchGoesBackToBrowsing() = runUnitTest {
        val vm = vm()
        vm.takeAction(LoggerAction.SearchTyped("rice"))

        vm.takeAction(LoggerAction.SearchCleared)

        assertEquals("", vm.state.query)
        assertIs<LoggerBody.Browse>(vm.state.body)
        assertTrue(vm.state.methods.isNotEmpty())
    }

    @Test
    fun aMissCarriesTheTerm() = runUnitTest {
        val vm = vm()

        vm.takeAction(LoggerAction.SearchTyped("kare-kare"))
        settle()

        assertEquals(LoggerBody.NoResults("kare-kare", offline = false), vm.state.body)
    }

    @Test
    fun theToggleAddsAndRemovesAFood() = runUnitTest {
        val vm = vm()
        val rice = assertIs<LoggerBody.Browse>(vm.state.body).recent.first { it.key == "f:rice" }

        vm.takeAction(LoggerAction.FoodToggleTapped(rice))
        assertEquals(listOf("f:rice"), basket.basket.value.map { it.key })
        assertEquals(LogMethod.Recent, basket.basket.value.single().draft.method)
        assertEquals(1, vm.state.basket?.count)

        vm.takeAction(LoggerAction.FoodToggleTapped(rice))
        assertTrue(basket.basket.value.isEmpty())
        assertNull(vm.state.basket)
    }

    @Test
    fun tappingANameOpensTheAmountSheetForThisDayAndMeal() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        val rice = assertIs<LoggerBody.Browse>(vm.state.body).recent.first()

        vm.takeAction(LoggerAction.FoodNameTapped(rice))

        assertEquals(
            listOf<LoggerEvent>(LoggerEvent.OpenAmount(FoodRef.Catalog("rice"), PickSource.Recent, LoggerFixtures.today, MealSlot.Dinner)),
            events,
        )
    }

    @Test
    fun addingTheBasketWritesOnceTellsTodayAndCloses() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        LoggerFixtures.pickedBasket().forEachIndexed { index, item ->
            basket.upsert(if (index == 0) item.copy(hunger = Hunger.Peckish) else item)
        }

        vm.takeAction(LoggerAction.AddToMealTapped)

        val added = diary.added.single()
        assertEquals(MealSlot.Dinner, added.slot)
        assertEquals(listOf("Arroz con pollo", "Garlic fried rice (sinangag)"), added.items.map { it.name })
        assertEquals(Triple(LoggerFixtures.today, MealSlot.Dinner, MealFeeling(hunger = Hunger.Peckish)), diary.feelings.single())
        assertEquals(listOf(LoggerFixtures.arroz, LoggerFixtures.sinangag), catalog.marked)
        assertTrue(basket.basket.value.isEmpty())
        val logged = recentLogEvents.logged.single()
        assertEquals(LoggerFixtures.today, logged.day)
        assertEquals(MealSlot.Dinner, logged.slot)
        assertEquals(2, logged.itemIds.size)
        assertEquals(listOf<LoggerEvent>(LoggerEvent.Dismiss), events, "no toast: Today shows Added with Undo")
        assertEquals(listOf(LogVia.Usual to 2), telemetry.events)
    }

    @Test
    fun howItFeltFollowsWhenTheModeAsksOrHungerQuestionsAreOn() = runUnitTest {
        settings.state.value = Settings(askTags = false, numbers = NumbersSettings(preset = PresetId.Mindful))
        val mindful = vm()
        val mindfulEvents = events(mindful)
        basket.upsert(LoggerFixtures.pickedBasket().first())
        mindful.takeAction(LoggerAction.AddToMealTapped)
        assertEquals(listOf<LoggerEvent>(LoggerEvent.OpenHowItFelt(LoggerFixtures.today, MealSlot.Dinner)), mindfulEvents)

        settings.state.value = Settings(askTags = true)
        val asking = vm()
        val askingEvents = events(asking)
        basket.upsert(LoggerFixtures.pickedBasket().first())
        asking.takeAction(LoggerAction.AddToMealTapped)
        assertEquals(listOf<LoggerEvent>(LoggerEvent.OpenHowItFelt(LoggerFixtures.today, MealSlot.Dinner)), askingEvents)
    }

    @Test
    fun pickingForAMealKeepsItsOwnBasketAndHandsThePicksBack() = runUnitTest {
        val shared = LoggerFixtures.pickedBasket().first()
        basket.upsert(shared)
        val vm = vm(LoggerRoute(pickForMeal = "meal-1"))
        val events = events(vm)

        assertTrue(vm.state.pickingForMeal)
        assertTrue(vm.state.methods.isEmpty(), "every method chip logs straight to the diary")
        assertNull(vm.state.basket, "the person's own picks are not this meal's")

        val rice = assertIs<LoggerBody.Browse>(vm.state.body).recent.first { it.key == "f:rice" }
        vm.takeAction(LoggerAction.FoodNameTapped(rice))
        assertEquals(1, vm.state.basket?.count, "a name tap picks the row's amount instead of opening the amount sheet")
        vm.takeAction(LoggerAction.AddToMealTapped)

        val picks = assertNotNull(mealPicks.latest.value)
        assertEquals("meal-1", picks.requestId)
        assertEquals(listOf("Jasmine rice, cooked"), picks.items.map { it.name })
        assertTrue(diary.added.isEmpty(), "nothing is logged")
        assertTrue(recentLogEvents.logged.isEmpty())
        assertEquals(listOf(shared), basket.basket.value, "the shared basket is untouched")
        assertEquals(listOf<LoggerEvent>(LoggerEvent.Dismiss), events)
    }

    @Test
    fun pickingForAMealWithNothingPickedHandsNothingBack() = runUnitTest {
        val vm = vm(LoggerRoute(pickForMeal = "meal-1"))

        vm.takeAction(LoggerAction.AddToMealTapped)

        assertNull(mealPicks.latest.value)
    }

    @Test
    fun pickingForAMealIgnoresAPrePick() = runUnitTest {
        vm(LoggerRoute(pickForMeal = "meal-1", prePickSavedMealId = LoggerFixtures.savedMeals.first().id))

        assertTrue(basket.basket.value.isEmpty())
    }

    @Test
    fun anEmptyBasketAddsNothing() = runUnitTest {
        val vm = vm()

        vm.takeAction(LoggerAction.AddToMealTapped)

        assertTrue(diary.added.isEmpty())
        assertTrue(recentLogEvents.logged.isEmpty())
    }

    @Test
    fun yesterdaysMealIsCopiedAndTodayIsTold() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(LoggerAction.YesterdayTapped)

        assertEquals(Triple(LoggerFixtures.today.minus(1, DateTimeUnit.DAY), LoggerFixtures.today, MealSlot.Dinner), diary.copied.single())
        assertEquals(2, recentLogEvents.logged.single().itemIds.size)
        assertEquals(listOf<LoggerEvent>(LoggerEvent.Dismiss), events)
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
    fun chipsOpenPanesAndBackComesHome() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(LoggerAction.DescribeItTapped)
        assertEquals(LoggerPane.Describe, vm.state.pane)
        vm.takeAction(LoggerAction.BackTapped)
        assertEquals(LoggerPane.Search, vm.state.pane)

        vm.takeAction(LoggerAction.SavedMealsTapped)
        assertEquals(LoggerPane.Saved, vm.state.pane)
        vm.takeAction(LoggerAction.BackTapped)

        vm.takeAction(LoggerAction.JustANumberTapped)
        assertEquals(LoggerPane.Describe, vm.state.pane)
        assertTrue(vm.state.describe.focusNumber)
        vm.takeAction(LoggerAction.BackTapped)

        vm.takeAction(LoggerAction.AddANoteTapped)
        vm.takeAction(LoggerAction.BackTapped)
        assertEquals(
            listOf(LoggerEvent.OpenMealNote(LoggerFixtures.today, MealSlot.Dinner), LoggerEvent.Dismiss),
            events,
            "back on search closes the sheet",
        )
    }

    @Test
    fun theOldLogRouteOpensTheMatchingPane() = runUnitTest {
        val day = LoggerFixtures.today.toString()

        assertEquals(LoggerPane.Saved, vm(LogRoute(day, MealSlot.Lunch, LogTab.Saved).toLoggerRoute()).state.pane)
        val number = vm(LogRoute(day, MealSlot.Lunch, focusNumber = true).toLoggerRoute())
        assertEquals(LoggerPane.Describe, number.state.pane)
        assertTrue(number.state.describe.focusNumber)
        assertEquals(LoggerPane.Describe, vm(LoggerRoute(start = LoggerStart.Describe)).state.pane)
    }

    @Test
    fun describeReadsTheTextIntoPicksAfterAPause() = runUnitTest {
        catalog.describeResult = Catching.success(LoggerFixtures.parsed)
        val vm = vm()
        basket.upsert(LoggerFixtures.pickedBasket().first())
        vm.takeAction(LoggerAction.DescribeItTapped)

        vm.takeAction(LoggerAction.DescribeTyped("2 eggs and toast"))
        assertEquals(DescribeStatus.Parsing, vm.state.describe.status)
        settle()

        assertEquals(DescribeStatus.Parsed, vm.state.describe.status)
        assertEquals(listOf("Egg, large", "Toast, white"), vm.state.describe.lines.map { it.name })
        assertEquals(3, basket.basket.value.size, "search picks stay alongside")

        vm.takeAction(LoggerAction.BackTapped)
        assertEquals(listOf("f:arroz"), basket.basket.value.map { it.key }, "leaving Describe drops its lines")
    }

    @Test
    fun aNewParseReplacesTheLastOneAndAFailureSaysWhy() = runUnitTest {
        catalog.describeResult = Catching.success(LoggerFixtures.parsed)
        val vm = vm()
        vm.takeAction(LoggerAction.DescribeTyped("2 eggs and toast"))
        settle()

        catalog.describeResult = Catching.success(LoggerFixtures.parsed.take(1))
        vm.takeAction(LoggerAction.DescribeTyped("2 eggs"))
        settle()
        assertEquals(listOf("Egg, large"), vm.state.describe.lines.map { it.name })

        appState.isOffline.value = true
        catalog.describeResult = Catching.failure(IllegalStateException("offline"))
        vm.takeAction(LoggerAction.DescribeTyped("2 eggs and juice"))
        settle()
        assertEquals(DescribeStatus.Offline, vm.state.describe.status)
        assertTrue(vm.state.describe.lines.isEmpty())
    }

    @Test
    fun anOlderParseIsDropped() = runUnitTest {
        val vm = vm()
        vm.takeAction(LoggerAction.DescribeTyped("2 eggs and toast"))

        vm.takeAction(LoggerAction.DescribeArrived("2 eggs", LoggerFixtures.parsed))

        assertTrue(basket.basket.value.isEmpty())
        assertEquals(DescribeStatus.Parsing, vm.state.describe.status)
    }

    @Test
    fun editingADescribedLineOpensItsAmount() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        val line = LoggerFixtures.describeState(policy(PresetId.Full)).describe.lines.first()

        vm.takeAction(LoggerAction.DescribedLineEditTapped(line))

        assertEquals(
            listOf<LoggerEvent>(LoggerEvent.OpenAmount(FoodRef.Catalog("egg"), PickSource.Describe, LoggerFixtures.today, MealSlot.Dinner)),
            events,
        )
    }

    @Test
    fun justANumberLogsQuickAddOnItsOwn() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        vm.takeAction(LoggerAction.JustANumberTapped)

        vm.takeAction(LoggerAction.NumberTyped("4a50"))
        assertEquals("450", vm.state.describe.number)
        vm.takeAction(LoggerAction.AddNumberTapped)

        val added = diary.added.single()
        assertEquals(LogMethod.QuickNumber, added.method)
        assertEquals(450.0, added.items.single().kcal)
        assertEquals(LoggerViewModel.QUICK_ADD_NAME, added.items.single().name)
        assertEquals(1, recentLogEvents.logged.single().itemIds.size)
        assertEquals(listOf<LoggerEvent>(LoggerEvent.Dismiss), events)
    }

    @Test
    fun aNumberIsRefusedWhenCaloriesAreHidden() = runUnitTest {
        policySource.policy.value = policy(PresetId.Blind)
        val vm = vm()
        vm.takeAction(LoggerAction.NumberTyped("450"))

        vm.takeAction(LoggerAction.AddNumberTapped)

        assertTrue(diary.added.isEmpty())
    }

    @Test
    fun savingTheNameMakesAFoodAndPicksIt() = runUnitTest {
        val vm = vm()
        vm.takeAction(LoggerAction.SearchTyped("kare-kare"))
        settle()

        vm.takeAction(LoggerAction.SaveNameTapped)

        val made = customFoods.foods.value.last()
        assertEquals("kare-kare", made.name)
        val picked = basket.basket.value.single()
        assertEquals(FoodRef.Custom(made.id), picked.ref)
        assertEquals(Precision.Named, picked.draft.precision)
        assertEquals(PickSource.Name, picked.source)
        assertEquals("", vm.state.query)
        assertEquals(1, vm.state.basket?.count)

        vm.takeAction(LoggerAction.SearchTyped("Kare-Kare"))
        vm.takeAction(LoggerAction.SaveNameTapped)
        assertEquals(1, customFoods.foods.value.count { it.name.equals("kare-kare", ignoreCase = true) }, "reused, not duplicated")
    }

    @Test
    fun describeTheTermTakesTheQueryAlong() = runUnitTest {
        catalog.describeResult = Catching.success(LoggerFixtures.parsed)
        val vm = vm()
        vm.takeAction(LoggerAction.SearchTyped("kare-kare"))
        settle()

        vm.takeAction(LoggerAction.DescribeTermTapped)

        assertEquals(LoggerPane.Describe, vm.state.pane)
        assertEquals("kare-kare", vm.state.describe.text)
        assertEquals(DescribeStatus.Parsing, vm.state.describe.status)
    }

    @Test
    fun aSavedMealIsPickedAndTheSheetGoesBackToSearch() = runUnitTest {
        val vm = vm()
        vm.takeAction(LoggerAction.SavedMealsTapped)

        vm.takeAction(LoggerAction.SavedMealPicked("sunday"))

        assertEquals(LoggerPane.Search, vm.state.pane)
        assertEquals(listOf("f:yogurt", "f:banana"), basket.basket.value.map { it.key })
        assertTrue(basket.basket.value.all { it.draft.method == LogMethod.Saved })
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
        assertTrue(LoggerMethod.JustANumber in vm.state.methods)

        policySource.policy.value = policy(PresetId.Blind)

        assertTrue(LoggerMethod.AddANote in vm.state.methods)
    }
}
