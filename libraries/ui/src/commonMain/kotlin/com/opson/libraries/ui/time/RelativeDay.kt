package com.opson.libraries.ui.time

import com.opson.libraries.core.fillTemplate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.concurrent.Volatile

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
 * Date and time copy. Pure and synchronous, so builders and tests call it directly. Not for
 * nutrition numbers.
 *
 * Two parts, installed once at app start: the [DateFormats] (names, the order of a date's parts,
 * the clock), which on a device are the platform's own and follow its language and region, and
 * the [DateWords] ("Today", "Oct 19 to 25"), from `strings_ui.xml`. Until then, and in every test
 * and preview, it is US English spelled by hand ([EnglishDateFormats], [DateWords]' defaults), the
 * same on the JVM and on iOS.
 */
object DateCopy {
    @Volatile
    private var formats: DateFormats = EnglishDateFormats

    @Volatile
    private var words: DateWords = DateWords()

    fun install(formats: DateFormats) {
        this.formats = formats
    }

    fun install(words: DateWords) {
        this.words = words
    }

    /** `Today`, `Yesterday`, `Tomorrow`, or `Thu, Oct 22`. */
    fun label(day: RelativeDay): String = when (day) {
        RelativeDay.Today -> words.today
        RelativeDay.Yesterday -> words.yesterday
        RelativeDay.Tomorrow -> words.tomorrow
        is RelativeDay.Date -> weekdayMonthDay(day.date)
    }

    /** `Thu, Oct 22`. */
    fun weekdayMonthDay(date: LocalDate): String = formats.weekdayMonthDay(date)

    /** `Sunday, Oct 25`, for subtitles that name the day in full. */
    fun longWeekdayMonthDay(date: LocalDate): String = formats.longWeekdayMonthDay(date)

    /** `Oct 22`. */
    fun monthDay(date: LocalDate): String = formats.monthDay(date)

    /** `October 25`, the calendar cell's spoken label. */
    fun longMonthDay(date: LocalDate): String = formats.longMonthDay(date)

    /** `May 11, 1998`: a birthday, or the day a goal is forecast for. */
    fun monthDayYear(date: LocalDate): String = formats.monthDayYear(date)

    /** `Aug 1, 2026`: a day far enough off to need its year, said briefly. */
    fun shortMonthDayYear(date: LocalDate): String = formats.shortMonthDayYear(date)

    /** `October 2026`, the calendar title. */
    fun monthYear(date: LocalDate): String = formats.monthYear(date)

    /** `10/19`: a short axis label, month and day in the order the region writes them as numbers. */
    fun numericMonthDay(date: LocalDate): String = formats.numericMonthDay(date)

    /** `Oct 19 to 25`, or `Oct 29 to Nov 4` across a month. */
    fun range(start: LocalDate, end: LocalDate): String =
        if (start.month == end.month && start.year == end.year) {
            words.rangeSameMonth.fillTemplate(monthDay(start), end.day)
        } else {
            words.range.fillTemplate(monthDay(start), monthDay(end))
        }

    /** `Oct 25, 9:41 PM`. */
    fun timestamp(dateTime: LocalDateTime): String = formats.timestamp(dateTime)

    /** `9:41 PM`, `12:05 AM`, `12:00 PM`; `21:41` where the clock runs to 24. */
    fun time(hour: Int, minute: Int): String = formats.time(hour, minute)

    fun shortWeekday(day: DayOfWeek): String = formats.shortWeekday(day)

    /** `M`, `T`: for a row of days too narrow for [shortWeekday]. Not `shortWeekday(day).take(1)`. */
    fun narrowWeekday(day: DayOfWeek): String = formats.narrowWeekday(day)

    fun longWeekday(day: DayOfWeek): String = formats.longWeekday(day)

    fun shortMonth(month: Month): String = formats.shortMonth(month)

    fun longMonth(month: Month): String = formats.longMonth(month)
}

/**
 * The words [DateCopy] puts around dates, as `strings_ui.xml` has them. The defaults are its
 * English, for tests and previews; `DateWordsDriftTest` fails when the two differ.
 *
 * @property rangeSameMonth `Oct 19 to 25`: the start's month and day, then the end's day number.
 * @property range `Oct 29 to Nov 4`: both as month and day.
 */
data class DateWords(
    val today: String = "Today",
    val yesterday: String = "Yesterday",
    val tomorrow: String = "Tomorrow",
    val rangeSameMonth: String = "%1\$s to %2\$s",
    val range: String = "%1\$s to %2\$s",
)
