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
import com.opson.features.today.impl.meal.MealFeelAction
import com.opson.features.today.impl.meal.MealFeelViewModel
import com.opson.features.today.impl.meal.MealNoteAction
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
 * Today's sheets: the calendar, the meal sheet, the meal note and how it felt, water, the item
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
        vm.takeAction(CalendarAction.DoneTapped)
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

        val loose = MealSheetStateBuilder.build(MealSlot.Uncategorized, today, TodayFixtures.day(), Day.empty(yesterday), Settings())
        assertTrue(MealSheetItem.AteNoDetails !in loose.items)
    }

    @Test
    fun mealSheetTapsOpenOrWrite() = runUnitTest {
        val vm = MealSheetViewModel(today, MealSlot.Dinner, diary, FakeSettingsRepository(), FakeCalendar())
        val events = collect(vm.eventFlow)

        vm.takeAction(MealSheetAction.AddFoodTapped)
        vm.takeAction(MealSheetAction.AddNoteTapped)
        vm.takeAction(MealSheetAction.HowItFeltTapped)
        vm.takeAction(MealSheetAction.SaveAsMealTapped)
        vm.takeAction(MealSheetAction.CopyFromDayBeforeTapped)
        vm.takeAction(MealSheetAction.AteNoDetailsTapped)

        assertEquals(
            listOf(
                MealSheetEvent.OpenLogger(today, MealSlot.Dinner),
                MealSheetEvent.OpenMealNote(today, MealSlot.Dinner),
                MealSheetEvent.OpenMealFeel(today, MealSlot.Dinner),
                MealSheetEvent.OpenCreateMeal(today, MealSlot.Dinner),
            ),
            events.take(4),
        )
        assertEquals(listOf(Triple(yesterday, today, MealSlot.Dinner)), diary.copiedSlots)
        assertEquals(listOf(today to MealSlot.Dinner), diary.ateNoDetails)
        assertEquals(2, events.count { it == MealSheetEvent.Dismiss })
    }

    // Meal note and how it felt

    @Test
    fun mealNotePrefillsOnceAndSavesTrimmedKeepingTheFeeling() = runUnitTest {
        val vm = MealNoteViewModel(today, MealSlot.Breakfast, diary)
        assertEquals("", vm.state.draft)

        vm.takeAction(MealNoteAction.NoteTyped("  Sunny bench  "))
        vm.takeAction(MealNoteAction.SaveTapped)

        val saved = diary.feelings.single().third
        assertEquals("Sunny bench", saved?.note)
        assertEquals(Hunger.Hungry, saved?.hunger, "the note does not wipe how it felt")
    }

    @Test
    fun anEmptyMealNoteClearsIt() = runUnitTest {
        val vm = MealNoteViewModel(today, MealSlot.Lunch, diary)
        assertEquals("Ate outside with Sam. Felt relaxed.", vm.state.draft)

        vm.takeAction(MealNoteAction.NoteTyped("   "))
        vm.takeAction(MealNoteAction.SaveTapped)

        assertNull(diary.feelings.single().third?.note)
    }

    @Test
    fun mealFeelTogglesAndSavesWithTheNote() = runUnitTest {
        val vm = MealFeelViewModel(today, MealSlot.Lunch, diary)

        vm.takeAction(MealFeelAction.HungerTapped(Hunger.Peckish))
        vm.takeAction(MealFeelAction.HungerTapped(Hunger.Peckish))
        vm.takeAction(MealFeelAction.FullnessTapped(Fullness.Satisfied))
        vm.takeAction(MealFeelAction.MoodTapped(Mood.Calm))
        vm.takeAction(MealFeelAction.MoodTapped(Mood.Social))
        vm.takeAction(MealFeelAction.MoodTapped(Mood.Calm))
        vm.takeAction(MealFeelAction.SaveTapped)

        assertEquals(
            MealFeeling(hunger = null, fullness = Fullness.Satisfied, moods = setOf(Mood.Social), note = "Ate outside with Sam. Felt relaxed."),
            diary.feelings.single().third,
        )
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
        val vm = WaterSheetViewModel(today, diary, FakeSettingsRepository())

        vm.takeAction(WaterSheetAction.AddGlassTapped)
        vm.takeAction(WaterSheetAction.CustomAmountTyped("3a0"))
        assertEquals("30", vm.state.custom)
        vm.takeAction(WaterSheetAction.AddCustomTapped)
        vm.takeAction(WaterSheetAction.RemoveGlassTapped)

        assertEquals(listOf(today to 250, today to 30), diary.water)
        assertEquals(listOf(today to 250), diary.removedWater)
    }

    // Item

    @Test
    fun itemSheetRemovesTheEntryAndDismissesWhenItIsGone() = runUnitTest {
        val vm = ItemSheetViewModel(today, "adobo-$today", diary, FakePolicySource(), FakeCalendar())
        val events = collect(vm.eventFlow)
        assertEquals("Chicken adobo", vm.state.name)
        assertEquals("310 kcal", vm.state.calories.text())

        vm.takeAction(ItemSheetAction.RemoveTapped)
        assertEquals(listOf("adobo-$today"), diary.removed)
        assertIs<ItemSheetEvent.ShowToast>(events.first())

        val gone = ItemSheetViewModel(today, "nothing", diary, FakePolicySource(), FakeCalendar())
        val goneEvents = collect(gone.eventFlow)
        assertEquals(listOf<ItemSheetEvent>(ItemSheetEvent.Dismiss), goneEvents)
    }

    @Test
    fun itemSheetCaloriesFollowThePolicy() = runUnitTest {
        val vm = ItemSheetViewModel(today, "adobo-$today", diary, FakePolicySource(policy(PresetId.Blind)), FakeCalendar())
        assertEquals(null, vm.state.calories.text())
    }

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

    // Today's cards

    @Test
    fun todaysCardsTogglesGroupsHidesWaterAndSendsShowingToModes() = runUnitTest {
        val settings = FakeSettingsRepository()
        val source = FakePolicySource()
        val changer = FakeNumbersChanger(NumbersOutcome.Applied(fewer = true))
        val vm = TodayCardsViewModel(settings, source, changer, FastingRelease { false })
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
        val vm = TodayCardsViewModel(FakeSettingsRepository(), FakePolicySource(), FakeNumbersChanger(NumbersOutcome.NeedsPause), FastingRelease { false })
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
            TodayCardsStateBuilder.build(on.copy(numbers = NumbersSettings(PresetId.Rhythm)), policy, fastingReleased = true).fasting,
            "a mode without fasting",
        )
    }

    @Test
    fun theFastingRowTogglesTheTodayCard() = runUnitTest {
        val settings = FakeSettingsRepository(Settings(fasting = true))
        val vm = TodayCardsViewModel(settings, FakePolicySource(), FakeNumbersChanger(), FastingRelease { true })

        vm.takeAction(TodayCardsAction.FastingToggled)

        assertFalse(settings.state.value.fastToday)
        assertEquals(false, vm.state.fasting)
    }

    // Explorer

    @Test
    fun aNewFoodIsOneFirstLoggedThisMonth() {
        val october = TodayFixtures.day()
        val september = TodayFixtures.day(LocalDate(2026, 9, 20))
        val stats = ExplorerStatsBuilder.from(frequent = october.allItems, days = listOf(september, october), today = today)

        assertTrue(stats.newFoods.isEmpty(), "everything was had in September")
        val fresh = ExplorerStatsBuilder.from(frequent = emptyList(), days = listOf(october), today = today)
        assertEquals("Peanut butter", fresh.newFoods.first().name, "newest first")
        assertEquals(listOf("Oat porridge with milk", "Banana", "Coffee with milk"), ExplorerStatsBuilder.from(october.items(MealSlot.Breakfast), emptyList(), today).safeFoods)
    }
}
