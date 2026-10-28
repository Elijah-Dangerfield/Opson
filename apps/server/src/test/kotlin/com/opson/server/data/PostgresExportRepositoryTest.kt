package com.opson.server.data

import com.opson.server.db.DatabaseTest
import com.opson.server.domain.DayRecord
import com.opson.server.domain.ExportRecord
import com.opson.server.domain.WeighIn
import com.opson.server.testing.DiaryFixtures
import com.opson.server.testing.DiaryFixtures.DAY
import com.opson.server.testing.DiaryFixtures.T0
import com.opson.server.testing.DiaryFixtures.entry
import com.opson.server.testing.DiaryFixtures.mutation
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.After
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class PostgresExportRepositoryTest : DatabaseTest() {

    @After
    fun clean() = DiaryFixtures.cleanAll(database)

    @Test
    fun anExportHoldsOnlyTheCallersLiveRows() = runTest {
        val alice = seedAuthUser()
        val bob = seedAuthUser()
        val sync = PostgresSyncRepository(database)
        sync.sync(
            alice,
            0,
            listOf(
                entry(name = "Alice's porridge"),
                entry(name = "Alice's deleted", deleted = true),
                WeighIn(UUID.randomUUID(), T0, 61.5, T0, false),
                DayRecord(DAY, 1200, null, null, emptyMap(), null, T0, false),
            ).map { mutation(it) },
        )
        sync.sync(bob, 0, listOf(entry(name = "Bob's lunch"), WeighIn(UUID.randomUUID(), T0, 90.0, T0, false)).map { mutation(it) })

        val exported = mutableListOf<ExportRecord>()
        PostgresExportRepository(database).export(alice) { exported += it }

        assertEquals(listOf("Alice's porridge"), exported.filterIsInstance<ExportRecord.Entry>().map { it.name })
        assertEquals(listOf(61.5), exported.filterIsInstance<ExportRecord.Weight>().map { it.kg })
        assertEquals(listOf(1200), exported.filterIsInstance<ExportRecord.Water>().map { it.waterMl })
    }

    @Test
    fun aLongHistoryComesOutWholeAndInOrder_withNoTransactionOpenWhileTheResponseIsWritten() = runTest {
        val user = seedAuthUser()
        val sync = PostgresSyncRepository(database)
        // More than two pages, several entries sharing a day and a time, so every part of the key matters.
        val total = PostgresExportRepository.PAGE_SIZE * 2 + 30
        val entries = (0 until total).map { i ->
            entry(name = "Item $i", day = DAY.plusDays((i / 40).toLong())).copy(loggedAt = T0 + (i % 3).minutes)
        }
        entries.chunked(500).forEach { chunk -> sync.sync(user, 0, chunk.map { mutation(it) }) }

        val exported = mutableListOf<ExportRecord.Entry>()
        PostgresExportRepository(database).export(user) { record ->
            // The sink writes to the client; a slow reader must not be holding a pooled connection.
            assertNull(TransactionManager.currentOrNull(), "the sink ran inside a transaction")
            if (record is ExportRecord.Entry) exported += record
        }

        assertEquals(total, exported.size)
        assertEquals(total, exported.map { it.name }.toSet().size)
        assertEquals(exported.sortedWith(compareBy({ it.day }, { it.loggedAt })), exported)
    }
}
