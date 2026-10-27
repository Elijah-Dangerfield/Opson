package com.opson.libraries.nutrition

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun withoutAHealthApp_everydayMovementIsTheEverydayActivityEstimate() {
        val resting = RestingEnergy(1610, RestingFrom.Calculated)
        assertEquals(320, EverydayActivity.MostlySitting.kcalFor(1610), "1,610 × 0.2 = 322, rounded to 10")
        assertEquals(600, EverydayActivity.SomeWalking.kcalFor(1610))
        assertEquals(890, EverydayActivity.OnMyFeet.kcalFor(1610))
        assertEquals(1170, EverydayActivity.PhysicalWork.kcalFor(1610))
        // release.exercise off: no logged exercise, and everyday activity still counts.
        val estimated = burnedFor(resting, health = null, EverydayActivity.SomeWalking, exercise = null, activityCounted = true)
        assertTrue(estimated.movementEstimated)
        assertEquals(2210, estimated.totalKcal)
        val logged = burnedFor(resting, health = null, EverydayActivity.SomeWalking, ExerciseTotals(180, 0), activityCounted = true)
        assertEquals(2390, logged.totalKcal, "logged exercise adds on top")
        val off = burnedFor(resting, health = null, EverydayActivity.SomeWalking, exercise = null, activityCounted = false)
        assertEquals(1610, off.totalKcal, "Add activity to Burned off is resting energy only, whatever the source")
        assertFalse(off.movementEstimated)
    }

    @Test
    fun aHealthAppReading_replacesTheEstimate_evenAZeroOne() {
        val resting = RestingEnergy(1610, RestingFrom.Calculated)
        val burned = burnedFor(resting, HealthActivity(movementKcal = 0, workoutKcal = null), EverydayActivity.PhysicalWork, exercise = null, activityCounted = true)
        assertNull(burned.everydayKcal)
        assertFalse(burned.movementEstimated)
        assertEquals(1610, burned.totalKcal)
    }

    @Test
    fun theEstimate_risesWithTheLevel_andNeverGoesNegative() {
        listOf(0, 900, 1430, 1610, 2400, 5000).forEach { resting ->
            val estimates = EverydayActivity.entries.map { it.kcalFor(resting) }
            assertEquals(estimates.sorted(), estimates, "at $resting")
            assertTrue(estimates.all { it >= 0 && it % 10 == 0 })
        }
        assertEquals(0, EverydayActivity.PhysicalWork.kcalFor(-100))
    }

    @Test
    fun aDeficitOrSurplus_aimsAtBurned() {
        val burned = Burned(RestingEnergy(1610, RestingFrom.Calculated), movementKcal = 390, workoutKcal = 180)
        val deficit = Targets.Default.copy(startingKcal = 1500, style = TargetStyle.Deficit(500, 500))
        assertEquals(DayGoal.About(1680), deficit.rangeFor(LocalDate(2026, 10, 27), PresetId.Full, burned.totalKcal))
    }
}
