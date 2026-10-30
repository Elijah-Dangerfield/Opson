package com.opson.libraries.ui.components.text

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A birthday typed rather than picked (owner feedback, 2026-10-30: "a date picker for the birthday
 * is annoying"). The field holds one run of digits and the boxes are read from it, in the
 * region's order; a whole date pasted or autofilled lands in all three, and only a date that
 * exists is a date.
 *
 * [keys] and [delete] edit the way iOS does: UIKit's `insertText` commits the key at the cursor
 * and `deleteBackward` takes the character before it, and Compose hands the field the whole new
 * text. The field keeps its cursor at the end, so that is where both happen.
 */
class DateEntryTest {

    private val us = DateOrder.MonthDayYear

    private fun keys(text: String, order: DateOrder = us, from: String = ""): String =
        text.fold(from) { typed, key -> DateEntry.edit(typed, typed + key, order) }

    private fun delete(typed: String, order: DateOrder = us): String = DateEntry.edit(typed, typed.dropLast(1), order)

    private fun boxes(typed: String, order: DateOrder = us): DateParts = DateEntry.boxes(typed, order)

    @Test
    fun theOrder_followsTheRegion() {
        assertEquals(DateOrder.MonthDayYear, DateOrder.forRegion("US"))
        assertEquals(DateOrder.DayMonthYear, DateOrder.forRegion("GB"))
        assertEquals(DateOrder.YearMonthDay, DateOrder.forRegion("jp"))
        assertEquals(DateOrder.DayMonthYear, DateOrder.forRegion(null))
    }

    // The bug on the iOS simulator: 0 5 1 1 tapped one by one showed "0511" in Year as well.
    @Test
    fun keysTappedOneByOne_fillTheBoxesInTurn() {
        assertEquals(DateParts(month = "0"), boxes(keys("0")))
        assertEquals(DateParts(month = "05"), boxes(keys("05")))
        assertEquals(DateParts(month = "05", day = "1"), boxes(keys("051")))
        assertEquals(DateParts(month = "05", day = "11"), boxes(keys("0511")))
        assertEquals(DateParts(month = "05", day = "11", year = "1998"), boxes(keys("05111998")))
    }

    @Test
    fun keysPastTheYear_areDropped() {
        assertEquals("05111998", keys("051119987"))
    }

    @Test
    fun aMonthOrDayThatCanOnlyStandAlone_movesOnByItself() {
        assertEquals(DateParts(month = "5", day = "11", year = "1998"), boxes(keys("5111998")))
        assertEquals(DateParts(day = "4", month = "7", year = "1990"), boxes(keys("471990", DateOrder.DayMonthYear), DateOrder.DayMonthYear))
    }

    @Test
    fun aDigitThatCannotFollow_endsTheBoxBeforeIt() {
        // A 1 then a 5 is January the 5th, not a 15th month.
        assertEquals(DateParts(month = "1", day = "5"), boxes(keys("15")))
        assertEquals(DateParts(month = "12", day = "3"), boxes(keys("123")))
        assertEquals(DateParts(day = "3", month = "4"), boxes(keys("34", DateOrder.DayMonthYear), DateOrder.DayMonthYear))
        assertEquals(DateParts(day = "31", month = "1"), boxes(keys("311", DateOrder.DayMonthYear), DateOrder.DayMonthYear))
    }

    @Test
    fun theYearFirst_thenMonthThenDay() {
        val ymd = DateOrder.YearMonthDay
        assertEquals(DateParts(year = "1998", month = "05", day = "11"), boxes(keys("19980511", ymd), ymd))
    }

    @Test
    fun delete_takesTheLastDigit_backAcrossTheBoxes() {
        var typed = keys("051119")
        typed = delete(typed)
        assertEquals(DateParts(month = "05", day = "11", year = "1"), boxes(typed))
        typed = delete(typed)
        assertEquals(DateParts(month = "05", day = "11"), boxes(typed))
        typed = delete(typed)
        assertEquals(DateParts(month = "05", day = "1"), boxes(typed))
        typed = delete(delete(delete(typed)))
        assertEquals(DateParts(), boxes(typed))
        assertEquals("", delete(typed), "delete in an empty field does nothing")
    }

    // Autofill and the simulator's typed text both arrive through insertText as one string.
    @Test
    fun aWholeDate_arrivingAtOnce_fillsAllThree() {
        listOf("05111998", "05/11/1998", "5/11/1998", "1998-05-11", "05.11.1998").forEach { whole ->
            assertEquals("05111998", DateEntry.edit("", whole, us), whole)
        }
    }

    @Test
    fun aWholeDate_inAnotherOrder_isReadTheWayThatMakesADate() {
        assertEquals(DateParts(day = "11", month = "05", year = "1998"), boxes(DateEntry.edit("", "1998-05-11", DateOrder.DayMonthYear), DateOrder.DayMonthYear))
        assertEquals(DateParts(day = "25", month = "12", year = "1990"), boxes(DateEntry.edit("", "12/25/1990", DateOrder.DayMonthYear), DateOrder.DayMonthYear))
    }

    @Test
    fun aWholeDate_replacesWhatWasTyped() {
        assertEquals("05111998", DateEntry.edit("12", "1205/11/1998", us))
    }

    @Test
    fun separatedGroups_thatAreNotAWholeDate_endTheirBoxes() {
        assertEquals(DateParts(month = "05", day = "1"), boxes(DateEntry.edit("", "5/1", us)))
        assertEquals(DateParts(month = "01", day = "02", year = "98"), boxes(DateEntry.edit("", "1/2/98", us)), "a year is not guessed from two digits")
    }

    @Test
    fun theNextDigit_goesIntoTheCurrentBox() {
        assertEquals(DatePart.Month, DateEntry.current("", us))
        assertEquals(DatePart.Month, DateEntry.current("1", us), "10, 11 or 12 may follow")
        assertEquals(DatePart.Day, DateEntry.current("5", us))
        assertEquals(DatePart.Year, DateEntry.current("0511", us))
        assertNull(DateEntry.current("05111998", us))
        assertEquals(DatePart.Year, DateEntry.current("", DateOrder.YearMonthDay))
    }

    @Test
    fun aDateFromTheScreen_isTypedAsDigits_thatReadBackTheSame() {
        val date = DateParts.of(LocalDate(1998, 5, 11))
        assertEquals("05111998", DateEntry.typed(date, us))
        assertEquals(date, boxes(DateEntry.typed(date, us)))
        assertEquals("15", DateEntry.typed(DateParts(month = "1", day = "5"), us))
        assertEquals("012", DateEntry.typed(DateParts(month = "1", day = "2"), us), "padded, or the 2 would join the 1")
        assertEquals("", DateEntry.typed(DateParts(year = "1998"), us), "a year alone leaves a gap before it")
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
