package com.opson.libraries.nutrition

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One number and a set surplus, as goal styles and as a day group's shape. One number is the aim,
 * stepped under the same floor as the range; a surplus goes on top of what the user burns, or of
 * the aim until that is known, and never past [MAX_SURPLUS_KCAL].
 */
class ExactAndSurplusTest {

    private val base = Targets.Default
    private val monday = LocalDate(2026, 10, 26)
    private val saturday = LocalDate(2026, 10, 31)

    // --- one number ---

    @Test
    fun oneNumber_isTheAim_everyDay() {
        val exact = base.useExact()
        assertEquals(DayGoal.Exact(2100), exact.rangeFor(monday, PresetId.Full))
        assertEquals(DayGoal.Exact(2100), exact.rangeFor(saturday, PresetId.NoNumbers))
        assertEquals(DayGoal.Exact(2100), exact.spanGoal(PresetId.Full))
    }

    @Test
    fun oneNumber_staysOneNumber_whenActivityAddsToIt() {
        val exact = base.useExact()
        val today = exact.today(ExerciseAdjustment.All, movementKcal = 200, workoutKcal = 100, goal = exact.rangeFor(monday, PresetId.Full))
        assertEquals(EnergyRange(2400, 2400, 2400, activityKcal = 300), today)
        assertTrue(today.single)
    }

    @Test
    fun oneNumber_stepsUp_andTheRangeMovesWithIt() {
        val raised = requireNotNull(base.useExact().stepNumber(up = true))
        assertEquals(2150, raised.energyKcal)
        assertEquals(1950 to 2350, raised.minKcal to raised.maxKcal)
        assertEquals(DayGoal.Range(1950, 2350, 2150), raised.useRange().rangeFor(monday, PresetId.Full))
    }

    @Test
    fun oneNumber_cannotGoBelowTheFloor() {
        assertEquals(2050, base.useExact().stepNumber(up = false)?.energyKcal, "under where it started is fine")
        val exact = base.useExact().withEnergy(MIN_GOAL_KCAL)
        assertEquals(MIN_GOAL_KCAL, exact.energyKcal)
        assertFalse(exact.canStepNumber(up = false))
        assertNull(exact.stepNumber(up = false))
        val raised = requireNotNull(exact.stepNumber(up = true))
        assertEquals(MIN_GOAL_KCAL, raised.stepNumber(up = false)?.energyKcal, "back down to the minimum, no further")
    }

    @Test
    fun oneNumber_stepsRightDownToTheFloor_notHalfTheHiddenRangeAboveIt() {
        var exact = base.useExact()
        while (exact.canStepNumber(up = false)) exact = requireNotNull(exact.stepNumber(up = false))
        assertEquals(MIN_GOAL_KCAL, exact.energyKcal)
        assertEquals(DayGoal.Exact(MIN_GOAL_KCAL), exact.rangeFor(monday, PresetId.Full))
        assertEquals(MIN_GOAL_KCAL to MIN_GOAL_KCAL + 400, exact.minKcal to exact.maxKcal, "the range behind it keeps its width, inside the bounds")
    }

    @Test
    fun oneNumber_stepsRightUpToTheCeiling() {
        var exact = base.useExact()
        while (exact.canStepNumber(up = true)) exact = requireNotNull(exact.stepNumber(up = true))
        assertEquals(MAX_GOAL_KCAL, exact.energyKcal)
        assertEquals(MAX_GOAL_KCAL - 400 to MAX_GOAL_KCAL, exact.minKcal to exact.maxKcal)
        assertNull(exact.stepNumber(up = true))
    }

    @Test
    fun oneNumber_typedNearTheBounds_isTheNumberTyped() {
        val exact = base.useExact()
        assertEquals(800, exact.withEnergy(800).energyKcal)
        assertEquals(900, exact.withEnergy(900).energyKcal)
        assertEquals(800 to 1200, exact.withEnergy(900).let { it.minKcal to it.maxKcal }, "pushed up to the floor, still 400 wide")
        assertEquals(6000, exact.withEnergy(6000).energyKcal)
        assertEquals(MIN_GOAL_KCAL, exact.withEnergy(100).energyKcal, "held at the floor")
        assertEquals(MAX_GOAL_KCAL, exact.withEnergy(9999).energyKcal, "held at the ceiling")
        val backToRange = exact.withEnergy(2500).useRange()
        assertEquals(DayGoal.Range(2300, 2700, 2500), backToRange.rangeFor(monday, PresetId.Full), "away from the bounds it is centred again")
    }

    @Test
    fun oneNumber_scalesTheMacrosInRatioMode() {
        val raised = requireNotNull(base.useExact().stepNumber(up = true, scaleMacros = true))
        assertEquals(base.raise(scaleMacros = true).proteinG, raised.proteinG)
        assertEquals(base.proteinG, requireNotNull(base.useExact().stepNumber(up = true)).proteinG)
    }

    // --- surplus ---

    @Test
    fun maxSurplus_isUnsetUntilAClinicianSetsIt() {
        assertNull(MAX_SURPLUS_KCAL)
    }

