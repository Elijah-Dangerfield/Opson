package com.opson.features.today.impl

import com.opson.features.today.impl.item.FoodAction
import com.opson.features.today.impl.item.ItemSheetAction
import com.opson.features.today.impl.item.ItemSheetEvent
import com.opson.features.today.impl.item.ItemSheetStateBuilder
import com.opson.features.today.impl.item.ItemSheetViewModel
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.RepeatsRelease
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Repeat this on a food's sheet (`release.repeats`), and the mark on a food a repeat logged. */
class ItemSheetRepeatTest : CoroutineTest() {
    private val today = TodayFixtures.today
    private val peanutButter = TodayFixtures.peanutButter

    private fun sheet(day: Day = TodayFixtures.day(), actionsOn: Boolean = true, repeatsOn: Boolean = true): ItemSheetViewModel {
        val diary = FakeDiaryRepository(listOf(day))
        return ItemSheetViewModel(
            today,
            peanutButter,
            diary,
            diary,
            FakeSettingsRepository(),
            FakePolicySource(policy(PresetId.Full)),
            FakeCalendar(),
            foodActionsFlag(actionsOn),
            RepeatsRelease { repeatsOn },
        )
    }

    private fun TestScope.events(vm: ItemSheetViewModel): List<ItemSheetEvent> {
        val events = mutableListOf<ItemSheetEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    /** A copy of the fixture day with the peanut butter logged by a repeat. */
    private fun repeatedDay(): Day {
        val day = TodayFixtures.day()
        return day.copy(itemsBySlot = day.itemsBySlot.mapValues { (_, items) -> items.map { if (it.id == peanutButter) it.copy(repeatRuleId = "rule-1") else it } })
    }

    @Test
    fun repeatThisSitsAboveDelete() = runUnitTest {
        assertEquals(
            listOf(FoodAction.ChangeAmount, FoodAction.Move, FoodAction.CopyToDay, FoodAction.EditCopy, FoodAction.Favorite, FoodAction.Repeat, FoodAction.Delete),
            sheet().state.actions,
        )
    }

    @Test
    fun repeatThisIsOfferedWithFoodActionsOff_andNotWithRepeatsOff() = runUnitTest {
        assertEquals(listOf(FoodAction.Repeat, FoodAction.Delete), sheet(actionsOn = false).state.actions)
        assertTrue(FoodAction.Repeat !in sheet(repeatsOn = false).state.actions)
    }

    @Test
    fun repeatThisOpensANewRepeatFromTheEntry() = runUnitTest {
        val vm = sheet()
        val events = events(vm)

        vm.takeAction(ItemSheetAction.RepeatTapped)
        vm.takeAction(ItemSheetAction.RepeatTapped)

        assertEquals(listOf<ItemSheetEvent>(ItemSheetEvent.OpenRepeat(today, peanutButter, ruleId = null)), events)
    }

    @Test
    fun aFoodARepeatLoggedOffersEditRepeat_whichOpensItsRule() = runUnitTest {
        val vm = sheet(day = repeatedDay())
        val events = events(vm)

        assertTrue(FoodAction.EditRepeat in vm.state.actions)
        assertTrue(FoodAction.Repeat !in vm.state.actions)
        vm.takeAction(ItemSheetAction.RepeatTapped)

        assertEquals(listOf<ItemSheetEvent>(ItemSheetEvent.OpenRepeat(today, peanutButter, ruleId = "rule-1")), events)
    }

    @Test
    fun aClosedDayStillOffersRepeatThis_itChangesNothingOnTheDay() {
        val state = ItemSheetStateBuilder.build(peanutButter, TodayFixtures.closedDay(), today, policy(PresetId.Full), actionsOn = true, repeatsOn = true)

        assertTrue(state != null && FoodAction.Repeat in state.actions && FoodAction.Delete !in state.actions)
    }

    @Test
    fun todaysRowMarksAFoodARepeatLogged() {
        val state = TodayStateBuilder.build(TodayFixtures.inputs(day = repeatedDay()), policy(PresetId.Full))
        val rows = state.meals.filterIsInstance<MealEntry.Full>().flatMap { it.card.items }

        assertEquals(listOf(peanutButter), rows.filter { it.repeat }.map { it.entryId })
    }
}
