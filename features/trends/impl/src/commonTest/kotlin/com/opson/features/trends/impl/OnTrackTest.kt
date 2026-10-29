package com.opson.features.trends.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TrendsCards
import com.opson.libraries.nutrition.DayGoal
import com.opson.libraries.nutrition.DayGroup
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GroupShape
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * The on-track calendar's rules: where a day stops being on plan and starts being a bit off, then
 * off, for a range and for one number; which way is "off" for a deficit, a surplus and a range;
 * the days that are never judged; and which calendar each mode gets.
 */
class OnTrackTest {

    private val range = DayGoal.Range(1900, 2300, 2100)

    @Test
    fun aRange_isOnPlanInside_aBitOffUpToTheMargin_offPastIt() {
        // A tenth of 2,100 is 210, more than the 150 floor.
        assertEquals(210, OnTrack.nearMarginKcal(range))
        assertEquals(PlanStanding.OnPlan, OnTrack.standing(1900.0, range))
        assertEquals(PlanStanding.OnPlan, OnTrack.standing(2300.0, range))
        assertEquals(PlanStanding.ABitOver, OnTrack.standing(2300.5, range))
        assertEquals(PlanStanding.ABitOver, OnTrack.standing(2510.0, range))
        assertEquals(PlanStanding.Over, OnTrack.standing(2510.5, range))
        assertEquals(PlanStanding.ABitUnder, OnTrack.standing(1899.5, range))
        assertEquals(PlanStanding.ABitUnder, OnTrack.standing(1690.0, range))
        assertEquals(PlanStanding.Under, OnTrack.standing(1689.5, range))
    }

    @Test
    fun oneNumber_hasTheLeewayTodayGivesIt_thenTheMargin() {
        val about = DayGoal.About(1800)
        assertEquals(180, OnTrack.nearMarginKcal(about))
        assertEquals(PlanStanding.OnPlan, OnTrack.standing(1700.0, about))
        assertEquals(PlanStanding.OnPlan, OnTrack.standing(1900.0, about))
        assertEquals(PlanStanding.ABitOver, OnTrack.standing(1901.0, about))
        assertEquals(PlanStanding.ABitOver, OnTrack.standing(2080.0, about))
        assertEquals(PlanStanding.Over, OnTrack.standing(2081.0, about))
        assertEquals(PlanStanding.ABitUnder, OnTrack.standing(1699.0, about))
        assertEquals(PlanStanding.ABitUnder, OnTrack.standing(1520.0, about))
        assertEquals(PlanStanding.Under, OnTrack.standing(1519.0, about))
    }

    @Test
    fun aSmallPlan_isStillGivenTheFloor() {
        val small = DayGoal.Exact(1200)
        assertEquals(OnTrack.NEAR_MIN_KCAL, OnTrack.nearMarginKcal(small))
        assertEquals(PlanStanding.ABitOver, OnTrack.standing(1450.0, small))
        assertEquals(PlanStanding.Over, OnTrack.standing(1451.0, small))
    }

    @Test
    fun aSetDeficit_isOnPlanWhenTheDaysDeficitIsCloseToTheOneSet() {
        val targets = Targets.Default.copy(style = TargetStyle.Deficit(kcal = 500, startingKcal = 500))
        val goal = targets.rangeFor(MONDAY, PresetId.Full, burnKcal = 2300)
        assertEquals(DayGoal.About(1800), goal)
        assertEquals(PlanStanding.OnPlan, OnTrack.standing(2300.0 - 500, goal), "exactly the deficit")
        assertEquals(PlanStanding.OnPlan, OnTrack.standing(2300.0 - 600, goal), "600 under is within the leeway")
        assertEquals(PlanStanding.ABitUnder, OnTrack.standing(2300.0 - 650, goal))
        assertEquals(PlanStanding.Over, OnTrack.standing(2300.0 - 200, goal), "a 200 deficit is far short of 500")
    }

    @Test
    fun off_pointsAwayFromTheGoal() {
        assertEquals(PlanTone.Off, OnTrack.tone(PlanStanding.Over, GoalDirection.Lose))
        assertEquals(PlanTone.Near, OnTrack.tone(PlanStanding.Under, GoalDirection.Lose), "far under a deficit is not the best day")
        assertEquals(PlanTone.Off, OnTrack.tone(PlanStanding.Under, GoalDirection.Gain))
        assertEquals(PlanTone.Near, OnTrack.tone(PlanStanding.Over, GoalDirection.Gain))
        assertEquals(PlanTone.Off, OnTrack.tone(PlanStanding.Over, GoalDirection.Hold))
        assertEquals(PlanTone.Off, OnTrack.tone(PlanStanding.Under, GoalDirection.Hold))
        assertEquals(PlanTone.Near, OnTrack.tone(PlanStanding.ABitOver, GoalDirection.Lose))
        assertEquals(PlanTone.Near, OnTrack.tone(PlanStanding.ABitUnder, GoalDirection.Lose))
        assertEquals(PlanTone.On, OnTrack.tone(PlanStanding.OnPlan, GoalDirection.Hold))
        listOf(PlanStanding.NotLogged, PlanStanding.Untracked, PlanStanding.Future, PlanStanding.InProgress, PlanStanding.NotYet, PlanStanding.Partial)
            .forEach { assertEquals(PlanTone.None, OnTrack.tone(it, GoalDirection.Lose), "$it is gray") }
    }

