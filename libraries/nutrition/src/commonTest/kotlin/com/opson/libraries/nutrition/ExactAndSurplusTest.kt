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
        assertEquals(DayGoal.Exact(2100), exact.rangeFor(saturday, PresetId.Blind))
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
        val exact = base.useExact()
        assertFalse(exact.canStepNumber(up = false))
        assertNull(exact.stepNumber(up = false))
        val raised = requireNotNull(exact.stepNumber(up = true))
        assertEquals(2100, raised.stepNumber(up = false)?.energyKcal, "back down to where it started, no further")
    }

    @Test
    fun oneNumber_scalesTheMacrosInRatioMode() {
        val raised = requireNotNull(base.useExact().stepNumber(up = true, scaleMacros = true))
        assertEquals(base.raise(scaleMacros = true).proteinG, raised.proteinG)
        assertEquals(base.proteinG, requireNotNull(base.useExact().stepNumber(up = true)).proteinG)
    }

    // --- surplus ---

    @Test
    fun surplus_startsAtZero_withoutABurn_soTheGoalIsTheAim() {
        val surplus = base.useSurplus(burnKcal = null)
        assertEquals(TargetStyle.Surplus(0), surplus.style)
        assertEquals(DayGoal.About(2100), surplus.rangeFor(monday, PresetId.Full))
    }

    @Test
    fun surplus_startsAtWhatTheAimAlreadyImplies_capped() {
        assertEquals(TargetStyle.Surplus(300), base.useSurplus(burnKcal = 1780).style)
        assertEquals(TargetStyle.Surplus(MAX_SURPLUS_KCAL), base.useSurplus(burnKcal = 1000).style)
        assertEquals(TargetStyle.Surplus(0), base.useSurplus(burnKcal = 2500).style)
    }

    @Test
    fun surplus_goesOnTheAim_untilTheBurnIsKnown_thenOnTheBurn() {
        val surplus = base.copy(style = TargetStyle.Surplus(300))
        assertEquals(DayGoal.About(2400), surplus.rangeFor(monday, PresetId.Full))
        assertEquals(DayGoal.About(2470), surplus.rangeFor(monday, PresetId.Full, burnKcal = 2172))
    }

    @Test
    fun surplus_isNeverBelowTheFloor_evenOnALowBurnDay() {
        val surplus = base.copy(style = TargetStyle.Surplus(100))
        // 1,500 burned plus 100 would be 1,600; the floor is where the user started, 2,100.
        assertEquals(DayGoal.About(2100), surplus.rangeFor(monday, PresetId.Full, burnKcal = 1500))
    }

    @Test
    fun surplus_stepsBetweenZeroAndTheCap() {
        val zero = base.useSurplus(burnKcal = null)
        assertNull(zero.stepSurplus(up = false))
        var targets = zero
        repeat(MAX_SURPLUS_KCAL / Targets.STEP_KCAL) { targets = requireNotNull(targets.stepSurplus(up = true)) }
        assertEquals(TargetStyle.Surplus(MAX_SURPLUS_KCAL), targets.style)
        assertNull(targets.stepSurplus(up = true))
        assertNull(base.stepSurplus(up = true), "no surplus to step")
    }

    @Test
    fun surplus_isOfferedInEveryMode_itCanOnlyRaiseTheGoal() {
        val surplus = base.copy(style = TargetStyle.Surplus(200))
        PresetId.entries.forEach { preset ->
            assertEquals(DayGoal.About(2300), surplus.rangeFor(monday, preset), preset.name)
        }
    }

    @Test
    fun pickingOneNumberOrASurplusAgain_keepsWhatWasSet() {
        val surplus = requireNotNull(base.useSurplus(null).stepSurplus(up = true))
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
        val atFloor = requireNotNull(base.useByDay().shapeGroup(0, GroupShape.Exact))
        assertNull(atFloor.stepNumber(up = false, group = 0))
        assertNull(exact.stepNumber(up = true, group = 5), "no such group")
    }

    @Test
    fun aGroup_canBeASurplus_onItsMiddleUntilTheBurnIsKnown() {
        val surplus = requireNotNull(twoGroups().shapeGroup(1, GroupShape.Surplus(0)))
        assertEquals(DayGoal.About(2500, groupIndex = 1), surplus.rangeFor(saturday, PresetId.Full))
        val more = requireNotNull(surplus.stepSurplus(up = true, group = 1)?.stepSurplus(up = true, group = 1))
        assertEquals(DayGoal.About(2600, groupIndex = 1), more.rangeFor(saturday, PresetId.Full))
        assertEquals(DayGoal.About(2270, groupIndex = 1), more.rangeFor(saturday, PresetId.Full, burnKcal = 2172))
        assertNull(surplus.stepSurplus(up = false, group = 1))
        assertNull(surplus.stepSurplus(up = true, group = 0), "group 0 is a range")
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
