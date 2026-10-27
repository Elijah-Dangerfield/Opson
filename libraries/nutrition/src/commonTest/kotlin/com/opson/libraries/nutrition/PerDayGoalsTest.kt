package com.opson.libraries.nutrition

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Different by day with every goal type: a range on Monday, a deficit on Tuesday, a surplus on
 * Thursday, the rest of the week on the base range. Each day resolves to its own group's goal,
 * and the floor and the deficit and surplus caps hold per group.
 */
class PerDayGoalsTest {

    private val base = Targets.Default
    private val monday = LocalDate(2026, 10, 26)
    private val tuesday = LocalDate(2026, 10, 27)
    private val thursday = LocalDate(2026, 10, 29)
    private val sunday = LocalDate(2026, 11, 1)

    /**
     * Group 0 the rest of the week on 1,900 to 2,300; Monday, Tuesday and Thursday in their own
     * groups, each raised to 2,000 to 2,400. Tuesday's deficit is picked against [deficitBurn],
     * Thursday's surplus against [surplusBurn].
     */
    private fun week(deficitBurn: Int?, surplusBurn: Int? = 1900): Targets {
        var targets = base.useByDay()
        listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY).forEachIndexed { index, day ->
            val group = index + 1
            targets = requireNotNull(targets.addDayGroup()).moveDay(day, group)
            repeat(2) { targets = requireNotNull(targets.step(RangeEnd.High, up = true, group = group)) }
            repeat(2) { targets = requireNotNull(targets.step(RangeEnd.Low, up = true, group = group)) }
        }
        targets = requireNotNull(targets.shapeGroup(2, GroupShape.Deficit(0, 0), deficitBurn))
        return requireNotNull(targets.shapeGroup(3, GroupShape.Surplus(0, 0), surplusBurn))
    }

    private fun Targets.shape(group: Int): GroupShape = (style as TargetStyle.ByDay).groups[group].shape

    @Test
    fun eachDay_resolvesToItsOwnGroupsGoal() {
        val targets = week(deficitBurn = 2900)
        assertEquals(DayGoal.Range(2000, 2400, 2200, groupIndex = 1), targets.rangeFor(monday, PresetId.Full, burnKcal = 2900))
        // Tuesday's middle is 2,200; against a 2,900 burn the deficit starts at 700, so the goal is 2,200.
        assertEquals(GroupShape.Deficit(700, 700), targets.shape(2))
        assertEquals(DayGoal.About(2200, groupIndex = 2), targets.rangeFor(tuesday, PresetId.Full, burnKcal = 2900))
        // Thursday's surplus started at 300 over a 1,900 burn.
        assertEquals(GroupShape.Surplus(300, 300), targets.shape(3))
        assertEquals(DayGoal.About(2400, groupIndex = 3), targets.rangeFor(thursday, PresetId.Full, burnKcal = 2100))
        assertEquals(DayGoal.Range(1900, 2300, 2100, groupIndex = 0), targets.rangeFor(sunday, PresetId.Full, burnKcal = 2900))
    }

    @Test
    fun aDeficitDay_movesWithWhatYouBurn_neverUnderTheFloor() {
        val targets = week(deficitBurn = 2900)
        assertEquals(DayGoal.About(2500, groupIndex = 2), targets.rangeFor(tuesday, PresetId.Full, burnKcal = 3200))
        assertEquals(DayGoal.About(2100, groupIndex = 2), targets.rangeFor(tuesday, PresetId.Full, burnKcal = 2000), "the floor, where this person started")
    }

    @Test
    fun aDeficitDay_isItsRange_untilTheBurnIsKnown_orOutsideAllNumbers() {
        val targets = week(deficitBurn = 2900)
        assertEquals(DayGoal.Range(2000, 2400, 2200, groupIndex = 2), targets.rangeFor(tuesday, PresetId.Full, burnKcal = null))
        assertEquals(DayGoal.Range(2000, 2400, 2200, groupIndex = 2), targets.rangeFor(tuesday, PresetId.MacrosWithGrams, burnKcal = 2900))
    }

    @Test
    fun aDeficitDay_canComeDown_butNotGrowPastWhereItStarted() {
        val targets = week(deficitBurn = 2900)
        assertNull(targets.stepDeficit(up = true, group = 2), "700 is where it started, its cap while MAX_DEFICIT_KCAL is unset")
        val less = requireNotNull(targets.stepDeficit(up = false, group = 2))
        assertEquals(GroupShape.Deficit(650, 700), less.shape(2))
        assertEquals(700, less.maxDeficitKcal(group = 2))
        assertEquals(GroupShape.Deficit(700, 700), requireNotNull(less.stepDeficit(up = true, group = 2)).shape(2))
        assertNull(targets.stepDeficit(up = false, group = 1), "Monday is a range")
        assertNull(targets.stepDeficit(up = false), "the style itself is not a deficit")
    }

    @Test
    fun aDeficitPicked_withNoBurnKnown_startsAndStaysAtZero() {
        val targets = week(deficitBurn = null)
        assertEquals(GroupShape.Deficit(0, 0), targets.shape(2))
        assertNull(targets.stepDeficit(up = true, group = 2))
        assertNull(targets.stepDeficit(up = false, group = 2))
    }

    @Test
    fun aGroupGoingBackToARange_findsItsRangeWhereItLeftIt() {
        val back = requireNotNull(week(deficitBurn = 2900).shapeGroup(2, GroupShape.Range))
        assertEquals(DayGoal.Range(2000, 2400, 2200, groupIndex = 2), back.rangeFor(tuesday, PresetId.Full, burnKcal = 2900))
    }

    @Test
    fun theSpan_runsAcrossEveryShape() {
        val targets = week(deficitBurn = 2900)
        // From the rest of the week's low end, 1,900, to Thursday's 2,900 burn plus 300.
        assertEquals(DayGoal.Range(1900, 3200, 2550), targets.spanGoal(PresetId.Full, burnKcal = 2900))
    }
}
