package com.opson.libraries.nutrition

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Burned (design sync 4): resting energy for the whole day from the source picked, falling back
 * to the calculation, plus activity synced so far when it counts.
 */
class BurnedTest {

    private val profile = BodyProfile(weightKg = 70.0, heightCm = 170.0, ageYears = 35, sex = BodySex.Female)

    @Test
    fun mifflinStJeor_roundedToTen() {
        // 700 + 1,062.5 - 175 - 161 = 1,426.5
        assertEquals(1430, RestingEnergies.calculate(profile))
        // + 5 instead of - 161: 1,592.5
        assertEquals(1590, RestingEnergies.calculate(profile.copy(sex = BodySex.Male)))
        // Halfway between the two constants: 1,509.5
        assertEquals(1510, RestingEnergies.calculate(profile.copy(sex = BodySex.Unsaid)))
    }

    @Test
    fun anythingMissingOrImpossible_hasNoAnswer() {
        assertNull(RestingEnergies.calculate(profile.copy(heightCm = null)))
        assertNull(RestingEnergies.calculate(profile.copy(weightKg = null)))
        assertNull(RestingEnergies.calculate(profile.copy(ageYears = null)))
        assertNull(RestingEnergies.calculate(profile.copy(sex = null)))
        assertNull(RestingEnergies.calculate(profile.copy(heightCm = 17.0)), "a height typed in the wrong unit")
        assertNull(RestingEnergies.calculate(profile.copy(ageYears = 8)))
        assertEquals(setOf(BodyDetail.Height, BodyDetail.Sex), profile.copy(heightCm = null, sex = null).missing)
    }

    @Test
    fun theHealthAppAndYourOwnNumber_fallBackToTheCalculation() {
        assertEquals(RestingEnergy(1560, RestingFrom.Health), RestingEnergies.resolve(RestingSource.Health, 1610, 1562, null))
        assertEquals(RestingEnergy(1610, RestingFrom.Calculated), RestingEnergies.resolve(RestingSource.Health, 1610, null, null))
        assertEquals(RestingEnergy(1600, RestingFrom.Own), RestingEnergies.resolve(RestingSource.Own, 1610, null, 1600))
        assertEquals(RestingEnergy(1610, RestingFrom.Calculated), RestingEnergies.resolve(RestingSource.Own, 1610, null, 40), "a stray number")
        assertEquals(RestingEnergy(1610, RestingFrom.Calculated), RestingEnergies.resolve(RestingSource.Calculate, 1610, 1560, 1600))
        assertNull(RestingEnergies.resolve(RestingSource.Calculate, null, 1560, 1600))
    }

    @Test
    fun burned_isRestingPlusActivity_whenActivityCounts() {
        val burned = Burned(RestingEnergy(1610, RestingFrom.Calculated), movementKcal = 390, workoutKcal = 180)
        assertEquals(2180, burned.totalKcal)
        assertEquals(1610, burned.copy(activityCounted = false).totalKcal)
        assertEquals(1610, burned.copy(movementKcal = null, workoutKcal = null).totalKcal, "no health app is no activity")
        assertEquals(1610, burned.copy(movementKcal = -50, workoutKcal = 0).totalKcal, "a bad reading never takes away")
    }

    @Test
    fun aDeficitOrSurplus_aimsAtBurned() {
        val burned = Burned(RestingEnergy(1610, RestingFrom.Calculated), movementKcal = 390, workoutKcal = 180)
        val deficit = Targets.Default.copy(startingKcal = 1500, style = TargetStyle.Deficit(500, 500))
        assertEquals(DayGoal.About(1680), deficit.rangeFor(LocalDate(2026, 10, 27), PresetId.Full, burned.totalKcal))
    }
}
