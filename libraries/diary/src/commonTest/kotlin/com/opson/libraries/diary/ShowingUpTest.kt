package com.opson.libraries.diary

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The streak and milestone rules: what counts, what is forgiven, what ends a run, and when a milestone is fresh. */
class ShowingUpTest {

    private val today = LocalDate(2026, 10, 29)

    private fun day(daysAgo: Int): LocalDate = today.minus(daysAgo, DateTimeUnit.DAY)

    private fun logged(vararg daysAgo: Int) = daysAgo.map { DayMark(day(it), logged = true) }

    private fun compute(marks: List<DayMark>, on: LocalDate = today) = ShowingUp.compute(marks, on)

    @Test
    fun nothingYet_isAnEmptyRun() {
        val progress = compute(emptyList())
        assertEquals(0, progress.current)
        assertEquals(0, progress.longest)
        assertTrue(progress.reached.isEmpty())
        assertEquals(Milestone.Days3, progress.next)
        assertEquals(7, progress.recent.size)
    }

    @Test
    fun eachKindOfShowingUp_countsOnItsOwn() {
        val marks = listOf(
            DayMark(day(2), logged = true),
            DayMark(day(1), closed = true),
            DayMark(day(0), checkedIn = true),
        )
        assertEquals(3, compute(marks).current)
    }

    @Test
    fun todayWithNothingYet_neverBreaksTheRun() {
        val progress = compute(logged(3, 2, 1))
        assertEquals(3, progress.current)
        assertFalse(progress.todayCounted)
        assertEquals(StreakDay.Pending, progress.recent.last())
    }

    @Test
    fun oneQuietDay_isForgiven_andAddsNothing() {
        val progress = compute(logged(4, 3, 1, 0))
        assertEquals(4, progress.current)
        assertEquals(StreakDay.Bridged, progress.recent[progress.recent.size - 3])
    }

    @Test
    fun yesterdayQuiet_keepsTheRunWhileTodayIsStillGoing() {
        val progress = compute(logged(3, 2))
        assertEquals(2, progress.current)
        assertEquals(listOf(StreakDay.Counted, StreakDay.Bridged, StreakDay.Pending), progress.recent.takeLast(3))
    }

    @Test
    fun twoQuietDaysInARow_endTheRun_andTheLongestStays() {
        val progress = compute(logged(6, 5, 4, 3, 0))
        assertEquals(1, progress.current)
        assertEquals(4, progress.longest)
        // Neither quiet day was forgiven, once the run ended on the second.
        assertEquals(listOf(StreakDay.Missed, StreakDay.Missed), progress.recent.subList(4, 6))
    }

    @Test
    fun twoQuietDaysBeforeToday_leaveNoRun() {
        assertEquals(0, compute(logged(5, 4, 3)).current)
        assertEquals(3, compute(logged(5, 4, 3)).longest)
    }

    @Test
    fun untrackedDays_areRests_howeverMany() {
        val holiday = (2..12).map { DayMark(day(it), untracked = true) }
        val progress = compute(logged(15, 14, 13) + holiday + logged(1, 0))
        assertEquals(5, progress.current)
        assertEquals(StreakDay.Rest, progress.recent[2])
    }

    @Test
    fun anUntrackedDay_whereSomethingWasLogged_isStillARest() {
        val marks = logged(2) + DayMark(day(1), logged = true, closed = true, untracked = true) + logged(0)
        val progress = compute(marks)
        assertEquals(2, progress.current)
        assertNull(progress.reached[Milestone.FirstWrapUp])
    }

    @Test
    fun aRestDoesNotResetTheQuietDays_itIsAsIfItWereNotThere() {
        // Logged, quiet, untracked, quiet: the two quiet days are in a row once the rest steps aside.
        val marks = logged(4) + DayMark(day(2), untracked = true) + logged(0)
        assertEquals(1, compute(marks).current)
    }

    @Test
    fun milestones_areReachedOnTheDayTheRunGetsThere() {
        val marks = logged(*(0..7).toList().toIntArray())
        val progress = compute(marks)
        assertEquals(8, progress.current)
        assertEquals(day(5), progress.reached[Milestone.Days3])
        assertEquals(day(1), progress.reached[Milestone.Week1])
        assertEquals(Milestone.Weeks2, progress.next)
        assertEquals(6, progress.daysToNext)
    }

    @Test
    fun aMilestone_staysReached_afterTheRunEnds() {
        val progress = compute(logged(*(10..16).toList().toIntArray()) + logged(0))
        assertEquals(1, progress.current)
        assertEquals(day(10), progress.reached[Milestone.Week1])
    }

