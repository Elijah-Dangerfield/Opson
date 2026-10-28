package com.opson.server.data

import com.opson.server.db.DatabaseTest
import com.opson.server.domain.CustomFood
import com.opson.server.domain.DayRecord
import com.opson.server.domain.DiaryRow
import com.opson.server.domain.DiaryRules
import com.opson.server.domain.Exercise
import com.opson.server.domain.Fast
import com.opson.server.domain.LogEntry
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
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.After
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class PostgresExportRepositoryTest : DatabaseTest() {

    @After
    fun clean() = DiaryFixtures.cleanAll(database)

    @Test
    fun anExportHoldsEveryKindOfTheCallersLiveRows_inTheOrderPromised() = runTest {
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
                DayRecord(DAY, 1200, null, "Slept badly", emptyMap(), null, T0, false, checkIn = """{"sleep":"poor"}"""),
                MealFeeling(DAY, MealSlot.Lunch, "hungry", null, emptyList(), "Late lunch", T0, false),
                Exercise(UUID.randomUUID(), DAY, T0, "walking", null, 40, 120, false, false, T0, false),
                Fast(DAY, T0, T0 + 12.hours, 12, T0, false),
                CustomFood(UUID.randomUUID(), "Oat bar", null, 1.0, "bar", Nutrients(kcal = 190.0), T0, false),
                SavedMeal(UUID.randomUUID(), "Packed lunch", MealSlot.Lunch, "[]", T0, false),
                UserSettings("""{"weightUnit":"kg"}""", null, T0, false),
            ).map { mutation(it) },
            pullFasts = true,
            pullExercises = true,
        )
        sync.sync(bob, 0, listOf(entry(name = "Bob's lunch"), WeighIn(UUID.randomUUID(), T0, 90.0, T0, false)).map { mutation(it) })

        val exported = mutableListOf<DiaryRow>()
        PostgresExportRepository(database).export(alice) { exported += it }

        assertEquals(
            listOf(
                LogEntry::class, WeighIn::class, DayRecord::class, MealFeeling::class, Exercise::class, Fast::class,
                CustomFood::class, SavedMeal::class, UserSettings::class,
            ),
            exported.map { it::class },
        )
        assertEquals(listOf("Alice's porridge"), exported.filterIsInstance<LogEntry>().map { it.name })
        assertEquals(listOf(61.5), exported.filterIsInstance<WeighIn>().map { it.kg })
        assertEquals("""{"sleep":"poor"}""", exported.filterIsInstance<DayRecord>().single().checkIn)
        assertEquals("Late lunch", exported.filterIsInstance<MealFeeling>().single().note)
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

        val exported = mutableListOf<LogEntry>()
        PostgresExportRepository(database).export(user) { row ->
            // The sink writes to the client; a slow reader must not be holding a pooled connection.
            assertNull(TransactionManager.currentOrNull(), "the sink ran inside a transaction")
            if (row is LogEntry) exported += row
        }

        assertEquals(total, exported.size)
        assertEquals(total, exported.map { it.name }.toSet().size)
        assertEquals(exported.sortedWith(compareBy({ it.day }, { it.loggedAt })), exported)
    }

    @Test
    fun everyOtherKindPagesWholeAndInOrder_withNoTransactionOpenWhileTheResponseIsWritten() = runTest {
        val user = seedAuthUser()
        val sync = PostgresSyncRepository(database)
        // Past a page of every kind that can have more than one row. Where the key has a second
        // column, the first is shared by several rows, so a page boundary falls inside a tie.
        val n = PostgresExportRepository.PAGE_SIZE + 12
        val days = (0 until n).map { DAY.minusDays(it.toLong()) }
        val rows: List<DiaryRow> =
            (0 until n).map { i -> WeighIn(UUID.randomUUID(), T0 + (i % 3).minutes, 60.0 + i / 100.0, T0, false) } +
                days.map { DayRecord(it, 1000, null, null, emptyMap(), null, T0, false) } +
                (0 until n).map { i -> MealFeeling(days[i / 4], MealSlot.entries[i % 4], null, null, emptyList(), "Note $i", T0, false) } +
                (0 until n).map { i -> Exercise(UUID.randomUUID(), DAY, T0 + (i % 3).minutes, "walking", "Walk $i", 30, 100, false, false, T0, false) } +
                days.map { Fast(it, T0, null, 12, T0, false) } +
                (0 until n).map { i -> CustomFood(UUID.randomUUID(), "Food ${i % 7}", null, 1.0, "bar", Nutrients(kcal = 100.0), T0, false) } +
                (0 until n).map { i -> SavedMeal(UUID.randomUUID(), "Meal ${i % 7}", null, "[]", T0, false) }
        rows.chunked(DiaryRules.MAX_MUTATIONS).forEach { chunk ->
            sync.sync(user, 0, chunk.map { mutation(it) }, pullFasts = true, pullExercises = true)
        }

        val exported = mutableListOf<DiaryRow>()
        PostgresExportRepository(database).export(user) { row ->
            assertNull(TransactionManager.currentOrNull(), "the sink ran inside a transaction")
            exported += row
        }

        assertEquals(
            listOf(WeighIn::class, DayRecord::class, MealFeeling::class, Exercise::class, Fast::class, CustomFood::class, SavedMeal::class),
            exported.map { it::class }.distinct(),
        )
        fun <T : DiaryRow> assertWholeAndInOrder(kind: String, rows: List<T>, identity: (T) -> Any, order: Comparator<T>) {
            assertEquals(n, rows.size, "$kind: rows")
            assertEquals(n, rows.map(identity).toSet().size, "$kind: distinct rows")
            assertEquals(rows.sortedWith(order), rows, "$kind: order")
        }
        assertWholeAndInOrder("weigh-ins", exported.filterIsInstance<WeighIn>(), { it.id }, compareBy { it.measuredAt })
        assertWholeAndInOrder("day records", exported.filterIsInstance<DayRecord>(), { it.day }, compareBy { it.day })
        assertWholeAndInOrder("meal notes", exported.filterIsInstance<MealFeeling>(), { it.day to it.slot }, compareBy({ it.day }, { it.slot.wire }))
        assertWholeAndInOrder("exercise", exported.filterIsInstance<Exercise>(), { it.id }, compareBy { it.startedAt })
        assertWholeAndInOrder("fasts", exported.filterIsInstance<Fast>(), { it.day }, compareBy { it.day })
        assertWholeAndInOrder("custom foods", exported.filterIsInstance<CustomFood>(), { it.id }, compareBy { it.name })
        assertWholeAndInOrder("saved meals", exported.filterIsInstance<SavedMeal>(), { it.id }, compareBy { it.name })
    }
}