    @Test
    fun direction_followsTheGoalInForce() {
        val deficit = Targets.Default.copy(style = TargetStyle.Deficit(500, 500))
        assertEquals(GoalDirection.Lose, OnTrack.direction(deficit, MONDAY, PresetId.Full, burnKcal = 2300))
        assertEquals(GoalDirection.Hold, OnTrack.direction(deficit, MONDAY, PresetId.Full, burnKcal = null), "no burn: the range")
        assertEquals(GoalDirection.Hold, OnTrack.direction(deficit, MONDAY, PresetId.MacrosOnly, burnKcal = 2300), "a mode without deficits")
        val surplus = Targets.Default.copy(style = TargetStyle.Surplus(300, 300))
        assertEquals(GoalDirection.Gain, OnTrack.direction(surplus, MONDAY, PresetId.Full, burnKcal = 2300))
        assertEquals(GoalDirection.Hold, OnTrack.direction(Targets.Default, MONDAY, PresetId.Full, burnKcal = 2300))

        val byDay = Targets.Default.copy(
            style = TargetStyle.ByDay(
                listOf(
                    DayGroup(DayOfWeek.entries.toSet() - DayOfWeek.MONDAY, 1900, 2300),
                    DayGroup(setOf(DayOfWeek.MONDAY), 1900, 2300, shape = GroupShape.Deficit(400, 400)),
                ),
            ),
        )
        assertEquals(GoalDirection.Lose, OnTrack.direction(byDay, MONDAY, PresetId.Full, burnKcal = 2300))
        assertEquals(GoalDirection.Hold, OnTrack.direction(byDay, MONDAY.plusDays(1), PresetId.Full, burnKcal = 2300))
    }

    @Test
    fun daysThatAreNeverJudged() {
        val settings = Settings()
        fun standing(day: Day, opened: Boolean = true) = OnTrack.standingOf(day, TODAY, settings, CalendarMode.Plan, opened, burnKcal = null)

        assertEquals(PlanStanding.Future, standing(Day.empty(TODAY.plusDays(1))))
        assertEquals(PlanStanding.Untracked, standing(logged(MONDAY, 2000.0).copy(untracked = true)))
        assertEquals(PlanStanding.NotLogged, standing(Day.empty(MONDAY)))
        assertEquals(PlanStanding.InProgress, standing(Day.empty(TODAY)))
        assertEquals(PlanStanding.InProgress, standing(logged(TODAY, 900.0)), "lunchtime is not under plan")
        assertEquals(PlanStanding.OnPlan, standing(logged(TODAY, 2000.0).copy(closedAt = Instant.parse("2026-10-25T21:00:00Z"))), "closed, today is judged")
        assertEquals(PlanStanding.NotYet, standing(logged(MONDAY, 2000.0), opened = false))
        assertEquals(PlanStanding.OnPlan, standing(logged(MONDAY, 2000.0)))
    }

    @Test
    fun aMealWithoutAmounts_leavesTheDayUnjudged_unlessItIsAlreadyOver() {
        val settings = Settings()
        fun standing(day: Day) = OnTrack.standingOf(day, TODAY, settings, CalendarMode.Plan, opened = true, burnKcal = null)
        val unknownDinner = setOf(MealSlot.Dinner)
        assertEquals(PlanStanding.Partial, standing(Day(MONDAY, ateNoDetails = unknownDinner)), "nothing known at all")
        assertEquals(PlanStanding.Partial, standing(logged(MONDAY, 1500.0).copy(ateNoDetails = unknownDinner)), "might still land on plan")
        assertEquals(PlanStanding.Over, standing(logged(MONDAY, 2900.0).copy(ateNoDetails = unknownDinner)), "already past it, whatever dinner was")
    }

    @Test
    fun theLoggedOnlyCalendar_saysOnlyLoggedOrNot() {
        fun standing(day: Day) = OnTrack.standingOf(day, TODAY, Settings(), CalendarMode.LoggedOnly, opened = true, burnKcal = null)
        assertEquals(PlanStanding.Logged, standing(logged(MONDAY, 3500.0)))
        assertEquals(PlanStanding.NotLogged, standing(Day.empty(MONDAY)))
        assertEquals(PlanTone.Logged, OnTrack.tone(PlanStanding.Logged, GoalDirection.Hold))
    }