    @Test
    fun theFirstWrapUp_andCheckIns_countOnTheirOwn() {
        val marks = (0..6).map { DayMark(day(it), logged = true, checkedIn = true) } + DayMark(day(8), closed = true)
        val progress = compute(marks)
        assertEquals(day(8), progress.reached[Milestone.FirstWrapUp])
        assertEquals(day(6), progress.reached[Milestone.FirstCheckIn])
        assertEquals(day(0), progress.reached[Milestone.CheckIns7])
        assertEquals(7, progress.checkIns)
    }

    @Test
    fun aYear_isThreeHundredSixtyFiveDays() {
        val progress = compute(logged(*(0..364).toList().toIntArray()))
        assertEquals(today, progress.reached[Milestone.Year1])
        assertEquals(day(364 - 181), progress.reached[Milestone.Months6])
        assertNull(progress.next)
    }

    @Test
    fun daysThisMonth_countOnlyThisCalendarMonth() {
        val progress = compute(logged(0, 1, 28, 29, 30), on = today)
        // October 29: days 0, 1 and 28 ago are in October; 29 and 30 ago are September.
        assertEquals(3, progress.showedUpThisMonth)
    }

    @Test
    fun aFutureDay_isIgnored() {
        val progress = compute(logged(0) + DayMark(today.plus(1, DateTimeUnit.DAY), logged = true))
        assertEquals(1, progress.current)
    }

    @Test
    fun aLateNightLog_countsForItsOwnDate_atMidnightTheNextDayIsPending() {
        val marks = logged(0)
        val beforeMidnight = compute(marks, on = today)
        val afterMidnight = compute(marks, on = today.plus(1, DateTimeUnit.DAY))
        assertEquals(1, beforeMidnight.current)
        assertTrue(beforeMidnight.todayCounted)
        assertEquals(1, afterMidnight.current)
        assertFalse(afterMidnight.todayCounted)
    }

    @Test
    fun flyingEast_skipsADate_andTheSkippedDayIsForgiven() {
        // Logged Monday evening in New York; the phone wakes on Wednesday in Tokyo, so Tuesday never happened.
        val monday = LocalDate(2026, 10, 26)
        val wednesday = LocalDate(2026, 10, 28)
        val marks = listOf(DayMark(monday.minus(1, DateTimeUnit.DAY), logged = true), DayMark(monday, logged = true), DayMark(wednesday, logged = true))
        assertEquals(3, ShowingUp.compute(marks, wednesday).current)
    }

    @Test
    fun flyingWest_repeatsNoDate_soTheRunIsUnchanged() {
        // The phone goes back a day after a log: today is the date already logged, and still counts once.
        val marks = logged(1, 0)
        assertEquals(2, compute(marks, on = today).current)
        assertEquals(1, compute(marks, on = day(1)).current)
    }

    @Test
    fun toCelebrate_isTheFreshestHighest_andNothingOld() {
        val progress = compute(logged(*(0..9).toList().toIntArray()))
        assertEquals(Milestone.Week1, progress.toCelebrate(emptySet()))
        // Days 3 was reached a week ago: too late to celebrate.
        assertNull(progress.toCelebrate(setOf(Milestone.Week1)))
        assertEquals(Milestone.Days3, compute(logged(4, 3, 2, 1, 0)).toCelebrate(emptySet()))
    }

    @Test
    fun toCelebrate_putsTheStreakBeforeTheFirstWrapUp_andCheckIns() {
        val marks = logged(2, 1) + DayMark(day(0), closed = true, checkedIn = true)
        val progress = compute(marks)
        assertEquals(Milestone.Days3, progress.toCelebrate(emptySet()))
        assertEquals(Milestone.FirstWrapUp, progress.toCelebrate(setOf(Milestone.Days3)))
        assertEquals(Milestone.FirstCheckIn, progress.toCelebrate(setOf(Milestone.Days3, Milestone.FirstWrapUp)))
    }

    @Test
    fun celebratingOne_coversTheLowerOnesOfItsFamilyOnly() {
        assertEquals(setOf(Milestone.Days3, Milestone.Week1, Milestone.Weeks2), ShowingUp.coveredBy(Milestone.Weeks2))
        assertEquals(setOf(Milestone.FirstCheckIn), ShowingUp.coveredBy(Milestone.FirstCheckIn))
    }

    @Test
    fun wires_areUnique_andReadBack() {
        assertEquals(Milestone.entries.size, Milestone.entries.map { it.wire }.toSet().size)
        Milestone.entries.forEach { assertEquals(it, Milestone.fromWire(it.wire)) }
        assertNull(Milestone.fromWire("nope"))
    }
}
