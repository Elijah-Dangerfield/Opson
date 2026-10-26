package com.opson.libraries.ui.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** `dayInfo` in the prototype, and the timestamp copy on Modes ("Last changed by you, Oct 25, 9:41 PM."). */
class RelativeDayTest {

    private val today = LocalDate(2026, 10, 25)

    @Test
    fun of_namesTheThreeNearDays() {
        assertEquals(RelativeDay.Today, RelativeDay.of(today, today))
        assertEquals(RelativeDay.Yesterday, RelativeDay.of(LocalDate(2026, 10, 24), today))
        assertEquals(RelativeDay.Tomorrow, RelativeDay.of(LocalDate(2026, 10, 26), today))
        assertEquals(RelativeDay.Date(LocalDate(2026, 10, 22)), RelativeDay.of(LocalDate(2026, 10, 22), today))
        assertEquals(RelativeDay.Date(LocalDate(2026, 10, 27)), RelativeDay.of(LocalDate(2026, 10, 27), today))
    }

    @Test
    fun of_acrossAMonthAndAYear() {
        assertEquals(RelativeDay.Yesterday, RelativeDay.of(LocalDate(2026, 10, 31), LocalDate(2026, 11, 1)))
        assertEquals(RelativeDay.Tomorrow, RelativeDay.of(LocalDate(2027, 1, 1), LocalDate(2026, 12, 31)))
    }

    @Test
    fun label() {
        assertEquals("Today", DateCopy.label(RelativeDay.Today))
        assertEquals("Yesterday", DateCopy.label(RelativeDay.Yesterday))
        assertEquals("Tomorrow", DateCopy.label(RelativeDay.Tomorrow))
        assertEquals("Thu, Oct 22", DateCopy.label(RelativeDay.Date(LocalDate(2026, 10, 22))))
        assertEquals("Sun, Nov 1", DateCopy.label(RelativeDay.Date(LocalDate(2026, 11, 1))))
        assertEquals("Wed, Sep 30", DateCopy.label(RelativeDay.Date(LocalDate(2026, 9, 30))))
    }

    @Test
    fun dates() {
        assertEquals("Oct 25", DateCopy.monthDay(today))
        assertEquals("October 25", DateCopy.longMonthDay(today))
        assertEquals("Sunday, Oct 25", DateCopy.longWeekdayMonthDay(today))
        assertEquals("October 2026", DateCopy.monthYear(today))
    }

    @Test
    fun range_sameMonthAndAcross() {
        assertEquals("Oct 19 to 25", DateCopy.range(LocalDate(2026, 10, 19), today))
        assertEquals("Oct 29 to Nov 4", DateCopy.range(LocalDate(2026, 10, 29), LocalDate(2026, 11, 4)))
        assertEquals("Dec 28 to Jan 3", DateCopy.range(LocalDate(2026, 12, 28), LocalDate(2027, 1, 3)))
    }

    @Test
    fun timestamp() {
        assertEquals("Oct 25, 9:41 PM", DateCopy.timestamp(LocalDateTime(2026, 10, 25, 21, 41)))
        assertEquals("Oct 25, 12:05 AM", DateCopy.timestamp(LocalDateTime(2026, 10, 25, 0, 5)))
        assertEquals("Oct 25, 12:00 PM", DateCopy.timestamp(LocalDateTime(2026, 10, 25, 12, 0)))
        assertEquals("Oct 25, 9:07 AM", DateCopy.timestamp(LocalDateTime(2026, 10, 25, 9, 7)))
    }

    @Test
    fun time_rejectsNonsense() {
        assertFailsWith<IllegalArgumentException> { DateCopy.time(24, 0) }
    }
}
