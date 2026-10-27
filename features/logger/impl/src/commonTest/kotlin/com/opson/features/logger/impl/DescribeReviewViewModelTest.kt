package com.opson.features.logger.impl

import com.opson.features.logger.LoggerRoute
import com.opson.features.logger.LoggerStart
import com.opson.libraries.core.Catching
import com.opson.libraries.core.fixed
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.math.roundToInt
import kotlin.time.Clock

/**
 * Describe's review through [LoggerViewModel], one test per [DescribeReviewAction]:
 * each changes its own line, keeps the basket in step, and loses no other line.
 */
class DescribeReviewViewModelTest : CoroutineTest() {
    private val diary = FakeDiaryRepository(listOf(LoggerFixtures.day(), LoggerFixtures.dayBefore()))
    private val catalog = FakeFoodCatalog(
        foods = listOf(LoggerFixtures.rice, LoggerFixtures.coffee, LoggerFixtures.latte),
        describeResult = Catching.success(LoggerFixtures.parsed),
    )
    private val policySource = FakePolicySource()
    private val basket = BasketStore()

    private fun vm() = LoggerViewModel(
        route = LoggerRoute(slot = MealSlot.Breakfast, start = LoggerStart.Describe),
        diary = diary,
        customFoods = FakeCustomFoodRepository(emptyList()),
        savedMeals = FakeSavedMealRepository(emptyList()),
        foodCatalog = catalog,
        settingsRepository = FakeSettingsRepository(Settings(askTags = false)),
        policySource = policySource,
        basketStore = basket,
        committer = BasketCommitter(diary, catalog, basket, FakeRecentLogEvents(), RecordingTelemetry(), Clock.fixed(NOON)),
        calendar = FakeCalendar(),
        selectedDay = FakeSelectedDay(),
        clock = Clock.fixed(NOON),
        appState = FakeAppState(),
        telemetry = RecordingTelemetry(),
        mealPicks = FakeMealPicks(),
        scanEnabled = scanFlag(false),
        photoEnabled = photoFlag(false),
        voice = DescribeVoice(FakeSpeechToText(), voiceFlag(false), RecordingVoiceTelemetry()),
        searchOptionsEnabled = searchOptionsFlag(false),
        optionsTelemetry = RecordingSearchOptionsTelemetry(),
    )

    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    /** A sheet on Describe with "2 eggs and toast" read into lines. */
    private fun TestScope.described(text: String = "2 eggs and toast"): LoggerViewModel {
        val vm = vm()
        settle()
        vm.takeAction(LoggerAction.DescribeTyped(text))
        settle()
        return vm
    }

    private fun Display.text(): String? = when (this) {
        is Display.Shown -> text
        is Display.Soft -> text
        Display.Hidden -> null
    }

    private val LoggerViewModel.lines get() = state.describe.lines
    private fun LoggerViewModel.key(name: String) = lines.first { it.name == name }.key
    private fun describedPicks() = basket.basket.value.filter { it.source == PickSource.Describe }

    @Test
    fun aReadPutsEachLineInTheBasketWithItsControls() = runUnitTest {
        val vm = described()

        assertEquals(listOf("Egg, large", "Toast, white"), vm.lines.map { it.name })
        assertEquals(listOf("2 large", "1 slice"), vm.lines.map { it.amount })
        assertEquals(listOf("2 large", "1 slice"), describedPicks().map { it.draft.amountLabel })
        assertTrue(describedPicks().all { it.draft.method == LogMethod.Describe && it.draft.precision == Precision.Estimate })
        assertEquals(2, vm.state.describe.total?.count)
    }

    @Test
    fun moreAddsOneEggAndTouchesNothingElse() = runUnitTest {
        val vm = described()

        vm.takeAction(DescribeReviewAction.MoreTapped(vm.key("Egg, large")))
        settle()

        assertEquals(listOf("3 large", "1 slice"), vm.lines.map { it.amount })
        assertEquals(listOf(234, 80), describedPicks().map { it.draft.kcal?.roundToInt() })
        assertEquals("314 kcal", vm.state.describe.total?.calories?.text())
    }

