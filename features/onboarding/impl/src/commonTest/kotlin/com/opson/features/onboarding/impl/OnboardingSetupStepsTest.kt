package com.opson.features.onboarding.impl

import com.opson.features.onboarding.OnboardingRoute
import com.opson.libraries.core.logging.EXTRA_APP_EVENT
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogId
import com.opson.libraries.core.logging.LogTree
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.HeightInput
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.WeightUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.LocalTime

/**
 * The setup steps after the starting mode (`release.onboardingSetup`, docs/plans/onboarding-v2.md):
 *
 *  - Off, Start saves straight away, whatever the mode and whatever `release.goalsAndEnergy` says.
 *  - Goal numbers only for someone comfortable with them: a mode that shows calories (Full
 *    numbers) and no "numbers have been hard". Everyone else gets no goal step and no goal written.
 *  - With `release.goalsAndEnergy`, the resting energy step comes first and the goal starts from
 *    the ordinary day it gives, never under the floor. Without it, the goal is the range alone.
 *  - "Set this later" goes past both and writes neither.
 *  - Height in centimetres or feet and inches, weight in kilograms or pounds, starting in the
 *    region's units and saved in centimetres and kilograms.
 *  - The meal times step for No numbers and for eating regularly.
 *  - Only the steps shown write settings; Back walks the steps; no event carries a setup value.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingSetupStepsTest : CoroutineTest() {

    private val events = RecordingTree()

    @AfterTest
    fun tearDown() {
        KLog.uproot(events)
    }

    // ---------- the flags ----------

    @Test
    fun flagOff_startSavesStraightAway_evenInFullNumbers_andWithGoalsAndEnergyOn() = runUnitTest {
        listOf(false, true).forEach { goalsAndEnergy ->
            val settings = FakeSettingsRepository()
            val vm = atRecommendation(listOf(NumbersFeel.LoveThem), setupSteps = false, goalsAndEnergy = goalsAndEnergy, settings = settings)
            assertTrue(vm.state.setupFlow.isEmpty())

            vm.takeAction(OnboardingAction.StartTapped)
            runCurrent()

            assertTrue(vm.state.isFinishing, "saved and on the way to Today")
            assertEquals(Targets.Default, settings.settings.value.targets)
            assertNull(settings.settings.value.heightUnit)
        }
    }

    // ---------- who gets goal numbers ----------

    @Test
    fun goalNumbers_onlyForAModeThatShowsCalories_andNeverAfterNumbersHaveBeenHard() = runUnitTest {
        val feels = NumbersFeel.entries + listOf<NumbersFeel?>(null)
        val statements = StatementAnswer.entries + listOf<StatementAnswer?>(null)
        feels.forEach { feel ->
            statements.forEach { statement ->
                val answers = listOfNotNull<SetupOption>(feel, statement)
                val vm = atRecommendation(answers, goalsAndEnergy = true)
                val comfortable = vm.state.chosen == PresetId.Full && statement != StatementAnswer.Agree
                val goalSteps = vm.state.setupFlow.filter { it == OnboardingStep.Body || it == OnboardingStep.Goal }
                assertEquals(
                    if (comfortable) listOf(OnboardingStep.Body, OnboardingStep.Goal) else emptyList(),
                    goalSteps,
                    "feel $feel, statement $statement, starting in ${vm.state.chosen}",
                )
            }
        }
    }

    @Test
    fun someoneWhoAgreedNumbersWereHard_neverSeesAGoal_andHasNoGoalWritten() = runUnitTest {
        listOf(false, true).forEach { goalsAndEnergy ->
            val settings = FakeSettingsRepository()
            val vm = atRecommendation(listOf(NumbersFeel.LoveThem, StatementAnswer.Agree), goalsAndEnergy = goalsAndEnergy, settings = settings)

            assertEquals(PresetId.MacrosWithGrams, vm.state.chosen)
            assertFalse(vm.state.showsGoal)
            assertFalse(vm.state.showsBody)
            vm.takeAction(OnboardingAction.StartTapped)
            runCurrent()
            assertTrue(vm.state.isFinishing, "saved straight away, no setup step")
            assertEquals(Targets.Default, settings.settings.value.targets)
            assertNull(settings.settings.value.birthYear)
        }
    }

    @Test
    fun ratherNotSeeingNumbers_goesToTheMealTimes_withNoGoal() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atRecommendation(listOf(NumbersFeel.RatherNot), goalsAndEnergy = true, settings = settings)
        assertEquals(PresetId.NoNumbers, vm.state.chosen)
        assertEquals(listOf(OnboardingStep.Meals), vm.state.setupFlow)

        vm.takeAction(OnboardingAction.StartTapped)
        vm.takeAction(OnboardingAction.MealTimePicked(MealSlot.Breakfast, LocalTime(9, 30)))
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()

        val saved = settings.settings.value
        assertEquals(MealTimes(breakfast = LocalTime(9, 30)), saved.mealTimes)
        assertEquals(Targets.Default, saved.targets, "no goal step, so no goal written")
        assertEquals(PresetId.NoNumbers, saved.numbers.preset)
    }

    @Test
    fun someAreFine_startsInMacrosOnly_withNoGoalStep() = runUnitTest {
        val vm = atRecommendation(listOf(NumbersFeel.SomeAreFine), goalsAndEnergy = true)
        assertEquals(PresetId.MacrosOnly, vm.state.chosen)
        assertTrue(vm.state.setupFlow.isEmpty())
    }

    @Test
    fun pickingFullNumbersYourself_offersTheGoal_unlessNumbersHaveBeenHard() = runUnitTest {
        // The answer said no numbers, but the person chose every number; the goal is theirs to set.
        val vm = atRecommendation(listOf(NumbersFeel.RatherNot), goalsAndEnergy = true)
        vm.takeAction(OnboardingAction.AllModesTapped)
        vm.takeAction(OnboardingAction.AlternativeTapped(PresetId.Full))
        runCurrent()
        assertEquals(listOf(OnboardingStep.Body, OnboardingStep.Goal), vm.state.setupFlow)
    }

    // ---------- the goal without release.goalsAndEnergy ----------

    @Test
    fun fullNumbers_opensTheGoalStep_thenSavesTheRange() = runUnitTest {
        KLog.plant(events)
        val settings = FakeSettingsRepository()
        val vm = atRecommendation(listOf(NumbersFeel.LoveThem), settings = settings)

        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()
        assertEquals(OnboardingStep.Goal, vm.state.step)
        assertTrue(settings.stampFlags.isEmpty(), "nothing is saved until the last setup step")
        assertEquals(listOf(GoalChoice.Range), vm.state.goalView?.styles)
        assertEquals(Display.Shown("1,900 to 2,300 kcal"), vm.state.goalView?.amount)
        assertEquals(Display.Hidden, vm.state.goalView?.basedOn, "no resting energy step, so nothing to start from")

        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()

        assertTrue(vm.state.isFinishing, "saved and on the way to Today")
        assertEquals(TargetStyle.Range, settings.settings.value.targets.style)
        assertEquals(listOf(false), settings.stampFlags)
        assertEquals("goal", events.steps().last())
    }

    @Test
    fun goalStepper_raisesAndLowers_butNeverUnderTheFloor() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atGoal(listOf(NumbersFeel.LoveThem), settings = settings)
        assertFalse(vm.state.goalView!!.canLower, "the starting range is the floor while no dietitian has set one")

        vm.takeAction(OnboardingAction.GoalStepped(up = false))
        runCurrent()
        assertEquals(Targets.Default.minKcal, vm.state.goal.targets.minKcal, "lower at the floor does nothing")

        repeat(3) { vm.takeAction(OnboardingAction.GoalStepped(up = true)) }
        vm.takeAction(OnboardingAction.GoalStepped(up = false))
        runCurrent()
        assertEquals(Display.Shown("2,000 to 2,400 kcal"), vm.state.goalView?.amount)

        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        val saved = settings.settings.value.targets
        assertEquals(2000 to 2400, saved.minKcal to saved.maxKcal)
        assertEquals(Targets.Default.startingKcal, saved.startingKcal, "raising never moves the floor")
    }

    @Test
    fun withoutGoalsAndEnergy_onlyTheRangeIsOffered_andThereIsNoRestingEnergyStep() = runUnitTest {
        val vm = atGoal(listOf(NumbersFeel.LoveThem), goalsAndEnergy = false)

        vm.takeAction(OnboardingAction.GoalStyleTapped(GoalChoice.Deficit))
        runCurrent()

        assertEquals(GoalChoice.Range, vm.state.goal.style)
        assertEquals(listOf(OnboardingStep.Goal), vm.state.setupFlow)
    }

    @Test
    fun fullNumbersAndEatingRegularly_walksGoalThenMeals_andBackAgain() = runUnitTest {
        val vm = atRecommendation(listOf(Why.EatingRegularly, NumbersFeel.LoveThem))
        assertEquals(listOf(OnboardingStep.Goal, OnboardingStep.Meals), vm.state.setupFlow)

        vm.takeAction(OnboardingAction.StartTapped)
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(OnboardingStep.Meals, vm.state.step)

        vm.takeAction(OnboardingAction.Back)
        runCurrent()
        assertEquals(OnboardingStep.Goal, vm.state.step)
        vm.takeAction(OnboardingAction.Back)
        runCurrent()
        assertEquals(OnboardingStep.Recommendation, vm.state.step)
    }

    // ---------- resting energy, then the goal ----------

    @Test
    fun withGoalsAndEnergy_restingEnergyComesFirst_thenTheGoal_thenMeals_andBackAgain() = runUnitTest {
        val vm = atRecommendation(listOf(Why.EatingRegularly, NumbersFeel.LoveThem), goalsAndEnergy = true)
        assertEquals(listOf(OnboardingStep.Body, OnboardingStep.Goal, OnboardingStep.Meals), vm.state.setupFlow)

        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()
        assertEquals(OnboardingStep.Body, vm.state.step)
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(OnboardingStep.Goal, vm.state.step)
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(OnboardingStep.Meals, vm.state.step)

        listOf(OnboardingStep.Goal, OnboardingStep.Body, OnboardingStep.Recommendation).forEach { expected ->
            vm.takeAction(OnboardingAction.Back)
            runCurrent()
            assertEquals(expected, vm.state.step)
        }
    }

    @Test
    fun theGoal_startsFromAnOrdinaryDaysBurn_andSaysSo() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atBody(listOf(NumbersFeel.LoveThem), settings = settings)
        typeBody(vm, OnboardingFixtures.Body)
        vm.takeAction(OnboardingAction.ActivityPicked(EverydayActivity.PhysicalWork))
        runCurrent()
        // 1,360 resting and 990 of a very active day.
        assertEquals(Display.Shown("2,350 kcal"), vm.state.bodyView?.burned)

        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(Display.Shown("2,150 to 2,550 kcal"), vm.state.goalView?.amount, "the range around 2,350")
        assertEquals(Display.Shown("2,350 kcal"), vm.state.goalView?.basedOn)
        assertTrue(vm.state.goalView!!.canLower, "down to the app's starting range, and no further")

        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        val saved = settings.settings.value.targets
        assertEquals(2350, saved.energyKcal)
        assertEquals(Targets.Default.startingKcal, saved.floorKcal, "starting higher never raises the floor")
        // Ratio mode, the default: the macros moved with the energy.
        assertEquals(134, saved.proteinG)
    }

    @Test
    fun aLowEstimate_startsAtTheAppsStartingRange_neverUnderIt() = runUnitTest {
        val vm = atBody(listOf(NumbersFeel.LoveThem))
        typeBody(vm, OnboardingFixtures.Body)
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()

        // An ordinary day of 1,630 is under the floor while no dietitian has set one.
        assertEquals(Targets.Default.minKcal to Targets.Default.maxKcal, vm.state.goal.targets.minKcal to vm.state.goal.targets.maxKcal)
        assertEquals(Display.Hidden, vm.state.goalView?.basedOn, "the goal did not start from the burn, so it says nothing about it")
        assertFalse(vm.state.goalView!!.canLower)
    }

    @Test
    fun aGoalTheyMoved_staysPut_whenTheDetailsChange_andOneTheyDidNot_follows() = runUnitTest {
        val vm = atBody(listOf(NumbersFeel.LoveThem))
        typeBody(vm, OnboardingFixtures.Body)
        vm.takeAction(OnboardingAction.ActivityPicked(EverydayActivity.PhysicalWork))
        vm.takeAction(OnboardingAction.ContinueTapped)
        vm.takeAction(OnboardingAction.Back)
        vm.takeAction(OnboardingAction.ActivityPicked(EverydayActivity.OnMyFeet))
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        // 1,360 and 750 on their feet: 2,110, to the nearest step 2,100.
        assertEquals(2100, vm.state.goal.targets.energyKcal, "not moved by hand, so it follows the new estimate")

        vm.takeAction(OnboardingAction.GoalStepped(up = true))
        vm.takeAction(OnboardingAction.Back)
        vm.takeAction(OnboardingAction.ActivityPicked(EverydayActivity.PhysicalWork))
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(2150, vm.state.goal.targets.energyKcal, "moved by hand, so a new estimate leaves it")
    }

    @Test
    fun oneNumber_savesExact_onTheSteppedAim() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atGoal(listOf(NumbersFeel.LoveThem), goalsAndEnergy = true, settings = settings)

        vm.takeAction(OnboardingAction.GoalStyleTapped(GoalChoice.OneNumber))
        vm.takeAction(OnboardingAction.GoalStepped(up = true))
        runCurrent()
        assertEquals(Display.Shown("2,150 kcal"), vm.state.goalView?.amount)

        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(TargetStyle.Exact, settings.settings.value.targets.style)
        assertEquals(2150, settings.settings.value.targets.energyKcal)
    }

    @Test
    fun aSetSurplus_isAimedAtTheBurn_savesTheDetails_andLogsTheWeightOnce() = runUnitTest {
        val settings = FakeSettingsRepository()
        val weights = FakeWeightRepository()
        val vm = atBody(listOf(NumbersFeel.LoveThem), settings = settings, weights = weights)

        vm.takeAction(OnboardingAction.BirthYearTyped("1992x"))
        typeBody(vm, OnboardingFixtures.Body)
        runCurrent()
        assertEquals("1992", vm.state.body.birthYear, "only digits reach the field")
        assertEquals(Display.Shown("1,360 kcal"), vm.state.bodyView?.resting)
        assertEquals(Display.Shown("1,630 kcal"), vm.state.bodyView?.burned, "mostly sitting until the person says otherwise")

        vm.takeAction(OnboardingAction.ContinueTapped)
        vm.takeAction(OnboardingAction.GoalStyleTapped(GoalChoice.Surplus))
        runCurrent()
        assertEquals(Display.Hidden, vm.state.goalView?.amount, "a surplus follows the burn; no stepper here")
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()

        val saved = settings.settings.value
        assertEquals(1992, saved.birthYear)
        assertEquals(168.0, saved.heightCm)
        assertEquals(BodySex.Female, saved.bodySex)
        // The aim, 2,100 (the floor holds it there), over an ordinary day's 1,630: 470, floored to 50, and its own cap.
        assertEquals(TargetStyle.Surplus(kcal = 450, startingKcal = 450), saved.targets.style)
        assertEquals(EverydayActivity.MostlySitting, saved.everydayActivity)
        assertEquals(listOf(64.0), weights.added)

        // Saving again is blocked, and the weigh-in is never logged twice.
        vm.takeAction(OnboardingAction.Back)
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(listOf(64.0), weights.added)
    }

    @Test
    fun aSetDeficit_pickedStraightAway_isNone_soTheGoalIsTheAim() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atBody(listOf(NumbersFeel.LoveThem), settings = settings)
        typeBody(vm, OnboardingFixtures.Body)
        listOf(
            OnboardingAction.ActivityPicked(EverydayActivity.PhysicalWork),
            OnboardingAction.ContinueTapped,
            OnboardingAction.GoalStyleTapped(GoalChoice.Deficit),
            OnboardingAction.ContinueTapped,
        ).forEach(vm::takeAction)
        runCurrent()

        // The goal started at the burn, 2,350, so nothing is under it yet; and it can't grow from there.
        val targets = settings.settings.value.targets
        assertEquals(TargetStyle.Deficit(kcal = 0, startingKcal = 0), targets.style)
        assertEquals(Targets.Default.startingKcal, targets.floorKcal)
    }

    @Test
    fun aSetDeficit_afterLoweringTheRange_isWhatTheLoweredAimImplies() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atBody(listOf(NumbersFeel.LoveThem), settings = settings)
        typeBody(vm, OnboardingFixtures.Body)
        vm.takeAction(OnboardingAction.ActivityPicked(EverydayActivity.PhysicalWork))
        vm.takeAction(OnboardingAction.ContinueTapped)
        repeat(10) { vm.takeAction(OnboardingAction.GoalStepped(up = false)) }
        vm.takeAction(OnboardingAction.GoalStyleTapped(GoalChoice.Deficit))
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()

        // Lowered to the floor, 2,100, against 2,350 burned: 250, and that is also as far as it can go.
        assertEquals(TargetStyle.Deficit(kcal = 250, startingKcal = 250), settings.settings.value.targets.style)
    }

    @Test
    fun continuingPastBlankDetails_startsTheGoalAtTheAppsRange_andWritesNoDetails() = runUnitTest {
        val settings = FakeSettingsRepository()
        val weights = FakeWeightRepository()
        val vm = atBody(listOf(NumbersFeel.LoveThem), settings = settings, weights = weights)
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(Targets.Default.energyKcal, vm.state.goal.targets.energyKcal)

        vm.takeAction(OnboardingAction.GoalStyleTapped(GoalChoice.Deficit))
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        val saved = settings.settings.value
        assertIs<TargetStyle.Deficit>(saved.targets.style)
        assertNull(saved.birthYear)
        assertNull(saved.heightCm)
        assertTrue(weights.added.isEmpty())
    }

    // ---------- set this later ----------

    @Test
    fun setThisLater_onRestingEnergy_skipsTheGoal_andWritesNeither() = runUnitTest {
        val settings = FakeSettingsRepository()
        val weights = FakeWeightRepository()
        val vm = atBody(listOf(NumbersFeel.LoveThem), settings = settings, weights = weights)
        typeBody(vm, OnboardingFixtures.Body)

        vm.takeAction(OnboardingAction.GoalLaterTapped)
        runCurrent()

        assertTrue(vm.state.isFinishing, "no step after the goal, so it saved")
        val saved = settings.settings.value
        assertEquals(Targets.Default, saved.targets)
        assertNull(saved.birthYear)
        assertNull(saved.heightCm)
        assertTrue(weights.added.isEmpty())
        assertEquals(PresetId.Full, saved.numbers.preset)
    }

    @Test
    fun setThisLater_onTheGoal_goesOnToMeals_andBackThereSkipsTheGoal() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atBody(listOf(Why.EatingRegularly, NumbersFeel.LoveThem), settings = settings)
        typeBody(vm, OnboardingFixtures.Body)
        vm.takeAction(OnboardingAction.ContinueTapped)
        vm.takeAction(OnboardingAction.GoalStepped(up = true))
        vm.takeAction(OnboardingAction.GoalLaterTapped)
        runCurrent()
        assertEquals(OnboardingStep.Meals, vm.state.step)

        vm.takeAction(OnboardingAction.Back)
        runCurrent()
        assertEquals(OnboardingStep.Recommendation, vm.state.step, "the goal was put off, so Back skips it")

        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()
        assertEquals(OnboardingStep.Body, vm.state.step, "starting again offers the goal again")
        vm.takeAction(OnboardingAction.GoalLaterTapped)
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(Targets.Default, settings.settings.value.targets)
        assertNull(settings.settings.value.birthYear)
    }

    @Test
    fun setThisLater_offTheGoalsSteps_doesNothing() = runUnitTest {
        val vm = atRecommendation(listOf(NumbersFeel.RatherNot), goalsAndEnergy = true)
        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()
        val before = vm.state

        vm.takeAction(OnboardingAction.GoalLaterTapped)
        runCurrent()
        assertEquals(before, vm.state)
    }

    // ---------- units ----------

    @Test
    fun aUsPhone_startsInFeetAndPounds_andElsewhereMetric() = runUnitTest {
        assertEquals(HeightUnit.FtIn, atBody(listOf(NumbersFeel.LoveThem), region = "US").state.body.height.unit)
        assertEquals(WeightUnit.Lb, atBody(listOf(NumbersFeel.LoveThem), region = "US").state.body.weightUnit)
        assertEquals(HeightUnit.Cm, atBody(listOf(NumbersFeel.LoveThem), region = "DE").state.body.height.unit)
        assertEquals(WeightUnit.Kg, atBody(listOf(NumbersFeel.LoveThem), region = null).state.body.weightUnit)
    }

    @Test
    fun feetInchesAndPounds_areSavedInCentimetresAndKilograms_withTheUnitsPicked() = runUnitTest {
        val settings = FakeSettingsRepository()
        val weights = FakeWeightRepository()
        val vm = atBody(listOf(NumbersFeel.LoveThem), settings = settings, weights = weights, region = "US")
        vm.takeAction(OnboardingAction.HeightFeetTyped("5"))
        vm.takeAction(OnboardingAction.HeightInchesTyped("11,5"))
        vm.takeAction(OnboardingAction.WeightTyped("141"))
        vm.takeAction(OnboardingAction.ContinueTapped)
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()

        val saved = settings.settings.value
        assertEquals(WeightUnit.Lb, saved.weightUnit)
        assertEquals(HeightUnit.FtIn, saved.heightUnit)
        assertEquals(181.61, saved.heightCm!!, 0.001)
        assertEquals(63.957, weights.added.single(), 0.001)
    }

    @Test
    fun switchingUnits_carriesOverWhatWasTyped() = runUnitTest {
        val vm = atBody(listOf(NumbersFeel.LoveThem), region = "GB")
        vm.takeAction(OnboardingAction.HeightCmTyped("180"))
        vm.takeAction(OnboardingAction.WeightTyped("64"))
        vm.takeAction(OnboardingAction.HeightUnitPicked(HeightUnit.FtIn))
        vm.takeAction(OnboardingAction.WeightUnitPicked(WeightUnit.Lb))
        runCurrent()

        assertEquals(HeightInput(HeightUnit.FtIn, feet = "5", inches = "11"), vm.state.body.height)
        assertEquals("141.1", vm.state.body.weight)
        assertEquals(64.0, vm.state.body.weightKgOrNull()!!, 0.05)
    }

    @Test
    fun switchingUnits_convertsWhatIsTyped_soTheWeighInIsStillTheirs() = runUnitTest {
        val weights = FakeWeightRepository()
        val vm = atBody(listOf(NumbersFeel.LoveThem), weights = weights, region = "GB")
        vm.takeAction(OnboardingAction.BirthYearTyped("1992"))
        vm.takeAction(OnboardingAction.HeightCmTyped("175"))
        vm.takeAction(OnboardingAction.WeightTyped("70"))
        vm.takeAction(OnboardingAction.SexPicked(BodySex.Female))
        runCurrent()
        val resting = vm.state.bodyView?.resting

        vm.takeAction(OnboardingAction.HeightUnitPicked(HeightUnit.FtIn))
        vm.takeAction(OnboardingAction.WeightUnitPicked(WeightUnit.Lb))
        runCurrent()
        assertEquals(HeightInput(HeightUnit.FtIn, feet = "5", inches = "9"), vm.state.body.height, "175 cm, not 175 in")
        assertEquals("154.3", vm.state.body.weight, "70 kg, not 70 lb")
        assertEquals(resting, vm.state.bodyView?.resting, "the same person, so the same resting energy")

        vm.takeAction(OnboardingAction.HeightUnitPicked(HeightUnit.Cm))
        vm.takeAction(OnboardingAction.WeightUnitPicked(WeightUnit.Kg))
        runCurrent()
        assertEquals("175", vm.state.body.height.cm)
        assertEquals("70", vm.state.body.weight)

        vm.takeAction(OnboardingAction.WeightUnitPicked(WeightUnit.Lb))
        vm.takeAction(OnboardingAction.ContinueTapped)
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(70.0, weights.added.single(), 0.05)
    }

    @Test
    fun switchingUnitsAndBack_givesBackExactlyWhatWasTyped_andSavesIt() = runUnitTest {
        val settings = FakeSettingsRepository()
        val weights = FakeWeightRepository()
        val vm = atBody(listOf(NumbersFeel.LoveThem), settings = settings, weights = weights, region = "GB")
        vm.takeAction(OnboardingAction.HeightCmTyped("172"))
        vm.takeAction(OnboardingAction.WeightUnitPicked(WeightUnit.Lb))
        vm.takeAction(OnboardingAction.WeightTyped("160"))

        vm.takeAction(OnboardingAction.HeightUnitPicked(HeightUnit.FtIn))
        vm.takeAction(OnboardingAction.WeightUnitPicked(WeightUnit.Kg))
        runCurrent()
        assertEquals(HeightInput(HeightUnit.FtIn, feet = "5", inches = "7.5"), vm.state.body.height)
        assertEquals("72.6", vm.state.body.weight)
        vm.takeAction(OnboardingAction.HeightUnitPicked(HeightUnit.Cm))
        vm.takeAction(OnboardingAction.WeightUnitPicked(WeightUnit.Lb))
        runCurrent()
        assertEquals("172", vm.state.body.height.cm, "a mis-tap on ft and in and back is not 171")
        assertEquals("160", vm.state.body.weight, "a mis-tap on kg and back is not 160.1")

        vm.takeAction(OnboardingAction.HeightUnitPicked(HeightUnit.FtIn))
        vm.takeAction(OnboardingAction.WeightUnitPicked(WeightUnit.Kg))
        vm.takeAction(OnboardingAction.ContinueTapped)
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(172.0, settings.settings.value.heightCm!!, 1e-9, "the 172 typed, not 5 ft 7.5 in")
        assertEquals(HeightUnit.FtIn, settings.settings.value.heightUnit, "the unit showing is still the one kept")
        assertEquals(160 * 0.45359237, weights.added.single(), 1e-9, "the 160 lb typed, not 72.6 kg")
    }

    @Test
    fun editingAConvertedField_isWhatCountsFromThen() = runUnitTest {
        val vm = atBody(listOf(NumbersFeel.LoveThem), region = "GB")
        vm.takeAction(OnboardingAction.HeightCmTyped("171"))
        vm.takeAction(OnboardingAction.WeightTyped("72.5"))
        vm.takeAction(OnboardingAction.HeightUnitPicked(HeightUnit.FtIn))
        vm.takeAction(OnboardingAction.WeightUnitPicked(WeightUnit.Lb))
        vm.takeAction(OnboardingAction.HeightInchesTyped("6"))
        vm.takeAction(OnboardingAction.WeightTyped("150"))
        vm.takeAction(OnboardingAction.HeightUnitPicked(HeightUnit.Cm))
        vm.takeAction(OnboardingAction.WeightUnitPicked(WeightUnit.Kg))
        runCurrent()

        assertEquals("168", vm.state.body.height.cm, "the 5 ft 6 typed over the conversion, not the 171 before it")
        assertEquals("68", vm.state.body.weight, "the 150 lb typed over the conversion, not the 72.5 before it")
    }

    @Test
    fun aHeightThatIsNotOne_saysWhatTheFieldWants_inEitherUnit() = runUnitTest {
        val vm = atBody(listOf(NumbersFeel.LoveThem), region = "US")
        assertFalse(vm.state.bodyView!!.heightInvalid, "nothing typed yet, nothing to say")

        vm.takeAction(OnboardingAction.HeightFeetTyped("5"))
        vm.takeAction(OnboardingAction.HeightInchesTyped("13"))
        runCurrent()
        assertTrue(vm.state.bodyView!!.heightInvalid, "13 inches is not under a foot")
        vm.takeAction(OnboardingAction.HeightInchesTyped("10"))
        runCurrent()
        assertFalse(vm.state.bodyView!!.heightInvalid)

        vm.takeAction(OnboardingAction.HeightUnitPicked(HeightUnit.Cm))
        vm.takeAction(OnboardingAction.HeightCmTyped("5.10"))
        runCurrent()
        assertTrue(vm.state.bodyView!!.heightInvalid, "5.10 cm is not 5 ft 10")
        vm.takeAction(OnboardingAction.HeightCmTyped("178"))
        runCurrent()
        assertFalse(vm.state.bodyView!!.heightInvalid)
    }

    @Test
    fun aTypoInTheDetails_isNotSaved_inEitherUnit() = runUnitTest {
        listOf("GB", "US").forEach { region ->
            val settings = FakeSettingsRepository()
            val weights = FakeWeightRepository()
            val vm = atBody(listOf(NumbersFeel.LoveThem), settings = settings, weights = weights, region = region)
            vm.takeAction(OnboardingAction.BirthYearTyped("2020"))
            if (region == "US") {
                vm.takeAction(OnboardingAction.HeightFeetTyped("5"))
                vm.takeAction(OnboardingAction.HeightInchesTyped("12"))
                vm.takeAction(OnboardingAction.WeightTyped("50"))
            } else {
                vm.takeAction(OnboardingAction.HeightCmTyped("1680"))
                vm.takeAction(OnboardingAction.WeightTyped("6"))
            }
            runCurrent()
            assertEquals(Display.Hidden, vm.state.bodyView?.resting)
            val view = vm.state.bodyView!!
            assertTrue(view.birthYearInvalid && view.heightInvalid && view.weightInvalid, "$region: each field says what it wants")
            vm.takeAction(OnboardingAction.ContinueTapped)
            vm.takeAction(OnboardingAction.ContinueTapped)
            runCurrent()

            assertNull(settings.settings.value.birthYear, "someone born in 2020 is not 13")
            assertNull(settings.settings.value.heightCm, "$region height")
            assertTrue(weights.added.isEmpty(), "$region weight: 50 lb and 6 kg are both under 25 kg")
        }
    }

    // ---------- meals, actions and telemetry ----------

    @Test
    fun noNumbers_opensMealTimes_andSavesThem_withNoGoal() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atRecommendation(listOf(NumbersFeel.RatherNot), settings = settings)
        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()
        assertEquals(OnboardingStep.Meals, vm.state.step)
        assertEquals(listOf(OnboardingStep.Meals), vm.state.setupFlow)

        vm.takeAction(OnboardingAction.MealTimePicked(MealSlot.Breakfast, LocalTime(9, 30)))
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()

        val saved = settings.settings.value
        assertEquals(MealTimes(breakfast = LocalTime(9, 30)), saved.mealTimes)
        assertEquals(Targets.Default, saved.targets, "no goal step, so no goal written")
        assertEquals(PresetId.NoNumbers, saved.numbers.preset)
    }

    @Test
    fun setupActions_offTheirStep_doNothing() = runUnitTest {
        val vm = atRecommendation(listOf(NumbersFeel.LoveThem), goalsAndEnergy = true)
        val before = vm.state

        vm.takeAction(OnboardingAction.GoalStepped(up = true))
        vm.takeAction(OnboardingAction.WeightTyped("64"))
        vm.takeAction(OnboardingAction.HeightFeetTyped("5"))
        vm.takeAction(OnboardingAction.HeightUnitPicked(HeightUnit.FtIn))
        vm.takeAction(OnboardingAction.WeightUnitPicked(WeightUnit.Lb))
        vm.takeAction(OnboardingAction.MealTimePicked(MealSlot.Lunch, LocalTime(14, 0)))
        runCurrent()

        assertEquals(before, vm.state)
    }

    @Test
    fun theReason_explainsTheRecommendation_untilAnotherModeIsPicked() = runUnitTest {
        val vm = atRecommendation(listOf(NumbersFeel.SomeAreFine))
        assertEquals(RecommendationReason.SomeAreFine, vm.state.reason)

        vm.takeAction(OnboardingAction.AlternativeTapped(PresetId.MacrosWithGrams))
        runCurrent()
        assertNull(vm.state.reason)
    }

    @Test
    fun noEventAttribute_everCarriesASetupValue() = runUnitTest {
        KLog.plant(events)
        val vm = atBody(listOf(Why.EatingRegularly, NumbersFeel.LoveThem), region = "US")
        listOf(
            OnboardingAction.BirthYearTyped("1992"),
            OnboardingAction.HeightFeetTyped("5"),
            OnboardingAction.HeightInchesTyped("6"),
            OnboardingAction.WeightTyped("141"),
            OnboardingAction.SexPicked(BodySex.Female),
            OnboardingAction.ContinueTapped,
            OnboardingAction.GoalStyleTapped(GoalChoice.Deficit),
            OnboardingAction.ContinueTapped,
            OnboardingAction.MealTimePicked(MealSlot.Breakfast, LocalTime(9, 30)),
            OnboardingAction.ContinueTapped,
        ).forEach(vm::takeAction)
        runCurrent()

        assertEquals(listOf("resting_energy", "goal", "meal_times"), events.steps().takeLast(3))
        val values = events.entries
            .filter { it.context.extras[EXTRA_APP_EVENT].toString().startsWith("onboarding.") }
            .flatMap { entry -> entry.context.extras.values.map { it.toString().lowercase() } }
        val setupWords = listOf("1992", "141", "141.0", "167.64", "female", "deficit", "ft_in", "lb", "9:30", "09:30") +
            GoalChoice.entries.map { it.name.lowercase() } - "range"
        val leaked = values.filter { it in setupWords }
        assertTrue(leaked.isEmpty(), "setup values reached telemetry: $leaked")
    }

    // ---------- scaffolding ----------

    private fun typeBody(vm: OnboardingViewModel, body: BodyAnswer) {
        vm.takeAction(OnboardingAction.BirthYearTyped(body.birthYear))
        vm.takeAction(OnboardingAction.HeightUnitPicked(body.height.unit))
        if (body.height.unit == HeightUnit.Cm) {
            vm.takeAction(OnboardingAction.HeightCmTyped(body.height.cm))
        } else {
            vm.takeAction(OnboardingAction.HeightFeetTyped(body.height.feet))
            vm.takeAction(OnboardingAction.HeightInchesTyped(body.height.inches))
        }
        vm.takeAction(OnboardingAction.WeightUnitPicked(body.weightUnit))
        vm.takeAction(OnboardingAction.WeightTyped(body.weight))
        body.sex?.let { vm.takeAction(OnboardingAction.SexPicked(it)) }
    }

    private fun newVm(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        weights: FakeWeightRepository = FakeWeightRepository(),
        setupSteps: Boolean = true,
        goalsAndEnergy: Boolean = false,
        region: String? = "GB",
    ) = OnboardingViewModel(
        appCache = FakeAppCache(),
        // Signed in to a new account, so onboarding opens at the first question.
        authRepository = FakeAuthRepository(initialAuthState = sampleAuthenticated),
        authOutcomeClassifier = StubAuthOutcomeClassifier(),
        appleSignInCoordinator = FakeAppleSignInCoordinator(),
        settingsRepository = settings,
        weightRepository = weights,
        calendar = FixedCalendar,
        setupEnabled = onboardingSetupFlag(setupSteps),
        goalsAndEnergy = goalsAndEnergyFlag(goalsAndEnergy),
        region = { region },
        route = OnboardingRoute(),
        appleSignIn = true,
    )

    private fun TestScope.atRecommendation(
        answers: List<SetupOption>,
        setupSteps: Boolean = true,
        goalsAndEnergy: Boolean = false,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        weights: FakeWeightRepository = FakeWeightRepository(),
        region: String? = "GB",
    ): OnboardingViewModel {
        val vm = newVm(settings, weights, setupSteps, goalsAndEnergy, region)
        SetupQuestion.entries.forEach { question ->
            answers.filter { it in question.options }.forEach { vm.takeAction(OnboardingAction.OptionTapped(it)) }
            vm.takeAction(OnboardingAction.ContinueTapped)
        }
        runCurrent()
        check(vm.state.step == OnboardingStep.Recommendation)
        return vm
    }

    /** The resting energy step, with `release.goalsAndEnergy` on. */
    private fun TestScope.atBody(
        answers: List<SetupOption>,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        weights: FakeWeightRepository = FakeWeightRepository(),
        region: String? = "GB",
    ): OnboardingViewModel {
        val vm = atRecommendation(answers, goalsAndEnergy = true, settings = settings, weights = weights, region = region)
        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()
        check(vm.state.step == OnboardingStep.Body) { "expected the resting energy step, was ${vm.state.step}" }
        return vm
    }

    /** The goal step; with `release.goalsAndEnergy` on, past a resting energy step left blank. */
    private fun TestScope.atGoal(
        answers: List<SetupOption>,
        goalsAndEnergy: Boolean = false,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        weights: FakeWeightRepository = FakeWeightRepository(),
    ): OnboardingViewModel {
        val vm = atRecommendation(answers, goalsAndEnergy = goalsAndEnergy, settings = settings, weights = weights)
        vm.takeAction(OnboardingAction.StartTapped)
        if (goalsAndEnergy) vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        check(vm.state.step == OnboardingStep.Goal) { "expected the goal step, was ${vm.state.step}" }
        return vm
    }

    private class RecordingTree : LogTree() {
        val entries = mutableListOf<LogEntry>()

        override fun log(entry: LogEntry): LogId? {
            entries += entry
            return null
        }

        fun steps(): List<Any?> =
            entries.filter { it.context.extras[EXTRA_APP_EVENT] == "onboarding.step_viewed" }.map { it.context.extras["step"] }
    }
}
