package com.opson.server.data

import com.opson.server.db.DatabaseTest
import com.opson.server.db.FoodsTable
import com.opson.server.db.LogEntriesTable
import com.opson.server.domain.CustomFood
import com.opson.server.domain.DayRecord
import com.opson.server.domain.DiaryRules
import com.opson.server.domain.MealFeeling
import com.opson.server.domain.MealSlot
import com.opson.server.domain.Nutrients
import com.opson.server.domain.SavedMeal
import com.opson.server.domain.UserSettings
import com.opson.server.domain.WeighIn
import com.opson.server.testing.DiaryFixtures
import com.opson.server.testing.DiaryFixtures.DAY
import com.opson.server.testing.DiaryFixtures.T0
import com.opson.server.testing.DiaryFixtures.entry
import com.opson.server.testing.DiaryFixtures.mutation
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.junit.After
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class PostgresSyncRepositoryTest : DatabaseTest() {

    @After
    fun clean() = DiaryFixtures.cleanAll(database)

    private val repo get() = PostgresSyncRepository(database)

    private fun serverSeq(id: UUID): Long = database.blockingTransaction {
        LogEntriesTable.selectAll().where { LogEntriesTable.id eq id }.single()[LogEntriesTable.serverSeq]
    }

    @Test
    fun pushThenPullReturnsTheRow() = runTest {
        val user = seedAuthUser()
        val row = entry()
        val result = repo.sync(user, 0, listOf(mutation(row, "o1")))

        assertEquals(listOf("o1"), result.applied)
        assertEquals(listOf(row), result.changes.logEntries)
        assertTrue(result.cursor > 0)
        assertEquals(false, result.hasMore)
    }

    @Test
    fun newerWriteWins() = runTest {
        val user = seedAuthUser()
        val id = UUID.randomUUID()
        repo.sync(user, 0, listOf(mutation(entry(id, name = "Banana", updatedAt = T0))))
        val result = repo.sync(user, 0, listOf(mutation(entry(id, name = "Plantain", updatedAt = T0 + 1.minutes))))
        assertEquals("Plantain", result.changes.logEntries.single().name)
    }

    @Test
    fun olderWriteIsIgnoredAndDoesNotBumpTheCursor() = runTest {
        val user = seedAuthUser()
        val id = UUID.randomUUID()
        val first = repo.sync(user, 0, listOf(mutation(entry(id, name = "New", updatedAt = T0 + 5.minutes))))
        val seq = serverSeq(id)

        val stale = repo.sync(user, first.cursor, listOf(mutation(entry(id, name = "Old", updatedAt = T0), "stale")))

        assertEquals(listOf("stale"), stale.applied, "an ignored write is still done, so the client drops it")
        assertTrue(stale.changes.logEntries.isEmpty())
        assertEquals(first.cursor, stale.cursor)
        assertEquals(seq, serverSeq(id))
        assertEquals("New", repo.sync(user, 0, emptyList()).changes.logEntries.single().name)
    }

    @Test
    fun replayingTheSameMutationIsANoOp() = runTest {
        val user = seedAuthUser()
        val m = mutation(entry())
        val first = repo.sync(user, 0, listOf(m))
        val seq = serverSeq(m.row.let { (it as com.opson.server.domain.LogEntry).id })

        val replay = repo.sync(user, first.cursor, listOf(m))

        assertEquals(listOf(m.outboxId), replay.applied)
        assertTrue(replay.changes.logEntries.isEmpty())
        assertEquals(seq, serverSeq((m.row as com.opson.server.domain.LogEntry).id))
    }

    @Test
    fun tombstoneSyncsAsADeletedRow() = runTest {
        val user = seedAuthUser()
        val id = UUID.randomUUID()
        val created = repo.sync(user, 0, listOf(mutation(entry(id))))
        val deleted = repo.sync(user, created.cursor, listOf(mutation(entry(id, updatedAt = T0 + 1.minutes, deleted = true))))
        val row = deleted.changes.logEntries.single()
        assertEquals(id, row.id)
        assertTrue(row.deleted)
    }

    @Test
    fun pullPagesByCursorWithHasMore() = runTest {
        val user = seedAuthUser()
        val total = DiaryRules.PAGE_SIZE + 20
        (0 until total).chunked(DiaryRules.MAX_MUTATIONS).forEach { chunk ->
            repo.sync(user, Long.MAX_VALUE - 1, chunk.map { mutation(entry(name = "Item $it")) })
        }
        repo.sync(user, Long.MAX_VALUE - 1, listOf(mutation(WeighIn(UUID.randomUUID(), T0, 70.0, T0, false))))

        val page1 = repo.sync(user, 0, emptyList())
        assertEquals(DiaryRules.PAGE_SIZE, page1.changes.logEntries.size)
        assertEquals(1, page1.changes.weighIns.size)
        assertTrue(page1.hasMore)

        val page2 = repo.sync(user, page1.cursor, emptyList())
        assertEquals(20, page2.changes.logEntries.size)
        assertEquals(false, page2.hasMore)
        val seen = (page1.changes.logEntries + page2.changes.logEntries).map { it.id }.toSet()
        assertEquals(total, seen.size)

        val page3 = repo.sync(user, page2.cursor, emptyList())
        assertTrue(page3.changes.logEntries.isEmpty() && page3.changes.weighIns.isEmpty())
        assertEquals(page2.cursor, page3.cursor)
    }

    @Test
    fun usersNeverSeeEachOthersRows() = runTest {
        val alice = seedAuthUser()
        val bob = seedAuthUser()
        repo.sync(alice, 0, listOf(mutation(entry(name = "Alice's lunch"))))
        repo.sync(alice, 0, listOf(mutation(UserSettings("{\"preset\":\"blind\"}", null, T0, false))))
        repo.sync(bob, 0, listOf(mutation(entry(name = "Bob's lunch"))))

        val bobView = repo.sync(bob, 0, emptyList())
        assertEquals(listOf("Bob's lunch"), bobView.changes.logEntries.map { it.name })
        assertNull(bobView.changes.settings)
        assertEquals(listOf("Alice's lunch"), repo.sync(alice, 0, emptyList()).changes.logEntries.map { it.name })
    }

    @Test
    fun aRowIdBelongingToSomeoneElseCannotBeOverwritten() = runTest {
        val alice = seedAuthUser()
        val bob = seedAuthUser()
        val id = UUID.randomUUID()
        repo.sync(alice, 0, listOf(mutation(entry(id, name = "Alice's"))))

        repo.sync(bob, 0, listOf(mutation(entry(id, name = "Hijacked", updatedAt = T0 + 60.minutes))))

        assertEquals("Alice's", repo.sync(alice, 0, emptyList()).changes.logEntries.single().name)
        assertTrue(repo.sync(bob, 0, emptyList()).changes.logEntries.isEmpty())
    }

    @Test
    fun everyEntityRoundTrips() = runTest {
        val user = seedAuthUser()
        val day = DayRecord(DAY, 1500, T0, "good day", mapOf(MealSlot.Snack to true), true, T0, false)
        val feeling = MealFeeling(DAY, MealSlot.Lunch, "hungry", "satisfied", listOf("calm", "tired"), "ok", T0, false)
        val weighIn = WeighIn(UUID.randomUUID(), T0, 70.4, T0, false)
        val custom = CustomFood(UUID.randomUUID(), "Granola", "Home", 40.0, "g", Nutrients(kcal = 180.0, fat = 7.5), T0, false)
        val saved = SavedMeal(UUID.randomUUID(), "Usual breakfast", MealSlot.Breakfast, """[{"name":"Toast"}]""", T0, false)
        val settings = UserSettings("""{"preset":"full","overrides":{}}""", T0, T0, false)

        val result = repo.sync(user, 0, listOf(day, feeling, weighIn, custom, saved, settings).map { mutation(it) })

        assertEquals(listOf(day), result.changes.dayRecords)
        assertEquals(listOf(feeling), result.changes.mealFeelings)
        assertEquals(listOf(weighIn), result.changes.weighIns)
        assertEquals(listOf(custom), result.changes.customFoods)
        assertEquals("Usual breakfast", result.changes.savedMeals.single().name)
        assertEquals("full", Regex("\"preset\": ?\"(\\w+)\"").find(result.changes.settings!!.settingsJson)?.groupValues?.get(1))
    }

    @Test
    fun perDayRowsUpsertOnTheirNaturalKey() = runTest {
        val user = seedAuthUser()
        val first = DayRecord(DAY, 500, null, null, emptyMap(), null, T0, false)
        repo.sync(user, 0, listOf(mutation(first)))
        repo.sync(user, 0, listOf(mutation(first.copy(waterMl = 750, clientUpdatedAt = T0 + 1.minutes))))
        assertEquals(listOf(750), repo.sync(user, 0, emptyList()).changes.dayRecords.map { it.waterMl })
    }

    @Test
    fun newEntriesBumpFoodPopularityOnce() = runTest {
        val user = seedAuthUser()
        val foodId = DiaryFixtures.insertFood(database)
        val id = UUID.randomUUID()
        repo.sync(user, 0, listOf(mutation(entry(id, foodId = foodId))))
        repo.sync(user, 0, listOf(mutation(entry(id, foodId = foodId, updatedAt = T0 + 1.minutes))))
        repo.sync(user, 0, listOf(mutation(entry(foodId = foodId))))

        val popularity = database.blockingTransaction {
            FoodsTable.selectAll().where { FoodsTable.id eq foodId }.single()[FoodsTable.popularity]
        }
        assertEquals(2, popularity)
    }

    @Test
    fun unknownFoodIdIsDroppedButTheEntryKept() = runTest {
        val user = seedAuthUser()
        val result = repo.sync(user, 0, listOf(mutation(entry(name = "Mystery", foodId = UUID.randomUUID()))))
        val row = result.changes.logEntries.single()
        assertEquals("Mystery", row.name)
        assertNull(row.foodId)
    }
}
