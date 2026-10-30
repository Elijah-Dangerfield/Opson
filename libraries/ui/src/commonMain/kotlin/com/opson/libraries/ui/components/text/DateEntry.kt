package com.opson.libraries.ui.components.text

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.number

/** One box of a typed date, and how many digits fills it. */
enum class DatePart(val length: Int) {
    Day(2),
    Month(2),
    Year(4),
}

/**
 * The order a date's boxes come in, as the phone's region writes dates: month first in the US,
 * year first in much of East Asia, Sweden, Hungary and Canada, day first nearly everywhere else.
 */
enum class DateOrder(val parts: List<DatePart>) {
    MonthDayYear(listOf(DatePart.Month, DatePart.Day, DatePart.Year)),
    DayMonthYear(listOf(DatePart.Day, DatePart.Month, DatePart.Year)),
    YearMonthDay(listOf(DatePart.Year, DatePart.Month, DatePart.Day)),
    ;

    companion object {
        /** The order for an ISO 3166 [countryCode]; day first when it is unknown. */
        fun forRegion(countryCode: String?): DateOrder = when (countryCode?.uppercase()) {
            in MONTH_FIRST -> MonthDayYear
            in YEAR_FIRST -> YearMonthDay
            else -> DayMonthYear
        }

        private val MONTH_FIRST = setOf("US", "AS", "GU", "MP", "PR", "UM", "VI", "FM", "MH", "PW", "PH")
        private val YEAR_FIRST = setOf("CN", "JP", "KR", "KP", "TW", "MN", "HU", "LT", "IR", "SE", "CA", "ZA")
    }
}

/** A date as typed, box by box. Each box holds digits only, at most its [DatePart.length]. */
data class DateParts(val day: String = "", val month: String = "", val year: String = "") {

    operator fun get(part: DatePart): String = when (part) {
        DatePart.Day -> day
        DatePart.Month -> month
        DatePart.Year -> year
    }

    fun with(part: DatePart, text: String): DateParts = when (part) {
        DatePart.Day -> copy(day = text)
        DatePart.Month -> copy(month = text)
        DatePart.Year -> copy(year = text)
    }

    val isBlank: Boolean get() = day.isEmpty() && month.isEmpty() && year.isEmpty()

    /** Every box typed, the year in full. */
    val isComplete: Boolean get() = day.isNotEmpty() && month.isNotEmpty() && year.length == DatePart.Year.length

    /** The date these make, or null while one is missing or the day isn't in that month. */
    fun dateOrNull(): LocalDate? {
        if (!isComplete) return null
        val y = year.toIntOrNull() ?: return null
        val m = month.toIntOrNull()?.takeIf { it in 1..MONTHS } ?: return null
        val d = day.toIntOrNull()?.takeIf { it in 1..daysIn(y, m) } ?: return null
        return LocalDate(y, m, d)
    }

    companion object {
        /** [date] as the boxes show it, day and month to two digits; empty for null. */
        fun of(date: LocalDate?): DateParts = date?.let {
            DateParts(
                day = it.day.toString().padStart(2, '0'),
                month = it.month.number.toString().padStart(2, '0'),
                year = it.year.toString(),
            )
        } ?: DateParts()

        private const val MONTHS = 12

        private fun daysIn(year: Int, month: Int): Int = when (Month(month)) {
            Month.FEBRUARY -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            Month.APRIL, Month.JUNE, Month.SEPTEMBER, Month.NOVEMBER -> 30
            else -> 31
        }
    }
}

/**
 * Typing into a date's boxes (`DateField`). Pure, so the screen and its tests agree on what a
 * keystroke, a paste or an autofill does.
 */
object DateEntry {

    /**
     * [raw] typed into [part]. Digits only, at most the box's length. More than fits is a whole
     * date, pasted or filled in by the phone into one box: it is split across all three, in
     * [order] or, when that makes no date, whichever order does ("1998-05-11" in a day-first
     * region). Failing that, the box keeps its first digits.
     */
    fun type(parts: DateParts, part: DatePart, raw: String, order: DateOrder): DateParts {
        val digits = raw.filter { it in '0'..'9' }
        if (digits.length <= part.length) return parts.with(part, digits)
        whole(raw, order)?.let { return it }
        return parts.with(part, digits.take(part.length))
    }

    /**
     * Whether [part] is finished, so focus can move to the next box: it is full, or its first
     * digit can't start a longer one (a month from 2 to 9, a day from 4 to 9).
     */
    fun isDone(part: DatePart, text: String): Boolean = when {
        text.length >= part.length -> true
        text.length != 1 -> false
        part == DatePart.Month -> text[0] in '2'..'9'
        part == DatePart.Day -> text[0] in '4'..'9'
        else -> false
    }

    /** A whole date in [raw], by its separators or as eight digits. Null when no order makes one. */
    private fun whole(raw: String, order: DateOrder): DateParts? {
        val groups = raw.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }
        val candidates = when {
            groups.size == WHOLE_PARTS -> ordersFor(order).map { o -> fromGroups(groups, o) }
            groups.size == 1 && groups[0].length == EIGHT_DIGITS -> ordersFor(order).map { o -> fromDigits(groups[0], o) }
            else -> emptyList()
        }
        return candidates.firstOrNull { it.dateOrNull() != null }?.let { DateParts.of(it.dateOrNull()) }
    }

    private fun ordersFor(preferred: DateOrder): List<DateOrder> = listOf(preferred) + DateOrder.entries.filter { it != preferred }

    private fun fromGroups(groups: List<String>, order: DateOrder): DateParts =
        order.parts.zip(groups).fold(DateParts()) { parts, (part, text) -> parts.with(part, text) }

    private fun fromDigits(digits: String, order: DateOrder): DateParts {
        var at = 0
        return order.parts.fold(DateParts()) { parts, part ->
            parts.with(part, digits.substring(at, at + part.length)).also { at += part.length }
        }
    }

    private const val WHOLE_PARTS = 3
    private const val EIGHT_DIGITS = 8
}
