package com.opson.features.logger.impl

import com.opson.libraries.core.fixed
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.FoodAmount
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.toLogDraft
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.TimeZone
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_toast_stop_asking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The amount sheet: [AmountStateBuilder]'s tiles at each level, and
 * [AmountViewModel] one action at a time. The stepper's arithmetic is
 * `PortionAmountTest`'s in `:libraries:foods`.
 */
class AmountTest : CoroutineTest() {
    private val catalog = FakeFoodCatalog(foods = listOf(LoggerFixtures.banana, LoggerFixtures.yogurt, LoggerFixtures.arroz))
    private val customFoods = FakeCustomFoodRepository(listOf(LoggerFixtures.granola))
    private val settings = FakeSettingsRepository()
    private val policySource = FakePolicySource()
    private val basket = BasketStore()
    private val diary = FakeDiaryRepository()
    private val recentLogEvents = FakeRecentLogEvents()
    private val telemetry = RecordingTelemetry()

    private fun vm(foodId: String = "banana", custom: Boolean = false, source: PickSource = PickSource.Search) = AmountViewModel(
        AmountSheetRoute(foodId, custom, source, LoggerFixtures.today.toString(), MealSlot.Dinner),
        catalog,
        customFoods,
        settings,
        policySource,
        basket,
        BasketCommitter(diary, catalog, basket, recentLogEvents, telemetry, Clock.fixed(NOON)),
    )

