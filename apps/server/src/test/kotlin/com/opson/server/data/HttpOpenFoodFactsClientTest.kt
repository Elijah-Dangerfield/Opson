package com.opson.server.data

import com.opson.server.domain.OpenFoodFactsResult
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
        val product = assertIs<OpenFoodFactsResult.Found>(result).product
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
    fun productWithoutANameIsNotUsable() {
        val body = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"status":1,"product":{"nutriments":{}}}""",
        ) as kotlinx.serialization.json.JsonObject
        assertNull(HttpOpenFoodFactsClient.map(code, body))
    }

    @Test
    fun notFoundOn404OrStatusZero() = runTest {
        val notFound = HttpOpenFoodFactsClient(MockEngine { respondError(HttpStatusCode.NotFound) })
        assertEquals(OpenFoodFactsResult.NotFound, notFound.product(code))

        val statusZero = HttpOpenFoodFactsClient(
            MockEngine { respond("""{"status":0,"status_verbose":"product not found"}""", HttpStatusCode.OK, json) },
        )
        assertEquals(OpenFoodFactsResult.NotFound, statusZero.product(code))
    }

    @Test
    fun serverErrorIsUnavailable() = runTest {
        val client = HttpOpenFoodFactsClient(MockEngine { respondError(HttpStatusCode.BadGateway) })
        assertIs<OpenFoodFactsResult.Unavailable>(client.product(code))
    }

    @Test
    fun slowResponseTimesOutAsUnavailable() = runBlocking<Unit> {
        // Real time, not runTest's virtual clock: the timeout and the delay must race for real.
        val engine = MockEngine {
            delay(2_000)
            respond(found, HttpStatusCode.OK, json)
        }
        val client = HttpOpenFoodFactsClient(engine, timeoutMs = 50)
        assertIs<OpenFoodFactsResult.Unavailable>(client.product(code))
    }
}
