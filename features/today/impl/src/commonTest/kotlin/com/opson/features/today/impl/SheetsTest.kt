package com.opson.features.today.impl

import com.opson.features.today.impl.calendar.CalendarAction
import com.opson.features.today.impl.calendar.CalendarEvent
import com.opson.features.today.impl.calendar.CalendarStateBuilder
import com.opson.features.today.impl.calendar.CalendarViewModel
import com.opson.features.today.impl.cards.TodayCardsAction
import com.opson.features.today.impl.cards.TodayCardsEvent
import com.opson.features.today.impl.cards.TodayCardsStateBuilder
import com.opson.features.today.impl.cards.TodayCardsViewModel
import com.opson.features.today.impl.daynote.DayNoteAction
import com.opson.features.today.impl.daynote.DayNoteEvent
import com.opson.features.today.impl.daynote.DayNoteViewModel
import com.opson.features.today.impl.item.ItemSheetAction
import com.opson.features.today.impl.item.ItemSheetEvent
import com.opson.features.today.impl.item.ItemSheetViewModel
import com.opson.features.today.impl.meal.MealNoteAction
import com.opson.features.today.impl.meal.MealNoteEvent
import com.opson.features.today.impl.meal.MealNoteViewModel
import com.opson.features.today.impl.meal.MealSheetAction
import com.opson.features.today.impl.meal.MealSheetEvent
import com.opson.features.today.impl.meal.MealSheetItem
import com.opson.features.today.impl.meal.MealSheetStateBuilder
import com.opson.features.today.impl.meal.MealSheetViewModel
import com.opson.features.today.impl.water.WaterSheetAction
import com.opson.features.today.impl.water.WaterSheetStateBuilder
import com.opson.features.today.impl.water.WaterSheetViewModel
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.FastingRelease
import com.opson.libraries.diary.Fullness
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Mood
import com.opson.libraries.diary.NumbersOutcome
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.WaterUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Today's sheets: the calendar, the meal sheet, the meal's Note, water, the item
 * sheet, the day note and Today's cards. One test per action, plus their builders. Explorer's
 * new-food rule is here too, being the one pure helper Today owns outside the builder.
 */
class SheetsTest : CoroutineTest() {
    private val today = TodayFixtures.today
    private val yesterday = today.minus(DatePeriod(days = 1))
    private val diary = FakeDiaryRepository(listOf(TodayFixtures.day(), TodayFixtures.yesterday()))

    private fun <E : Any> TestScope.collect(flow: kotlinx.coroutines.flow.Flow<E>): List<E> {
        val events = mutableListOf<E>()
        backgroundScope.launch { flow.toList(events) }
        return events
    }

    // Calendar

    @Test
    fun calendarLaysOutOctoberSundayFirstWithDots() {
        val state = CalendarStateBuilder.build(CalendarStateBuilder.monthOf(today), today, today, setOf(LocalDate(2026, 10, 3)))

        assertEquals(4, state.cells.takeWhile { it == null }.size, "Oct 1 2026 is a Thursday")
        assertEquals(31, state.cells.filterNotNull().size)
        assertTrue(state.cells.filterNotNull().first { it.date.day == 3 }.logged)
        assertTrue(state.cells.filterNotNull().first { it.date == today }.selected)
        assertFalse(state.cells.filterNotNull().first { it.date.day == 27 }.enabled, "two days ahead is past the plan")
        assertTrue(state.canGoPrev)
        assertFalse(state.canGoNext)
    }

    @Test
    fun calendarStopsAYearBack() {
        val yearAgo = CalendarStateBuilder.monthOf(today).minus(DatePeriod(months = CalendarStateBuilder.MONTHS_BACK))
        assertFalse(CalendarStateBuilder.build(yearAgo, today, today, emptySet()).canGoPrev)
    }

