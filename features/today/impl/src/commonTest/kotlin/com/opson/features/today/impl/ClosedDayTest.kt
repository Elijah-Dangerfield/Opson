package com.opson.features.today.impl

import com.opson.features.today.impl.dayclose.DayClosedAction
import com.opson.features.today.impl.dayclose.DayClosedEvent
import com.opson.features.today.impl.dayclose.DayClosedViewModel
import com.opson.features.today.impl.daynote.DayNoteAction
import com.opson.features.today.impl.daynote.DayNoteEvent
import com.opson.features.today.impl.daynote.DayNoteViewModel
import com.opson.features.today.impl.item.FoodAction
import com.opson.features.today.impl.item.ItemSheetAction
import com.opson.features.today.impl.item.ItemSheetViewModel
import com.opson.features.today.impl.meal.MealNoteAction
import com.opson.features.today.impl.meal.MealNoteEvent
import com.opson.features.today.impl.meal.MealNoteViewModel
import com.opson.features.today.impl.meal.MealSheetAction
import com.opson.features.today.impl.meal.MealSheetEvent
import com.opson.features.today.impl.meal.MealSheetViewModel
import com.opson.features.today.impl.water.WaterSheetAction
import com.opson.features.today.impl.water.WaterSheetEvent
import com.opson.features.today.impl.water.WaterSheetViewModel
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.FastingRelease
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MinuteTicker
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A day closed with Close the day takes nothing until it is reopened (docs/decisions.md,
 * 2026-10-27): Today draws no +, no select tools and no adding menu items, a tap that would add
 * says so instead, every sheet that writes gives way to the closed sheet when its write is
 * refused, and Reopen, from Today, the menu, an item's sheet or the closed sheet, opens it again.
 */
class ClosedDayTest : CoroutineTest() {
    private val today = TodayFixtures.today
    private val yesterday = today.minus(DatePeriod(days = 1))
    private val closedToday: Day = TodayFixtures.day().copy(closedAt = TodayFixtures.noon)
    private val diary = FakeDiaryRepository(listOf(closedToday, TodayFixtures.yesterday()))

    private fun today(selectOn: Boolean = true) = TodayViewModel(
        diary = diary,
        settingsRepository = FakeSettingsRepository(),
        policySource = FakePolicySource(),
        selectedDay = FakeSelectedDay(),
        calendar = FakeCalendar(),
        recentLogEvents = FakeRecentLogEvents(),
        clock = FakeClock(),
        health = FakeHealthRepository(),
        healthFlag = healthFlag(false),
        fasting = FakeFastingRepository(),
        fastingRelease = FastingRelease { false },
        ticker = MinuteTicker { flowOf(TodayFixtures.noon) },
        editor = diary,
        weights = FakeWeightRepository(),
        summaryEnabled = summaryFlag(false),
        selectEnabled = selectFlag(selectOn),
        foodActionsEnabled = foodActionsFlag(true),
        goalsAndEnergy = goalsAndEnergyFlag(false),
        burnedRepository = FakeBurnedRepository(),
    )

    private fun <E> TestScope.collect(flow: Flow<E>): List<E> {
        val events = mutableListOf<E>()
        backgroundScope.launch { flow.toList(events) }
        return events
    }

    // --- what Today draws ---

    @Test
    fun aClosedDay_losesEveryPlus_select_andTheAddingMenuItems_andSaysSoWithReopen() {
        val state = TodayFixtures.todayState(policy(PresetId.Full), day = closedToday, selectOn = true)

        assertTrue(state.closed)
        assertEquals(CloseDayButton(closed = true), state.closeDay)
        assertFalse(state.selectTools, "select, drag and swipe all change the day")
        assertTrue(TodayRow.Closed in todayRows(state))

        val menu = TodayStateBuilder.build(TodayFixtures.inputs(closedToday, selectOn = true), policy(PresetId.Full), menuOpen = true).menu
        val items = menu?.groups.orEmpty().flatten()
        assertTrue(DayMenuItem.Reopen(isToday = true) in items)
        assertTrue(items.none { it is DayMenuItem.CloseDay })
        assertTrue(DayMenuItem.CopyYesterday !in items && DayMenuItem.AddNote !in items && DayMenuItem.ClearDay !in items)
        assertTrue(DayMenuItem.CopyToTomorrow in items, "copying out to an open day changes nothing here")
    }

    @Test
    fun aPastDayNobodyClosed_staysOpen_withAnEmptyCheck() {
        val past = TodayFixtures.day(today.minus(DatePeriod(days = 3)))
        val state = TodayFixtures.todayState(policy(PresetId.Full), day = past)

        assertFalse(state.closed)
        assertEquals(CloseDayButton(closed = false), state.closeDay)
        assertTrue(TodayRow.Closed !in todayRows(state))
    }

    // --- what Today does ---

    @Test
    fun tapsThatWouldAdd_sayTheDayIsClosed_andWriteNothing() = runUnitTest {
        val vm = today()
        val events = collect(vm.eventFlow)

        vm.takeAction(TodayAction.MealPlusTapped(MealSlot.Dinner))
        vm.takeAction(TodayAction.WaterPlusTapped)
        vm.takeAction(TodayAction.MealTileTapped(MealSlot.Lunch))
        vm.takeAction(TodayAction.DayNoteTapped)
        vm.takeAction(TodayAction.CopyYesterdayTapped)
        vm.takeAction(TodayAction.ClearDayTapped)
        vm.takeAction(TodayAction.ClearDayTapped)

        assertEquals(List(6) { TodayEvent.OpenDayClosed(today) }, events)
        assertEquals(0, diary.writes)
    }

