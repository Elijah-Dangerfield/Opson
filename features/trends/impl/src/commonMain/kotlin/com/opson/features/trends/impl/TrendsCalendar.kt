package com.opson.features.trends.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.ui.time.DateCopy
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Seven days, [start] to [end] inclusive. */
data class WeekWindow(val start: LocalDate, val end: LocalDate) {
    val days: List<LocalDate> get() = List(TrendsCalendar.DAYS_IN_WEEK) { start.plus(it, DateTimeUnit.DAY) }
}

/**
 * Which dates Trends looks at. Pure, so the window rules are tested once here
 * and the Trends and Nutrients builders agree on "this week".
 */
object TrendsCalendar {

    /** Weekly bars at 4 weeks and 3 months (the prototype's `WEEKLY` has 12). */
    const val DAYS_IN_WEEK = 7
    const val FOUR_WEEKS_BARS = 4
    const val THREE_MONTHS_BARS = 12

    /** How far back a food has to be absent to count as new this week. */
    const val NEW_FOOD_LOOKBACK_DAYS = 90

    /**
     * The last full week ending on or before [today] for [weekStart]: it ends on
     * the latest date not after today whose weekday is the day before
     * [weekStart]. With a Saturday start the week can end before today, as the
     * prototype does.
     */
    fun weekWindow(today: LocalDate, weekStart: DayOfWeek): WeekWindow {
        val lastDay = DayOfWeek((weekStart.isoDayNumber + LAST_DAY_OFFSET) % DAYS_IN_WEEK + 1)
        val back = (today.dayOfWeek.isoDayNumber - lastDay.isoDayNumber + DAYS_IN_WEEK) % DAYS_IN_WEEK
        val end = today.minus(back, DateTimeUnit.DAY)
        return WeekWindow(end.minus(DAYS_IN_WEEK - 1, DateTimeUnit.DAY), end)
    }

    /** The first day of the week (by [weekStart]) that holds [day]. */
    fun weekStartOf(day: LocalDate, weekStart: DayOfWeek): LocalDate {
        val back = (day.dayOfWeek.isoDayNumber - weekStart.isoDayNumber + DAYS_IN_WEEK) % DAYS_IN_WEEK
        return day.minus(back, DateTimeUnit.DAY)
    }

    /** First days of the last [count] weeks, oldest first; the last one holds [today]. */
    fun weekStarts(today: LocalDate, weekStart: DayOfWeek, count: Int): List<LocalDate> {
        val current = weekStartOf(today, weekStart)
        return List(count) { index -> current.minus((count - 1 - index) * DAYS_IN_WEEK, DateTimeUnit.DAY) }
    }

    fun barCount(range: TrendsRange): Int = when (range) {
        TrendsRange.Week -> DAYS_IN_WEEK
        TrendsRange.FourWeeks -> FOUR_WEEKS_BARS
        TrendsRange.ThreeMonths -> THREE_MONTHS_BARS
    }

    /**
     * The first date Trends needs: the chart's range, the week window and the
     * new-food lookback before it, so one `observeDays` covers every card.
     */
    fun firstNeeded(today: LocalDate, settings: Settings): LocalDate {
        val window = weekWindow(today, settings.weekStart)
        val lookback = window.start.minus(NEW_FOOD_LOOKBACK_DAYS, DateTimeUnit.DAY)
        val chart = when (settings.trendsRange) {
            TrendsRange.Week -> window.start
            else -> weekStarts(today, settings.weekStart, barCount(settings.trendsRange)).first()
        }
        return minOf(lookback, chart)
    }

    /** "Mo", "Tu": the first two letters of the weekday, as the prototype labels its week. */
    fun weekdayLabel(date: LocalDate): String = DateCopy.longWeekday(date.dayOfWeek).take(2)


    // weekStart - 1, in 0..6 before the +1: (iso + 5) % 7 + 1 steps back one day and wraps Monday to Sunday.
    private const val LAST_DAY_OFFSET = 5
}

/** What a day ate in energy, for the chart: `null` when nothing was logged or nothing logged had energy (a gap, not a zero). */
internal fun Day.intakeKcal(): Double? = if (hasLogged) totals().kcal.value else null
