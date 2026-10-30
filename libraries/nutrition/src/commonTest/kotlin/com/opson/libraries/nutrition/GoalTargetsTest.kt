package com.opson.libraries.nutrition

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A goal's target is a pure function of the goal and its baseline (owner feedback: "Use my goal"
 * landed somewhere new every time), and what the baseline is made of.
 */
class GoalTargetsTest {

    private val start = LocalDate(2026, 10, 30)

    private val inputs = BaselineInputs(
        restingFrom = RestingFrom.Calculated,
        restingKcal = 1800,
        activityFrom = ActivityFrom.Everyday,
        everyday = EverydayActivity.SomeWalking,
        activityKcal = 680,
        digesting = false,
        weightKg = 90.0,
        heightCm = 180.0,
        ageYears = 30,
        sex = BodySex.Male,
    )

    private fun lose(kgPerWeek: Double = 0.5, tolerance: Int = 100) = Goal(
        id = Goal.WEIGHT_ID,
        type = GoalType.Weight(GoalDirection.Lose, targetKg = 80.0, kgPerWeek = kgPerWeek, startKg = 90.0),
        startDate = start,
        toleranceKcal = tolerance,
        baseline = GoalBaselines.of(inputs),
    )

    @Test
    fun theTarget_isTheBurnPlusTheDelta_withAFixedBand() {
        val target = GoalTargets.of(lose())!!
        assertEquals(1930, target.aimKcal, "2,480 less 550")
        assertEquals(1830, target.minKcal)
        assertEquals(2030, target.maxKcal)
        assertEquals(-550, target.deltaKcal)
    }

    @Test
    fun applyingTheSameGoal_landsOnTheSameTargets_whateverWasThere() {
        val goal = lose()
        val starts = listOf(
            Targets.Default,
            Targets.Default.raise().raise().raise(),
            Targets.Default.step(RangeEnd.Low, up = false)!!.step(RangeEnd.Low, up = false)!!,
            Targets.Default.useExact().withNumber(3000),
            Targets.Default.useDeficit(2500),
            Targets.Default.useByDay(),
        )
        val results = starts.map { GoalTargets.apply(goal, it, scaleMacros = false)!!.second }
        results.forEach {
            assertEquals(TargetStyle.Range, it.style)
            assertEquals(1930, it.energyKcal)
            assertEquals(1830 to 2030, it.minKcal to it.maxKcal)
        }
        // Applying again to its own result changes nothing, macros included.
        val once = GoalTargets.apply(goal, Targets.Default, scaleMacros = true)!!.second
        val twice = GoalTargets.apply(goal, once, scaleMacros = true)!!.second
        assertEquals(once, twice)
    }

    @Test
    fun applying_stampsTheGoal() {
        val (stamped, _) = GoalTargets.apply(lose().copy(setsTarget = false), Targets.Default, scaleMacros = false)!!
        assertEquals(1930, stamped.targetsKcal)
        assertTrue(stamped.setsTarget)
    }

    @Test
    fun aLoss_neverAimsUnderTheLowGoalLine() {
        val small = lose(kgPerWeek = 1.5).copy(baseline = GoalBaseline(burnKcal = 1900, sex = BodySex.Female))
        assertEquals(1200, GoalTargets.of(small)!!.aimKcal)
    }

    @Test
    fun digestingFood_isSolvedForTheTarget() {
        // (2,480 - 550) / 0.9 = 2,144: eating that burns 214 digesting it, so 2,694 out and 550 under.
        val goal = lose().copy(baseline = GoalBaselines.of(inputs.copy(digesting = true)))
        val target = GoalTargets.of(goal)!!
        assertEquals(2140, target.aimKcal)
        assertEquals(2480 + 214, target.burnKcal)
    }

    @Test
    fun pregnancyAddsItsEnergy_andAnEnergyBalanceGoalWorksTheSameWay() {
        val balance = Goal("balance", GoalType.EnergyBalance(-500), start, baseline = GoalBaselines.of(inputs.copy(stage = BodyStage.Breastfeeding)))
        assertEquals(2480 + 330 - 500, GoalTargets.of(balance)!!.aimKcal)
        assertNull(GoalTargets.of(Goal("meals", GoalType.Meals(), start, baseline = GoalBaselines.of(inputs))))
        assertNull(GoalTargets.of(lose().copy(baseline = null)), "no baseline, no target")
    }

    @Test
    fun aTargetPointingAgainstTheGoal_isSpotted() {
        val goal = lose()
        val surplus = Targets.Default.withBand(2600, 2500, 2700)
        assertTrue(GoalTargets.contradicts(goal, surplus, PresetId.Full))
        assertFalse(GoalTargets.contradicts(goal, Targets.Default, PresetId.Full))
        val gain = goal.copy(type = GoalType.Weight(GoalDirection.Gain, kgPerWeek = 0.25))
        assertTrue(GoalTargets.contradicts(gain, Targets.Default, PresetId.Full), "2,100 is under 2,480")
        val keep = goal.copy(type = GoalType.Weight(GoalDirection.Maintain))
        assertFalse(GoalTargets.contradicts(keep, Targets.Default.withBand(2300, 2200, 2400), PresetId.Full))
        assertTrue(GoalTargets.contradicts(keep, Targets.Default.withBand(1900, 1800, 2000), PresetId.Full))
    }

