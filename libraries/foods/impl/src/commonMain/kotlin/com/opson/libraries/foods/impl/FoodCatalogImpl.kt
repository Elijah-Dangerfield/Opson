package com.opson.libraries.foods.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.foods.BarcodeMatch
import com.opson.libraries.foods.Confidence
import com.opson.libraries.foods.EnergyCheck
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodCatalog
import com.opson.libraries.foods.FoodQuery
import com.opson.libraries.foods.FoodSearchFilter
import com.opson.libraries.foods.FoodSearchResult
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.foods.FoodTier
import com.opson.libraries.foods.Gtin
import com.opson.libraries.foods.NutrientProfile
import com.opson.libraries.foods.ParsedItem
import com.opson.libraries.foods.Portion
import com.opson.libraries.foods.ReviewStatus
import com.opson.libraries.foods.SearchOptions
import com.opson.libraries.foods.SearchOrder
import com.opson.libraries.foods.SearchSource
import com.opson.libraries.foods.passesOffline
import com.opson.libraries.foods.db.FoodCacheDao
import com.opson.libraries.foods.db.FoodCacheEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class FoodCatalogImpl(
    private val api: FoodApi,
    private val cache: FoodCacheDao,
    private val clock: Clock,
) : FoodCatalog {

    private val recentSearches = RecentSearches(clock)

    override suspend fun search(query: String, limit: Int, filter: FoodSearchFilter): Catching<FoodSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Catching.success(FoodSearchResult(query = "", foods = emptyList(), fromCache = false))
        val bounded = limit.coerceIn(1, MAX_LIMIT)
        recentSearches.get(trimmed, bounded, filter)?.let { return Catching.success(it) }
        val online = api.search(trimmed, bounded, filter)
        online.getOrNull()?.let { response ->
            val result = FoodSearchResult(query = response.query, foods = response.foods.map { it.toDomain() }, fromCache = false)
            recentSearches.put(trimmed, bounded, filter, result)
            return Catching.success(result)
        }
        // Out of reach (or the server failed): what the user has picked before
        // is better than an empty list in a grocery store basement.
        return Catching {
            val cached = searchCache(trimmed, bounded).filter { it.passesOffline(filter) }
            val ordered = if (filter.order == SearchOrder.Name) cached.sortedBy { it.name.lowercase() } else cached
            FoodSearchResult(query = trimmed, foods = ordered, fromCache = true)
        }
    }

    override fun recentSearch(query: String, limit: Int, filter: FoodSearchFilter): FoodSearchResult? =
        query.trim().takeIf { it.isNotEmpty() }?.let { recentSearches.get(it, limit.coerceIn(1, MAX_LIMIT), filter) }

    override suspend fun searchOptions(): Catching<SearchOptions> = api.searchOptions().map { response ->
        SearchOptions(openFoodFacts = SearchSource.OpenFoodFacts.wire in response.sources)
    }

    override suspend fun detail(id: String): Catching<Food> {
        val online = api.detail(id).map { it.toDomain() }
        online.getOrNull()?.let { food ->
            // Keep a cached copy current; foods nobody picked stay out of the cache.
            cache.get(id)?.let { cache.upsert(it.copy(json = food.encode(), searchText = food.searchText())) }
            return Catching.success(food)
        }
        return cache.get(id)?.decode()?.let { Catching.success(it) } ?: online
    }

    override suspend fun barcode(code: String): Catching<Food?> {
        val trimmed = code.trim()
        val online = api.barcode(trimmed).map { it?.toDomain() }
        online.getOrNull()?.let { keep(it) }
        if (online.isSuccess) return online
        return cachedByBarcode(trimmed)?.let { Catching.success(it) } ?: online
    }

    override suspend fun barcodeMatches(code: String): Catching<List<BarcodeMatch>> {
        val trimmed = code.trim()
        val online = api.barcodeMatches(trimmed).map { response ->
            response?.let { answer ->
                val listed = answer.matches.orEmpty().map { it.toDomain() }
                val food = answer.toDomain()
                // The answer leads, whatever the list says; a server without the list gives the answer alone.
                listOf(listed.firstOrNull { it.food.id == food.id } ?: BarcodeMatch(food)) + listed.filter { it.food.id != food.id }
            }.orEmpty()
        }
        // Only the answer is kept, so an offline scan of the code finds the same food a single answer would.
        online.getOrNull()?.firstOrNull()?.let { keep(it.food) }
        if (online.isSuccess) return online
        val cached = cachedByBarcode(trimmed) ?: return online
        return Catching.success(listOf(BarcodeMatch(cached)))
    }

    /**
     * A scanned food, kept but not marked used: the amount sheet opens it next, and has to work
     * even when the server cannot serve it again (offline, or a source whose answers it may not
     * store). It reaches Recent only once it is logged.
     */
    private suspend fun keep(food: Food) {
        val existing = cache.get(food.id)
        cache.upsert(food.toCacheRow(lastUsedAt = existing?.lastUsedAtEpochMs, favorite = existing?.favorite ?: false))
    }

    /** The cache stores GTINs (older rows the code as the server sent it), so try both forms. */
    private suspend fun cachedByBarcode(code: String): Food? =
        listOfNotNull(Gtin.normalize(code), code).distinct().firstNotNullOfOrNull { cache.getByBarcode(it)?.decode() }

    override suspend fun describe(text: String): Catching<List<ParsedItem>> =
        api.describe(text.trim()).map { response -> response.items.map { it.toDomain() } }

    override fun observeFavorites(): Flow<List<Food>> =
        cache.observeFavorites().map { rows -> rows.mapNotNull { it.decode() } }

    override fun observeRecent(limit: Int): Flow<List<Food>> =
        cache.observeRecent(limit).map { rows -> rows.mapNotNull { it.decode() } }

    override suspend fun markUsed(food: Food) {
        val existing = cache.get(food.id)
        cache.upsert(food.toCacheRow(lastUsedAt = clock.now().toEpochMilliseconds(), favorite = existing?.favorite ?: false))
        cache.trim(keep = CACHE_LIMIT)
    }

    override suspend fun setFavorite(food: Food, favorite: Boolean) {
        val existing = cache.get(food.id)
        cache.upsert(food.toCacheRow(lastUsedAt = existing?.lastUsedAtEpochMs, favorite = favorite))
    }

    /**
     * The words typed against every food on the phone ([FoodQuery], as the server reads them),
     * favorites first, then the most recently used. Only the matches' JSON is read.
     */
    private suspend fun searchCache(query: String, limit: Int): List<Food> {
        val ids = cache.searchable().filter { FoodQuery.matches(query, it.searchText) }.take(limit).map { it.id }
        if (ids.isEmpty()) return emptyList()
        val rows = cache.getAll(ids).associateBy { it.id }
        return ids.mapNotNull { rows[it]?.decode() }
    }

    internal companion object {
        const val MAX_LIMIT = 50

        /** Catalog foods kept on the phone besides favorites: the long tail search and logging use offline. */
        const val CACHE_LIMIT = 500
    }
}

