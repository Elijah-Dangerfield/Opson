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
 * different by day, and `rangeFor` answers for any date. The floor rule is the old one: nothing
 * goes below where the user started (the low end, and the aim in the middle) while
 * [GOAL_FLOOR_KCAL] is unset, and a deficit never grows past where it started while
 * [MAX_DEFICIT_KCAL] is unset.
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
    fun theLowEnd_isLockedWhereItStarted() {
        assertFalse(base.canStep(RangeEnd.Low, up = false))
        assertNull(base.step(RangeEnd.Low, up = false))
        assertEquals(base.startingMinKcal, base.lowFloorKcal)
    }

    @Test
    fun theHighEnd_alwaysRaises_andTheAimFollowsTheMiddle() {
        val raised = requireNotNull(base.step(RangeEnd.High, up = true))
        assertEquals(1900, raised.minKcal)
        assertEquals(2350, raised.maxKcal)
        assertEquals(2125, raised.energyKcal)
    }

    @Test
    fun theHighEnd_cannotPullTheAimBelowWhereItStarted() {
        // 1,900 to 2,300 puts the aim at the start, 2,100; one step down would make it 2,075.
        assertNull(base.step(RangeEnd.High, up = false))
        // Raised twice first (aim 2,150), it can come back down one step (aim 2,125).
        val wider = requireNotNull(base.step(RangeEnd.High, up = true)?.step(RangeEnd.High, up = true))
        assertEquals(2350, wider.step(RangeEnd.High, up = false)?.maxKcal)
    }

    @Test
    fun theLowEnd_canComeBackDownToWhereItStarted_butNoFurther() {
        val raised = requireNotNull(base.step(RangeEnd.Low, up = true))
        val back = requireNotNull(raised.step(RangeEnd.Low, up = false))
        assertEquals(1900, back.minKcal)
        assertNull(back.step(RangeEnd.Low, up = false))
    }

    @Test
    fun aRefusedStep_saysWhetherTheFloorOrTheGapStoppedIt() {
        assertEquals(StepBlock.Floor, base.stepBlock(RangeEnd.Low, up = false))
        assertEquals(StepBlock.Floor, base.stepBlock(RangeEnd.High, up = false))
        assertNull(base.stepBlock(RangeEnd.High, up = true))
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
    fun lower_respectsTheLowEndsFloorToo() {
        // Moved apart by hand: the aim could go down, but the low end is already at its floor.
        val raisedHigh = requireNotNull(base.step(RangeEnd.High, up = true)?.step(RangeEnd.High, up = true))
        assertFalse(raisedHigh.canLower)
        assertNull(raisedHigh.lower())
    }

    @Test
    fun aRange_isTheSameGoalEveryDay() {
        val goal = base.rangeFor(monday, PresetId.Full)
        assertEquals(DayGoal.Range(1900, 2300, 2100), goal)
        assertEquals(goal, base.rangeFor(thursday, PresetId.Blind))
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
        val low = base.copy(startingKcal = 1800, style = TargetStyle.Deficit(kcal = 300, startingKcal = 300))
        assertEquals(DayGoal.About(1870), low.rangeFor(monday, PresetId.Full, burnKcal = 2172))
        // A low-burn day: 1,900 - 300 would be 1,600, but the floor is 1,800.
        assertEquals(DayGoal.About(1800), low.rangeFor(monday, PresetId.Full, burnKcal = 1900))
        // With the floor at the default start, 2,100.
        val atStart = base.copy(style = TargetStyle.Deficit(kcal = 300, startingKcal = 300))
        assertEquals(DayGoal.About(2100), atStart.rangeFor(monday, PresetId.Sprint, burnKcal = 2172))
    }

    @Test
    fun deficit_fallsBackToTheRange_withoutABurn_orOutsideFullAndSprint() {
        val targets = base.copy(startingKcal = 1800, style = TargetStyle.Deficit(kcal = 300, startingKcal = 300))
        val range = DayGoal.Range(1900, 2300, 2100)
        assertEquals(range, targets.rangeFor(monday, PresetId.Full, burnKcal = null))
        PresetId.entries.filterNot(TargetStyle::deficitAllowed).forEach { preset ->
            assertEquals(range, targets.rangeFor(monday, preset, burnKcal = 2172), preset.name)
        }
        assertEquals(setOf(PresetId.Full, PresetId.Sprint), PresetId.entries.filter(TargetStyle::deficitAllowed).toSet())
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
        assertEquals(DayGoal.Range(1950, 2350, 2150, groupIndex = 1), training.rangeFor(monday, PresetId.Fuel))
        assertEquals(DayGoal.Range(1900, 2300, 2100, groupIndex = 0), training.rangeFor(thursday, PresetId.Fuel))
    }

    @Test
    fun byDay_groupStepsKeepTheFloor() {
        val targets = base.useByDay()
        assertNull(targets.step(RangeEnd.Low, up = false, group = 0))
        assertNull(targets.step(RangeEnd.High, up = false, group = 0))
        assertNull(targets.step(RangeEnd.High, up = true, group = 3), "no such group")
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
