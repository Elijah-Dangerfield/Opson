package com.opson.server.testing

import com.opson.server.domain.BarcodeChoice
import com.opson.server.domain.BarcodeOutcome
import com.opson.server.domain.Barcodes
import com.opson.server.domain.CatalogSource
import com.opson.server.domain.ExternalProduct
import com.opson.server.domain.Food
import com.opson.server.domain.FoodCatalogRepository
import com.opson.server.domain.FoodMatch
import com.opson.server.domain.MatchTier
import com.opson.server.domain.Nutrients
import com.opson.server.domain.Portion
import com.opson.server.domain.SearchFilter
import com.opson.server.domain.SearchSort
import java.util.UUID

/**
 * In-memory [FoodCatalogRepository]. Search is deliberately simple (exact,
 * then prefix, then "every query word appears"), enough for the routes and
 * the describer; the real ranking is tested against Postgres.
 */
class FakeFoodCatalog(foods: List<Food> = emptyList()) : FoodCatalogRepository {
    val foods = foods.toMutableList()
    val searches = mutableListOf<Pair<String, Boolean>>()
    val filters = mutableListOf<SearchFilter>()
    override var searchesOpenFoodFacts: Boolean = false
    val barcodeOutcomes = mutableListOf<Pair<String, BarcodeOutcome>>()

    override suspend fun search(
        query: String,
        limit: Int,
        record: Boolean,
        country: String?,
        openFoodFacts: Boolean,
        filter: SearchFilter,
    ): List<FoodMatch> {
        val q = FoodCatalogRepository.normalizeQuery(query)
        searches += q to record
        filters += filter
        val words = q.split(' ').map { it.trimEnd('s') }
        return foods.mapNotNull { food ->
            val name = food.name.lowercase()
            val tier = when {
                name == q -> MatchTier.Exact
                name.startsWith(q) -> MatchTier.Prefix
                words.all { name.contains(it) } -> MatchTier.FullText
                else -> null
            }
            tier?.let { FoodMatch(food, it, 1.0) }
        }.sortedBy { it.tier }.take(limit).let { matches ->
            if (filter.sort == SearchSort.Name) matches.sortedBy { it.food.name.lowercase() } else matches
        }
    }

    /** Calls to [findById] and [findByIds], which stand for a transaction each in the real catalog. */
    var loads = 0

    override suspend fun findById(id: UUID): Food? {
        loads++
        return foods.firstOrNull { it.id == id }
    }

    override suspend fun findByIds(ids: Collection<UUID>): Map<UUID, Food> {
        loads++
        return foods.filter { it.id in ids }.associateBy { it.id }
    }

    override suspend fun findByBarcode(barcode: String): Food? = findAllByBarcode(barcode).firstOrNull()

    override suspend fun findAllByBarcode(barcode: String): List<Food> {
        val forms = Barcodes.equivalents(barcode)
        return BarcodeChoice.ordered(foods.filter { it.barcode in forms }.sortedBy { it.tier })
    }

    override suspend fun upsertExternalProduct(source: CatalogSource, product: ExternalProduct): Food {
        val existing = foods.indexOfFirst { it.source == source && it.sourceId == product.barcode }
        val food = Food(
            id = if (existing >= 0) foods[existing].id else UUID.randomUUID(),
            source = source,
            sourceId = product.barcode,
            name = product.name,
            brand = product.brand,
            barcode = product.barcode,
            per100g = product.per100g,
            micros = product.micros,
            portions = product.portions,
        )
        if (existing >= 0) foods[existing] = food else foods += food
        return food
    }

    override suspend fun recordBarcodeLookup(barcode: String, outcome: BarcodeOutcome) {
        barcodeOutcomes += barcode to outcome
    }

    companion object {
        fun food(
            name: String,
            kcal: Double? = 100.0,
            portions: List<Portion> = emptyList(),
            source: CatalogSource = CatalogSource.UsdaSr,
            barcode: String? = null,
            id: UUID = UUID.randomUUID(),
        ) = Food(
            id = id,
            source = source,
            sourceId = id.toString(),
            name = name,
            brand = null,
            barcode = barcode,
            per100g = Nutrients(kcal = kcal, protein = 10.0, carbs = 20.0, fat = 5.0, fiber = 2.0),
            micros = mapOf("sodium_mg" to 100.0),
            portions = portions,
        )
    }
}
