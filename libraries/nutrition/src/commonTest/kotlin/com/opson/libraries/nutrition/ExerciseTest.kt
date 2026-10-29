package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercise's energy and the rule for which activity a day's Burned counts
 * (docs/research/energy-expenditure.md).
 */
class ExerciseTest {

    private val resting = RestingEnergy(1600, RestingFrom.Calculated)

    @Test
    fun estimate_isMetLessRestingTimesKgTimesHours() {
        // Brisk walking, 4.8 MET: (4.8 - 1) x 70 x 0.5 = 133
        assertEquals(133, ExerciseEnergy.estimateKcal(4.8, 70.0, 30))
        // Running at 10 min/mile, 9.3 MET, an hour at 80 kg: 8.3 x 80 = 664
        assertEquals(664, ExerciseEnergy.estimateKcal(9.3, 80.0, 60))
        // Sitting still is resting energy, which Burned already has.
        assertEquals(0, ExerciseEnergy.estimateKcal(1.0, 70.0, 60))
        assertEquals(0, ExerciseEnergy.estimateKcal(0.9, 70.0, 60), "never below zero")
    }

    @Test
    fun estimate_needsAWeightAndALength() {
        assertNull(ExerciseEnergy.estimateKcal(4.8, null, 30), "no weigh-in yet: the number is typed")
        assertNull(ExerciseEnergy.estimateKcal(4.8, 7.0, 30), "a weight that is a typo")
        assertNull(ExerciseEnergy.estimateKcal(4.8, 70.0, 0))
        assertNull(ExerciseEnergy.estimateKcal(4.8, 70.0, ExerciseEnergy.MAX_MINUTES + 1))
        assertEquals(ExerciseEnergy.MAX_KCAL, ExerciseEnergy.estimateKcal(23.0, 300.0, 600), "capped where the server caps it")
    }

    @Test
    fun theList_hasUniqueKeys_realMets_andNoOther() {
        val keys = ExerciseActivities.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(ExerciseActivities.OTHER !in keys)
        assertTrue(ExerciseActivities.all.all { it.met in 1.0..20.0 && it.compendiumCode.length == 5 })
        assertEquals(4.8, ExerciseActivities["walking_brisk"]?.met)
        assertNull(ExerciseActivities["other"])
    }

    @Test
    fun everydayActivity_isAShareOfResting_roundedToTen() {
        assertEquals(320, EverydayActivity.MostlySitting.kcalFor(1600))
        assertEquals(600, EverydayActivity.SomeWalking.kcalFor(1600))
        assertEquals(880, EverydayActivity.OnMyFeet.kcalFor(1600))
        assertEquals(1160, EverydayActivity.PhysicalWork.kcalFor(1600))
        assertEquals(EverydayActivity.MostlySitting, EverydayActivity.Default)
    }

    @Test
    fun withoutAHealthApp_burnedIsRestingPlusEverydayPlusAllExercise() {
        val burned = burnedFor(resting, health = null, EverydayActivity.MostlySitting, ExerciseTotals(allKcal = 250, notOnWatchKcal = 0), activityCounted = true)

        assertEquals(1600 + 320 + 250, burned.totalKcal)
        assertNull(burned.movementKcal)
        assertNull(burned.workoutKcal)
    }

    @Test
    fun withAHealthApp_itsActivityReplacesEverydayAndLoggedExercise_exceptWhatTheWatchMissed() {
        val burned = burnedFor(
            resting,
            health = HealthActivity(movementKcal = 400, workoutKcal = 200),
            everyday = EverydayActivity.OnMyFeet,
            exercise = ExerciseTotals(allKcal = 450, notOnWatchKcal = 150),
            activityCounted = true,
        )

        assertEquals(1600 + 400 + 200 + 150, burned.totalKcal)
        assertNull(burned.everydayKcal, "the watch's movement is the measured version of it")
        assertEquals(150, burned.exerciseKcal)
    }

    @Test
    fun activitySwitchedOff_keepsOnlyTheEverydayBaseline_whateverTheSource() {
        val physical = EverydayActivity.PhysicalWork.kcalFor(1600)
        assertEquals(1600 + physical, burnedFor(resting, null, EverydayActivity.PhysicalWork, ExerciseTotals(500, 0), activityCounted = false).totalKcal)
        assertEquals(
            1600 + physical,
            burnedFor(resting, HealthActivity(300, 100), EverydayActivity.PhysicalWork, ExerciseTotals(500, 500), activityCounted = false).totalKcal,
            "the watch's reading and logged exercise stop counting; the ordinary day doesn't",
        )
        assertEquals(1600, burnedFor(resting, HealthActivity(300, 100), null, ExerciseTotals(500, 500), activityCounted = false).totalKcal)
    }

    @Test
    fun withTheReleaseOff_burnedIsWhatItWasBefore() {
        assertEquals(1600, burnedFor(resting, null, everyday = null, exercise = null, activityCounted = true).totalKcal)
        assertEquals(
            Burned(resting, movementKcal = 300, workoutKcal = 100),
            burnedFor(resting, HealthActivity(300, 100), everyday = null, exercise = null, activityCounted = true),
        )
    }

    @Test
    fun aDeficit_isAimedAtBurnedWithLoggedExercise() {
        val burned = burnedFor(resting, null, EverydayActivity.MostlySitting, ExerciseTotals(300, 0), activityCounted = true)
        val deficit = Targets.Default.copy(startingKcal = 1500, style = TargetStyle.Deficit(500, 500))

        assertEquals(
            DayGoal.About(1600 + 320 + 300 - 500),
            deficit.rangeFor(kotlinx.datetime.LocalDate(2026, 10, 27), PresetId.Full, burned.totalKcal),
        )
    }

    @Test
    fun digestingFood_isATenthOfWhatWasEaten_inWholeKcal() {
        assertEquals(0.10, THERMIC_EFFECT_OF_FOOD_SHARE)
        assertEquals(125, thermicEffectKcal(1245.0))
        assertEquals(0, thermicEffectKcal(0.0))
        assertEquals(0, thermicEffectKcal(-40.0))
        assertEquals(0, thermicEffectKcal(Double.NaN))
    }

    @Test
    fun digestingFood_addsToBurnedOnEitherSide_andIsNotActivity() {
        val manual = burnedFor(resting, null, EverydayActivity.MostlySitting, ExerciseTotals(250, 0), activityCounted = true, foodKcal = 125)
        assertEquals(1600 + 320 + 250 + 125, manual.totalKcal)
        assertEquals(320 + 250, manual.activityKcal, "activity is the same with or without it")
        assertEquals(320 + 250 + 125, manual.aboveRestingKcal)

        val health = burnedFor(resting, HealthActivity(400, 200), EverydayActivity.MostlySitting, ExerciseTotals.None, activityCounted = true, foodKcal = 125)
        assertEquals(1600 + 400 + 200 + 125, health.totalKcal, "the health app's energy never includes it, so nothing counts twice")

        val off = burnedFor(resting, HealthActivity(400, 200), null, null, activityCounted = false, foodKcal = 125)
        assertEquals(1600 + 125, off.totalKcal, "switching activity off doesn't switch digestion off")
    }

    @Test
    fun digestingFood_off_leavesBurnedAsItWas() {
        val burned = burnedFor(resting, null, EverydayActivity.MostlySitting, ExerciseTotals.None, activityCounted = true)
        assertNull(burned.foodKcal)
        assertEquals(1600 + 320, burned.totalKcal)
    }
}
