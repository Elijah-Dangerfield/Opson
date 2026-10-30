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
import com.opson.libraries.foods.ApplicationStatus
import com.opson.libraries.foods.BuilderApplicationForm
import com.opson.libraries.foods.LabelRegion
import com.opson.libraries.foods.ShoppingFrequency
import com.opson.libraries.opson.AppCache
import com.opson.libraries.opson.AppData
import kotlinx.coroutines.flow.Flow
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
import com.opson.libraries.foods.FoodVerification
import com.opson.libraries.foods.VerifyField
import com.opson.libraries.foods.VerifyProblem
import com.opson.libraries.foods.VerifyValues
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    private class FakeAppCache : AppCache {
        private val state = MutableStateFlow(AppData())
        override val updates: Flow<AppData> = state
        override suspend fun get(): AppData = state.value
        override suspend fun set(value: AppData) {
            state.value = value
        }
        override suspend fun clear() {
            state.value = AppData()
        }
    }

    private val appCache = FakeAppCache()

    /** Answers [status] with [body]; change [body] between calls to stand in for the server moving on. */
    private class Server(var status: HttpStatusCode, var body: String)

    private fun program(status: HttpStatusCode, body: String, seen: MutableList<String>? = null) = program(Server(status, body), seen)

    private fun program(server: Server, seen: MutableList<String>? = null) = HttpBuilderProgram(
        FakeNetworkClient { request ->
            seen?.add("${request.method.value} ${request.url.encodedPath}")
            (request.body as? OutgoingContent.ByteArrayContent)?.let { seen?.add(it.bytes().decodeToString()) }
            respond(server.body, server.status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
        entitlements,
        appCache,
    )

    private val pending = """
        {"status":"none","trust":"anyone","canJoin":false,"joinBlock":"full","applyBlock":"pending",
         "application":{"id":"a1","status":"pending","submittedAtEpochMs":1800000000000}}
    """.trimIndent()

    private val form = BuilderApplicationForm(
        shopping = ShoppingFrequency.Weekly,
        region = LabelRegion.AustraliaNz,
        motivation = "  The muesli aisle here is a mess.  ",
        photoRetention = true,
        via = "more",
    )

    private val joined = """
        {"status":"active","trust":"builder","joinedAtEpochMs":1800000000000,"proUntilEpochMs":1802592000000,
         "accepted30d":0,"needed":10,"photoRetention":true,"canJoin":false,"termsVersion":"2026-10-29"}
    """.trimIndent()

    @Test
    fun applyingSendsTheAnswersAndTheRulesAndReadsThePendingApplication() = runUnitTest {
        val seen = mutableListOf<String>()
        val builders = program(HttpStatusCode.Created, pending, seen)

        val result = assertIs<BuilderResult.Done>(builders.sendApplication(form))

        assertEquals(BuilderStatus.None, result.standing.status)
        assertEquals(ApplicationStatus.Pending, result.standing.application?.status)
        // The precise code wins over the one older apps read.
        assertEquals(JoinBlock.Pending, result.standing.joinBlock)
        assertEquals(false, builders.isBuilder)
        assertEquals("POST /v1/builders/applications", seen[0])
        val body = Json.parseToJsonElement(seen[1]).jsonObject
        assertEquals("true", body.getValue("acceptTerms").jsonPrimitive.content)
        assertEquals(HttpBuilderProgram.TERMS_VERSION, body.getValue("termsVersion").jsonPrimitive.content)
        assertEquals("weekly", body.getValue("shopping").jsonPrimitive.content)
        assertEquals("au_nz", body.getValue("region").jsonPrimitive.content)
        assertEquals("The muesli aisle here is a mess.", body.getValue("motivation").jsonPrimitive.content)
        assertEquals(0, entitlements.refreshed)
    }

    @Test
    fun beingAcceptedElsewhereRefreshesWhatTheAccountHoldsOnTheNextRead() = runUnitTest {
        val server = Server(HttpStatusCode.OK, pending)
        val builders = program(server)
        builders.refresh()
        assertEquals(0, entitlements.refreshed)

        server.body = joined
        builders.refresh()

        assertEquals(true, builders.isBuilder)
        assertEquals(1, entitlements.refreshed)
        builders.refresh()
        assertEquals(1, entitlements.refreshed, "only when it changes")
    }

    @Test
    fun aRefusedApplicationSaysWhy() = runUnitTest {
        val builders = program(HttpStatusCode.Forbidden, """{"error":"declined","message":"You can apply again later."}""")

        assertEquals(BuilderResult.Blocked(JoinBlock.Declined), builders.sendApplication(form))
        assertEquals(BuilderResult.Blocked(JoinBlock.TooNew), program(HttpStatusCode.Forbidden, """{"error":"too_new"}""").sendApplication(form))
    }

    @Test
    fun aDeclineCarriesItsNoteAndADecisionIsSeenOnce() = runUnitTest {
        val declined = """
            {"status":"none","trust":"anyone","canJoin":false,"applyBlock":"declined","availableAtEpochMs":1802592000000,
             "application":{"id":"a2","status":"declined","submittedAtEpochMs":1800000000000,"decidedAtEpochMs":1800000000000,
              "note":"  Not this month.  ","reapplyAtEpochMs":1802592000000}}
        """.trimIndent()
        val builders = program(HttpStatusCode.OK, declined)

        val standing = assertIs<BuilderResult.Done>(builders.refresh()).standing

        assertEquals("Not this month.", standing.application?.note)
        assertEquals(JoinBlock.Declined, standing.joinBlock)
        assertEquals(false, builders.decisionSeen("a2"))
        builders.markDecisionSeen("a2")
        assertEquals(true, builders.decisionSeen("a2"))
        assertEquals(false, builders.decisionSeen("a3"))
    }

    @Test
    fun withdrawingPostsAndAnOlderServerAnswerStillParses() = runUnitTest {
        val seen = mutableListOf<String>()
        val older = """{"status":"none","trust":"anyone","canJoin":true}"""
        val result = assertIs<BuilderResult.Done>(program(HttpStatusCode.OK, older, seen).withdraw())

        assertEquals("POST /v1/builders/applications/withdraw", seen[0])
        assertEquals(true, result.standing.canApply)
        assertNull(result.standing.application)
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

        val result = checks.verify(FoodVerification("c1", "food-1", "mlkit", listOf(LabelTextLine("Calories 230", 0.9))))

        assertEquals(FoodCheckResult.Unconfirmed(listOf("fat_g")), result)
        val body = Json.parseToJsonElement(seen.single()).jsonObject
        val line = body.getValue("lines").jsonArray.single().jsonObject
        assertEquals("Calories 230", line.getValue("text").jsonPrimitive.content)
        // An old app's check: no problem, no photos.
        assertFalse("problem" in body)
        assertFalse("photos" in body)
    }

    @Test
    fun aMatchWaitingForASecondCheckIsReadAsThat_notAsTheReviewItAlsoSitsIn() = runUnitTest {
        val checks = HttpFoodChecks(
            FakeNetworkClient {
                respond(
                    """{"id":"c3","outcome":"confirmed","grounded":4,"groundable":4,"review":"open","awaiting":"agreement"}""",
                    HttpStatusCode.Created,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )

        val result = checks.verify(FoodVerification("c3", "food-1", "mlkit", listOf(LabelTextLine("Calories 230", 0.9))))

        assertEquals(FoodCheckResult.AwaitingAgreement, result)
    }

    @Test
    fun somethingWrongGoesWithItsPhotosAndWaitsForAReviewer() = runUnitTest {
        val seen = mutableListOf<String>()
        val checks = HttpFoodChecks(
            FakeNetworkClient { request ->
                seen += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                respond(
                    """{"id":"c2","outcome":"unconfirmed","grounded":4,"groundable":4,"review":"open"}""",
                    HttpStatusCode.Created,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        val problem = VerifyProblem(
            wrong = setOf(VerifyField.Calories, VerifyField.Serving),
            // Said both ways: wrong wins.
            missing = setOf(VerifyField.Ingredients, VerifyField.Serving),
            values = VerifyValues(basisLabel = "1 bar", basisGrams = 40.0, kcal = 190.0, name = "  "),
            note = "  New recipe  ",
        )

        val result = checks.verify(FoodVerification("c2", "food-1", "vision", emptyList(), problem, front = byteArrayOf(1), label = byteArrayOf(2)))

        assertEquals(FoodCheckResult.InReview, result)
        val body = Json.parseToJsonElement(seen.single()).jsonObject
        val sent = body.getValue("problem").jsonObject
        assertEquals(listOf("calories", "serving"), sent.getValue("wrong").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("ingredients"), sent.getValue("missing").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("New recipe", sent.getValue("note").jsonPrimitive.content)
        val values = sent.getValue("values").jsonObject
        assertEquals(190.0, values.getValue("kcal").jsonPrimitive.content.toDouble())
        assertFalse("name" in values, "a blank name is left out")
        val photos = body.getValue("photos").jsonObject
        assertTrue("front" in photos && "label" in photos)
    }

}