    @Test
    fun calendarPicksADayOrTodayAndIgnoresDisabledDays() = runUnitTest {
        val selected = FakeSelectedDay(yesterday)
        diary.loggedDates.value = mapOf((2026 to 10) to setOf(yesterday))
        val vm = CalendarViewModel(yesterday, diary, selected, FakeCalendar())
        val events = collect(vm.eventFlow)

        assertTrue(vm.state.cells.filterNotNull().first { it.date == yesterday }.logged)
        vm.takeAction(CalendarAction.DayTapped(today.plus(DatePeriod(days = 3))))
        assertEquals(yesterday, selected.date.value)
        assertTrue(events.isEmpty())

        vm.takeAction(CalendarAction.TodayTapped)
        assertEquals(today, selected.date.value)
        vm.takeAction(CalendarAction.PrevMonthTapped)
        assertEquals(LocalDate(2026, 9, 1), vm.state.month)
        vm.takeAction(CalendarAction.DayTapped(LocalDate(2026, 9, 12)))
        assertEquals(LocalDate(2026, 9, 12), selected.date.value)
        vm.takeAction(CalendarAction.CloseTapped)
        assertEquals(3, events.count { it == CalendarEvent.Dismiss })
    }

    // Meal sheet

    @Test
    fun mealSheetOffersCopyOnlyWhenTheDayBeforeHasTheMealAndSaveOnlyWithItems() {
        val dinner = MealSheetStateBuilder.build(MealSlot.Dinner, today, TodayFixtures.day(), TodayFixtures.yesterday(), Settings())
        assertTrue(MealSheetItem.CopyFromDayBefore(dayIsToday = true) in dinner.items)
        assertTrue(MealSheetItem.SaveAsMeal !in dinner.items)

        val lunch = MealSheetStateBuilder.build(MealSlot.Lunch, today, TodayFixtures.day(), TodayFixtures.yesterday(), Settings())
        assertTrue(lunch.items.none { it is MealSheetItem.CopyFromDayBefore })
        assertTrue(MealSheetItem.SaveAsMeal in lunch.items)
    }

    @Test
    fun mealSheetHasNoAteWithNoDetails() {
        val dinner = MealSheetStateBuilder.build(MealSlot.Dinner, today, TodayFixtures.day(), Day.empty(yesterday), Settings())
        assertEquals(listOf(MealSheetItem.AddFood, MealSheetItem.Note), dinner.items)
    }

    @Test
    fun mealSheetTapsOpenOrWrite() = runUnitTest {
        val vm = MealSheetViewModel(today, MealSlot.Dinner, diary, FakeSettingsRepository(), FakeCalendar())
        val events = collect(vm.eventFlow)

        vm.takeAction(MealSheetAction.AddFoodTapped)
        vm.takeAction(MealSheetAction.NoteTapped)
        vm.takeAction(MealSheetAction.SaveAsMealTapped)
        vm.takeAction(MealSheetAction.CopyFromDayBeforeTapped)

        assertEquals(
            listOf(
                MealSheetEvent.OpenLogger(today, MealSlot.Dinner),
                MealSheetEvent.OpenMealNote(today, MealSlot.Dinner),
                MealSheetEvent.OpenCreateMeal(today, MealSlot.Dinner),
            ),
            events.take(3),
        )
        assertEquals(listOf(Triple(yesterday, today, MealSlot.Dinner)), diary.copiedSlots)
        assertEquals(1, events.count { it == MealSheetEvent.Dismiss })
    }

    /** The sheet animates down after a write; a second tap in that time is queued behind the first. */
    @Test
    fun aSecondTapWhileTheMealSheetClosesWritesNothingMore() = runUnitTest {
        val vm = MealSheetViewModel(today, MealSlot.Dinner, diary, FakeSettingsRepository(), FakeCalendar())

        vm.takeAction(MealSheetAction.CopyFromDayBeforeTapped)
        vm.takeAction(MealSheetAction.CopyFromDayBeforeTapped)

        assertEquals(1, diary.copiedSlots.size)
    }

    // A meal's Note