    @Test
    fun surplus_startsAtZero_withoutABurn_soTheGoalIsTheAim() {
        val surplus = base.useSurplus(burnKcal = null)
        assertEquals(TargetStyle.Surplus(0, 0), surplus.style)
        assertEquals(DayGoal.About(2100), surplus.rangeFor(monday, PresetId.Full))
    }

    @Test
    fun surplus_startsAtWhatTheAimAlreadyImplies() {
        // A 2,100 aim against 1,780 burned is 320 over, floored to the step.
        assertEquals(TargetStyle.Surplus(300, 300), base.useSurplus(burnKcal = 1780).style)
        assertEquals(TargetStyle.Surplus(0, 0), base.useSurplus(burnKcal = 2500).style)
    }

    @Test
    fun surplus_goesOnTheAim_untilTheBurnIsKnown_thenOnTheBurn() {
        val surplus = base.copy(style = TargetStyle.Surplus(300, 300))
        assertEquals(DayGoal.About(2400), surplus.rangeFor(monday, PresetId.Full))
        assertEquals(DayGoal.About(2470), surplus.rangeFor(monday, PresetId.Full, burnKcal = 2172))
    }

    @Test
    fun surplus_isNeverBelowTheFloor_evenOnALowBurnDay() {
        val surplus = base.copy(style = TargetStyle.Surplus(100, 100))
        assertEquals(DayGoal.About(1600), surplus.rangeFor(monday, PresetId.Full, burnKcal = 1500))
        // 1,000 burned plus 100 would be 1,100; a goal Opson works out never goes under 1,200.
        assertEquals(DayGoal.About(LOW_GOAL_FEMALE_KCAL), surplus.rangeFor(monday, PresetId.Full, burnKcal = 1000))
    }

    @Test
    fun surplus_cannotGrowPastWhereItStarted() {
        val targets = base.useSurplus(burnKcal = 1780)
        assertNull(targets.stepSurplus(up = true))
        val smaller = requireNotNull(targets.stepSurplus(up = false))
        assertEquals(TargetStyle.Surplus(250, 300), smaller.style)
        assertEquals(TargetStyle.Surplus(300, 300), smaller.stepSurplus(up = true)?.style)
        assertEquals(300, targets.maxSurplusKcal())
    }

    @Test
    fun surplus_neverGoesBelowZero_andIsLockedAtZeroWithoutABurn() {
        val zero = base.useSurplus(burnKcal = null)
        assertNull(zero.stepSurplus(up = false))
        assertNull(zero.stepSurplus(up = true))
        assertNull(base.stepSurplus(up = true), "no surplus to step")
    }

    @Test
    fun surplus_isFullNumbersOnly_elsewhereTheRange() {
        val surplus = base.copy(style = TargetStyle.Surplus(200, 200))
        PresetId.entries.forEach { preset ->
            val expected = if (TargetStyle.surplusAllowed(preset)) DayGoal.About(2300) else DayGoal.Range(1900, 2300, 2100)
            assertEquals(expected, surplus.rangeFor(monday, preset), preset.name)
        }
        assertEquals(PresetId.entries.filter(TargetStyle::deficitAllowed), PresetId.entries.filter(TargetStyle::surplusAllowed))
    }

    @Test
    fun pickingOneNumberOrASurplusAgain_keepsWhatWasSet() {
        val surplus = requireNotNull(base.useSurplus(1780).stepSurplus(up = false))
        assertEquals(surplus, surplus.useSurplus(burnKcal = 1000))
        assertEquals(TargetStyle.Range, surplus.useRange().style)
        assertEquals(TargetStyle.Exact, surplus.useExact().style)
    }

    // --- day group shapes ---

    private val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    /** Weekdays on the base range, the weekend in group 1 at 2,300 to 2,700. */
    private fun twoGroups(): Targets {
        var targets = requireNotNull(base.useByDay().addDayGroup())
        weekend.forEach { targets = targets.moveDay(it, 1) }
        repeat(16) { targets = requireNotNull(targets.step(if (it % 2 == 0) RangeEnd.High else RangeEnd.Low, up = true, group = 1)) }
        return targets
    }

    @Test
    fun aGroup_canBeOneNumber_itsMiddle() {
        val exact = requireNotNull(twoGroups().shapeGroup(1, GroupShape.Exact))
        assertEquals(DayGoal.Exact(2500, groupIndex = 1), exact.rangeFor(saturday, PresetId.Full))
        assertEquals(DayGoal.Range(1900, 2300, 2100, groupIndex = 0), exact.rangeFor(monday, PresetId.Full))
    }

    @Test
    fun aGroupsNumber_steps_andKeepsTheFloor() {
        val exact = requireNotNull(twoGroups().shapeGroup(1, GroupShape.Exact))
        assertEquals(DayGoal.Exact(2550, groupIndex = 1), exact.stepNumber(up = true, group = 1)?.rangeFor(saturday, PresetId.Full))
        var atFloor = requireNotNull(base.withEnergy(MIN_GOAL_KCAL).useByDay().shapeGroup(0, GroupShape.Exact))
        while (atFloor.canStepNumber(up = false, group = 0)) atFloor = requireNotNull(atFloor.stepNumber(up = false, group = 0))
        assertEquals(DayGoal.Exact(MIN_GOAL_KCAL, groupIndex = 0), atFloor.rangeFor(monday, PresetId.Full), "the number itself reaches the floor")
        assertNull(atFloor.stepNumber(up = false, group = 0))
        assertNull(exact.stepNumber(up = true, group = 5), "no such group")
    }

