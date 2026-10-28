package com.opson.features.logger.impl

import com.opson.features.logger.LoggerRoute
import com.opson.features.logger.LoggerStart
import com.opson.libraries.core.Catching
import com.opson.libraries.core.fixed
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.foods.ReviewStatus
import com.opson.libraries.health.ExerciseRelease
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    private val submittedFoods = FakeSubmittedFoods()

    @Suppress("LongParameterList")
    private fun vm(
        route: LoggerRoute = LoggerRoute(slot = MealSlot.Dinner),
        at: Instant = NOON,
        scanOn: Boolean = false,
        photoOn: Boolean = false,
        packOn: Boolean = false,
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
        submitFoodEnabled = submitFoodFlag(packOn),
        voice = DescribeVoice(FakeSpeechToText(), voiceFlag(voiceOn), RecordingVoiceTelemetry()),
        fastingRelease = fastingFlag(fastingOn),
        exerciseRelease = ExerciseRelease { exerciseOn },
        submittedFoods = submittedFoods,
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
        val vm = vm(scanOn = true, photoOn = true, packOn = true, voiceOn = true, fastingOn = true)
        settle()

        assertEquals(
            listOf(
                AddAction.SearchFood, AddAction.ScanBarcode, AddAction.PackagedFood, AddAction.Photo, AddAction.SayOrType(voice = true), AddAction.SavedMeals,
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

        assertTrue(
            vm.state.actions.none { it == AddAction.ScanBarcode || it == AddAction.Photo || it == AddAction.PackagedFood || it == AddAction.Fasting },
        )
        assertTrue(AddAction.SayOrType(voice = false) in vm.state.actions, "Describe ships without voice, and says so")
        vm.takeAction(LoggerAction.BarcodeTapped)
        vm.takeAction(LoggerAction.PhotoTapped)
        vm.takeAction(LoggerAction.PackagedFoodTapped)
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
    fun packagedFoodOpensThePacksPhotosForThisMeal() = runUnitTest {
        val vm = vm(packOn = true)
        val events = events(vm)
        settle()

        vm.takeAction(LoggerAction.PackagedFoodTapped)
        settle()

        assertEquals(listOf<LoggerEvent>(LoggerEvent.OpenPackagedFood(LoggerFixtures.today, MealSlot.Dinner)), events)
    }

    @Test
    fun aClosedDayDoesNotOfferPackagedFood() = runUnitTest {
        diary.close(LoggerFixtures.today)
        val vm = vm(packOn = true)
        settle()

        assertTrue(AddAction.PackagedFood !in vm.state.actions, "it logs, and a closed day takes nothing")
    }

    @Test
    fun aRecentFoodWaitingForReviewReadsUnverified() = runUnitTest {
        val granola = LoggerFixtures.granola
        submittedFoods.reviews.value = mapOf(granola.id to ReviewStatus.Pending)
        diary.recent.value = listOf(LoggerFixtures.item(LoggerFixtures.rice, MealSlot.Breakfast).copy(foodId = null, customFoodId = granola.id, name = granola.name))
        val vm = vm()
        settle()

        assertEquals(listOf(true), vm.state.recent.map { it.unverified })

        submittedFoods.reviews.value = mapOf(granola.id to ReviewStatus.Approved)
        settle()
        assertEquals(listOf(false), vm.state.recent.map { it.unverified }, "approved: the mark goes")
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
        vm.takeAction(QuickAddAction.NumberTyped(Metric.Calories, "450"))
        vm.takeAction(QuickAddAction.AddTapped)
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

    /** A refused + wrote nothing, so nothing is waiting to show and the same + works after Reopen. */
    @Test
    fun aRecentPlusRefusedByAClosedDay_logsOnceTheDayIsReopened() = runUnitTest {
        val vm = vm()
        settle()
        val rice = vm.state.recent.first()

        diary.close(LoggerFixtures.today)
        vm.takeAction(LoggerAction.RecentAddTapped(rice))
        settle()
        assertTrue(diary.added.isEmpty())
        vm.takeAction(LoggerAction.ReopenTapped)
        settle()
        vm.takeAction(LoggerAction.RecentAddTapped(vm.state.recent.first { it.key == rice.key }))
        settle()

        assertEquals(1, diary.added.size)
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

    // Quick add

    private suspend fun LoggerViewModel.typeQuick(form: QuickAddForm) {
        takeAction(QuickAddAction.NameTyped(form.name))
        form.numbers.forEach { (metric, text) -> takeAction(QuickAddAction.NumberTyped(metric, text)) }
    }

    @Test
    fun aQuickNumberLogsOnItsOwn_underTheDefaultName() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        vm.takeAction(LoggerAction.DescribeItTapped)

        vm.takeAction(QuickAddAction.NumberTyped(Metric.Calories, "4a50"))
        assertEquals("450", vm.state.describe.quickAdd?.fields?.first()?.text)
        vm.takeAction(QuickAddAction.AddTapped)

        val added = diary.added.single()
        assertEquals(LogMethod.QuickNumber, added.method)
        val item = added.items.single()
        assertEquals(450.0, item.kcal)
        assertEquals(QuickAdd.DEFAULT_NAME, item.name)
        assertNull(item.protein, "a macro nobody typed is unknown, not 0")
        assertEquals(1, recentLogEvents.logged.single().itemIds.size)
        assertEquals(listOf(LogVia.QuickNumber to 1), telemetry.events)
        assertEquals(listOf<LoggerEvent>(LoggerEvent.Dismiss), events)
    }

    @Test
    fun allNumbers_logsTheNameEnergyAndMacrosAsTyped() = runUnitTest {
        val vm = vm()
        settle()
        vm.typeQuick(LoggerFixtures.proteinBar)
        vm.takeAction(QuickAddAction.NumberTyped(Metric.Fat, "20,5"))

        vm.takeAction(QuickAddAction.AddTapped)

        val item = diary.added.single().items.single()
        assertEquals("Protein bar", item.name)
        assertEquals(listOf(400.0, 30.0, 50.0, 20.5, 4.0), listOf(item.kcal, item.protein, item.carbs, item.fat, item.fiber))
    }

    @Test
    fun macrosWithGrams_storesTheMacrosEnergy_neverATypedOne() = runUnitTest {
        policySource.policy.value = policy(PresetId.MacrosWithGrams)
        val vm = vm()
        settle()
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber), vm.state.describe.quickAdd?.fields?.map { it.metric })
        // A calorie number sent anyway (a field left over from a mode change) is not this mode's to take.
        vm.typeQuick(LoggerFixtures.proteinBar)

        vm.takeAction(QuickAddAction.AddTapped)

        val item = diary.added.single().items.single()
        assertEquals(30.0 * 4 + 50.0 * 4 + 20.0 * 9, item.kcal, "4/4/9, so the day's totals agree with the macros")
        assertEquals(30.0, item.protein)
        assertEquals(4.0, item.fiber)
    }

    @Test
    fun macrosOnlyAndNoNumbers_takeNoNumbers() = runUnitTest {
        listOf(PresetId.MacrosOnly, PresetId.NoNumbers).forEach { preset ->
            policySource.policy.value = policy(preset)
            val vm = vm()
            settle()
            assertNull(vm.state.describe.quickAdd, "$preset")
            vm.typeQuick(LoggerFixtures.proteinBar)

            vm.takeAction(QuickAddAction.AddTapped)

            assertTrue(diary.added.isEmpty(), "$preset")
        }
    }

    @Test
    fun aModeThatHidesTheNumbersAfterTheyWereTyped_hasTheLastWord() = runUnitTest {
        val vm = vm()
        settle()
        vm.typeQuick(LoggerFixtures.proteinBar)

        policySource.policy.value = policy(PresetId.NoNumbers)
        settle()
        vm.takeAction(QuickAddAction.AddTapped)

        assertTrue(diary.added.isEmpty())
    }

    @Test
    fun theMismatch_isFixedEitherWay() = runUnitTest {
        val vm = vm()
        settle()
        vm.typeQuick(LoggerFixtures.proteinBar)
        assertEquals(QuickAddMismatch(Display.Shown("500 kcal"), Display.Shown("400 kcal")), vm.state.describe.quickAdd?.mismatch)

        // The macros drive.
        vm.takeAction(QuickAddAction.UseMacroEnergyTapped)
        assertEquals("500", vm.state.describe.quickAdd?.fields?.first { it.metric == Metric.Calories }?.text)
        assertNull(vm.state.describe.quickAdd?.mismatch)

        // The energy drives: back to 400, and the three macros scale down together.
        vm.takeAction(QuickAddAction.NumberTyped(Metric.Calories, "400"))
        vm.takeAction(QuickAddAction.FitMacrosTapped)
        val fields = vm.state.describe.quickAdd?.fields.orEmpty().associate { it.metric to it.text }
        assertEquals(mapOf(Metric.Calories to "400", Metric.Protein to "24", Metric.Carbs to "40", Metric.Fat to "16", Metric.Fiber to "4"), fields)
        assertNull(vm.state.describe.quickAdd?.mismatch)

        vm.takeAction(QuickAddAction.AddTapped)
        val item = diary.added.single().items.single()
        assertEquals(listOf(400.0, 24.0, 40.0, 16.0), listOf(item.kcal, item.protein, item.carbs, item.fat))
    }

    @Test
    fun nothingAboveZero_addsNothing() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(QuickAddAction.NameTyped("Tea"))
        vm.takeAction(QuickAddAction.NumberTyped(Metric.Calories, "0"))
        assertEquals(false, vm.state.describe.quickAdd?.canAdd)

        vm.takeAction(QuickAddAction.AddTapped)

        assertTrue(diary.added.isEmpty())
    }

    @Test
    fun backOutOfDescribe_dropsTheQuickAdd() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(LoggerAction.DescribeItTapped)
        vm.typeQuick(LoggerFixtures.proteinBar)

        vm.takeAction(LoggerAction.BackTapped)
        vm.takeAction(LoggerAction.DescribeItTapped)

        assertEquals("", vm.state.describe.quickAdd?.name)
        assertTrue(vm.state.describe.quickAdd?.fields.orEmpty().all { it.text.isEmpty() })
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

        assertEquals(RelativeDay.Yesterday, vm.state.day)
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
        assertNotNull(vm.state.describe.quickAdd)

        policySource.policy.value = policy(PresetId.NoNumbers)
        settle()

        assertNull(vm.state.describe.quickAdd)
    }

    // A day closed with Close the day (docs/decisions.md, 2026-10-27)

    @Test
    fun aClosedDay_saysSoOverTheGrid_keepsOnlyWhatIsNotPartOfTheDay_andLogsNothing() = runUnitTest {
        diary.close(LoggerFixtures.today)
        settings.state.value = Settings(fasting = true)
        val vm = vm(route = LoggerRoute(slot = MealSlot.Dinner, start = LoggerStart.Saved), fastingOn = true)
        val events = events(vm)
        settle()

        assertTrue(vm.state.closed)
        assertEquals(LoggerPane.Actions, vm.state.pane, "Saved meals only adds, so the sheet opens on the closed line")
        assertEquals(listOf(AddAction.WeighIn, AddAction.Fasting), vm.state.actions)
        assertTrue(vm.state.recent.isEmpty())

        vm.takeAction(LoggerAction.SavedMealPicked("sunday"))
        vm.takeAction(LoggerAction.WaterTapped)
        vm.takeAction(LoggerAction.CopyMealTapped)
        settle()

        assertTrue(diary.added.isEmpty() && diary.water.isEmpty() && diary.copied.isEmpty())
        assertTrue(recentLogEvents.logged.isEmpty())
        assertTrue(events.isEmpty(), "the sheet stays on the closed line rather than closing as if it had logged")
    }

    @Test
    fun reopen_onTheClosedLine_bringsTheGridBack() = runUnitTest {
        diary.close(LoggerFixtures.today)
        val vm = vm()
        settle()

        vm.takeAction(LoggerAction.ReopenTapped)
        settle()

        assertEquals(listOf(LoggerFixtures.today), diary.reopened)
        assertFalse(vm.state.closed)
        assertTrue(AddAction.SearchFood in vm.state.actions)
    }

    @Test
    fun aDayClosedWhileTheSheetIsOpen_refusesTheLog_andTheSheetSaysSo() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()
        assertFalse(vm.state.closed)

        diary.close(LoggerFixtures.today)
        vm.takeAction(LoggerAction.SavedMealPicked("sunday"))
        settle()

        assertTrue(diary.added.isEmpty())
        assertTrue(vm.state.closed)
        assertTrue(LoggerEvent.Dismiss !in events)
    }

    @Test
    fun aQuickAddOnADayClosedWhileTheSheetIsOpen_isRefused() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()
        vm.typeQuick(LoggerFixtures.proteinBar)

        diary.close(LoggerFixtures.today)
        vm.takeAction(QuickAddAction.AddTapped)
        settle()

        assertTrue(diary.added.isEmpty())
        assertTrue(vm.state.closed)
        assertTrue(LoggerEvent.Dismiss !in events)
    }
}
