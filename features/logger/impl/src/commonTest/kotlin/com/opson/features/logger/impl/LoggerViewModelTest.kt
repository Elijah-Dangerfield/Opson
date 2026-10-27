package com.opson.features.logger.impl

import com.opson.features.logger.LoggerRoute
import com.opson.features.logger.LoggerStart
import com.opson.libraries.core.Catching
import com.opson.libraries.core.fixed
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.health.ExerciseRelease
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import opson.libraries.resources.generated.resources.Res
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * [LoggerViewModel], the + sheet (design sync 4, 13.4a and 13.4b), one test per action: what
 * each grid action opens or writes, Recent's one-tap +, the panes, and the meal it all goes to.
 * The state mapping itself is [LoggerStateBuilderTest]'s; Describe's lines are
 * [DescribeReviewViewModelTest]'s.
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
    private val telemetry = RecordingTelemetry()
    private val selectedDay = FakeSelectedDay()
    private val recentLogEvents = FakeRecentLogEvents()
    private val appState = FakeAppState()

    @Suppress("LongParameterList")
    private fun vm(
        route: LoggerRoute = LoggerRoute(slot = MealSlot.Dinner),
        at: Instant = NOON,
        scanOn: Boolean = false,
        photoOn: Boolean = false,
        voiceOn: Boolean = false,
        fastingOn: Boolean = false,
        exerciseOn: Boolean = false,
        diaryRepo: FakeDiaryRepository = diary,
    ) = LoggerViewModel(
        route = route,
        diary = diaryRepo,
        customFoods = customFoods,
        savedMeals = savedMeals,
        foodCatalog = catalog,
        settingsRepository = settings,
        policySource = policySource,
        logger = FoodLogger(diaryRepo, catalog, recentLogEvents, telemetry, Clock.fixed(at)),
        calendar = FakeCalendar(),
        selectedDay = selectedDay,
        clock = Clock.fixed(at),
        appState = appState,
        telemetry = telemetry,
        scanEnabled = scanFlag(scanOn),
        photoEnabled = photoFlag(photoOn),
        voice = DescribeVoice(FakeSpeechToText(), voiceFlag(voiceOn), RecordingVoiceTelemetry()),
        fastingRelease = fastingFlag(fastingOn),
        exerciseRelease = ExerciseRelease { exerciseOn },
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

    // The grid

    @Test
    fun theGridHoldsEveryActionThatCanRun() = runUnitTest {
        settings.state.value = Settings(fasting = true)
        val vm = vm(scanOn = true, photoOn = true, voiceOn = true, fastingOn = true)
        settle()

        assertEquals(
            listOf(
                AddAction.SearchFood, AddAction.ScanBarcode, AddAction.Photo, AddAction.SayOrType(voice = true), AddAction.SavedMeals,
                AddAction.CopyMeal(MealSlot.Dinner), AddAction.Water, AddAction.WeighIn, AddAction.Note,
                AddAction.Fasting,
            ),
            vm.state.actions,
        )
    }

    @Test
    fun withTheFlagsOffTheirActionsAreNotThereAndTheirTapsDoNothing() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()

        assertTrue(vm.state.actions.none { it == AddAction.ScanBarcode || it == AddAction.Photo || it == AddAction.Fasting })
        assertTrue(AddAction.SayOrType(voice = false) in vm.state.actions, "Describe ships without voice, and says so")
        vm.takeAction(LoggerAction.BarcodeTapped)
        vm.takeAction(LoggerAction.PhotoTapped)
        settle()
        assertTrue(events.isEmpty())
    }

    @Test
    fun exercise_isOfferedWithItsFlag_andOpensTheSheetForThisDay() = runUnitTest {
        val vm = vm(exerciseOn = true)
        val events = events(vm)
        settle()

        assertEquals(AddAction.Exercise, vm.state.actions.last())
        vm.takeAction(LoggerAction.ExerciseTapped)
        settle()
        assertEquals(listOf<LoggerEvent>(LoggerEvent.OpenExercise(LoggerFixtures.today)), events)
    }

    @Test
    fun exercise_isNotOffered_withTheFlagOff_whereEnergyOutIsHidden_orToSomeoneNumbersHaveBeenHardFor() = runUnitTest {
        val off = vm(exerciseOn = false)
        settle()
        assertTrue(AddAction.Exercise !in off.state.actions)

        policySource.policy.value = policy(PresetId.MacrosWithGrams)
        val hidden = vm(exerciseOn = true)
        settle()
        assertTrue(AddAction.Exercise !in hidden.state.actions, "macros modes hide energy out")

        policySource.policy.value = policy(PresetId.Full)
        settings.state.value = Settings(onboarding = OnboardingAnswers(hard = true))
        val hard = vm(exerciseOn = true)
        settle()
        assertTrue(AddAction.Exercise !in hard.state.actions)
    }

    @Test
    fun searchFoodOpensSearchOnThisMealAndDay() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(LoggerAction.SearchFoodTapped)
        settle()

        assertEquals(listOf<LoggerEvent>(LoggerEvent.OpenSearch(LoggerFixtures.today, MealSlot.Dinner)), events)
    }

    @Test
    fun scanAndPhotoOpenForThisMealWhenTheirFlagsAreOn() = runUnitTest {
        val vm = vm(scanOn = true, photoOn = true)
        val events = events(vm)
        settle()

        vm.takeAction(LoggerAction.BarcodeTapped)
        vm.takeAction(LoggerAction.PhotoTapped)
        settle()

        assertEquals(
            listOf(LoggerEvent.OpenScanner(LoggerFixtures.today, MealSlot.Dinner), LoggerEvent.OpenPhoto(LoggerFixtures.today, MealSlot.Dinner)),
            events,
        )
    }

    @Test
    fun weighInFastingAndNoteOpenTheirScreens() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(LoggerAction.WeighInTapped)
        vm.takeAction(LoggerAction.FastingTapped)
        vm.takeAction(LoggerAction.NoteTapped)
        settle()

        assertEquals(
            listOf(LoggerEvent.OpenWeighIn, LoggerEvent.OpenFasting, LoggerEvent.OpenMealNote(LoggerFixtures.today, MealSlot.Dinner)),
            events,
        )
        assertEquals(listOf(LogVia.Note to 0), telemetry.events)
    }

    @Test
    fun waterAddsAGlassAndOffersItBack() = runUnitTest {
        settings.state.value = Settings(glassMl = 300)
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(LoggerAction.WaterTapped)
        vm.takeAction(LoggerAction.WaterTapped)
        settle()

        assertEquals(listOf(LoggerFixtures.today to 300), diary.water, "a second tap while closing adds nothing")
        assertEquals(listOf(LoggerEvent.WaterAdded(LoggerFixtures.today, 300), LoggerEvent.Dismiss), events)
    }

    @Test
    fun copyAMealCopiesYesterdaysAndTodayIsTold() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(LoggerAction.CopyMealTapped)

        assertEquals(Triple(LoggerFixtures.today.minus(1, DateTimeUnit.DAY), LoggerFixtures.today, MealSlot.Dinner), diary.copied.single())
        assertEquals(2, recentLogEvents.logged.single().itemIds.size)
        assertEquals(listOf<LoggerEvent>(LoggerEvent.Dismiss), events)
        assertEquals(listOf(LogVia.Yesterday to 2), telemetry.events)
    }

    /** The sheet animates down after a write; a second tap in that time is queued behind the first. */
    @Test
    fun aSecondTapWhileTheSheetClosesWritesNothingMore() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(LoggerAction.CopyMealTapped)
        vm.takeAction(LoggerAction.CopyMealTapped)
        vm.takeAction(LoggerAction.NumberTyped("450"))
        vm.takeAction(LoggerAction.AddNumberTapped)
        vm.takeAction(LoggerAction.WaterTapped)

        assertEquals(1, diary.copied.size)
        assertTrue(diary.added.isEmpty())
        assertTrue(diary.water.isEmpty())
        assertEquals(listOf<LoggerEvent>(LoggerEvent.Dismiss), events)
    }

    // Recent at full height

    @Test
    fun draggingUpGrowsTheSheetAndItNeverShrinks() = runUnitTest {
        val vm = vm()
        settle()
        assertEquals(SheetHeight.Partial, vm.state.height)

        vm.takeAction(LoggerAction.SheetExpanded)
        vm.takeAction(LoggerAction.SheetExpanded)
        assertEquals(SheetHeight.Full, vm.state.height)
    }

    @Test
    fun recentsPlusLogsAtTheLastAmountAndStaysOpen() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()
        val rice = vm.state.recent.first { it.ref == FoodRef.Catalog("rice") }
        assertFalse(rice.added)

        vm.takeAction(LoggerAction.RecentAddTapped(rice))
        settle()

        val added = diary.added.single()
        assertEquals(MealSlot.Dinner, added.slot)
        assertEquals("1 cup", added.items.single().amountText)
        assertEquals(LogMethod.Recent, added.items.single().method)
        assertTrue(vm.state.recent.first { it.key == rice.key }.added, "✓ once it is in")
        assertEquals(1, recentLogEvents.logged.size, "Today offers Undo")
        assertTrue(events.isEmpty(), "the sheet stays open")
    }

    @Test
    fun recentsCheckTakesWhatItAddedBackOut() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(LoggerAction.RecentAddTapped(vm.state.recent.first()))
        settle()

        vm.takeAction(LoggerAction.RecentAddTapped(vm.state.recent.first()))
        settle()

        assertEquals(1, diary.added.size, "nothing logged twice")
        assertEquals(1, diary.removed.size)
        assertFalse(vm.state.recent.first().added)
    }

    @Test
    fun aDoubleTapOnRecentsPlusLogsTheFoodOnce() = runUnitTest {
        val vm = vm()
        settle()
        val rice = vm.state.recent.first()
        diary.holdWrites = true

        vm.takeAction(LoggerAction.RecentAddTapped(rice))
        vm.takeAction(LoggerAction.RecentAddTapped(rice))
        settle()
        assertEquals(1, diary.added.size)

        diary.showWrites()
        settle()
        assertTrue(vm.state.recent.first().added)
        vm.takeAction(LoggerAction.RecentAddTapped(vm.state.recent.first()))
        settle()
        assertEquals(1, diary.removed.size, "once the day shows it, the ✓ takes it back out")
    }

    @Test
    fun aFoodsNameOpensItsDetail() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(LoggerAction.RecentNameTapped(vm.state.recent.first()))
        settle()

        assertEquals(listOf<LoggerEvent>(LoggerEvent.OpenFoodDetail(FoodRef.Catalog("rice"), PickSource.Recent, LoggerFixtures.today, MealSlot.Dinner)), events)
    }

    // Panes

    @Test
    fun actionsOpenPanesAndBackComesHome() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(LoggerAction.DescribeItTapped)
        assertEquals(LoggerPane.Describe, vm.state.pane)
        assertEquals(SheetHeight.Full, vm.state.height)
        vm.takeAction(LoggerAction.BackTapped)
        assertEquals(LoggerPane.Actions, vm.state.pane)

        vm.takeAction(LoggerAction.SavedMealsTapped)
        assertEquals(LoggerPane.Saved, vm.state.pane)
        vm.takeAction(LoggerAction.BackTapped)

        vm.takeAction(LoggerAction.BackTapped)
        assertEquals(listOf<LoggerEvent>(LoggerEvent.Dismiss), events, "back on the grid closes the sheet")
    }

    @Test
    fun eachStartOpensItsPaneFullHeight() = runUnitTest {
        assertEquals(LoggerPane.Saved, vm(LoggerRoute(slot = MealSlot.Lunch, start = LoggerStart.Saved)).state.pane)
        val describe = vm(LoggerRoute(slot = MealSlot.Lunch, start = LoggerStart.Describe))
        assertEquals(LoggerPane.Describe, describe.state.pane)
        assertEquals(SheetHeight.Full, describe.state.height)
        assertEquals(SheetHeight.Partial, vm(LoggerRoute()).state.height)
    }

    @Test
    fun aSavedMealFromTheFoodsTabOpensOnSavedMeals() = runUnitTest {
        val vm = vm(LoggerRoute(prePickSavedMealId = "sunday"))
        assertEquals(LoggerPane.Saved, vm.state.pane)
        assertEquals(SheetHeight.Full, vm.state.height)
    }

    @Test
    fun aSavedMealIsLoggedWholeAndTheSheetCloses() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()
        vm.takeAction(LoggerAction.SavedMealsTapped)

        vm.takeAction(LoggerAction.SavedMealPicked("sunday"))
        vm.takeAction(LoggerAction.SavedMealPicked("sunday"))

        val added = diary.added.single()
        assertEquals(listOf("Greek yogurt, plain", "Banana"), added.items.map { it.name })
        assertTrue(added.items.all { it.method == LogMethod.Saved })
        assertEquals(listOf(LogVia.Saved to 2), telemetry.events)
        assertEquals(listOf<LoggerEvent>(LoggerEvent.Dismiss), events)
    }

    @Test
    fun searchsDescribeItArrivesWithTheTermBeingRead() = runUnitTest {
        catalog.describeResult = Catching.success(LoggerFixtures.parsed)
        val vm = vm(LoggerRoute(slot = MealSlot.Dinner, start = LoggerStart.Describe, describeText = "kare-kare"))
        runCurrent()

        assertEquals(LoggerPane.Describe, vm.state.pane)
        assertEquals("kare-kare", vm.state.describe.text)
        settle()
        assertEquals(listOf("kare-kare"), catalog.described)
    }

    @Test
    fun describesAddLogsItsLinesAndCloses() = runUnitTest {
        catalog.describeResult = Catching.success(LoggerFixtures.parsed)
        val vm = vm()
        val events = events(vm)
        vm.takeAction(LoggerAction.DescribeItTapped)
        vm.takeAction(LoggerAction.DescribeTyped("2 eggs and toast"))
        assertEquals(DescribeStatus.Parsing, vm.state.describe.status)
        settle()

        vm.takeAction(LoggerAction.AddToMealTapped)
        vm.takeAction(LoggerAction.AddToMealTapped)
        settle()

        val added = diary.added.single()
        assertEquals(listOf("Egg, large", "Toast, white"), added.items.map { it.name })
        assertTrue(added.items.all { it.precision == Precision.Estimate && it.method == LogMethod.Describe })
        assertEquals(listOf<LoggerEvent>(LoggerEvent.Dismiss), events)
    }

    @Test
    fun anOlderParseIsDropped() = runUnitTest {
        val vm = vm()
        vm.takeAction(LoggerAction.DescribeTyped("2 eggs and toast"))

        vm.takeAction(LoggerAction.DescribeArrived("2 eggs", LoggerFixtures.parsed))

        assertTrue(vm.state.describe.lines.isEmpty())
        assertEquals(DescribeStatus.Parsing, vm.state.describe.status)
    }

    @Test
    fun aFailedReadSaysWhy() = runUnitTest {
        appState.isOffline.value = true
        catalog.describeResult = Catching.failure(IllegalStateException("offline"))
        val vm = vm()
        vm.takeAction(LoggerAction.DescribeTyped("2 eggs and juice"))
        settle()

        assertEquals(DescribeStatus.Offline, vm.state.describe.status)
        assertTrue(vm.state.describe.lines.isEmpty())
    }

    @Test
    fun describesJustSaveTheNameLogsANameOnlyFood() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        vm.takeAction(LoggerAction.DescribeItTapped)
        vm.takeAction(LoggerAction.DescribeTyped("Lola's kare-kare"))

        vm.takeAction(LoggerAction.SaveNameTapped)
        settle()

        val made = customFoods.foods.value.last()
        assertEquals("Lola's kare-kare", made.name)
        val logged = diary.added.single().items.single()
        assertEquals(Precision.Named, logged.precision)
        assertEquals(made.id, logged.customFoodId)
        assertEquals(listOf<LoggerEvent>(LoggerEvent.Dismiss), events)
    }

    @Test
    fun describesNumberLogsOnItsOwn() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        vm.takeAction(LoggerAction.DescribeItTapped)

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
        policySource.policy.value = policy(PresetId.NoNumbers)
        val vm = vm()
        vm.takeAction(LoggerAction.NumberTyped("450"))

        vm.takeAction(LoggerAction.AddNumberTapped)

        assertTrue(diary.added.isEmpty())
    }

    // The meal

    @Test
    fun slotPickedRetargetsTheSheet() = runUnitTest {
        val vm = vm()
        settle()

        vm.takeAction(LoggerAction.SlotPicked(MealSlot.Lunch))

        assertEquals(MealSlot.Lunch, vm.state.slot)
        assertTrue(vm.state.actions.none { it is AddAction.CopyMeal }, "yesterday only had a dinner")
        vm.takeAction(LoggerAction.RecentAddTapped(vm.state.recent.first()))
        settle()
        assertEquals(MealSlot.Lunch, diary.added.single().slot)
    }

    @Test
    fun withNoDayItLogsToTheDayTodayIsShowing() = runUnitTest {
        val yesterday = LoggerFixtures.today.minus(1, DateTimeUnit.DAY)
        selectedDay.select(yesterday)
        val vm = vm()

        vm.takeAction(LoggerAction.WaterTapped)
        assertEquals(yesterday, diary.water.single().first)
    }

    private val midMorning: Instant = Instant.parse("2026-10-25T10:40:00Z")

    private fun historyDiary() = FakeDiaryRepository(LoggerFixtures.midMorningHistory() + LoggerFixtures.day())

    @Test
    fun theNavPlusUsesTheClockBeforeThereIsHistory() = runUnitTest {
        val vm = vm(route = LoggerRoute())
        settle()

        assertEquals(MealSlot.Lunch, vm.state.slot, "two days of meals is not history yet; noon is lunch")
    }

    @Test
    fun theNavPlusPicksTheMealUsuallyLoggedAtThisTime() = runUnitTest {
        val vm = vm(route = LoggerRoute(), at = Instant.parse("2026-10-25T07:50:00Z"), diaryRepo = historyDiary())
        settle()

        assertEquals(MealSlot.Breakfast, vm.state.slot)
    }

    @Test
    fun theNavPlusFallsBackToUncategorized() = runUnitTest {
        val vm = vm(route = LoggerRoute(), at = midMorning, diaryRepo = historyDiary())
        settle()

        assertEquals(MealSlot.Uncategorized, vm.state.slot)
        assertEquals(MealSlot.Uncategorized, vm.state.slots.last())
    }

    @Test
    fun aMealsOwnPlusAlwaysUsesThatMeal() = runUnitTest {
        val vm = vm(route = LoggerRoute(slot = MealSlot.Breakfast), at = midMorning, diaryRepo = historyDiary())
        settle()

        assertEquals(MealSlot.Breakfast, vm.state.slot)
    }

    @Test
    fun aMealPickedFromTheDropdownStaysWhenTheInputsChange() = runUnitTest {
        val history = historyDiary()
        val vm = vm(route = LoggerRoute(), at = midMorning, diaryRepo = history)
        settle()

        vm.takeAction(LoggerAction.SlotPicked(MealSlot.Snack))
        history.recent.value = listOf(LoggerFixtures.item(LoggerFixtures.banana, MealSlot.Snack))
        settle()

        assertEquals(MealSlot.Snack, vm.state.slot)
    }

    @Test
    fun aDifferentModeRebuildsWithoutReopening() = runUnitTest {
        val vm = vm()
        settle()
        assertTrue(vm.state.describe.canNumber)

        policySource.policy.value = policy(PresetId.NoNumbers)
        settle()

        assertFalse(vm.state.describe.canNumber)
    }
}
