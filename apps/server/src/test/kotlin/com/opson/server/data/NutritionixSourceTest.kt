package com.opson.server.data

import com.opson.server.config.Env
import com.opson.server.config.NutritionixConfig
import com.opson.server.domain.FoodSourceResult
import com.opson.server.domain.Portion
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NutritionixSourceTest {

    private val config = NutritionixConfig(appId = "app-id", appKey = "app-key")
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val upc = "049000028911"

    /** The shape `GET /v2/search/item?upc=` returns: one food, nutrients per serving. */
    private val granola = """
        {"foods":[{
          "food_name":"Oats & Honey Granola Bar","brand_name":"Nature Valley",
          "serving_qty":2,"serving_unit":"bars","serving_weight_grams":42,
          "nf_calories":190,"nf_total_fat":7,"nf_saturated_fat":1,"nf_cholesterol":0,"nf_sodium":180,
          "nf_total_carbohydrate":29,"nf_dietary_fiber":2,"nf_sugars":11,"nf_protein":4,
          "full_nutrients":[{"attr_id":203,"value":4},{"attr_id":307,"value":180},{"attr_id":269,"value":11}],
          "nix_item_id":"abc123"
        }]}
    """.trimIndent()

    private fun parse(body: String) = Json.parseToJsonElement(body) as JsonObject

    @Test
    fun sendsTheKeysAndTheUpcAndScalesToPer100g() = runTest {
        var request: io.ktor.client.request.HttpRequestData? = null
        val engine = MockEngine {
            request = it
            respond(granola, HttpStatusCode.OK, json)
        }
        val result = NutritionixSource(config, engine).lookup(upc)

        val sent = checkNotNull(request)
        assertEquals("/v2/search/item", sent.url.encodedPath)
        assertEquals(upc, sent.url.parameters["upc"])
        assertEquals("app-id", sent.headers["x-app-id"])
        assertEquals("app-key", sent.headers["x-app-key"])

        val product = assertIs<FoodSourceResult.Found>(result).product
        assertEquals("Oats & Honey Granola Bar", product.name)
        assertEquals("Nature Valley", product.brand)
        assertEquals(452.4, product.per100g.kcal)
        assertEquals(69.05, product.per100g.carbs)
        assertEquals(428.571, product.micros["sodium_mg"])
        assertEquals(26.19, product.micros["sugars_g"]!!, 0.01)
        assertEquals(listOf(Portion("2 bars", 42.0)), product.portions)
    }

    @Test
    fun aServingInMlIsPer100mlWithNoGrams() {
        val body = parse(
            """{"foods":[{"food_name":"Coke","serving_qty":1,"serving_unit":"can","serving_weight_grams":null,
               "nf_metric_qty":355,"nf_metric_uom":"ml","nf_calories":140,"nf_total_carbohydrate":39}]}""",
        )
        val product = checkNotNull(NutritionixSource.map(upc, body))
        assertEquals(39.4, product.per100g.kcal)
        assertEquals(listOf(Portion("1 can (355 ml)", null)), product.portions)
    }

    @Test
    fun noServingSizeMeansNoProduct() {
        assertNull(NutritionixSource.map(upc, parse("""{"foods":[{"food_name":"Mystery","nf_calories":100}]}""")))
        assertNull(NutritionixSource.map(upc, parse("""{"foods":[]}""")))
    }

    @Test
    fun statusesMapToResults() = runTest {
        suspend fun status(code: HttpStatusCode) = NutritionixSource(config, MockEngine { respondError(code) }).lookup(upc)
        assertEquals(FoodSourceResult.NotFound, status(HttpStatusCode.NotFound))
        assertIs<FoodSourceResult.Unavailable>(status(HttpStatusCode.Unauthorized))
        assertIs<FoodSourceResult.Unavailable>(status(HttpStatusCode.TooManyRequests))
        assertIs<FoodSourceResult.Unavailable>(status(HttpStatusCode.BadGateway))
    }

    @Test
    fun cachingFollowsTheFlag() {
        assertEquals(false, NutritionixSource(config).cacheable)
        assertEquals(true, NutritionixSource(config.copy(cacheResults = true)).cacheable)
    }

    @Test
    fun configNeedsBothKeysAndCachingIsOffByDefault() {
        fun env(vararg pairs: Pair<String, String>) = Env(envFilePath = "/nonexistent", realEnv = mapOf(*pairs)::get)
        assertNull(NutritionixConfig.fromEnv(env("NUTRITIONIX_APP_ID" to "id")))
        assertNull(NutritionixConfig.fromEnv(env("NUTRITIONIX_APP_KEY" to "key")))
        val both = checkNotNull(NutritionixConfig.fromEnv(env("NUTRITIONIX_APP_ID" to "id", "NUTRITIONIX_APP_KEY" to "s3cret")))
        assertEquals(false, both.cacheResults)
        val cached = NutritionixConfig.fromEnv(
            env("NUTRITIONIX_APP_ID" to "id", "NUTRITIONIX_APP_KEY" to "key", "NUTRITIONIX_CACHE_RESULTS" to "true"),
        )
        assertEquals(true, cached?.cacheResults)
        assertTrue("s3cret" !in both.toString(), "the key never reaches a log line")
    }
}
