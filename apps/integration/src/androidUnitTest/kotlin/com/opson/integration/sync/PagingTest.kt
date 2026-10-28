package com.opson.integration.sync

import com.opson.integration.helpers.Harness
import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.randomUserId
import com.opson.integration.helpers.rawSync
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * **A heavy account on a fresh install.** The server pulls at most `DiaryRules.PAGE_SIZE` (500)
 * rows of each entity per request and says `hasMore`, with the cursor held at the lowest point any
 * truncated entity reached. The phone follows `hasMore` within its request budget. Both halves are
 * unit-tested on their own side; this runs them together with more than one page of two entities
 * at once, so a cursor that skipped rows or a client that stopped early shows up as missing rows.
 */
class PagingTest : IntegrationTest() {

    @Test
    fun aFreshInstall_pullsMoreThanOnePageOfEveryEntity() = integration {
        val user = randomUserId()
        server.seedAuthUser(user)
        val today = Clock.System.todayIn(TimeZone.UTC)
        val days = (0 until DAY_RECORDS).map { today.minus(it, DateTimeUnit.DAY) }
        val entryIds = (0 until ENTRIES).map { UUID.randomUUID().toString() }

        // Interleaved, so both entities grow their server_seq together and the cursor has to stop
        // at the lower of the two truncated pages to miss nothing.
        val mutations = entryIds.mapIndexed { i, id -> entryMutation(id, days[i % days.size]) } +
            days.map(::dayMutation)
        mutations.shuffled().chunked(MUTATIONS_PER_PUSH).forEach { chunk -> push(user, chunk) }
        assertEquals(ENTRIES.toLong(), server.countFor("log_entries", user))
        assertEquals(DAY_RECORDS.toLong(), server.countFor("day_records", user))

        val proxy = faultProxy()
        val phone = client(user, through = proxy)
        phone.syncNow().getOrThrow()

        val entries = phone.tables.entries.observeRange(FIRST_DAY, LAST_DAY).first()
        assertEquals(entryIds.toSet(), entries.map { it.id }.toSet(), "every entry, across three pages")
        val records = phone.tables.days.observeRange(FIRST_DAY, LAST_DAY).first()
        assertEquals(days.map { it.toString() }.toSet(), records.map { it.day }.toSet(), "every day, across two pages")
        assertEquals(0, phone.pendingChanges(), "pulling queues nothing to push back")
        assertTrue(proxy.requests.count { it == "/v1/sync" } >= 3, "one sync, three pages: ${proxy.requests}")
    }

    private suspend fun Harness.push(user: String, chunk: List<JsonObject>) {
        val body = buildJsonObject {
            put("cursor", 0)
            put("mutations", buildJsonArray { chunk.forEach { add(it) } })
        }
        val response = Json.parseToJsonElement(rawSync(user, body.toString()).bodyAsText()).jsonObject
        assertEquals(chunk.size, response.getValue("applied").jsonArray.size, "the seed was refused: $response")
    }

    private fun entryMutation(id: String, day: LocalDate) = mutation(
        "log_entry",
        buildJsonObject {
            put("id", id)
            put("day", day.toString())
            put("slot", "lunch")
            put("loggedAtEpochMs", NOW_MS)
            put("name", "Soup")
            put("precision", "estimate")
            put("kcal", 200.0)
            put("method", "search")
            put("clientUpdatedAtEpochMs", NOW_MS)
        },
    )

    private fun dayMutation(day: LocalDate) = mutation(
        "day_record",
        buildJsonObject {
            put("day", day.toString())
            put("waterMl", 500)
            put("clientUpdatedAtEpochMs", NOW_MS)
        },
    )

    private fun mutation(entity: String, row: JsonObject) = buildJsonObject {
        put("id", UUID.randomUUID().toString())
        put("entity", entity)
        put("row", row)
    }

    private companion object {
        /** Three pages of 500. */
        const val ENTRIES = 1_200

        /** Two pages of 500. */
        const val DAY_RECORDS = 600

        /** Under the server's 500-mutation cap. */
        const val MUTATIONS_PER_PUSH = 400

        const val FIRST_DAY = "0000-01-01"
        const val LAST_DAY = "9999-12-31"

        val NOW_MS = System.currentTimeMillis()
    }
}
