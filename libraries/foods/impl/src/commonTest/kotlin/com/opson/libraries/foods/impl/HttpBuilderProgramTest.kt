package com.opson.libraries.foods.impl

import com.opson.libraries.entitlements.Entitlement
import com.opson.libraries.entitlements.EntitlementRepository
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.BuilderResult
import com.opson.libraries.foods.BuilderStatus
import com.opson.libraries.foods.FoodCheckResult
import com.opson.libraries.foods.JoinBlock
import com.opson.libraries.foods.LabelTextLine
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(InternalNetworkingApi::class)
class HttpBuilderProgramTest : CoroutineTest() {

    private class FakeNetworkClient(handler: MockRequestHandler) : NetworkClient {
        private val http = HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(HttpTimeout) { requestTimeoutMillis = 30_000 }
            install(ContentNegotiation) { json(Json { explicitNulls = false }) }
        }
        override val client: HttpClient get() = http
        override val authenticatedClient: HttpClient get() = http
        override suspend fun awaitAuthReady() = Unit
    }

    private class CountingEntitlements : EntitlementRepository {
        override val entitlements: StateFlow<Set<Entitlement>> = MutableStateFlow(Entitlement.DEFAULTS)
        var refreshed = 0

        override suspend fun refresh(): Result<Unit> {
            refreshed++
            return Result.success(Unit)
        }
    }

    private val entitlements = CountingEntitlements()

    private fun program(status: HttpStatusCode, body: String, seen: MutableList<String>? = null) = HttpBuilderProgram(
        FakeNetworkClient { request ->
            seen?.add("${request.method.value} ${request.url.encodedPath}")
            (request.body as? OutgoingContent.ByteArrayContent)?.let { seen?.add(it.bytes().decodeToString()) }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
        entitlements,
    )

    private val joined = """
        {"status":"active","trust":"builder","joinedAtEpochMs":1800000000000,"proUntilEpochMs":1802592000000,
         "accepted30d":0,"needed":10,"photoRetention":true,"canJoin":false,"termsVersion":"2026-10-29"}
    """.trimIndent()

    @Test
    fun joiningSendsTheAcceptedTermsAndRefreshesWhatTheAccountHolds() = runUnitTest {
        val seen = mutableListOf<String>()
        val builders = program(HttpStatusCode.OK, joined, seen)

        val result = assertIs<BuilderResult.Done>(builders.join(photoRetention = true))

        assertEquals(BuilderStatus.Active, result.standing.status)
        assertEquals(true, builders.isBuilder)
        assertEquals("POST /v1/builders/join", seen[0])
        val body = Json.parseToJsonElement(seen[1]).jsonObject
        assertEquals("true", body.getValue("acceptTerms").jsonPrimitive.content)
        assertEquals(HttpBuilderProgram.TERMS_VERSION, body.getValue("termsVersion").jsonPrimitive.content)
        assertEquals(1, entitlements.refreshed)
    }

    @Test
    fun aRefusedJoinSaysWhy() = runUnitTest {
        val builders = program(HttpStatusCode.Forbidden, """{"error":"too_new","message":"Add a food first."}""")

        assertEquals(BuilderResult.Blocked(JoinBlock.TooNew), builders.join(photoRetention = false))
        assertEquals(0, entitlements.refreshed)
    }

    @Test
    fun aServerFromBeforeTheProgramIsUnavailableAndClearingForgetsTheStanding() = runUnitTest {
        assertEquals(BuilderResult.Unavailable, program(HttpStatusCode.NotFound, "{}").refresh())

        val builders = program(HttpStatusCode.OK, joined)
        builders.refresh()
        builders.clear("someone")
        assertNull(builders.standing.value)
    }

    @Test
    fun aCheckSendsTheLabelTextAndReadsTheVerdict() = runUnitTest {
        val seen = mutableListOf<String>()
        val checks = HttpFoodChecks(
            FakeNetworkClient { request ->
                seen += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                respond(
                    """{"id":"c1","outcome":"unconfirmed","grounded":3,"groundable":4,"missing":["fat_g"]}""",
                    HttpStatusCode.Created,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )

        val result = checks.check("c1", "food-1", "mlkit", listOf(LabelTextLine("Calories 230", 0.9)))

        assertEquals(FoodCheckResult.Unconfirmed(listOf("fat_g")), result)
        val line = Json.parseToJsonElement(seen.single()).jsonObject.getValue("lines").jsonArray.single().jsonObject
        assertEquals("Calories 230", line.getValue("text").jsonPrimitive.content)
    }
}
