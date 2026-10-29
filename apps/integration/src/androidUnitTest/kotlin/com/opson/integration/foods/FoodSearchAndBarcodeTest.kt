package com.opson.integration.foods

import com.opson.integration.helpers.FakeUpstreams.OffProduct
import com.opson.integration.helpers.FaultProxy.Fault
import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.randomUserId
import com.opson.libraries.foods.FoodSearchFilter
import com.opson.libraries.foods.Portion
import com.opson.libraries.foods.SearchCategory
import com.opson.libraries.foods.SearchOptions
import com.opson.libraries.foods.SearchOrder
import com.opson.libraries.foods.SearchSource
import io.ktor.http.HttpStatusCode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Food search and lookup.** The catalog client against the real catalog:
 * ranked search, a food by id with its portions, a barcode the catalog knows
 * in whatever form the scanner reads it, a barcode only Open Food Facts knows,
 * and a barcode nobody knows, whose miss report reaches the server without
 * anything that names the person who scanned it.
 *
 * The catalog is shared by every test in the JVM, so each test's foods carry a
 * random word no other food has.
 */
class FoodSearchAndBarcodeTest : IntegrationTest() {

    private val word = (1..10).map { 'a' + Random.nextInt(26) }.joinToString("")

    @Test
    fun search_ranksTheExactNameFirst_andGenericFoodsBeforeBranded() = integration {
        val exact = server.seedFood("$word apple", kcalPer100g = 52.0)
        val pie = server.seedFood("$word apple pie", kcalPer100g = 237.0)
        val juice = server.seedFood("$word apple juice", kcalPer100g = 46.0, source = "usda_branded")
        val phone = client()

        val result = phone.foods.search("$word apple").getOrThrow()

        assertFalse(result.fromCache)
        val ids = result.foods.map { it.id }
        assertEquals(exact, ids.first(), "the exact name leads: $ids")
        assertTrue(ids.indexOf(pie) in 0 until ids.indexOf(juice), "a generic food outranks a branded one: $ids")
    }

    @Test
    fun detail_returnsTheFoodWithItsPortions() = integration {
        val id = server.seedFood("$word yogurt", kcalPer100g = 61.0, portionsJson = """[{"label":"1 cup","grams":245}]""")
        val phone = client()

        val food = phone.foods.detail(id).getOrThrow()

        assertEquals("$word yogurt", food.name)
        assertEquals(61.0, food.per100g.kcal)
        assertEquals(listOf(Portion("1 cup", 245.0)), food.portions)
    }

    @Test
    fun barcodeHit_comesFromTheCatalog_andIsKeptForOffline() = integration {
        val code = randomEan13()
        val id = server.seedFood("$word granola", kcalPer100g = 471.0, barcode = code, source = "usda_branded")
        val phone = client()

        val food = assertNotNull(phone.foods.barcode(code).getOrThrow())

        assertEquals(id, food.id)
        assertEquals(emptyList(), server.upstreams.openFoodFactsLookups, "a catalog hit never asks Open Food Facts")
        assertNotNull(phone.database.foodCacheDao().get(id), "kept on the device so the amount sheet works offline")
    }

    /**
     * A code in two databases: USDA Branded's GTIN-14 and an Open Food Facts answer cached under
     * the EAN-13. Asked for every match, the phone gets both over the wire, the server's answer
     * first; asked the old way, the same single food it always got. Only the answer is kept.
     */
    @Test
    fun barcodeInTwoDatabases_listsEveryMatch_withTheAnswerFirst() = integration {
        val code = randomEan13(prefix = '0')
        val branded = server.seedFood("$word protein bar", kcalPer100g = 380.0, barcode = "0$code", source = "usda_branded")
        val off = server.seedFood("$word protein bar", kcalPer100g = 360.0, barcode = code, source = "off")
        val phone = client()

        val matches = phone.foods.barcodeMatches(code.drop(1)).getOrThrow()

        assertEquals(listOf(branded, off), matches.map { it.food.id })
        assertEquals(listOf(true, false), matches.map { it.food.verified })
        assertTrue(matches.none { it.corrected || it.energyCheck.isNotEmpty() })
        assertEquals(branded, phone.foods.barcode(code).getOrThrow()?.id, "the single answer is the first match")
        assertNotNull(phone.database.foodCacheDao().get(branded))
        assertNull(phone.database.foodCacheDao().get(off), "only the answer is kept on the device")
        assertEquals(emptyList(), server.upstreams.openFoodFactsLookups, "a catalog hit never asks Open Food Facts")
    }

    /**
     * The importers store a Branded or Open Food Facts code as its 14-digit GTIN. A US scanner
     * reads the 12 printed digits, a European one the 13-digit EAN, and a small US pack carries
     * the 8-digit UPC-E. All four are one product.
     */
    @Test
    fun barcodeStoredAsGtin14_isFoundByEveryFormOfItsCode() = integration {
        val upcE = randomUpcE()
        val upcA = upcEToUpcA(upcE)
        val id = server.seedFood("$word crackers", kcalPer100g = 430.0, barcode = upcA.padStart(14, '0'), source = "usda_branded")
        val phone = client()

        for (scanned in listOf(upcA, "0$upcA", "00$upcA", upcE)) {
            assertEquals(id, phone.foods.barcode(scanned).getOrThrow()?.id, "a scan of $scanned")
        }
        assertEquals(emptyList(), server.upstreams.openFoodFactsLookups, "every form was a catalog hit")
    }