    @Test
    fun lessTakesHalfASliceAndStopsAtAHalf() = runUnitTest {
        val vm = described()
        val toast = vm.key("Toast, white")

        vm.takeAction(DescribeReviewAction.LessTapped(toast))
        vm.takeAction(DescribeReviewAction.LessTapped(toast))
        settle()

        assertEquals("½ slice", vm.lines.first { it.key == toast }.amount)
        assertEquals(false, vm.lines.first { it.key == toast }.canLess)
        assertEquals(40.0, describedPicks().first { it.key == toast }.draft.kcal ?: 0.0, 0.001)
    }

    @Test
    fun aUnitPickedFromTheMenuChangesTheMeasure() = runUnitTest {
        val vm = described()
        val toast = vm.lines.first { it.name == "Toast, white" }

        vm.takeAction(DescribeReviewAction.UnitPicked(toast.key, toast.units.indexOf("thick slice")))
        settle()

        val line = vm.lines.first { it.key == toast.key }
        assertEquals("thick slice", line.units[line.unitIndex])
        assertEquals("1 thick slice", line.amount)
        assertEquals(39.0, describedPicks().first { it.key == toast.key }.draft.grams)
    }

    @Test
    fun anAlternativeSwapsTheFoodAndTheOldOneCanComeBack() = runUnitTest {
        val vm = described()
        val eggs = vm.key("Egg, large")

        vm.takeAction(DescribeReviewAction.AlternativePicked(eggs, 0))
        settle()

        val line = vm.lines.first { it.key == eggs }
        assertEquals("Egg, scrambled", line.name)
        assertEquals("2 large", line.amount)
        assertEquals("Egg, large", line.alternatives.first())
        assertEquals(FoodRef.Catalog("egg-scrambled"), describedPicks().first { it.key == eggs }.ref)

        vm.takeAction(DescribeReviewAction.AlternativePicked(eggs, 0))
        settle()
        assertEquals("Egg, large", vm.lines.first { it.key == eggs }.name)
    }

    @Test
    fun removeTakesTheLineOutAndARereadDoesNotBringItBack() = runUnitTest {
        val vm = described()

        vm.takeAction(DescribeReviewAction.RemoveTapped(vm.key("Toast, white")))
        settle()
        assertEquals(listOf("Egg, large"), vm.lines.map { it.name })
        assertEquals(1, describedPicks().size)

        vm.takeAction(LoggerAction.DescribeTyped("2 eggs and toast with jam"))
        settle()
        assertEquals(listOf("Egg, large"), vm.lines.map { it.name })
    }

    @Test
    fun editingTheTextKeepsTheLinesThePersonChanged() = runUnitTest {
        val vm = described()
        vm.takeAction(DescribeReviewAction.MoreTapped(vm.key("Egg, large")))
        settle()

        catalog.describeResult = Catching.success(LoggerFixtures.parsed + LoggerFixtures.unmatched)
        vm.takeAction(LoggerAction.DescribeTyped("2 eggs and toast and grandma's kugel"))
        settle()

        assertEquals(listOf("Egg, large", "Toast, white", "grandma's kugel"), vm.lines.map { it.name })
        assertEquals("3 large", vm.lines.first().amount)
        assertEquals(3, describedPicks().size)
    }

    @Test
    fun findAddsAMissedFoodWithoutLeavingDescribe() = runUnitTest {
        val vm = described()

        vm.takeAction(DescribeReviewAction.FindTapped())
        assertNotNull(vm.state.describe.find)
        vm.takeAction(DescribeReviewAction.FindTyped("coffee"))
        assertNull(vm.state.describe.find?.results, "searching")
        settle()
        assertEquals(listOf("Coffee, brewed", "Coffee, latte"), vm.state.describe.find?.results?.map { it.name })

        vm.takeAction(DescribeReviewAction.FindPicked("coffee"))
        settle()

        assertNull(vm.state.describe.find)
        assertEquals(LoggerPane.Describe, vm.state.pane)
        assertEquals(listOf("Egg, large", "Toast, white", "Coffee, brewed"), vm.lines.map { it.name })
        assertEquals(3, describedPicks().size)
    }