    @Test
    fun mealSheetOffersOneNoteForTheNoteAndHunger() {
        val items = TodayFixtures.mealSheetState().items
        assertEquals(1, items.count { it == MealSheetItem.Note })
    }

    @Test
    fun mealNotePrefillsOnceAndSavesTrimmedKeepingTheHunger() = runUnitTest {
        val vm = MealNoteViewModel(today, MealSlot.Breakfast, diary)
        assertEquals("", vm.state.draft)
        assertEquals(Hunger.Hungry, vm.state.hunger)

        vm.takeAction(MealNoteAction.NoteTyped("  Sunny bench  "))
        vm.takeAction(MealNoteAction.SaveTapped)

        val saved = diary.feelings.single().third
        assertEquals("Sunny bench", saved?.note)
        assertEquals(Hunger.Hungry, saved?.hunger, "typing a note does not wipe the hunger")
    }

    @Test
    fun mealNoteHungerPicksAndClearsWhenTappedAgain() = runUnitTest {
        val vm = MealNoteViewModel(today, MealSlot.Dinner, diary)
        assertNull(vm.state.hunger)

        vm.takeAction(MealNoteAction.HungerTapped(Hunger.Stuffed))
        assertEquals(Hunger.Stuffed, vm.state.hunger)
        vm.takeAction(MealNoteAction.HungerTapped(Hunger.Stuffed))
        assertNull(vm.state.hunger)
        vm.takeAction(MealNoteAction.HungerTapped(Hunger.Starving))
        vm.takeAction(MealNoteAction.SaveTapped)

        assertEquals(MealFeeling(hunger = Hunger.Starving), diary.feelings.single().third, "a hunger with no note is a Note too")
    }

    /** Rows from the old "How it felt" sheet keep their fullness and moods: the Note only edits its own two. */
    @Test
    fun mealNoteKeepsWhatTheOldSheetStored() = runUnitTest {
        val old = MealFeeling(hunger = Hunger.Hungry, fullness = Fullness.Satisfied, moods = setOf(Mood.Calm), note = "Picnic")
        val stored = FakeDiaryRepository(listOf(TodayFixtures.day().copy(feelings = mapOf(MealSlot.Lunch to old))))
        val vm = MealNoteViewModel(today, MealSlot.Lunch, stored)

        vm.takeAction(MealNoteAction.HungerTapped(Hunger.Full))
        vm.takeAction(MealNoteAction.NoteTyped("Picnic in the park"))
        vm.takeAction(MealNoteAction.SaveTapped)

        assertEquals(old.copy(hunger = Hunger.Full, note = "Picnic in the park"), stored.feelings.single().third)
    }

    /** Save before the day is read would store the blank sheet over the Note already there. */
    @Test
    fun aMealNoteSavedBeforeTheDayIsReadWritesNothing() = runUnitTest {
        val open = MutableStateFlow(false)
        val vm = MealNoteViewModel(today, MealSlot.Lunch, diary.heldUntil(open))

        vm.takeAction(MealNoteAction.SaveTapped)
        assertTrue(diary.feelings.isEmpty())

        open.value = true
        assertEquals("Ate outside with Sam. Felt relaxed.", vm.state.draft)
    }

    @Test
    fun anUntouchedEmptyMealNoteSavesQuietly() = runUnitTest {
        val vm = MealNoteViewModel(today, MealSlot.Dinner, diary)
        val events = collect(vm.eventFlow)

        vm.takeAction(MealNoteAction.SaveTapped)

        assertEquals(listOf<MealNoteEvent>(MealNoteEvent.Dismiss), events, "no toast for nothing saved")
    }

    @Test
    fun anEmptyMealNoteClearsIt() = runUnitTest {
        val vm = MealNoteViewModel(today, MealSlot.Lunch, diary)
        assertEquals("Ate outside with Sam. Felt relaxed.", vm.state.draft)

        vm.takeAction(MealNoteAction.NoteTyped("   "))
        vm.takeAction(MealNoteAction.SaveTapped)

        assertNull(diary.feelings.single().third?.note)
    }

