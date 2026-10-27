package com.opson.libraries.diary.impl

import com.opson.libraries.core.logging.EXTRA_APP_EVENT
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogId
import com.opson.libraries.core.logging.LogTree
import com.opson.libraries.diary.ExerciseDraft
import com.opson.libraries.diary.SyncEntity
import com.opson.libraries.diary.impl.wire.ExerciseRowDto
import com.opson.libraries.diary.impl.wire.WireJson
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.ExerciseActivities
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * [ExerciseRepositoryImpl] over the in-memory database: every write lands with its outbox row,
 * an edit keeps the row it replaced as the base a refusal restores, a delete is a tombstone that
 * syncs, a bad draft never reaches the outbox, and the app events carry no health data.
 */
class ExerciseRepositoryImplTest : CoroutineTest() {
    private val db = FakeDiaryDb()
    private val clock = FakeClock(Instant.parse("2026-10-25T18:00:00Z"))
    private val scheduler = FakeScheduler()
    private val repo = ExerciseRepositoryImpl(db.tables, DiaryWriter(db.tables, clock, scheduler))
    private val events = ExerciseEventTree()

    private val day = LocalDate(2026, 10, 25)
    private val walk = ExerciseDraft(day, Instant.parse("2026-10-25T07:30:00Z"), "walking", minutes = 30, kcal = 98)

    @BeforeTest
    fun plant() = KLog.plant(events)

    @AfterTest
    fun uproot() = KLog.uproot(events)

    @Test
    fun add_storesTheEntryAndQueuesItForTheDay() = runUnitTest {
        val entry = repo.add(walk)

        assertEquals(listOf(entry), repo.observeDay(day).first())
        val outbox = db.outbox.rows.single()
        assertEquals(SyncEntity.Exercise.wire, outbox.entity)
        assertEquals(entry.id, outbox.entityId)
        assertEquals("2026-10-25", outbox.day, "a planned day waits until the server takes it")
        assertNull(outbox.basePayload)
        val row = WireJson.decodeFromString(ExerciseRowDto.serializer(), outbox.payload)
        assertEquals(98, row.kcal)
        assertEquals(30, row.minutes)
        assertEquals(1, scheduler.requests)
    }

    @Test
    fun update_keepsTheFirstBase_andDeleteIsATombstone() = runUnitTest {
        val entry = repo.add(walk)
        db.outbox.clear() // synced
        val synced = db.exercises.state.value.getValue(entry.id)

        repo.update(entry.id, walk.copy(minutes = 45, kcal = 147))
        repo.update(entry.id, walk.copy(minutes = 50, kcal = 163))

        assertEquals(50, repo.get(entry.id)?.minutes)
        val outbox = db.outbox.rows.single()
        assertEquals(synced, WireJson.decodeFromString(ExerciseRowDto.serializer(), outbox.basePayload!!).toEntity())

        repo.delete(entry.id)

        assertTrue(repo.observeDay(day).first().isEmpty())
        assertNull(repo.get(entry.id))
        assertTrue(db.exercises.state.value.getValue(entry.id).deleted, "kept as a tombstone so the delete syncs")
        assertTrue(WireJson.decodeFromString(ExerciseRowDto.serializer(), db.outbox.rows.single().payload).deleted)
    }

    @Test
    fun aStampIsAlwaysNewerThanTheLastOne_evenInTheSameMillisecond() = runUnitTest {
        val entry = repo.add(walk)
        val first = db.exercises.state.value.getValue(entry.id).clientUpdatedAtEpochMs

        repo.update(entry.id, walk.copy(minutes = 31))

        assertTrue(db.exercises.state.value.getValue(entry.id).clientUpdatedAtEpochMs > first)
    }

    @Test
    fun aDraftTheServerWouldRefuse_isNeverWritten() = runUnitTest {
        assertFailsWith<IllegalArgumentException> { repo.add(walk.copy(minutes = 0)) }
        assertFailsWith<IllegalArgumentException> { repo.add(walk.copy(kcal = 9_000)) }
        assertFailsWith<IllegalArgumentException> { repo.add(walk.copy(activity = "underwater_basket_weaving")) }
        assertFailsWith<IllegalArgumentException> { repo.add(walk.copy(activity = ExerciseActivities.OTHER, name = " ")) }
        assertTrue(db.exercises.rows.isEmpty())
        assertTrue(db.outbox.rows.isEmpty())
    }

    @Test
    fun other_keepsItsTrimmedName() = runUnitTest {
        val entry = repo.add(walk.copy(activity = ExerciseActivities.OTHER, name = "  Canoe polo ", kcal = 300, kcalTyped = true))

        assertEquals("Canoe polo", entry.name)
        assertTrue(entry.isOther)
    }

    @Test
    fun events_sayOnlyWhetherListedAndTyped() = runUnitTest {
        val entry = repo.add(walk)
        repo.update(entry.id, walk.copy(activity = ExerciseActivities.OTHER, name = "Canoe polo", kcalTyped = true))
        repo.delete(entry.id)

        assertEquals(listOf(mapOf<String, Any?>("listed" to "true", "typed" to "false")), events.named("exercise.logged"))
        assertEquals(listOf(mapOf<String, Any?>("listed" to "false", "typed" to "true")), events.named("exercise.edited"))
        assertEquals(1, events.named("exercise.deleted").size)
    }
}

private class ExerciseEventTree : LogTree() {
    private val entries = mutableListOf<LogEntry>()

    override fun log(entry: LogEntry): LogId? {
        if (entry.context.extras.containsKey(EXTRA_APP_EVENT)) entries += entry
        return null
    }

    fun named(name: String): List<Map<String, Any?>> =
        entries.filter { it.context.extras[EXTRA_APP_EVENT] == name }.map { it.context.extras - EXTRA_APP_EVENT }
}