    @Test
    fun copyingIntoADayClosedOnAnotherPhone_saysSo() = runUnitTest {
        diary.days.value += yesterday to TodayFixtures.yesterday().copy(closedAt = TodayFixtures.noon)
        diary.days.value += today to TodayFixtures.day()
        val vm = today()
        val events = collect(vm.eventFlow)

        vm.takeAction(TodayAction.FoodLongPressed(TodayFixtures.peanutButter))
        vm.takeAction(TodayAction.CopyToTapped)
        vm.takeAction(TodayAction.CopyDayPicked(CopyDay.Yesterday))
        vm.takeAction(TodayAction.CopySlotPicked(MealSlot.Snack))
        vm.takeAction(TodayAction.CopyConfirmed)

        assertEquals(listOf<TodayEvent>(TodayEvent.OpenDayClosed(yesterday)), events)
        assertTrue(diary.copiedItems.isEmpty())
        assertTrue(vm.state.selection != null, "still selecting, so another day can be picked")
    }

    @Test
    fun reopen_bringsThePlusesBack() = runUnitTest {
        val vm = today()
        assertTrue(vm.state.closed)

        vm.takeAction(TodayAction.ReopenTapped)

        assertEquals(listOf(today), diary.reopened)
        assertFalse(vm.state.closed)
        assertTrue(vm.state.selectTools)
    }

    // --- the sheets, when the day closes under them ---

    @Test
    fun theSheetsThatWrite_giveWayToTheClosedSheet() = runUnitTest {
        val water = WaterSheetViewModel(today, diary, FakeSettingsRepository())
        val waterEvents = collect(water.eventFlow)
        water.takeAction(WaterSheetAction.AddGlassTapped)

        val note = DayNoteViewModel(today, diary, FakeCalendar())
        val noteEvents = collect(note.eventFlow)
        note.takeAction(DayNoteAction.NoteTyped("Lazy Sunday"))
        note.takeAction(DayNoteAction.SaveTapped)

        val mealNote = MealNoteViewModel(today, MealSlot.Dinner, diary)
        val mealNoteEvents = collect(mealNote.eventFlow)
        mealNote.takeAction(MealNoteAction.NoteTyped("At Lola's"))
        mealNote.takeAction(MealNoteAction.SaveTapped)

        val meal = MealSheetViewModel(today, MealSlot.Dinner, diary, FakeSettingsRepository(), FakeCalendar())
        val mealEvents = collect(meal.eventFlow)
        meal.takeAction(MealSheetAction.CopyFromDayBeforeTapped)

        assertEquals(listOf<WaterSheetEvent>(WaterSheetEvent.DayClosed(today)), waterEvents)
        assertEquals(listOf<DayNoteEvent>(DayNoteEvent.DayClosed(today)), noteEvents)
        assertEquals(listOf<MealNoteEvent>(MealNoteEvent.DayClosed(today)), mealNoteEvents)
        assertEquals(listOf<MealSheetEvent>(MealSheetEvent.DayClosed(today)), mealEvents)
        assertEquals(0, diary.writes)
    }

    @Test
    fun aFoodOnAClosedDay_offersOnlyWhatLeavesTheDayAsItIs_andReopen() = runUnitTest {
        val sheet = ItemSheetViewModel(
            today, TodayFixtures.peanutButter, diary, diary, FakeSettingsRepository(), FakePolicySource(), FakeCalendar(), foodActionsFlag(true),
        )

        assertTrue(sheet.state.closed)
        assertEquals(listOf(FoodAction.CopyToDay, FoodAction.Favorite), sheet.state.actions)

        sheet.takeAction(ItemSheetAction.ReopenTapped)

        assertEquals(listOf(today), diary.reopened)
        assertFalse(sheet.state.closed)
        assertTrue(FoodAction.Delete in sheet.state.actions)
    }

    @Test
    fun theClosedSheet_reopensTheDay_orLeavesItClosed() = runUnitTest {
        val reopen = DayClosedViewModel(today, diary, FakeCalendar())
        val reopenEvents = collect(reopen.eventFlow)
        assertTrue(reopen.state.isToday)
        reopen.takeAction(DayClosedAction.ReopenTapped)
        assertEquals(listOf(today), diary.reopened)
        assertEquals(listOf<DayClosedEvent>(DayClosedEvent.Dismiss), reopenEvents)

        val notNow = DayClosedViewModel(yesterday, diary, FakeCalendar())
        val notNowEvents = collect(notNow.eventFlow)
        assertFalse(notNow.state.isToday)
        notNow.takeAction(DayClosedAction.NotNowTapped)
        assertEquals(listOf(today), diary.reopened, "Not now opens nothing")
        assertEquals(listOf<DayClosedEvent>(DayClosedEvent.Dismiss), notNowEvents)
    }

    @Test
    fun copyingToTomorrowFromAClosedDay_stillWorks() = runUnitTest {
        val vm = today()
        val events = collect(vm.eventFlow)

        vm.takeAction(TodayAction.CopyToTomorrowTapped)

        assertEquals(listOf(today to today.plus(DatePeriod(days = 1))), diary.copiedDays)
        assertTrue(events.none { it is TodayEvent.OpenDayClosed })
    }
}
