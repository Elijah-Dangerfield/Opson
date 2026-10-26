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
        assertEquals(listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"), days.map(TrendsCalendar::weekdayLabel))
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
    fun firstNeeded_coversTheChartAndTheNewFoodLookback() {
        val week = TrendsCalendar.firstNeeded(sunday, Settings())
        assertEquals(LocalDate(2026, 7, 21), week) // Oct 19 minus 90 days
        val threeMonths = TrendsCalendar.firstNeeded(sunday, Settings(trendsRange = TrendsRange.ThreeMonths))
        assertEquals(week, threeMonths) // 12 weeks back from Oct 19 is Aug 3, inside the lookback
    }
}
