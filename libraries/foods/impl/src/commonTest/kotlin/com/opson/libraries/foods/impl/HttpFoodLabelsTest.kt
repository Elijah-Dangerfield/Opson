package com.opson.libraries.foods.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.CarbBasis
import com.opson.libraries.foods.FoodSubmission
import com.opson.libraries.foods.LabelCheckCode
import com.opson.libraries.foods.LabelFacts
import com.opson.libraries.foods.LabelNutrient
import com.opson.libraries.foods.LabelParse
import com.opson.libraries.foods.ServingUnit
import com.opson.libraries.foods.SubmissionOrigin
import com.opson.libraries.foods.SubmitResult
import com.opson.libraries.networking.InternalNetworkingApi
import com.opson.libraries.networking.NetworkClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(InternalNetworkingApi::class, ExperimentalEncodingApi::class)
class HttpFoodLabelsTest : CoroutineTest() {

    private class FakeNetworkClient(handler: MockRequestHandler) : NetworkClient {
        private val http = HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(HttpTimeout) { requestTimeoutMillis = 30_000 }
            // Strict, as a debug build decodes: a response field the DTOs leave out fails here.
            install(ContentNegotiation) { json(Json) }
        }
        override val client: HttpClient get() = http
        override val authenticatedClient: HttpClient get() = http
        override suspend fun awaitAuthReady() = Unit
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val front = byteArrayOf(1, 2, 3)
    private val label = byteArrayOf(4, 5, 6)

    private fun labels(status: HttpStatusCode, body: String = "{}", seen: MutableList<String>? = null) = HttpFoodLabels(
        FakeNetworkClient { request ->
            seen?.add(request.url.encodedPath)
            seen?.add((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )

    @Test
    fun aReadingArrivesInTheAppsTermsAndUnknownKeysAreDropped() = runUnitTest {
        val seen = mutableListOf<String>()
        val body = """
            {"parseId":"p1","model":"claude-fable-5-1","readable":true,
             "food":{"name":"Granola bar","brand":null,"barcode":null,"servingSize":40.0,"servingUnit":"g","servingLabel":null,
                     "servingsPerContainer":null,"basis":"us","perServing":{"energy_kcal":190.0,"protein_g":4.0,"glitter_g":2.0},
                     "per100":{},"acknowledged":[]},
             "confidence":{"name":0.9},
             "checks":[{"code":"energy_macros_mismatch","fields":["energy_kcal"],"expected":234.0,"actual":270.0},{"code":"from_the_future"}]}
        """.trimIndent()

        val read = assertIs<LabelParse.Read>(labels(HttpStatusCode.OK, body, seen).parse(front, label))

        assertEquals("p1", read.parseId)
        assertEquals(mapOf(LabelNutrient.Energy to 190.0, LabelNutrient.Protein to 4.0), read.facts.perServing)
        assertEquals(ServingUnit.Gram, read.facts.servingUnit)
        assertEquals(CarbBasis.Us, read.facts.basis)
        assertEquals(listOf(LabelCheckCode.EnergyMacrosMismatch), read.checks.map { it.code })
        assertEquals(HttpFoodLabels.PARSE_PATH, seen[0])
        val sent = json.decodeFromString(LabelParseRequest.serializer(), seen[1])
        assertEquals(Base64.encode(front), sent.front)
        assertEquals(Base64.encode(label), sent.label)
    }

    @Test
    fun parseStatusesBecomeCalmOutcomes() = runUnitTest {
        assertEquals(LabelParse.Unavailable, labels(HttpStatusCode.ServiceUnavailable).parse(front, label))
        assertEquals(LabelParse.Unavailable, labels(HttpStatusCode.NotFound).parse(front, label))
        assertEquals(LabelParse.TooMany, labels(HttpStatusCode.TooManyRequests).parse(front, label))
        assertEquals(LabelParse.Failed(offline = false), labels(HttpStatusCode.BadGateway).parse(front, label))
    }

    @Test
    fun aSubmissionSendsTheFoodTheChecksKeptAndBothPhotos() = runUnitTest {
        val seen = mutableListOf<String>()
        val facts = LabelFacts(
            name = "Granola bar",
            servingSize = 40.0,
            servingUnit = ServingUnit.Gram,
            perServing = mapOf(LabelNutrient.Energy to 270.0),
        )
        val result = labels(
            HttpStatusCode.Created,
            """{"submissionId":"s1","foodId":"f1","status":"pending","checks":[{"code":"energy_macros_mismatch","fields":["energy_kcal"]}]}""",
            seen,
        ).submit(
            FoodSubmission(
                id = "s1",
                parseId = "p1",
                origin = SubmissionOrigin.Search,
                barcode = null,
                front = front,
                label = label,
                facts = facts,
                acknowledged = listOf(LabelCheckCode.EnergyMacrosMismatch),
                customFoodId = "c1",
            ),
        )

        assertEquals(SubmitResult.Submitted("s1", "f1"), result)
        val sent = json.decodeFromString(FoodSubmissionRequest.serializer(), seen[1])
        assertEquals("search", sent.origin)
        assertEquals("c1", sent.customFoodId, "the person's own copy, so it can be marked unverified")
        assertEquals("p1", sent.parseId)
        assertEquals(listOf("energy_macros_mismatch"), sent.food.acknowledged)
        assertEquals(mapOf("energy_kcal" to 270.0), sent.food.perServing)
        assertEquals(Base64.encode(label), sent.label)
    }

    @Test
    fun aRepeatedSubmissionIsStillSubmittedAndFailuresAreValues() = runUnitTest {
        val submission = FoodSubmission("s1", null, SubmissionOrigin.Barcode, "012345678905", front, label, LabelFacts(name = "x"), emptyList())
        assertEquals(
            SubmitResult.Submitted("s1", "f1"),
            labels(HttpStatusCode.OK, """{"submissionId":"s1","foodId":"f1"}""").submit(submission),
        )
        assertEquals(SubmitResult.TooMany, labels(HttpStatusCode.TooManyRequests).submit(submission))
        assertEquals(SubmitResult.Failed(offline = false), labels(HttpStatusCode.BadRequest).submit(submission))
    }
}
