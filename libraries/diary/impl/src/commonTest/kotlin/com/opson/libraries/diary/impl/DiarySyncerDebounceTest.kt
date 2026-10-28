package com.opson.libraries.diary.impl

import com.opson.libraries.diary.impl.wire.SyncChangesDto
import com.opson.libraries.diary.impl.wire.SyncResponseDto
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/**
 * The sync after a local write waits [SYNC_DEBOUNCE] from the last write, so a
 * burst of edits goes up as one request. Virtual time, so a standard
 * dispatcher (docs/practices/testing.md).
 */
class DiarySyncerDebounceTest : CoroutineTest() {
    override val testDispatcher: TestDispatcher = StandardTestDispatcher()

    private val db = FakeDiaryDb()
    private val clock = FakeClock()
    private val user = FakeActiveUser()
    private val appScope by lazy { AppCoroutineScope(dispatchers) }
    private val api = FakeSyncApi { request ->
        SyncResponseDto(request.mutations.map { it.id }, emptyList(), SyncChangesDto(), request.cursor + 1, false)
    }
    private val syncer by lazy {
        DiarySyncer(
            api,
            db.tables,
            SettingsStore(FakeCacheFactory(), db.outboxDao, db.transactor, clock, user, appScope),
            user,
            appScope,
            clock,
            FakeCacheFactory(),
        )
    }

    @AfterTest
    fun tearDown() = appScope.cancel()

    @Test
    fun aBurstOfWritesSyncsOnceTwoSecondsAfterTheLast() = runUnitTest {
        syncer.requestSync()
        runCurrent()
        advanceTimeBy(1_900.milliseconds)
        syncer.requestSync()
        runCurrent()
        advanceTimeBy(1_900.milliseconds)
        runCurrent()
        assertEquals(0, api.requests.size, "still inside the window after the second write")

        advanceTimeBy(200.milliseconds)
        runCurrent()
        assertEquals(1, api.requests.size)
    }

    @Test
    fun aPullLongerThanOneSyncCarriesOnWithoutANewTrigger() = runUnitTest {
        // A long history on a new phone: more pages than one sync may send.
        val pages = MAX_REQUESTS_PER_SYNC + 5
        api.respond = { request ->
            SyncResponseDto(emptyList(), emptyList(), SyncChangesDto(), request.cursor + 1, hasMore = request.cursor + 1 < pages)
        }

        syncer.sync()
        assertEquals(MAX_REQUESTS_PER_SYNC, api.requests.size)

        advanceTimeBy(SYNC_DEBOUNCE + 100.milliseconds)
        runCurrent()
        assertEquals(pages, api.requests.size, "the rest of the pull follows on its own")

        advanceTimeBy(SYNC_DEBOUNCE * 3)
        runCurrent()
        assertEquals(pages, api.requests.size, "and stops once it has caught up")
    }
}
