package com.opson.features.logger.impl

import com.opson.features.logger.LogRoute
import com.opson.features.logger.LogTab
import com.opson.libraries.core.Catching
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_toast_added_to
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Log screen: [LogStateBuilder]'s visibility rules and [LogViewModel] one
 * action at a time, the parse round trip through a fake catalog included.
 */
class LogTest : CoroutineTest() {
    private val diary = FakeDiaryRepository(listOf(LoggerFixtures.day(), LoggerFixtures.dayBefore()))
    private val savedMeals = FakeSavedMealRepository(LoggerFixtures.savedMeals)
    private val catalog = FakeFoodCatalog(describeResult = Catching.success(LoggerFixtures.parsed))
    private val settings = FakeSettingsRepository()
    private val policySource = FakePolicySource()
    private val appState = FakeAppState()
    private val telemetry = RecordingTelemetry()

    private fun vm(tab: LogTab = LogTab.Describe, focusNumber: Boolean = false) = LogViewModel(
        route = LogRoute(LoggerFixtures.today.toString(), MealSlot.Dinner, tab, focusNumber),
        diary = diary,
        savedMeals = savedMeals,
        foodCatalog = catalog,
        settingsRepository = settings,
        policySource = policySource,
        calendar = FakeCalendar(),
        appState = appState,
        telemetry = telemetry,
    )

    private fun TestScope.events(vm: LogViewModel): List<LogEvent> {
        val events = mutableListOf<LogEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    private val addedToDinner = LogEvent.ShowToast(Toast(Res.string.logger_toast_added_to, listOf(MealSlot.Dinner.sentenceName)))

    @Test
    fun aNumberIsOnlyOfferedWhenCaloriesAreNotHidden() {
        fun canNumber(preset: PresetId) = LoggerFixtures.logState(policy(preset), LogTab.Describe).describe.canNumber

        assertTrue(canNumber(PresetId.Full))
        assertTrue(canNumber(PresetId.Fuel), "softened calories still let you type a number")
        assertFalse(canNumber(PresetId.Blind))
        assertFalse(canNumber(PresetId.Mindful))
    }

    @Test
    fun parsedRowsAndSavedTotalsShowEnergyOnlyAtShow() {
        val full = LoggerFixtures.logState(policy(PresetId.Full), LogTab.Describe)
        val blind = LoggerFixtures.logState(policy(PresetId.Blind), LogTab.Describe)

        assertEquals("182 kcal", (assertIs<DescribeStatus.Parsed>(full.describe.status).items.first().calories as Display.Shown).text)
        assertTrue(assertIs<DescribeStatus.Parsed>(blind.describe.status).items.all { it.calories == Display.Hidden })
        assertTrue(blind.saved.meals.all { it.calories == Display.Hidden })
        assertEquals("205 kcal", (LoggerFixtures.logState(policy(PresetId.Full), LogTab.Saved).saved.meals.first().calories as Display.Shown).text)
    }

    @Test
    fun theDayBeforeRowNeedsThatMeal() {
        val inputs = LoggerFixtures.logInputs()
        fun row(slot: MealSlot) = LogStateBuilder.build(inputs, policy(PresetId.Full), slot, LogTab.Saved, "", DescribeModel.Idle, "", false).saved.dayBefore

        assertEquals(DayBeforeRow(MealSlot.Dinner, "Chicken adobo, Garlic fried rice (sinangag)"), row(MealSlot.Dinner))
        assertNull(row(MealSlot.Lunch))
    }

    @Test
    fun theNumberTakesDigitsOnly() = runUnitTest {
        val vm = vm()

        vm.takeAction(LogAction.NumberTyped("4a5.0"))

        assertEquals("450", vm.state.describe.number)
    }

    @Test
    fun zeroIsNotANumberToLog() = runUnitTest {
        val vm = vm()

        vm.takeAction(LogAction.NumberTyped("0"))
        vm.takeAction(LogAction.AddNumberTapped)

        assertTrue(diary.added.isEmpty())
    }

    @Test
    fun aNumberIsLoggedAsAQuickAdd() = runUnitTest {
        val vm = vm(focusNumber = true)
        val events = events(vm)
        assertTrue(vm.state.describe.focusNumber)

        vm.takeAction(LogAction.NumberTyped("450"))
        vm.takeAction(LogAction.AddNumberTapped)

        val added = diary.added.single()
        val draft = added.items.single()
        assertEquals(LogViewModel.QUICK_ADD_NAME, draft.name)
        assertEquals(Precision.Named, draft.precision)
        assertEquals(450.0, draft.kcal)
        assertEquals(LogMethod.QuickNumber, added.method)
        assertEquals(listOf(addedToDinner, LogEvent.Close), events)
        assertEquals(listOf(LogVia.QuickNumber to 1), telemetry.events)
    }

    @Test
    fun aNumberIsIgnoredWhenCaloriesAreHidden() = runUnitTest {
        policySource.policy.value = policy(PresetId.Blind)
        val vm = vm()

        vm.takeAction(LogAction.NumberTyped("450"))
        vm.takeAction(LogAction.AddNumberTapped)

        assertTrue(diary.added.isEmpty())
        assertEquals("", vm.state.describe.number)
    }

    @Test
    fun goParsesAndAddAddsTheParsedItems() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(LogAction.DescribeTyped("2 eggs and toast"))
        vm.takeAction(LogAction.GoTapped)
        assertEquals(listOf("Egg, scrambled", "Toast, white"), assertIs<DescribeStatus.Parsed>(vm.state.describe.status).items.map { it.name })

        vm.takeAction(LogAction.AddParsedTapped)

        val added = diary.added.single()
        assertEquals(LogMethod.Describe, added.method)
        assertTrue(added.items.all { it.precision == Precision.Estimate })
        assertEquals(listOf(addedToDinner, LogEvent.Close), events)
    }

