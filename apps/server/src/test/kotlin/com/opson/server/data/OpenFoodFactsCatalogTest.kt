package com.opson.server.data

import com.opson.server.config.FoodSourcesConfig
import com.opson.server.db.DatabaseTest
import com.opson.server.db.FoodsTable
import com.opson.server.domain.CatalogSource
import com.opson.server.domain.FoodTier
import com.opson.server.testing.DiaryFixtures
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.After
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/** Open Food Facts in name search, behind `FOOD_SEARCH_OFF_ENABLED`. */
class OpenFoodFactsCatalogTest : DatabaseTest() {

    @After
    fun clean() {
        DiaryFixtures.cleanAll(database)
        database.blockingTransaction { TransactionManager.current().exec("DELETE FROM off_foods") }
    }

    private fun repo(enabled: Boolean) =
        PostgresFoodCatalogRepository(database, Clock.System, FoodSourcesConfig(searchOpenFoodFacts = enabled))

    private fun seedGeneric(name: String) {
        database.blockingTransaction {
            FoodsTable.insert {
                it[id] = UUID.randomUUID()
                it[sourceKind] = "usda_fndds"
                it[sourceId] = name
                it[FoodsTable.name] = name
                it[kcal100g] = 100f
            }
        }
    }

    private fun seedOff(name: String, barcode: String, countries: List<String> = emptyList(), scans: Int = 0): UUID {
        val id = UUID.randomUUID()
        val tags = countries.joinToString(",", "{", "}")
        database.blockingTransaction {
            TransactionManager.current().exec(
                """
                INSERT INTO off_foods (id, barcode, name, kcal_100g, protein_100g, carbs_100g, fat_100g,
                                       portions, countries_tags, source_version, scans)
                VALUES ('$id', '$barcode', '${name.replace("'", "''")}', 250, 8, 30, 10,
                        '[{"label": "1 slice (25 g)", "grams": 25}]', '$tags', '2026-10-26', $scans)
                """.trimIndent(),
            )
        }
        return id
    }

    @Test
    fun offByDefaultSearchAnswersAsIn01() = runTest {
        seedGeneric("Bread, white")
        val offId = seedOff("Pain de mie", "03000000000017")
        val results = repo(enabled = false).search("pain de mie", 10, record = false, openFoodFacts = true)
        assertTrue(results.isEmpty(), "Open Food Facts is not searched until the flag is on")
        assertNull(repo(enabled = false).findById(offId))
    }

    @Test
    fun onItFillsInWhatTheCatalogLacks() = runTest {
        seedGeneric("Bread, white")
        val offId = seedOff("Pain de mie", "03000000000017", countries = listOf("en:france"))
        val catalog = repo(enabled = true)

        val found = catalog.search("pain de mie", 10, record = false, country = "FR", openFoodFacts = true).single().food
        assertEquals(offId, found.id)
        assertEquals(CatalogSource.OpenFoodFacts, found.source)
        assertEquals(FoodTier.Community, found.tier)
        assertEquals(false, found.verified)
        assertEquals("03000000000017", found.barcode)
        assertEquals(250.0, found.per100g.kcal)
        assertEquals(found, assertNotNull(catalog.findById(offId)), "detail answers the id search handed out")

        assertTrue(catalog.search("pain de mie", 10, record = false).isEmpty(), "callers that don't ask (Describe) never get it")
    }

    @Test
    fun genericFoodsStayFirstAndTheCallersCountryLeadsTheRest() = runTest {
        seedGeneric("Bread, white")
        seedOff("White bread", "03000000000024", countries = listOf("en:france"), scans = 500)
        seedOff("White bread", "00000000000031", countries = listOf("en:united-states"), scans = 3)
        val names = repo(enabled = true).search("white bread", 10, record = false, country = "US", openFoodFacts = true)
            .map { it.food.name to it.food.barcode }
        assertEquals(
            listOf("Bread, white" to null, "White bread" to "00000000000031", "White bread" to "03000000000024"),
            names,
        )
    }

    @Test
    fun shortQueriesLeaveOpenFoodFactsOut() = runTest {
        seedOff("Mi-cuit", "03000000000048", scans = 900)
        assertTrue(repo(enabled = true).search("mi", 10, record = false, openFoodFacts = true).isEmpty())
        assertEquals(1, repo(enabled = true).search("mi-cuit", 10, record = false, openFoodFacts = true).size)
    }

    @Test
    fun aTypoStillFindsAProduct() = runTest {
        seedOff("Cheerios", "00016000275287", scans = 10)
        val results = repo(enabled = true).search("cheeerios", 10, record = false, openFoodFacts = true)
        assertEquals(listOf("Cheerios"), results.map { it.food.name })
    }
}
