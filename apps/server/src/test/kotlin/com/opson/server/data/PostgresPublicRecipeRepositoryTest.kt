package com.opson.server.data

import com.opson.server.db.DatabaseTest
import com.opson.server.domain.NewRecipeReport
import com.opson.server.domain.PublishOutcome
import com.opson.server.domain.RecipeReportReason
import com.opson.server.domain.RecipeRules
import com.opson.server.domain.UserId
import com.opson.server.testing.DiaryFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Every write to a shared recipe's row waits for its owner's diary lock, as sync's writes do: the
 * row's `server_seq` is the owner's pull cursor, and a write that skipped the lock could commit
 * below a cursor their other phone had already passed. What each write does is in
 * RecipeRoutesTest; this covers only that it waits. Real wall time: the waits are Postgres's.
 */
class PostgresPublicRecipeRepositoryTest : DatabaseTest() {

    @After
    fun clean() = DiaryFixtures.cleanAll(database)

    private val repository get() = PostgresPublicRecipeRepository(database, Clock.System)

    /** Holds [owner]'s lock while [write] starts, checks it is still waiting, then lets it finish. */
    private fun <T> assertWaitsForTheOwnersLock(owner: UserId, write: suspend () -> T): T = runBlocking {
        val held = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val holder = async(Dispatchers.IO) {
            database.transaction {
                lockUser(owner)
                held.complete(Unit)
                release.await()
            }
        }
        held.await()
        val writing = async(Dispatchers.IO) { write() }
        delay(WAIT_MS)
        val waited = !writing.isCompleted
        release.complete(Unit)
        holder.await()
        val result = writing.await()
        assertTrue(waited, "the write waited for the owner's lock")
        result
    }

    @Test
    fun everyWriteToASharedRecipeWaitsForItsOwnersLock() {
        val owner = seedAuthUser()
        val recipe = DiaryFixtures.recipe()
        val code = runBlocking { assertIs<PublishOutcome.Published>(repository.publish(owner, recipe, showName = false)).code }
        val others = List(RecipeRules.HIDE_AFTER_REPORTS) { seedAuthUser() }

        assertTrue(assertWaitsForTheOwnersLock(owner) { repository.countSave(code, others[0]) })
        for (reporter in others) {
            assertWaitsForTheOwnersLock(owner) {
                repository.report(NewRecipeReport(UUID.randomUUID(), reporter, code, RecipeReportReason.Spam, null))
            }
        }
        assertTrue(assertWaitsForTheOwnersLock(owner) { repository.dismissReports(recipe.id, "test") } > 0)
        assertTrue(assertWaitsForTheOwnersLock(owner) { repository.remove(recipe.id, "test", "Spam") })
        assertTrue(assertWaitsForTheOwnersLock(owner) { repository.restore(recipe.id, "test") })
        assertTrue(assertWaitsForTheOwnersLock(owner) { repository.unpublish(owner, recipe.id) })

        runBlocking { repository.publish(owner, recipe.copy(clientUpdatedAt = recipe.clientUpdatedAt + kotlin.time.Duration.parse("1h")), showName = false) }
        val reverted = assertWaitsForTheOwnersLock(owner) { RecipeReverter(database, Clock.System).revert(owner, "test", "spam", null) }
        assertFalse(reverted.undone == 0, "the shared recipe came down")
    }

    private companion object {
        const val WAIT_MS = 400L
    }
}