    @Test
    fun typingAgainClearsTheParse() = runUnitTest {
        val vm = vm()
        vm.takeAction(LogAction.DescribeTyped("2 eggs"))
        vm.takeAction(LogAction.GoTapped)

        vm.takeAction(LogAction.DescribeTyped("2 eggs and toast"))

        assertEquals(DescribeStatus.Idle, vm.state.describe.status)
    }

    @Test
    fun aFailedParseIsOfflineWhenTheDeviceIs() = runUnitTest {
        catalog.describeResult = Catching.failure(IllegalStateException("no route"))
        appState.isOffline.value = true
        val vm = vm()

        vm.takeAction(LogAction.DescribeTyped("2 eggs"))
        vm.takeAction(LogAction.GoTapped)

        assertEquals(DescribeStatus.Offline, vm.state.describe.status)
    }

    @Test
    fun aFailedOrEmptyParseOnlineSaysItDidNotParse() = runUnitTest {
        catalog.describeResult = Catching.failure(IllegalStateException("500"))
        val vm = vm()
        vm.takeAction(LogAction.DescribeTyped("2 eggs"))
        vm.takeAction(LogAction.GoTapped)
        assertEquals(DescribeStatus.Failed, vm.state.describe.status)

        catalog.describeResult = Catching.success(emptyList())
        vm.takeAction(LogAction.GoTapped)
        assertEquals(DescribeStatus.Failed, vm.state.describe.status)
    }

    @Test
    fun goWithNothingTypedDoesNothing() = runUnitTest {
        val vm = vm()

        vm.takeAction(LogAction.GoTapped)

        assertEquals(DescribeStatus.Idle, vm.state.describe.status)
    }

    @Test
    fun justTheNameLogsNoNumbers() = runUnitTest {
        val vm = vm()

        vm.takeAction(LogAction.DescribeTyped("  Lola's pancit "))
        vm.takeAction(LogAction.SaveNameOnlyTapped)

        val draft = diary.added.single().items.single()
        assertEquals("Lola's pancit", draft.name)
        assertEquals(Precision.Named, draft.precision)
        assertNull(draft.kcal)
        assertNull(draft.protein)
        assertEquals(listOf(LogVia.NameOnly to 1), telemetry.events)
    }

    @Test
    fun copyingTheDayBeforeCopiesThatMeal() = runUnitTest {
        val vm = vm(LogTab.Saved)
        val events = events(vm)

        vm.takeAction(LogAction.CopyDayBeforeTapped)

        assertEquals(Triple(LoggerFixtures.today.minus(1, DateTimeUnit.DAY), LoggerFixtures.today, MealSlot.Dinner), diary.copied.single())
        assertEquals(listOf(addedToDinner, LogEvent.Close), events)
    }

    @Test
    fun aSavedMealIsAddedAsSaved() = runUnitTest {
        val vm = vm(LogTab.Saved)

        vm.takeAction(LogAction.SavedMealAddTapped("sunday"))

        val added = diary.added.single()
        assertEquals(LogMethod.Saved, added.method)
        assertEquals(listOf("Greek yogurt, plain", "Banana, raw"), added.items.map { it.name })
        assertTrue(added.items.all { it.method == LogMethod.Saved })
    }

    @Test
    fun slotsAndTabsArePicked() = runUnitTest {
        val vm = vm()

        vm.takeAction(LogAction.SlotPicked(MealSlot.Lunch))
        vm.takeAction(LogAction.TabPicked(LogTab.Saved))

        assertEquals(MealSlot.Lunch, vm.state.slot)
        assertEquals(LogTab.Saved, vm.state.tab)
    }

    @Test
    fun mealGroupsOffLogsToUncategorized() = runUnitTest {
        settings.state.value = Settings(mealGroups = false)
        val vm = vm()

        vm.takeAction(LogAction.DescribeTyped("tea"))
        vm.takeAction(LogAction.SaveNameOnlyTapped)

        assertEquals(MealSlot.Uncategorized, diary.added.single().slot)
    }

    @Test
    fun backCloses() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(LogAction.BackTapped)

        assertEquals(listOf<LogEvent>(LogEvent.Close), events)
    }
}
