package com.opson.libraries.nutrition

import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The small pure pieces: the weight trend EMA (alpha 0.2, seeded with the
 * first weigh-in, as in the prototype's weight screen), unit conversion, and
 * the fasting plans.
 *
 * Not covered here: which presets allow fasting ([PresetsTest]).
 */
class MathTest {

    private fun assertClose(expected: Double, actual: Double) =
        assertTrue(kotlin.math.abs(expected - actual) < 1e-9, "expected $expected, was $actual")

    @Test
    fun ema_emptyIsEmpty() {
        assertEquals(emptyList(), WeightTrend.ema(emptyList()))
    }

    @Test
    fun ema_seedsWithTheFirstValue() {
        assertEquals(listOf(70.0), WeightTrend.ema(listOf(70.0)))
    }

    @Test
    fun ema_weightsEachNewPointByAlpha() {
        val trend = WeightTrend.ema(listOf(70.0, 72.0, 71.0))
        assertEquals(3, trend.size)
        assertClose(70.0, trend[0])
        assertClose(70.4, trend[1])
        assertClose(70.52, trend[2])
    }

    @Test
    fun ema_defaultAlphaIsPointTwo() {
        assertEquals(0.2, WeightTrend.DEFAULT_ALPHA)
    }

    @Test
    fun ema_alphaOneFollowsTheData() {
        assertEquals(listOf(70.0, 72.0, 71.0), WeightTrend.ema(listOf(70.0, 72.0, 71.0), alpha = 1.0))
    }

    @Test
    fun ema_rejectsAnAlphaOutsideZeroToOne() {
        assertFailsWith<IllegalArgumentException> { WeightTrend.ema(listOf(70.0), alpha = 0.0) }
        assertFailsWith<IllegalArgumentException> { WeightTrend.ema(listOf(70.0), alpha = 1.5) }
    }

    @Test
    fun water_flOzAt2957Ml() {
        assertEquals(29.57, Units.ML_PER_FL_OZ)
        assertClose(1.0, Units.mlToFlOz(29.57))
        assertClose(295.7, Units.flOzToMl(10.0))
        assertClose(250.0, Units.flOzToMl(Units.mlToFlOz(250.0)))
    }

    @Test
    fun weight_kgAndLb() {
        assertClose(0.45359237, Units.lbToKg(1.0))
        assertClose(2.2046226218487757, Units.kgToLb(1.0))
        assertClose(72.6, Units.lbToKg(Units.kgToLb(72.6)))
    }

    @Test
    fun water_defaultGoalIsEightGlassesOf250() {
        assertEquals(2000, WaterDefaults.GOAL_ML)
    }

    @Test
    fun fasting_offersExactlyThreePlansUpTo16Hours() {
        assertEquals(listOf("12:12", "14:10", "16:8"), FastingPlan.entries.map { it.key })
        FastingPlan.entries.forEach {
            assertTrue(it.fastingHours <= Fasting.MAX_FASTING_HOURS, it.key)
            assertEquals(24, it.fastingHours + it.eatingHours, it.key)
        }
        assertEquals(16, Fasting.MAX_FASTING_HOURS)
    }

    @Test
    fun fasting_windowsMatchThePrototype() {
        assertEquals(LocalTime(7, 0) to LocalTime(19, 0), FastingPlan.TwelveTwelve.opens to FastingPlan.TwelveTwelve.closes)
        assertEquals(LocalTime(9, 0) to LocalTime(19, 0), FastingPlan.FourteenTen.opens to FastingPlan.FourteenTen.closes)
        assertEquals(LocalTime(11, 0) to LocalTime(19, 0), FastingPlan.SixteenEight.opens to FastingPlan.SixteenEight.closes)
    }

    @Test
    fun fasting_defaultPlanIs14_10AndUnknownKeysAreRejected() {
        assertEquals(FastingPlan.FourteenTen, FastingPlan.Default)
        assertEquals(FastingPlan.SixteenEight, FastingPlan.fromKey("16:8"))
        assertNull(FastingPlan.fromKey("18:6"))
        assertNull(FastingPlan.fromKey("20:4"))
    }
}