    @Test
    fun anAddedFoodSurvivesARereadAndAClearedBox() = runUnitTest {
        val vm = described()
        vm.takeAction(DescribeReviewAction.FindTapped())
        vm.takeAction(DescribeReviewAction.FindTyped("coffee"))
        settle()
        vm.takeAction(DescribeReviewAction.FindPicked("coffee"))
        settle()

        vm.takeAction(LoggerAction.DescribeTyped(""))
        settle()

        assertEquals(listOf("Coffee, brewed"), vm.lines.map { it.name })
        assertEquals(listOf("Coffee, brewed"), describedPicks().map { it.draft.name })
    }

    @Test
    fun findOnALineSwapsThatLine() = runUnitTest {
        val vm = described()
        val toast = vm.key("Toast, white")

        vm.takeAction(DescribeReviewAction.FindTapped(toast))
        assertEquals("Toast, white", vm.state.describe.find?.replacing)
        vm.takeAction(DescribeReviewAction.FindTyped("rice"))
        settle()
        vm.takeAction(DescribeReviewAction.FindPicked("rice"))
        settle()

        assertEquals(listOf("Egg, large", "Jasmine rice, cooked"), vm.lines.map { it.name })
        assertEquals(toast, vm.key("Jasmine rice, cooked"))
        assertEquals("Toast, white", vm.lines.last().alternatives.first())
    }

    @Test
    fun findOnALineTheCatalogDidNotKnowStartsFromItsWords() = runUnitTest {
        catalog.describeResult = Catching.success(listOf(LoggerFixtures.unmatched))
        val vm = described("grandma's kugel")

        vm.takeAction(DescribeReviewAction.FindTapped(vm.key("grandma's kugel")))
        settle()

        assertEquals("grandma's kugel", vm.state.describe.find?.query)
        assertEquals(listOf("grandma's kugel"), catalog.searches)
    }

    @Test
    fun closingFindKeepsEveryLine() = runUnitTest {
        val vm = described()
        vm.takeAction(DescribeReviewAction.FindTapped())
        vm.takeAction(DescribeReviewAction.FindTyped("coffee"))

        vm.takeAction(DescribeReviewAction.FindClosed)
        settle()

        assertNull(vm.state.describe.find)
        assertEquals(listOf("Egg, large", "Toast, white"), vm.lines.map { it.name })
        assertTrue(catalog.searches.isEmpty(), "the search waiting on its debounce was dropped")
    }

    @Test
    fun withCaloriesHiddenNoLineIsWeighed() = runUnitTest {
        policySource.policy.value = policy(PresetId.Blind)
        catalog.describeResult = Catching.success(listOf(LoggerFixtures.parsed[0].copy(portionLabel = null)))
        val vm = described("2 eggs")

        assertEquals("2 large", vm.lines.single().amount)
        assertTrue("grams" !in vm.lines.single().units)
    }

    @Test
    fun addLogsTheLinesAsCorrected() = runUnitTest {
        val vm = described()
        vm.takeAction(DescribeReviewAction.MoreTapped(vm.key("Egg, large")))
        vm.takeAction(DescribeReviewAction.RemoveTapped(vm.key("Toast, white")))
        settle()

        vm.takeAction(LoggerAction.AddToMealTapped)
        settle()

        val logged = diary.added.single()
        assertEquals(MealSlot.Breakfast, logged.slot)
        assertEquals(listOf("Egg, large"), logged.items.map { it.name })
        assertEquals(listOf(150.0), logged.items.map { it.grams })
        assertTrue(basket.basket.value.isEmpty())
    }

    @Test
    fun backDropsTheReview() = runUnitTest {
        val vm = described()
        vm.takeAction(DescribeReviewAction.FindTapped())

        vm.takeAction(LoggerAction.BackTapped)
        settle()

        assertEquals(LoggerPane.Search, vm.state.pane)
        assertTrue(describedPicks().isEmpty())
        vm.takeAction(LoggerAction.DescribeItTapped)
        assertTrue(vm.lines.isEmpty())
        assertNull(vm.state.describe.find)
    }
}
