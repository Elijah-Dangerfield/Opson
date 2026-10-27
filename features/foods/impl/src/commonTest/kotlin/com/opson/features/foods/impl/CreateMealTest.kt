package com.opson.features.foods.impl

import com.opson.features.foods.CreateMealRoute
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.PickedForMeal
import com.opson.libraries.diary.Precision
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.foods_meal_saved
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Create a meal: [CreateMealStateBuilder]'s totals and visibility, and [CreateMealViewModel]'s actions. */
class CreateMealTest : CoroutineTest() {
    private val breakfast = listOf(FoodsFixtures.porridge, FoodsFixtures.banana).mapIndexed { index, draft ->
        LogItem(
            id = "b-$index",
            day = FoodsFixtures.today,
            slot = MealSlot.Breakfast,
            loggedAt = Instant.parse("2026-10-25T08:00:00Z"),
            name = draft.name,
            precision = draft.precision,
            method = LogMethod.Search,
            foodId = draft.foodId,
            amountText = draft.amountText,
            kcal = draft.kcal,
            protein = draft.protein,
            carbs = draft.carbs,
            fat = draft.fat,
        )
    }
    private val diary = FakeDiaryRepository(
        listOf(
            Day(
                date = FoodsFixtures.today,
                itemsBySlot = MealSlot.entries.associateWith { slot ->
                    when (slot) {
                        MealSlot.Breakfast -> breakfast
                        MealSlot.Uncategorized -> breakfast.map { it.copy(slot = slot) }
                        else -> emptyList()
                    }
                },
            ),
        ),
    )
    private val savedMeals = FakeSavedMealRepository()
    private val mealPicks = FakeMealPicks()
    private val policySource = FakePolicySource()

    private fun vm(route: CreateMealRoute = CreateMealRoute(), prefillName: String? = null) =
        CreateMealViewModel(route, prefillName, savedMeals, mealPicks, diary, policySource)

    private fun TestScope.events(vm: CreateMealViewModel): List<CreateMealEvent> {
        val events = mutableListOf<CreateMealEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    @Test
    fun theTotalAddsUpEveryFoodThroughThePolicy() {
        val state = FoodsFixtures.createMealState(policy(PresetId.Full))

        assertEquals(listOf("300", "105", "40"), state.items.map { (it.calories as Display.Shown).text })
        assertEquals("445 kcal", (state.total as Display.Shown).text)
        assertEquals(listOf("13P", "80C", "8F"), state.totalMacros.map { it.text })
        assertTrue(state.canSave)
    }

    @Test
    fun blindHidesEveryAmountAndEveryMacro() {
        val state = FoodsFixtures.createMealState(policy(PresetId.NoNumbers))

        assertTrue(state.items.all { it.calories == Display.Hidden })
        assertEquals(Display.Hidden, state.total)
        assertTrue(state.totalMacros.isEmpty())
    }

    @Test
    fun nameOnlyFoodsGiveNoTotalAndNoMacroLine() {
        val named = LogDraft(name = "Lola's soup", precision = Precision.Named)
        val state = CreateMealStateBuilder.build(MealDraft(name = "Soup night", items = listOf(named, named)), policy(PresetId.Full))

        assertEquals(Display.Hidden, state.total)
        assertTrue(state.totalMacros.isEmpty(), "not 0P 0C 0F")
    }

    @Test
    fun saveNeedsANameAndAFood() {
        val full = policy(PresetId.Full)
        assertFalse(CreateMealStateBuilder.build(MealDraft(name = "  ", items = listOf(FoodsFixtures.banana)), full).canSave)
        assertFalse(CreateMealStateBuilder.build(MealDraft(name = "Snack"), full).canSave)
        assertTrue(CreateMealStateBuilder.build(MealDraft(name = "Snack", items = listOf(FoodsFixtures.banana)), full).canSave)
    }

    @Test
    fun fromTodayTheMealArrivesFilledInOnce() = runUnitTest {
        val vm = vm(CreateMealRoute(FoodsFixtures.today.toString(), MealSlot.Breakfast), prefillName = "My breakfast")

        assertEquals("My breakfast", vm.state.name)
        assertEquals(MealSlot.Breakfast, vm.state.usualSlot)
        assertEquals(listOf("Oat porridge with milk", "Banana"), vm.state.items.map { it.name })

        vm.takeAction(CreateMealAction.NameTyped("Weekday breakfast"))
        assertEquals("Weekday breakfast", vm.state.name, "typing is not overwritten")
    }

    /** Breakfast's two foods and one lunch, with no Uncategorized copies, as Today's select mode sees a day. */
    private fun selectVm(entryIds: List<String>, slot: MealSlot? = null, prefillName: String? = null): CreateMealViewModel {
        val lunch = breakfast.first().copy(id = "l-0", slot = MealSlot.Lunch, name = "Chicken adobo")
        val day = Day(
            date = FoodsFixtures.today,
            itemsBySlot = mapOf(MealSlot.Breakfast to breakfast, MealSlot.Lunch to listOf(lunch)),
        )
        val route = CreateMealRoute(FoodsFixtures.today.toString(), slot, entryIds)
        return CreateMealViewModel(route, prefillName, savedMeals, mealPicks, FakeDiaryRepository(listOf(day)), policySource)
    }

    @Test
    fun fromTodaysSelectionOnlyThePickedEntriesArriveInTheDaysOrder() = runUnitTest {
        val vm = selectVm(entryIds = listOf("l-0", "b-1"))

        assertEquals(listOf("Banana", "Chicken adobo"), vm.state.items.map { it.name })
        assertEquals("", vm.state.name, "foods from two meals give no meal to name it after")
        assertEquals(MealSlot.Lunch, vm.state.usualSlot)
    }

    @Test
    fun selectedEntriesFromOneMealAreNamedAndUsuallyAtThatMeal() = runUnitTest {
        val vm = selectVm(entryIds = listOf("b-1"), slot = MealSlot.Breakfast, prefillName = "My breakfast")

        assertEquals(listOf("Banana"), vm.state.items.map { it.name }, "not the whole breakfast")
        assertEquals("My breakfast", vm.state.name)
        assertEquals(MealSlot.Breakfast, vm.state.usualSlot)
    }

    @Test
    fun anUncategorizedPrefillIsUsuallyAtLunch() = runUnitTest {
        val vm = vm(CreateMealRoute(FoodsFixtures.today.toString(), MealSlot.Uncategorized), prefillName = "My meal")

        assertEquals(MealSlot.Lunch, vm.state.usualSlot)
        assertEquals(2, vm.state.items.size)
    }

    @Test
    fun aFreshMealStartsEmptyAtLunch() = runUnitTest {
        val vm = vm()

        assertEquals("", vm.state.name)
        assertEquals(MealSlot.Lunch, vm.state.usualSlot)
        assertTrue(vm.state.items.isEmpty())
        assertFalse(vm.state.canSave)
    }

    @Test
    fun addAFoodOpensTheLoggerAndItsPicksComeBackOnce() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(CreateMealAction.AddFoodTapped)
        val requestId = assertIs<CreateMealEvent.OpenPicker>(events.single()).requestId

        mealPicks.picked(PickedForMeal("someone-else", listOf(FoodsFixtures.coffee)))
        assertTrue(vm.state.items.isEmpty(), "another screen's picks are not this meal's")

        mealPicks.picked(PickedForMeal(requestId, listOf(FoodsFixtures.porridge, FoodsFixtures.banana)))
        assertEquals(listOf("Oat porridge with milk", "Banana"), vm.state.items.map { it.name })
        assertNull(mealPicks.latest.value, "consumed, so it is not added again")

        mealPicks.picked(PickedForMeal(requestId, listOf(FoodsFixtures.coffee)))
        assertEquals(3, vm.state.items.size, "a second trip to the logger adds to the first")
    }

