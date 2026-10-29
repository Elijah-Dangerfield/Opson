package com.opson.libraries.health.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Health Connect's resting energy, worked out from total and active. The reads themselves need a
 * device and are not covered here.
 */
class HealthConnectEnergyTest {

    @Test
    fun restingIsTotalLessActive() {
        assertEquals(1_760.0, HealthConnectEnergy.resting(totalKcal = 2_600.0, activeKcal = 840.0))
    }

    /** Before: with no active reading the whole day's 2,600 came back as resting energy. */
    @Test
    fun withoutAnActiveReading_theTotalIsNotRestingEnergy() {
        assertNull(HealthConnectEnergy.resting(totalKcal = 2_600.0, activeKcal = null))
        assertNull(HealthConnectEnergy.resting(totalKcal = null, activeKcal = 300.0))
    }

    @Test
    fun sumsThatDisagree_neverGoBelowZero() {
        assertEquals(0.0, HealthConnectEnergy.resting(totalKcal = 200.0, activeKcal = 300.0))
    }
}