    @Test
    fun mode_planOnlyWhereTheNumbersShowInFull_andNeverAfterNumbersHaveBeenHard() {
        val context = FormatContext(day = TODAY)
        val deficit = Settings(targets = Targets.Default.copy(style = TargetStyle.Deficit(500, 500)))
        assertEquals(CalendarMode.Plan, OnTrack.mode(Settings(), TrendsFixtures.policy(), context))
        assertEquals(CalendarMode.LoggedOnly, OnTrack.mode(Settings(), TrendsFixtures.policy(PresetId.MacrosOnly), context))
        assertEquals(CalendarMode.LoggedOnly, OnTrack.mode(Settings(), TrendsFixtures.policy(PresetId.MacrosWithGrams), context))
        assertEquals(CalendarMode.LoggedOnly, OnTrack.mode(Settings(), TrendsFixtures.policy(PresetId.NoNumbers), context))
        assertEquals(CalendarMode.LoggedOnly, OnTrack.mode(Settings(onboarding = OnboardingAnswers(hard = true)), TrendsFixtures.policy(), context))
        val energyHidden = TrendsFixtures.policy(overrides = mapOf(Metric.EnergyOut to Visibility.Hide))
        assertEquals(CalendarMode.LoggedOnly, OnTrack.mode(deficit, energyHidden, context), "the colors would say what was burned")
        assertEquals(CalendarMode.Plan, OnTrack.mode(Settings(), energyHidden, context), "a range never needs it")
    }

    @Test
    fun theCalendar_threeMonths_thisOneShowing_laidOutFromTheWeekStart() {
        val inputs = TrendsFixtures.trendsInputs()
        val calendar = assertNotNull(TrendsStateBuilder.build(inputs, TrendsFixtures.policy()).calendar)
        assertEquals(listOf(LocalDate(2026, 8, 1), LocalDate(2026, 9, 1), LocalDate(2026, 10, 1)), calendar.months.map { it.first })
        assertEquals(2, calendar.page)
        // October 1st, 2026 is a Thursday: three blanks from Monday, four from Sunday.
        assertEquals(3, calendar.shown.cells.takeWhile { it == null }.size)
        val sunday = assertNotNull(TrendsStateBuilder.build(TrendsFixtures.trendsInputs(Settings(weekStart = DayOfWeek.SUNDAY)), TrendsFixtures.policy()).calendar)
        assertEquals(4, sunday.shown.cells.takeWhile { it == null }.size)

        val october = calendar.shown.cells.filterNotNull()
        assertEquals(31, october.size)
        assertEquals(true, october.single { it.date == TODAY }.isToday)
        assertEquals(PlanStanding.NotLogged, october.single { it.date == LocalDate(2026, 10, 22) }.standing, "the fixture's empty Thursday")
        assertEquals(PlanStanding.Future, october.last().standing)
        assertEquals(calendar.shown.on + calendar.shown.near + calendar.shown.off, october.count { it.tone in setOf(PlanTone.On, PlanTone.Near, PlanTone.Off) })
    }

    @Test
    fun theCalendar_againstADeficit_usesEachDaysBurned() {
        val inputs = TrendsFixtures.deficitInputs()
        val calendar = assertNotNull(TrendsStateBuilder.build(inputs, TrendsFixtures.policy()).calendar)
        assertEquals(GoalDirection.Lose, calendar.direction)
        val monday = calendar.shown.cells.filterNotNull().single { it.date == MONDAY }
        val day = inputs.days.single { it.date == MONDAY }
        val goal = inputs.settings.targets.rangeFor(MONDAY, PresetId.Full, inputs.burnedByDay?.get(MONDAY)?.totalKcal)
        assertEquals(OnTrack.standing(day.totals().kcal.known, goal), monday.standing)
    }

    @Test
    fun theCalendar_switchedOff_isNotDrawn() {
        assertNull(TrendsStateBuilder.build(TrendsFixtures.trendsInputs(Settings(trendsCards = TrendsCards(calendar = false))), TrendsFixtures.policy()).calendar)
    }

    @Test
    fun theCalendar_inHiddenModes_isLoggedOnly() {
        val calendar = assertNotNull(TrendsStateBuilder.build(TrendsFixtures.deficitInputs(), TrendsFixtures.policy(PresetId.NoNumbers)).calendar)
        assertEquals(CalendarMode.LoggedOnly, calendar.mode)
        val tones = calendar.months.flatMap { month -> month.cells.filterNotNull().map { it.tone } }.toSet()
        assertEquals(setOf(PlanTone.Logged, PlanTone.None), tones)
    }

    private fun logged(date: LocalDate, kcal: Double) = Day(
        date = date,
        itemsBySlot = MealSlot.entries.associateWith { slot ->
            if (slot != MealSlot.Lunch) {
                emptyList()
            } else {
                listOf(
                    LogItem(
                        id = "lunch-$date",
                        day = date,
                        slot = MealSlot.Lunch,
                        loggedAt = Instant.parse("2026-10-20T12:00:00Z"),
                        name = "Lunch",
                        precision = Precision.Estimate,
                        method = LogMethod.Search,
                        kcal = kcal,
                    ),
                )
            }
        },
    )

    private fun LocalDate.plusDays(days: Int) = LocalDate.fromEpochDays(toEpochDays() + days)

    private companion object {
        val TODAY = TrendsFixtures.today
        val MONDAY = LocalDate(2026, 10, 19)
    }
}