    @Test
    fun usualSlotAndRemove() = runUnitTest {
        val vm = vm(CreateMealRoute(FoodsFixtures.today.toString(), MealSlot.Breakfast), prefillName = "My breakfast")

        vm.takeAction(CreateMealAction.UsualSlotPicked(MealSlot.Snack))
        vm.takeAction(CreateMealAction.ItemRemoveTapped(0))

        assertEquals(MealSlot.Snack, vm.state.usualSlot)
        assertEquals(listOf("Banana"), vm.state.items.map { it.name })
    }

    @Test
    fun aSecondSaveTapBeforeTheScreenClosesSavesNothing() = runUnitTest {
        val vm = vm(CreateMealRoute(FoodsFixtures.today.toString(), MealSlot.Breakfast), prefillName = "My breakfast")
        val events = events(vm)

        vm.takeAction(CreateMealAction.SaveTapped)
        vm.takeAction(CreateMealAction.SaveTapped)

        assertEquals(1, savedMeals.created.size)
        assertEquals(1, events.count { it == CreateMealEvent.Close })
    }

    @Test
    fun saveWritesTheMealSaysSoAndCloses() = runUnitTest {
        val vm = vm(CreateMealRoute(FoodsFixtures.today.toString(), MealSlot.Breakfast), prefillName = "My breakfast")
        val events = events(vm)
        vm.takeAction(CreateMealAction.NameTyped("  Usual breakfast "))

        vm.takeAction(CreateMealAction.SaveTapped)

        val saved = savedMeals.created.single()
        assertEquals("Usual breakfast", saved.name)
        assertEquals(MealSlot.Breakfast, saved.usualSlot)
        assertEquals(listOf("Oat porridge with milk", "Banana"), saved.items.map { it.name })
        assertEquals(listOf(CreateMealEvent.ShowToast(Toast(Res.string.foods_meal_saved)), CreateMealEvent.Close), events)
    }

    @Test
    fun saveDoesNothingWithoutANameOrAFood() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(CreateMealAction.SaveTapped)
        vm.takeAction(CreateMealAction.NameTyped("Snack"))
        vm.takeAction(CreateMealAction.SaveTapped)

        assertTrue(savedMeals.created.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun closeCloses() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(CreateMealAction.CloseTapped)

        assertEquals(listOf<CreateMealEvent>(CreateMealEvent.Close), events)
    }
}
