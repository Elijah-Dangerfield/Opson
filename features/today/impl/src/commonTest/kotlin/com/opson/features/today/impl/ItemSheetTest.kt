package com.opson.features.today.impl

import com.opson.features.today.impl.item.FoodAction
import com.opson.features.today.impl.item.ItemSheetAction
import com.opson.features.today.impl.item.ItemSheetEvent
import com.opson.features.today.impl.item.ItemSheetViewModel
import com.opson.features.today.impl.item.ItemStep
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_toast_amount_changed
import opson.libraries.resources.generated.resources.today_toast_favorited
import opson.libraries.resources.generated.resources.today_toast_moved_to
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A food's ⋮ (design sync 4, 13.3d): one test per action, and the flag off. */
class ItemSheetTest : CoroutineTest() {
    private val today = TodayFixtures.today
    private val peanutButter = TodayFixtures.peanutButter
    private val diary = FakeDiaryRepository(listOf(TodayFixtures.day()))
    private val settings = FakeSettingsRepository()

    private fun sheet(entryId: String = peanutButter, on: Boolean = true, preset: PresetId = PresetId.Full) =
        ItemSheetViewModel(today, entryId, diary, diary, settings, FakePolicySource(policy(preset)), FakeCalendar(), foodActionsFlag(on))

    private fun TestScope.events(vm: ItemSheetViewModel): List<ItemSheetEvent> {
        val events = mutableListOf<ItemSheetEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    @Test
    fun theHeader_saysAmountPrecisionMealAndEnergy_andOffersEveryAction() = runUnitTest {
        val vm = sheet()

        assertEquals("Peanut butter", vm.state.name)
        assertEquals("2 tbsp", vm.state.amount)
        assertEquals(MealSlot.Snack, vm.state.slot)
        assertEquals("190 kcal", vm.state.calories.text())
        assertEquals(
            listOf(FoodAction.ChangeAmount, FoodAction.Move, FoodAction.CopyToDay, FoodAction.EditCopy, FoodAction.Favorite, FoodAction.Delete),
            vm.state.actions,
        )
    }

    @Test
    fun theHeadersEnergy_followsThePolicy() = runUnitTest {
        assertNull(sheet(preset = PresetId.Blind).state.calories.text())
    }

    @Test
    fun flagOff_offersDeleteAlone_andIgnoresTheRest() = runUnitTest {
        val vm = sheet(on = false)

        assertEquals(listOf(FoodAction.Delete), vm.state.actions)
        vm.takeAction(ItemSheetAction.MoveTapped)
        vm.takeAction(ItemSheetAction.MoveSlotPicked(MealSlot.Dinner))
        vm.takeAction(ItemSheetAction.FavoriteTapped)
        assertEquals(ItemStep.Actions, vm.state.step)
        assertEquals(0, diary.writes)
        assertEquals(Settings(), settings.settings.value)
    }

    @Test
    fun aNameOnlyEntry_cannotChangeAmountOrBeAFavorite() = runUnitTest {
        val vm = sheet(entryId = "banana-$today")

        assertTrue(FoodAction.ChangeAmount !in vm.state.actions, "no quantity to scale")
        assertTrue(FoodAction.Favorite !in vm.state.actions, "no food to star")
    }

    @Test
    fun changeAmount_scalesTheEntry_andCloses() = runUnitTest {
        val vm = sheet()
        val events = events(vm)

        vm.takeAction(ItemSheetAction.ChangeAmountTapped)
        assertEquals(ItemStep.Amount(text = "2", unit = "tbsp", canSave = false), vm.state.step, "the same amount saves nothing")
        vm.takeAction(ItemSheetAction.AmountTyped("3"))
        vm.takeAction(ItemSheetAction.AmountSaved)

        val changed = diary.updated.single()
        assertEquals(3.0, changed.quantity)
        assertEquals(285.0, changed.kcal)
        assertEquals(peanutButter, changed.id)
        assertEquals(Res.string.today_toast_amount_changed, assertIs<ItemSheetEvent.ShowToast>(events.first()).toast.message)
        assertEquals(ItemSheetEvent.Dismiss, events.last())
    }

    @Test
    fun move_pickingAnotherMealMovesIt_theSameMealGoesBack() = runUnitTest {
        val vm = sheet()
        val events = events(vm)

        vm.takeAction(ItemSheetAction.MoveTapped)
        assertEquals(ItemStep.MoveTo(MealSlot.Snack), vm.state.step)
        vm.takeAction(ItemSheetAction.MoveSlotPicked(MealSlot.Snack))
        assertEquals(ItemStep.Actions, vm.state.step)
        assertTrue(diary.moved.isEmpty())

        vm.takeAction(ItemSheetAction.MoveTapped)
        vm.takeAction(ItemSheetAction.MoveSlotPicked(MealSlot.Dinner))
        assertEquals(listOf(listOf(peanutButter) to MealSlot.Dinner), diary.moved)
        assertEquals(Res.string.today_toast_moved_to, assertIs<ItemSheetEvent.ShowToast>(events.first()).toast.message)
    }

    @Test
    fun copyToAnotherDay_startsOnTomorrowAndTheSameMeal() = runUnitTest {
        val vm = sheet()

        vm.takeAction(ItemSheetAction.CopyTapped)
        assertEquals(ItemStep.CopyTo(CopyDay.Tomorrow, MealSlot.Snack), vm.state.step)
        vm.takeAction(ItemSheetAction.CopyDayPicked(CopyDay.Yesterday))
        vm.takeAction(ItemSheetAction.CopySlotPicked(MealSlot.Breakfast))
        vm.takeAction(ItemSheetAction.CopyConfirmed)

        assertEquals(listOf(Triple(listOf(peanutButter), today.plus(DatePeriod(days = -1)), MealSlot.Breakfast)), diary.copiedItems)
    }

    @Test
    fun editACopy_opensIt() = runUnitTest {
        val vm = sheet()
        val events = events(vm)

        vm.takeAction(ItemSheetAction.EditCopyTapped)

        assertEquals(listOf<ItemSheetEvent>(ItemSheetEvent.OpenEditCopy(today, peanutButter)), events)
        assertEquals(0, diary.writes, "the copy is made on its own screen")
    }

    @Test
    fun addToFavorites_starsTheFood_thenOffersToTakeItOff() = runUnitTest {
        val vm = sheet()
        val events = events(vm)

        vm.takeAction(ItemSheetAction.FavoriteTapped)

        assertEquals(listOf("f:peanut-butter"), settings.settings.value.favoriteFoods)
        assertEquals(Res.string.today_toast_favorited, assertIs<ItemSheetEvent.ShowToast>(events.first()).toast.message)
        assertTrue(FoodAction.Unfavorite in sheet().state.actions)
    }

    @Test
    fun delete_removesTheEntry() = runUnitTest {
        val vm = sheet()

        vm.takeAction(ItemSheetAction.DeleteTapped)

        assertEquals(listOf(peanutButter), diary.removed)
    }

    @Test
    fun back_returnsToTheActions() = runUnitTest {
        val vm = sheet()

        vm.takeAction(ItemSheetAction.CopyTapped)
        vm.takeAction(ItemSheetAction.BackTapped)

        assertEquals(ItemStep.Actions, vm.state.step)
    }
}
