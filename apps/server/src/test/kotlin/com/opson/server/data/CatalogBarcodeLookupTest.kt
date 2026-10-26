package com.opson.server.data

import com.opson.server.config.FoodSourcesConfig
import com.opson.server.config.NutritionixConfig
import com.opson.server.domain.BarcodeLookupResult
import com.opson.server.domain.BarcodeOutcome
import com.opson.server.domain.CatalogSource
import com.opson.server.domain.ExternalProduct
import com.opson.server.domain.FoodSource
import com.opson.server.domain.FoodSourceResult
import com.opson.server.domain.FoodTier
import com.opson.server.domain.LiveFoodSources
import com.opson.server.domain.Nutrients
import com.opson.server.domain.OpenFoodFactsClient
import com.opson.server.testing.FakeFoodCatalog
import com.opson.server.testing.FakeFoodCatalog.Companion.food
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CatalogBarcodeLookupTest {

    private class FakeSource(
        override val catalogSource: CatalogSource,
        var result: FoodSourceResult,
        override val cacheable: Boolean = true,
    ) : FoodSource {
        override val outcome = when (catalogSource) {
            CatalogSource.Nutritionix -> BarcodeOutcome.Nutritionix
            else -> BarcodeOutcome.OpenFoodFacts
        }
        val calls = mutableListOf<String>()
        override suspend fun lookup(barcode: String): FoodSourceResult {
            calls += barcode
            return result
        }
    }

    private val code = "3017620422003"
    private val product = ExternalProduct(code, "Hazelnut spread", "Brand", Nutrients(kcal = 539.0), emptyMap(), emptyList())

    private fun lookup(catalog: FakeFoodCatalog, vararg sources: FoodSource) =
        CatalogBarcodeLookup(catalog, LiveFoodSources { sources.toList() })

    @Test
    fun catalogHitNeverCallsAnOutsideSource() = runTest {
        val catalog = FakeFoodCatalog(listOf(food("Spread", barcode = code)))
        val off = FakeSource(CatalogSource.OpenFoodFacts, FoodSourceResult.NotFound)
        assertIs<BarcodeLookupResult.Found>(lookup(catalog, off).lookup(code))
        assertEquals(emptyList(), off.calls)
        assertEquals(listOf(code to BarcodeOutcome.Catalog), catalog.barcodeOutcomes)
    }

    @Test
    fun aScanMatchesTheCatalogInAnyFormOfTheCode() = runTest {
        // Branded Foods stores GTIN-14; the phone reads the UPC-A off the can.
        val catalog = FakeFoodCatalog(listOf(food("Cola", barcode = "00049000028911", source = CatalogSource.UsdaBranded)))
        val off = FakeSource(CatalogSource.OpenFoodFacts, FoodSourceResult.NotFound)
        val found = assertIs<BarcodeLookupResult.Found>(lookup(catalog, off).lookup("049000028911"))
        assertEquals("Cola", found.food.name)
        assertEquals(emptyList(), off.calls)
    }

    @Test
    fun openFoodFactsHitIsCachedIntoTheCatalog() = runTest {
        val catalog = FakeFoodCatalog()
        val off = FakeSource(CatalogSource.OpenFoodFacts, FoodSourceResult.Found(product))
        val lookup = lookup(catalog, off)

        val first = assertIs<BarcodeLookupResult.Found>(lookup.lookup(code))
        assertEquals(CatalogSource.OpenFoodFacts, first.food.source)
        assertEquals(FoodTier.Community, first.food.tier)
        assertEquals(false, first.food.verified)
        val second = assertIs<BarcodeLookupResult.Found>(lookup.lookup(code))

        assertEquals(first.food.id, second.food.id)
        assertEquals(1, off.calls.size)
        assertEquals(listOf(BarcodeOutcome.OpenFoodFacts, BarcodeOutcome.Catalog), catalog.barcodeOutcomes.map { it.second })
    }

    @Test
    fun sourcesAreAskedInTheOrderGivenAndTheFirstAnswerWins() = runTest {
        val catalog = FakeFoodCatalog()
        val nutritionix = FakeSource(CatalogSource.Nutritionix, FoodSourceResult.Found(product.copy(name = "From Nutritionix")))
        val off = FakeSource(CatalogSource.OpenFoodFacts, FoodSourceResult.Found(product))

        val found = assertIs<BarcodeLookupResult.Found>(lookup(catalog, nutritionix, off).lookup(code))

        assertEquals("From Nutritionix", found.food.name)
        assertEquals(FoodTier.Licensed, found.food.tier)
        assertEquals(emptyList(), off.calls)
        assertEquals(listOf(code to BarcodeOutcome.Nutritionix), catalog.barcodeOutcomes)
    }

    @Test
    fun aMissFallsThroughToTheNextSource() = runTest {
        val catalog = FakeFoodCatalog()
        val nutritionix = FakeSource(CatalogSource.Nutritionix, FoodSourceResult.NotFound)
        val off = FakeSource(CatalogSource.OpenFoodFacts, FoodSourceResult.Found(product))

        val found = assertIs<BarcodeLookupResult.Found>(lookup(catalog, nutritionix, off).lookup(code))

        assertEquals(CatalogSource.OpenFoodFacts, found.food.source)
        assertEquals(listOf(code), nutritionix.calls)
        assertEquals(listOf(code to BarcodeOutcome.OpenFoodFacts), catalog.barcodeOutcomes)
    }

    @Test
    fun aSourceThatMayNotBeCachedIsServedButNotStored() = runTest {
        val catalog = FakeFoodCatalog()
        val nutritionix = FakeSource(CatalogSource.Nutritionix, FoodSourceResult.Found(product), cacheable = false)
        val lookup = lookup(catalog, nutritionix)

        val first = assertIs<BarcodeLookupResult.Found>(lookup.lookup(code))
        val second = assertIs<BarcodeLookupResult.Found>(lookup.lookup(code))

        assertEquals(emptyList(), catalog.foods)
        assertEquals(2, nutritionix.calls.size, "not cached, so asked again")
        assertEquals(first.food.id, second.food.id, "the same product keeps one id")
        assertEquals(listOf(BarcodeOutcome.Nutritionix, BarcodeOutcome.Nutritionix), catalog.barcodeOutcomes.map { it.second })
    }

    @Test
    fun missIsRecordedAndNotCached() = runTest {
        val catalog = FakeFoodCatalog()
        val off = FakeSource(CatalogSource.OpenFoodFacts, FoodSourceResult.NotFound)
        val lookup = lookup(catalog, off)
        assertEquals(BarcodeLookupResult.NotFound, lookup.lookup(code))
        assertEquals(BarcodeLookupResult.NotFound, lookup.lookup(code))
        assertEquals(2, off.calls.size)
        assertEquals(listOf(BarcodeOutcome.Miss, BarcodeOutcome.Miss), catalog.barcodeOutcomes.map { it.second })
    }

    @Test
    fun outageIsUnavailableNotAMiss() = runTest {
        val catalog = FakeFoodCatalog()
        val off = FakeSource(CatalogSource.OpenFoodFacts, FoodSourceResult.Unavailable(RuntimeException("timeout")))
        assertEquals(BarcodeLookupResult.Unavailable, lookup(catalog, off).lookup(code))
        assertEquals(listOf(code to BarcodeOutcome.Error), catalog.barcodeOutcomes)
    }

    @Test
    fun oneSourceDownAndAnotherSayingNoIsAMiss() = runTest {
        val catalog = FakeFoodCatalog()
        val nutritionix = FakeSource(CatalogSource.Nutritionix, FoodSourceResult.Unavailable(null))
        val off = FakeSource(CatalogSource.OpenFoodFacts, FoodSourceResult.NotFound)
        assertEquals(BarcodeLookupResult.NotFound, lookup(catalog, nutritionix, off).lookup(code))
        assertEquals(listOf(code to BarcodeOutcome.Miss), catalog.barcodeOutcomes)
    }

    @Test
    fun nutritionixIsOnlyConfiguredWithBothKeysAndGoesBeforeOpenFoodFacts() {
        val off = OpenFoodFactsSource(
            object : OpenFoodFactsClient {
                override suspend fun product(barcode: String) = FoodSourceResult.NotFound
            },
        )

        val defaults = ConfiguredLiveFoodSources(FoodSourcesConfig(), off).inTierOrder()
        assertEquals(listOf(CatalogSource.OpenFoodFacts), defaults.map { it.catalogSource })

        val withKeys = ConfiguredLiveFoodSources(FoodSourcesConfig(NutritionixConfig("id", "key")), off).inTierOrder()
        assertEquals(listOf(CatalogSource.Nutritionix, CatalogSource.OpenFoodFacts), withKeys.map { it.catalogSource })
        assertEquals(false, withKeys.first().cacheable, "Nutritionix caching is off unless asked for")
    }
}
