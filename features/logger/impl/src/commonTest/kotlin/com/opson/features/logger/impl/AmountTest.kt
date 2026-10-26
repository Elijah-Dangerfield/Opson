package com.opson.features.logger.impl

import com.opson.libraries.diary.Fullness
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.core.fixed
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.TimeZone
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_toast_stop_asking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The amount sheet: [AmountStateBuilder]'s tiles at each level, and
 * [AmountViewModel] one action at a time. The stepper's arithmetic is
 * `PortionAmountTest`'s in `:libraries:foods`.
 */
class AmountTest : CoroutineTest() {
    private val catalog = FakeFoodCatalog(foods = listOf(LoggerFixtures.banana, LoggerFixtures.yogurt))
    private val customFoods = FakeCustomFoodRepository(listOf(LoggerFixtures.granola))
    private val settings = FakeSettingsRepository()
    private val policySource = FakePolicySource()
    private val basket = BasketStore()

    private fun vm(foodId: String = "banana", custom: Boolean = false, source: PickSource = PickSource.Search) =
        AmountViewModel(AmountSheetRoute(foodId, custom, source), catalog, customFoods, settings, policySource, basket)

    private fun TestScope.events(vm: AmountViewModel): List<AmountEvent> {
        val events = mutableListOf<AmountEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    private fun build(policy: VisibilityPolicy, choice: AmountChoice = AmountChoice(), settings: Settings = Settings()) =
        AmountStateBuilder.build(
            AmountFood.Catalog(LoggerFixtures.yogurt),
            choice,
            settings,
            policy,
            FoodRef.Catalog("yogurt"),
            PickSource.Search,
        )

    private fun Display.text(): String? = (this as? Display.Shown)?.text

    @Test
    fun fullShowsEnergyProteinAndFiberAndKeepsCalciumHidden() {
        val state = build(policy(PresetId.Full))

        assertEquals(listOf(Metric.Calories, Metric.Protein), state.nutrition.map { it.metric }, "yogurt has no fiber on record")
        assertEquals("100 kcal", state.nutrition.first().display.text())
    }

    @Test
    fun calciumShowsAsAPercentWhenMicrosAreShown() {
        val micros = VisibilityPolicy(
            NumbersSettings(PresetId.Full, overrides = mapOf(Metric.Micros to Visibility.Show)),
            Clock.fixed(NOON),
            TimeZone.UTC,
        )

        val calcium = build(micros).nutrition.last()

        assertEquals(Metric.Micros, calcium.metric)
        assertEquals("19%", calcium.display.text(), "187 mg of a 1,000 mg reference")
    }

    @Test
    fun blindShowsNoTilesAndFuelOnlyTheShownOnes() {
        assertTrue(build(policy(PresetId.Blind)).nutrition.isEmpty())
        assertEquals(listOf(Metric.Protein), build(policy(PresetId.Fuel)).nutrition.map { it.metric })
    }

    @Test
    fun justTheNameHidesTheAmountAndTheNumbers() {
        val state = build(policy(PresetId.Full), AmountChoice(precision = Precision.Named))

        assertFalse(state.showAmount)
        assertTrue(state.nutrition.isEmpty())
    }

    @Test
    fun theFoodArrivesWithItsUnits() = runUnitTest {
        val state = vm("yogurt").state

        assertFalse(state.loading)
        assertEquals("Greek yogurt, plain", state.name)
        assertEquals(listOf("1 package", "1 cup", "g"), state.units)
        assertEquals("1 package", state.amountText)
        assertEquals(Precision.Estimate, state.precision)
    }

    @Test
    fun aFoodThatCannotBeLoadedSaysSo() = runUnitTest {
        val state = vm("not-cached").state

        assertTrue(state.missing)
        assertFalse(state.loading)
    }

    @Test
    fun lessAndMoreStepByAHalf() = runUnitTest {
        val vm = vm()

        vm.takeAction(AmountAction.MoreTapped)
        assertEquals("1.5 × 1 medium", vm.state.amountText)
        vm.takeAction(AmountAction.LessTapped)
        vm.takeAction(AmountAction.LessTapped)
        assertEquals("0.5 × 1 medium", vm.state.amountText)
        assertFalse(vm.state.canLess)
    }

    @Test
    fun switchingUnitsConvertsTheAmount() = runUnitTest {
        val vm = vm()

        vm.takeAction(AmountAction.UnitPicked(1))

        assertEquals("118 g", vm.state.amountText)
        assertEquals(1, vm.state.unitIndex)
    }

    @Test
    fun precisionIsPicked() = runUnitTest {
        val vm = vm()

        vm.takeAction(AmountAction.PrecisionPicked(Precision.Weighed))

        assertEquals(Precision.Weighed, vm.state.precision)
    }

    @Test
    fun hungerAndFullnessSelectAndClear() = runUnitTest {
        val vm = vm()

        vm.takeAction(AmountAction.HungerTapped(Hunger.Peckish))
        vm.takeAction(AmountAction.FullnessTapped(Fullness.Satisfied))
        assertEquals(Hunger.Peckish, vm.state.hunger)
        assertEquals(Fullness.Satisfied, vm.state.fullness)

        vm.takeAction(AmountAction.HungerTapped(Hunger.Peckish))
        assertNull(vm.state.hunger)
    }

    @Test
    fun stopAskingTurnsTheQuestionsOffForGood() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        vm.takeAction(AmountAction.HungerTapped(Hunger.Peckish))

        vm.takeAction(AmountAction.StopAskingTapped)

        assertFalse(settings.state.value.askTags)
        assertFalse(vm.state.askTags)
        assertNull(vm.state.hunger)
        assertEquals(listOf<AmountEvent>(AmountEvent.ShowToast(Toast(Res.string.logger_toast_stop_asking))), events)
    }

