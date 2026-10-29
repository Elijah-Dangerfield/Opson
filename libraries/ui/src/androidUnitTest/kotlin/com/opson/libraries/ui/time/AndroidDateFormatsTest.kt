package com.opson.libraries.ui.time

import androidx.test.core.app.ApplicationProvider
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals

/**
 * The platform's dates against the hand-spelled English every test and preview reads: in US
 * English a device says what it said before the dates followed the locale, and elsewhere the
 * region's own order and names.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidDateFormatsTest {

    private val formats = AndroidDateFormats(ApplicationProvider.getApplicationContext())
    private val sunday = LocalDate(2026, 10, 25)
    private lateinit var before: Locale

    @Before
    fun rememberLocale() {
        before = Locale.getDefault()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(before)
    }

    @Test
    fun usEnglishReadsAsTheEnglishFallback() {
        Locale.setDefault(Locale.US)
        val english = EnglishDateFormats

        assertEquals(english.weekdayMonthDay(sunday), formats.weekdayMonthDay(sunday))
        assertEquals(english.longWeekdayMonthDay(sunday), formats.longWeekdayMonthDay(sunday))
        assertEquals(english.monthDay(sunday), formats.monthDay(sunday))
        assertEquals(english.longMonthDay(sunday), formats.longMonthDay(sunday))
        assertEquals(english.monthDayYear(sunday), formats.monthDayYear(sunday))
        assertEquals(english.shortMonthDayYear(sunday), formats.shortMonthDayYear(sunday))
        assertEquals(english.monthYear(sunday), formats.monthYear(sunday))
        assertEquals(english.numericMonthDay(sunday), formats.numericMonthDay(sunday))
        assertEquals(english.longWeekday(DayOfWeek.THURSDAY), formats.longWeekday(DayOfWeek.THURSDAY))
        assertEquals(english.shortWeekday(DayOfWeek.THURSDAY), formats.shortWeekday(DayOfWeek.THURSDAY))
        assertEquals(english.narrowWeekday(DayOfWeek.THURSDAY), formats.narrowWeekday(DayOfWeek.THURSDAY))
        assertEquals(english.longMonth(Month.MAY), formats.longMonth(Month.MAY))
        assertEquals(english.shortMonth(Month.SEPTEMBER), formats.shortMonth(Month.SEPTEMBER))
        // ICU puts a narrow no-break space before AM and PM; it reads the same.
        assertEquals(english.time(21, 41), formats.time(21, 41).replace(' ', ' '))
        assertEquals(
            english.timestamp(LocalDateTime(2026, 10, 25, 0, 5)),
            formats.timestamp(LocalDateTime(2026, 10, 25, 0, 5)).replace(' ', ' '),
        )
    }

    @Test
    fun anotherRegionUsesItsOwnOrderAndNames() {
        Locale.setDefault(Locale.GERMANY)

        assertEquals("25. Okt.", formats.monthDay(sunday))
        assertEquals("Oktober 2026", formats.monthYear(sunday))
        assertEquals("25.10.", formats.numericMonthDay(sunday))
    }
}
