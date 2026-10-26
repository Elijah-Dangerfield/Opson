package com.opson.libraries.diary

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Design sync 2, "ADD SHEET": the meal picks itself from this person's own logging times. */
class MealSlotPickerTest {

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 10, 26)

    private fun now(hour: Int, minute: Int = 0) = LocalDateTime(today, LocalTime(hour, minute))

    private fun item(
        date: LocalDate,
        slot: MealSlot,
        hour: Int,
        minute: Int = 0,
        name: String = "Toast",
        foodId: String? = null,
        loggedOn: LocalDate = date,
    ) = LogItem(
        id = "$date-$slot-$hour-$minute-$name",
        day = date,
        slot = slot,
        loggedAt = LocalDateTime(loggedOn, LocalTime(hour, minute)).toInstant(zone),
        name = name,
        precision = Precision.Estimate,
        method = LogMethod.Search,
        foodId = foodId,
    )

    private fun day(date: LocalDate, vararg items: LogItem) =
        Day(date = date, itemsBySlot = MealSlot.entries.associateWith { slot -> items.filter { it.slot == slot } })

    private fun daysAgo(n: Int) = today.minus(n, DateTimeUnit.DAY)

    /** [count] days, each with breakfast at 8:00 and dinner at 19:00. */
    private fun regular(count: Int = 5) = (1..count).map { n ->
        val date = daysAgo(n)
        day(date, item(date, MealSlot.Breakfast, 8), item(date, MealSlot.Dinner, 19))
    }

    private fun pick(days: List<Day>, hour: Int, minute: Int = 0, groups: Boolean = true) =
        MealSlotPicker.pick(days, now(hour, minute), zone, groups)

    @Test
    fun beforeThereIsHistoryTheClockDecides() {
        assertEquals(MealSlot.Breakfast, pick(emptyList(), 9))
        assertEquals(MealSlot.Lunch, pick(emptyList(), 11))
        assertEquals(MealSlot.Dinner, pick(regular(count = 2), 21))
    }

    @Test
    fun theMealUsuallyLoggedAtThisTimeWins() {
        assertEquals(MealSlot.Dinner, pick(regular(), 19, 30))
        assertEquals(MealSlot.Breakfast, pick(regular(), 7, 15))
    }

    @Test
    fun aTimeWithNoUsualMealIsUncategorized() {
        // 10:40 with history: nothing logged within the hour, as in logger-uncategorized.
        assertEquals(MealSlot.Uncategorized, pick(regular(), 10, 40))
    }

    @Test
    fun fewerThanThreeLogsNearNowIsNoPattern() {
        val days = regular() + day(daysAgo(6), item(daysAgo(6), MealSlot.Lunch, 13)) + day(daysAgo(7), item(daysAgo(7), MealSlot.Lunch, 13))
        assertEquals(MealSlot.Uncategorized, pick(days, 13))
    }

    @Test
    fun noClearWinnerIsUncategorized() {
        val days = (1..4).map { n ->
            val date = daysAgo(n)
            val slot = if (n % 2 == 0) MealSlot.Lunch else MealSlot.Snack
            day(date, item(date, slot, 15), item(date, MealSlot.Breakfast, 8))
        }
        assertEquals(MealSlot.Uncategorized, pick(days, 15))
    }

    @Test
    fun aClearMajorityIsEnough() {
        val days = (1..5).map { n ->
            val date = daysAgo(n)
            val slot = if (n == 1) MealSlot.Lunch else MealSlot.Snack
            day(date, item(date, slot, 15))
        }
        assertEquals(MealSlot.Snack, pick(days, 15, 20))
    }

    @Test
    fun mealGroupsOffIsAlwaysUncategorized() {
        assertEquals(MealSlot.Uncategorized, pick(regular(), 19, groups = false))
    }

    @Test
    fun onlyTheLastFourteenDaysCount() {
        val old = (15..25).map { n ->
            val date = daysAgo(n)
            day(date, item(date, MealSlot.Lunch, 12))
        }
        // The old lunches are past the window, so the five regular days decide: nothing near noon.
        assertEquals(MealSlot.Uncategorized, pick(regular() + old, 12))
    }

    @Test
    fun aMealLoggedOnALaterDayIsNotATime() {
        val backfilled = (1..5).map { n ->
            val date = daysAgo(n)
            day(date, item(date, MealSlot.Lunch, 9, loggedOn = date.plus(1, DateTimeUnit.DAY)))
        }
        // No honest times at all: still no history, so the clock picks breakfast at 9.
        assertEquals(MealSlot.Breakfast, pick(backfilled, 9))
    }

    @Test
    fun usualAroundListsTheFoodsLoggedNearNowMostOftenFirst() {
        val days = (1..4).map { n ->
            val date = daysAgo(n)
            val items = buildList {
                add(item(date, MealSlot.Snack, 10, 30, name = "Banana", foodId = "banana"))
                if (n <= 2) add(item(date, MealSlot.Breakfast, 10, 15, name = "Coffee", foodId = "coffee"))
                add(item(date, MealSlot.Dinner, 19, name = "Rice", foodId = "rice"))
            }
            day(date, *items.toTypedArray())
        }

        val usual = MealSlotPicker.usualAround(days, now(10, 40), zone)

        assertEquals(listOf("Banana", "Coffee"), usual.map { it.name })
    }

    @Test
    fun aroundLabelRoundsDownToTheHalfHour() {
        assertEquals(LocalTime(10, 30), MealSlotPicker.aroundLabelTime(now(10, 40)))
        assertEquals(LocalTime(10, 0), MealSlotPicker.aroundLabelTime(now(10, 29)))
    }
}
