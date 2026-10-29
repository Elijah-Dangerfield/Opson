package com.opson.libraries.diary

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class RepeatsTest {
    private val rule = "6f1c2b8e-3d4a-4e6b-9c1d-2a3b4c5d6e7f"
    private val sunday = LocalDate(2026, 10, 25)

    @Test
    fun anEntryIdIsTheSameEveryTimeItIsAskedFor() {
        assertEquals(repeatEntryId(rule, sunday, 0), repeatEntryId(rule, sunday, 0))
    }

    @Test
    fun anEntryIdDiffersByRuleDayAndPosition() {
        val ids = setOf(
            repeatEntryId(rule, sunday, 0),
            repeatEntryId(rule, sunday, 1),
            repeatEntryId(rule, LocalDate(2026, 10, 26), 0),
            repeatEntryId("0b9e2f0c-8a1d-4c3e-b5f6-7a8b9c0d1e2f", sunday, 0),
        )
        assertEquals(4, ids.size)
    }

    /** The server parses ids with `UUID.fromString` and refuses any spelling but the canonical lowercase one. */
    @Test
    fun anEntryIdIsACanonicalVersion8Uuid() {
        repeat(50) { index ->
            val id = repeatEntryId(rule, sunday, index)
            assertEquals(id, Uuid.parse(id).toString())
            assertEquals(id.lowercase(), id)
            assertEquals('8', id[14], "version nibble")
            assertTrue(id[19] in "89ab", "RFC variant")
        }
    }

    @Test
    fun aYearOfOneRulesIdsDoNotCollide() {
        val ids = (0 until 366).flatMap { day -> (0 until 5).map { repeatEntryId(rule, LocalDate.fromEpochDays(sunday.toEpochDays() + day), it) } }
        assertEquals(ids.size, ids.toSet().size)
        assertNotEquals(repeatEntryId(rule, sunday, 0), repeatEntryId(rule.uppercase(), sunday, 0))
    }

    @Test
    fun aPlanFallsOnItsDaysBetweenItsStartAndEnd() {
        val plan = plan(days = RepeatDays.Weekdays, start = LocalDate(2026, 10, 26), end = LocalDate(2026, 11, 6))

        assertFalse(plan.fallsOn(sunday), "before the start")
        assertTrue(plan.fallsOn(LocalDate(2026, 10, 26)))
        assertFalse(plan.fallsOn(LocalDate(2026, 10, 31)), "a Saturday")
        assertTrue(plan.fallsOn(LocalDate(2026, 11, 6)), "the end day is included")
        assertFalse(plan.fallsOn(LocalDate(2026, 11, 9)), "after the end")
    }

    @Test
    fun aPausedRuleIsNeverDue() {
        assertTrue(RepeatRule("r", plan()).isDue(sunday))
        assertFalse(RepeatRule("r", plan(), paused = true).isDue(sunday))
    }

    @Test
    fun theNextDayAPlanLogs() {
        assertEquals(sunday, plan().nextFrom(sunday))
        assertEquals(LocalDate(2026, 10, 26), plan(days = RepeatDays.Weekdays).nextFrom(sunday))
        assertEquals(LocalDate(2026, 10, 30), plan(days = setOf(DayOfWeek.FRIDAY)).nextFrom(sunday))
        assertEquals(LocalDate(2026, 11, 2), plan(start = LocalDate(2026, 11, 2)).nextFrom(sunday))
        assertNull(plan(end = LocalDate(2026, 10, 24)).nextFrom(sunday))
        assertNull(plan(days = setOf(DayOfWeek.FRIDAY), end = LocalDate(2026, 10, 29)).nextFrom(sunday))
        assertNull(plan(days = emptySet()).nextFrom(sunday))
    }

    private fun plan(
        days: Set<DayOfWeek> = RepeatDays.EveryDay,
        start: LocalDate = sunday,
        end: LocalDate? = null,
    ) = RepeatPlan(
        name = "Oat porridge",
        items = listOf(LogDraft(name = "Oat porridge", precision = Precision.Estimate)),
        slot = MealSlot.Breakfast,
        days = days,
        startDay = start,
        endDay = end,
    )
}
