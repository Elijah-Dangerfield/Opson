package com.opson.libraries.diary.impl

import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * An entry's micros through Room and the sync wire: a 0 the food listed comes back 0, a key it
 * never had stays missing, and an entry with no micros at all stays null. Collapsing either into
 * 0 would turn "Not listed" into "none" on every screen.
 */
class MicrosStorageTest {

    private fun item(micros: Map<String, Double>?) = LogItem(
        id = "e1",
        day = LocalDate(2026, 10, 25),
        slot = MealSlot.Lunch,
        loggedAt = Instant.fromEpochMilliseconds(1_790_000_000_000),
        name = "Sparkling water",
        precision = Precision.Estimate,
        method = LogMethod.Search,
        micros = micros,
    )

    @Test
    fun aListedZeroAndAMissingKey_surviveRoom() {
        val stored = item(mapOf("sodium_mg" to 0.0, "calcium_mg" to 12.0)).toEntity(updatedAtEpochMs = 1).toDomain()
        assertEquals(mapOf("sodium_mg" to 0.0, "calcium_mg" to 12.0), stored?.micros)
        assertNull(stored?.micros?.get("vitamin_d_ug"))
    }

    @Test
    fun aListedZeroAndAMissingKey_surviveTheSyncWire() {
        val pulled = item(mapOf("sodium_mg" to 0.0)).toEntity(updatedAtEpochMs = 1).toRow().toEntity().toDomain()
        assertEquals(mapOf("sodium_mg" to 0.0), pulled?.micros)
    }

    @Test
    fun noMicrosAtAll_staysNull_notEmptyNotZeros() {
        assertNull(item(null).toEntity(updatedAtEpochMs = 1).toDomain()?.micros)
        assertNull(item(null).toEntity(updatedAtEpochMs = 1).toRow().toEntity().toDomain()?.micros)
    }
}