    @Test
    fun pickingReplacesTheFoodsBasketEntryAndGoesBack() = runUnitTest {
        basket.upsert(LoggerFixtures.pickedBasket().first())
        val vm = vm(source = PickSource.Favorite)
        val events = events(vm)

        vm.takeAction(AmountAction.MoreTapped)
        vm.takeAction(AmountAction.HungerTapped(Hunger.VeryHungry))
        vm.takeAction(AmountAction.PickTapped)

        val item = basket.basket.value.single()
        assertEquals("1.5 × 1 medium", item.draft.amountText)
        assertEquals(157.5, item.draft.kcal!!, 0.1)
        assertEquals(LogMethod.Favorite, item.draft.method)
        assertEquals(Hunger.VeryHungry, item.hunger)
        assertEquals(LoggerFixtures.banana, item.food)
        assertEquals(listOf<AmountEvent>(AmountEvent.Dismiss), events)
    }

    @Test
    fun reopeningAPickedFoodStartsFromThePick() = runUnitTest {
        val picked = LoggerFixtures.banana.let { food ->
            BasketItem(
                key = "f:banana",
                ref = FoodRef.Catalog("banana"),
                draft = AmountFood.Catalog(food).draftFor(AmountChoice(Precision.Weighed, unitIndex = 1, quantity = 150.0), PickSource.Search, FoodRef.Catalog("banana")),
                source = PickSource.Search,
                fullness = Fullness.VeryFull,
            )
        }
        basket.upsert(picked)

        val state = vm().state

        assertEquals("150 g", state.amountText)
        assertEquals(1, state.unitIndex)
        assertEquals(Precision.Weighed, state.precision)
        assertEquals(Fullness.VeryFull, state.fullness)
    }

    @Test
    fun aNameOnlyPickLogsNoNumbers() = runUnitTest {
        val vm = vm()

        vm.takeAction(AmountAction.PrecisionPicked(Precision.Named))
        vm.takeAction(AmountAction.PickTapped)

        val draft = basket.basket.value.single().draft
        assertEquals(Precision.Named, draft.precision)
        assertNull(draft.kcal)
        assertNull(draft.amountText)
        assertEquals("banana", draft.foodId)
    }

    @Test
    fun aCustomFoodStepsInItsOwnUnit() = runUnitTest {
        val vm = vm("granola", custom = true)

        assertEquals(listOf("g"), vm.state.units)
        assertEquals("45 g", vm.state.amountText)
        vm.takeAction(AmountAction.MoreTapped)
        assertEquals("55 g", vm.state.amountText)
    }

    @Test
    fun closeLeavesTheBasketAlone() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(AmountAction.CloseTapped)

        assertTrue(basket.basket.value.isEmpty())
        assertEquals(listOf<AmountEvent>(AmountEvent.Dismiss), events)
    }
}
