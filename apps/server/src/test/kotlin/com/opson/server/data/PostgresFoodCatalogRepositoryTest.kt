package com.opson.server.data

import com.opson.server.db.DatabaseTest
import com.opson.server.db.FoodSearchEventsTable
import com.opson.server.db.FoodsTable
import com.opson.server.domain.BarcodeOutcome
import com.opson.server.domain.CatalogSource
import com.opson.server.domain.ExternalProduct
import com.opson.server.domain.FoodCategory
import com.opson.server.domain.FoodTier
import com.opson.server.domain.MatchTier
import com.opson.server.domain.Nutrients
import com.opson.server.domain.Portion
import com.opson.server.domain.SearchCategory
import com.opson.server.domain.SearchFilter
import com.opson.server.domain.SearchSort
import com.opson.server.domain.SearchSource
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
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

    private fun seed(
        name: String,
        source: String = "usda_sr",
        brand: String? = null,
        popularity: Int = 0,
        barcode: String? = null,
        category: FoodCategory? = null,
    ): UUID {
        val id = UUID.randomUUID()
        database.blockingTransaction {
            FoodsTable.insert {
                it[FoodsTable.id] = id
                it[sourceKind] = source
                it[sourceId] = id.toString()
                it[FoodsTable.name] = name
                it[FoodsTable.brand] = brand
                it[FoodsTable.barcode] = barcode
                it[FoodsTable.category] = category?.wire
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

    // ── Search options (category, source, sort) ─────────────────────────────

    private suspend fun names(query: String, filter: SearchFilter) =
        repo.search(query, 10, record = false, filter = filter).map { it.food.name }

    @Test
    fun noOptionsIsTheStatementAndTheResults01Gets() = runTest {
        seed("Chicken, roasted")
        seed("Chicken sandwich, fast food", category = FoodCategory.Restaurant)
        seed("Chicken Noodle Soup", source = "usda_branded", barcode = "00051000012616")
        assertSame(PostgresFoodCatalogRepository.SEARCH_SQL, PostgresFoodCatalogRepository.searchSql(SearchFilter.None))
        assertSame(
            PostgresFoodCatalogRepository.SEARCH_SQL,
            PostgresFoodCatalogRepository.searchSql(SearchFilter(sort = SearchSort.Name)),
            "sorting by name reorders in Kotlin and leaves the statement alone",
        )
        assertFalse("category" in PostgresFoodCatalogRepository.SEARCH_SQL)
        assertEquals(names("chicken"), names("chicken", SearchFilter.None))
    }

    @Test
    fun aCategoryKeepsOnlyItsFoods() = runTest {
        seed("Chicken, roasted")
        seed("Chicken sandwich, fast food", category = FoodCategory.Restaurant)
        seed("CHICK-FIL-A, chicken sandwich", source = "usda_sr", category = FoodCategory.Restaurant)
        seed("Chicken broth drink", source = "usda_branded", barcode = "00051000012617", category = FoodCategory.Beverage)

        assertEquals(
            setOf("Chicken sandwich, fast food", "CHICK-FIL-A, chicken sandwich"),
            names("chicken", SearchFilter(category = SearchCategory.Restaurant)).toSet(),
        )
        assertEquals(listOf("Chicken broth drink"), names("chicken", SearchFilter(category = SearchCategory.Beverage)))
        val typo = names("chiken sandwich", SearchFilter(category = SearchCategory.Restaurant))
        assertTrue(typo.all { "sandwich" in it.lowercase() && it != "Chicken broth drink" }, "the typo fallback filters too: $typo")
    }

    @Test
    fun commonIsTiersOneAndTwo() = runTest {
        seed("Peanut butter, smooth")
        seed("Peanut Butter Crunchy", source = "usda_branded", barcode = "00051500255193")
        seed("Peanut butter, organic", source = "nutritionix")
        seed("Peanut butter, bio", source = "off")
        assertEquals(
            setOf("Peanut butter, smooth", "Peanut Butter Crunchy"),
            names("peanut butter", SearchFilter(category = SearchCategory.Common)).toSet(),
        )
    }

    @Test
    fun theSourceFilterKeepsUsdaOrOpenFoodFacts() = runTest {
        seed("Hazelnut spread", source = "usda_sr")
        seed("Hazelnut spread, chocolate", source = "usda_branded", barcode = "00009800895007")
        seed("Hazelnut spread, bio", source = "off")
        seed("Hazelnut spread, licensed", source = "nutritionix")
        assertEquals(
            setOf("Hazelnut spread", "Hazelnut spread, chocolate"),
            names("hazelnut spread", SearchFilter(source = SearchSource.Usda)).toSet(),
        )
        assertEquals(listOf("Hazelnut spread, bio"), names("hazelnut spread", SearchFilter(source = SearchSource.OpenFoodFacts)))
    }

    @Test
    fun sortByNameOrdersTheBestMatchesAToZ() = runTest {
        seed("Rice, white, cooked")
        seed("Rice, brown, cooked")
        seed("Arroz con pollo, rice")
        repeat(4) { seed("Zucchini $it") }
        val best = names("rice", SearchFilter.None)
        val byName = names("rice", SearchFilter(sort = SearchSort.Name))
        assertEquals(best.toSet(), byName.toSet(), "the same matches, reordered")
        assertEquals(listOf("Arroz con pollo, rice", "Rice, brown, cooked", "Rice, white, cooked"), byName)
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
        val product = ExternalProduct(
            barcode = "3017620422003", name = "Hazelnut spread", brand = "Acme",
            per100g = Nutrients(kcal = 539.0, fat = 30.9), micros = mapOf("sodium_mg" to 42.8),
            portions = listOf(Portion("15 g", 15.0)),
        )
        val first = repo.upsertExternalProduct(CatalogSource.OpenFoodFacts, product)
        val second = repo.upsertExternalProduct(CatalogSource.OpenFoodFacts, product.copy(name = "Hazelnut cocoa spread"))

        assertEquals(first.id, second.id)
        assertEquals("Hazelnut cocoa spread", second.name)
        assertEquals(CatalogSource.OpenFoodFacts, second.source)
        assertEquals(FoodTier.Community, second.tier)
        assertEquals(false, second.verified)
        assertEquals(second, repo.findByBarcode("3017620422003"))
        repo.recordBarcodeLookup("3017620422003", BarcodeOutcome.OpenFoodFacts)
    }

    // ── Branded foods and source tiers ──────────────────────────────────────

    @Test
    fun tierAndVerifiedFollowTheSource() = runTest {
        val generic = assertNotNull(repo.findById(seed("Banana, raw", source = "usda_fndds")))
        val branded = assertNotNull(repo.findById(seed("Banana Chips", source = "usda_branded", barcode = "00011110852714")))
        val community = assertNotNull(repo.findById(seed("Banana chips", source = "off", barcode = "3017620422003")))
        assertEquals(listOf(FoodTier.Reference, FoodTier.Label, FoodTier.Community), listOf(generic, branded, community).map { it.tier })
        assertEquals(listOf(true, true, false), listOf(generic, branded, community).map { it.verified })
    }

    @Test
    fun anUpsertThatMovesARowToAnotherSourceMovesItsTier() = runTest {
        val id = seed("Cola", source = "off", barcode = "00049000028911")
        database.blockingTransaction {
            FoodsTable.update({ FoodsTable.id eq id }) { it[sourceKind] = "usda_branded" }
        }
        val food = assertNotNull(repo.findById(id))
        assertEquals(FoodTier.Label, food.tier)
        assertEquals(true, food.verified)
    }

    @Test
    fun plainGenericQueryKeepsGenericFoodsFirst() = runTest {
        seed("Banana, raw", source = "usda_fndds")
        seed("Bananas", source = "usda_branded", brand = "Dole", barcode = "00033383000016", popularity = 500)
        seed("Banana Chips", source = "usda_branded", brand = "Kroger", barcode = "00011110852714")
        seed("Banana chips", source = "off", brand = "Acme", barcode = "3017620422003", popularity = 900)
        seed("Bread, banana", source = "usda_sr")

        assertEquals(
            listOf("Banana, raw", "Bread, banana", "Bananas", "Banana Chips", "Banana chips"),
            names("banana"),
            "generic before branded; then exact before prefix; label (verified) before community at the same tier",
        )
    }

    @Test
    fun brandedWinsWhenTheQueryNamesTheBrand() = runTest {
        // SR Legacy names a few brands inside generic rows, which the
        // generic-first rule alone would put on top.
        seed("Cereal, Cheerios", source = "usda_sr")
        seed("Cheerios Cereal", source = "usda_branded", brand = "Cheerios", barcode = "00016000275287")
        seed("Honey Nut Cheerios", source = "usda_branded", brand = "General Mills Sales Inc.", barcode = "00016000275270")

        assertEquals("Cheerios Cereal", names("cheerios").first(), "the brand is named")
        assertEquals("Honey Nut Cheerios", names("general mills cheerios").first(), "filler like Sales Inc. need not be typed")
    }

    @Test
    fun aBrandThatIsAlsoAFoodNameDoesNotTakeOverTheFood() = runTest {
        seed("Peanut butter", source = "usda_fndds")
        seed("Smooth Operator Peanut Butter", source = "usda_branded", brand = "Peanut Butter & Co.", barcode = "00898148001019")
        assertEquals(listOf("Peanut butter", "Smooth Operator Peanut Butter"), names("peanut butter"))
    }

    @Test
    fun typoMatchesOnlyFillInWhenDirectMatchesRunShort() = runTest {
        seed("Banana, raw")
        seed("Bananna split") // trigram only
        assertEquals(listOf("Banana, raw", "Bananna split"), names("banana", limit = 10))
        assertEquals(listOf("Banana, raw"), names("banana", limit = 1))
    }

    @Test
    fun aBrandNamedAlongsideTheFoodBeatsTheGenericFood() = runTest {
        seed("Yogurt, Greek, plain, nonfat", source = "usda_sr")
        seed("Greek Yogurt, Plain", source = "usda_branded", brand = "Chobani", barcode = "00818290010490")
        seed("Greek Yogurt, Honey", source = "usda_branded", brand = "The Greek Gods", barcode = "00694649000019")

        assertEquals("Greek Yogurt, Plain", names("chobani greek yogurt").first())
        assertEquals(
            listOf("Yogurt, Greek, plain, nonfat", "Greek Yogurt, Plain", "Greek Yogurt, Honey"),
            names("greek yogurt"),
            "half a brand (Greek, without Gods) is not naming it, so the generic food leads",
        )
    }

    @Test
    fun aBrandMatchNeverBeatsANameMatch() = runTest {
        // Found in the app: "brown rice" offered this product of the brand "Rice Browns" first.
        seed("Crispy Sushi Rice Patties", source = "usda_branded", brand = "Rice Browns", barcode = "00850000000017")
        seed("Brown Rice Cakes", source = "usda_branded", brand = "Lundberg", barcode = "00073416000506")
        seed("Snacks, rice cracker brown rice, plain", source = "usda_sr")
        seed("Rice, brown, cooked, no added fat", source = "usda_fndds")

        assertEquals(
            listOf(
                "Rice, brown, cooked, no added fat",
                "Snacks, rice cracker brown rice, plain",
                "Brown Rice Cakes",
                "Crispy Sushi Rice Patties",
            ),
            names("brown rice"),
            "the head match first, the generic full-text match next, then Branded by name, the brand-only match last",
        )
    }

    @Test
    fun aBrandMustBeNamedInOrder() = runTest {
        seed("Jasmine Green Tea", source = "usda_branded", brand = "Teas' Tea", barcode = "00835003000010")
        seed("Tea, hot, leaf, green", source = "usda_fndds")
        assertEquals("Tea, hot, leaf, green", names("green tea").first(), "\"green tea\" does not name Teas' Tea")
        assertEquals("Jasmine Green Tea", names("teas tea").first())
    }

    @Test
    fun aShortQueryIsAWordBeingTypedNotABrand() = runTest {
        seed("Skinny Sticks", source = "usda_branded", brand = "Avo's", barcode = "00850000000024")
        seed("Avocado, raw", source = "usda_fndds")
        assertEquals(listOf("Avocado, raw", "Skinny Sticks"), names("avo"))
    }

    @Test
    fun headMatchesLookPastCategoriesAndWordOrder() = runTest {
        seed("Salmon, sockeye, canned, total can contents", source = "usda_sr")
        seed("Salmon salad", source = "usda_fndds")
        seed("Fish, salmon, raw", source = "usda_sr")
        assertEquals(MatchTier.Head, repo.search("salmon", 10, record = false).single { it.food.name == "Fish, salmon, raw" }.tier)
        seed("Bread, whole grain white", source = "usda_fndds")
        seed("Bread, white", source = "usda_fndds")
        assertEquals("Bread, white", names("white bread").first(), "\"Bread, white\" is the query's words in another order")
    }

    @Test
    fun restaurantRowsInGenericTablesFollowPlainFoods() = runTest {
        seed("CHICK-FIL-A, chicken sandwich", source = "usda_sr")
        seed("Chicken, ground, raw", source = "usda_fndds")
        assertEquals(listOf("Chicken, ground, raw", "CHICK-FIL-A, chicken sandwich"), names("chick"))
        assertEquals("CHICK-FIL-A, chicken sandwich", names("chick fil a").first(), "unless the query names the restaurant")
    }

    @Test
    fun aTypoFindsTheFoodEvenWhenProductsShareTheTypo() = runTest {
        seed("Organic Hummus, Avacado & Cilantro", source = "usda_branded", brand = "Baba", barcode = "00850000000031")
        seed("Avocado, raw", source = "usda_fndds")
        assertEquals(listOf("Avocado, raw", "Organic Hummus, Avacado & Cilantro"), names("avacado"))
    }

    @Test
    fun verifiedBeatsPopularWithinAMatchTier() = runTest {
        seed("Peanut Butter, Creamy", source = "off", brand = "Acme", barcode = "3017620422003", popularity = 100)
        seed("Peanut Butter, Creamy", source = "usda_branded", brand = "Jif", barcode = "00051500255162")
        val results = repo.search("peanut butter creamy", 10, record = false).map { it.food }
        assertEquals(listOf(CatalogSource.UsdaBranded, CatalogSource.OpenFoodFacts), results.map { it.source })
    }

    @Test
    fun barcodeMatchesAnyFormOfTheCodeAndPrefersTheBetterTier() = runTest {
        seed("Coke (Open Food Facts)", source = "off", barcode = "0049000028911")
        seed("Coca-Cola Classic", source = "usda_branded", barcode = "00049000028911")

        listOf("049000028911", "0049000028911", "00049000028911").forEach { scanned ->
            assertEquals("Coca-Cola Classic", repo.findByBarcode(scanned)?.name, scanned)
        }
        assertNull(repo.findByBarcode("049000028912"))
    }

    @Test
    fun barcodeLookupExpandsUpcE() = runTest {
        seed("Gum", source = "usda_branded", barcode = "00042100005264")
        assertEquals("Gum", repo.findByBarcode("04252614")?.name)
    }

    @Test
    fun portionsWithoutGramsDecode() = runTest {
        val id = seed("Cola", source = "usda_branded", barcode = "00049000028911")
        database.blockingTransaction {
            FoodsTable.update({ FoodsTable.id eq id }) {
                it[portions] = """[{"label": "1 can (355 ml)", "grams": null}, {"label": "1 bottle"}]"""
            }
        }
        assertEquals(
            listOf(Portion("1 can (355 ml)", null), Portion("1 bottle", null)),
            assertNotNull(repo.findById(id)).portions,
        )
    }
}
