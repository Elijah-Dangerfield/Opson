package com.opson.libraries.nutrition

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Design v3 Targets: each end of the range steps on its own, a goal can be a set deficit or
 * different by day, and `rangeFor` answers for any date. Either end moves freely between
 * [MIN_GOAL_KCAL] and [MAX_GOAL_KCAL] while [GOAL_FLOOR_KCAL] is unset (when a goal is low is
 * `GoalSafetyTest`'s, a note rather than a lock), and a deficit never grows past where it started
 * while [MAX_DEFICIT_KCAL] is unset.
 *
 * Not covered here: a dietitian's floor or maximum, because neither exists yet and inventing one
 * for a test would be the first step to shipping it.
 */
class TargetStyleTest {

    private val base = Targets.Default
    private val monday = LocalDate(2026, 10, 26)
    private val thursday = LocalDate(2026, 10, 29)

    @Test
    fun maxDeficit_isUnsetUntilADietitianSetsIt() {
        assertNull(MAX_DEFICIT_KCAL)
    }

    @Test
    fun theLowEnd_goesUnderWhereItStarted_downToTheMinimum() {
        val lowered = requireNotNull(base.step(RangeEnd.Low, up = false))
        assertEquals(1850, lowered.minKcal)
        assertEquals(1900, lowered.startingMinKcal, "where it started is a record, not a limit")
        assertEquals(MIN_GOAL_KCAL, base.floorKcal)

        var low = base
        while (true) low = low.step(RangeEnd.Low, up = false) ?: break
        assertEquals(MIN_GOAL_KCAL, low.minKcal)
        assertEquals(StepBlock.Floor, low.stepBlock(RangeEnd.Low, up = false))
    }

    @Test
    fun theHighEnd_stopsAtTheMaximum() {
        var high = base
        while (true) high = high.step(RangeEnd.High, up = true) ?: break
        assertEquals(MAX_GOAL_KCAL, high.maxKcal)
        assertEquals(StepBlock.Ceiling, high.stepBlock(RangeEnd.High, up = true))
    }

    @Test
    fun theHighEnd_alwaysRaises_andTheAimFollowsTheMiddle() {
        val raised = requireNotNull(base.step(RangeEnd.High, up = true))
        assertEquals(1900, raised.minKcal)
        assertEquals(2350, raised.maxKcal)
        assertEquals(2125, raised.energyKcal)
    }

    @Test
    fun theHighEnd_comesDown_pullingTheAimUnderWhereItStarted() {
        val lowered = requireNotNull(base.step(RangeEnd.High, up = false))
        assertEquals(2250, lowered.maxKcal)
        assertEquals(2075, lowered.energyKcal)
    }

    @Test
    fun aRefusedStep_saysWhetherTheFloorTheCeilingOrTheGapStoppedIt() {
        val atFloor = base.copy(energyKcal = 1000, minKcal = MIN_GOAL_KCAL, maxKcal = 1200)
        assertEquals(StepBlock.Floor, atFloor.stepBlock(RangeEnd.Low, up = false))
        assertNull(atFloor.stepBlock(RangeEnd.High, up = false))
        assertNull(base.stepBlock(RangeEnd.High, up = true))
        assertNull(base.stepBlock(RangeEnd.Low, up = false))
        var narrow = base
        while (true) narrow = narrow.step(RangeEnd.Low, up = true) ?: break
        assertEquals(StepBlock.Gap, narrow.stepBlock(RangeEnd.Low, up = true))
    }

    @Test
    fun theEnds_stayAtLeastOneStepApart() {
        var targets = base
        while (true) targets = targets.step(RangeEnd.Low, up = true) ?: break
        assertEquals(Targets.STEP_KCAL, targets.maxKcal - targets.minKcal)
    }

    @Test
    fun aRangeStep_inRatioMode_scalesTheMacrosWithTheAim() {
        val raised = requireNotNull(base.step(RangeEnd.High, up = true, scaleMacros = true))
        assertEquals(Math.roundHalfUp(120 * 2125 / 2100.0), raised.proteinG)
        assertEquals(base.fiberG, raised.fiberG)
        val grams = requireNotNull(base.step(RangeEnd.High, up = true, scaleMacros = false))
        assertEquals(base.proteinG, grams.proteinG)
    }

    @Test
    fun lower_stopsWhereTheLowEndWouldPassTheMinimum() {
        // Moved apart by hand: the aim could go down, but the low end is already at the minimum.
        val wide = base.copy(energyKcal = 1500, minKcal = MIN_GOAL_KCAL, maxKcal = 2200)
        assertFalse(wide.canLower)
        assertNull(wide.lower())
        assertTrue(base.canLower)
    }

    @Test
    fun aRange_isTheSameGoalEveryDay() {
        val goal = base.rangeFor(monday, PresetId.Full)
        assertEquals(DayGoal.Range(1900, 2300, 2100), goal)
        assertEquals(goal, base.rangeFor(thursday, PresetId.NoNumbers))
    }

    @Test
    fun deficit_startsAtWhatTheAimAlreadyImplies() {
        val deficit = base.useDeficit(burnKcal = 2420).style
        // 2,420 burned against a 2,100 aim is 320, floored to the step.
        assertEquals(TargetStyle.Deficit(kcal = 300, startingKcal = 300), deficit)
        assertEquals(TargetStyle.Deficit(0, 0), base.useDeficit(burnKcal = null).style)
        assertEquals(TargetStyle.Deficit(0, 0), base.useDeficit(burnKcal = 1800).style)
    }

    @Test
    fun deficit_cannotGrowPastWhereItStarted() {
        val targets = base.useDeficit(burnKcal = 2420)
        assertNull(targets.stepDeficit(up = true))
        val smaller = requireNotNull(targets.stepDeficit(up = false))
        assertEquals(250, (smaller.style as TargetStyle.Deficit).kcal)
        assertEquals(300, (requireNotNull(smaller.stepDeficit(up = true)).style as TargetStyle.Deficit).kcal)
    }

    @Test
    fun deficit_neverGoesBelowZero() {
        assertNull(base.useDeficit(burnKcal = null).stepDeficit(up = false))
    }

    @Test
    fun deficit_goalIsAboutTheBurnLessTheDeficit_neverBelowTheFloor() {
        val deficit = base.copy(style = TargetStyle.Deficit(kcal = 300, startingKcal = 300))
        assertEquals(DayGoal.About(1870), deficit.rangeFor(monday, PresetId.Full, burnKcal = 2172))
        assertEquals(DayGoal.About(1600), deficit.rangeFor(monday, PresetId.Full, burnKcal = 1900))
        // A low-burn day: 1,400 - 300 would be 1,100, but a goal Opson works out stays at 1,200 or more.
        assertEquals(DayGoal.About(LOW_GOAL_FEMALE_KCAL), deficit.rangeFor(monday, PresetId.Full, burnKcal = 1400))
    }

    @Test
    fun deficit_fallsBackToTheRange_withoutABurn_orOutsideFullAndSprint() {
        val targets = base.copy(startingKcal = 1800, style = TargetStyle.Deficit(kcal = 300, startingKcal = 300))
        val range = DayGoal.Range(1900, 2300, 2100)
        assertEquals(range, targets.rangeFor(monday, PresetId.Full, burnKcal = null))
        PresetId.entries.filterNot(TargetStyle::deficitAllowed).forEach { preset ->
            assertEquals(range, targets.rangeFor(monday, preset, burnKcal = 2172), preset.name)
        }
        assertEquals(setOf(PresetId.Full, PresetId.Full), PresetId.entries.filter(TargetStyle::deficitAllowed).toSet())
    }

    @Test
    fun byDay_startsAsOneGroupWithEveryDay() {
        val style = assertIs<TargetStyle.ByDay>(base.useByDay().style)
        assertEquals(listOf(DayGroup(DayOfWeek.entries.toSet(), 1900, 2300)), style.groups)
    }

    @Test
    fun byDay_eachDayIsInExactlyOneGroup() {
        val targets = requireNotNull(base.useByDay().addDayGroup())
            .moveDay(DayOfWeek.MONDAY, 1)
            .moveDay(DayOfWeek.TUESDAY, 1)
            .moveDay(DayOfWeek.MONDAY, 1)
        val groups = (targets.style as TargetStyle.ByDay).groups
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), groups[1].days)
        DayOfWeek.entries.forEach { day -> assertEquals(1, groups.count { day in it.days }, day.name) }
    }

    @Test
    fun byDay_eachGroupHasItsOwnRange_forItsDays() {
        val training = requireNotNull(
            requireNotNull(base.useByDay().addDayGroup())
                .moveDay(DayOfWeek.MONDAY, 1)
                .step(RangeEnd.High, up = true, group = 1)
                ?.step(RangeEnd.Low, up = true, group = 1),
        )
        assertEquals(DayGoal.Range(1950, 2350, 2150, groupIndex = 1), training.rangeFor(monday, PresetId.MacrosOnly))
        assertEquals(DayGoal.Range(1900, 2300, 2100, groupIndex = 0), training.rangeFor(thursday, PresetId.MacrosOnly))
    }

    @Test
    fun byDay_groupStepsKeepTheFloor() {
        val targets = base.copy(style = TargetStyle.ByDay(listOf(DayGroup(DayOfWeek.entries.toSet(), MIN_GOAL_KCAL, 1200))))
        assertNull(targets.step(RangeEnd.Low, up = false, group = 0))
        assertEquals(1150, targets.step(RangeEnd.High, up = false, group = 0)?.let { (it.style as TargetStyle.ByDay).groups[0].maxKcal })
        assertNull(targets.step(RangeEnd.High, up = true, group = 3), "no such group")
    }

    @Test
    fun byDay_aTappedDay_movesHere_andATappedDayOfThisGroup_goesBackToTheFirst() {
        fun groups(targets: Targets) = (targets.style as TargetStyle.ByDay).groups
        val split = requireNotNull(base.useByDay().addDayGroup())

        val saturday = split.toggleDay(DayOfWeek.SATURDAY, 1)
        assertEquals(setOf(DayOfWeek.SATURDAY), groups(saturday)[1].days)

        val back = saturday.toggleDay(DayOfWeek.SATURDAY, 1)
        assertEquals(emptySet(), groups(back)[1].days)
        assertEquals(DayOfWeek.entries.toSet(), groups(back)[0].days)

        assertEquals(saturday, saturday.toggleDay(DayOfWeek.MONDAY, 0), "the first group's own days have nowhere to go")
        assertEquals(groups(back), groups(saturday.toggleDay(DayOfWeek.SATURDAY, 0)), "tapped in the first group, a day comes back to it")
        assertEquals(saturday, saturday.toggleDay(DayOfWeek.MONDAY, 5), "no such group")
    }

    @Test
    fun byDay_whateverIsTapped_everyDayStaysInExactlyOneGroup() {
        var targets = requireNotNull(requireNotNull(base.useByDay().addDayGroup()).addDayGroup())
        val taps = listOf(1, 2, 0, 1, 1, 2, 0, 2)
        DayOfWeek.entries.forEachIndexed { index, day ->
            taps.drop(index).forEach { group -> targets = targets.toggleDay(day, group) }
        }
        val groups = (targets.style as TargetStyle.ByDay).groups
        DayOfWeek.entries.forEach { day -> assertEquals(1, groups.count { day in it.days }, day.name) }
    }

    @Test
    fun byDay_upToFourGroups() {
        var targets = base.useByDay()
        repeat(3) { targets = requireNotNull(targets.addDayGroup()) }
        assertEquals(TargetStyle.MAX_GROUPS, (targets.style as TargetStyle.ByDay).groups.size)
        assertNull(targets.addDayGroup())
    }

    @Test
    fun byDay_removingAGroupHandsItsDaysToTheFirst() {
        val targets = requireNotNull(base.useByDay().addDayGroup()).moveDay(DayOfWeek.FRIDAY, 1)
        val removed = requireNotNull(targets.removeDayGroup(1))
        val groups = (removed.style as TargetStyle.ByDay).groups
        assertEquals(1, groups.size)
        assertEquals(DayOfWeek.entries.toSet(), groups[0].days)
        assertNull(removed.removeDayGroup(0), "the first group stays")
    }

    @Test
    fun pickingAStyleAgain_keepsWhatWasSet() {
        val byDay = requireNotNull(base.useByDay().addDayGroup())
        assertEquals(byDay, byDay.useByDay())
        val deficit = base.useDeficit(2420).stepDeficit(up = false)!!
        assertEquals(deficit, deficit.useDeficit(3000))
        assertEquals(TargetStyle.Range, deficit.useRange().style)
    }

    private object Math {
        fun roundHalfUp(value: Double): Int = kotlin.math.floor(value + 0.5).toInt()
    }
}