    // Water

    @Test
    fun waterCustomAmountsKeepDigitsAndConvertFluidOunces() {
        assertEquals(355, WaterSheetStateBuilder.customMl("12", WaterUnit.FlOz))
        assertEquals(300, WaterSheetStateBuilder.customMl("300", WaterUnit.Ml))
        assertNull(WaterSheetStateBuilder.customMl("0", WaterUnit.Ml))
        assertFalse(WaterSheetStateBuilder.build(Day.empty(today), Settings(), "").canRemove)
        assertEquals("250 ml", WaterSheetStateBuilder.build(Day.empty(today), Settings(), "").glassLabel)
    }

    @Test
    fun waterSheetAddsRemovesAndOpensSettings() = runUnitTest {
        fun sheet() = WaterSheetViewModel(today, diary, FakeSettingsRepository())

        sheet().takeAction(WaterSheetAction.AddGlassTapped)
        val custom = sheet()
        custom.takeAction(WaterSheetAction.CustomAmountTyped("3a0"))
        assertEquals("30", custom.state.custom)
        custom.takeAction(WaterSheetAction.AddCustomTapped)
        sheet().takeAction(WaterSheetAction.RemoveGlassTapped)

        assertEquals(listOf(today to 250, today to 30), diary.water)
        assertEquals(listOf(today to 250), diary.removedWater)
    }

    @Test
    fun aSecondTapWhileTheWaterSheetClosesCountsNoSecondGlass() = runUnitTest {
        val vm = WaterSheetViewModel(today, diary, FakeSettingsRepository())

        vm.takeAction(WaterSheetAction.AddGlassTapped)
        vm.takeAction(WaterSheetAction.AddGlassTapped)
        vm.takeAction(WaterSheetAction.RemoveGlassTapped)

        assertEquals(listOf(today to 250), diary.water)
        assertTrue(diary.removedWater.isEmpty())
    }

    // Item

    @Test
    fun itemSheetRemovesTheEntryAndDismissesWhenItIsGone() = runUnitTest {
        val vm = itemSheet("adobo-$today")
        val events = collect(vm.eventFlow)
        assertEquals("Chicken adobo", vm.state.name)
        assertEquals("310 kcal", vm.state.calories.text())

        vm.takeAction(ItemSheetAction.DeleteTapped)
        assertEquals(listOf("adobo-$today"), diary.removed)
        assertIs<ItemSheetEvent.ShowToast>(events.first())

        val gone = itemSheet("nothing")
        val goneEvents = collect(gone.eventFlow)
        assertEquals(listOf<ItemSheetEvent>(ItemSheetEvent.Dismiss), goneEvents)
    }

    @Test
    fun itemSheetCaloriesFollowThePolicy() = runUnitTest {
        val vm = itemSheet("adobo-$today", FakePolicySource(policy(PresetId.NoNumbers)))
        assertEquals(null, vm.state.calories.text())
    }

    private fun itemSheet(entryId: String, policySource: FakePolicySource = FakePolicySource()) =
        ItemSheetViewModel(today, entryId, diary, diary, FakeSettingsRepository(), policySource, FakeCalendar(), foodActionsFlag(false))

    // Day note

    @Test
    fun dayNoteSavesWithoutClosingTheDay() = runUnitTest {
        val vm = DayNoteViewModel(today, diary, FakeCalendar())
        val events = collect(vm.eventFlow)

        vm.takeAction(DayNoteAction.NoteTyped(" Lazy Sunday "))
        vm.takeAction(DayNoteAction.SaveTapped)

        assertEquals(listOf<Pair<LocalDate, String?>>(today to "Lazy Sunday"), diary.notes)
        assertTrue(diary.closed.isEmpty())
        assertEquals<DayNoteEvent>(DayNoteEvent.Dismiss, events.last())
    }

