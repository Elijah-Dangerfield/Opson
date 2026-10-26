package com.opson.server.data

import com.opson.server.domain.BarcodeLookupResult
import com.opson.server.domain.BarcodeOutcome
import com.opson.server.domain.FoodSource
import com.opson.server.domain.Nutrients
import com.opson.server.domain.OpenFoodFactsClient
import com.opson.server.domain.OpenFoodFactsProduct
import com.opson.server.domain.OpenFoodFactsResult
import com.opson.server.testing.FakeFoodCatalog
import com.opson.server.testing.FakeFoodCatalog.Companion.food
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CatalogBarcodeLookupTest {

    private class FakeOpenFoodFacts(var result: OpenFoodFactsResult) : OpenFoodFactsClient {
        val calls = mutableListOf<String>()
        override suspend fun product(barcode: String): OpenFoodFactsResult {
            calls += barcode
            return result
        }
    }

    private val code = "3017620422003"
    private val product = OpenFoodFactsProduct(code, "Hazelnut spread", "Brand", Nutrients(kcal = 539.0), emptyMap(), emptyList())

    @Test
    fun catalogHitNeverCallsOpenFoodFacts() = runTest {
        val catalog = FakeFoodCatalog(listOf(food("Spread", barcode = code)))
        val off = FakeOpenFoodFacts(OpenFoodFactsResult.NotFound)
        val result = CatalogBarcodeLookup(catalog, off).lookup(code)
        assertIs<BarcodeLookupResult.Found>(result)
        assertEquals(emptyList(), off.calls)
        assertEquals(listOf(code to BarcodeOutcome.Catalog), catalog.barcodeOutcomes)
    }

    @Test
    fun openFoodFactsHitIsCachedIntoTheCatalog() = runTest {
        val catalog = FakeFoodCatalog()
        val off = FakeOpenFoodFacts(OpenFoodFactsResult.Found(product))
        val lookup = CatalogBarcodeLookup(catalog, off)

        val first = assertIs<BarcodeLookupResult.Found>(lookup.lookup(code))
        assertEquals(FoodSource.OpenFoodFacts, first.food.source)
        val second = assertIs<BarcodeLookupResult.Found>(lookup.lookup(code))

        assertEquals(first.food.id, second.food.id)
        assertEquals(1, off.calls.size)
        assertEquals(listOf(BarcodeOutcome.OpenFoodFacts, BarcodeOutcome.Catalog), catalog.barcodeOutcomes.map { it.second })
    }

    @Test
    fun missIsRecordedAndNotCached() = runTest {
        val catalog = FakeFoodCatalog()
        val off = FakeOpenFoodFacts(OpenFoodFactsResult.NotFound)
        val lookup = CatalogBarcodeLookup(catalog, off)
        assertEquals(BarcodeLookupResult.NotFound, lookup.lookup(code))
        assertEquals(BarcodeLookupResult.NotFound, lookup.lookup(code))
        assertEquals(2, off.calls.size)
        assertEquals(listOf(BarcodeOutcome.Miss, BarcodeOutcome.Miss), catalog.barcodeOutcomes.map { it.second })
    }

    @Test
    fun outageIsUnavailableNotAMiss() = runTest {
        val catalog = FakeFoodCatalog()
        val off = FakeOpenFoodFacts(OpenFoodFactsResult.Unavailable(RuntimeException("timeout")))
        assertEquals(BarcodeLookupResult.Unavailable, CatalogBarcodeLookup(catalog, off).lookup(code))
        assertEquals(listOf(code to BarcodeOutcome.Error), catalog.barcodeOutcomes)
    }
}
