package com.opson.libraries.foods.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.foods.Confidence
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodCatalog
import com.opson.libraries.foods.FoodSearchResult
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.foods.FoodTier
import com.opson.libraries.foods.Gtin
import com.opson.libraries.foods.NutrientProfile
import com.opson.libraries.foods.ParsedItem
import com.opson.libraries.foods.Portion
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

    override suspend fun search(query: String, limit: Int): Catching<FoodSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Catching.success(FoodSearchResult(query = "", foods = emptyList(), fromCache = false))
        val bounded = limit.coerceIn(1, MAX_LIMIT)
        val online = api.search(trimmed, bounded)
        online.getOrNull()?.let { response ->
            return Catching.success(
                FoodSearchResult(query = response.query, foods = response.foods.map { it.toDomain() }, fromCache = false),
            )
        }
        // Out of reach (or the server failed): what the user has picked before
        // is better than an empty list in a grocery store basement.
        return Catching {
            FoodSearchResult(query = trimmed, foods = searchCache(trimmed, bounded), fromCache = true)
        }
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
        online.getOrNull()?.let { food ->
            // Kept but not marked used: the amount sheet opens it next, and has
            // to work even when the server cannot serve it again (offline, or
            // a source whose answers it may not store). It reaches Recent only
            // once it is logged.
            val existing = cache.get(food.id)
            cache.upsert(food.toCacheRow(lastUsedAt = existing?.lastUsedAtEpochMs, favorite = existing?.favorite ?: false))
        }
        if (online.isSuccess) return online
        return cachedByBarcode(trimmed)?.let { Catching.success(it) } ?: online
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
    }

    override suspend fun setFavorite(food: Food, favorite: Boolean) {
        val existing = cache.get(food.id)
        cache.upsert(food.toCacheRow(lastUsedAt = existing?.lastUsedAtEpochMs, favorite = favorite))
    }

    private suspend fun searchCache(query: String, limit: Int): List<Food> {
        // LIKE wildcards in what the user typed would match far too much.
        val needle = query.lowercase().replace("%", "").replace("_", "").trim()
        if (needle.isEmpty()) return emptyList()
        return cache.search("%$needle%", limit).mapNotNull { it.decode() }
    }

    private companion object {
        const val MAX_LIMIT = 50
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

private fun Food.toCacheRow(lastUsedAt: Long?, favorite: Boolean) = FoodCacheEntity(
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
)
