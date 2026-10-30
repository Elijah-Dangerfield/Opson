package com.opson.libraries.nutrition

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** On track per day for every goal type, the projection band, and which types each mode offers. */
class GoalDaysTest {

    // 2026-10-26 is a Monday.
    private val mon = LocalDate(2026, 10, 26)
    private fun day(offset: Int) = LocalDate.fromEpochDays(mon.toEpochDays() + offset)
    private val today = day(4)

    private val baseline = GoalBaseline(burnKcal = 2480, sex = BodySex.Male)
    private val lose = Goal(
        id = Goal.WEIGHT_ID,
        type = GoalType.Weight(GoalDirection.Lose, targetKg = 80.0, kgPerWeek = 0.5, startKg = 90.0),
        startDate = mon,
        baseline = baseline,
    )
    private val context = GoalContext(today = today, target = GoalTargets.of(lose))

    private fun ate(offset: Int, kcal: Double?, closed: Boolean = false) =
        day(offset) to GoalDayFacts(day(offset), logged = true, eatenKcal = kcal, closed = closed, showedUp = true)

    @Test
    fun aCalorieDay_isMetInsideTheBand() {
        val facts = mapOf(ate(0, 1900.0), ate(1, 2100.0), ate(2, 2400.0), ate(3, 1600.0))
        assertEquals(
            listOf(GoalVerdict.Met, GoalVerdict.Near, GoalVerdict.Off, GoalVerdict.Near, GoalVerdict.InProgress, GoalVerdict.Future),
            GoalDays.verdicts(lose, (0..5).map(::day), facts, context).map { it.verdict },
        )
    }

    @Test
    fun farUnderALossGoal_isOnlyNear_neverTheBestDay() {
        assertEquals(GoalVerdict.Near, GoalDays.verdict(lose, day(0), mapOf(ate(0, 600.0)), context).verdict)
        val gain = lose.copy(type = GoalType.Weight(GoalDirection.Gain, kgPerWeek = 0.25))
        val gainContext = context.copy(target = GoalTargets.of(gain))
        assertEquals(GoalVerdict.Off, GoalDays.verdict(gain, day(0), mapOf(ate(0, 600.0)), gainContext).verdict)
        assertEquals(GoalVerdict.Near, GoalDays.verdict(gain, day(0), mapOf(ate(0, 4000.0)), gainContext).verdict)
    }

    @Test
    fun skippedEmptyAndUnknownDays_andDaysBeforeTheGoal_dontCount() {
        val facts = mapOf(day(0) to GoalDayFacts(day(0), untracked = true), ate(2, null))
        assertEquals(GoalVerdict.NotCounted, GoalDays.verdict(lose, day(0), facts, context).verdict)
        assertEquals(GoalVerdict.NotCounted, GoalDays.verdict(lose, day(1), facts, context).verdict)
        assertEquals(GoalVerdict.NotCounted, GoalDays.verdict(lose, day(2), facts, context).verdict)
        assertEquals(GoalVerdict.NotCounted, GoalDays.verdict(lose.copy(startDate = day(3)), day(2), mapOf(ate(2, 1930.0)), context).verdict)
    }

    @Test
    fun todayIsJudged_onceWrappedUp() {
        assertEquals(GoalVerdict.Met, GoalDays.verdict(lose, today, mapOf(ate(4, 1930.0, closed = true)), context).verdict)
    }

    @Test
    fun weekly_judgesEachDayByItsReplayedTarget() {
        // Monday 1,000 under: Tuesday's target is 2,090 (the cap allows 166 more over six days).
        val weekly = lose.copy(averaging = GoalAveraging.Weekly)
        val facts = mapOf(ate(0, 930.0), ate(1, 2100.0))
        val tuesday = GoalDays.verdict(weekly, day(1), facts, context)
        assertEquals(2090, tuesday.targetKcal)
        assertEquals(GoalVerdict.Met, tuesday.verdict, "2,100 is over the daily band and inside Tuesday's")
        assertEquals(GoalVerdict.Near, GoalDays.verdict(lose, day(1), facts, context).verdict)
    }

