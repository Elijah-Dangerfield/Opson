package com.opson.features.foods.impl

import com.opson.features.foods.EditCopyRoute
import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.core.fixed
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.foods_copy_saved
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Edit a copy (design sync 4, 13.3e): what it opens with, the "Was" hints, and what Save writes. */
class EditCopyTest : CoroutineTest() {
    private val entry = FoodsFixtures.peanutButter
    private val diary = FakeDiaryRepository(listOf(Day(date = FoodsFixtures.today, itemsBySlot = mapOf(MealSlot.Snack to listOf(entry)))))
    private val customFoods = FakeCustomFoodRepository()

    private fun vm(preset: PresetId = PresetId.Full, entryId: String = entry.id) = EditCopyViewModel(
        route = EditCopyRoute(FoodsFixtures.today.toString(), entryId),
        mine = "(mine)",
        diary = diary,
        customFoods = customFoods,
        calendar = FakeCalendar(),
        policySource = FakePolicySource(preset),
    )

    private fun TestScope.events(vm: EditCopyViewModel): List<EditCopyEvent> {
        val events = mutableListOf<EditCopyEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    @Test
    fun opensWithTheFoodsDetails_namedMine_andUseForThisEntryOn() = runUnitTest {
        val state = vm().state

        assertEquals("Peanut butter (mine)", state.name)
        assertEquals("2 tbsp = 32 g", state.serving)
        assertEquals(listOf("190", "8", "7", "16", "2"), state.fields.map { it.text })
        assertTrue(state.fields.all { it.was == null }, "nothing changed yet")
        assertTrue(state.useForEntry)
        assertEquals(MealSlot.Snack, state.slot)
        assertTrue(state.isToday)
    }

    @Test
    fun aChangedNumber_saysWhatItWas() = runUnitTest {
        val vm = vm()

        vm.takeAction(EditCopyAction.NutritionTyped(Metric.Calories, "180"))
        vm.takeAction(EditCopyAction.NutritionTyped(Metric.Protein, "7"))

        val was = vm.state.fields.associate { it.metric to it.was?.text() }
        assertEquals("190", was[Metric.Calories])
        assertEquals("8 g", was[Metric.Protein])
        assertNull(was[Metric.Carbs])
    }

    @Test
    fun save_makesTheCopy_pointsTheEntryAtIt_andLeavesTheOriginalAlone() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        vm.takeAction(EditCopyAction.NutritionTyped(Metric.Calories, "180"))
        vm.takeAction(EditCopyAction.BrandTyped("Store brand"))

        vm.takeAction(EditCopyAction.SaveTapped)
        vm.takeAction(EditCopyAction.SaveTapped)

        val draft = customFoods.created.single()
        assertEquals("Peanut butter (mine)", draft.name)
        assertEquals("Store brand", draft.brand)
        assertEquals(2.0, draft.servingAmount)
        assertEquals("tbsp", draft.servingUnit)
        assertEquals(180.0, draft.kcal)
        assertEquals(8.0, draft.protein)
        val switched = diary.updated.single()
        assertEquals(entry.id, switched.id)
        assertEquals("made-1", switched.customFoodId)
        assertNull(switched.foodId)
        assertEquals("Peanut butter (mine)", switched.name)
        assertEquals(180.0, switched.kcal)
        assertEquals(entry.loggedAt, switched.loggedAt)
        assertEquals(entry.amountText, switched.amountText)
        assertTrue(customFoods.changed.isEmpty(), "no food of theirs was rewritten")
        assertEquals(Res.string.foods_copy_saved, assertIs<EditCopyEvent.ShowToast>(events.first()).toast.message)
        assertEquals(EditCopyEvent.Close, events.last())
    }

    @Test
    fun useForThisEntryOff_savesOnlyTheCopy() = runUnitTest {
        val vm = vm()

        vm.takeAction(EditCopyAction.UseForEntryToggled)
        vm.takeAction(EditCopyAction.SaveTapped)

        assertEquals(1, customFoods.created.size)
        assertTrue(diary.updated.isEmpty())
    }

