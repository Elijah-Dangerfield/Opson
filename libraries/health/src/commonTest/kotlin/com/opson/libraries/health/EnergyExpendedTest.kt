package com.opson.libraries.health

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnergyExpendedTest {

    @Test
    fun movement_isActiveLessWorkouts() {
        assertEquals(260.0, EnergyExpended(restingKcal = 1580.0, activeKcal = 410.0, workoutKcal = 150.0).movementKcal)
    }

    @Test
    fun movement_neverGoesBelowZero_whenTheSumsDisagree() {
        assertEquals(0.0, EnergyExpended(restingKcal = null, activeKcal = 100.0, workoutKcal = 140.0).movementKcal)
    }

    @Test
    fun movement_isNullWithoutActiveEnergy() {
        assertNull(EnergyExpended(restingKcal = 1580.0, activeKcal = null, workoutKcal = 150.0).movementKcal)
    }

    @Test
    fun total_isRestingPlusActive() {
        assertEquals(1990.0, EnergyExpended(restingKcal = 1580.0, activeKcal = 410.0, workoutKcal = 150.0).totalKcal)
    }

    @Test
    fun total_usesWhicheverPartExists() {
        assertEquals(410.0, EnergyExpended(restingKcal = null, activeKcal = 410.0, workoutKcal = null).totalKcal)
        assertEquals(1580.0, EnergyExpended(restingKcal = 1580.0, activeKcal = null, workoutKcal = null).totalKcal)
    }

    @Test
    fun total_isNullOnlyWhenNeitherPartExists() {
        assertNull(EnergyExpended(restingKcal = null, activeKcal = null, workoutKcal = 150.0).totalKcal)
    }

    @Test
    fun aRecordedZero_isAValue() {
        assertEquals(0.0, EnergyExpended(restingKcal = 0.0, activeKcal = 0.0, workoutKcal = 0.0).totalKcal)
    }
}
