package com.opson.libraries.foods.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.FoodSearchFilter
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.foods.FoodTier
import com.opson.libraries.foods.Portion
import com.opson.libraries.foods.SearchCategory
import com.opson.libraries.foods.SearchOptions
import com.opson.libraries.foods.SearchOrder
import com.opson.libraries.foods.SearchSource
import com.opson.libraries.foods.db.FoodCacheDao
import com.opson.libraries.foods.db.FoodCacheEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * [FoodCatalogImpl] over a fake API and an in-memory cache: the offline
 * fallbacks, what lands in the cache (foods the user picked or starred, and a
 * scanned food, unused), and that Recent and Favorites read from it.
 *
 * Not covered: the HTTP layer and the server's ranking, which belong to
 * `:apps:server` route tests and `:apps:integration`.
 */
class FoodCatalogImplTest : CoroutineTest() {
    private val api = FakeFoodApi()
    private val cache = FakeFoodCacheDao()
    private val clock = object : Clock {
        var now = Instant.parse("2026-10-25T12:00:00Z")
        override fun now() = now
    }
    private val catalog = FoodCatalogImpl(api, cache, clock)

    @Test
    fun describeCarriesTheMatchedFoodAndItsAlternatives() = runUnitTest {
        val item = DescribedItemDto(
            text = "some rice", name = "rice", foodId = "rice", quantity = 1.0, amountText = "1 cup",
            portionLabel = "1 cup", grams = 158.0, kcal = 205.0, confidence = "high",
            food = rice, alternatives = listOf(yogurt),
        )
        api.describeResult = Result.success(DescribeResponse(listOf(item, item.copy(food = null, alternatives = emptyList()))))

        val (matched, fromAnOlderServer) = catalog.describe("some rice").getOrThrow()

        assertEquals(rice.toDomain(), matched.food)
        assertEquals(listOf("yogurt"), matched.alternatives.map { it.id })
        assertNull(fromAnOlderServer.food)
        assertTrue(fromAnOlderServer.alternatives.isEmpty())
    }

    @Test
    fun searchResultsComeFromTheServerWhenItAnswers() = runUnitTest {
        api.searchResult = Result.success(FoodSearchResponse("rice", listOf(rice)))

        val result = catalog.search("Rice ").getOrThrow()

        assertFalse(result.fromCache)
        assertEquals(listOf("rice"), result.foods.map { it.id })
        assertTrue(cache.rows.value.isEmpty(), "browsing results is not picking them")
    }

    @Test
    fun aSearchAnsweredAMomentAgoIsAnsweredFromMemory() = runUnitTest {
        api.searchResult = Result.success(FoodSearchResponse("rice", listOf(rice)))
        assertNull(catalog.recentSearch("rice"), "nothing asked yet")

        catalog.search("rice")
        val again = catalog.search("  RICE ").getOrThrow()

        assertEquals(1, api.filters.size, "one round trip for the same normalized query")
        assertEquals(listOf("rice"), again.foods.map { it.id })
        assertFalse(again.fromCache, "a server answer, not the offline fallback")
        assertEquals(again, catalog.recentSearch("Rice"))
        assertNull(catalog.recentSearch("rice", filter = FoodSearchFilter(order = SearchOrder.Name)), "a filter is another question")
    }

    @Test
    fun aRememberedSearchIsAskedAgainAfterFiveMinutes() = runUnitTest {
        api.searchResult = Result.success(FoodSearchResponse("rice", listOf(rice)))
        catalog.search("rice")

        clock.now += 5.minutes
        assertNull(catalog.recentSearch("rice"))
        catalog.search("rice")
        assertEquals(2, api.filters.size)
    }

    @Test
    fun aFailedOrOfflineSearchIsNotRemembered() = runUnitTest {
        catalog.markUsed(rice.toDomain())
        api.searchResult = Result.failure(IllegalStateException("offline"))
        assertTrue(catalog.search("rice").getOrThrow().fromCache)
        assertNull(catalog.recentSearch("rice"))

        api.searchResult = Result.success(FoodSearchResponse("rice", listOf(rice)))
        assertFalse(catalog.search("rice").getOrThrow().fromCache, "back online, the server is asked")
    }

    @Test
    fun offlineSearchFallsBackToFoodsPickedBefore() = runUnitTest {
        catalog.markUsed(rice.toDomain())
        catalog.markUsed(yogurt.toDomain())
        api.searchResult = Result.failure(IllegalStateException("offline"))

        val result = catalog.search("rice").getOrThrow()

        assertTrue(result.fromCache)
        assertEquals(listOf("rice"), result.foods.map { it.id })
    }