    @Test
    fun theBaselineTakesTheHealthAverage_onlyWithThreeDaysOfReadings() {
        val resting = RestingEnergy(1800, RestingFrom.Health)
        val profile = BodyProfile(90.0, 180.0, 30, BodySex.Male)
        val two = GoalBaselines.inputs(resting, EverydayActivity.SomeWalking, listOf(600, 700), true, false, profile, BodyStage.None, null)
        assertEquals(ActivityFrom.Everyday, two.activityFrom)
        assertEquals(680, two.activityKcal)
        val week = GoalBaselines.inputs(resting, EverydayActivity.SomeWalking, listOf(610, 720, 540, 660), true, false, profile, BodyStage.None, null)
        assertEquals(ActivityFrom.Health, week.activityFrom)
        assertEquals(650, week.activityKcal, "632.5, to the nearest 50")
        val notCounted = GoalBaselines.inputs(resting, EverydayActivity.SomeWalking, listOf(610, 720, 540, 660), false, false, profile, BodyStage.None, null)
        assertEquals(ActivityFrom.Everyday, notCounted.activityFrom)
    }

    @Test
    fun connectingHealth_offersARecalculation_withTheOldAndNewTarget() {
        val goal = lose()
        val withHealth = inputs.copy(restingFrom = RestingFrom.Health, restingKcal = 1900, activityFrom = ActivityFrom.Health, activityKcal = 800)
        val offer = GoalBaselines.recalculation(goal, withHealth)!!
        assertEquals(setOf(BaselineChange.RestingSource, BaselineChange.ActivitySource), offer.changes)
        assertEquals(1930, offer.oldAimKcal)
        assertEquals(2150, offer.newAimKcal)
    }

    @Test
    fun sameInputs_offerNothing() {
        assertNull(GoalBaselines.recalculation(lose(), inputs))
    }

    @Test
    fun drift_waitsForAHundredKcal_butSomethingThePersonDidNeedsOnlyTen() {
        val goal = lose()
        // 4 kg lighter: resting 40 less. Under the drift threshold.
        assertNull(GoalBaselines.recalculation(goal, inputs.copy(weightKg = 86.0, restingKcal = 1760, activityKcal = 660)))
        // 9 kg lighter: about 120 less. Offered.
        assertEquals(
            setOf(BaselineChange.Weight, BaselineChange.Resting),
            GoalBaselines.recalculation(goal, inputs.copy(weightKg = 81.0, restingKcal = 1710, activityKcal = 640))?.changes,
        )
        // A new activity level, even a small step, is offered.
        assertEquals(
            setOf(BaselineChange.ActivityLevel),
            GoalBaselines.recalculation(goal, inputs.copy(everyday = EverydayActivity.MostlySitting, activityKcal = 360))?.changes,
        )
        // Body fat changes the limit, not the target: never offered on its own.
        assertNull(GoalBaselines.recalculation(goal, inputs.copy(bodyFatPercent = 20.0)))
    }

    @Test
    fun keepingTheTarget_isNotAskedAgainForTheSameInputs() {
        val switched = inputs.copy(digesting = true)
        val goal = lose()
        assertEquals(setOf(BaselineChange.DigestingFood), GoalBaselines.recalculation(goal, switched)?.changes)
        val kept = goal.copy(declined = switched)
        assertNull(GoalBaselines.recalculation(kept, switched))
        // A further change is asked about.
        val more = switched.copy(everyday = EverydayActivity.OnMyFeet, activityKcal = 990)
        assertEquals(2490, GoalBaselines.recalculation(kept, more)?.newAimKcal)
    }

    @Test
    fun aGoalNotSettingTheTarget_isNeverOfferedOne() {
        assertNull(GoalBaselines.recalculation(lose().copy(setsTarget = false), inputs.copy(digesting = true)))
        assertNull(GoalBaselines.recalculation(lose().copy(status = GoalStatus.Paused), inputs.copy(digesting = true)))
    }

    @Test
    fun aGoalWithNoBaselineYet_isOfferedOne() {
        val offer = GoalBaselines.recalculation(lose().copy(baseline = null), inputs)!!
        assertNull(offer.oldAimKcal)
        assertEquals(1930, offer.newAimKcal)
    }

    @Test
    fun aGoalWithNoBaseline_keptThrough_isNotAskedAgain_untilTheInputsMove() {
        val kept = lose().copy(baseline = null, declined = inputs)
        assertNull(GoalBaselines.recalculation(kept, inputs), "Keep sticks")
        assertNull(GoalBaselines.recalculation(kept, inputs.copy(weightKg = 89.6)), "a weigh-in's drift isn't worth asking again")
        assertEquals(1630, GoalBaselines.recalculation(kept, inputs.copy(activityKcal = 380, everyday = EverydayActivity.MostlySitting))?.newAimKcal)
    }

    @Test
    fun aMigratedGoal_withNoInputs_isJudgedByTheTargetAlone() {
        val legacy = lose().copy(baseline = GoalBaseline(burnKcal = 2480, sex = BodySex.Male))
        assertNull(GoalBaselines.recalculation(legacy, inputs.copy(restingKcal = 1850)), "50 more is drift")
        assertEquals(setOf(BaselineChange.Unknown), GoalBaselines.recalculation(legacy, inputs.copy(restingKcal = 1900))?.changes)
    }
}
