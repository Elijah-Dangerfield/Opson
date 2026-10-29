package com.opson.libraries.diary.impl

import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.DayMark
import com.opson.libraries.diary.Energy
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.db.DayRecordEntity
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** What each stored day says about showing up, read from the dates with entries and the day records. */
class DayMarksImplTest {

    private fun record(
        day: String,
        closed: Boolean = false,
        untracked: Boolean = false,
        ate: Set<MealSlot> = emptySet(),
        checkIn: CheckIn? = null,
        water: Int? = null,
    ) = DayRecordEntity(
        day = day,
        waterMl = water,
        closedAtEpochMs = if (closed) 1L else null,
        note = null,
        ateNoDetailsJson = ate.toAteJson(),
        mealGroups = null,
        clientUpdatedAtEpochMs = 0L,
        deleted = false,
        untracked = untracked,
        checkIn = checkIn.toCheckInJson(),
    )

    @Test
    fun eachSignal_marksItsDay_inDateOrder() {
        val marks = DayMarksImpl.marks(
            withEntries = listOf("2026-10-03", "2026-10-01"),
            records = listOf(
                record("2026-10-01", closed = true),
                record("2026-10-02", ate = setOf(MealSlot.Lunch)),
                record("2026-10-04", checkIn = CheckIn(energy = Energy.Ok)),
                record("2026-10-05", untracked = true),
            ),
        )
        assertEquals(
            listOf(
                DayMark(LocalDate(2026, 10, 1), logged = true, closed = true),
                DayMark(LocalDate(2026, 10, 2), logged = true),
                DayMark(LocalDate(2026, 10, 3), logged = true),
                DayMark(LocalDate(2026, 10, 4), checkedIn = true),
                DayMark(LocalDate(2026, 10, 5), untracked = true),
            ),
            marks,
        )
    }

    @Test
    fun aGlassOfWater_isLogging() {
        assertEquals(
            listOf(DayMark(LocalDate(2026, 10, 1), logged = true)),
            DayMarksImpl.marks(emptyList(), listOf(record("2026-10-01", water = 250))),
        )
    }

    @Test
    fun aRecordWithNothingOnIt_isLeftOut() {
        assertEquals(emptyList(), DayMarksImpl.marks(emptyList(), listOf(record("2026-10-01", water = 0))))
    }
}