    @Test
    fun searchOptionsGoToTheServerAndAnUnfilteredSearchSendsNone() = runUnitTest {
        api.searchResult = Result.success(FoodSearchResponse("rice", listOf(rice)))
        val restaurant = FoodSearchFilter(category = SearchCategory.Restaurant, order = SearchOrder.Name)

        catalog.search("rice")
        catalog.search("rice", filter = restaurant)

        assertEquals(listOf(FoodSearchFilter.None, restaurant), api.filters)
    }

    @Test
    fun offlineSearchFiltersBySourceAndTierButHasNoCategories() = runUnitTest {
        val riceSoup = rice.copy(id = "rice-soup", name = "Rice soup, bowl", source = "off", tier = 4)
        val riceNevo = rice.copy(id = "rice-nevo", name = "Rice white boiled", source = "nevo", tier = 1)
        catalog.markUsed(rice.toDomain())
        catalog.markUsed(riceSoup.toDomain())
        catalog.markUsed(riceNevo.toDomain())
        api.searchResult = Result.failure(IllegalStateException("offline"))
        suspend fun ids(filter: FoodSearchFilter) = catalog.search("rice", filter = filter).getOrThrow().foods.map { it.id }.toSet()

        assertEquals(setOf("rice", "rice-soup", "rice-nevo"), ids(FoodSearchFilter.None))
        assertEquals(setOf("rice"), ids(FoodSearchFilter(source = SearchSource.Usda)))
        assertEquals(setOf("rice-soup"), ids(FoodSearchFilter(source = SearchSource.OpenFoodFacts)))
        assertEquals(setOf("rice", "rice-nevo"), ids(FoodSearchFilter(category = SearchCategory.Common)))
        assertEquals(emptySet(), ids(FoodSearchFilter(category = SearchCategory.Beverage)))
    }

    @Test
    fun searchOptionsSayWhetherTheServerSearchesOpenFoodFacts() = runUnitTest {
        assertEquals(SearchOptions(openFoodFacts = false), catalog.searchOptions().getOrThrow())
        api.optionsResult = Result.success(SearchOptionsResponse(listOf("usda", "off")))
        assertEquals(SearchOptions(openFoodFacts = true), catalog.searchOptions().getOrThrow())
    }

    @Test
    fun recentIsNewestFirstAndFavoritesSurviveBeingUsedAgain() = runUnitTest {
        catalog.markUsed(rice.toDomain())
        clock.now = Instant.parse("2026-10-25T13:00:00Z")
        catalog.markUsed(yogurt.toDomain())
        catalog.setFavorite(rice.toDomain(), favorite = true)
        clock.now = Instant.parse("2026-10-25T14:00:00Z")
        catalog.markUsed(rice.toDomain())

        assertEquals(listOf("rice", "yogurt"), catalog.observeRecent().first().map { it.id })
        assertEquals(listOf("rice"), catalog.observeFavorites().first().map { it.id })
    }

    @Test
    fun starringAFoodDoesNotPutItInRecent() = runUnitTest {
        catalog.setFavorite(yogurt.toDomain(), favorite = true)

        assertTrue(catalog.observeRecent().first().isEmpty())
        assertEquals(listOf("yogurt"), catalog.observeFavorites().first().map { it.id })
    }