private val CacheJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

private fun Food.encode(): String = CacheJson.encodeToString(Food.serializer(), this)

/** A row another build wrote in a shape this one cannot read is skipped, not fatal. */
private fun FoodCacheEntity.decode(): Food? = Catching { CacheJson.decodeFromString(Food.serializer(), json) }.getOrNull()

private fun Food.searchText(): String = listOfNotNull(name, brand).joinToString(" ").lowercase()

internal fun Food.toCacheRow(lastUsedAt: Long?, favorite: Boolean) = FoodCacheEntity(
    id = id,
    json = encode(),
    searchText = searchText(),
    // As a GTIN, so a later scan in any of the code's forms finds it offline.
    barcode = barcode?.let { Gtin.normalize(it) ?: it },
    lastUsedAtEpochMs = lastUsedAt,
    favorite = favorite,
)

internal fun FoodResponse.toDomain(): Food = Food(
    id = id,
    source = FoodSource.fromWire(source),
    name = name,
    per100g = NutrientProfile(per100g.kcal, per100g.protein, per100g.carbs, per100g.fat, per100g.fiber),
    brand = brand,
    barcode = barcode,
    micros = micros,
    // A serving known only by volume has no gram weight to scale by, so it is
    // left out and the amount sheet falls back to grams, as for a food with
    // no portions at all.
    portions = portions.mapNotNull { p -> p.grams?.let { Portion(p.label, it) } },
    tier = FoodTier.fromWire(tier),
    verified = verified,
    reviewStatus = ReviewStatus.fromWire(reviewStatus),
)

internal fun BarcodeMatchDto.toDomain(): BarcodeMatch = BarcodeMatch(
    food = food.toDomain(),
    energyCheck = energyCheck.mapNotNullTo(LinkedHashSet()) { EnergyCheck.fromWire(it) },
    corrected = corrected,
)

internal fun DescribedItemDto.toDomain(): ParsedItem = ParsedItem(
    text = text,
    name = name,
    quantity = quantity,
    amountText = amountText,
    confidence = Confidence.fromWire(confidence),
    foodId = foodId,
    unit = unit,
    portionLabel = portionLabel,
    grams = grams,
    kcal = kcal,
    protein = protein,
    carbs = carbs,
    fat = fat,
    fiber = fiber,
    micros = micros,
    food = food?.toDomain(),
    alternatives = alternatives.map { it.toDomain() },
)
