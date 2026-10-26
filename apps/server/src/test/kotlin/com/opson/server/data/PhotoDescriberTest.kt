package com.opson.server.data

import com.opson.server.domain.DescribeConfidence
import com.opson.server.domain.DescribeUnit
import com.opson.server.domain.EstimateResult
import com.opson.server.domain.EstimatedFood
import com.opson.server.domain.ImageType
import com.opson.server.domain.PhotoDescribeResult
import com.opson.server.domain.Portion
import com.opson.server.domain.PortionSize
import com.opson.server.testing.FakeEstimateProvider
import com.opson.server.testing.FakeFoodCatalog
import com.opson.server.testing.FakeFoodCatalog.Companion.food
import com.opson.server.testing.TestImages
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PhotoDescriberTest {

    private val broccoli = food("broccoli, steamed", kcal = 35.0, portions = listOf(Portion("1 cup", 156.0)))
    private val oil = food("olive oil", kcal = 884.0, portions = listOf(Portion("1 tbsp", 13.5)))
    private val chicken = food("chicken breast, grilled", kcal = 165.0)
    private val apple = food("apple", kcal = 52.0, portions = listOf(Portion("1 medium", 182.0)))
    private val catalog = FakeFoodCatalog(listOf(broccoli, oil, chicken, apple))
    private val provider = FakeEstimateProvider()
    private val describer = PhotoDescriber(provider, CatalogFoodDescriber(catalog))

    private suspend fun describe(vararg foods: EstimatedFood): PhotoDescribeResult.Described {
        provider.result = EstimateResult.Estimated(foods.toList())
        return assertIs<PhotoDescribeResult.Described>(describer.describe(TestImages.jpeg, ImageType.Jpeg, "no butter"))
    }

    @Test
    fun householdUnitsUseTheCatalogPortion() = runTest {
        val items = describe(
            EstimatedFood("Broccoli, steamed", 2.0, "cups", 300.0),
            EstimatedFood("Olive oil", 1.0, "tbsp", 14.0),
        ).items
        assertEquals(listOf(broccoli.id, oil.id), items.map { it.foodId })
        assertEquals(312.0, items[0].grams)
        assertEquals("2 cup", items[0].amountText)
        assertEquals("1 tbsp", items[1].portionLabel)
        assertEquals(DescribeUnit.Tablespoon, items[1].unit)
    }

    @Test
    fun aUnitTheFoodHasNoPortionForFallsBackToTheModelsWeight() = runTest {
        val item = describe(EstimatedFood("chicken breast, grilled", 1.0, "piece", 120.0)).items.single()
        assertEquals(chicken.id, item.foodId)
        assertEquals(120.0, item.grams)
        assertEquals(198.0, item.nutrients?.kcal)
        assertNull(item.portionLabel)
    }

    @Test
    fun noUnitIsTheModelsWeightInGrams() = runTest {
        val item = describe(EstimatedFood("chicken breast, grilled", 1.0, null, 120.0)).items.single()
        assertEquals(DescribeUnit.Gram, item.unit)
        assertEquals("120 g", item.amountText)
        assertEquals(120.0, item.grams)
    }

    @Test
    fun aSizeWordPicksThePortion() = runTest {
        val item = describe(EstimatedFood("apple", 1.0, "medium", 150.0)).items.single()
        assertEquals("1 medium", item.portionLabel)
        assertEquals(182.0, item.grams)
    }

    @Test
    fun anUnmatchedFoodKeepsItsNameAndWeight() = runTest {
        val item = describe(EstimatedFood("Dragon fruit", 1.0, "piece", 200.0)).items.single()
        assertNull(item.foodId)
        assertEquals("dragon fruit", item.name)
        assertEquals(200.0, item.grams)
        assertEquals(DescribeConfidence.Low, item.confidence)
    }

    @Test
    fun phrasesFromModelOutput() {
        assertEquals(PortionSize.Large, PhotoDescriber.phrase(EstimatedFood("egg", 2.0, "large", 100.0))?.size)
        assertEquals(DescribeUnit.Ounce, PhotoDescriber.phrase(EstimatedFood("steak", 6.0, "oz", 170.0))?.unit)
        assertNull(PhotoDescriber.phrase(EstimatedFood("   ", 1.0, null, null)))
        val bare = PhotoDescriber.phrase(EstimatedFood("bread", -1.0, "", null))
        assertEquals(1.0, bare?.quantity)
        assertNull(bare?.unit)
    }

    @Test
    fun theImageAndNoteReachTheProvider() = runTest {
        describe()
        assertEquals(Triple(TestImages.jpeg.size, ImageType.Jpeg, "no butter"), provider.calls.single())
    }

    @Test
    fun providerOutcomesPassThrough() = runTest {
        provider.result = EstimateResult.NotConfigured
        assertEquals(PhotoDescribeResult.NotConfigured, describer.describe(TestImages.png, ImageType.Png, null))
        provider.result = EstimateResult.Failed("refusal")
        assertEquals(PhotoDescribeResult.Failed("refusal"), describer.describe(TestImages.png, ImageType.Png, null))
    }

    @Test
    fun aLongListIsCapped() = runTest {
        val many = (1..30).map { EstimatedFood("apple", 1.0, null, 100.0) }.toTypedArray()
        assertEquals(PhotoDescriber.MAX_FOODS, describe(*many).items.size)
    }
}
