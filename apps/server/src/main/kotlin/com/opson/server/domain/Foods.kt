package com.opson.server.domain

import java.util.UUID

/** Where a catalog food came from. Wire values match the `foods.source` CHECK. */
enum class FoodSource(val wire: String) {
    UsdaFoundation("usda_foundation"),
    UsdaSr("usda_sr"),
    UsdaFndds("usda_fndds"),
    OpenFoodFacts("off"),
    ;

    /** USDA foods are generic; Open Food Facts products are branded. */
    val isGeneric: Boolean get() = this != OpenFoodFacts

    companion object {
        fun fromWire(value: String): FoodSource? = entries.firstOrNull { it.wire == value }
    }
}

/** A household measure: `{label: "1 cup, sliced", grams: 150}`. */
data class Portion(val label: String, val grams: Double)

/**
 * A catalog food. Nutrients are per 100 g and null when the source does not
 * say. [micros] keys carry their unit (`sodium_mg`, `vitamin_d_ug`); see
 * scripts/import_usda_foods.py for the full list.
 */
data class Food(
    val id: UUID,
    val source: FoodSource,
    val sourceId: String,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val per100g: Nutrients,
    val micros: Map<String, Double>,
    val portions: List<Portion>,
)

/** How well a search result matched the query, best first. */
enum class MatchTier { Exact, Head, Prefix, FullText, Similar }

data class FoodMatch(val food: Food, val tier: MatchTier, val similarity: Double)

/**
 * The shared catalog. Search ranking (docs/system-design.md §7): exact name,
 * then prefix, then full text, then trigram similarity; generic (USDA) above
 * branded (Open Food Facts) at the same tier; popularity breaks ties.
 */
interface FoodCatalogRepository {
    /**
     * Ranked matches for [query]. When [record] is true, one anonymous
     * `food_search_events` row is written with the normalized query and the
     * result count (no user id, by design).
     */
    suspend fun search(query: String, limit: Int, record: Boolean = true): List<FoodMatch>

    suspend fun findById(id: UUID): Food?

    suspend fun findByBarcode(barcode: String): Food?

    /**
     * Insert or refresh an Open Food Facts product, keyed on
     * `(source = 'off', source_id = barcode)`. Returns the stored row, whose
     * id is stable across refreshes.
     */
    suspend fun upsertOpenFoodFactsProduct(product: OpenFoodFactsProduct): Food

    /** One anonymous `barcode_lookups` row. */
    suspend fun recordBarcodeLookup(barcode: String, outcome: BarcodeOutcome)

    companion object {
        const val DEFAULT_LIMIT = 25
        const val MAX_LIMIT = 50
        const val MAX_QUERY = 100

        /** Lowercase, trimmed, single-spaced, capped. What search matches on and what gets recorded. */
        fun normalizeQuery(raw: String): String =
            raw.lowercase().trim().replace(Regex("\\s+"), " ").take(MAX_QUERY)
    }
}

enum class BarcodeOutcome(val wire: String) {
    Catalog("catalog"),
    OpenFoodFacts("off"),
    Miss("miss"),
    Error("error"),
}

/** A product as Open Food Facts describes it, already mapped to per-100 g values. */
data class OpenFoodFactsProduct(
    val barcode: String,
    val name: String,
    val brand: String?,
    val per100g: Nutrients,
    val micros: Map<String, Double>,
    val portions: List<Portion>,
)

sealed interface OpenFoodFactsResult {
    data class Found(val product: OpenFoodFactsProduct) : OpenFoodFactsResult
    data object NotFound : OpenFoodFactsResult

    /** Timeout, network failure or a 5xx. Not cached, not a miss. */
    data class Unavailable(val cause: Throwable?) : OpenFoodFactsResult
}

/** `world.openfoodfacts.org/api/v2/product/{code}.json`. No key; descriptive User-Agent. */
interface OpenFoodFactsClient {
    suspend fun product(barcode: String): OpenFoodFactsResult
}

sealed interface BarcodeLookupResult {
    data class Found(val food: Food) : BarcodeLookupResult
    data object NotFound : BarcodeLookupResult
    data object Unavailable : BarcodeLookupResult
}

/** Catalog first, then Open Food Facts (cached into the catalog), else not found. */
interface BarcodeLookup {
    suspend fun lookup(barcode: String): BarcodeLookupResult

    companion object {
        /** EAN-8 through GTIN-14, digits only. */
        fun isValidBarcode(code: String): Boolean = code.length in 8..14 && code.all { it in '0'..'9' }
    }
}
