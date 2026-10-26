package com.opson.integration.foods

import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.randomUserId
import com.opson.libraries.foods.Portion
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Food search and lookup.** The catalog client against the real catalog:
 * ranked search, a food by id with its portions, a barcode the catalog knows,
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

    private fun randomEan13(): String = "2" + (1..12).map { Random.nextInt(10) }.joinToString("")
}
