package com.opson.server

import com.opson.server.db.DatabaseTest
import com.opson.server.di.ServerComponent
import com.opson.server.di.create
import com.opson.server.domain.UserId
import com.opson.server.routes.FoodSearchResponse
import com.opson.server.routes.SyncMutationDto
import com.opson.server.routes.SyncRequest
import com.opson.server.routes.SyncResponse
import com.opson.server.testing.DiaryFixtures
import com.opson.server.testing.TestJwt
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.After
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `/v1/sync` end to end: the real DI graph over real Postgres through
 * [installApp], two "devices" for one user plus a second user. Covers push,
 * pull, a conflict, a tombstone, a replay, and that the export and search
 * routes read the same data.
 */
class FullStackSyncTest : DatabaseTest() {

    @After
    fun clean() = DiaryFixtures.cleanAll(database)

    private val t0 = System.currentTimeMillis() - 60_000

    private fun entryRow(id: String, name: String, updatedAt: Long, deleted: Boolean = false, foodId: String? = null): JsonObject =
        buildJsonObject {
            put("id", id)
            put("day", java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString())
            put("slot", "lunch")
            put("loggedAtEpochMs", t0)
            if (foodId != null) put("foodId", foodId)
            put("name", name)
            put("amountText", "1 bowl")
            put("grams", 300.0)
            put("precision", "estimate")
            put("kcal", 420.0)
            put("method", "describe")
            put("clientUpdatedAtEpochMs", updatedAt)
            put("deleted", deleted)
        }

    private suspend fun HttpClient.sync(user: UserId, cursor: Long, vararg mutations: SyncMutationDto): SyncResponse {
        val resp = post("/v1/sync") {
            header(HttpHeaders.Authorization, TestJwt.bearer(user))
            contentType(ContentType.Application.Json)
            setBody(SyncRequest(cursor, mutations.toList()))
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        return resp.body()
    }

    @Test
    fun twoDevicesConvergeThroughSync() = runTest {
        val user = seedAuthUser()
        val other = seedAuthUser()
        val component = ServerComponent::class.create(database, null)
        val foodId = DiaryFixtures.insertFood(database, name = "Soup, lentil").toString()
        val entryId = UUID.randomUUID().toString()

        testApplication {
            application { installApp(component, TestJwt.verification) }
            val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

            // Device A logs lunch and sets a preset.
            val create = SyncMutationDto("a1", "log_entry", entryRow(entryId, "Lentil soup", t0, foodId = foodId))
            val settings = SyncMutationDto(
                "a2",
                "settings",
                buildJsonObject {
                    putJsonObject("settings") { put("preset", "delayed") }
                    put("clientUpdatedAtEpochMs", t0)
                },
            )
            val a = client.sync(user, 0, create, settings)
            assertEquals(listOf("a1", "a2"), a.applied)

            // Device B starts from zero and sees both.
            val b = client.sync(user, 0)
            assertEquals(listOf("Lentil soup"), b.changes.logEntries.map { it.name })
            assertEquals(foodId, b.changes.logEntries.single().foodId)
            assertEquals("delayed", b.changes.settings?.settings?.get("preset")?.toString()?.trim('"'))

            // Conflict: B renames later, A renames earlier but syncs after. B's edit wins.
            client.sync(user, b.cursor, SyncMutationDto("b1", "log_entry", entryRow(entryId, "Red lentil soup", t0 + 2_000)))
            val aLate = client.sync(user, a.cursor, SyncMutationDto("a3", "log_entry", entryRow(entryId, "Soup", t0 + 1_000)))
            assertEquals(listOf("a3"), aLate.applied)
            assertEquals(listOf("Red lentil soup"), aLate.changes.logEntries.map { it.name })

            // Tombstone from A reaches B.
            val aDelete = client.sync(
                user,
                aLate.cursor,
                SyncMutationDto("a4", "log_entry", entryRow(entryId, "Red lentil soup", t0 + 3_000, deleted = true)),
            )
            val bAfterDelete = client.sync(user, b.cursor)
            assertTrue(bAfterDelete.changes.logEntries.single().deleted)

            // Replaying A's very first push changes nothing for anyone.
            val replay = client.sync(user, aDelete.cursor, create)
            assertEquals(listOf("a1"), replay.applied)
            assertTrue(replay.changes.logEntries.isEmpty())
            assertEquals(aDelete.cursor, replay.cursor)

            // Another user sees none of it.
            val stranger = client.sync(other, 0)
            assertTrue(stranger.changes.logEntries.isEmpty())
            assertEquals(null, stranger.changes.settings)

            // Search reads the same catalog the entry linked to.
            val search = client.get("/v1/foods/search?q=lentil") { header(HttpHeaders.Authorization, TestJwt.bearer(user)) }
            assertEquals("Soup, lentil", search.body<FoodSearchResponse>().foods.single().name)
        }
    }

    @Test
    fun exportReadsWhatSyncWrote() = runTest {
        val user = seedAuthUser()
        val component = ServerComponent::class.create(database, null)
        testApplication {
            application { installApp(component, TestJwt.verification) }
            val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            client.sync(
                user,
                0,
                SyncMutationDto("e1", "log_entry", entryRow(UUID.randomUUID().toString(), "Porridge", t0)),
                SyncMutationDto("e2", "log_entry", entryRow(UUID.randomUUID().toString(), "Deleted", t0, deleted = true)),
                SyncMutationDto(
                    "w1",
                    "weigh_in",
                    buildJsonObject {
                        put("id", UUID.randomUUID().toString())
                        put("measuredAtEpochMs", t0)
                        put("kg", 70.4)
                        put("clientUpdatedAtEpochMs", t0)
                    },
                ),
            )
            val csv = client.get("/v1/export.csv") { header(HttpHeaders.Authorization, TestJwt.bearer(user)) }.bodyAsText()
            val lines = csv.trim().lines()
            assertEquals(3, lines.size, csv)
            assertTrue(lines[1].startsWith("entry,") && lines[1].contains("Porridge"))
            assertTrue(lines[2].startsWith("weight,") && lines[2].contains("70.4"))
        }
    }
}
