package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals

/** The day's eaten ± (design sync 4): σ per food by how it was logged, √Σσ², rounded to 10. */
class EatenUncertaintyTest {

    @Test
    fun eachWayOfLogging_hasItsOwnShare() {
        fun one(loggedAs: LoggedAs) = EatenUncertainty.of(listOf(EatenEntry(1000.0, loggedAs))).plusMinusKcal
        assertEquals(50, one(LoggedAs.Weighed))
        assertEquals(100, one(LoggedAs.Label))
        assertEquals(250, one(LoggedAs.Estimate))
        assertEquals(150, one(LoggedAs.QuickAdd))
        assertEquals(400, one(LoggedAs.AteNoDetails))
    }

    @Test
    fun theDay_addsInQuadrature_notStraight() {
        // Two 400 kcal estimates: σ 100 each, √(100² + 100²) = 141, so 140, not 200.
        val day = EatenUncertainty.of(listOf(EatenEntry(400.0, LoggedAs.Estimate), EatenEntry(400.0, LoggedAs.Estimate)))
        assertEquals(140, day.plusMinusKcal)
        assertEquals(2, day.estimates)
        assertEquals(2, day.foods)
    }

    @Test
    fun weighedAndLabelled_areNotEstimates() {
        val day = EatenUncertainty.of(
            listOf(
                EatenEntry(300.0, LoggedAs.Weighed),
                EatenEntry(200.0, LoggedAs.Label),
                EatenEntry(100.0, LoggedAs.QuickAdd),
                EatenEntry(500.0, LoggedAs.AteNoDetails),
            ),
        )
        assertEquals(2, day.estimates)
        assertEquals(4, day.foods)
    }

    @Test
    fun nothingLogged_isNoSpread_andABadNumberAddsNone() {
        assertEquals(EatenUncertainty.None, EatenUncertainty.of(emptyList()))
        assertEquals(0, EatenUncertainty.of(listOf(EatenEntry(Double.NaN, LoggedAs.Estimate), EatenEntry(-80.0, LoggedAs.Estimate))).plusMinusKcal)
    }
}
