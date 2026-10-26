package com.opson.server.data

import com.opson.server.db.BarcodeMissesTable
import com.opson.server.db.DatabaseTest
import com.opson.server.domain.BarcodeMiss
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.junit.After
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class PostgresBarcodeMissRepositoryTest : DatabaseTest() {

    private val now = Instant.parse("2026-10-26T18:30:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val repository by lazy { PostgresBarcodeMissRepository(database, clock) }

    @After
    fun cleanTables() {
        database.blockingTransaction { BarcodeMissesTable.deleteAll() }
    }

    @Test
    fun recordsEveryColumn() = runTest {
        val id = UUID.randomUUID()
        repository.record(BarcodeMiss.of(id, "049000028911", country = "us", platform = "ios", appVersion = "0.2.0"))

        val row = database.blockingTransaction { BarcodeMissesTable.selectAll().single() }
        assertEquals(id, row[BarcodeMissesTable.id])
        assertEquals("049000028911", row[BarcodeMissesTable.barcode])
        assertEquals("00049000028911", row[BarcodeMissesTable.gtin])
        assertEquals("US", row[BarcodeMissesTable.country])
        assertEquals("ios", row[BarcodeMissesTable.platform])
        assertEquals("0.2.0", row[BarcodeMissesTable.appVersion])
        assertEquals(java.time.Instant.parse("2026-10-26T18:30:00Z"), row[BarcodeMissesTable.createdAt])
    }

    @Test
    fun aRetriedReportIsOneRow() = runTest {
        val miss = BarcodeMiss.of(UUID.randomUUID(), "4006381333931", country = null, platform = "android", appVersion = null)
        repository.record(miss)
        repository.record(miss)

        assertEquals(1, database.blockingTransaction { BarcodeMissesTable.selectAll().count() })
    }

    @Test
    fun aCodeWithABadCheckDigitIsStoredWithoutAGtin() = runTest {
        repository.record(BarcodeMiss.of(UUID.randomUUID(), "4006381333932", country = "DE", platform = "ios", appVersion = "0.2.0"))

        val row = database.blockingTransaction { BarcodeMissesTable.selectAll().single() }
        assertEquals(null, row[BarcodeMissesTable.gtin])
    }
}
