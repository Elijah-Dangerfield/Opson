package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A target typed rather than stepped: the same limits as a step, and any number between them. */
class TargetsTypedTest {

    private val base = Targets.Default

    @Test
    fun aRangeEnd_takesAnyNumber_betweenTheLimits() {
        val low = base.withEnd(RangeEnd.Low, 1875)
        assertEquals(1875 to 2300, low?.minKcal to low?.maxKcal)
        assertEquals((1875 + 2300) / 2, low?.energyKcal, "the aim stays the middle of the range")
        assertEquals(2600, base.withEnd(RangeEnd.High, 2600)?.maxKcal)
    }

    @Test
    fun aRangeEnd_pastALimit_isRefused_withTheReason() {
        assertEquals(StepBlock.Floor, base.endBlock(RangeEnd.Low, MIN_GOAL_KCAL - 1))
        assertEquals(StepBlock.Ceiling, base.endBlock(RangeEnd.High, MAX_GOAL_KCAL + 1))
        assertEquals(StepBlock.Gap, base.endBlock(RangeEnd.Low, 2280), "the ends can't come within a step")
        assertNull(base.withEnd(RangeEnd.Low, 2280))
        assertNull(base.withEnd(RangeEnd.Low, 15), "a number on its way to 1500 writes nothing")
    }

    @Test
    fun aDayGroupsEnds_andNumber_areTypedTheSameWay() {
        val byDay = base.useByDay().addDayGroup()!!.shapeGroup(1, GroupShape.Exact)!!
        assertEquals(2500, byDay.withEnd(RangeEnd.High, 2500, group = 0)?.let { (it.style as TargetStyle.ByDay).groups[0].maxKcal })
        assertEquals(2400, byDay.withGroupNumber(2400, group = 1)?.let { (it.style as TargetStyle.ByDay).groups[1].midKcal })
        assertNull(byDay.withGroupNumber(MIN_GOAL_KCAL - 50, group = 1))
    }

    @Test
    fun aDeficitOrSurplus_isHeldToItsCap() {
        val deficit = base.useDeficit(burnKcal = 2700)
        assertEquals(600, (deficit.style as TargetStyle.Deficit).kcal)
        assertEquals(450, (deficit.withDeficit(450)?.style as? TargetStyle.Deficit)?.kcal)
        assertNull(deficit.withDeficit(650), "past where it started, while no clinician has set a cap")
        assertNull(deficit.withDeficit(-1))
        val surplus = base.useSurplus(burnKcal = 1800)
        assertEquals(125, (surplus.withSurplus(125)?.style as? TargetStyle.Surplus)?.kcal)
        assertNull(surplus.withSurplus(301))
        assertNull(base.withDeficit(100), "only a deficit can take one")
    }
}
