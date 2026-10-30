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

    /** The box after [part], or null after the last. */
    fun after(part: DatePart): DatePart? = parts.getOrNull(parts.indexOf(part) + 1)

    /** The box before [part], or null before the first. */
    fun before(part: DatePart): DatePart? = parts.getOrNull(parts.indexOf(part) - 1)

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
 * Typing a date as one run of digits (`DateField`). Pure, so the screen and its tests agree on
 * what a keystroke, a paste or an autofill does.
 *
 * The field holds one string: the digits typed, in the region's [DateOrder]. The boxes on screen
 * are that string read box by box ([boxes]), so there is no focus to hand from box to box and no
 * keystroke can land between two of them.
 */
object DateEntry {

    /**
     * The boxes [typed] fills, in [order]. Each box takes digits until it is done: the year at
     * four, a day or month at two, or at one when that digit can only stand alone (a month from 2
     * to 9, a day from 4 to 9) or when the next digit could not follow it (a 1 then a 5 is
     * January and the 5th, a 3 then a 4 is the 3rd and the start of the year).
     */
    fun boxes(typed: String, order: DateOrder): DateParts {
        var parts = DateParts()
        var rest = typed.filter { it in '0'..'9' }
        for (part in order.parts) {
            if (rest.isEmpty()) break
            val taken = takes(part, rest)
            parts = parts.with(part, rest.take(taken))
            rest = rest.drop(taken)
        }
        return parts
    }

    /**
     * The digits that fill the boxes with [parts], as far as they run without a gap. A box the
     * next one's first digit would otherwise join is padded to its length (a 1 before a 2 is
     * "01"), so reading them back puts each digit in the same box.
     */
    fun typed(parts: DateParts, order: DateOrder): String {
        val filled = order.parts
            .takeWhile { parts[it].isNotEmpty() }
            .map { part -> part to parts[part].filter { it in '0'..'9' }.take(part.length) }
        return filled.mapIndexed { index, (part, text) ->
            val next = filled.getOrNull(index + 1)?.second?.firstOrNull()
            if (next != null && takes(part, text + next) > text.length) text.padStart(part.length, '0') else text
        }.joinToString(separator = "")
    }

    /**
     * What the field holds after an edit took it from [was] to [now]. A whole date arriving at
     * once (a paste, the phone's autofill, a simulator typing) replaces everything, in [order] or
     * whichever order makes it a date ("1998-05-11" in a day-first region). Otherwise digits are
     * kept, a separator ends the box it follows ("5/11" is May and the 11th), and digits past the
     * last box are dropped.
     */
    fun edit(was: String, now: String, order: DateOrder): String {
        if (now == was) return was
        val inserted = inserted(was, now)
        if (inserted.length > 1) whole(inserted, order)?.let { return typed(it, order) }
        var typed = ""
        now.forEach { char -> typed = if (char in '0'..'9') typed + char else close(typed, order) }
        return typed(boxes(typed, order), order)
    }

    /**
     * The box the next digit goes into: the last one typed in while it can take more, else the
     * one after. Null once the last box is done.
     */
    fun current(typed: String, order: DateOrder): DatePart? {
        val parts = boxes(typed, order)
        val last = order.parts.lastOrNull { parts[it].isNotEmpty() } ?: return order.parts.first()
        return if (isDone(last, parts[last])) order.after(last) else last
    }

    /**
     * Whether [part] is finished: it is full, or its first digit can't start a longer one (a
     * month from 2 to 9, a day from 4 to 9).
     */
    fun isDone(part: DatePart, text: String): Boolean = when {
        text.length >= part.length -> true
        text.length != 1 -> false
        part == DatePart.Month -> text[0] in '2'..'9'
        part == DatePart.Day -> text[0] in '4'..'9'
        else -> false
    }

    /** How many of [digits], which are not empty, [part] takes. */
    private fun takes(part: DatePart, digits: String): Int {
        val next = digits.getOrNull(1)
        val alone = when (part) {
            DatePart.Year -> false
            DatePart.Month -> digits[0] in '2'..'9' || (digits[0] == '1' && next != null && next !in '0'..'2')
            DatePart.Day -> digits[0] in '4'..'9' || (digits[0] == '3' && next != null && next !in '0'..'1')
        }
        return if (alone) 1 else minOf(part.length, digits.length)
    }

    /** [typed] with the box it ends in closed, as a separator after it says: a day or month padded to two. */
    private fun close(typed: String, order: DateOrder): String {
        val parts = boxes(typed, order)
        val last = order.parts.lastOrNull { parts[it].isNotEmpty() } ?: return typed
        if (last == DatePart.Year) return typed
        return typed(parts.with(last, parts[last].padStart(last.length, '0')), order)
    }

    /** What an edit from [was] to [now] put in: [now] less what it shares with [was] at either end. */
    private fun inserted(was: String, now: String): String {
        val start = now.commonPrefixWith(was).length
        val end = now.commonSuffixWith(was).length.coerceAtMost(minOf(now.length, was.length) - start)
        return now.substring(start, now.length - end)
    }

    private fun groups(raw: String): List<String> = raw.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }

    /** A whole date in [raw], by its separators or as eight digits, with the year in full. Null when no order makes one. */
    private fun whole(raw: String, order: DateOrder): DateParts? {
        val groups = groups(raw)
        val candidates = when {
            groups.size == WHOLE_PARTS -> ordersFor(order).map { o -> fromGroups(groups, o) }
            groups.size == 1 && groups[0].length == EIGHT_DIGITS -> ordersFor(order).map { o -> fromDigits(groups[0], o) }
            else -> emptyList()
        }
        return candidates
            .firstOrNull { it.year.length == DatePart.Year.length && it.dateOrNull() != null }
            ?.let { DateParts.of(it.dateOrNull()) }
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
