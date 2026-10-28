package com.opson.server.data

import com.opson.server.db.DatabaseTest
import com.opson.server.domain.CustomFood
import com.opson.server.domain.DayRecord
import com.opson.server.domain.DiaryRow
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
import org.junit.After
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

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
}
