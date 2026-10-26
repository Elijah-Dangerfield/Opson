package com.opson.server.routes

import com.opson.server.domain.DiaryChanges
import com.opson.server.domain.DiaryRules
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
import kotlinx.serialization.json.put
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class SyncRoutesTest {

    private val userId = UserId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
    private val now = Instant.parse("2026-10-25T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private class FakeSyncRepository : SyncRepository {
        val calls = mutableListOf<Triple<UserId, Long, List<SyncMutation>>>()
        override suspend fun sync(userId: UserId, cursor: Long, mutations: List<SyncMutation>): SyncResult {
            calls += Triple(userId, cursor, mutations)
            return SyncResult(mutations.map { it.outboxId }, DiaryChanges(), cursor = cursor + 7, hasMore = false)
        }
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
            SyncMutationDto("entity", "recipe", entryRow()),
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
