package com.opson.features.foods.impl

import com.opson.libraries.diary.CustomFoodDraft
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.foods_food_saved
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Add a food: which fields each mode asks for, what is saved, and [AddFoodViewModel]'s actions. */
class AddFoodTest : CoroutineTest() {
    private val customFoods = FakeCustomFoodRepository()
    private val policySource = FakePolicySource()

    private fun vm() = AddFoodViewModel(customFoods, policySource)

    private fun TestScope.events(vm: AddFoodViewModel): List<AddFoodEvent> {
        val events = mutableListOf<AddFoodEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    private fun asked(preset: PresetId) = AddFoodStateBuilder.asked(policy(preset))

    @Test
    fun fullAsksForEveryField() {
        assertEquals(listOf(Metric.Calories, Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber), asked(PresetId.Full))
    }

    @Test
    fun nutrientFocusAsksOnlyProteinAndFiber() {
        assertEquals(listOf(Metric.Protein, Metric.Fiber), asked(PresetId.Nutrient))
    }

    @Test
    fun blindAndTheOtherHidingModesAskForNoNumberAtAll() {
        listOf(PresetId.Blind, PresetId.Rhythm, PresetId.Explorer, PresetId.Mindful).forEach { preset ->
            val state = AddFoodStateBuilder.build(FoodsFixtures.foodForm(), policy(preset))
            assertTrue(state.labelOnly, "$preset")
            assertTrue(state.fields.isEmpty(), "$preset")
        }
    }

    @Test
    fun delayedStillAsksForCaloriesSinceTheyAreTheirOwnInput() {
        assertTrue(Metric.Calories in asked(PresetId.Delayed))
    }

    @Test
    fun blankAndUnaskedFieldsSaveAsUnknownNeverZero() {
        val form = FoodForm(
            name = " Lola's pandesal ",
            brand = "  ",
            servingAmount = "",
            nutrition = mapOf(Metric.Calories to "120", Metric.Protein to "", Metric.Fiber to "0"),
        )

        assertEquals(
            CustomFoodDraft(name = "Lola's pandesal", servingAmount = 1.0, servingUnit = "piece", kcal = 120.0, fiber = 0.0),
            AddFoodStateBuilder.draft(form, policy(PresetId.Full)),
        )
        // Calories was typed before the mode changed to Nutrient focus, which does not ask for it.
        assertEquals(null, AddFoodStateBuilder.draft(form, policy(PresetId.Nutrient)).kcal)
        assertEquals(0.0, AddFoodStateBuilder.draft(form, policy(PresetId.Nutrient)).fiber, "a typed 0 is a 0")
    }

    @Test
    fun theFormFollowsTheModeAndKeepsWhatWasTyped() = runUnitTest {
        val vm = vm()
        vm.takeAction(AddFoodAction.NutritionTyped(Metric.Protein, "4"))
        assertEquals(5, vm.state.fields.size)

        policySource.policy.value = policy(PresetId.Nutrient)

        assertEquals(listOf(NutritionField(Metric.Protein, "4"), NutritionField(Metric.Fiber, "")), vm.state.fields)
    }

    @Test
    fun nutrientFocusSavesOnlyProteinAndFiber() = runUnitTest {
        val vm = vm()
        vm.takeAction(AddFoodAction.NameTyped("Lola's pandesal"))
        AddFoodStateBuilder.FIELD_ORDER.forEach { vm.takeAction(AddFoodAction.NutritionTyped(it, "5")) }

        policySource.policy.value = policy(PresetId.Nutrient)
        vm.takeAction(AddFoodAction.SaveTapped)

        assertEquals(
            CustomFoodDraft(name = "Lola's pandesal", servingAmount = 1.0, servingUnit = "piece", protein = 5.0, fiber = 5.0),
            customFoods.created.single(),
        )
    }

    @Test
    fun blindSavesTheNameWithNoNumberEvenOnesTypedBefore() = runUnitTest {
        val vm = vm()
        vm.takeAction(AddFoodAction.NameTyped("Lola's pandesal"))
        vm.takeAction(AddFoodAction.NutritionTyped(Metric.Calories, "120"))

        policySource.policy.value = policy(PresetId.Blind)
        assertTrue(vm.state.labelOnly)
        assertTrue(vm.state.canSave, "a name is enough")
        vm.takeAction(AddFoodAction.SaveTapped)

        assertEquals(
            CustomFoodDraft(name = "Lola's pandesal", servingAmount = 1.0, servingUnit = "piece"),
            customFoods.created.single(),
        )
    }

    @Test
    fun numbersKeepDigitsAndOneDecimalPoint() = runUnitTest {
        val vm = vm()

        vm.takeAction(AddFoodAction.ServingAmountTyped("1,5 cups"))
        vm.takeAction(AddFoodAction.NutritionTyped(Metric.Calories, "12a0.5.5"))

        assertEquals("1.5", vm.state.servingAmount)
        assertEquals("120.55", vm.state.fields.first { it.metric == Metric.Calories }.text)
    }

    @Test
    fun saveNeedsOnlyAName() = runUnitTest {
        val vm = vm()
        assertFalse(vm.state.canSave)

        vm.takeAction(AddFoodAction.NameTyped("Lola's pandesal"))

        assertTrue(vm.state.canSave)
    }

    @Test
    fun saveWritesTheFoodSaysSoAndCloses() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        vm.takeAction(AddFoodAction.NameTyped("  Lola's pandesal "))
        vm.takeAction(AddFoodAction.BrandTyped("Corner bakery"))
        vm.takeAction(AddFoodAction.ServingUnitPicked(ServingUnit.Grams))
        vm.takeAction(AddFoodAction.ServingAmountTyped("60"))
        vm.takeAction(AddFoodAction.NutritionTyped(Metric.Calories, "120"))

        vm.takeAction(AddFoodAction.SaveTapped)

        assertEquals(
            CustomFoodDraft(name = "Lola's pandesal", servingAmount = 60.0, servingUnit = "g", brand = "Corner bakery", kcal = 120.0),
            customFoods.created.single(),
        )
        assertEquals(
            listOf(AddFoodEvent.ShowToast(Toast(Res.string.foods_food_saved, listOf("Lola's pandesal"))), AddFoodEvent.Close),
            events,
        )
    }

    @Test
    fun aSecondSaveTapBeforeTheScreenClosesAddsNothing() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        vm.takeAction(AddFoodAction.NameTyped("Lola's pandesal"))

        vm.takeAction(AddFoodAction.SaveTapped)
        vm.takeAction(AddFoodAction.SaveTapped)

        assertEquals(1, customFoods.created.size)
        assertEquals(1, events.count { it == AddFoodEvent.Close })
    }

    @Test
    fun saveWithoutANameDoesNothing() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(AddFoodAction.SaveTapped)

        assertTrue(customFoods.created.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun closeCloses() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(AddFoodAction.CloseTapped)

        assertEquals(listOf<AddFoodEvent>(AddFoodEvent.Close), events)
    }
}