    @Test
    fun aGroupsNumber_reachesTheCeiling_andWidensAgainOnTheWayBack() {
        var exact = requireNotNull(twoGroups().shapeGroup(1, GroupShape.Exact))
        while (exact.canStepNumber(up = true, group = 1)) exact = requireNotNull(exact.stepNumber(up = true, group = 1))
        assertEquals(DayGoal.Exact(MAX_GOAL_KCAL, groupIndex = 1), exact.rangeFor(saturday, PresetId.Full))
        repeat(20) { exact = requireNotNull(exact.stepNumber(up = false, group = 1)) }
        val group = (exact.style as TargetStyle.ByDay).groups[1]
        assertEquals(5000, group.midKcal)
        assertEquals(400, group.maxKcal - group.minKcal, "back to the base range's width")
    }

    @Test
    fun aGroup_canBeASurplus_onItsMiddleUntilTheBurnIsKnown() {
        // The weekend's middle is 2,500; picked against a 2,400 burn, the surplus starts at 100.
        val surplus = requireNotNull(twoGroups().shapeGroup(1, GroupShape.Surplus(0, 0), burnKcal = 2400))
        assertEquals(GroupShape.Surplus(100, 100), (surplus.style as TargetStyle.ByDay).groups[1].shape)
        assertEquals(DayGoal.About(2600, groupIndex = 1), surplus.rangeFor(saturday, PresetId.Full))
        assertEquals(DayGoal.About(2500, groupIndex = 1), surplus.rangeFor(saturday, PresetId.Full, burnKcal = 2400))
        assertNull(surplus.stepSurplus(up = true, group = 1), "no further than where it started")
        val less = requireNotNull(surplus.stepSurplus(up = false, group = 1))
        assertEquals(GroupShape.Surplus(50, 100), (less.style as TargetStyle.ByDay).groups[1].shape)
        assertNull(surplus.stepSurplus(up = true, group = 0), "group 0 is a range")
        assertEquals(DayGoal.Range(2300, 2700, 2500, groupIndex = 1), surplus.rangeFor(saturday, PresetId.NoNumbers), "All numbers only")
    }

    @Test
    fun aGroupsShape_keepsItsRange_forWhenItGoesBack() {
        val shaped = requireNotNull(twoGroups().shapeGroup(1, GroupShape.Exact))
        assertEquals(DayGoal.Range(2300, 2700, 2500, groupIndex = 1), shaped.shapeGroup(1, GroupShape.Range)?.rangeFor(saturday, PresetId.Full))
        assertEquals(shaped, shaped.shapeGroup(1, GroupShape.Exact), "picking the same shape changes nothing")
        assertNull(base.shapeGroup(0, GroupShape.Exact), "not different by day")
    }

    @Test
    fun aNewGroup_isARange() {
        val shaped = requireNotNull(base.useByDay().shapeGroup(0, GroupShape.Exact))
        val added = requireNotNull(shaped.addDayGroup())
        assertEquals(GroupShape.Range, (added.style as TargetStyle.ByDay).groups[1].shape)
    }

    @Test
    fun spanGoal_runsFromTheLowestGroupGoalToTheHighest() {
        val shaped = requireNotNull(twoGroups().shapeGroup(1, GroupShape.Exact))
        assertEquals(DayGoal.Range(1900, 2500, 2200), shaped.spanGoal(PresetId.Full))
        // A group with no days is not drawn; one level left is that group's own goal.
        val empty = requireNotNull(base.useByDay().shapeGroup(0, GroupShape.Exact)?.addDayGroup())
        assertEquals(DayGoal.Exact(2100, groupIndex = 0), empty.spanGoal(PresetId.Full))
    }

    // --- judging a day against one number ---

    @Test
    fun oneNumber_hasLeewayEitherSide_aRangeHasNone() {
        val one = EnergyRange(2100, 2100, 2100, activityKcal = 0)
        assertEquals(GoalPosition.On, one.position(2100.0 + ONE_NUMBER_LEEWAY_KCAL))
        assertEquals(GoalPosition.On, one.position(2100.0 - ONE_NUMBER_LEEWAY_KCAL))
        assertEquals(GoalPosition.Above, one.position(2101.0 + ONE_NUMBER_LEEWAY_KCAL))
        assertEquals(GoalPosition.Below, one.position(1999.0 - 1))

        val range = EnergyRange(2100, 1900, 2300, activityKcal = 0)
        assertFalse(range.single)
        assertEquals(GoalPosition.Above, range.position(2301.0))
        assertEquals(GoalPosition.On, range.position(1900.0))
        assertEquals(GoalPosition.Below, range.position(1899.0))
    }
}
