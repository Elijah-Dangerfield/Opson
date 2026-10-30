package com.opson.libraries.diary.ui

import com.opson.libraries.ui.components.text.DateParts
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A typed birthday, as onboarding's age question and Profile check it. The problems never say
 * where an age line is: the age gate's question is neutral, and its own rule decides on Continue.
 */
class BirthdaysTest {

    private val today = LocalDate(2026, 10, 25)

    @Test
    fun nothingIsSaid_untilEveryBoxIsTyped() {
        assertNull(Birthdays.problem(DateParts(day = "31", month = "04"), today))
        assertNull(Birthdays.dateOrNull(DateParts(day = "31", month = "04"), today))
    }

    @Test
    fun noSuchDay_isNotADate() {
        assertEquals(BirthdayProblem.NotADate, Birthdays.problem(DateParts(day = "31", month = "04", year = "1990"), today))
    }

    @Test
    fun aDayToCome_orAnAgeOutsideTheCallers_isTheYear() {
        assertEquals(BirthdayProblem.OutOfRange, Birthdays.problem(DateParts.of(LocalDate(2027, 1, 1)), today))
        assertEquals(BirthdayProblem.OutOfRange, Birthdays.problem(DateParts.of(LocalDate(1890, 1, 1)), today))
        val twelve = DateParts.of(LocalDate(2014, 1, 1))
        assertNull(Birthdays.problem(twelve, today), "any age for the age question, which decides on Continue")
        assertEquals(BirthdayProblem.OutOfRange, Birthdays.problem(twelve, today, Birthdays.EQUATION_AGES), "too young for the equation")
    }

    @Test
    fun aBirthday_isTheDateItMakes() {
        assertEquals(LocalDate(1992, 4, 18), Birthdays.dateOrNull(DateParts(day = "18", month = "4", year = "1992"), today))
    }
}
