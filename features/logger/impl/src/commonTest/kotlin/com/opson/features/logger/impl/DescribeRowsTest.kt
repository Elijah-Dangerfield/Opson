package com.opson.features.logger.impl

import com.opson.libraries.foods.AmountUnit
import com.opson.libraries.foods.Confidence
import com.opson.libraries.foods.ParsedItem
import com.opson.libraries.foods.Portion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [DescribeRows]: how a read becomes lines, and what each control does to one line. */
class DescribeRowsTest {
    private val eggs = LoggerFixtures.parsed[0]
    private val toast = LoggerFixtures.parsed[1]
    private fun row(item: ParsedItem, householdOnly: Boolean = false) = DescribeRows.fromItem("describe:0", item, householdOnly)

    @Test
    fun aReadLineCountsTheServersPortion() {
        val line = row(eggs)
        assertEquals(LoggerFixtures.eggLarge, line.food)
        assertEquals(AmountUnit.OfPortion(Portion("1 large", 50.0)), line.unit)
        assertEquals(2.0, line.quantity)
        assertEquals(156.0, line.draft().kcal)
        assertEquals(listOf(LoggerFixtures.eggScrambled, LoggerFixtures.eggFried), line.alternatives)
    }

    @Test
    fun eggsStepByOneAndNeverBelowOne() {
        val one = DescribeRows.less(row(eggs))
        assertEquals(1.0, one.quantity)
        assertTrue(one.edited)
        assertFalse(DescribeRows.canLess(one))
        assertEquals(1.0, DescribeRows.less(one).quantity)
        assertEquals(3.0, DescribeRows.more(row(eggs)).quantity)
    }

    @Test
    fun slicesStepByAHalf() {
        val more = DescribeRows.more(row(toast))
        assertEquals(1.5, more.quantity)
        assertEquals(0.5, DescribeRows.less(row(toast)).quantity)
        assertFalse(DescribeRows.canLess(DescribeRows.less(row(toast))))
    }

    @Test
    fun gramsStepByTen() {
        val grams = DescribeRows.unitPicked(row(toast), DescribeRows.units(LoggerFixtures.toastWhite, householdOnly = false).indexOf(AmountUnit.Grams), false)
        assertEquals(AmountUnit.Grams, grams.unit)
        assertEquals(28.0, grams.quantity)
        assertEquals(38.0, DescribeRows.more(grams).quantity)
    }

    @Test
    fun pickingAnotherMeasureKeepsTheCount() {
        val thick = DescribeRows.unitPicked(row(toast), 1, householdOnly = false)
        assertEquals("1 thick slice", (thick.unit as AmountUnit.OfPortion).portion.label)
        assertEquals(1.0, thick.quantity)
        assertEquals(39.0, thick.draft().grams)
    }

    @Test
    fun bookkeepingPortionsAreNotOffered() {
        val juice = LoggerFixtures.food("juice", "Orange juice", "1 individual school container", 124.0, 58.0, 1.0, 13.0, 0.0, more = listOf(Portion("1 cup", 248.0), Portion("Guideline amount per fl oz of beverage", 2.5)))
        assertEquals(listOf("1 cup"), DescribeRows.units(juice, householdOnly = true).map { it.label })
    }

    @Test
    fun withCaloriesHiddenAWeightBecomesAHouseholdMeasure() {
        val weighed = eggs.copy(portionLabel = null)
        assertEquals(AmountUnit.Grams, row(weighed).unit)
        val hidden = row(weighed, householdOnly = true)
        assertEquals(AmountUnit.OfPortion(Portion("1 large", 50.0)), hidden.unit)
        assertEquals(2.0, hidden.quantity)
    }

    @Test
    fun swappingKeepsTheCountWhenTheMeasureIsTheSame() {
        val scrambled = DescribeRows.swapped(row(eggs), LoggerFixtures.eggScrambled, householdOnly = false)
        assertEquals(LoggerFixtures.eggScrambled, scrambled.food)
        assertEquals(2.0, scrambled.quantity)
        // The food it replaced is one tap away.
        assertEquals(listOf(LoggerFixtures.eggLarge, LoggerFixtures.eggFried), scrambled.alternatives)
        assertTrue(scrambled.edited)
    }

    @Test
    fun swappingToAFoodMeasuredOtherwiseKeepsTheWeight() {
        val swapped = DescribeRows.swapped(row(toast), LoggerFixtures.rice, householdOnly = false)
        assertEquals("1 cup", swapped.unit?.label)
        assertEquals(0.5, swapped.quantity, "28 g of toast is about half a cup, on half steps")
    }

    @Test
    fun aLineWithNoFoodLogsAsRead() {
        val line = row(LoggerFixtures.unmatched)
        assertNull(line.food)
        assertEquals("grandma's kugel", line.draft().name)
        assertEquals(line, DescribeRows.more(line))
        assertFalse(DescribeRows.canLess(line))
    }

    @Test
    fun aNewReadKeepsWhatThePersonChangedAndAdded() {
        val first = DescribeRows.read(DescribeReview(), LoggerFixtures.parsed, householdOnly = false)
        val edited = first.copy(
            rows = listOf(DescribeRows.more(first.rows[0]), first.rows[1]) +
                DescribeRows.added("describe:9", "coffee", LoggerFixtures.coffee, householdOnly = false),
        )

        val again = DescribeRows.read(edited, LoggerFixtures.parsed + LoggerFixtures.unmatched, householdOnly = false)

        assertEquals(listOf("Egg, large", "Toast, white", "grandma's kugel", "Coffee, brewed"), again.rows.map { it.name })
        assertEquals(3.0, again.rows[0].quantity, "the eggs stepped to three stay three")
        assertEquals(edited.rows[0].key, again.rows[0].key)
        assertEquals(again.rows.size, again.rows.map { it.key }.toSet().size, "keys are never reused")
    }

    @Test
    fun aLineTakenOutStaysOutWhenTheSameWordsAreReadAgain() {
        val read = DescribeRows.read(DescribeReview(), LoggerFixtures.parsed, householdOnly = false)
        val withoutToast = read.copy(rows = read.rows.take(1), dismissed = setOf("toast"))
        assertEquals(listOf("Egg, large"), DescribeRows.read(withoutToast, LoggerFixtures.parsed, householdOnly = false).rows.map { it.name })
    }

    @Test
    fun clearingKeepsOnlyWhatWasAdded() {
        val review = DescribeReview(
            rows = listOf(row(eggs), DescribeRows.added("describe:1", "coffee", LoggerFixtures.coffee, householdOnly = false)),
            find = DescribeFind(replacing = null),
        )
        val cleared = DescribeRows.cleared(review)
        assertEquals(listOf("Coffee, brewed"), cleared.rows.map { it.name })
        assertNull(cleared.find)
    }

    @Test
    fun aServerOlderThanTwoPointOhStillGivesALine() {
        val old = ParsedItem(text = "2 eggs", name = "Egg", quantity = 2.0, amountText = "2", confidence = Confidence.High, foodId = "egg", grams = 100.0, kcal = 143.0)
        val line = row(old)
        assertNull(line.food)
        assertEquals(143.0, line.draft().kcal)
        assertEquals("egg", line.toLogPick().ref?.key?.substringAfter(':'))
    }
}
