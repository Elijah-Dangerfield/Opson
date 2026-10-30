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
        val typed = DateEntry.type(DateParts(), DatePart.Day, "1a", DateOrder.DayMonthYear).parts
        assertEquals("1", typed.day)
        assertEquals("199", DateEntry.type(DateParts(), DatePart.Year, "199", DateOrder.DayMonthYear).parts.year)
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
        val us = DateEntry.type(DateParts(), DatePart.Month, "05111998", DateOrder.MonthDayYear).parts
        assertEquals(LocalDate(1998, 5, 11), us.dateOrNull())

        val separated = DateEntry.type(DateParts(), DatePart.Day, "11/5/1998", DateOrder.DayMonthYear).parts
        assertEquals(DateParts(day = "11", month = "05", year = "1998"), separated)
    }

    @Test
    fun aWholeDate_inAnotherOrder_isReadTheWayThatMakesADate() {
        val iso = DateEntry.type(DateParts(), DatePart.Day, "19980511", DateOrder.DayMonthYear).parts
        assertEquals(LocalDate(1998, 5, 11), iso.dateOrNull())
    }

    @Test
    fun digitsPastAFullBox_goOnToTheNextBoxWithRoom() {
        val typed = DateEntry.type(DateParts(month = "04"), DatePart.Day, "123", DateOrder.DayMonthYear)
        assertEquals(DateParts(day = "12", month = "04", year = "3"), typed.parts, "Month is full, so the 3 starts the year")
        assertEquals(DatePart.Year, typed.focus)
    }

    // The simulator bug: "05111998" typed quickly. Focus moves a beat behind the digits, so some
    // land in the box before the one the cursor is heading to. Each one has to end up where it
    // would have gone.
    @Test
    fun digitsTypedQuickly_intoABoxThatIsAlreadyDone_carryOn() {
        val order = DateOrder.MonthDayYear
        var parts = DateParts()
        "05111998".forEach { digit -> parts = DateEntry.type(parts, DatePart.Month, parts.month + digit, order).parts }
        assertEquals(DateParts(month = "05", day = "11", year = "1998"), parts)
    }

    @Test
    fun aDigitTypedLate_intoTheBoxBefore_joinsTheBoxItWasMeantFor() {
        val typed = DateEntry.type(DateParts(month = "05", day = "11", year = "199"), DatePart.Month, "058", DateOrder.MonthDayYear)
        assertEquals(DateParts(month = "05", day = "11", year = "1998"), typed.parts)
    }

    @Test
    fun aMonthThatStandsAlone_passesTheNextDigitOn() {
        val order = DateOrder.MonthDayYear
        val five = DateEntry.type(DateParts(), DatePart.Month, "5", order)
        assertEquals(DatePart.Day, five.focus)
        val next = DateEntry.type(five.parts, DatePart.Month, "51", order)
        assertEquals(DateParts(month = "5", day = "1"), next.parts, "5 is May; the 1 starts the day")
        assertEquals(DatePart.Day, next.focus)
    }

    @Test
    fun aFinishedBox_movesOn_onlyWhenADigitWasAdded() {
        val order = DateOrder.MonthDayYear
        assertEquals(DatePart.Day, DateEntry.type(DateParts(month = "0"), DatePart.Month, "05", order).focus)
        assertNull(DateEntry.type(DateParts(month = "05"), DatePart.Month, "5", order).focus, "deleting the 0 stays put")
        assertNull(DateEntry.type(DateParts(month = "05", day = "11"), DatePart.Year, "1998", order).focus, "nothing after the year")
    }

    @Test
    fun aWholeDate_inTheFirstBox_inEveryShape() {
        listOf("05/11/1998", "05111998", "5/11/1998", "5-11-1998", "05.11.1998").forEach { pasted ->
            val typed = DateEntry.type(DateParts(), DatePart.Month, pasted, DateOrder.MonthDayYear)
            assertEquals(DateParts(month = "05", day = "11", year = "1998"), typed.parts, pasted)
            assertEquals(DatePart.Year, typed.focus, pasted)
        }
    }

    @Test
    fun aWholeDate_replacesWhatTheBoxesHeld() {
        val typed = DateEntry.type(DateParts(month = "12", day = "30", year = "2001"), DatePart.Day, "05/11/1998", DateOrder.MonthDayYear)
        assertEquals(DateParts(month = "05", day = "11", year = "1998"), typed.parts)
    }

    @Test
    fun separatedGroups_thatAreNotAWholeDate_goOneToABox() {
        val typed = DateEntry.type(DateParts(), DatePart.Month, "5/11", DateOrder.MonthDayYear)
        assertEquals(DateParts(month = "5", day = "11"), typed.parts)
        assertEquals(DatePart.Year, typed.focus)

        val notADate = DateEntry.type(DateParts(), DatePart.Day, "31/04/1990", DateOrder.DayMonthYear)
        assertEquals(DateParts(day = "31", month = "04", year = "1990"), notADate.parts, "kept as typed, for the field to say it is no date")
    }

    @Test
    fun theYearFirst_carriesOnIntoMonthThenDay() {
        val order = DateOrder.YearMonthDay
        var parts = DateParts()
        "19980511".forEach { digit -> parts = DateEntry.type(parts, DatePart.Year, parts.year + digit, order).parts }
        assertEquals(DateParts(year = "1998", month = "05", day = "11"), parts)
    }

    @Test
    fun deleteInAnEmptyBox_takesTheLastDigitOfTheOneBefore() {
        val order = DateOrder.MonthDayYear
        val back = DateEntry.deleteBefore(DateParts(month = "05", day = "11"), DatePart.Year, order)
        assertEquals(DateParts(month = "05", day = "1"), back.parts)
        assertEquals(DatePart.Day, back.focus)

        val first = DateEntry.deleteBefore(DateParts(month = "05"), DatePart.Month, order)
        assertEquals(DateParts(month = "05"), first.parts, "nothing before the first box")
        assertNull(first.focus)

        val dayFirst = DateEntry.deleteBefore(DateParts(day = "11"), DatePart.Month, DateOrder.DayMonthYear)
        assertEquals(DateParts(day = "1"), dayFirst.parts)
    }

    @Test
    fun theCursorStarts_inTheFirstEmptyBox() {
        assertEquals(DatePart.Month, DateEntry.firstEmpty(DateParts(), DateOrder.MonthDayYear))
        assertEquals(DatePart.Day, DateEntry.firstEmpty(DateParts(month = "05"), DateOrder.MonthDayYear))
        assertEquals(DatePart.Year, DateEntry.firstEmpty(DateParts(), DateOrder.YearMonthDay))
        assertNull(DateEntry.firstEmpty(DateParts.of(LocalDate(1998, 5, 11)), DateOrder.MonthDayYear))
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
