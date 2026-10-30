package com.opson.server.routes

import com.opson.server.domain.DiaryChanges
import com.opson.server.domain.DiaryRules
import com.opson.server.domain.Exercise
import com.opson.server.domain.Recipe
import com.opson.server.domain.Fast
import com.opson.server.domain.LogEntry
import com.opson.server.domain.SyncMutation
import com.opson.server.domain.SyncRepository
import com.opson.server.domain.SyncResult
import com.opson.server.domain.UserId
import com.opson.server.plugins.ProblemResponse
import com.opson.server.plugins.installAuthentication
import com.opson.server.plugins.installRateLimits
import com.opson.server.plugins.installSerialization
import com.opson.server.plugins.installStatusPages
import com.opson.server.testing.TestJwt
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class SyncRoutesTest {

    private companion object {
        val FAST = Fast(
            day = LocalDate.parse("2026-10-24"),
            startedAt = Instant.parse("2026-10-24T19:00:00Z"),
            endedAt = null,
            goalHours = 14,
            clientUpdatedAt = Instant.parse("2026-10-24T19:00:00Z"),
            deleted = false,
        )
        val EXERCISE = Exercise(
            id = UUID.fromString("22222222-2222-4222-8222-222222222222"),
            day = LocalDate.parse("2026-10-24"),
            startedAt = Instant.parse("2026-10-24T07:00:00Z"),
            activity = "running_10",
            name = null,
            minutes = 30,
            kcal = 290,
            kcalTyped = false,
            notOnWatch = false,
            clientUpdatedAt = Instant.parse("2026-10-24T08:00:00Z"),
            deleted = false,
        )
    }

    private val userId = UserId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
    private val now = Instant.parse("2026-10-25T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private class FakeSyncRepository : SyncRepository {
        val calls = mutableListOf<Triple<UserId, Long, List<SyncMutation>>>()
        val pulledFasts = mutableListOf<Boolean>()
        val pulledExercises = mutableListOf<Boolean>()
        val knewCheckIn = mutableListOf<Boolean>()
        val pulledRecipes = mutableListOf<Boolean>()
        override suspend fun sync(
            userId: UserId,
            cursor: Long,
            mutations: List<SyncMutation>,
            pullFasts: Boolean,
            pullExercises: Boolean,
            clientKnowsCheckIn: Boolean,
            pullRecipes: Boolean,
        ): SyncResult {
            calls += Triple(userId, cursor, mutations)
            pulledRecipes += pullRecipes
            pulledFasts += pullFasts
            pulledExercises += pullExercises
            knewCheckIn += clientKnowsCheckIn
            val fasts = if (pullFasts) listOf(FAST) else null
            val exercises = if (pullExercises) listOf(EXERCISE) else null
            return SyncResult(mutations.map { it.outboxId }, DiaryChanges(fasts = fasts, exercises = exercises), cursor = cursor + 7, hasMore = false)
        }
    }

    private fun fastRow(hours: Long = 14, goalHours: Int = 14): JsonObject = buildJsonObject {
        val start = Instant.parse("2026-10-24T19:00:00Z")
        put("day", "2026-10-24")
        put("startedAtEpochMs", start.toEpochMilliseconds())
        put("endedAtEpochMs", start.toEpochMilliseconds() + hours * 3_600_000)
        put("goalHours", goalHours)
        put("clientUpdatedAtEpochMs", now.toEpochMilliseconds())
    }

    private fun exerciseRow(minutes: Int = 30, kcal: Int = 290, activity: String = "running_10", day: String = "2026-10-24"): JsonObject =
        buildJsonObject {
            put("id", UUID.randomUUID().toString())
            put("day", day)
            put("startedAtEpochMs", Instant.parse("2026-10-24T07:00:00Z").toEpochMilliseconds())
            put("activity", activity)
            put("minutes", minutes)
            put("kcal", kcal)
            put("clientUpdatedAtEpochMs", now.toEpochMilliseconds())
        }

    private fun entryRow(day: String = "2026-10-25", kcal: Double = 105.0, slot: String = "breakfast"): JsonObject =
        buildJsonObject {
            put("id", UUID.randomUUID().toString())
            put("day", day)
            put("slot", slot)
            put("loggedAtEpochMs", now.toEpochMilliseconds())
            put("name", "Banana")
            put("precision", "estimate")
            put("kcal", kcal)
            put("method", "search")
            put("clientUpdatedAtEpochMs", now.toEpochMilliseconds())
        }

    private suspend fun withApp(repo: SyncRepository, block: suspend (HttpClient) -> Unit) {
        testApplication {
            application {
                installSerialization()
                installRateLimits()
                installStatusPages()
                installAuthentication(TestJwt.verification)
                routing { syncRoutes(repo, clock) }
            }
            block(createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } })
        }
    }

    private suspend fun HttpClient.sync(body: Any, auth: Boolean = true): HttpResponse = post("/v1/sync") {
        if (auth) header(HttpHeaders.Authorization, TestJwt.bearer(userId))
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    @Test
    fun requiresAuth() = runTest {
        val repo = FakeSyncRepository()
        withApp(repo) { client ->
            assertEquals(HttpStatusCode.Unauthorized, client.sync(SyncRequest(), auth = false).status)
            assertTrue(repo.calls.isEmpty())
        }
    }

    @Test
    fun emptyOutboxStillPulls() = runTest {
        val repo = FakeSyncRepository()
        withApp(repo) { client ->
            val resp = client.sync(SyncRequest(cursor = 40))
            assertEquals(HttpStatusCode.OK, resp.status)
            val body = resp.body<SyncResponse>()
            assertEquals(47, body.cursor)
            assertEquals(false, body.hasMore)
            assertEquals(Triple(userId, 40L, emptyList<SyncMutation>()), repo.calls.single())
        }
    }

    @Test
    fun validMutationIsPassedThroughAndEchoed() = runTest {
        val repo = FakeSyncRepository()
        withApp(repo) { client ->
            val body = client.sync(SyncRequest(mutations = listOf(SyncMutationDto("outbox-1", "log_entry", entryRow()))))
                .body<SyncResponse>()
            assertEquals(listOf("outbox-1"), body.applied)
            assertTrue(body.rejected.isEmpty())
            val entry = assertIs<LogEntry>(repo.calls.single().third.single().row)
            assertEquals("Banana", entry.name)
        }
    }

    @Test
    fun userIdInTheBodyIsIgnored() = runTest {
        val repo = FakeSyncRepository()
        val row = JsonObject(entryRow() + ("userId" to JsonPrimitive(UUID.randomUUID().toString())))
        withApp(repo) { client ->
            client.sync(SyncRequest(mutations = listOf(SyncMutationDto("m", "log_entry", row))))
            assertEquals(userId, repo.calls.single().first)
        }
    }

    @Test
    fun unknownEntityAndInvalidRowsAreRejectedWithStableCodes() = runTest {
        val repo = FakeSyncRepository()
        val mutations = listOf(
            SyncMutationDto("ok", "log_entry", entryRow()),
            SyncMutationDto("entity", "smoothie", entryRow()),
            SyncMutationDto("slot", "log_entry", entryRow(slot = "brunch")),
            SyncMutationDto("negative", "log_entry", entryRow(kcal = -5.0)),
            SyncMutationDto("future", "log_entry", entryRow(day = "2027-01-01")),
            SyncMutationDto("shape", "weigh_in", buildJsonObject { put("kg", "heavy") }),
        )
        withApp(repo) { client ->
            val body = client.sync(SyncRequest(mutations = mutations)).body<SyncResponse>()
            assertEquals(listOf("ok"), body.applied)
            assertEquals(
                mapOf(
                    "entity" to "unknown_entity",
                    "slot" to "invalid_row",
                    "negative" to "invalid_row",
                    "future" to "invalid_row",
                    "shape" to "invalid_row",
                ),
                body.rejected.associate { it.id to it.code },
            )
            assertEquals("slot is not a meal slot", body.rejected.first { it.id == "slot" }.message)
            assertTrue(body.rejected.none { it.message.contains("heavy") }, "messages must not echo row values")
            assertEquals(1, repo.calls.single().third.size)
        }
    }

    @Test
    fun aZeroOneRequestGetsTheZeroOneResponse() = runTest {
        val repo = FakeSyncRepository()
        withApp(repo) { client ->
            // Exactly what a 0.1 client sends: no `accepts`.
            val resp = client.sync("""{"cursor":0,"mutations":[]}""")
            assertEquals(HttpStatusCode.OK, resp.status)
            val changes = Json.parseToJsonElement(resp.bodyAsText()).jsonObject.getValue("changes").jsonObject
            assertEquals(
                setOf("logEntries", "dayRecords", "mealFeelings", "weighIns", "customFoods", "savedMeals"),
                changes.keys,
                "no key a 0.1 client does not know",
            )
            assertEquals(listOf(false), repo.pulledFasts)
            assertEquals(listOf(false), repo.pulledExercises)
            assertEquals(listOf(false), repo.knewCheckIn, "0.1's day records carry no check-in")
        }
    }

    @Test
    fun aRequestThatAcceptsExerciseGetsIt_andOnlyThat() = runTest {
        val repo = FakeSyncRepository()
        withApp(repo) { client ->
            val resp = client.sync("""{"cursor":0,"mutations":[],"accepts":["exercise"]}""")
            val changes = Json.parseToJsonElement(resp.bodyAsText()).jsonObject.getValue("changes").jsonObject
            assertTrue("exercises" in changes)
            assertTrue("fasts" !in changes, "fasts were not asked for")
            assertEquals(listOf(EXERCISE.toRowDto()), Json.decodeFromString<SyncResponse>(resp.bodyAsText()).changes.exercises)
            assertEquals(listOf(true), repo.pulledExercises)
            assertEquals(listOf(true), repo.knewCheckIn, "any accepts at all is a client that knows check-ins")
        }
    }

    @Test
    fun anExerciseIsDecoded_andOneOutOfRangeIsRefused() = runTest {
        val repo = FakeSyncRepository()
        val mutations = listOf(
            SyncMutationDto("ok", "exercise", exerciseRow()),
            SyncMutationDto("long", "exercise", exerciseRow(minutes = 1441)),
            SyncMutationDto("none", "exercise", exerciseRow(minutes = 0)),
            SyncMutationDto("big", "exercise", exerciseRow(kcal = 9_000)),
            SyncMutationDto("blank", "exercise", exerciseRow(activity = " ")),
            SyncMutationDto("future", "exercise", exerciseRow(day = "2026-12-25")),
        )
        withApp(repo) { client ->
            val body = client.sync(SyncRequest(mutations = mutations, accepts = listOf("exercise"))).body<SyncResponse>()
            assertEquals(listOf("ok"), body.applied)
            assertEquals(
                mapOf("long" to "invalid_row", "none" to "invalid_row", "big" to "invalid_row", "blank" to "invalid_row", "future" to "invalid_row"),
                body.rejected.associate { it.id to it.code },
            )
            val exercise = assertIs<Exercise>(repo.calls.single().third.single().row)
            assertEquals(290, exercise.kcal)
            assertEquals("running_10", exercise.activity)
        }
    }

    @Test
    fun aRecipeIsDecodedWithoutItsSharing_andOneOutOfRangeIsRefused() = runTest {
        val repo = FakeSyncRepository()
        fun recipeRow(servings: Double = 6.0, items: Int = 2) = buildJsonObject {
            put("id", UUID.randomUUID().toString())
            put("name", "Chili")
            put("servings", servings)
            put("items", kotlinx.serialization.json.JsonArray(List(items) { buildJsonObject { put("name", "Beans") } }))
            put("clientUpdatedAtEpochMs", now.toEpochMilliseconds())
            // A client can't make its recipe public through sync.
            put("publicCode", "abcd2345")
            put("publicState", "live")
        }
        val mutations = listOf(
            SyncMutationDto("ok", "recipe", recipeRow()),
            SyncMutationDto("none", "recipe", recipeRow(servings = 0.0)),
            SyncMutationDto("crowded", "recipe", recipeRow(items = 61)),
        )
        withApp(repo) { client ->
            val body = client.sync(SyncRequest(mutations = mutations, accepts = listOf("recipe"))).body<SyncResponse>()
            assertEquals(listOf("ok"), body.applied)
            assertEquals(mapOf("none" to "invalid_row", "crowded" to "invalid_row"), body.rejected.associate { it.id to it.code })
            val recipe = assertIs<Recipe>(repo.calls.single().third.single().row)
            assertEquals(2, recipe.itemCount)
            assertNull(recipe.sharing)
            assertEquals(listOf(true), repo.pulledRecipes)
        }
    }

    @Test
    fun aRequestThatAcceptsFastsGetsThem() = runTest {
        val repo = FakeSyncRepository()
        withApp(repo) { client ->
            val body = client.sync(SyncRequest(accepts = listOf("fast"))).body<SyncResponse>()
            assertEquals(listOf(FAST.toRowDto()), body.changes.fasts)
            assertEquals(listOf(true), repo.pulledFasts)
        }
    }

    @Test
    fun aFastIsDecodedAndAFastPast16HoursIsRefused() = runTest {
        val repo = FakeSyncRepository()
        val mutations = listOf(
            SyncMutationDto("ok", "fast", fastRow()),
            SyncMutationDto("long", "fast", fastRow(hours = 17)),
            SyncMutationDto("goal", "fast", fastRow(hours = 2, goalHours = 20)),
        )
        withApp(repo) { client ->
            val body = client.sync(SyncRequest(mutations = mutations, accepts = listOf("fast"))).body<SyncResponse>()
            assertEquals(listOf("ok"), body.applied)
            assertEquals(mapOf("long" to "invalid_row", "goal" to "invalid_row"), body.rejected.associate { it.id to it.code })
            val fast = assertIs<Fast>(repo.calls.single().third.single().row)
            assertEquals(14, fast.goalHours)
        }
    }

    @Test
    fun tooManyMutationsIsA400() = runTest {
        val repo = FakeSyncRepository()
        val mutations = List(DiaryRules.MAX_MUTATIONS + 1) { SyncMutationDto("m$it", "log_entry", entryRow()) }
        withApp(repo) { client ->
            val resp = client.sync(SyncRequest(mutations = mutations))
            assertEquals(HttpStatusCode.BadRequest, resp.status)
            assertEquals("too_many_mutations", resp.body<ProblemResponse>().error.code)
            assertTrue(repo.calls.isEmpty())
        }
    }

    @Test
    fun negativeCursorIsA400() = runTest {
        withApp(FakeSyncRepository()) { client ->
            val resp = client.sync(SyncRequest(cursor = -1))
            assertEquals("invalid_cursor", resp.body<ProblemResponse>().error.code)
        }
    }

    @Test
    fun malformedBodyIsA400Problem() = runTest {
        withApp(FakeSyncRepository()) { client ->
            val resp = client.sync("""{"mutations": "nope"}""")
            assertEquals(HttpStatusCode.BadRequest, resp.status)
            assertEquals("bad_request", resp.body<ProblemResponse>().error.code)
        }
    }
}
