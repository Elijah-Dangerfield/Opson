package com.opson.libraries.foods.impl

import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.LoggedFood
import com.opson.libraries.diary.LoggedFoods
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.BarcodeMatch
import com.opson.libraries.foods.EnergyCheck
import com.opson.libraries.foods.FoodSearchFilter
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.foods.FoodTier
import com.opson.libraries.foods.Portion
import com.opson.libraries.foods.ReviewStatus
import com.opson.libraries.foods.SearchCategory
import com.opson.libraries.foods.SearchOptions
import com.opson.libraries.foods.SearchOrder
import com.opson.libraries.foods.SearchSource
import com.opson.libraries.foods.db.FoodCacheDao
import com.opson.libraries.foods.db.FoodCacheEntity
import com.opson.libraries.foods.db.FoodCacheSearchRow
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
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
    fun offlineSearchMatchesWordsAsTheServerDoes() = runUnitTest {
        catalog.markUsed(rice.toDomain())
        catalog.markUsed(rice.copy(id = "berries", name = "Strawberries, raw").toDomain())
        api.searchResult = Result.failure(IllegalStateException("offline"))

        assertEquals(listOf("rice"), catalog.search("cooked jasmine").getOrThrow().foods.map { it.id })
        assertEquals(listOf("berries"), catalog.search("strawberry").getOrThrow().foods.map { it.id })
    }

    @Test
    fun theCacheKeepsTheLongTailAndEveryFavorite() = runUnitTest {
        catalog.setFavorite(yogurt.toDomain(), favorite = true)
        repeat(FoodCatalogImpl.CACHE_LIMIT + 5) {
            clock.now += 1.seconds
            catalog.markUsed(rice.copy(id = "food-$it").toDomain())
        }

        val kept = cache.rows.value.keys
        assertEquals(FoodCatalogImpl.CACHE_LIMIT + 1, kept.size)
        assertTrue("yogurt" in kept, "a favorite is never trimmed")
        assertFalse("food-0" in kept, "the oldest goes first")
        assertTrue("food-${FoodCatalogImpl.CACHE_LIMIT + 4}" in kept)
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
    fun everyMatchComesBackWithTheAnswerFirstAndOnlyTheAnswerIsKept() = runUnitTest {
        val branded = yogurt.copy(id = "yogurt-branded", source = "usda_branded", per100g = NutrientsDto(kcal = 0.0, protein = 10.0))
        api.barcodeResult = Result.success(
            yogurt.copy(
                matches = listOf(
                    BarcodeMatchDto(branded, energyCheck = listOf("zero_with_macros", "something_newer")),
                    BarcodeMatchDto(yogurt, corrected = true),
                ),
            ),
        )

        val matches = catalog.barcodeMatches("012345678905").getOrThrow()

        assertEquals(listOf("yogurt", "yogurt-branded"), matches.map { it.food.id }, "the answer leads even if the list is out of order")
        assertTrue(matches[0].corrected)
        assertEquals(setOf(EnergyCheck.ZeroWithMacros), matches[1].energyCheck, "an unknown check is dropped, not fatal")
        assertTrue(matches[1].numbersLookOff)
        assertEquals(listOf("yogurt"), cache.rows.value.keys.toList(), "only the answer is kept on the device")
    }

    @Test
    fun aServerWithoutTheListGivesTheAnswerAloneAndOfflineTheKeptOne() = runUnitTest {
        api.barcodeResult = Result.success(yogurt)
        assertEquals(listOf(BarcodeMatch(yogurt.toDomain())), catalog.barcodeMatches("012345678905").getOrThrow())

        api.barcodeResult = Result.failure(IllegalStateException("offline"))
        assertEquals(listOf("yogurt"), catalog.barcodeMatches("0012345678905").getOrThrow().map { it.food.id })
        assertTrue(catalog.barcodeMatches("4006381333931").isFailure, "never scanned, and out of reach")

        api.barcodeResult = Result.success(null)
        assertEquals(emptyList(), catalog.barcodeMatches("4006381333931").getOrThrow())
        assertEquals(4, api.matchesAsked)
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
        assertEquals(ReviewStatus.Approved, fromAnOlderServer.reviewStatus, "no field is a food nobody has to check")
    }

    @Test
    fun aFoodThePersonAddedArrivesAsOpsonsAndUnverifiedUntilApproved() {
        val bar = FoodResponse(
            id = "bar",
            source = "user_submitted",
            name = "Oat bar",
            per100g = NutrientsDto(kcal = 450.0),
            tier = 4,
            verified = false,
            reviewStatus = "pending",
        ).toDomain()
        assertEquals(FoodSource.UserSubmitted, bar.source)
        assertEquals(ReviewStatus.Pending, bar.reviewStatus)
        assertTrue(bar.unverified)

        val approved = bar.copy(reviewStatus = ReviewStatus.Approved, verified = true)
        assertEquals(false, approved.unverified)
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

    val detailsById = mutableMapOf<String, FoodResponse>()
    val detailsAsked = mutableListOf<String>()

    override suspend fun detail(id: String): Result<FoodResponse> {
        detailsAsked += id
        return detailsById[id]?.let { Result.success(it) } ?: detailResult ?: Result.failure(IllegalStateException("not found"))
    }

    /** Ids the server answers 404 for. Any other id it lacks fails as [unreachable] does. */
    val goneIds = mutableSetOf<String>()
    var unreachable: Throwable = IOException("offline")

    override suspend fun detailOrGone(id: String): Result<FoodResponse?> {
        detailsAsked += id
        return when (id) {
            in detailsById -> Result.success(detailsById.getValue(id))
            in goneIds -> Result.success(null)
            else -> Result.failure(unreachable)
        }
    }

    override suspend fun barcode(code: String) = barcodeResult

    var matchesAsked = 0

    override suspend fun barcodeMatches(code: String): Result<FoodResponse?> {
        matchesAsked++
        return barcodeResult
    }

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

    override suspend fun searchable(): List<FoodCacheSearchRow> =
        rows.value.values
            .sortedWith(compareByDescending<FoodCacheEntity> { it.favorite }.thenByDescending { it.lastUsedAtEpochMs ?: Long.MIN_VALUE })
            .map { FoodCacheSearchRow(it.id, it.searchText, it.favorite, it.lastUsedAtEpochMs) }

    override suspend fun getAll(ids: List<String>): List<FoodCacheEntity> = ids.mapNotNull { rows.value[it] }

    override suspend fun trim(keep: Int) {
        val kept = rows.value.values.filterNot { it.favorite }
            .sortedByDescending { it.lastUsedAtEpochMs ?: Long.MIN_VALUE }
            .take(keep)
            .mapTo(HashSet()) { it.id }
        rows.value = rows.value.filterValues { it.favorite || it.id in kept }
    }

    override fun observeFavorites(): Flow<List<FoodCacheEntity>> =
        rows.map { m -> m.values.filter { it.favorite }.sortedBy { it.searchText } }

    override fun observeRecent(limit: Int): Flow<List<FoodCacheEntity>> =
        rows.map { m -> m.values.filter { it.lastUsedAtEpochMs != null }.sortedByDescending { it.lastUsedAtEpochMs }.take(limit) }

    override suspend fun deleteAll() {
        rows.value = emptyMap()
    }

    override suspend fun deleteAllButFavorites() {
        rows.value = rows.value.filterValues { it.favorite }
    }
}

class LongTailFoodCacheTest : CoroutineTest() {
    private val api = FakeFoodApi()
    private val cache = FakeFoodCacheDao()
    private val logged = MutableStateFlow<List<LoggedFood>>(emptyList())
    private val loggedFoods = object : LoggedFoods {
        override fun observe(limit: Int): Flow<List<LoggedFood>> = logged.map { it.take(limit) }
    }
    private val longTail = LongTailFoodCache(loggedFoods, api, cache)

    @Test
    fun fetchesTheCatalogFoodsThePhoneLacksAndNoOthers() = runUnitTest {
        api.detailsById += "oats" to food("oats", "Oats, raw")
        cache.upsert(food("eggs", "Egg, whole, raw").toDomain().toCacheRow(lastUsedAt = 1L, favorite = false))
        logged.value = listOf(logged("oats", at = 30L), logged("eggs", at = 20L), logged(null, at = 10L, custom = "brownie"))

        assertTrue(longTail.sync().isSuccess)

        assertEquals(listOf("oats"), api.detailsAsked)
        assertEquals(30L, cache.rows.value.getValue("oats").lastUsedAtEpochMs, "it sits in Recent where the diary has it")
        assertTrue(longTail.sync().isSuccess)
        assertEquals(listOf("oats"), api.detailsAsked, "nothing more to ask for")
    }

    @Test
    fun offlineItStopsAndTriesAgainNextTime() = runUnitTest {
        logged.value = List(5) { logged("food-$it", at = 100L - it) }

        assertTrue(longTail.sync().isFailure)
        assertEquals(3, api.detailsAsked.size, "three failures in a row and it stops")

        repeat(5) { api.detailsById += "food-$it" to food("food-$it", "Rice") }
        assertTrue(longTail.sync().isSuccess)
        assertEquals(5, cache.rows.value.size)
    }

    @Test
    fun aFoodTheServerNoLongerHasIsNotAskedForAgain() = runUnitTest {
        api.detailsById += "kept" to food("kept", "Oats, raw")
        api.goneIds += "gone"
        logged.value = listOf(logged("gone", at = 20L), logged("kept", at = 10L))

        longTail.sync()
        longTail.sync()

        assertEquals(listOf("gone", "kept"), api.detailsAsked)
    }

    @Test
    fun foodsThatAreAllGoneAreAnAnswerNotAFailure() = runUnitTest {
        api.goneIds += List(5) { "gone-$it" }
        logged.value = List(5) { logged("gone-$it", at = 100L - it) }

        assertTrue(longTail.sync().isSuccess, "a 404 is the server answering")
        assertEquals(5, api.detailsAsked.size, "each is asked once, none stops the run")
        assertTrue(longTail.sync().isSuccess)
        assertEquals(5, api.detailsAsked.size, "and none is asked for again")
    }

    @Test
    fun aRefusalOtherThanTransportIsNotRetriedAndDoesNotFail() = runUnitTest {
        api.unreachable = IllegalStateException("not a food")
        logged.value = List(4) { logged("odd-$it", at = 100L - it) }

        assertTrue(longTail.sync().isSuccess)
        assertEquals(4, api.detailsAsked.size)
    }

    private fun food(id: String, name: String) = FoodResponse(
        id = id,
        source = "usda_sr",
        name = name,
        per100g = NutrientsDto(kcal = 100.0),
    )

    private fun logged(foodId: String?, at: Long, custom: String? = null) = LoggedFood(
        latest = LogItem(
            id = "entry-$foodId-$custom",
            day = LocalDate(2026, 10, 25),
            slot = MealSlot.Lunch,
            loggedAt = Instant.fromEpochMilliseconds(at),
            name = foodId ?: custom.orEmpty(),
            precision = Precision.Estimate,
            method = LogMethod.Search,
            foodId = foodId,
            customFoodId = custom,
        ),
        times = 1,
    )
}
