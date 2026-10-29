package com.opson.features.foods.impl

import com.opson.features.foods.RepeatRoute
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.PickedForMeal
import com.opson.libraries.diary.RepeatDays
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.repeat_toast_deleted
import opson.libraries.resources.generated.resources.repeat_toast_saved
import opson.libraries.resources.generated.resources.repeat_toast_saved_logged
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The repeat editor: [RepeatStateBuilder]'s choices and notes, and [RepeatViewModel]'s actions. Today is Sunday Oct 25. */
class RepeatTest : CoroutineTest() {
    private val today = FoodsFixtures.today
    private val porridge = LogItem(
        id = "porridge-today",
        day = today,
        slot = MealSlot.Breakfast,
        loggedAt = Instant.parse("2026-10-25T08:05:00Z"),
        name = "Oat porridge with milk",
        precision = FoodsFixtures.porridge.precision,
        method = LogMethod.Search,
        foodId = FoodsFixtures.porridge.foodId,
        amountText = "1 bowl",
        kcal = 300.0,
    )
    private val savedMeals = FakeSavedMealRepository(FoodsFixtures.savedMeals)
    private val mealPicks = FakeMealPicks()
    private val repeats = FakeRepeatRepository(FoodsFixtures.repeats)

    private fun vm(route: RepeatRoute = RepeatRoute(), days: List<Day> = listOf(Day.empty(today).with(porridge))) =
        RepeatViewModel(route, repeats, mealPicks, savedMeals, FakeDiaryRepository(days), FakeCalendar(), FakePolicySource())

    private fun TestScope.events(vm: RepeatViewModel): List<RepeatEvent> {
        val events = mutableListOf<RepeatEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    @Test
    fun aNewRepeatOpensEmpty_everyDayFromToday_andCannotBeSavedYet() = runUnitTest {
        val state = vm().state

        assertTrue(state.isNew)
        assertTrue(state.items.isEmpty())
        assertEquals(RepeatDays.EveryDay, state.days)
        assertEquals(RepeatStart.Today, state.start)
        assertEquals(RepeatStart.entries, state.starts)
        assertEquals(RepeatEnd.Never, state.end)
        assertEquals(RepeatNote.LogsToday, state.note)
        assertFalse(state.canSave)
    }

    @Test
    fun aSavedMealIsPickedWithItsFoodsAndUsualMeal_andTappedAgainItGoes() = runUnitTest {
        val vm = vm()

        vm.takeAction(RepeatAction.SlotPicked(MealSlot.Dinner))
        vm.takeAction(RepeatAction.SavedMealTapped("work-lunch"))

        assertEquals("Work lunch", vm.state.mealName)
        assertEquals(listOf("Chicken adobo", "Jasmine rice"), vm.state.items.map { it.name })
        assertEquals(MealSlot.Lunch, vm.state.slot)
        assertTrue(vm.state.savedMeals.single { it.id == "work-lunch" }.selected)
        assertTrue(vm.state.canSave)

        vm.takeAction(RepeatAction.SavedMealTapped("work-lunch"))
        assertNull(vm.state.mealName)
        assertTrue(vm.state.items.isEmpty())
    }

    @Test
    fun aFoodPickedThroughSearchComesBackOnce_andMakesTheListItsOwn() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        vm.takeAction(RepeatAction.SavedMealTapped("usual-breakfast"))

        vm.takeAction(RepeatAction.AddFoodTapped)
        val requestId = assertIs<RepeatEvent.OpenPicker>(events.single()).requestId
        mealPicks.picked(PickedForMeal("a-meal-being-made", listOf(FoodsFixtures.yogurt)))
        mealPicks.picked(PickedForMeal(requestId, listOf(FoodsFixtures.berries)))

        assertEquals(listOf("Oat porridge with milk", "Banana", "Coffee with milk", "Berries"), vm.state.items.map { it.name })
        assertNull(vm.state.mealName, "no longer the saved meal as it is")
        assertNull(mealPicks.latest.value)
    }

