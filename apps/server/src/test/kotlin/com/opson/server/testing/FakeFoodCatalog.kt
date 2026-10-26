package com.opson.server.testing

import com.opson.server.domain.BarcodeOutcome
import com.opson.server.domain.Food
import com.opson.server.domain.FoodCatalogRepository
import com.opson.server.domain.FoodMatch
import com.opson.server.domain.FoodSource
import com.opson.server.domain.MatchTier
import com.opson.server.domain.Nutrients
import com.opson.server.domain.OpenFoodFactsProduct
import com.opson.server.domain.Portion
import java.util.UUID

/**
 * In-memory [FoodCatalogRepository]. Search is deliberately simple (exact,
 * then prefix, then "every query word appears"), enough for the routes and
 * the describer; the real ranking is tested against Postgres.
 */
class FakeFoodCatalog(foods: List<Food> = emptyList()) : FoodCatalogRepository {
    val foods = foods.toMutableList()
    val searches = mutableListOf<Pair<String, Boolean>>()
    val barcodeOutcomes = mutableListOf<Pair<String, BarcodeOutcome>>()

    override suspend fun search(query: String, limit: Int, record: Boolean): List<FoodMatch> {
        val q = FoodCatalogRepository.normalizeQuery(query)
        searches += q to record
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
        }.sortedBy { it.tier }.take(limit)
    }

    override suspend fun findById(id: UUID): Food? = foods.firstOrNull { it.id == id }

    override suspend fun findByBarcode(barcode: String): Food? = foods.firstOrNull { it.barcode == barcode }

    override suspend fun upsertOpenFoodFactsProduct(product: OpenFoodFactsProduct): Food {
        val existing = foods.indexOfFirst { it.source == FoodSource.OpenFoodFacts && it.sourceId == product.barcode }
        val food = Food(
            id = if (existing >= 0) foods[existing].id else UUID.randomUUID(),
            source = FoodSource.OpenFoodFacts,
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
            source: FoodSource = FoodSource.UsdaSr,
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
