package com.opson.libraries.foods.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.FoodReport
import com.opson.libraries.foods.FoodReportReason
import com.opson.libraries.foods.FoodReportResult
import com.opson.libraries.foods.SuggestedFoodValues
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

@OptIn(InternalNetworkingApi::class, ExperimentalEncodingApi::class)
class HttpFoodReportsTest : CoroutineTest() {

    private class FakeNetworkClient(handler: MockRequestHandler) : NetworkClient {
        private val http = HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(HttpTimeout) { requestTimeoutMillis = 30_000 }
            // Strict, as a debug build decodes: a response field the DTOs leave out fails here.
            install(ContentNegotiation) { json(Json { explicitNulls = false }) }
        }
        override val client: HttpClient get() = http
        override val authenticatedClient: HttpClient get() = http
        override suspend fun awaitAuthReady() = Unit
    }

    private val report = FoodReport(
        id = "4f1c0c4e-0000-0000-0000-000000000001",
        foodId = "0f0f0f0f-0000-0000-0000-000000000001",
        reason = FoodReportReason.WrongCalories,
        suggested = SuggestedFoodValues(basisLabel = "1 can", basisGrams = 473.0, kcal = 10.0, servingLabel = "  "),
        note = "  The can says 10  ",
        photo = byteArrayOf(1, 2, 3),
    )

    private fun reports(status: HttpStatusCode, body: String = "{}", seen: MutableList<String>? = null) = HttpFoodReports(
        FakeNetworkClient { request ->
            seen?.add(request.url.encodedPath)
            seen?.add((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )

    @Test
    fun sendsTheReportToTheFoodsRoute() = runUnitTest {
        val seen = mutableListOf<String>()
        val result = reports(HttpStatusCode.Created, """{"reportId":"${report.id}","status":"open"}""", seen).send(report)

        assertEquals(FoodReportResult.Sent(applied = false), result)
        assertEquals("/v1/foods/${report.foodId}/reports", seen[0])
        val body = Json.parseToJsonElement(seen[1]).jsonObject
        assertEquals("wrong_calories", body.getValue("reason").jsonPrimitive.content)
        assertEquals("The can says 10", body.getValue("note").jsonPrimitive.content)
        assertEquals(Base64.encode(byteArrayOf(1, 2, 3)), body.getValue("photo").jsonPrimitive.content)
        val suggested = body.getValue("suggested").jsonObject
        assertEquals(473.0, suggested.getValue("basisGrams").jsonPrimitive.content.toDouble())
        assertFalse("servingLabel" in suggested, "a blank serving is left out")
    }

    @Test
    fun aReportAppliedAtOnceSaysSo() = runUnitTest {
        val result = reports(HttpStatusCode.Created, """{"reportId":"x","status":"accepted"}""").send(report)
        assertEquals(FoodReportResult.Sent(applied = true), result)
    }

    @Test
    fun eachRefusalHasItsOwnAnswer() = runUnitTest {
        assertEquals(FoodReportResult.TooMany, reports(HttpStatusCode.TooManyRequests).send(report))
        assertEquals(FoodReportResult.Gone, reports(HttpStatusCode.NotFound).send(report))
        assertEquals(FoodReportResult.Failed(offline = false), reports(HttpStatusCode.BadRequest).send(report))
        assertIs<FoodReportResult.Failed>(reports(HttpStatusCode.InternalServerError).send(report))
    }
}
