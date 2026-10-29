package com.opson.libraries.prompts.impl

import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.CheckInMood
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.prompts.impl.candidates.CheckInPrompt
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckInPromptTest {

    private fun day(n: Int, logged: Boolean = true, untracked: Boolean = false, checkIn: CheckIn? = null) = Day(
        date = LocalDate(2026, 10, n),
        ateNoDetails = if (logged) setOf(MealSlot.Lunch) else emptySet(),
        untracked = untracked,
        checkIn = checkIn,
    )

    @Test
    fun `offered after three logged days with no check-in`() {
        assertTrue(CheckInPrompt.worthOffering(listOf(day(1), day(2), day(3), day(4, logged = false))))
    }

    @Test
    fun `not before a few days of logging and untracked days do not count`() {
        assertFalse(CheckInPrompt.worthOffering(listOf(day(1), day(2), day(3, logged = false))))
        assertFalse(CheckInPrompt.worthOffering(listOf(day(1), day(2), day(3, untracked = true))))
    }

    @Test
    fun `never for someone already checking in`() {
        val checkedIn = CheckIn(moods = setOf(CheckInMood.entries.first()))
        assertFalse(CheckInPrompt.worthOffering(listOf(day(1), day(2), day(3), day(4, logged = false, checkIn = checkedIn))))
    }
}
