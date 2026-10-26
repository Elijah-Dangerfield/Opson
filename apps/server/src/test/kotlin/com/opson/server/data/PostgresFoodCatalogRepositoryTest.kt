package com.opson.server.data

import com.opson.server.db.DatabaseTest
import com.opson.server.db.FoodSearchEventsTable
import com.opson.server.db.FoodsTable
import com.opson.server.domain.BarcodeOutcome
import com.opson.server.domain.FoodSource
import com.opson.server.domain.MatchTier
import com.opson.server.domain.Nutrients
import com.opson.server.domain.OpenFoodFactsProduct
import com.opson.server.domain.Portion
import com.opson.server.testing.DiaryFixtures
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.junit.After
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class PostgresFoodCatalogRepositoryTest : DatabaseTest() {

    @After
    fun clean() = DiaryFixtures.cleanAll(database)

    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-10-25T12:00:00Z")
    }
    private val repo get() = PostgresFoodCatalogRepository(database, clock)

    private fun seed(name: String, source: String = "usda_sr", brand: String? = null, popularity: Int = 0): UUID {
        val id = UUID.randomUUID()
        database.blockingTransaction {
            FoodsTable.insert {
                it[FoodsTable.id] = id
                it[sourceKind] = source
                it[sourceId] = id.toString()
                it[FoodsTable.name] = name
                it[FoodsTable.brand] = brand
                it[kcal100g] = 100f
                it[micros] = """{"sodium_mg": 1.5}"""
                it[portions] = """[{"label": "1 cup", "grams": 240}]"""
            }
            FoodsTable.update({ FoodsTable.id eq id }) { it[FoodsTable.popularity] = popularity }
        }
        return id
    }

    private suspend fun names(query: String, limit: Int = 10) = repo.search(query, limit, record = false).map { it.food.name }

    @Test
    fun ranksExactThenHeadThenPrefixThenFullTextThenSimilar() = runTest {
        seed("Bread, banana")
        seed("Bananas, raw")
        seed("Banana")
        seed("Banana chips")
        seed("Bananna split") // typo-ish, trigram only

        val results = repo.search("banana", 10, record = false)

        assertEquals(
            listOf(
                "Banana" to MatchTier.Exact,
                "Bananas, raw" to MatchTier.Head,
                "Banana chips" to MatchTier.Prefix,
                "Bread, banana" to MatchTier.FullText,
                "Bananna split" to MatchTier.Similar,
            ),
            results.map { it.food.name to it.tier },
        )
    }

    @Test
    fun headMatchFindsUsdaStyleNamesForPlurals() = runTest {
        seed("Eggplant, raw")
        seed("Eggnog")
        seed("Egg, whole, raw")
        assertEquals("Egg, whole, raw", names("eggs").first())
    }

    @Test
    fun prefixNeedsWholeWords() = runTest {
        seed("Toaster pastries, fruit")
        seed("Melba toast")
        val results = repo.search("toast", 10, record = false)
        assertEquals("Melba toast", results.first().food.name, "whole word 'toast' beats the start of 'toaster'")
        assertTrue(results.none { it.tier == MatchTier.Prefix })
    }

    @Test
    fun halfTypedWordsStillMatch() = runTest {
        seed("Banana, raw")
        assertEquals(listOf("Banana, raw"), names("banan"))
    }

    @Test
    fun genericBeatsBrandedAtTheSameTier() = runTest {
        seed("Peanut butter, smooth", source = "off", brand = "Acme", popularity = 50)
        seed("Peanut butter, chunky", source = "usda_sr")
        assertEquals(listOf("Peanut butter, chunky", "Peanut butter, smooth"), names("peanut butter"))
    }

    @Test
    fun popularityBreaksTies() = runTest {
        seed("Apple, raw, gala")
        seed("Apple, raw, fuji", popularity = 10)
        assertEquals("Apple, raw, fuji", names("apple raw").first())
    }

    @Test
    fun fullTextMatchesWordsInAnyOrderAndPlurals() = runTest {
        seed("Yogurt, Greek, plain, nonfat")
        seed("Egg, whole, raw")
        assertEquals(listOf("Yogurt, Greek, plain, nonfat"), names("greek yogurt"))
        assertEquals(listOf("Egg, whole, raw"), names("eggs"))
        assertEquals(listOf("Egg, whole, raw"), names("whole egg"))
    }

    @Test
    fun matchesBrandInFullText() = runTest {
        seed("Hazelnut spread", source = "off", brand = "Nutella")
        assertEquals(listOf("Hazelnut spread"), names("nutella"))
    }

    @Test
    fun likeWildcardsInTheQueryAreLiteral() = runTest {
        seed("Banana")
        assertTrue(names("%").isEmpty())
        assertTrue(repo.search("_anana", 10, record = false).none { it.tier == MatchTier.Prefix })
    }

    @Test
    fun respectsTheLimit() = runTest {
        repeat(5) { seed("Rice $it") }
        assertEquals(2, names("rice", limit = 2).size)
    }

    @Test
    fun recordsOneAnonymousEventPerSearch() = runTest {
        seed("Banana")
        repo.search("  BANANA ", 10)
        repo.search("zzzz unknown food", 10)
        repo.search("banana", 10, record = false)

        val events = database.blockingTransaction {
            FoodSearchEventsTable.selectAll().orderBy(FoodSearchEventsTable.id).map { it[FoodSearchEventsTable.queryNorm] to it[FoodSearchEventsTable.resultCount] }
        }
        assertEquals(listOf("banana" to 1, "zzzz unknown food" to 0), events)
    }

    @Test
    fun decodesPortionsAndMicros() = runTest {
        val id = seed("Milk")
        val food = assertNotNull(repo.findById(id))
        assertEquals(listOf(Portion("1 cup", 240.0)), food.portions)
        assertEquals(mapOf("sodium_mg" to 1.5), food.micros)
        assertEquals(100.0, food.per100g.kcal)
        assertNull(repo.findById(UUID.randomUUID()))
    }

    @Test
    fun openFoodFactsUpsertIsIdempotentAndKeepsTheId() = runTest {
        val product = OpenFoodFactsProduct(
            barcode = "3017620422003", name = "Hazelnut spread", brand = "Acme",
            per100g = Nutrients(kcal = 539.0, fat = 30.9), micros = mapOf("sodium_mg" to 42.8),
            portions = listOf(Portion("15 g", 15.0)),
        )
        val first = repo.upsertOpenFoodFactsProduct(product)
        val second = repo.upsertOpenFoodFactsProduct(product.copy(name = "Hazelnut cocoa spread"))

        assertEquals(first.id, second.id)
        assertEquals("Hazelnut cocoa spread", second.name)
        assertEquals(FoodSource.OpenFoodFacts, second.source)
        assertEquals(second, repo.findByBarcode("3017620422003"))
        repo.recordBarcodeLookup("3017620422003", BarcodeOutcome.OpenFoodFacts)
    }
}