    @Test
    fun theDaysAndWhatSavingDoes() = runUnitTest {
        val vm = vm()
        vm.takeAction(RepeatAction.SavedMealTapped("usual-breakfast"))

        vm.takeAction(RepeatAction.DaysPicked(RepeatDays.Weekdays))
        assertEquals(RepeatNote.Tomorrow, vm.state.note, "Sunday is not a weekday")

        vm.takeAction(RepeatAction.DayToggled(DayOfWeek.MONDAY))
        assertEquals(RepeatNote.NextOn(LocalDate(2026, 10, 27)), vm.state.note)

        vm.takeAction(RepeatAction.DaysPicked(RepeatDays.EveryDay))
        vm.takeAction(RepeatAction.StartPicked(RepeatStart.Tomorrow))
        assertEquals(RepeatNote.Tomorrow, vm.state.note)

        RepeatDays.EveryDay.forEach { vm.takeAction(RepeatAction.DayToggled(it)) }
        assertTrue(vm.state.days.isEmpty())
        assertFalse(vm.state.canSave, "a repeat needs a day")
    }

    @Test
    fun savingANewRepeatSavesThePlan_saysTodayIsLogged_andCloses() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        vm.takeAction(RepeatAction.SavedMealTapped("usual-breakfast"))
        vm.takeAction(RepeatAction.DaysPicked(RepeatDays.Weekdays))
        vm.takeAction(RepeatAction.EndPicked(RepeatEnd.TwoWeeks))

        vm.takeAction(RepeatAction.SaveTapped)
        vm.takeAction(RepeatAction.SaveTapped)

