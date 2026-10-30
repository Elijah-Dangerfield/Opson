package com.opson.libraries.diary.impl

import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.RecipeDraft
import com.opson.libraries.diary.RecipeShareState
import com.opson.libraries.diary.RecipeSharing
import com.opson.libraries.diary.SyncEntity
import com.opson.libraries.diary.impl.wire.RecipeRowDto
import com.opson.libraries.diary.impl.wire.WireJson
import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * [RecipeRepositoryImpl] over the in-memory database: a recipe and its outbox row land together,
 * an edit keeps the sharing the server last gave it, and a delete is a tombstone that syncs.
 */
class RecipeRepositoryImplTest : CoroutineTest() {
    private val db = FakeDiaryDb()
    private val clock = FakeClock(Instant.parse("2026-10-25T18:00:00Z"))
    private val scheduler = FakeScheduler()
    private val repo = RecipeRepositoryImpl(db.tables, DiaryWriter(db.tables, clock, scheduler))

    private val beans = LogDraft(name = "Black beans", precision = Precision.Weighed, grams = 800.0, kcal = 728.0)
    private val draft = RecipeDraft(name = "  Chili ", servings = 6.0, ingredients = listOf(beans), notes = " ")

    @Test
    fun create_storesItAndQueuesIt() = runUnitTest {
        val recipe = repo.create(draft)

        assertEquals("Chili", recipe.name)
        assertNull(recipe.notes, "blank notes are none")
        assertEquals(listOf(recipe), repo.observeAll().first())
        val queued = db.outbox.rows.single()
        assertEquals(SyncEntity.Recipe.wire, queued.entity)
        val row = WireJson.decodeFromString(RecipeRowDto.serializer(), queued.payload)
        assertEquals(6.0, row.servings)
        assertEquals(1, row.items.size)
        assertNull(queued.basePayload)
        assertTrue(scheduler.requests > 0)
    }

    @Test
    fun anEditKeepsTheSharing_andQueuesTheOldRowAsItsBase() = runUnitTest {
        val recipe = repo.create(draft)
        db.outbox.clear()
        repo.markShared(recipe.id, RecipeSharing("abcd2345", RecipeShareState.Live, showName = true))

        repo.update(recipe.id, draft.copy(name = "Chili for six"))

        val edited = repo.get(recipe.id)!!
        assertEquals("Chili for six", edited.name)
        assertEquals(RecipeSharing("abcd2345", RecipeShareState.Live, showName = true), edited.sharing)
        assertEquals(edited, repo.byShareCode("abcd2345"))
        val queued = db.outbox.rows.single()
        assertEquals("Chili", WireJson.decodeFromString(RecipeRowDto.serializer(), queued.basePayload!!).name)
    }

    @Test
    fun markShared_queuesNothing() = runUnitTest {
        val recipe = repo.create(draft)
        db.outbox.clear()
        repo.markShared(recipe.id, RecipeSharing("abcd2345", RecipeShareState.Live, showName = false))
        assertTrue(db.outbox.rows.isEmpty())
    }

    @Test
    fun delete_isATombstoneThatSyncs() = runUnitTest {
        val recipe = repo.create(draft)
        db.outbox.clear()

        repo.delete(recipe.id)

        assertTrue(repo.observeAll().first().isEmpty())
        assertNull(repo.get(recipe.id))
        assertTrue(WireJson.decodeFromString(RecipeRowDto.serializer(), db.outbox.rows.single().payload).deleted)
    }

    @Test
    fun aRecipeOutsideTheServersLimitsIsRefusedHere() = runUnitTest {
        assertFailsWith<IllegalArgumentException> { repo.create(draft.copy(name = " ")) }
        assertFailsWith<IllegalArgumentException> { repo.create(draft.copy(servings = 0.0)) }
        assertTrue(db.outbox.rows.isEmpty())
        val many = repo.create(draft.copy(ingredients = List(80) { beans }, savedFrom = "not a code"))
        assertEquals(60, many.ingredients.size)
        assertNull(many.savedFrom)
    }
}
