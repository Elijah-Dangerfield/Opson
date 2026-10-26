package com.opson.features.settings.impl.integrations

import com.opson.features.settings.impl.eventsOf
import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Leave out a day sheet: one test per action, and the window it keeps to. */
class LeaveOutViewModelTest : CoroutineTest() {

    private val diary = FakeDiary(IntegrationsFixtures.days)

    private fun vm() = LeaveOutViewModel(diary, FakeCalendar())

    private fun LeaveOutState.cell(date: LocalDate) = cells.filterNotNull().first { it.date == date }

    @Test
    fun opensOnThisMonth_withTheDaysLeftOutSelected() = runUnitTest {
        val state = vm().state
        assertEquals(LocalDate(2026, 10, 1), state.month)
        assertEquals("October 2026", state.title)
        assertTrue(state.cell(IntegrationsFixtures.familyVisit).leftOut)
        assertFalse(state.cell(LocalDate(2026, 10, 17)).leftOut)
        assertTrue(state.cell(IntegrationsFixtures.today).isToday)
        // October 1 2026 is a Thursday: four blanks, Sunday first.
        assertEquals(4, state.cells.takeWhile { it == null }.size)
    }

    @Test
    fun dayTapped_leavesItOut() = runUnitTest {
        val vm = vm()
        vm.takeAction(LeaveOutAction.DayTapped(LocalDate(2026, 10, 20)))
        assertEquals(setOf(IntegrationsFixtures.familyVisit, LocalDate(2026, 10, 20)), diary.untracked())
        assertTrue(vm.state.cell(LocalDate(2026, 10, 20)).leftOut)
    }

    @Test
    fun dayTapped_again_countsItAgain() = runUnitTest {
        val vm = vm()
        vm.takeAction(LeaveOutAction.DayTapped(IntegrationsFixtures.familyVisit))
        assertEquals(emptySet(), diary.untracked())
        assertFalse(vm.state.cell(IntegrationsFixtures.familyVisit).leftOut)
    }

    @Test
    fun dayTapped_outsideTheWindow_doesNothing() = runUnitTest {
        val vm = vm()
        vm.takeAction(LeaveOutAction.DayTapped(LocalDate(2026, 10, 26)))
        vm.takeAction(LeaveOutAction.DayTapped(LocalDate(2026, 7, 27)))
        assertEquals(setOf(IntegrationsFixtures.familyVisit), diary.untracked())
        assertFalse(vm.state.cell(LocalDate(2026, 10, 26)).enabled)
    }

    @Test
    fun monthArrows_stopAtTheWindow() = runUnitTest {
        val vm = vm()
        assertFalse(vm.state.canGoNext)

        repeat(5) { vm.takeAction(LeaveOutAction.PrevMonthTapped) }

        // July holds the window's first day, July 28; June is in no average.
        assertEquals(LocalDate(2026, 7, 1), vm.state.month)
        assertFalse(vm.state.canGoPrev)
        assertFalse(vm.state.cell(LocalDate(2026, 7, 27)).enabled)
        assertTrue(vm.state.cell(LocalDate(2026, 7, 28)).enabled)

        vm.takeAction(LeaveOutAction.NextMonthTapped)
        assertEquals(LocalDate(2026, 8, 1), vm.state.month)
    }

    @Test
    fun anotherMonth_showsItsOwnDaysLeftOut() = runUnitTest {
        diary.setUntracked(LocalDate(2026, 9, 12), untracked = true)
        val vm = vm()
        vm.takeAction(LeaveOutAction.PrevMonthTapped)
        assertTrue(vm.state.cell(LocalDate(2026, 9, 12)).leftOut)
    }

    @Test
    fun doneTapped_dismisses() = runUnitTest {
        val vm = vm()
        val events = eventsOf(vm)
        vm.takeAction(LeaveOutAction.DoneTapped)
        assertEquals(listOf<LeaveOutEvent>(LeaveOutEvent.Dismiss), events)
    }
}
