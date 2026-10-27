package com.opson.features.foods.impl

import com.opson.features.logger.FoodSearchRoute
import com.opson.features.logger.LoggerRoute
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The Foods tab: [FoodsStateBuilder]'s sections and visibility, and [FoodsViewModel]'s actions. */
class FoodsTabTest : CoroutineTest() {
    private val diary = FakeDiaryRepository().apply { recent.value = FoodsFixtures.recent }
    private val customFoods = FakeCustomFoodRepository(listOf(FoodsFixtures.pandesal))
    private val savedMeals = FakeSavedMealRepository(FoodsFixtures.savedMeals)
    private val policySource = FakePolicySource()

    private fun vm() = FoodsViewModel(customFoods, savedMeals, diary, policySource)

    private fun TestScope.events(vm: FoodsViewModel): List<FoodsEvent> {
        val events = mutableListOf<FoodsEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    private fun texts(rows: List<FoodsRowUi>) = rows.map { (it.calories as? Display.Shown)?.text }

    @Test
    fun savedMealsComeFirstThenYourFoodsThenRecent() {
        val state = FoodsFixtures.foodsState(policy(PresetId.Full))

        assertFalse(state.loading)
        assertEquals(listOf("Usual breakfast", "Work lunch", "Yogurt and fruit"), state.savedMeals.map { it.name })
        assertEquals("Oat porridge with milk, Banana, Coffee with milk", state.savedMeals.first().sub)
        assertEquals(listOf("445", "515", "170"), texts(state.savedMeals), "bare, as v3 draws a row")
        assertEquals(listOf("Corner bakery · 1 roll"), state.yourFoods.map { it.sub })
        assertEquals(listOf("120"), texts(state.yourFoods))
        assertEquals(listOf("Jasmine rice", "Banana", "Coffee with milk"), state.recent.map { it.name })
    }

    @Test
    fun everyHidingModeHidesEveryRowsCalories() {
        listOf(PresetId.NoNumbers, PresetId.NutrientsFirst, PresetId.NoNumbers, PresetId.NoNumbers, PresetId.NoNumbers).forEach { preset ->
            val state = FoodsFixtures.foodsState(policy(preset))
            val rows = state.savedMeals + state.yourFoods + state.recent
            assertTrue(rows.all { it.calories == Display.Hidden }, "$preset")
        }
    }

    @Test
    fun aSoftenedModeHasNoWordForARowAndHidesIt() {
        val rows = FoodsFixtures.foodsState(policy(PresetId.NutrientsFirst)).savedMeals
        assertTrue(rows.all { it.calories == Display.Hidden })
    }

    @Test
    fun delayedOpensAtTheRevealTime() {
        assertTrue(FoodsFixtures.foodsState(policy(PresetId.Later)).savedMeals.all { it.calories == Display.Hidden })
        val evening = FoodsFixtures.foodsState(policy(PresetId.Later, at = Instant.parse("2026-10-25T21:30:00Z")))
        assertEquals("445", (evening.savedMeals.first().calories as Display.Shown).text)
    }

    @Test
    fun aRecentItemWithNoFoodBehindItIsLeftOutAndRepeatsCollapse() {
        val quick = LogItem(
            id = "quick",
            day = FoodsFixtures.today,
            slot = MealSlot.Snack,
            loggedAt = Instant.parse("2026-10-25T10:00:00Z"),
            name = "Quick add",
            precision = Precision.Named,
            method = LogMethod.QuickNumber,
            kcal = 200.0,
        )
        val inputs = FoodsInputs(emptyList(), emptyList(), listOf(quick) + FoodsFixtures.recent + FoodsFixtures.recent.first())

        val recent = FoodsStateBuilder.build(inputs, policy(PresetId.Full)).recent

        assertEquals(listOf("f:rice", "f:banana", "f:coffee"), recent.map { it.key })
    }

    @Test
    fun theViewModelBuildsFromWhatItReadsAndFollowsTheMode() = runUnitTest {
        val vm = vm()
        assertEquals(3, vm.state.savedMeals.size)
        assertEquals(1, vm.state.yourFoods.size)

        policySource.policy.value = policy(PresetId.NoNumbers)

        assertEquals(Display.Hidden, vm.state.savedMeals.first().calories)
    }

    @Test
    fun searchOpensTheLoggerWithSearchFocused() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(FoodsAction.SearchTapped)

        assertEquals(listOf<FoodsEvent>(FoodsEvent.OpenLogger(FoodSearchRoute())), events)
    }

    @Test
    fun theTilesOpenCreateAMealAndAddAFood() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(FoodsAction.CreateMealTapped)
        vm.takeAction(FoodsAction.AddFoodTapped)

        assertEquals(listOf(FoodsEvent.OpenCreateMeal, FoodsEvent.OpenAddFood), events)
    }

    @Test
    fun aRowOpensTheLoggerWithItPicked() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(FoodsAction.RowTapped(vm.state.savedMeals.first().target))
        vm.takeAction(FoodsAction.RowTapped(vm.state.yourFoods.first().target))
        vm.takeAction(FoodsAction.RowTapped(vm.state.recent.first().target))

        assertEquals(
            listOf<FoodsEvent>(
                FoodsEvent.OpenLogger(LoggerRoute(prePickSavedMealId = "usual-breakfast")),
                FoodsEvent.OpenLogger(LoggerRoute(prePickFoodId = "pandesal", prePickIsCustom = true)),
                FoodsEvent.OpenLogger(LoggerRoute(prePickFoodId = "rice", prePickIsCustom = false)),
            ),
            events,
        )
        assertEquals(FoodsTarget.Food(FoodRef.Custom("pandesal")), vm.state.yourFoods.first().target)
    }
}
