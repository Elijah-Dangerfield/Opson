package com.opson.features.today.impl

import com.opson.features.today.impl.notes.NotesAction
import com.opson.features.today.impl.notes.NotesEvent
import com.opson.features.today.impl.notes.NotesStateBuilder
import com.opson.features.today.impl.notes.NotesViewModel
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.ui.time.RelativeDay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.diary_hunger_hungry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Notes list: every meal's Note newest first, its window, and where a tap goes. */
class NotesTest : CoroutineTest() {
    private val today = TodayFixtures.today

    private fun daysAgo(days: Int, vararg notes: Pair<MealSlot, MealFeeling>): Day =
        Day.empty(today.minus(DatePeriod(days = days))).copy(feelings = notes.toMap())

    @Test
    fun notesAreNewestFirst_latestMealFirst_andAFeelingWithNothingInItIsLeftOut() {
        val state = NotesStateBuilder.build(
            listOf(
                daysAgo(3, MealSlot.Breakfast to MealFeeling(note = "Oats")),
                TodayFixtures.day(),
                daysAgo(1, MealSlot.Lunch to MealFeeling()),
            ),
            today,
            NotesStateBuilder.PAGE_DAYS,
            fromTrends = false,
        )

        assertEquals(listOf(today, today.minus(DatePeriod(days = 3))), state.days.map { it.date }, "a day with an empty feeling has no notes")
        val todays = state.days.first()
        assertEquals(RelativeDay.Today, todays.label)
        assertNull(todays.year)
        assertEquals(listOf(MealSlot.Lunch, MealSlot.Breakfast), todays.notes.map { it.slot })
        assertEquals(MealNoteUi(null, "Ate outside with Sam. Felt relaxed."), todays.notes[0].note)
        assertEquals(MealNoteUi(Res.string.diary_hunger_hungry, null), todays.notes[1].note)
    }

    @Test
    fun aDayFromAnotherYear_carriesItsYear() {
        val newYear = Day.empty(LocalDate(2025, 12, 31)).copy(feelings = mapOf(MealSlot.Dinner to MealFeeling(hunger = Hunger.Full)))
        val state = NotesStateBuilder.build(listOf(newYear), today, NotesStateBuilder.PAGE_DAYS, fromTrends = true)

        assertEquals(2025, state.days.single().year)
        assertTrue(state.fromTrends)
    }

    @Test
    fun showEarlier_widensTheWindow_untilAboutThreeYears() = runUnitTest {
        val old = daysAgo(120, MealSlot.Dinner to MealFeeling(note = "Birthday dinner"))
        val vm = NotesViewModel(fromTrends = false, diary = FakeDiaryRepository(listOf(TodayFixtures.day(), old)), calendar = FakeCalendar())

        assertEquals(listOf(today), vm.state.days.map { it.date }, "120 days back is past the first page")
        vm.takeAction(NotesAction.ShowEarlierTapped)
        assertEquals(listOf(today, old.date), vm.state.days.map { it.date })
        assertEquals(2 * NotesStateBuilder.PAGE_DAYS, vm.state.windowDays)

        repeat(20) { vm.takeAction(NotesAction.ShowEarlierTapped) }
        assertEquals(NotesStateBuilder.MAX_DAYS, vm.state.windowDays)
        assertFalse(vm.state.canShowEarlier)
    }

    @Test
    fun aTapOpensThatMealsNote_andBackCloses() = runUnitTest {
        val vm = NotesViewModel(fromTrends = false, diary = FakeDiaryRepository(listOf(TodayFixtures.day())), calendar = FakeCalendar())
        val events = mutableListOf<NotesEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }

        vm.takeAction(NotesAction.NoteTapped(today, MealSlot.Lunch))
        vm.takeAction(NotesAction.BackTapped)

        assertEquals(listOf(NotesEvent.OpenNote(today, MealSlot.Lunch), NotesEvent.Close), events)
    }
}
