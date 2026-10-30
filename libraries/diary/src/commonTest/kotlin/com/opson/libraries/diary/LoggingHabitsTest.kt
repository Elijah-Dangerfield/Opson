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
    fun aMealLoggedToday_countsForItsTime_butNotForTheUsualDay() {
        val days = (1..2).map { day(ago(it), item(ago(it), MealSlot.Lunch, 13, 0)) } + day(today, item(today, MealSlot.Lunch, 13, 0))
        val habits = habits(days)
        assertEquals(LocalTime(13, 0), habits.usual.lunch, "two days and today make three")
        assertEquals(2, habits.loggedDays)
    }

    @Test
    fun backFilledItems_untrackedDays_andDaysPastTheWindow_sayNothingAboutWhen() {
        val backFilled = (1..3).map { day(ago(it), item(ago(it), MealSlot.Breakfast, 21, 0, loggedOn = ago(it - 1))) }
        val untracked = (4..6).map { day(ago(it), item(ago(it), MealSlot.Breakfast, 11, 0), untracked = true) }
        val outside = (30..32).map { day(ago(it), item(ago(it), MealSlot.Breakfast, 6, 0)) }
        val habits = habits(backFilled + untracked + outside)
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

    private fun closed(date: LocalDate, hour: Int, minute: Int = 0, on: LocalDate = date, untracked: Boolean = false) =
        Day(date = date, closedAt = LocalDateTime(on, LocalTime(hour, minute)).toInstant(zone), untracked = untracked)

    @Test
    fun theUsualWrapUp_isTheMiddleOfRecentWrapUps_roundedToFiveMinutes() {
        val days = listOf(20 to 10, 20 to 14, 21 to 3, 20 to 20).mapIndexed { i, (hour, minute) -> closed(ago(i + 1), hour, minute) }
        assertEquals(LocalTime(20, 15), habits(days).wrapUp)
    }

    @Test
    fun theUsualWrapUp_waitsForThreeWrapUps() {
        assertNull(habits(listOf(closed(ago(1), 20), closed(ago(2), 20))).wrapUp)
        assertEquals(LocalTime(20, 0), habits((1..3).map { closed(ago(it), 20) }).wrapUp)
    }

    @Test
    fun aWrapUpJustAfterMidnight_belongsToTheNightBefore_andOneDaysLaterSaysNothing() {
        // Three nights wrapped up at 12:20 AM the next morning, one caught up three days later at noon.
        val lateNights = (2..4).map { closed(ago(it), 0, 20, on = ago(it - 1)) }
        val caughtUp = closed(ago(8), 12, 0, on = ago(5))
        assertEquals(LocalTime(0, 20), habits(lateNights + caughtUp).wrapUp)
    }

    @Test
    fun aLateNightAndAnEveningAverage_acrossMidnight_notAtNoon() {
        val days = listOf(
            closed(ago(1), 23, 30),
            closed(ago(2), 0, 30, on = ago(1)),
            closed(ago(3), 23, 50),
        )
        assertEquals(LocalTime(23, 50), habits(days).wrapUp)
    }

    @Test
    fun untrackedDaysAndOldWrapUps_areLeftOut() {
        val old = (LoggingHabitsEstimator.WINDOW_DAYS + 1..LoggingHabitsEstimator.WINDOW_DAYS + 5).map { closed(ago(it), 18) }
        val away = (1..3).map { closed(ago(it), 9, untracked = true) }
        assertNull(habits(old + away).wrapUp)
    }

    @Test
    fun theWeightedMedian_takesTheValueWhereHalfTheWeightLies() {
        assertEquals(10, LoggingHabitsEstimator.weightedMedian(listOf(10 to 3.0, 20 to 1.0, 30 to 1.0)))
        assertEquals(30, LoggingHabitsEstimator.weightedMedian(listOf(10 to 1.0, 20 to 1.0, 30 to 5.0)))
        assertEquals(20, LoggingHabitsEstimator.weightedMedian(listOf(30 to 1.0, 10 to 1.0, 20 to 1.0)))
    }
}
