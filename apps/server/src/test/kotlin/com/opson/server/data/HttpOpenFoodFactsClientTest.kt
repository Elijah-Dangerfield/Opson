package com.opson.server.data

import com.opson.server.domain.FoodSourceResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class HttpOpenFoodFactsClientTest {

    private val code = "3017620422003"
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private val found = """
        {"code":"$code","status":1,"product":{
          "product_name":"Hazelnut spread","brands":"Acme, Acme Group",
          "serving_size":"15 g","serving_quantity":"15",
          "nutriments":{"energy-kcal_100g":539,"proteins_100g":6.3,"carbohydrates_100g":57.5,
            "fat_100g":30.9,"fiber_100g":"3.4","sodium_100g":0.0428,"sugars_100g":56.3,
            "saturated-fat_100g":10.6,"vitamin-d_100g":0.0000025}}}
    """.trimIndent()

    @Test
    fun sendsADescriptiveUserAgentAndMapsTheProduct() = runTest {
        var userAgent: String? = null
        var path: String? = null
        val engine = MockEngine { req ->
            userAgent = req.headers[HttpHeaders.UserAgent]
            path = req.url.encodedPath
            respond(found, HttpStatusCode.OK, json)
        }
        val result = HttpOpenFoodFactsClient(engine).product(code)

        assertEquals("Opson/0.1 (food logger)", userAgent)
        assertEquals("/api/v2/product/$code.json", path)
        val product = assertIs<FoodSourceResult.Found>(result).product
        assertEquals("Hazelnut spread", product.name)
        assertEquals("Acme", product.brand)
        assertEquals(539.0, product.per100g.kcal)
        assertEquals(3.4, product.per100g.fiber)
        assertEquals(42.8, product.micros["sodium_mg"])
        assertEquals(2.5, product.micros["vitamin_d_ug"])
        assertEquals(15.0, product.portions.single().grams)
        assertEquals("15 g", product.portions.single().label)
    }

    @Test
    fun kilojoulesAreConvertedWhenKcalIsMissing() {
        val body = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"status":1,"product":{"product_name":"Oats","nutriments":{"energy_100g":1569}}}""",
        ) as kotlinx.serialization.json.JsonObject
        assertEquals(375.0, HttpOpenFoodFactsClient.map(code, body)?.per100g?.kcal)
    }

    @Test
    fun kilojoulesUnderTheirOwnKeyAreConvertedToo() {
        val body = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"status":1,"product":{"product_name":"Oats","nutriments":{"energy-kj_100g":1569,"proteins_100g":13}}}""",
        ) as kotlinx.serialization.json.JsonObject
        assertEquals(375.0, HttpOpenFoodFactsClient.map(code, body)?.per100g?.kcal)
    }

    /** No energy at all is unknown, not 0: the catalog's energy guard works it out from the macros. */
    @Test
    fun noEnergyIsUnknown() {
        val body = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"status":1,"product":{"product_name":"Oats","nutriments":{"proteins_100g":13,"carbohydrates_100g":60,"fat_100g":7}}}""",
        ) as kotlinx.serialization.json.JsonObject
        assertNull(HttpOpenFoodFactsClient.map(code, body)?.per100g?.kcal)
    }

    /**
     * The Monster bug's product as Open Food Facts answered it on 2026-10-29
     * (`resources/off/monster_zero_ultra_0070847012474.json`, data from Open Food
     * Facts under the ODbL): energy in kcal and kJ per 100 ml, a 473 ml can.
     * This copy was always right; the 0 came from USDA Branded (FoodEnergyTest).
     */
    @Test
    fun monsterZeroUltraReadsAsTwoKcalPer100AndACan() {
        val text = checkNotNull(javaClass.getResource("/off/monster_zero_ultra_0070847012474.json")).readText()
        val body = kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject
        val product = checkNotNull(HttpOpenFoodFactsClient.map("0070847012474", body))
        assertEquals("Monster Energy", product.brand)
        assertEquals(2.0, product.per100g.kcal)
        assertEquals(0.0, product.per100g.protein)
        assertEquals(0.0, product.per100g.fat)
        assertEquals(473.176, product.portions.single().grams)
        assertEquals("1 Can (16 fl oz)", product.portions.single().label)
    }

    /** A nutrient the label gives as 0 is 0; one it leaves out is not there at all, never 0. */
    @Test
    fun aListedZeroStaysZero_andAMissingNutrientIsAbsent() {
        val body = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"status":1,"product":{"product_name":"Water","nutriments":{"sodium_100g":0,"energy-kcal_100g":0}}}""",
        ) as kotlinx.serialization.json.JsonObject
        val micros = HttpOpenFoodFactsClient.map(code, body)?.micros.orEmpty()
        assertEquals(0.0, micros["sodium_mg"])
        assertEquals(false, "vitamin_d_ug" in micros)
    }

    @Test
    fun productWithoutANameIsNotUsable() {
        val body = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"status":1,"product":{"nutriments":{}}}""",
        ) as kotlinx.serialization.json.JsonObject
        assertNull(HttpOpenFoodFactsClient.map(code, body))
    }

    @Test
    fun notFoundOn404OrStatusZero() = runTest {
        val notFound = HttpOpenFoodFactsClient(MockEngine { respondError(HttpStatusCode.NotFound) })
        assertEquals(FoodSourceResult.NotFound, notFound.product(code))

        val statusZero = HttpOpenFoodFactsClient(
            MockEngine { respond("""{"status":0,"status_verbose":"product not found"}""", HttpStatusCode.OK, json) },
        )
        assertEquals(FoodSourceResult.NotFound, statusZero.product(code))
    }

    @Test
    fun serverErrorIsUnavailable() = runTest {
        val client = HttpOpenFoodFactsClient(MockEngine { respondError(HttpStatusCode.BadGateway) })
        assertIs<FoodSourceResult.Unavailable>(client.product(code))
    }

    @Test
    fun slowResponseTimesOutAsUnavailable() = runBlocking<Unit> {
        // Real time, not runTest's virtual clock: the timeout and the delay must race for real.
        val engine = MockEngine {
            delay(2_000)
            respond(found, HttpStatusCode.OK, json)
        }
        val client = HttpOpenFoodFactsClient(engine, timeoutMs = 50)
        assertIs<FoodSourceResult.Unavailable>(client.product(code))
    }
}