    @Test
    fun aCopyOfTheirOwnFood_keepsItsBrand_andNeverChangesIt() = runUnitTest {
        val own = CustomFood(id = "granola", name = "Granola", servingAmount = 1.0, servingUnit = "cup", brand = "Corner bakery", kcal = 400.0)
        customFoods.foods.value = listOf(own)
        val fromOwn = entry.copy(id = "g-1", name = "Granola", foodId = null, customFoodId = own.id)
        val diary = FakeDiaryRepository(listOf(Day(date = FoodsFixtures.today, itemsBySlot = mapOf(MealSlot.Breakfast to listOf(fromOwn)))))
        val vm = EditCopyViewModel(EditCopyRoute(FoodsFixtures.today.toString(), "g-1"), "(mine)", diary, customFoods, FakeCalendar(), FakePolicySource())

        assertEquals("Corner bakery", vm.state.brand)
        vm.takeAction(EditCopyAction.SaveTapped)
        assertEquals(listOf(own, customFoods.foods.value.last()), customFoods.foods.value)
        assertTrue(customFoods.changed.isEmpty())
    }

    @Test
    fun calorieHidden_onlyShownNumbersAreAsked_andTheRestCopyUnseen() = runUnitTest {
        val hidden = VisibilityPolicy(NumbersSettings(overrides = mapOf(Metric.Calories to Visibility.Hide)), Clock.fixed(NOON), TimeZone.UTC)
        val vm = EditCopyViewModel(
            EditCopyRoute(FoodsFixtures.today.toString(), entry.id),
            "(mine)",
            diary,
            customFoods,
            FakeCalendar(),
            object : VisibilityPolicySource {
                override val policy = MutableStateFlow(hidden)
            },
        )

        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber), vm.state.fields.map { it.metric })
        vm.takeAction(EditCopyAction.NutritionTyped(Metric.Protein, "9"))
        vm.takeAction(EditCopyAction.SaveTapped)

        val draft = customFoods.created.single()
        assertEquals(9.0, draft.protein)
        assertEquals(190.0, draft.kcal, "calories copied as they were, never shown")
    }

    @Test
    fun macrosWithGrams_asksTheGrams_neverTheCalories() = runUnitTest {
        val vm = vm(PresetId.MacrosWithGrams)

        assertTrue(vm.state.fields.isNotEmpty())
        assertTrue(vm.state.fields.none { it.metric == Metric.Calories })
    }

    @Test
    fun macrosOnly_softWordsAreNoPlaceForANumber_soNoneIsAsked() = runUnitTest {
        val vm = vm(PresetId.MacrosOnly)

        assertTrue(vm.state.fields.isEmpty())
        vm.takeAction(EditCopyAction.SaveTapped)
        assertEquals(16.0, customFoods.created.single().fat, "everything copied as it was")
    }

    @Test
    fun inBlind_noNumberIsShown() = runUnitTest {
        assertTrue(vm(PresetId.NoNumbers).state.fields.isEmpty())
    }

    @Test
    fun aBlankShownField_isUnknown_notZero() = runUnitTest {
        val vm = vm()

        vm.takeAction(EditCopyAction.NutritionTyped(Metric.Fiber, ""))
        vm.takeAction(EditCopyAction.SaveTapped)

        assertNull(customFoods.created.single().fiber)
    }

    @Test
    fun noName_noSave() = runUnitTest {
        val vm = vm()

        vm.takeAction(EditCopyAction.NameTyped("  "))
        vm.takeAction(EditCopyAction.SaveTapped)

        assertTrue(customFoods.created.isEmpty())
    }

    @Test
    fun anEntryGone_closes() = runUnitTest {
        val vm = vm(entryId = "nothing")
        val events = events(vm)

        assertTrue(vm.state.loading)
        assertEquals(listOf<EditCopyEvent>(EditCopyEvent.Close), events)
    }

    private fun Display.text(): String? = when (this) {
        is Display.Shown -> text
        is Display.Soft -> text
        Display.Hidden -> null
    }
}
