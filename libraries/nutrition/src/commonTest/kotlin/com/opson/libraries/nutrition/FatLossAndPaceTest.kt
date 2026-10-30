package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The personal fat-loss limit (Alpert 2005), body fat from Profile or Deurenberg's estimate, and the pace tiers. */
class FatLossAndPaceTest {

    @Test
    fun theLimit_isAbout69KcalAKilogramOfFat_fromProfileBodyFat() {
        // 80 kg at 25%: 20 kg of fat, about 1,384 kcal a day.
        val limit = FatLoss.limitFor(profilePercent = 25.0, weightKg = 80.0, heightCm = null, ageYears = null, sex = null)!!
        assertEquals(20.0, limit.fatMassKg, 1e-9)
        assertEquals(1380, limit.kcalPerDay)
        assertEquals(BodyFatFrom.Profile, limit.bodyFat.from)
        // The owner's case: about 700 a day is roughly 10 kg of fat.
        assertEquals(690, FatLoss.limitFor(12.5, 80.0, null, null, null)!!.kcalPerDay)
    }

    @Test
    fun withoutBodyFat_DeurenbergEstimatesIt_andSaysSo() {
        // 30-year-old man, 180 cm, 90 kg: BMI 27.8, so 1.2 x 27.8 + 6.9 - 10.8 - 5.4 = 24.0%.
        val estimate = FatLoss.bodyFat(null, 90.0, 180.0, 30, BodySex.Male)!!
        assertEquals(BodyFatFrom.Estimated, estimate.from)
        assertEquals(24.03, estimate.percent, 0.01)
        // A woman of the same build carries more by the formula's sex term.
        assertEquals(34.83, FatLoss.bodyFat(null, 90.0, 180.0, 30, BodySex.Female)!!.percent, 0.01)
        // Not said is halfway.
        assertEquals(29.43, FatLoss.bodyFat(null, 90.0, 180.0, 30, BodySex.Unsaid)!!.percent, 0.01)
        // Teenagers get the children's formula.
        assertEquals(1.51 * 20.0 - 0.70 * 15 - 3.6 * 0.5 + 1.4, FatLoss.deurenberg(20.0, 15, null), 1e-9)
    }

    @Test
    fun aProfileNumberWins_andAnOutOfRangeOneIsIgnored() {
        assertEquals(BodyFatFrom.Profile, FatLoss.bodyFat(18.0, 90.0, 180.0, 30, BodySex.Male)!!.from)
        assertEquals(BodyFatFrom.Estimated, FatLoss.bodyFat(0.0, 90.0, 180.0, 30, BodySex.Male)!!.from)
    }

    @Test
    fun noBodyFat_andNotEnoughForAnEstimate_isNoLimit() {
        assertNull(FatLoss.limitFor(null, 90.0, null, 30, BodySex.Male), "no height")
        assertNull(FatLoss.limitFor(null, 90.0, 180.0, null, BodySex.Male), "no age")
        assertNull(FatLoss.limitFor(20.0, null, 180.0, 30, BodySex.Male), "no weight")
    }

    @Test
    fun anEstimateIsHeldInsideFiveToSixtyPercent() {
        assertEquals(60.0, FatLoss.bodyFat(null, 250.0, 150.0, 80, BodySex.Female)!!.percent)
    }

    @Test
    fun lossTiers_goByShareOfBodyWeight() {
        val big = 150.0
        assertEquals(PaceTier.Gentle, GoalPace.assess(GoalDirection.Lose, 0.75, big, null).tier)
        assertEquals(PaceTier.Steady, GoalPace.assess(GoalDirection.Lose, 1.5, big, null).tier, "1% of 150 kg, about 1,650 a day")
        assertEquals(PaceTier.Faster, GoalPace.assess(GoalDirection.Lose, 2.0, big, null).tier)
    }

    @Test
    fun aDeficitPastTheFatLimit_winsOverTheTier() {
        // 70 kg at 15%: 10.5 kg of fat, a limit of about 730.
        val limit = FatLoss.limitFor(15.0, 70.0, null, null, null)!!
        assertEquals(730, limit.kcalPerDay)
        // 0.5 kg a week is 550 a day: steady, under the limit.
        assertEquals(PaceTier.Steady, GoalPace.assess(GoalDirection.Lose, 0.5, 70.0, limit).tier)
        // 0.7 kg a week is 1% (steady by share) but 770 a day, past the limit.
        val past = GoalPace.assess(GoalDirection.Lose, 0.7, 70.0, limit)
        assertEquals(PaceTier.AboveFatLimit, past.tier)
        assertTrue(past.needsCare)
    }

    @Test
    fun withoutAWeight_theTiersGoByTheDeficit() {
        assertEquals(PaceTier.Gentle, GoalPace.assessDelta(-500, null, null).tier)
        assertEquals(PaceTier.Steady, GoalPace.assessDelta(-1000, null, null).tier)
        assertEquals(PaceTier.Faster, GoalPace.assessDelta(-1100, null, null).tier)
    }

    @Test
    fun gainTiers_areHalfTheLoss() {
        assertEquals(PaceTier.Gentle, GoalPace.assess(GoalDirection.Gain, 0.2, 80.0, null).tier)
        assertEquals(PaceTier.Steady, GoalPace.assess(GoalDirection.Gain, 0.4, 80.0, null).tier)
        assertEquals(PaceTier.Faster, GoalPace.assess(GoalDirection.Gain, 0.5, 80.0, null).tier)
        assertFalse(GoalPace.assess(GoalDirection.Gain, 0.2, 80.0, null).needsCare)
    }
}
