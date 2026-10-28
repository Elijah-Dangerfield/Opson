package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Heights typed in centimetres or feet and inches, stored in centimetres, and the region's units. */
class HeightTest {

    @Test
    fun feetAndInches_convertToCentimetres() {
        assertEquals(182.88, Heights.cm(6, 0.0), 1e-9)
        assertEquals(181.61, Heights.cm(5, 11.5), 1e-9)
        assertEquals(152.4, Heights.cm(5, 0.0), 1e-9)
    }

    @Test
    fun feetAndInches_roundTrip_toTheHalfInch() {
        for (feet in 3..8) {
            for (halves in 0 until 24) {
                val typed = FeetInches(feet, halves / 2.0)
                assertEquals(typed, Heights.feetInches(Heights.cm(typed.feet, typed.inches)), "$typed")
            }
        }
    }

    @Test
    fun fiveElevenAndAHalf_comesBackAsTyped() {
        val stored = HeightInput(HeightUnit.FtIn, feet = "5", inches = "11.5").cmOrNull()!!
        assertEquals(HeightInput(HeightUnit.FtIn, feet = "5", inches = "11.5"), HeightInput.of(stored, HeightUnit.FtIn))
    }

    @Test
    fun almostTwelveInches_carriesIntoTheNextFoot() {
        // 5'11.8" rounds to 6'0", never 5'12".
        assertEquals(FeetInches(6, 0.0), Heights.feetInches(Heights.cm(5, 11.8)))
    }

    @Test
    fun centimetres_roundTripThroughFeetAndInches_withinAQuarterInch() {
        // 250 cm is 8'2.4", which shows as 8'2.5" and reads back just over the top.
        for (cm in 100..249) {
            val back = HeightInput.of(cm.toDouble(), HeightUnit.FtIn).cmOrNull()!!
            assertTrue(kotlin.math.abs(back - cm) <= Heights.CM_PER_INCH / 4, "$cm came back as $back")
        }
    }

    @Test
    fun blankInches_areZero() {
        assertEquals(182.88, HeightInput(HeightUnit.FtIn, feet = "6").cmOrNull()!!, 1e-9)
    }

    @Test
    fun theRange_holdsInBothUnits() {
        assertEquals(100.0, HeightInput(HeightUnit.Cm, cm = "100").cmOrNull())
        assertEquals(250.0, HeightInput(HeightUnit.Cm, cm = "250").cmOrNull())
        assertNull(HeightInput(HeightUnit.Cm, cm = "99.9").cmOrNull())
        assertNull(HeightInput(HeightUnit.Cm, cm = "1680").cmOrNull(), "an extra digit")

        // 3'3.5" is 100.33 cm, 3'3" is 99.06; 8'2" is 248.92, 8'2.5" is 250.19.
        assertEquals(100.33, HeightInput(HeightUnit.FtIn, feet = "3", inches = "3.5").cmOrNull()!!, 1e-9)
        assertNull(HeightInput(HeightUnit.FtIn, feet = "3", inches = "3").cmOrNull())
        assertEquals(248.92, HeightInput(HeightUnit.FtIn, feet = "8", inches = "2").cmOrNull()!!, 1e-9)
        assertNull(HeightInput(HeightUnit.FtIn, feet = "8", inches = "2.5").cmOrNull())
    }

    @Test
    fun typos_inFeetAndInches_areNotAHeight() {
        assertNull(HeightInput(HeightUnit.FtIn, feet = "5", inches = "12").cmOrNull(), "12 inches is a foot")
        assertNull(HeightInput(HeightUnit.FtIn, feet = "", inches = "70").cmOrNull(), "feet are needed")
        assertNull(HeightInput(HeightUnit.FtIn, feet = "5.5", inches = "").cmOrNull(), "feet are whole")
        assertNull(HeightInput(HeightUnit.FtIn, feet = "5", inches = ".").cmOrNull())
    }

    @Test
    fun switchingUnits_keepsAHeight_andClearsATypo() {
        val metric = HeightInput(HeightUnit.Cm, cm = "180")
        val imperial = metric.inUnit(HeightUnit.FtIn)
        assertEquals(HeightInput(HeightUnit.FtIn, feet = "5", inches = "11"), imperial)
        assertEquals(HeightInput(HeightUnit.Cm, cm = "180"), imperial.inUnit(HeightUnit.Cm))

        assertEquals(HeightInput(HeightUnit.FtIn), HeightInput(HeightUnit.Cm, cm = "18").inUnit(HeightUnit.FtIn))
        assertEquals(metric, metric.inUnit(HeightUnit.Cm))
    }

    @Test
    fun aStoredHeight_fillsTheFields() {
        assertEquals(HeightInput(HeightUnit.Cm, cm = "168"), HeightInput.of(167.64, HeightUnit.Cm))
        assertEquals(HeightInput(HeightUnit.FtIn, feet = "5", inches = "6"), HeightInput.of(167.64, HeightUnit.FtIn))
        assertEquals(HeightInput(HeightUnit.FtIn), HeightInput.of(null, HeightUnit.FtIn))
    }

    @Test
    fun theUnitedStates_startsInFeetAndPounds_everywhereElseMetric() {
        assertEquals(HeightUnit.FtIn, RegionUnits.height("US"))
        assertEquals(WeightUnit.Lb, RegionUnits.weight("US"))
        assertEquals(HeightUnit.FtIn, RegionUnits.height("lr"))
        assertEquals(HeightUnit.Cm, RegionUnits.height("GB"))
        assertEquals(WeightUnit.Kg, RegionUnits.weight("DE"))
        assertEquals(HeightUnit.Cm, RegionUnits.height(null))
        assertEquals(WeightUnit.Kg, RegionUnits.weight(null))
    }
}
