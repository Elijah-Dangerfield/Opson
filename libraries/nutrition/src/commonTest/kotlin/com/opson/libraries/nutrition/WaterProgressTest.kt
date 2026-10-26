package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals

class WaterProgressTest {

    @Test
    fun glasses_wholeGlassesOfTheGoal() {
        assertEquals(WaterGlasses(3, 8), WaterProgress.glasses(750.0, 250, 8))
        assertEquals(WaterGlasses(2, 8), WaterProgress.glasses(749.0, 250, 8))
        assertEquals(WaterGlasses(0, 8), WaterProgress.glasses(0.0, 250, 8))
    }

    @Test
    fun glasses_floatingPointSumsStillFill() {
        val ml = List(8) { 29.57 * (250 / 29.57) }.sum()
        assertEquals(WaterGlasses(8, 8), WaterProgress.glasses(ml, 250, 8))
    }

    @Test
    fun glasses_neverPastTheGoalAndNeverNegative() {
        assertEquals(WaterGlasses(8, 8), WaterProgress.glasses(4000.0, 250, 8))
        assertEquals(WaterGlasses(0, 6), WaterProgress.glasses(-250.0, 250, 6))
    }

    @Test
    fun glasses_degenerateInputs() {
        assertEquals(WaterGlasses(0, 8), WaterProgress.glasses(500.0, 0, 8))
        assertEquals(WaterGlasses(0, 8), WaterProgress.glasses(Double.NaN, 250, 8))
    }

    @Test
    fun glassSize_mlAndFlOz() {
        assertEquals("250 ml", WaterProgress.glassSize(250, WaterUnit.Ml))
        assertEquals("2,000 ml", WaterProgress.glassSize(2000, WaterUnit.Ml))
        // The prototype rounds 250 / 29.57 = 8.45 to 8.
        assertEquals("8 fl oz", WaterProgress.glassSize(250, WaterUnit.FlOz))
        assertEquals("17 fl oz", WaterProgress.glassSize(500, WaterUnit.FlOz))
    }
}
