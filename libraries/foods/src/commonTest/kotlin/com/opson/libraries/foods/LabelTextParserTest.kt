package com.opson.libraries.foods

import com.opson.libraries.foods.LabelNutrient.AddedSugars
import com.opson.libraries.foods.LabelNutrient.Calcium
import com.opson.libraries.foods.LabelNutrient.Carbs
import com.opson.libraries.foods.LabelNutrient.Cholesterol
import com.opson.libraries.foods.LabelNutrient.Energy
import com.opson.libraries.foods.LabelNutrient.Fat
import com.opson.libraries.foods.LabelNutrient.Fiber
import com.opson.libraries.foods.LabelNutrient.Iron
import com.opson.libraries.foods.LabelNutrient.Potassium
import com.opson.libraries.foods.LabelNutrient.Protein
import com.opson.libraries.foods.LabelNutrient.SaturatedFat
import com.opson.libraries.foods.LabelNutrient.Sodium
import com.opson.libraries.foods.LabelNutrient.Sugars
import com.opson.libraries.foods.LabelNutrient.TransFat
import com.opson.libraries.foods.LabelNutrient.VitaminD
import com.opson.libraries.foods.LabelTextFixtures.lines
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class LabelTextParserTest {

    private fun assertValues(expected: Map<LabelNutrient, Double>, actual: Map<LabelNutrient, Double>) {
        assertEquals(expected.keys, actual.keys, "nutrients")
        expected.forEach { (key, value) ->
            val read = actual.getValue(key)
            if (abs(read - value) > 1e-6) fail("$key: expected $value, read $read")
        }
    }

    @Test
    fun usSingleColumnReadsPerServing() {
        val draft = LabelTextParser.parse(LabelTextFixtures.US_SINGLE)
        with(draft.facts) {
            assertEquals(CarbBasis.Us, basis)
            assertEquals(55.0, servingSize)
            assertEquals(ServingUnit.Gram, servingUnit)
            assertEquals("2/3 cup", servingLabel)
            assertEquals(8.0, servingsPerContainer)
            assertNull(name)
            assertNull(brand)
            assertEquals(emptyMap(), per100)
            assertValues(
                mapOf(
                    Energy to 230.0, Fat to 8.0, SaturatedFat to 1.0, TransFat to 0.0, Cholesterol to 0.0, Sodium to 160.0,
                    Carbs to 37.0, Fiber to 4.0, Sugars to 12.0, AddedSugars to 10.0, Protein to 3.0, VitaminD to 2.0,
                    Calcium to 260.0, Iron to 8.0, Potassium to 240.0,
                ),
                perServing,
            )
        }
        assertEquals(LabelLayout(panelFound = true, perServingColumn = true, per100Column = false, asPrepared = false, dualLanguage = false), draft.layout)
        assertEquals(5, draft.headlineFound)
    }

    @Test
    fun usDualColumnTakesThePerServingColumn() {
        val draft = LabelTextParser.parse(LabelTextFixtures.US_DUAL)
        assertEquals(255.0, draft.facts.servingSize)
        assertEquals("1 cup", draft.facts.servingLabel)
        assertEquals(2.0, draft.facts.servingsPerContainer)
        assertEquals(emptyMap(), draft.facts.per100)
        assertValues(
            mapOf(
                Energy to 220.0, Fat to 5.0, SaturatedFat to 2.0, TransFat to 0.0, Cholesterol to 15.0, Sodium to 240.0,
                Carbs to 35.0, Fiber to 6.0, Sugars to 7.0, AddedSugars to 4.0, Protein to 9.0, VitaminD to 5.0,
                Calcium to 200.0, Iron to 1.0, Potassium to 470.0,
            ),
            draft.facts.perServing,
        )
        assertEquals(2, draft.layout.columns)
        assertTrue(draft.layout.perServingColumn)
    }

    @Test
    fun euTableReadsBothColumnsAndSaltAsSodium() {
        val draft = LabelTextParser.parse(LabelTextFixtures.EU_TWO_COLUMNS)
        with(draft.facts) {
            assertEquals(CarbBasis.Eu, basis)
            assertEquals(30.0, servingSize)
            assertEquals(ServingUnit.Gram, servingUnit)
            assertNull(servingLabel)
            assertValues(
                mapOf(Energy to 392.0, Fat to 8.5, SaturatedFat to 1.2, Carbs to 67.0, Sugars to 22.0, Fiber to 7.0, Protein to 9.1, Sodium to 192.0),
                per100,
            )
            assertValues(
                mapOf(Energy to 118.0, Fat to 2.6, SaturatedFat to 0.4, Carbs to 20.0, Sugars to 6.6, Fiber to 2.1, Protein to 2.7, Sodium to 56.0),
                perServing,
            )
        }
        assertEquals(2, draft.layout.columns)
        assertTrue(draft.layout.per100Column && draft.layout.perServingColumn)
        assertEquals(emptyList(), LabelChecks.check(draft.facts.copy(name = "Granola")))
    }

    @Test
    fun germanDecimalCommas() {
        val draft = LabelTextParser.parse(LabelTextFixtures.GERMAN)
        assertEquals(CarbBasis.Eu, draft.facts.basis)
        assertValues(
            mapOf(Energy to 380.0, Fat to 12.0, SaturatedFat to 4.5, Carbs to 58.0, Sugars to 21.0, Fiber to 3.2, Protein to 7.4, Sodium to 208.0),
            draft.facts.per100,
        )
        assertEquals(emptyMap(), draft.facts.perServing)
        assertNull(draft.facts.servingSize)
        assertEquals(4, draft.headlineFound)
    }

    @Test
    fun frenchTable() {
        val draft = LabelTextParser.parse(LabelTextFixtures.FRENCH)
        assertEquals(CarbBasis.Eu, draft.facts.basis)
        assertValues(
            mapOf(Energy to 469.0, Fat to 21.0, SaturatedFat to 9.8, Carbs to 62.0, Sugars to 34.0, Fiber to 2.9, Protein to 6.2, Sodium to 232.0),
            draft.facts.per100,
        )
        assertFalse(draft.layout.dualLanguage)
    }

    @Test
    fun drinkPer100MlWithAServingInMl() {
        val draft = LabelTextParser.parse(LabelTextFixtures.DRINK)
        assertEquals(330.0, draft.facts.servingSize)
        assertEquals(ServingUnit.Milliliter, draft.facts.servingUnit)
        assertValues(
            mapOf(Energy to 42.0, Fat to 0.0, SaturatedFat to 0.0, Carbs to 10.6, Sugars to 10.6, Protein to 0.0, Sodium to 4.0),
            draft.facts.per100,
        )
        assertTrue(draft.layout.per100Column)
    }

    @Test
    fun canadianBilingualPanel() {
        val draft = LabelTextParser.parse(LabelTextFixtures.CANADIAN)
        assertTrue(draft.layout.dualLanguage)
        assertEquals(1, draft.layout.columns)
        assertEquals(CarbBasis.Us, draft.facts.basis)
        assertEquals(250.0, draft.facts.servingSize)
        assertEquals(ServingUnit.Milliliter, draft.facts.servingUnit)
        assertEquals("1 cup", draft.facts.servingLabel)
        assertValues(
            mapOf(
                Energy to 110.0, Fat to 0.0, SaturatedFat to 0.0, TransFat to 0.0, Carbs to 26.0, Fiber to 0.0, Sugars to 22.0,
                Protein to 2.0, Cholesterol to 0.0, Sodium to 0.0, Potassium to 450.0, Calcium to 20.0, Iron to 0.2,
            ),
            draft.facts.perServing,
        )
    }

    @Test
    fun asPreparedColumnIsSeenAndLeftOut() {
        val draft = LabelTextParser.parse(LabelTextFixtures.AS_PREPARED)
        assertTrue(draft.layout.asPrepared)
        assertEquals(2, draft.layout.columns)
        assertEquals(emptyMap(), draft.facts.perServing)
        assertNull(draft.facts.servingSize)
        assertValues(
            mapOf(Energy to 367.0, Fat to 4.1, SaturatedFat to 2.5, Carbs to 73.0, Sugars to 70.0, Fiber to 6.2, Protein to 5.5, Sodium to 152.0),
            draft.facts.per100,
        )
    }

    @Test
    fun kilojoulesOnlyBecomeKcal() {
        val draft = LabelTextParser.parse(LabelTextFixtures.KJ_ONLY)
        assertEquals(1500.0 / 4.184, draft.facts.per100.getValue(Energy), 1e-9)
        assertEquals(400.0, draft.facts.per100.getValue(Sodium), 1e-9)
    }

    @Test
    fun kcalPrintedBesideKjIsKeptAsPrinted() {
        val draft = LabelTextParser.parse(lines("Nutrition information", "Per 100g", "Energy 1650kJ / 400kcal"))
        assertEquals(400.0, draft.facts.per100.getValue(Energy))
    }

    @Test
    fun lessThanHalvesThePrintedNumber() {
        val draft = LabelTextParser.parse(LabelTextFixtures.LESS_THAN)
        assertEquals(0.5, draft.facts.perServing.getValue(Fiber))
        assertEquals(0.5, draft.facts.perServing.getValue(Sugars))
        assertEquals(15.0, draft.facts.servingSize)
        assertEquals(ServingUnit.Milliliter, draft.facts.servingUnit)
        assertEquals("1 tbsp", draft.facts.servingLabel)
        assertEquals(66.0, draft.facts.servingsPerContainer)
    }

    @Test
    fun aPackFrontOrAReceiptIsNotAPanel() {
        for (fixture in listOf(LabelTextFixtures.PACK_FRONT, LabelTextFixtures.RECEIPT)) {
            val draft = LabelTextParser.parse(fixture)
            assertEquals(LabelDraft(LabelFacts(), LabelLayout.NONE, emptyMap()), draft)
            assertEquals(0, draft.headlineFound)
        }
    }

    @Test
    fun aRowItCannotReadStaysEmpty() {
        val draft = LabelTextParser.parse(LabelTextFixtures.MESSY)
        assertValues(
            mapOf(Energy to 392.0, Fat to 8.5, SaturatedFat to 1.2, Sugars to 22.0, Sodium to 192.0),
            draft.facts.per100,
        )
        assertEquals(2, draft.headlineFound)
    }

    @Test
    fun aRowWithMoreNumbersThanColumnsStaysEmpty() {
        val draft = LabelTextParser.parse(lines("Nutrition information", "Per 100g", "Energy 392kcal", "Fat 8.5g 2.6g", "Protein 9.1g"))
        assertValues(mapOf(Energy to 392.0, Protein to 9.1), draft.facts.per100)
    }

    @Test
    fun aRowSplitFromItsNumberTakesItOnlyWhenTheUnitIsKnown() {
        val draft = LabelTextParser.parse(lines("Nutrition Facts", "Calories", "230", "Protein", "3", "Total Fat", "8g"))
        assertEquals(mapOf(Energy to 230.0, Fat to 8.0), draft.facts.perServing)
    }

    @Test
    fun percentOnlyRowsStayEmpty() {
        val draft = LabelTextParser.parse(lines("Nutrition Facts", "Calories 100", "Vitamin D 10%", "Iron 6%"))
        assertEquals(mapOf(Energy to 100.0), draft.facts.perServing)
    }

    @Test
    fun valuesReadAfterTheirLabelsArePlacedByPositionAndNotOtherwise() {
        fun line(text: String, row: Int, left: Double, height: Double) = LabelTextLine(text, left = left, top = 0.1 + row * 0.03, width = 0.2, height = height)
        val labels = LabelTextFixtures.COLUMN_ORDER_LABELS.mapIndexed { i, text -> line(text, i, 0.1, 0.02) }
        val values = LabelTextFixtures.COLUMN_ORDER_VALUES.mapIndexed { i, text -> line(text, i + 2, 0.6, 0.02) }

        val placed = LabelTextParser.parse(labels + values)
        assertValues(mapOf(Fat to 8.5, Carbs to 67.0, Protein to 9.1), placed.facts.per100)

        val unplaced = LabelTextParser.parse((labels + values).map { LabelTextLine(it.text) })
        assertEquals(emptyMap(), unplaced.facts.per100)
    }

    @Test
    fun fieldConfidenceIsTheWeakestLineBehindEachField() {
        val fixture = LabelTextFixtures.US_SINGLE.map { if (it.text == "3g") it.copy(confidence = 0.5) else it }
        val draft = LabelTextParser.parse(fixture)
        assertEquals(0.5, draft.fieldConfidence[Protein.wire])
        assertEquals(1.0, draft.fieldConfidence[Energy.wire])
        assertEquals(1.0, draft.fieldConfidence[LabelDraft.SERVING_SIZE])
        assertEquals(null, draft.fieldConfidence[LabelNutrient.Selenium.wire])
    }

    @Test
    fun ocrDigitLookalikesAreRepairedInsideNumbersOnly() {
        assertEquals("trans fat 0g", LabelNumbers.repair("trans fat og"))
        assertEquals("sodium 160mg", LabelNumbers.repair("sodium l60mg"))
        assertEquals("calories 230", LabelNumbers.repair("calories 23o"))
        assertEquals("sale 0,5 g calcio120mg", LabelNumbers.repair("sale o,5 g calcio120mg"))
    }
}
