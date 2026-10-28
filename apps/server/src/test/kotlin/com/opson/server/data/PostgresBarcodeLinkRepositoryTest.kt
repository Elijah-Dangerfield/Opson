package com.opson.server.data

import com.opson.server.db.DatabaseTest
import com.opson.server.db.FoodsTable
import com.opson.server.domain.LinkOutcome
import com.opson.server.domain.LinkStatus
import com.opson.server.testing.DiaryFixtures
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.After
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class PostgresBarcodeLinkRepositoryTest : DatabaseTest() {

    @After
    fun clean() = DiaryFixtures.cleanAll(database)

    private var now = Instant.parse("2026-10-27T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val repo get() = PostgresBarcodeLinkRepository(database, clock)
    private val catalog get() = PostgresFoodCatalogRepository(database, clock)

    private fun seed(name: String, barcode: String? = null, reviewStatus: String = "approved"): UUID {
        val id = UUID.randomUUID()
        database.blockingTransaction {
            FoodsTable.insert {
                it[FoodsTable.id] = id
                it[sourceKind] = if (reviewStatus == "approved") "usda_branded" else "user_submitted"
                it[sourceId] = id.toString()
                it[FoodsTable.name] = name
                it[FoodsTable.barcode] = barcode
                it[micros] = "{}"
                it[portions] = "[]"
            }
            if (reviewStatus != "approved") {
                TransactionManager.current().exec("UPDATE foods SET review_status = '$reviewStatus' WHERE id = '$id'")
            }
        }
        return id
    }

    @Test
    fun pairingsAreCountedPerBarcodeAndFood() = runTest {
        val oats = seed("Rolled oats")
        assertTrue(repo.record("012345678905", oats))
        now += 5.minutes
        assertTrue(repo.record("0012345678905", oats))

        val link = repo.list(LinkStatus.Pending, 10).single()
        assertEquals("00012345678905", link.gtin)
        assertEquals("012345678905", link.barcode, "the code as first scanned")
        assertEquals(2, link.pairings)
        assertEquals("Rolled oats", link.foodName)
        assertEquals(Instant.parse("2026-10-27T12:05:00Z"), link.lastAt)
    }

    @Test
    fun onlyAnApprovedFoodWithoutThatCodeIsSuggested() = runTest {
        val pending = seed("Someone's granola", reviewStatus = "pending")
        val already = seed("Oats", barcode = "00012345678905")
        assertFalse(repo.record("012345678905", pending))
        assertFalse(repo.record("012345678901", already), "a code with a bad check digit has no GTIN")
        assertTrue(repo.record("012345678905", already))
        assertFalse(repo.record("012345678905", UUID.randomUUID()))
        assertEquals(emptyList(), repo.list(LinkStatus.Pending, 10))
    }

    @Test
    fun attachingGivesTheFoodTheBarcodeForEveryone() = runTest {
        val oats = seed("Rolled oats")
        repo.record("012345678905", oats)

        assertEquals(LinkOutcome.Done, repo.attach("00012345678905", oats))

        assertEquals(oats, catalog.findByBarcode("012345678905")?.id)
        assertEquals(LinkStatus.Attached, repo.list(LinkStatus.Attached, 10).single().status)
        assertEquals(LinkOutcome.NotFound, repo.attach("00012345678905", oats), "decided once")
    }

    @Test
    fun attachingRefusesACodeAnotherFoodHasOrAFoodThatHasOne() = runTest {
        val oats = seed("Rolled oats")
        val other = seed("Other oats", barcode = "00012345678905")
        val coded = seed("Coded oats", barcode = "04006381333931")
        repo.record("012345678905", oats)
        repo.record("012345678912", coded)

        assertEquals(LinkOutcome.BarcodeTaken(other), repo.attach("00012345678905", oats))
        assertEquals(LinkOutcome.FoodHasBarcode, repo.attach("00012345678912", coded))
    }

    @Test
    fun dismissingTakesItOffTheQueue() = runTest {
        val oats = seed("Rolled oats")
        repo.record("012345678905", oats)

        assertEquals(LinkOutcome.Done, repo.dismiss("00012345678905", oats))

        assertEquals(emptyList(), repo.list(LinkStatus.Pending, 10))
        // Later pairings still count, for the record, but do not reopen it.
        repo.record("012345678905", oats)
        assertEquals(2, repo.list(LinkStatus.Dismissed, 10).single().pairings)
    }
}
