package com.opson.libraries.diary

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Totals and the [Day] helpers screens build on. The rule under test above
 * all: an unknown amount is unknown, never zero, so no screen can show a total
 * that is not one.
 *
 * Not covered: how a [Day] is assembled from the database, which is the
 * repository's (`DiaryRepositoryImplTest`).
 */
class DayTest {
    private val date = LocalDate(2026, 10, 25)

    @Test
    fun anEmptyDaySumsToARealZero() {
        val total = Day.empty(date).totals().kcal

        assertEquals(0.0, total.value)
        assertTrue(total.isEmpty)
    }

    @Test
    fun anUntrackedDayIsLoggedButNeverCountsInTrends() {
        val logged = Day(date, ateNoDetails = setOf(MealSlot.Lunch))

        assertTrue(logged.countsInTrends)
        assertTrue(logged.copy(untracked = true).hasLogged, "the calendar still shows the dot")
        assertFalse(logged.copy(untracked = true).countsInTrends)
        assertFalse(Day.empty(date).countsInTrends)
    }

    @Test
    fun nameOnlyItemsMakeTheTotalUnknownNotZero() {
        val total = listOf(item(kcal = null), item(kcal = null)).totals().kcal

        assertNull(total.value)
        assertEquals(2, total.unknownCount)
    }

    @Test
    fun aMixOfKnownAndUnknownIsAFloorAndSaysSo() {
        val total = listOf(item(kcal = 300.0), item(kcal = null), item(kcal = 150.0)).totals().kcal

        assertEquals(450.0, total.value)
        assertTrue(total.isPartial)
    }

    @Test
    fun eachNutrientIsKnownOrUnknownOnItsOwn() {
        val totals = listOf(item(kcal = 100.0, protein = null), item(kcal = 50.0, protein = 4.0)).totals()

        assertEquals(150.0, totals.kcal.value)
        assertTrue(!totals.kcal.isPartial)
        assertEquals(4.0, totals.protein.value)
        assertTrue(totals.protein.isPartial)
    }

    @Test
    fun ateNoDetailsCountsAsSomethingUnknown() {
        val day = Day(
            date = date,
            itemsBySlot = mapOf(MealSlot.Breakfast to listOf(item(kcal = 300.0, slot = MealSlot.Breakfast))),
            ateNoDetails = setOf(MealSlot.Lunch),
        )

        assertTrue(day.totals().kcal.isPartial)
        assertNull(day.totals(MealSlot.Lunch).kcal.value)
        assertEquals(300.0, day.totals(MealSlot.Breakfast).kcal.value)
    }

    @Test
    fun aMealIsDoneWithItemsOrAnAteNoDetailsMark() {
        val day = Day(
            date = date,
            itemsBySlot = mapOf(
                MealSlot.Breakfast to listOf(item(slot = MealSlot.Breakfast)),
                MealSlot.Uncategorized to listOf(item(slot = MealSlot.Uncategorized)),
            ),
            ateNoDetails = setOf(MealSlot.Dinner),
        )

        assertTrue(day.isMealDone(MealSlot.Breakfast))
        assertTrue(day.isMealDone(MealSlot.Dinner))
        assertFalse(day.isMealDone(MealSlot.Lunch))
        assertEquals(2, day.doneMealCount, "Uncategorized is not a meal")
        assertTrue(day.items(MealSlot.Snack).isEmpty())
    }

    @Test
    fun aDayIsClosedWhenTheUserClosedItOrItIsPast() {
        assertFalse(Day(date).isClosed(today = date))
        assertTrue(Day(date, closedAt = Instant.parse("2026-10-25T21:00:00Z")).isClosed(today = date))
        assertTrue(Day(date).isClosed(today = LocalDate(2026, 10, 26)))
        assertTrue(Day(date).formatContext(today = LocalDate(2026, 10, 26)).dayClosed)
    }

    @Test
    fun theDefaultSlotFollowsTheClock() {
        assertEquals(MealSlot.Breakfast, MealSlot.defaultFor(LocalTime(10, 29)))
        assertEquals(MealSlot.Lunch, MealSlot.defaultFor(LocalTime(10, 30)))
        assertEquals(MealSlot.Snack, MealSlot.defaultFor(LocalTime(14, 30)))
        assertEquals(MealSlot.Dinner, MealSlot.defaultFor(LocalTime(17, 0)))
        assertEquals(MealSlot.Uncategorized, MealSlot.defaultFor(LocalTime(8, 0), mealGroups = false))
        assertEquals(listOf(MealSlot.Breakfast, MealSlot.Lunch, MealSlot.Snack, MealSlot.Dinner), MealSlot.meals)
    }

    @Test
    fun aCustomFoodScalesPerServingAndKeepsUnknownsUnknown() {
        val bar = CustomFood(id = "c1", name = "Granola bar", servingAmount = 1.0, servingUnit = "bar", kcal = 190.0, fiber = null)

        val draft = bar.toDraft(servings = 2.0)

        assertEquals(380.0, draft.kcal)
        assertNull(draft.fiber)
        assertEquals("2 bars", draft.amountText)
        assertEquals("c1", draft.customFoodId)
        assertEquals(LogMethod.Custom, draft.method)
    }

    private fun item(kcal: Double? = 100.0, protein: Double? = 5.0, slot: MealSlot = MealSlot.Lunch) = LogItem(
        id = "id-${kcal}-${protein}-$slot",
        day = date,
        slot = slot,
        loggedAt = Instant.parse("2026-10-25T12:00:00Z"),
        name = "Food",
        precision = Precision.Estimate,
        method = LogMethod.Search,
        kcal = kcal,
        protein = protein,
    )
}
