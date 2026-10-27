package com.opson.libraries.storage.impl

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogId
import com.opson.libraries.core.logging.LogLevel
import com.opson.libraries.core.logging.LogTree
import com.opson.libraries.opson.storage.db.ClearableDao
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserScopedDaoCleanerTest {

    private val logs = RecordingLogTree().also(KLog::plant)

    @AfterTest
    fun tearDown() {
        KLog.clearTrees()
    }

    @Test
    fun oneFailingDao_doesNotStopTheRest() = runTest {
        val cleared = FakeDao()
        val cleaner = UserScopedDaoCleaner(setOf(FailingDao(), cleared))

        cleaner.clear(previousUserId = "user-a")

        assertTrue(cleared.wasCleared)
    }

    /**
     * A failed wipe leaves the previous user's rows for the next one to see,
     * which is the leak this class exists to prevent. Error is the level that
     * reaches Sentry as an event; warn would only be a breadcrumb.
     */
    @Test
    fun aFailedWipe_isLoggedAsAnError() = runTest {
        val cleaner = UserScopedDaoCleaner(setOf(FailingDao()))

        cleaner.clear(previousUserId = "user-a")

        assertEquals(listOf(LogLevel.Error), logs.entries.map { it.level })
    }

    private class FakeDao : ClearableDao {
        var wasCleared = false
        override suspend fun deleteAll() {
            wasCleared = true
        }
    }

    private class FailingDao : ClearableDao {
        override suspend fun deleteAll() = throw IllegalStateException("database is locked")
    }

    private class RecordingLogTree : LogTree() {
        val entries = mutableListOf<LogEntry>()
        override fun log(entry: LogEntry): LogId? {
            entries += entry
            return null
        }
    }
}