    private fun TestScope.events(vm: AmountViewModel): List<AmountEvent> {
        val events = mutableListOf<AmountEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    private fun build(policy: VisibilityPolicy, choice: AmountChoice = AmountChoice()) =
        AmountStateBuilder.build(
            AmountFood.Catalog(LoggerFixtures.arroz),
            choice,
            Settings(),
            policy,
            FoodRef.Catalog("arroz"),
            PickSource.Search,
            MealSlot.Dinner,
        )

    private fun Display.text(): String? = (this as? Display.Shown)?.text

    @Test
    fun fullShowsEnergyAndTheThreeMacros() {
        val state = build(policy(PresetId.Full))

        assertEquals(listOf(Metric.Calories, Metric.Protein, Metric.Carbs, Metric.Fat), state.nutrition.map { it.metric })
        assertEquals("525 kcal", state.nutrition.first().display.text())
        assertEquals("Latin American", state.tag)
        assertEquals("plate", state.perUnit)
        assertEquals(listOf("plate", "cup", "grams"), state.units)
        assertEquals("1 plate", state.amountText)
        assertEquals(MealSlot.Dinner, state.slot)
    }

    @Test
    fun aHiddenMacroDropsItsTile() {
        val noCarbs = VisibilityPolicy(
            NumbersSettings(PresetId.Full, overrides = mapOf(Metric.Carbs to Visibility.Hide)),
            Clock.fixed(NOON),
            TimeZone.UTC,
        )

        assertEquals(listOf(Metric.Calories, Metric.Protein, Metric.Fat), build(noCarbs).nutrition.map { it.metric })
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
        assertEquals(listOf("package", "grams"), state.units)
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
        assertEquals("1.5 medium", vm.state.amountText)
        vm.takeAction(AmountAction.LessTapped)
        vm.takeAction(AmountAction.LessTapped)
        assertEquals("0.5 medium", vm.state.amountText)
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
    fun hungerSelectsAndClears() = runUnitTest {
        val vm = vm()

        vm.takeAction(AmountAction.HungerTapped(Hunger.Peckish))
        assertEquals(Hunger.Peckish, vm.state.hunger)

        vm.takeAction(AmountAction.HungerTapped(Hunger.Peckish))
        assertNull(vm.state.hunger)
    }

    @Test
    fun stopAskingTurnsTheQuestionOffForGood() = runUnitTest {
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
    fun pickAndKeepGoingReplacesTheBasketEntryAndGoesBack() = runUnitTest {
        val banana = LoggerFixtures.banana
        basket.upsert(BasketItem("f:banana", FoodRef.Catalog("banana"), banana.toLogDraft(FoodAmount.Servings(1.0)), PickSource.Usual, banana))
        val vm = vm(source = PickSource.Usual)
        val events = events(vm)

        vm.takeAction(AmountAction.MoreTapped)
        vm.takeAction(AmountAction.HungerTapped(Hunger.VeryHungry))
        vm.takeAction(AmountAction.PickAndKeepGoingTapped)

        val item = basket.basket.value.single()
        assertEquals("1.5 × 1 medium", item.draft.amountText)
        assertEquals(157.5, item.draft.kcal!!, 0.1)
        assertEquals(LogMethod.Favorite, item.draft.method)
        assertEquals(Hunger.VeryHungry, item.hunger)
        assertEquals(banana, item.food)
        assertTrue(diary.added.isEmpty(), "keep going logs nothing yet")
        assertEquals(listOf<AmountEvent>(AmountEvent.Dismiss), events)
    }

    @Test
    fun addToMealPicksThenLogsTheWholeBasket() = runUnitTest {
        settings.state.value = Settings(askTags = false)
        basket.upsert(LoggerFixtures.pickedBasket().first())
        val vm = vm()
        val events = events(vm)

        vm.takeAction(AmountAction.AddToMealTapped)

        val added = diary.added.single()
        assertEquals(MealSlot.Dinner, added.slot)
        assertEquals(listOf("Arroz con pollo", "Banana"), added.items.map { it.name })
        assertTrue(basket.basket.value.isEmpty())
        val logged = recentLogEvents.logged.single()
        val event = assertIs<AmountEvent.Logged>(events.single())
        assertEquals(LoggerFixtures.today, event.day)
        assertEquals(Committed(MealSlot.Dinner, logged.itemIds, askHowItFelt = false), event.committed)
    }

    @Test
    fun editingADescribedLineReplacesThatLine() = runUnitTest {
        val line = LoggerFixtures.parsed.first().toDescribedItem(0).copy(ref = FoodRef.Catalog("banana"), key = "f:banana")
        basket.upsert(line)
        val vm = vm(source = PickSource.Describe)

        vm.takeAction(AmountAction.PickAndKeepGoingTapped)

        val item = basket.basket.value.single()
        assertEquals(PickSource.Describe, item.source)
        assertEquals(LogMethod.Describe, item.draft.method)
    }

    @Test
    fun reopeningAPickedFoodStartsFromThePick() = runUnitTest {
        val ref = FoodRef.Catalog("banana")
        val choice = AmountChoice(Precision.Weighed, unitIndex = 1, quantity = 150.0)
        val draft = AmountFood.Catalog(LoggerFixtures.banana).draftFor(choice, PickSource.Search, ref)
        basket.upsert(BasketItem(key = "f:banana", ref = ref, draft = draft, source = PickSource.Search, hunger = Hunger.Hungry))

        val state = vm().state

        assertEquals("150 g", state.amountText)
        assertEquals(1, state.unitIndex)
        assertEquals(Precision.Weighed, state.precision)
        assertEquals(Hunger.Hungry, state.hunger)
    }

    @Test
    fun aNameOnlyPickLogsNoNumbers() = runUnitTest {
        val vm = vm()

        vm.takeAction(AmountAction.PrecisionPicked(Precision.Named))
        vm.takeAction(AmountAction.PickAndKeepGoingTapped)

        val draft = basket.basket.value.single().draft
        assertEquals(Precision.Named, draft.precision)
        assertNull(draft.kcal)
        assertNull(draft.amountText)
        assertEquals("banana", draft.foodId)
    }

    @Test
    fun aCustomFoodStepsInItsOwnUnit() = runUnitTest {
        val vm = vm("granola", custom = true)

        assertEquals(listOf("grams"), vm.state.units)
        assertEquals("45 g", vm.state.amountText)
        vm.takeAction(AmountAction.MoreTapped)
        assertEquals("55 g", vm.state.amountText)
    }

    @Test
    fun backLeavesTheBasketAlone() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(AmountAction.BackTapped)

        assertTrue(basket.basket.value.isEmpty())
        assertEquals(listOf<AmountEvent>(AmountEvent.Dismiss), events)
    }
}
