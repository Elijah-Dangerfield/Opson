package com.opson.libraries.diary

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** What a recap covers (`release.recaps`). [wire] is the route argument; never rename one. */
enum class RecapKind(val wire: String) {
    Week("week"),
    Month("month"),
    ;

    companion object {
        fun fromWire(value: String?): RecapKind? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * One week, by the person's own week start, or one calendar month: the days a recap covers. Pure,
 * so Today's banner, Wrap up the day and the recap itself agree on where a week ends.
 */
data class RecapPeriod(val kind: RecapKind, val start: LocalDate) {
    val end: LocalDate
        get() = when (kind) {
            RecapKind.Week -> start.plus(DAYS_IN_WEEK - 1, DateTimeUnit.DAY)
            RecapKind.Month -> start.plus(DatePeriod(months = 1)).minus(1, DateTimeUnit.DAY)
        }

    /** Every date in it, oldest first. */
    val days: List<LocalDate> get() = generateSequence(start) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= end }.toList()

    val dayCount: Int get() = (end.toEpochDays() - start.toEpochDays()).toInt() + 1

    fun previous(): RecapPeriod = when (kind) {
        RecapKind.Week -> copy(start = start.minus(DAYS_IN_WEEK, DateTimeUnit.DAY))
        RecapKind.Month -> copy(start = start.minus(DatePeriod(months = 1)))
    }

    fun next(): RecapPeriod = when (kind) {
        RecapKind.Week -> copy(start = start.plus(DAYS_IN_WEEK, DateTimeUnit.DAY))
        RecapKind.Month -> copy(start = start.plus(DatePeriod(months = 1)))
    }

    /** Over by [today]: its last day is behind it. */
    fun endedBy(today: LocalDate): Boolean = end < today

    /** Stable and unique for the banner's memory: `week-2026-10-19`, `month-2026-10-01`. */
    val key: String get() = "${kind.wire}-$start"

    companion object {
        const val DAYS_IN_WEEK = 7

        /** The week holding [date], starting on [weekStart]. */
        fun weekOf(date: LocalDate, weekStart: DayOfWeek): RecapPeriod {
            val back = (date.dayOfWeek.isoDayNumber - weekStart.isoDayNumber + DAYS_IN_WEEK) % DAYS_IN_WEEK
            return RecapPeriod(RecapKind.Week, date.minus(back, DateTimeUnit.DAY))
        }

        /** The calendar month holding [date]. */
        fun monthOf(date: LocalDate): RecapPeriod = RecapPeriod(RecapKind.Month, LocalDate(date.year, date.month, 1))

        fun of(kind: RecapKind, date: LocalDate, weekStart: DayOfWeek): RecapPeriod = when (kind) {
            RecapKind.Week -> weekOf(date, weekStart)
            RecapKind.Month -> monthOf(date)
        }

        /** The latest week that is over by [today]: the one before today's. */
        fun lastWeek(today: LocalDate, weekStart: DayOfWeek): RecapPeriod = weekOf(today, weekStart).previous()

        /** The latest month that is over by [today]. */
        fun lastMonth(today: LocalDate): RecapPeriod = monthOf(today).previous()
    }
}