        val created = repeats.created.single()
        assertEquals("Usual breakfast", created.plan.name)
        assertEquals("usual-breakfast", created.plan.savedMealId)
        assertEquals(RepeatDays.Weekdays, created.plan.days)
        assertEquals(today, created.plan.startDay)
        assertEquals(LocalDate(2026, 11, 7), created.plan.endDay, "two weeks, the start day included")
        assertEquals(
            listOf(RepeatEvent.ShowToast(Toast(Res.string.repeat_toast_saved_logged, listOf(MealSlot.Breakfast.sentenceName))), RepeatEvent.Close),
            events,
        )
    }

    @Test
    fun aRepeatThatLoggedNothingTodaySaysSaved() = runUnitTest {
        repeats.loggedOnSave = false
        val vm = vm()
        val events = events(vm)
        vm.takeAction(RepeatAction.SavedMealTapped("usual-breakfast"))

        vm.takeAction(RepeatAction.SaveTapped)

        assertEquals(RepeatEvent.ShowToast(Toast(Res.string.repeat_toast_saved)), events.first())
    }

    @Test
    fun repeatThisFromTodayStartsFromTheEntry_whichCountsAsTodays() = runUnitTest {
        val vm = vm(RepeatRoute(fromDayIso = today.toString(), fromEntryId = porridge.id))

        assertEquals(listOf("Oat porridge with milk"), vm.state.items.map { it.name })
        assertEquals(MealSlot.Breakfast, vm.state.slot)
        assertEquals(RepeatNote.CountsToday, vm.state.note)

        vm.takeAction(RepeatAction.SaveTapped)
        assertEquals(listOf(porridge.id), repeats.created.single().loggedToday)
        assertEquals("Oat porridge with milk", repeats.created.single().plan.name)
    }

    @Test
    fun repeatThisFromAnotherDayDoesNotCountForToday() = runUnitTest {
        val yesterday = LocalDate(2026, 10, 24)
        val entry = porridge.copy(id = "porridge-yesterday", day = yesterday)
        val vm = vm(RepeatRoute(fromDayIso = yesterday.toString(), fromEntryId = entry.id), days = listOf(Day.empty(yesterday).with(entry)))

        assertEquals(RepeatNote.LogsToday, vm.state.note)
        vm.takeAction(RepeatAction.SaveTapped)
        assertTrue(repeats.created.single().loggedToday.isEmpty())
    }

    @Test
    fun anUncategorizedEntryRepeatsInTheMealItsTimeFallsIn() = runUnitTest {
        val entry = porridge.copy(slot = MealSlot.Uncategorized, loggedAt = Instant.parse("2026-10-25T13:00:00Z"))
        val vm = vm(RepeatRoute(fromDayIso = today.toString(), fromEntryId = entry.id), days = listOf(Day.empty(today).with(entry)))

        assertEquals(MealSlot.Lunch, vm.state.slot)
    }

    @Test
    fun aSavedRepeatOpensAsSaved_withPauseAndItsOwnEnd() = runUnitTest {
        val vm = vm(RepeatRoute(ruleId = "repeat-yogurt"))

        assertFalse(vm.state.isNew)
        assertTrue(vm.state.paused)
        assertEquals(RepeatNote.Paused, vm.state.note)
        assertTrue(vm.state.starts.isEmpty(), "it has started")
        assertEquals(RepeatEnd.On(LocalDate(2026, 11, 3)), vm.state.end)
        assertTrue(RepeatEnd.On(LocalDate(2026, 11, 3)) in vm.state.ends)
    }

    @Test
    fun resumingASavedRepeatSavesItsPlanAndPause() = runUnitTest {
        val vm = vm(RepeatRoute(ruleId = "repeat-yogurt"))

        vm.takeAction(RepeatAction.PauseToggled)
        vm.takeAction(RepeatAction.SaveTapped)

        val saved = repeats.rules.value.single { it.id == "repeat-yogurt" }
        assertFalse(saved.paused)
        assertEquals(LocalDate(2026, 11, 3), saved.plan.endDay)
        assertTrue(repeats.created.isEmpty())
    }

    @Test
    fun anEditToARepeatThatLoggedTodaySaysChangesStartNextTime() = runUnitTest {
        val logged = porridge.copy(repeatRuleId = "repeat-coffee")
        val vm = vm(RepeatRoute(ruleId = "repeat-coffee"), days = listOf(Day.empty(today).with(logged)))

        assertEquals(RepeatNote.LoggedToday, vm.state.note)
    }

    @Test
    fun deletingARepeatSaysWhatItLoggedStays() = runUnitTest {
        val vm = vm(RepeatRoute(ruleId = "repeat-coffee"))
        val events = events(vm)

        vm.takeAction(RepeatAction.DeleteTapped)

        assertEquals(listOf("repeat-coffee"), repeats.deleted)
        assertEquals(listOf(RepeatEvent.ShowToast(Toast(Res.string.repeat_toast_deleted)), RepeatEvent.Close), events)
    }

    @Test
    fun aClosedTodayIsNotLoggedOnSave() {
        val state = RepeatStateBuilder.build(FoodsFixtures.repeatDraft().copy(days = RepeatDays.EveryDay), FoodsFixtures.savedMeals, today, policy(PresetId.Full), todayOpen = false)

        assertEquals(RepeatNote.Tomorrow, state.note)
    }

    @Test
    fun anEndThatHasPassedSaysItWontLogAgain() {
        val draft = FoodsFixtures.repeatDraft().copy(start = LocalDate(2026, 10, 1), end = RepeatEnd.On(LocalDate(2026, 10, 20)), ruleId = "old")
        val state = RepeatStateBuilder.build(draft, FoodsFixtures.savedMeals, today, policy(PresetId.Full))

        assertEquals(RepeatNote.Never, state.note)
    }

    @Test
    fun theFoodsCaloriesFollowTheMode() {
        val full = FoodsFixtures.repeatState(policy(PresetId.Full))
        val blind = FoodsFixtures.repeatState(policy(PresetId.NoNumbers))

        assertEquals(listOf("300", "105", "40"), full.items.map { (it.calories as Display.Shown).text })
        assertTrue(blind.items.all { it.calories == Display.Hidden })
    }

    @Test
    fun theOfferedEndsAreCountedFromTheStart() {
        val start = LocalDate(2026, 10, 25)

        assertEquals(LocalDate(2026, 10, 31), RepeatEnd.Week.lastDay(start))
        assertEquals(LocalDate(2026, 11, 24), RepeatEnd.Month.lastDay(start))
        assertEquals(RepeatEnd.Week, RepeatEnd.of(start, LocalDate(2026, 10, 31)))
        assertEquals(RepeatEnd.On(LocalDate(2026, 11, 1)), RepeatEnd.of(start, LocalDate(2026, 11, 1)))
        assertEquals(RepeatEnd.Never, RepeatEnd.of(start, null))
    }
}

private fun Day.with(item: LogItem): Day = copy(itemsBySlot = itemsBySlot + (item.slot to (items(item.slot) + item)))

