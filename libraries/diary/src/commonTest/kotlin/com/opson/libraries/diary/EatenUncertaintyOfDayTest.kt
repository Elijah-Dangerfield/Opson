package com.opson.libraries.diary

import com.opson.libraries.nutrition.LoggedAs
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** How each logged food reads for the day's ±, and how big a meal eaten with no details usually is. */
class EatenUncertaintyOfDayTest {

    private val date = LocalDate(2026, 10, 27)

    private fun item(kcal: Double, precision: Precision = Precision.Estimate, method: LogMethod = LogMethod.Search, slot: MealSlot = MealSlot.Lunch, day: LocalDate = date) =
        LogItem(
            id = "$kcal-$method-$precision",
            day = day,
            slot = slot,
            loggedAt = Instant.parse("2026-10-27T12:00:00Z"),
            name = "food",
            precision = precision,
            method = method,
            kcal = kcal,
        )

    private fun day(vararg items: LogItem, date: LocalDate = this.date, ateNoDetails: Set<MealSlot> = emptySet()) = Day(
        date = date,
        itemsBySlot = MealSlot.entries.associateWith { slot -> items.filter { it.slot == slot } },
        ateNoDetails = ateNoDetails,
    )

    @Test
    fun eachItem_readsByHowItWasLogged() {
        assertEquals(LoggedAs.Weighed, item(100.0, Precision.Weighed).loggedAs)
        assertEquals(LoggedAs.Label, item(100.0, Precision.Weighed, LogMethod.Barcode).loggedAs, "a label's own error stays")
        assertEquals(LoggedAs.QuickAdd, item(100.0, method = LogMethod.QuickNumber).loggedAs)
        assertEquals(LoggedAs.Estimate, item(100.0, method = LogMethod.Describe).loggedAs)
        assertEquals(LoggedAs.Estimate, item(100.0, Precision.Named).loggedAs)
    }

    @Test
    fun aMealEatenWithNoDetails_countsAtFortyPercentOfItsUsualSize() {
        val today = day(item(400.0), ateNoDetails = setOf(MealSlot.Dinner))
        val uncertainty = today.eatenUncertainty { if (it == MealSlot.Dinner) 600.0 else 0.0 }
        // σ 100 for the estimate and 240 for the dinner: √(100² + 240²) = 260.
        assertEquals(260, uncertainty.plusMinusKcal)
        assertEquals(2, uncertainty.foods)
        assertEquals(2, uncertainty.estimates)
    }

    @Test
    fun theUsualMeal_isTheMedianOfDaysThatLoggedIt_orItsShareOfTheAim() {
        val history = listOf(
            day(item(500.0, slot = MealSlot.Dinner, day = date.minusDays(1)), date = date.minusDays(1)),
            day(item(700.0, slot = MealSlot.Dinner, day = date.minusDays(2)), date = date.minusDays(2)),
            day(item(900.0, slot = MealSlot.Dinner, day = date.minusDays(3)), date = date.minusDays(3)),
            day(date = date.minusDays(4)),
            day(item(5000.0, slot = MealSlot.Dinner, day = date.minusDays(5)), date = date.minusDays(5)).copy(untracked = true),
        )
        assertEquals(700.0, UsualMealSize.of(history, MealSlot.Dinner, aimKcal = 2000))
        assertEquals(500.0, UsualMealSize.of(emptyList(), MealSlot.Breakfast, aimKcal = 2000))
    }

    private fun LocalDate.minusDays(days: Int) = LocalDate.fromEpochDays(toEpochDays() - days)
}