    @Test
    fun barcodeOnlyOpenFoodFactsKnows_isServed_andTheNextScanInAnotherFormIsACatalogHit() = integration {
        val code = randomEan13(prefix = '0')
        server.upstreams.openFoodFactsProducts[code] = OffProduct("$word oat bar", brand = "Corner Mill", kcalPer100g = 402.0)

        val food = assertNotNull(client().foods.barcode(code).getOrThrow(), "Open Food Facts knows it")
        assertEquals("$word oat bar", food.name)
        assertEquals(402.0, food.per100g.kcal)
        assertEquals(listOf(code), server.upstreams.openFoodFactsLookups)

        // Another phone, whose scanner reads the 12-digit UPC-A of the same pack.
        val again = assertNotNull(client().foods.barcode(code.drop(1)).getOrThrow())
        assertEquals(food.id, again.id, "every user gets the same food for the product")
        assertEquals(listOf(code), server.upstreams.openFoodFactsLookups, "the second scan never left the server")
    }

    @Test
    fun barcodeMiss_isNull_andTheMissReportStoresNoUser() = integration {
        val code = randomEan13()
        val user = randomUserId()
        val phone = client(user)

        assertNull(phone.foods.barcode(code).getOrThrow(), "nobody knows it")
        assertEquals(listOf(code), server.upstreams.openFoodFactsLookups, "the outside source was asked first")

        phone.barcodeMisses.report(code)

        val misses = server.query("SELECT * FROM barcode_misses WHERE barcode = ?", code)
        val miss = misses.single()
        assertEquals("android", miss["platform"])
        assertEquals(
            setOf("id", "created_at", "barcode", "gtin", "country", "platform", "app_version"),
            miss.keys,
            "no column could hold who scanned it",
        )
        assertFalse(miss.values.any { it?.toString() == user }, "and no value is the user's id")
    }

    /**
     * Open Food Facts down and the catalog without the code: the server can't say whether anyone
     * knows it, so it answers 503 `barcode_lookup_unavailable`. That is a failure on the phone,
     * not "nobody knows it", so the logger never offers to report a miss for a code that may be
     * fine. A code the phone scanned before still opens from its own cache.
     */
    @Test
    fun barcodeWhenEverySourceIsDown_failsRatherThanMisses_andFallsBackToTheCache() = integration {
        val known = randomEan13()
        server.seedFood("$word muesli", kcalPer100g = 360.0, barcode = known, source = "usda_branded")
        val proxy = faultProxy()
        val phone = client(through = proxy)
        val cached = assertNotNull(phone.foods.barcode(known).getOrThrow())

        server.upstreams.openFoodFactsDown = true
        val unknown = phone.foods.barcode(randomEan13())
        assertTrue(unknown.isFailure, "unavailable, not a miss: $unknown")

        proxy.failAll(
            "/v1/foods/barcode",
            Fault.Status(
                HttpStatusCode.ServiceUnavailable,
                """{"error":{"code":"barcode_lookup_unavailable","message":"Barcode lookup is unavailable right now. Try again."}}""",
            ),
        )
        assertEquals(cached.id, phone.foods.barcode(known).getOrThrow()?.id, "the code scanned before opens from the device")
    }

    /**
     * The Logger's search options: `category`, `source` and `sort` reach the server's filter, and
     * `/search/options` says which sources this server offers. Open Food Facts search is off unless
     * `FOOD_SEARCH_OFF_ENABLED` is set, as here.
     */
    @Test
    fun filtersAndOptions_matchWhatTheServerOffers() = integration {
        val tea = server.seedFood("$word tea", kcalPer100g = 1.0)
        val iced = server.seedFood("$word tea, iced", kcalPer100g = 30.0, source = "usda_branded", category = "beverage")
        val bun = server.seedFood("$word tea bun", kcalPer100g = 320.0)
        val phone = client()

        assertEquals(SearchOptions(openFoodFacts = false), phone.foods.searchOptions().getOrThrow())

        val drinks = phone.foods.search("$word tea", filter = FoodSearchFilter(category = SearchCategory.Beverage)).getOrThrow()
        assertEquals(listOf(iced), drinks.foods.map { it.id }, "beverages only")

        val usda = phone.foods.search("$word tea", filter = FoodSearchFilter(source = SearchSource.Usda)).getOrThrow()
        assertEquals(setOf(tea, iced, bun), usda.foods.map { it.id }.toSet(), "every one of them is USDA's")

        val byName = phone.foods.search("$word tea", filter = FoodSearchFilter(order = SearchOrder.Name)).getOrThrow()
        val names = byName.foods.map { it.name }
        assertEquals(names.sortedBy { it.lowercase() }, names, "A to Z")
    }

    private fun randomEan13(prefix: Char = '2'): String = withCheckDigit(prefix + digits(11))

    /** A number-system-0 UPC-E, its check digit its UPC-A's, as printed on the pack. */
    private fun randomUpcE(): String {
        val body = "0" + digits(5) + Random.nextInt(5, 10)
        return body + upcEToUpcA(body + "0").last()
    }

    /** The UPC-A a UPC-E stands for, when its sixth digit is 5 to 9 (the only kind [randomUpcE] makes). */
    private fun upcEToUpcA(upcE: String): String = withCheckDigit(upcE.substring(0, 6) + "0000" + upcE[6])

    private fun digits(n: Int): String = (1..n).map { Random.nextInt(10) }.joinToString("")

    private fun withCheckDigit(body: String): String {
        val total = body.reversed().withIndex().sumOf { (i, c) -> (c - '0') * if (i % 2 == 0) 3 else 1 }
        return body + (10 - total % 10) % 10
    }
}
