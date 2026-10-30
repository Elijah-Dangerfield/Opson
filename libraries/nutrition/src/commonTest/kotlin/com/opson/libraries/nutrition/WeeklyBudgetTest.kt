package com.opson.libraries.nutrition

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The weekly budget: counted days, skipped days that ask nothing of the others, and the caps that
 * keep a big day from turning into a hungry week (docs/plans/goals-v2.md, "Weekly averaging").
 */
class WeeklyBudgetTest {

    // 2026-10-26 is a Monday.
    private val mon = LocalDate(2026, 10, 26)
    private fun day(offset: Int) = LocalDate.fromEpochDays(mon.toEpochDays() + offset)

    private val aim = 1930
    private val floor = BudgetFloor(lowGoalKcal = 1500, burnKcal = 2430, fatLossLimitKcal = 700)

    private fun eaten(offset: Int, kcal: Double?, closed: Boolean = false) =
        day(offset) to GoalDayFacts(day(offset), logged = true, eatenKcal = kcal, closed = closed)

    private fun week(today: Int, averaging: GoalAveraging = GoalAveraging.Weekly, vararg days: Pair<LocalDate, GoalDayFacts>) =
        WeeklyBudgets.of(day(today), DayOfWeek.MONDAY, aim, 100, averaging, days.toMap(), floor)

    @Test
    fun daily_neverMovesTheTarget_butStillAddsTheWeekUp() {
        val budget = week(2, GoalAveraging.Daily, eaten(0, 1430.0), eaten(1, 1430.0))
        assertEquals(0, budget.adjustmentKcal)
        assertEquals(aim, budget.todayTargetKcal)
        assertEquals(1000, budget.balanceKcal)
        assertEquals(2, budget.countedDays)
    }

    @Test
    fun daysUnderTheTarget_easeTheRestOfTheWeek() {
        // The owner's case: a 500 deficit, and two days well under. Five days left share 1,000.
        val budget = week(2, GoalAveraging.Weekly, eaten(0, 1430.0), eaten(1, 1430.0))
        assertEquals(200, budget.adjustmentKcal)
        assertEquals(2130, budget.todayTargetKcal, "a 300 deficit today keeps the week on track")
        assertEquals(2030 to 2230, budget.todayMinKcal to budget.todayMaxKcal)
        assertEquals(5, budget.remainingDays)
        assertFalse(budget.capped)
    }

    @Test
    fun aBigDay_tightensTheRestOnlyALittle_andTheRestIsDropped() {
        // 2,000 over on Monday would be 333 a day for six days; the cap is a tenth of the target.
        val budget = week(1, GoalAveraging.Weekly, eaten(0, 3930.0))
        assertEquals(-190, budget.adjustmentKcal)
        assertTrue(budget.capped)
        assertEquals(1740, budget.todayTargetKcal)
    }

    @Test
    fun tightening_neverAsksForADeficitPastTheFatLossLimit() {
        val tight = BudgetFloor(lowGoalKcal = 1500, burnKcal = 2430, fatLossLimitKcal = 500)
        val budget = WeeklyBudgets.of(day(1), DayOfWeek.MONDAY, aim, 100, GoalAveraging.Weekly, mapOf(eaten(0, 3930.0)), tight)
        assertEquals(0, budget.adjustmentKcal, "the target is already a 500 deficit")
        assertTrue(budget.capped)
    }

    @Test
    fun tightening_neverGoesUnderTheLowGoalLine() {
        val low = BudgetFloor(lowGoalKcal = 1900, burnKcal = null, fatLossLimitKcal = null)
        val budget = WeeklyBudgets.of(day(1), DayOfWeek.MONDAY, aim, 100, GoalAveraging.Weekly, mapOf(eaten(0, 3930.0)), low)
        assertEquals(-30, budget.adjustmentKcal)
    }

    @Test
    fun easing_isCappedAtAFifthOfTheTarget() {
        val budget = week(2, GoalAveraging.Weekly, eaten(0, 500.0), eaten(1, 500.0))
        assertEquals(380, budget.adjustmentKcal, "572 a day, capped at 386")
        assertTrue(budget.capped)
    }

    @Test
    fun skippedEmptyAndPartialDays_areLeftOut_andAskNothingOfTheOthers() {
        val budget = week(
            3,
            GoalAveraging.Weekly,
            day(0) to GoalDayFacts(day(0), untracked = true),
            day(2) to GoalDayFacts(day(2), logged = true, eatenKcal = null),
        )
        assertEquals(
            listOf(BudgetDayState.Skipped, BudgetDayState.NotLogged, BudgetDayState.Partial, BudgetDayState.Today),
            budget.days.take(4).map { it.state },
        )
        assertEquals(0, budget.countedDays)
        assertEquals(0, budget.adjustmentKcal)
        assertEquals(aim, budget.todayTargetKcal)
        assertEquals(aim * 6, budget.weekTargetKcal, "Monday was skipped")
        assertEquals(4, budget.remainingDays)
    }

    @Test
    fun aDaySkippedAhead_isNotGivenAShare() {
        val budget = week(1, GoalAveraging.Weekly, eaten(0, 1330.0), day(4) to GoalDayFacts(day(4), untracked = true))
        assertEquals(5, budget.remainingDays)
        assertEquals(120, budget.adjustmentKcal, "600 over five days")
    }

    @Test
    fun todayCountsOnceWrappedUp_andTheRestShareIt() {
        val budget = week(1, GoalAveraging.Weekly, eaten(0, 1930.0), eaten(1, 1430.0, closed = true))
        assertEquals(2, budget.countedDays)
        assertEquals(5, budget.remainingDays)
        assertEquals(100, budget.adjustmentKcal)
    }

    @Test
    fun theWeekStartsOnTheChosenDay_andLastWeekIsForgotten() {
        val lastSunday = LocalDate.fromEpochDays(mon.toEpochDays() - 1)
        val budget = week(0, GoalAveraging.Weekly, lastSunday to GoalDayFacts(lastSunday, logged = true, eatenKcal = 4000.0))
        assertEquals(mon, budget.start)
        assertEquals(0, budget.adjustmentKcal)
        val sundayStart = WeeklyBudgets.of(day(2), DayOfWeek.SUNDAY, aim, 100, GoalAveraging.Weekly, emptyMap(), floor)
        assertEquals(lastSunday, sundayStart.start)
    }

    @Test
    fun aPastDay_isReplayedAsItStoodThatMorning() {
        val days = mapOf(eaten(0, 1430.0), eaten(1, 2000.0), eaten(2, 1430.0))
        // Tuesday's target saw only Monday: 500 over six days.
        val tuesday = WeeklyBudgets.targetOn(day(1), DayOfWeek.MONDAY, aim, 100, GoalAveraging.Weekly, days, floor)
        assertEquals(80, tuesday.adjustmentKcal)
    }
}