    @Test
    fun aDayNoteSavedBeforeTheDayIsReadWritesNothing() = runUnitTest {
        val noted = FakeDiaryRepository(listOf(TodayFixtures.day().copy(note = "Dinner at Lola's")))
        val open = MutableStateFlow(false)
        val vm = DayNoteViewModel(today, noted.heldUntil(open), FakeCalendar())

        vm.takeAction(DayNoteAction.SaveTapped)
        assertTrue(noted.notes.isEmpty())

        open.value = true
        vm.takeAction(DayNoteAction.SaveTapped)
        assertEquals(listOf<Pair<LocalDate, String?>>(today to "Dinner at Lola's"), noted.notes)
    }

    // Today's cards

    @Test
    fun todaysCardsTogglesGroupsHidesWaterAndSendsShowingToModes() = runUnitTest {
        val settings = FakeSettingsRepository()
        val source = FakePolicySource()
        val changer = FakeNumbersChanger(NumbersOutcome.Applied(fewer = true))
        val vm = TodayCardsViewModel(settings, source, changer, FastingRelease { false }, summaryFlag(false))
        val events = collect(vm.eventFlow)

        vm.takeAction(TodayCardsAction.MealGroupsToggled)
        assertFalse(settings.state.value.mealGroups)
        assertFalse(vm.state.mealGroups)

        vm.takeAction(TodayCardsAction.WaterToggled)
        assertEquals(listOf<NumbersChange>(NumbersChange.Override(Metric.Water, Visibility.Hide)), changer.requested)

        source.policy.value = policy(PresetId.Full, overrides = mapOf(Metric.Water to Visibility.Hide))
        vm.takeAction(TodayCardsAction.WaterToggled)
        assertEquals(1, changer.requested.size, "showing more is not a one-tap toggle")
        assertEquals<TodayCardsEvent>(TodayCardsEvent.OpenModes, events.last())
    }

    @Test
    fun hidingWaterBehindThePauseOpensIt() = runUnitTest {
        val vm = TodayCardsViewModel(FakeSettingsRepository(), FakePolicySource(), FakeNumbersChanger(NumbersOutcome.NeedsPause), FastingRelease { false }, summaryFlag(false))
        val events = collect(vm.eventFlow)

        vm.takeAction(TodayCardsAction.WaterToggled)

        assertEquals<TodayCardsEvent>(TodayCardsEvent.OpenPause(NumbersChange.Override(Metric.Water, Visibility.Hide)), events.single())
    }

    @Test
    fun theFastingRowShowsOnlyWhileFastingIsOfferedAndOn() {
        val policy = policy(PresetId.Full)
        val on = Settings(fasting = true)
        assertEquals(true, TodayCardsStateBuilder.build(on, policy, fastingReleased = true).fasting)
        assertNull(TodayCardsStateBuilder.build(Settings(), policy, fastingReleased = true).fasting, "timer off")
        assertNull(TodayCardsStateBuilder.build(on, policy, fastingReleased = false).fasting, "flag off")
        assertNull(
            TodayCardsStateBuilder.build(on.copy(onboarding = OnboardingAnswers(hard = true)), policy, fastingReleased = true).fasting,
            "after the statement",
        )
        assertNull(
            TodayCardsStateBuilder.build(on.copy(numbers = NumbersSettings(PresetId.NoNumbers)), policy, fastingReleased = true).fasting,
            "a mode without fasting",
        )
    }

    @Test
    fun theFastingRowTogglesTheTodayCard() = runUnitTest {
        val settings = FakeSettingsRepository(Settings(fasting = true))
        val vm = TodayCardsViewModel(settings, FakePolicySource(), FakeNumbersChanger(), FastingRelease { true }, summaryFlag(false))

        vm.takeAction(TodayCardsAction.FastingToggled)

        assertFalse(settings.state.value.fastToday)
        assertEquals(false, vm.state.fasting)
    }
}
