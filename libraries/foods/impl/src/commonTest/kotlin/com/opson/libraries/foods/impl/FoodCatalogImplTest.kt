package com.opson.libraries.foods.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
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
import kotlin.time.Instant

/**
 * [FoodCatalogImpl] over a fake API and an in-memory cache: the offline
 * fallbacks, what lands in the cache (only foods the user picked or starred),
 * and that Recent and Favorites read from it.
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
    fun searchResultsComeFromTheServerWhenItAnswers() = runUnitTest {
        api.searchResult = Result.success(FoodSearchResponse("rice", listOf(rice)))

        val result = catalog.search("Rice ").getOrThrow()

        assertFalse(result.fromCache)
        assertEquals(listOf("rice"), result.foods.map { it.id })
        assertTrue(cache.rows.value.isEmpty(), "browsing results is not picking them")
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

    override suspend fun search(query: String, limit: Int) = searchResult

    override suspend fun detail(id: String) = detailResult ?: Result.failure(IllegalStateException("not found"))

    override suspend fun barcode(code: String) = barcodeResult

    override suspend fun describe(text: String) = Result.success(DescribeResponse(emptyList()))
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
