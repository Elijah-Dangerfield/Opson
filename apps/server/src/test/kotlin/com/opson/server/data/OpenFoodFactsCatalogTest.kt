package com.opson.server.data

import com.opson.server.config.FoodSourcesConfig
import com.opson.server.db.DatabaseTest
import com.opson.server.db.FoodsTable
import com.opson.server.domain.BarcodeLookupResult
import com.opson.server.domain.BarcodeOutcome
import com.opson.server.domain.CatalogSource
import com.opson.server.domain.FoodSource
import com.opson.server.domain.FoodSourceResult
import com.opson.server.domain.FoodTier
import com.opson.server.domain.LiveFoodSources
import com.opson.server.domain.SearchCategory
import com.opson.server.domain.SearchFilter
import com.opson.server.domain.SearchSource
import com.opson.server.testing.DiaryFixtures
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
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

    private fun seedOff(name: String, barcode: String, countries: List<String> = emptyList(), scans: Int = 0, kcal: Int = 250): UUID {
        val id = UUID.randomUUID()
        val tags = countries.joinToString(",", "{", "}")
        database.blockingTransaction {
            TransactionManager.current().exec(
                """
                INSERT INTO off_foods (id, barcode, name, kcal_100g, protein_100g, carbs_100g, fat_100g,
                                       portions, countries_tags, source_version, scans)
                VALUES ('$id', '$barcode', '${name.replace("'", "''")}', $kcal, 8, 30, 10,
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
        // Different labels: two loaves, not one product listed twice.
        seedOff("White bread", "03000000000024", countries = listOf("en:france"), scans = 500, kcal = 260)
        seedOff("White bread", "00000000000031", countries = listOf("en:united-states"), scans = 3)
        val names = repo(enabled = true).search("white bread", 10, record = false, country = "US", openFoodFacts = true)
            .map { it.food.name to it.food.barcode }
        assertEquals(
            listOf("Bread, white" to null, "White bread" to "00000000000031", "White bread" to "03000000000024"),
            names,
        )
    }

    @Test
    fun copiesOfOneProductCollapse_andABrandsRowsLeaveRoomForOpenFoodFacts() = runTest {
        // On dev "nutella" filled the first screen with Branded copies of the jar,
        // and Open Food Facts' own copies of it sat below them, or twice in a row.
        fun branded(name: String, sourceId: String, kcal: Float, brand: String = "Nutella") = database.blockingTransaction {
            FoodsTable.insert {
                it[id] = UUID.randomUUID()
                it[sourceKind] = "usda_branded"
                it[FoodsTable.sourceId] = sourceId
                it[FoodsTable.name] = name
                it[FoodsTable.brand] = brand
                it[kcal100g] = kcal
            }
        }
        repeat(8) { branded("Nutella", "jar-$it", 541f) }
        repeat(12) { branded("Hazelnut Spread with Cocoa $it", "spread-$it", 500f + it) }
        val jar = seedOff("Nutella", "03017620422003", scans = 900, kcal = 539)
        seedOff("Nutella", "03017620425035", scans = 50, kcal = 539)
        seedOff("Nutella", "08000500310427", scans = 5, kcal = 539)

        val results = repo(enabled = true).search("nutella", 10, record = false, openFoodFacts = true)

        assertEquals(listOf("Nutella" to CatalogSource.UsdaBranded, "Nutella" to CatalogSource.OpenFoodFacts), results.take(2).map { it.food.name to it.food.source })
        assertEquals(jar, results[1].food.id, "the most scanned copy")
        assertEquals(1, results.count { it.food.source == CatalogSource.OpenFoodFacts })
        assertEquals(10, results.size)
    }

    @Test
    fun aScanReadsTheExportBeforeCallingOpenFoodFacts() = runTest {
        val jar = seedOff("Nutella", "03017620422003", scans = 900)
        val liveCalls = mutableListOf<String>()
        val live = object : FoodSource {
            override val catalogSource = CatalogSource.OpenFoodFacts
            override val outcome = BarcodeOutcome.OpenFoodFacts
            override val cacheable = true
            override suspend fun lookup(barcode: String): FoodSourceResult {
                liveCalls += barcode
                return FoodSourceResult.NotFound
            }
        }
        fun lookup(enabled: Boolean) = CatalogBarcodeLookup(repo(enabled), LiveFoodSources { listOf(live) })

        // The EAN-13 as printed on the jar, and its 14-digit GTIN form.
        for (scanned in listOf("3017620422003", "03017620422003")) {
            val found = lookup(enabled = true).lookup(scanned)
            assertEquals(jar, (found as BarcodeLookupResult.Found).food.id, scanned)
        }
        assertTrue(liveCalls.isEmpty(), "the export answered: $liveCalls")

        assertEquals(BarcodeLookupResult.NotFound, lookup(enabled = false).lookup("3017620422003"))
        assertEquals(listOf("3017620422003"), liveCalls, "with the flag off only the live API is asked")
    }

    @Test
    fun theSourceFilterPicksOneSideAndCategoriesLeaveOpenFoodFactsOut() = runTest {
        seedGeneric("Bread, white")
        seedOff("White bread", "03000000000024", scans = 500)
        val catalog = repo(enabled = true)
        suspend fun names(filter: SearchFilter) =
            catalog.search("white bread", 10, record = false, openFoodFacts = true, filter = filter).map { it.food.name }

        assertEquals(listOf("Bread, white", "White bread"), names(SearchFilter.None))
        assertEquals(listOf("White bread"), names(SearchFilter(source = SearchSource.OpenFoodFacts)))
        assertEquals(listOf("Bread, white"), names(SearchFilter(source = SearchSource.Usda)))
        // Open Food Facts rows carry no category and are tier 4, so no tab ever shows them.
        assertEquals(listOf("Bread, white"), names(SearchFilter(category = SearchCategory.Common)))
        assertEquals(emptyList(), names(SearchFilter(category = SearchCategory.Beverage)))
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

    /**
     * The Monster bug, end to end on Postgres: the code is in USDA Branded as imported
     * (FoodData Central 2680587: 0 kcal and 0.83 g carbs per 100 ml, an 8 fl oz serving in ml
     * with no grams) and in the Open Food Facts export (2 kcal per 100 ml, a 473 ml can).
     * Before, the better tier answered with 0 kcal for any amount.
     */
    @Test
    fun monsterZeroUltraNoLongerScansAsZeroKcal() = runTest {
        val branded = UUID.randomUUID()
        database.blockingTransaction {
            FoodsTable.insert {
                it[id] = branded
                it[sourceKind] = "usda_branded"
                it[sourceId] = "2680587"
                it[name] = "Energy Drink"
                it[brand] = "Monster"
                it[barcode] = "00070847012474"
                it[kcal100g] = 0f
                it[protein100g] = 0f
                it[carbs100g] = 0.83f
                it[fat100g] = 0f
                it[portions] = """[{"label": "8.0 fl oz (240 ml)", "grams": null}]"""
            }
            TransactionManager.current().exec(
                """
                INSERT INTO off_foods (id, barcode, name, brand, kcal_100g, protein_100g, carbs_100g, fat_100g,
                                       portions, source_version)
                VALUES ('${UUID.randomUUID()}', '00070847012474', 'Zero Ultra', 'Monster Energy', 2, 0, 1.268, 0,
                        '[{"label": "1 Can (16 fl oz)", "grams": 473.176}]', '2026-10-26')
                """.trimIndent(),
            )
        }

        // With the export on, its copy answers: the Branded 0 is in doubt and this one is not.
        val withExport = assertNotNull(repo(enabled = true).findByBarcode("070847012474"))
        assertEquals("Zero Ultra", withExport.name)
        assertEquals(2.0, withExport.per100g.kcal)

        // Without it, the Branded row answers with its estimate and a serving the app can log.
        val brandedOnly = assertNotNull(repo(enabled = false).findByBarcode("070847012474"))
        assertEquals(branded, brandedOnly.id)
        assertEquals(2.1, brandedOnly.per100g.kcal)
        val serving = brandedOnly.portions.single()
        assertEquals(240.0, serving.grams)
        assertEquals(5L, Math.round(brandedOnly.per100g.kcal!! * serving.grams!! / 100))

        // The stored row is untouched: the guard works on the way out.
        val stored = database.blockingTransaction {
            FoodsTable.selectAll().where { FoodsTable.id eq branded }.single()[FoodsTable.kcal100g]
        }
        assertEquals(0f, stored)
    }
}
