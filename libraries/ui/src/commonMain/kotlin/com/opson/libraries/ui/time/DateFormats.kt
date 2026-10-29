@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.ui.time

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * How a date or a time is written: the month and weekday names, the order of a date's parts, and
 * the clock. [DateCopy] reads these; nothing else should.
 *
 * [EnglishDateFormats] spells US English by hand, the same on the JVM and on iOS, for tests and
 * previews. The app installs the platform's own, which follow the device's language and region
 * (`AndroidDateFormats`, `IosDateFormats`).
 *
 * Hidden from the iOS header, as Swift never uses it.
 */
@HiddenFromObjC
interface DateFormats {
    /** `Thu, Oct 22`. */
    fun weekdayMonthDay(date: LocalDate): String

    /** `Sunday, Oct 25`. */
    fun longWeekdayMonthDay(date: LocalDate): String

    /** `Oct 22`. */
    fun monthDay(date: LocalDate): String

    /** `October 25`. */
    fun longMonthDay(date: LocalDate): String

    /** `May 11, 1998`. */
    fun monthDayYear(date: LocalDate): String

    /** `Aug 1, 2026`. */
    fun shortMonthDayYear(date: LocalDate): String

    /** `October 2026`. */
    fun monthYear(date: LocalDate): String

    /** `10/19`, in the order the region writes a month and day as numbers (`19/10`, `19.10.`). */
    fun numericMonthDay(date: LocalDate): String

    /** `Oct 25, 9:41 PM`. */
    fun timestamp(dateTime: LocalDateTime): String

    /** `9:41 PM`, or `21:41` where the clock runs to 24. */
    fun time(hour: Int, minute: Int): String

    fun longWeekday(day: DayOfWeek): String

    fun shortWeekday(day: DayOfWeek): String

    /** One letter or the region's shortest form: `M`, `T`, for a row of days too narrow for `Mon`. */
    fun narrowWeekday(day: DayOfWeek): String

    fun longMonth(month: Month): String

    fun shortMonth(month: Month): String
}

/**
 * [DateFormats] from Unicode date skeletons, which a platform turns into the pattern its locale
 * uses ("MMMd" is `MMM d` in the US and `d MMM` in the UK). A platform only implements [format].
 */
@HiddenFromObjC
abstract class SkeletonDateFormats : DateFormats {

    /** [dateTime] in the pattern the device's locale has for [skeleton]. */
    protected abstract fun format(dateTime: LocalDateTime, skeleton: String): String

    override fun weekdayMonthDay(date: LocalDate): String = format(date.atNoon(), "EEEMMMd")

    override fun longWeekdayMonthDay(date: LocalDate): String = format(date.atNoon(), "EEEEMMMd")

    override fun monthDay(date: LocalDate): String = format(date.atNoon(), "MMMd")

    override fun longMonthDay(date: LocalDate): String = format(date.atNoon(), "MMMMd")

    override fun monthDayYear(date: LocalDate): String = format(date.atNoon(), "yMMMMd")

    override fun shortMonthDayYear(date: LocalDate): String = format(date.atNoon(), "yMMMd")

    override fun monthYear(date: LocalDate): String = format(date.atNoon(), "yMMMM")

    override fun numericMonthDay(date: LocalDate): String = format(date.atNoon(), "Md")

    override fun timestamp(dateTime: LocalDateTime): String = format(dateTime, "MMMdjmm")

    override fun time(hour: Int, minute: Int): String {
        require(hour in 0..23 && minute in 0..59) { "Not a time: $hour:$minute" }
        return format(LocalDateTime(REFERENCE_YEAR, Month.JANUARY, 1, hour, minute), "jmm")
    }

    // The stand-alone forms (c, L): a name on its own, which some languages inflect differently
    // from the same name inside a date.
    override fun longWeekday(day: DayOfWeek): String = format(dayOf(day), "cccc")

    override fun shortWeekday(day: DayOfWeek): String = format(dayOf(day), "ccc")

    override fun narrowWeekday(day: DayOfWeek): String = format(dayOf(day), "ccccc")

    override fun longMonth(month: Month): String = format(LocalDateTime(REFERENCE_YEAR, month, 1, 12, 0), "LLLL")

    override fun shortMonth(month: Month): String = format(LocalDateTime(REFERENCE_YEAR, month, 1, 12, 0), "LLL")

    private fun LocalDate.atNoon(): LocalDateTime = LocalDateTime(year, month, day, 12, 0)

    /** A day that falls on [day]: 1 January 2024 was a Monday. */
    private fun dayOf(day: DayOfWeek): LocalDateTime = LocalDateTime(REFERENCE_YEAR, Month.JANUARY, 1 + day.ordinal, 12, 0)

    private companion object {
        const val REFERENCE_YEAR = 2024
    }
}

/** US English spelled by hand, identical on every platform: what tests and previews read. */
@HiddenFromObjC
object EnglishDateFormats : DateFormats {

    override fun weekdayMonthDay(date: LocalDate): String = "${shortWeekday(date.dayOfWeek)}, ${monthDay(date)}"

    override fun longWeekdayMonthDay(date: LocalDate): String = "${longWeekday(date.dayOfWeek)}, ${monthDay(date)}"

    override fun monthDay(date: LocalDate): String = "${shortMonth(date.month)} ${date.day}"

    override fun longMonthDay(date: LocalDate): String = "${longMonth(date.month)} ${date.day}"

    override fun monthDayYear(date: LocalDate): String = "${longMonth(date.month)} ${date.day}, ${date.year}"

    override fun shortMonthDayYear(date: LocalDate): String = "${monthDay(date)}, ${date.year}"

    override fun monthYear(date: LocalDate): String = "${longMonth(date.month)} ${date.year}"

    override fun numericMonthDay(date: LocalDate): String = "${date.month.ordinal + 1}/${date.day}"

    override fun timestamp(dateTime: LocalDateTime): String = "${monthDay(dateTime.date)}, ${time(dateTime.hour, dateTime.minute)}"

    override fun time(hour: Int, minute: Int): String {
        require(hour in 0..23 && minute in 0..59) { "Not a time: $hour:$minute" }
        val half = if (hour < 12) "AM" else "PM"
        val clock = when (val h = hour % 12) {
            0 -> 12
            else -> h
        }
        return "$clock:${minute.toString().padStart(2, '0')} $half"
    }

    override fun shortWeekday(day: DayOfWeek): String = longWeekday(day).take(3)

    override fun narrowWeekday(day: DayOfWeek): String = longWeekday(day).take(1)

    override fun longWeekday(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Monday"
        DayOfWeek.TUESDAY -> "Tuesday"
        DayOfWeek.WEDNESDAY -> "Wednesday"
        DayOfWeek.THURSDAY -> "Thursday"
        DayOfWeek.FRIDAY -> "Friday"
        DayOfWeek.SATURDAY -> "Saturday"
        DayOfWeek.SUNDAY -> "Sunday"
    }

    override fun shortMonth(month: Month): String = longMonth(month).take(3)

    override fun longMonth(month: Month): String = when (month) {
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
