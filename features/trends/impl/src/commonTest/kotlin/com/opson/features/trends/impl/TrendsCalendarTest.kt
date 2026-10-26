package com.opson.features.trends.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TrendsRange
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The week window and weekly bars Trends and Nutrients agree on. What the
 * cards draw from them is `TrendsStateBuilderTest`.
 */
class TrendsCalendarTest {

    private val sunday = LocalDate(2026, 10, 25)
    private val wednesday = LocalDate(2026, 10, 21)

    @Test
    fun window_mondayStart_endsOnTheLatestSunday() {
        assertEquals(WeekWindow(LocalDate(2026, 10, 19), sunday), TrendsCalendar.weekWindow(sunday, DayOfWeek.MONDAY))
        assertEquals(
            WeekWindow(LocalDate(2026, 10, 12), LocalDate(2026, 10, 18)),
            TrendsCalendar.weekWindow(wednesday, DayOfWeek.MONDAY),
        )
    }

    @Test
    fun window_sundayStart_endsOnTheLatestSaturday() {
        assertEquals(
            WeekWindow(LocalDate(2026, 10, 18), LocalDate(2026, 10, 24)),
            TrendsCalendar.weekWindow(sunday, DayOfWeek.SUNDAY),
        )
        assertEquals(
            WeekWindow(LocalDate(2026, 10, 11), LocalDate(2026, 10, 17)),
            TrendsCalendar.weekWindow(wednesday, DayOfWeek.SUNDAY),
        )
    }

    @Test
    fun window_saturdayStart_canEndBeforeToday() {
        assertEquals(
            WeekWindow(LocalDate(2026, 10, 17), LocalDate(2026, 10, 23)),
            TrendsCalendar.weekWindow(sunday, DayOfWeek.SATURDAY),
        )
        assertEquals(
            WeekWindow(LocalDate(2026, 10, 10), LocalDate(2026, 10, 16)),
            TrendsCalendar.weekWindow(wednesday, DayOfWeek.SATURDAY),
        )
    }

    @Test
    fun window_onTheLastDayOfTheWeek_includesToday() {
        val saturday = LocalDate(2026, 10, 24)
        assertEquals(saturday, TrendsCalendar.weekWindow(saturday, DayOfWeek.SUNDAY).end)
    }

    @Test
    fun window_isSevenDaysInOrder() {
        val days = TrendsCalendar.weekWindow(sunday, DayOfWeek.MONDAY).days
        assertEquals(7, days.size)
        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), days.map(TrendsCalendar::weekdayLabel))
    }

    @Test
    fun weekStarts_lastOneHoldsToday() {
        val starts = TrendsCalendar.weekStarts(wednesday, DayOfWeek.MONDAY, 4)
        assertEquals(
            listOf(LocalDate(2026, 9, 28), LocalDate(2026, 10, 5), LocalDate(2026, 10, 12), LocalDate(2026, 10, 19)),
            starts,
        )
    }

    @Test
    fun firstNeeded_coversTheSpanLastWeekAndTheNewFoodLookback() {
        // Last week (Coast's last check) starts Oct 12; 90 days before it.
        assertEquals(LocalDate(2026, 7, 14), TrendsCalendar.firstNeeded(sunday, Settings()))
        // 12 weeks back from Oct 19 is Aug 3; 90 days before it.
        assertEquals(LocalDate(2026, 5, 5), TrendsCalendar.firstNeeded(sunday, Settings(trendsRange = TrendsRange.ThreeMonths)))
    }

    @Test
    fun span_weekIsTheWindow_monthAndThreeMonthsRunToToday() {
        val week = TrendsCalendar.span(wednesday, DayOfWeek.MONDAY, TrendsRange.Week)
        assertEquals(TrendsCalendar.weekWindow(wednesday, DayOfWeek.MONDAY).start, week.start)
        assertEquals(7, week.dayCount)
        val month = TrendsCalendar.span(sunday, DayOfWeek.MONDAY, TrendsRange.FourWeeks)
        assertEquals(LocalDate(2026, 9, 28), month.start)
        assertEquals(sunday, month.end)
        assertEquals(28, month.dayCount)
        assertEquals(84, TrendsCalendar.span(sunday, DayOfWeek.MONDAY, TrendsRange.ThreeMonths).dayCount)
    }
}
