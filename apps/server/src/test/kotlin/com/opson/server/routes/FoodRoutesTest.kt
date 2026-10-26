package com.opson.server.routes

import com.opson.server.domain.BarcodeLookup
import com.opson.server.domain.BarcodeLookupResult
import com.opson.server.domain.CatalogSource
import com.opson.server.domain.DescribeConfidence
import com.opson.server.domain.DescribedItem
import com.opson.server.domain.FoodDescriber
import com.opson.server.domain.Nutrients
import com.opson.server.domain.Portion
import com.opson.server.domain.UserId
import com.opson.server.plugins.ProblemResponse
import com.opson.server.plugins.installAuthentication
import com.opson.server.plugins.installRateLimits
import com.opson.server.plugins.installSerialization
import com.opson.server.plugins.installStatusPages
import com.opson.server.testing.FakeFoodCatalog
import com.opson.server.testing.FakeFoodCatalog.Companion.food
import com.opson.server.testing.TestJwt
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoodRoutesTest {

    private val userId = UserId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
    private val banana = food("bananas, raw", kcal = 89.0, portions = listOf(Portion("1 medium", 118.0)))
    private val bread = food("bread, white", kcal = 266.0)

    private class FakeBarcodeLookup(var result: BarcodeLookupResult) : BarcodeLookup {
        val codes = mutableListOf<String>()
        override suspend fun lookup(barcode: String): BarcodeLookupResult {
            codes += barcode
            return result
        }
    }

    private class FakeDescriber : FoodDescriber {
        val texts = mutableListOf<String>()
        override suspend fun describe(text: String): List<DescribedItem> {
            texts += text
            return listOf(
                DescribedItem(
                    text = "2 eggs", name = "Egg, whole", foodId = UUID.randomUUID(), quantity = 2.0, unit = null,
                    amountText = "2", portionLabel = "1 large", grams = 100.0, nutrients = Nutrients(kcal = 143.0),
                    micros = emptyMap(), confidence = DescribeConfidence.High,
                ),
            )
        }
    }

    private suspend fun withApp(
        catalog: FakeFoodCatalog = FakeFoodCatalog(listOf(banana, bread)),
        lookup: BarcodeLookup = FakeBarcodeLookup(BarcodeLookupResult.NotFound),
        describer: FoodDescriber = FakeDescriber(),
        block: suspend (HttpClient) -> Unit,
    ) {
        testApplication {
            application {
                installSerialization()
                installRateLimits()
                installStatusPages()
                installAuthentication(TestJwt.verification)
                routing { foodRoutes(catalog, lookup, describer) }
            }
            block(createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } })
        }
    }

    private fun HttpRequestBuilder.auth() = header(HttpHeaders.Authorization, TestJwt.bearer(userId))

    @Test
    fun everyRouteRequiresAuth() = runTest {
        withApp { client ->
            assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/foods/search?q=banana").status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/foods/${banana.id}").status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/foods/barcode/12345678").status)
            assertEquals(HttpStatusCode.Unauthorized, client.post("/v1/describe").status)
        }
    }

    @Test
    fun searchReturnsFoodsAndRecordsTheQuery() = runTest {
        val catalog = FakeFoodCatalog(listOf(banana, bread))
        withApp(catalog) { client ->
            val body = client.get("/v1/foods/search?q=%20Banana%20&limit=5") { auth() }.body<FoodSearchResponse>()
            assertEquals("banana", body.query)
            assertEquals(listOf(banana.id.toString()), body.foods.map { it.id })
            assertEquals(89.0, body.foods.single().per100g.kcal)
            assertEquals(listOf(PortionDto("1 medium", 118.0)), body.foods.single().portions)
            assertEquals(listOf("banana" to true), catalog.searches)
        }
    }

    @Test
    fun searchWithoutAQueryIsA400() = runTest {
        withApp { client ->
            val resp = client.get("/v1/foods/search?q=%20") { auth() }
            assertEquals(HttpStatusCode.BadRequest, resp.status)
            assertEquals("invalid_query", resp.body<ProblemResponse>().error.code)
        }
    }

    @Test
    fun foodDetailFoundMissingAndMalformed() = runTest {
        withApp { client ->
            assertEquals(banana.name, client.get("/v1/foods/${banana.id}") { auth() }.body<FoodResponse>().name)

            val missing = client.get("/v1/foods/${UUID.randomUUID()}") { auth() }
            assertEquals(HttpStatusCode.NotFound, missing.status)
            assertEquals("not_found", missing.body<ProblemResponse>().error.code)

            val malformed = client.get("/v1/foods/not-a-uuid") { auth() }
            assertEquals("invalid_id", malformed.body<ProblemResponse>().error.code)
        }
    }

    @Test
    fun barcodeOutcomesMapToStatuses() = runTest {
        val lookup = FakeBarcodeLookup(BarcodeLookupResult.Found(banana))
        withApp(lookup = lookup) { client ->
            val ok = client.get("/v1/foods/barcode/0123456789012") { auth() }
            assertEquals(HttpStatusCode.OK, ok.status)
            assertEquals(banana.id.toString(), ok.body<FoodResponse>().id)

            lookup.result = BarcodeLookupResult.NotFound
            val miss = client.get("/v1/foods/barcode/0123456789012") { auth() }
            assertEquals(HttpStatusCode.NotFound, miss.status)
            assertEquals("not_found", miss.body<ProblemResponse>().error.code)

            lookup.result = BarcodeLookupResult.Unavailable
            val down = client.get("/v1/foods/barcode/0123456789012") { auth() }
            assertEquals(HttpStatusCode.ServiceUnavailable, down.status)
            assertEquals("barcode_lookup_unavailable", down.body<ProblemResponse>().error.code)
        }
    }

    @Test
    fun responsesCarryTierVerifiedAndVolumeOnlyServings() = runTest {
        val cola = food(
            "Coca-Cola Classic",
            source = CatalogSource.UsdaBranded,
            barcode = "00049000028911",
            portions = listOf(Portion("1 Can (355 mL)", null)),
        )
        val scanned = food("Hazelnut spread", source = CatalogSource.OpenFoodFacts, barcode = "3017620422003")
        val lookup = FakeBarcodeLookup(BarcodeLookupResult.Found(cola))
        withApp(FakeFoodCatalog(listOf(banana, cola, scanned)), lookup = lookup) { client ->
            val branded = client.get("/v1/foods/barcode/049000028911") { auth() }
            assertEquals(
                """"portions":[{"label":"1 Can (355 mL)"}],"tier":2,"verified":true}""",
                branded.bodyAsText().substringAfter(""""micros":{"sodium_mg":100.0},"""),
                "a volume-only serving goes out with no grams key at all",
            )

            val generic = client.get("/v1/foods/${banana.id}") { auth() }.body<FoodResponse>()
            assertEquals(1 to true, generic.tier to generic.verified)
            val community = client.get("/v1/foods/${scanned.id}") { auth() }.body<FoodResponse>()
            assertEquals(4 to false, community.tier to community.verified)
            assertEquals("usda_branded", client.get("/v1/foods/${cola.id}") { auth() }.body<FoodResponse>().source)
        }
    }

    @Test
    fun malformedBarcodeNeverReachesTheLookup() = runTest {
        val lookup = FakeBarcodeLookup(BarcodeLookupResult.NotFound)
        withApp(lookup = lookup) { client ->
            for (code in listOf("123", "abcdefghij", "123456789012345")) {
                val resp = client.get("/v1/foods/barcode/$code") { auth() }
                assertEquals("invalid_barcode", resp.body<ProblemResponse>().error.code, code)
            }
            assertTrue(lookup.codes.isEmpty())
        }
    }

    @Test
    fun describeReturnsItems() = runTest {
        val describer = FakeDescriber()
        withApp(describer = describer) { client ->
            val resp = client.post("/v1/describe") {
                auth()
                contentType(ContentType.Application.Json)
                setBody(DescribeRequest("2 eggs"))
            }
            val item = resp.body<DescribeResponse>().items.single()
            assertEquals("high", item.confidence)
            assertEquals(100.0, item.grams)
            assertEquals(143.0, item.kcal)
            assertEquals(listOf("2 eggs"), describer.texts)
        }
    }

    @Test
    fun describeRejectsEmptyAndHugeText() = runTest {
        withApp { client ->
            for (text in listOf("  ", "x".repeat(1_001))) {
                val resp = client.post("/v1/describe") {
                    auth()
                    contentType(ContentType.Application.Json)
                    setBody(DescribeRequest(text))
                }
                assertEquals(HttpStatusCode.BadRequest, resp.status)
                assertEquals("invalid_text", resp.body<ProblemResponse>().error.code)
            }
        }
    }
}
