package com.opson.server.data

import com.opson.server.db.DatabaseTest
import com.opson.server.db.ProfilesTable
import com.opson.server.domain.UpdateProfileOutcome
import com.opson.server.domain.UserId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.deleteAll
import org.junit.After
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Reference repository test: real Postgres via [DatabaseTest], `@After` table
 * cleanup for isolation, a `repo()` factory wiring the SUT with an injected
 * clock, and the create / idempotent-replay / unique-conflict / not-found cases.
 *
 * Each profile references `auth.users` (V2 FK), so tests seed an auth user with
 * `seedAuthUser()` before creating a profile.
 */
@OptIn(ExperimentalTime::class)
class PostgresProfileRepositoryTest : DatabaseTest() {

    @After
    fun cleanTables() {
        database.blockingTransaction { ProfilesTable.deleteAll() }
    }

    private fun repo(clock: Clock = Clock.System) = PostgresProfileRepository(database, clock)

    @Test
    fun findOrCreate_createsThenReturnsSameRow() = runTest {
        val userId = seedAuthUser()

        val created = repo().findOrCreate(userId)
        assertEquals(userId, created.userId)
        assertTrue(created.displayName.isNotBlank())
        assertEquals(created.createdAt, created.updatedAt)

        val again = repo().findOrCreate(userId)
        assertEquals(created.displayName, again.displayName)
        assertEquals(created.createdAt, again.createdAt)
    }

    @Test
    fun updateDisplayName_succeeds() = runTest {
        val userId = seedAuthUser()
        repo().findOrCreate(userId)

        val outcome = repo().updateDisplayName(userId, "Ada")

        assertTrue(outcome is UpdateProfileOutcome.Success)
        assertEquals("Ada", (outcome as UpdateProfileOutcome.Success).profile.displayName)
    }

    @Test
    fun updateDisplayName_takenReturnsConflict() = runTest {
        val a = seedAuthUser()
        val b = seedAuthUser()
        repo().findOrCreate(a)
        repo().findOrCreate(b)
        repo().updateDisplayName(a, "Ada")

        val outcome = repo().updateDisplayName(b, "Ada")

        assertEquals(UpdateProfileOutcome.DisplayNameTaken, outcome)
    }

    @Test
    fun updateDisplayName_unknownUserReturnsNotFound() = runTest {
        // No profile row, so no FK seeding needed — this exercises the 0-rows path.
        val outcome = repo().updateDisplayName(UserId(UUID.randomUUID()), "ghost")

        assertEquals(UpdateProfileOutcome.NotFound, outcome)
    }
}
