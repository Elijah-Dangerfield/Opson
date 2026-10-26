package com.opson.server.data

import com.opson.server.domain.DescribeConfidence
import com.opson.server.domain.DescribeUnit
import com.opson.server.domain.Portion
import com.opson.server.testing.FakeFoodCatalog
import com.opson.server.testing.FakeFoodCatalog.Companion.food
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogFoodDescriberTest {

    private val egg = food(
        "egg",
        kcal = 143.0,
        portions = listOf(Portion("1 large", 50.0), Portion("1 medium", 44.0), Portion("1 cup", 243.0)),
    )
    private val rice = food("rice", kcal = 130.0, portions = listOf(Portion("1 cup", 158.0), Portion("2 tablespoon", 20.0)))
    private val toast = food("toast", kcal = 300.0, portions = listOf(Portion("1 slice", 30.0)))
    private val salt = food("salt", kcal = 0.0)
    private val catalog = FakeFoodCatalog(listOf(egg, rice, toast, salt))
    private val describer = CatalogFoodDescriber(catalog)

    @Test
    fun countWithoutUnitUsesTheDefaultPortion() = runTest {
        val item = describer.describe("2 eggs").single()
        assertEquals(egg.id, item.foodId)
        assertEquals("1 medium", item.portionLabel)
        assertEquals(88.0, item.grams)
        assertEquals(125.8, item.nutrients?.kcal)
        assertEquals("2", item.amountText)
    }

    @Test
    fun sizeWordPicksTheMatchingPortion() = runTest {
        val item = describer.describe("2 large eggs").single()
        assertEquals("1 large", item.portionLabel)
        assertEquals(100.0, item.grams)
        assertEquals(DescribeConfidence.Medium, item.confidence)
        assertEquals("2 large", item.amountText)
    }

    @Test
    fun unitMatchingAPortionLabelUsesIt() = runTest {
        val item = describer.describe("1/2 cup rice").single()
        assertEquals("1 cup", item.portionLabel)
        assertEquals(79.0, item.grams)
        assertEquals(DescribeUnit.Cup, item.unit)
        assertEquals("1/2 cup", item.amountText)
    }

    @Test
    fun portionLabelsWithAnAmountAreDividedPerUnit() = runTest {
        val item = describer.describe("1 tbsp rice").single()
        assertEquals("2 tablespoon", item.portionLabel)
        assertEquals(10.0, item.grams)
    }

    @Test
    fun gramsAreUsedAsGiven() = runTest {
        val item = describer.describe("150 g rice").single()
        assertEquals(150.0, item.grams)
        assertEquals(195.0, item.nutrients?.kcal)
        assertNull(item.portionLabel)
        assertEquals(DescribeConfidence.High, item.confidence)
    }

    @Test
    fun ouncesConvertToGrams() = runTest {
        assertEquals(56.7, describer.describe("2 oz rice").single().grams)
    }

    @Test
    fun unitWithNoMatchingPortionFallsBackToADefaultServing() = runTest {
        val item = describer.describe("a bowl of rice").single()
        assertEquals("1 cup", item.portionLabel)
        assertEquals(158.0, item.grams)
        assertEquals(DescribeConfidence.Medium, item.confidence)
    }

    @Test
    fun foodWithNoPortionsIsAHundredGramGuess() = runTest {
        val item = describer.describe("salt").single()
        assertEquals(100.0, item.grams)
        assertEquals(DescribeConfidence.Low, item.confidence)
    }

    @Test
    fun noMatchStillReturnsTheItem() = runTest {
        val item = describer.describe("dragonfruit smoothie").single()
        assertNull(item.foodId)
        assertNull(item.nutrients)
        assertEquals("dragonfruit smoothie", item.name)
        assertEquals(DescribeConfidence.Low, item.confidence)
    }

    @Test
    fun describesEveryPhraseWithoutRecordingSearches() = runTest {
        val items = describer.describe("2 eggs and toast")
        assertEquals(listOf(egg.id, toast.id), items.map { it.foodId })
        assertTrue(catalog.searches.none { (_, recorded) -> recorded })
    }
}