    @Test
    fun anUnknownBarcodeIsNullNotAFailure() = runUnitTest {
        api.barcodeResult = Result.success(null)

        val result = catalog.barcode("0000000000000")

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun aCachedFoodAnswersDetailAndBarcodeOffline() = runUnitTest {
        catalog.markUsed(yogurt.toDomain())
        api.detailResult = Result.failure(IllegalStateException("offline"))
        api.barcodeResult = Result.failure(IllegalStateException("offline"))

        assertEquals("yogurt", catalog.detail("yogurt").getOrThrow().id)
        assertEquals("yogurt", catalog.barcode("012345678905").getOrThrow()?.id)
        assertTrue(catalog.detail("never-seen").isFailure)
    }

    @Test
    fun aScannedFoodIsKeptForTheAmountSheetButNotPutInRecent() = runUnitTest {
        api.barcodeResult = Result.success(yogurt)

        assertEquals("yogurt", catalog.barcode("012345678905").getOrThrow()?.id)

        assertTrue(catalog.observeRecent().first().isEmpty(), "scanning is not logging")
        api.detailResult = Result.failure(IllegalStateException("offline"))
        api.barcodeResult = Result.failure(IllegalStateException("offline"))
        assertEquals("yogurt", catalog.detail("yogurt").getOrThrow().id)
        // iOS reads a UPC-A as its EAN-13; the cache keys on the GTIN, so either form finds it.
        assertEquals("yogurt", catalog.barcode("0012345678905").getOrThrow()?.id)
    }

    @Test
    fun scanningAgainKeepsAFoodsRecentPlaceAndStar() = runUnitTest {
        catalog.markUsed(yogurt.toDomain())
        catalog.setFavorite(yogurt.toDomain(), favorite = true)
        api.barcodeResult = Result.success(yogurt)

        catalog.barcode("012345678905")

        assertEquals(listOf("yogurt"), catalog.observeRecent().first().map { it.id })
        assertEquals(listOf("yogurt"), catalog.observeFavorites().first().map { it.id })
    }

    @Test
    fun tierAndVerifiedReachTheModelAndVolumeOnlyServingsAreLeftOut() {
        val cola = FoodResponse(
            id = "cola",
            source = "usda_branded",
            name = "Coca-Cola Classic",
            per100g = NutrientsDto(kcal = 39.4),
            portions = listOf(PortionDto("1 can (355 ml)"), PortionDto("1 glass (240 g)", 240.0)),
            tier = 2,
            verified = true,
        ).toDomain()
        assertEquals(FoodSource.UsdaBranded, cola.source)
        assertEquals(FoodTier.Label, cola.tier)
        assertTrue(cola.verified)
        assertEquals(listOf(Portion("1 glass (240 g)", 240.0)), cola.portions)

        val fromAnOlderServer = rice.toDomain()
        assertEquals(FoodTier.Unknown, fromAnOlderServer.tier)
        assertEquals(false, fromAnOlderServer.verified)
    }

    private val rice = FoodResponse(
        id = "rice",
        source = "usda_sr",
        name = "Jasmine rice, cooked",
        per100g = NutrientsDto(kcal = 130.0, protein = 2.7, carbs = 28.0, fat = 0.3),
        portions = listOf(PortionDto("1 cup", 158.0)),
    )

    private val yogurt = FoodResponse(
        id = "yogurt",
        source = "off",
        name = "Greek yogurt, plain",
        brand = "Fage",
        barcode = "012345678905",
        per100g = NutrientsDto(kcal = 59.0, protein = 10.0),
    )
}

private class FakeFoodApi : FoodApi {
    var searchResult: Result<FoodSearchResponse> = Result.success(FoodSearchResponse("", emptyList()))
    var detailResult: Result<FoodResponse>? = null
    var barcodeResult: Result<FoodResponse?> = Result.success(null)
    var optionsResult: Result<SearchOptionsResponse> = Result.success(SearchOptionsResponse(listOf("usda")))
    val filters = mutableListOf<FoodSearchFilter>()

    override suspend fun search(query: String, limit: Int, filter: FoodSearchFilter): Result<FoodSearchResponse> {
        filters += filter
        return searchResult
    }

    override suspend fun searchOptions() = optionsResult

    override suspend fun detail(id: String) = detailResult ?: Result.failure(IllegalStateException("not found"))

    override suspend fun barcode(code: String) = barcodeResult

    var describeResult: Result<DescribeResponse> = Result.success(DescribeResponse(emptyList()))

    override suspend fun describe(text: String) = describeResult
}

private class FakeFoodCacheDao : FoodCacheDao {
    val rows = MutableStateFlow<Map<String, FoodCacheEntity>>(emptyMap())

    override suspend fun upsert(row: FoodCacheEntity) {
        rows.value = rows.value + (row.id to row)
    }

    override suspend fun get(id: String) = rows.value[id]

    override suspend fun getByBarcode(barcode: String) = rows.value.values.firstOrNull { it.barcode == barcode }

    override suspend fun search(pattern: String, limit: Int): List<FoodCacheEntity> {
        val needle = pattern.trim('%')
        return rows.value.values.filter { needle in it.searchText }.take(limit)
    }

    override fun observeFavorites(): Flow<List<FoodCacheEntity>> =
        rows.map { m -> m.values.filter { it.favorite }.sortedBy { it.searchText } }

    override fun observeRecent(limit: Int): Flow<List<FoodCacheEntity>> =
        rows.map { m -> m.values.filter { it.lastUsedAtEpochMs != null }.sortedByDescending { it.lastUsedAtEpochMs }.take(limit) }

    override suspend fun deleteAll() {
        rows.value = emptyMap()
    }
}
