package com.opson.libraries.ui.time

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * A day as the app names it: relative when it is today or next to it, a date
 * otherwise. State carries this rather than a string, so the label is spelled
 * once, here, and tone or locale changes never touch a builder.
 */
sealed interface RelativeDay {
    data object Today : RelativeDay
    data object Yesterday : RelativeDay
    data object Tomorrow : RelativeDay
    data class Date(val date: LocalDate) : RelativeDay

    companion object {
        fun of(day: LocalDate, today: LocalDate): RelativeDay = when (day) {
            today -> Today
            today.minus(DatePeriod(days = 1)) -> Yesterday
            today.plus(DatePeriod(days = 1)) -> Tomorrow
            else -> Date(day)
        }
    }
}

/**
 * Date and time copy, in English, spelled by hand rather than through a
 * platform formatter so the JVM and iOS print the same thing whatever the
 * device locale is (the same reason `EnglishNutritionFormatter` exists). Pure,
 * so builders and tests call it directly. Not for nutrition numbers.
 */
object DateCopy {

    /** `Today`, `Yesterday`, `Tomorrow`, or `Thu, Oct 22`. */
    fun label(day: RelativeDay): String = when (day) {
        RelativeDay.Today -> "Today"
        RelativeDay.Yesterday -> "Yesterday"
        RelativeDay.Tomorrow -> "Tomorrow"
        is RelativeDay.Date -> weekdayMonthDay(day.date)
    }

    /** `Thu, Oct 22`. */
    fun weekdayMonthDay(date: LocalDate): String = "${shortWeekday(date.dayOfWeek)}, ${monthDay(date)}"

    /** `Sunday, Oct 25`, for subtitles that name the day in full. */
    fun longWeekdayMonthDay(date: LocalDate): String = "${longWeekday(date.dayOfWeek)}, ${monthDay(date)}"

    /** `Oct 22`. */
    fun monthDay(date: LocalDate): String = "${shortMonth(date.month)} ${date.day}"

    /** `October 25`, the calendar cell's spoken label. */
    fun longMonthDay(date: LocalDate): String = "${longMonth(date.month)} ${date.day}"

    /** `October 2026`, the calendar title. */
    fun monthYear(date: LocalDate): String = "${longMonth(date.month)} ${date.year}"

    /** `Oct 19 to 25`, or `Oct 29 to Nov 4` across a month. */
    fun range(start: LocalDate, end: LocalDate): String =
        if (start.month == end.month && start.year == end.year) {
            "${monthDay(start)} to ${end.day}"
        } else {
            "${monthDay(start)} to ${monthDay(end)}"
        }

    /** `Oct 25, 9:41 PM`. */
    fun timestamp(dateTime: LocalDateTime): String = "${monthDay(dateTime.date)}, ${time(dateTime.hour, dateTime.minute)}"

    /** `9:41 PM`, `12:05 AM`, `12:00 PM`. */
    fun time(hour: Int, minute: Int): String {
        require(hour in 0..23 && minute in 0..59) { "Not a time: $hour:$minute" }
        val half = if (hour < 12) "AM" else "PM"
        val clock = when (val h = hour % 12) {
            0 -> 12
            else -> h
        }
        return "$clock:${minute.toString().padStart(2, '0')} $half"
    }

    fun shortWeekday(day: DayOfWeek): String = longWeekday(day).take(3)

    fun longWeekday(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Monday"
        DayOfWeek.TUESDAY -> "Tuesday"
        DayOfWeek.WEDNESDAY -> "Wednesday"
        DayOfWeek.THURSDAY -> "Thursday"
        DayOfWeek.FRIDAY -> "Friday"
        DayOfWeek.SATURDAY -> "Saturday"
        DayOfWeek.SUNDAY -> "Sunday"
    }

    fun shortMonth(month: Month): String = longMonth(month).take(3)

    fun longMonth(month: Month): String = when (month) {
        Month.JANUARY -> "January"
        Month.FEBRUARY -> "February"
        Month.MARCH -> "March"
        Month.APRIL -> "April"
        Month.MAY -> "May"
        Month.JUNE -> "June"
        Month.JULY -> "July"
        Month.AUGUST -> "August"
        Month.SEPTEMBER -> "September"
        Month.OCTOBER -> "October"
        Month.NOVEMBER -> "November"
        Month.DECEMBER -> "December"
    }
}
