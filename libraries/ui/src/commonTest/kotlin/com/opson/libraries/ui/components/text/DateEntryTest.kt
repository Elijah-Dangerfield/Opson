package com.opson.libraries.ui.components.text

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A birthday typed rather than picked (owner feedback, 2026-10-30: "a date picker for the birthday
 * is annoying"): the boxes follow the region's order, move on when a box is done, take a whole
 * date pasted or autofilled into one box, and only make a date that exists.
 */
class DateEntryTest {

    @Test
    fun theOrder_followsTheRegion() {
        assertEquals(DateOrder.MonthDayYear, DateOrder.forRegion("US"))
        assertEquals(DateOrder.DayMonthYear, DateOrder.forRegion("GB"))
        assertEquals(DateOrder.YearMonthDay, DateOrder.forRegion("jp"))
        assertEquals(DateOrder.DayMonthYear, DateOrder.forRegion(null))
    }

    @Test
    fun aBox_takesDigitsOnly_upToItsLength() {
        val typed = DateEntry.type(DateParts(), DatePart.Day, "1a", DateOrder.DayMonthYear)
        assertEquals("1", typed.day)
        assertEquals("199", DateEntry.type(DateParts(), DatePart.Year, "199", DateOrder.DayMonthYear).year)
    }

    @Test
    fun aBox_isDone_whenFull_orWhenItsFirstDigitCanOnlyStandAlone() {
        assertTrue(DateEntry.isDone(DatePart.Month, "4"), "no month starts with 4 and goes on")
        assertFalse(DateEntry.isDone(DatePart.Month, "1"), "10, 11 or 12 may follow")
        assertTrue(DateEntry.isDone(DatePart.Day, "4"))
        assertFalse(DateEntry.isDone(DatePart.Day, "3"), "30 or 31 may follow")
        assertTrue(DateEntry.isDone(DatePart.Day, "18"))
        assertFalse(DateEntry.isDone(DatePart.Year, "199"))
        assertTrue(DateEntry.isDone(DatePart.Year, "1992"))
        assertFalse(DateEntry.isDone(DatePart.Day, ""))
    }

    @Test
    fun aWholeDate_inOneBox_isSplitAcrossAllThree() {
        val us = DateEntry.type(DateParts(), DatePart.Month, "05111998", DateOrder.MonthDayYear)
        assertEquals(LocalDate(1998, 5, 11), us.dateOrNull())

        val separated = DateEntry.type(DateParts(), DatePart.Day, "11/5/1998", DateOrder.DayMonthYear)
        assertEquals(DateParts(day = "11", month = "05", year = "1998"), separated)
    }

    @Test
    fun aWholeDate_inAnotherOrder_isReadTheWayThatMakesADate() {
        val iso = DateEntry.type(DateParts(), DatePart.Day, "19980511", DateOrder.DayMonthYear)
        assertEquals(LocalDate(1998, 5, 11), iso.dateOrNull())
    }

    @Test
    fun tooManyDigits_thatMakeNoDate_keepTheBoxsFirstDigits() {
        val typed = DateEntry.type(DateParts(month = "04"), DatePart.Day, "123", DateOrder.DayMonthYear)
        assertEquals(DateParts(day = "12", month = "04"), typed)
    }

    @Test
    fun onlyRealDates_areDates() {
        assertNull(DateParts(day = "31", month = "04", year = "1990").dateOrNull(), "April has 30 days")
        assertNull(DateParts(day = "29", month = "02", year = "1990").dateOrNull())
        assertEquals(LocalDate(1992, 2, 29), DateParts(day = "29", month = "2", year = "1992").dateOrNull())
        assertNull(DateParts(day = "1", month = "13", year = "1990").dateOrNull())
        assertNull(DateParts(day = "1", month = "1", year = "199").dateOrNull(), "a year in full")
    }

    @Test
    fun aDate_showsInItsBoxes_padded() {
        assertEquals(DateParts(day = "02", month = "07", year = "1990"), DateParts.of(LocalDate(1990, 7, 2)))
        assertTrue(DateParts.of(null).isBlank)
    }
}
