package com.opson.features.logger.impl

import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.Precision
import com.opson.libraries.foods.AmountUnit
import com.opson.libraries.foods.Confidence
import com.opson.libraries.foods.ParsedItem
import com.opson.libraries.foods.Portion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The amount rules for estimate lines, apart from any screen. */
class PhotoRowsTest {
    private val chicken = PhotoFixtures.chicken
    private val broccoli = PhotoFixtures.broccoli

    private fun item(grams: Double?, portionLabel: String? = null, quantity: Double = 1.0) = ParsedItem(
        text = "x", name = "x", quantity = quantity, amountText = "x", confidence = Confidence.High,
        foodId = "id", portionLabel = portionLabel, grams = grams,
    )

    @Test
    fun aMatchedPortionIsCountedToTheNearestHalf() {
        val row = PhotoRows.fromEstimate("k", item(grams = 250.0, portionLabel = "1 cup"), broccoli, householdOnly = false)
        assertEquals(AmountUnit.OfPortion(Portion("1 cup", 156.0)), row.unit)
        assertEquals(1.5, row.quantity)
    }

    @Test
    fun aWeightStaysAWeightWhileCaloriesShow() {
        val row = PhotoRows.fromEstimate("k", item(grams = 121.4), chicken, householdOnly = false)
        assertEquals(AmountUnit.Grams, row.unit)
        assertEquals(121.0, row.quantity)
    }

    @Test
    fun aWeightBecomesAHouseholdMeasureWhileCaloriesAreHidden() {
        val row = PhotoRows.fromEstimate("k", item(grams = 250.0), chicken, householdOnly = true)
        assertEquals(AmountUnit.OfPortion(Portion("1 piece", 120.0)), row.unit)
        assertEquals(2.0, row.quantity)
    }

    @Test
    fun withNoAmountAtAllItIsOneOfTheFirstUnit() {
        val row = PhotoRows.fromEstimate("k", item(grams = null), broccoli, householdOnly = false)
        assertEquals(AmountUnit.OfPortion(Portion("1 cup", 156.0)), row.unit)
        assertEquals(1.0, row.quantity)
    }

    @Test
    fun anUnmatchedLineKeepsTheServersItem() {
        val parsed = item(grams = 20.0)
        val row = PhotoRows.fromEstimate("k", parsed, null, householdOnly = false)
        assertNull(row.unit)
        assertEquals(parsed, row.parsed)
        assertEquals(Precision.Estimate, row.draft().precision)
    }

    @Test
    fun everyLineLogsAsADescribedEstimate() {
        val draft = PhotoRows.fromEstimate("k", item(grams = 120.0), chicken, householdOnly = false).draft()
        assertEquals(LogMethod.Describe, draft.method)
        assertEquals(Precision.Estimate, draft.precision)
        assertEquals(198.0, draft.kcal)
        assertEquals(PickSource.Photo, PhotoRows.fromEstimate("k", item(grams = 120.0), chicken, false).toLogPick().source)
    }

    @Test
    fun aSwapKeepsTheWeight() {
        val old = PhotoRows.fromEstimate("k", item(grams = 120.0), chicken, householdOnly = false)
        val swapped = PhotoRows.picked("k", PhotoFixtures.thigh, old, householdOnly = false)
        assertEquals(AmountUnit.Grams, swapped.unit)
        assertEquals(120.0, swapped.quantity)
        assertEquals(PhotoFixtures.thigh, swapped.food)
    }

    @Test
    fun anAddedFoodStartsAtOneOfItsFirstUnit() {
        val added = PhotoRows.picked("k", broccoli, row = null, householdOnly = false)
        assertEquals(AmountUnit.OfPortion(Portion("1 cup", 156.0)), added.unit)
        assertEquals(1.0, added.quantity)
    }

    @Test
    fun hidingCaloriesMovesAWeightedLineToItsMeasure() {
        val row = PhotoRows.fromEstimate("k", item(grams = 240.0), chicken, householdOnly = false)
        val moved = PhotoRows.forPolicy(row, householdOnly = true)
        assertEquals(AmountUnit.OfPortion(Portion("1 piece", 120.0)), moved.unit)
        assertEquals(2.0, moved.quantity)
        assertEquals(row, PhotoRows.forPolicy(row, householdOnly = false))
    }

    @Test
    fun stepsAndUnitsFollowTheAmountSheetsRules() {
        val row = PhotoRows.fromEstimate("k", item(grams = 156.0, portionLabel = "1 cup"), broccoli, householdOnly = false)
        assertEquals(1.5, PhotoRows.more(row).quantity)
        assertEquals(0.5, PhotoRows.less(row).quantity)
        val grams = PhotoRows.unitPicked(row, index = 1, householdOnly = false)
        assertEquals(AmountUnit.Grams, grams.unit)
        assertEquals(156.0, grams.quantity)
    }
}
