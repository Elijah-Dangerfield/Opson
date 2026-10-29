package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Resting energy from what a person types, in both unit systems, through the same conversions the
 * app uses ([HeightInput], [Units]). The owner's report: a 5'11", 28-year-old man at 170 lb was
 * shown about 2,600. Mifflin–St Jeor gives 1,763 for him; 2,690 is what 170 read as kilograms gives.
 * Where the app's stored weight unit comes from is `SettingsStoreTest`'s.
 */
class RestingEnergyUnitsTest {

    private val imperialHeightCm = requireNotNull(HeightInput(HeightUnit.FtIn, feet = "5", inches = "11").cmOrNull())

    @Test
    fun imperialInputs_giveAbout1763() {
        val profile = BodyProfile(weightKg = Units.lbToKg(170.0), heightCm = imperialHeightCm, ageYears = 28, sex = BodySex.Male)
        val working = requireNotNull(RestingEnergies.working(profile))

        assertEquals(180.34, imperialHeightCm, 0.001)
        assertEquals(1763.0, working.exactKcal, 1.0)
        assertEquals(1760, RestingEnergies.calculate(profile))
    }

    @Test
    fun metricInputs_giveTheSame() {
        val height = requireNotNull(HeightInput(HeightUnit.Cm, cm = "180").cmOrNull())
        val profile = BodyProfile(weightKg = 77.1, heightCm = height, ageYears = 28, sex = BodySex.Male)

        assertEquals(1760, RestingEnergies.calculate(profile))
    }

    /** The symptom, pinned: pounds stored as kilograms is the only input slip that lands near 2,600. */
    @Test
    fun poundsReadAsKilograms_isTheWrongNumber() {
        val profile = BodyProfile(weightKg = 170.0, heightCm = imperialHeightCm, ageYears = 28, sex = BodySex.Male)

        assertEquals(2690, RestingEnergies.calculate(profile))
    }

    @Test
    fun theWorkingAddsUpToTheNumber() {
        val profile = BodyProfile(weightKg = 70.0, heightCm = 170.0, ageYears = 35, sex = BodySex.Female)
        val working = requireNotNull(RestingEnergies.working(profile))

        assertEquals(700.0, working.weightTerm)
        assertEquals(1062.5, working.heightTerm)
        assertEquals(-175.0, working.ageTerm)
        assertEquals(-161.0, working.sexTerm)
        assertEquals(RestingEnergies.calculate(profile), working.kcal)
        assertTrue(RestingEnergies.working(profile.copy(sex = null)) == null)
    }
}
