package com.opson.libraries.diary

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The usual times Today says "Usually around" with, and what its late-day nudge compares against. */
class LoggingHabitsTest {
    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 10, 29)
    private val own = MealTimes(breakfast = LocalTime(8, 0), lunch = LocalTime(12, 30), snack = LocalTime(15, 30), dinner = LocalTime(19, 0))

    private fun ago(days: Int): LocalDate = today.minus(days, DateTimeUnit.DAY)

    private fun item(date: LocalDate, slot: MealSlot, hour: Int, minute: Int = 0, kcal: Double = 500.0, loggedOn: LocalDate = date) = LogItem(
        id = "$date-$slot-$hour-$minute",
        day = date,
        slot = slot,
        loggedAt = LocalDateTime(loggedOn, LocalTime(hour, minute)).toInstant(zone),
        name = "Toast",
        precision = Precision.Estimate,
        method = LogMethod.Search,
        kcal = kcal,
    )

    private fun day(date: LocalDate, vararg items: LogItem, untracked: Boolean = false, ateNoDetails: Set<MealSlot> = emptySet()) =
        Day(
            date = date,
            itemsBySlot = MealSlot.entries.associateWith { slot -> items.filter { it.slot == slot } },
            untracked = untracked,
            ateNoDetails = ateNoDetails,
        )

    private fun habits(days: List<Day>) = LoggingHabitsEstimator.of(days, today, zone, own)

    @Test
    fun withTooLittleToGoOn_theTimesAreTheOnesThePersonSet() {
        val days = (1..2).map { day(ago(it), item(ago(it), MealSlot.Lunch, 14, 10)) }
        val habits = habits(days)
        assertEquals(own, habits.usual)
        assertTrue(habits.learned.isEmpty())
        assertNull(habits.lastMealTime)
    }

    @Test
    fun aMealsUsualTime_isTheMiddleOfItsDays_roundedToFiveMinutes() {
        val days = listOf(32, 33, 41, 47, 52).mapIndexed { i, minute -> day(ago(i + 1), item(ago(i + 1), MealSlot.Snack, 15, minute)) }
        val habits = habits(days)
        assertEquals(LocalTime(15, 40), habits.usual.snack)
        assertEquals(setOf(MealSlot.Snack), habits.learned)
        assertEquals(own.dinner, habits.usual.dinner, "a meal with nothing to learn from keeps the person's own time")
    }

    @Test
    fun oneOddDay_doesNotMoveIt() {
        val usual = (1..6).map { day(ago(it), item(ago(it), MealSlot.Dinner, 19, 30)) }
        val late = day(ago(7), item(ago(7), MealSlot.Dinner, 23, 50))
        assertEquals(LocalTime(19, 30), habits(usual + late).usual.dinner)
    }

    @Test
    fun recentDays_countForMore_soANewRoutineWinsOverAnOldOne() {
        // Three weeks ago lunch was at noon; the last ten days it has been at two.
        val old = (18..27).map { day(ago(it), item(ago(it), MealSlot.Lunch, 12, 0)) }
        val new = (1..10).map { day(ago(it), item(ago(it), MealSlot.Lunch, 14, 0)) }
        assertEquals(LocalTime(14, 0), habits(old + new).usual.lunch)
    }

    @Test
    fun backFilledItems_untrackedDays_today_andDaysPastTheWindow_sayNothingAboutWhen() {
        val backFilled = (1..3).map { day(ago(it), item(ago(it), MealSlot.Breakfast, 21, 0, loggedOn = ago(it - 1))) }
        val untracked = (4..6).map { day(ago(it), item(ago(it), MealSlot.Breakfast, 11, 0), untracked = true) }
        val outside = (30..32).map { day(ago(it), item(ago(it), MealSlot.Breakfast, 6, 0)) }
        val now = day(today, item(today, MealSlot.Breakfast, 10, 0))
        val habits = habits(backFilled + untracked + outside + now)
        assertEquals(own.breakfast, habits.usual.breakfast)
        assertEquals(3, habits.loggedDays, "back-filled days still count as logged, untracked and out-of-window ones don't")
    }

    @Test
    fun theFirstItemOfAMeal_isItsTime() {
        val days = (1..3).map { day(ago(it), item(ago(it), MealSlot.Breakfast, 9, 30), item(ago(it), MealSlot.Breakfast, 8, 15)) }
        assertEquals(LocalTime(8, 15), habits(days).usual.breakfast)
    }

    @Test
    fun theLastMeal_isTheLatestLearnedOne() {
        val days = (1..4).map {
            day(ago(it), item(ago(it), MealSlot.Breakfast, 8, 0), item(ago(it), MealSlot.Dinner, 20, 15))
        }
        assertEquals(LocalTime(20, 15), habits(days).lastMealTime)
    }

    @Test
    fun aTypicalDay_isTheMiddleOfTheLoggedOnes() {
        val days = listOf(
            day(ago(1), item(ago(1), MealSlot.Lunch, 12, kcal = 1800.0)),
            day(ago(2), item(ago(2), MealSlot.Lunch, 12, kcal = 2000.0), item(ago(2), MealSlot.Dinner, 19, kcal = 200.0)),
            day(ago(3), item(ago(3), MealSlot.Lunch, 12, kcal = 2400.0), ateNoDetails = setOf(MealSlot.Breakfast)),
            day(ago(4)),
        )
        val habits = habits(days)
        assertEquals(3, habits.loggedDays, "an empty day is not a logged one")
        assertEquals(2200.0, habits.typicalKcal)
        assertEquals(2.0, habits.typicalEntries)
    }

    @Test
    fun theWeightedMedian_takesTheValueWhereHalfTheWeightLies() {
        assertEquals(10, LoggingHabitsEstimator.weightedMedian(listOf(10 to 3.0, 20 to 1.0, 30 to 1.0)))
        assertEquals(30, LoggingHabitsEstimator.weightedMedian(listOf(10 to 1.0, 20 to 1.0, 30 to 5.0)))
        assertEquals(20, LoggingHabitsEstimator.weightedMedian(listOf(30 to 1.0, 10 to 1.0, 20 to 1.0)))
    }
}