    @Test
    fun burnAndSteps_countUp_andStepsWithoutASourceDontCount() {
        val burn = Goal("burn", GoalType.Burn(500, BurnMeasure.Active), mon)
        val facts = mapOf(
            day(0) to GoalDayFacts(day(0), activeKcal = 520),
            day(1) to GoalDayFacts(day(1), activeKcal = 460),
            day(2) to GoalDayFacts(day(2), activeKcal = 100),
        )
        assertEquals(
            listOf(GoalVerdict.Met, GoalVerdict.Near, GoalVerdict.Off, GoalVerdict.NotCounted),
            GoalDays.verdicts(burn, (0..3).map(::day), facts, context).map { it.verdict },
        )
        val total = Goal("burn", GoalType.Burn(2500, BurnMeasure.Total), mon)
        assertEquals(GoalVerdict.Met, GoalDays.verdict(total, day(0), mapOf(day(0) to GoalDayFacts(day(0), burnedKcal = 2600)), context).verdict)
        val steps = Goal("steps", GoalType.Steps(8000), mon)
        assertEquals(GoalVerdict.NotCounted, GoalDays.verdict(steps, day(0), facts, context).verdict)
        assertEquals(GoalVerdict.Met, GoalDays.verdict(steps, day(0), mapOf(day(0) to GoalDayFacts(day(0), steps = 9000)), context).verdict)
    }

    @Test
    fun trackEveryDay_andMeals() {
        val track = Goal("track", GoalType.TrackEveryDay, mon)
        val facts = mapOf(
            day(0) to GoalDayFacts(day(0), showedUp = true, logged = true, mealsWithFood = 3),
            day(1) to GoalDayFacts(day(1), logged = true, mealsWithFood = 2),
            day(2) to GoalDayFacts(day(2), logged = true, mealsWithFood = 1),
        )
        assertEquals(
            listOf(GoalVerdict.Met, GoalVerdict.Off, GoalVerdict.Off, GoalVerdict.Off, GoalVerdict.InProgress),
            GoalDays.verdicts(track, (0..4).map(::day), facts, context).map { it.verdict },
        )
        val meals = Goal("meals", GoalType.Meals(), mon)
        assertEquals(
            listOf(GoalVerdict.Met, GoalVerdict.Near, GoalVerdict.Off, GoalVerdict.NotCounted),
            GoalDays.verdicts(meals, (0..3).map(::day), facts, context).map { it.verdict },
        )
    }

    @Test
    fun theProjection_isABand_fromTheLowEndToTheHighEnd() {
        val projection = GoalProjections.weight(lose, fromKg = 90.0, from = mon)!!
        // 650, 550 and 450 a day under: 119, 140 and 172 days to lose 10 kg.
        assertEquals(day(119), projection.lowKcalReached)
        assertEquals(day(140), projection.targetReached)
        assertEquals(day(172), projection.highKcalReached)
        assertEquals(day(119), projection.bestReached(GoalDirection.Lose))
        assertEquals(26, projection.points.size, "weekly, to the slowest")
        assertEquals(90.0, projection.points.first().targetKg)
        val last = projection.points.last()
        assertEquals(listOf(80.0, 80.0, 80.0), listOf(last.lowKcalKg, last.targetKg, last.highKcalKg))
        assertNull(GoalProjections.weight(lose.copy(baseline = null), 90.0, mon))
    }

    @Test
    fun theCatalog_followsTheModeAndTheHardAnswer() {
        assertEquals(
            listOf(GoalKind.Weight, GoalKind.EnergyBalance, GoalKind.Burn, GoalKind.Steps, GoalKind.TrackEveryDay, GoalKind.Meals),
            GoalCatalog.offered(policy(PresetId.Full), hard = false),
        )
        assertEquals(GoalCatalog.GENTLE, GoalCatalog.offered(policy(PresetId.Full), hard = true))
        assertEquals(GoalCatalog.GENTLE, GoalCatalog.offered(policy(PresetId.NoNumbers), hard = false))
        assertEquals(
            listOf(GoalKind.Burn, GoalKind.Steps, GoalKind.TrackEveryDay, GoalKind.Meals),
            GoalCatalog.offered(policy(PresetId.Full), hard = false, stage = BodyStage.Pregnant),
        )
        assertEquals(false, GoalCatalog.weeklyOffered(policy(PresetId.Full), hard = true))
        assertEquals(true, GoalCatalog.weeklyOffered(policy(PresetId.Full), hard = false))
    }
}
