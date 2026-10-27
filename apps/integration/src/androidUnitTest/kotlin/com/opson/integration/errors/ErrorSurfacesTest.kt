package com.opson.integration.errors

import com.opson.integration.helpers.FaultProxy
import com.opson.integration.helpers.FaultProxy.Fault
import com.opson.integration.helpers.Harness
import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.TestClient
import com.opson.integration.helpers.TestClock
import com.opson.integration.helpers.randomUserId
import com.opson.libraries.diary.SyncEntity
import com.opson.libraries.diary.SyncRejection
import com.opson.libraries.foods.PhotoEstimate
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.net.SocketTimeoutException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * **When the server misbehaves.** A 500, a request that never answers and a
 * 200 whose body is not the contract each leave sync failed, the write on
 * screen and in the outbox, and the cursor where it was, and the next good
 * sync carries the write up exactly once. Search falls back to foods picked
 * before; a photo estimate says it failed. Nothing throws out of a repository
 * and nothing is lost.
 */
class ErrorSurfacesTest : IntegrationTest() {

    private class Setup(val phone: TestClient, val proxy: FaultProxy, val user: String)

    /** A phone behind a proxy, warmed by one good sync so the first slow request is not the faulted one. */
    private suspend fun Harness.phoneBehindProxy(timeoutMs: Long = 30_000): Setup {
        val user = randomUserId()
        val proxy = faultProxy()
        val phone = client(user, through = proxy, requestTimeoutMillis = timeoutMs)
        phone.syncNow().getOrThrow()
        return Setup(phone, proxy, user)
    }

    private suspend fun Harness.assertSyncFailsKeepingTheWrite(setup: Setup, expected: (Throwable) -> Unit) {
        val phone = setup.phone
        val cursor = phone.tables.syncState.get()?.cursor
        val tea = phone.log("Tea", kcal = 2.0)

        val result = phone.syncNow()

        expected(result.exceptionOrNull() ?: error("the sync should have failed"))
        assertEquals(listOf(tea), phone.diary.observeDay(phone.today()).first().allItems, "still on screen")
        assertEquals(1, phone.pendingChanges(), "still queued")
        assertEquals(cursor, phone.tables.syncState.get()?.cursor, "the pull cursor did not move")
        assertEquals(0L, server.countFor("log_entries", setup.user))

        setup.proxy.heal()
        phone.syncNow().getOrThrow()
        assertEquals(0, phone.pendingChanges())
        assertEquals(1L, server.countFor("log_entries", setup.user), "it went up once")
    }

    @Test
    fun sync_server500_failsAndKeepsTheWrite() = integration {
        val setup = phoneBehindProxy()
        setup.proxy.failAll("/v1/sync", Fault.Status(HttpStatusCode.InternalServerError))

        assertSyncFailsKeepingTheWrite(setup) { assertIs<ServerResponseException>(it) }
        assertTrue(setup.proxy.requests.count { it == "/v1/sync" } >= 1 + 4, "a 5xx is retried before giving up")
    }

    @Test
    fun sync_timeout_failsAndKeepsTheWrite() = integration {
        val setup = phoneBehindProxy(timeoutMs = 1_000)
        setup.proxy.failAll("/v1/sync", Fault.Hang)

        assertSyncFailsKeepingTheWrite(setup) {
            assertTrue(it is HttpRequestTimeoutException || it is SocketTimeoutException, "a timeout, not $it")
        }
    }

    @Test
    fun sync_malformedResponse_failsAndKeepsTheWrite() = integration {
        val setup = phoneBehindProxy()
        setup.proxy.failAll("/v1/sync", Fault.Malformed)

        assertSyncFailsKeepingTheWrite(setup) { /* any decode failure */ }
    }

    @Test
    fun sync_aRowTheServerRefuses_isRolledBack_andReported() = integration {
        val phone = client(randomUserId())
        val bread = phone.log("Bread", kcal = 80.0)
        // An older build queued this row before the app learned to clamp names
        // to the server's 200 characters.
        val queued = phone.tables.outbox.page(10, "9999-12-31").single()
        val row = Json.parseToJsonElement(queued.payload).jsonObject
        phone.tables.outbox.deleteByIds(listOf(queued.id))
        phone.tables.outbox.insert(queued.copy(payload = JsonObject(row + ("name" to JsonPrimitive("x".repeat(201)))).toString()))

        coroutineScope {
            val rejection = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { phone.sync.rejections.first() } }
            phone.syncNow().getOrThrow()
            assertEquals(SyncRejection(SyncEntity.LogEntry, bread.id, "invalid_row"), rejection.await())
        }
        assertEquals(emptyList(), phone.diary.observeDay(phone.today()).first().allItems, "the refused new row is rolled back")
        assertEquals(0, phone.pendingChanges(), "and not sent again forever")
    }

    @Test
    fun sync_aPhoneClockDaysAhead_doesNotLoseTheWrite() = integration {
        val user = randomUserId()
        val phone = client(user, clock = TestClock().apply { advance(2.days) })
        val toast = phone.log("Toast", kcal = 90.0)

        phone.syncNow().getOrThrow()

        assertEquals(
            listOf(toast.id to "Toast"),
            phone.diary.observeDay(phone.today()).first().allItems.map { it.id to it.name },
            "still on the phone (its logged time now the server's)",
        )
        assertEquals(1L, server.countFor("log_entries", user), "and on the server")
        assertEquals(0, phone.pendingChanges())

        // Its far-future stamp must not win over a later edit from a phone whose clock is right.
        val other = client(user)
        other.syncNow().getOrThrow()
        other.diary.updateItem(other.diary.observeDay(phone.today()).first().allItems.single().copy(name = "Rye toast"))
        other.syncNow().getOrThrow()
        phone.syncNow().getOrThrow()
        assertEquals("Rye toast", phone.diary.observeDay(phone.today()).first().allItems.single().name)
    }

    @Test
    fun search_whenTheServerFails_fallsBackToFoodsPickedBefore() = integration {
        val word = (1..10).map { 'a' + Random.nextInt(26) }.joinToString("")
        val id = server.seedFood("$word plum", kcalPer100g = 46.0)
        val setup = phoneBehindProxy(timeoutMs = 1_000)
        val foods = setup.phone.foods
        foods.markUsed(foods.detail(id).getOrThrow())

        for (fault in listOf(Fault.Status(HttpStatusCode.InternalServerError), Fault.Hang, Fault.Malformed)) {
            setup.proxy.failAll("/v1/foods/search", fault)
            val result = foods.search("$word plum").getOrThrow()
            assertTrue(result.fromCache, "$fault: answered from the device")
            assertEquals(listOf(id), result.foods.map { it.id }, "$fault: the food picked before")
            setup.proxy.heal()
        }

        val online = foods.search("$word plum").getOrThrow()
        assertFalse(online.fromCache)
    }

    @Test
    fun photo_whenTheServerFails_saysItFailed() = integration {
        val setup = phoneBehindProxy()
        setup.proxy.failAll("/v1/describe/photo", Fault.Status(HttpStatusCode.InternalServerError))
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(32)

        assertEquals(PhotoEstimate.Failed(offline = false), setup.phone.photoEstimator.estimate(jpeg, note = null))

        setup.proxy.heal()
        setup.proxy.failAll("/v1/describe/photo", Fault.Malformed)
        assertIs<PhotoEstimate.Failed>(setup.phone.photoEstimator.estimate(jpeg, note = null))
    }
}
