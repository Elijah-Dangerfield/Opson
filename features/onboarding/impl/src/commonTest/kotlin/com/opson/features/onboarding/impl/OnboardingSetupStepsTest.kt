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
import com.opson.libraries.nutrition.ActivityLevel
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Display
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
 *  - Off, Start saves straight away, whatever the mode.
 *  - The goal step only where the mode shows calories (Full numbers); its stepper never goes
 *    under the floor, and only Range is offered without `release.goalsAndEnergy`.
 *  - The resting energy step only for a set deficit or surplus; its weight is logged once, as a
 *    weigh-in, and the deficit or surplus is aimed at the resting energy it gives.
 *  - The meal times step for No numbers and for eating regularly.
 *  - Skip keeps the defaults; only the steps shown write settings; Back walks the steps.
 *  - No event carries a setup value.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingSetupStepsTest : CoroutineTest() {

    private val events = RecordingTree()

    @AfterTest
    fun tearDown() {
        KLog.uproot(events)
    }

    @Test
    fun flagOff_startSavesStraightAway_evenInFullNumbers() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atRecommendation(listOf(NumbersFeel.LoveThem), setupSteps = false, settings = settings)
        assertTrue(vm.state.setupFlow.isEmpty())

        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        assertTrue(vm.state.isFinishing, "saved and on the way to Today")
        assertEquals(Targets.Default, settings.settings.value.targets)
    }

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
    fun withoutGoalsAndEnergy_onlyTheRangeIsOffered() = runUnitTest {
        val vm = atGoal(listOf(NumbersFeel.LoveThem), goalsAndEnergy = false)

        vm.takeAction(OnboardingAction.GoalStyleTapped(GoalChoice.Deficit))
        runCurrent()

        assertEquals(GoalChoice.Range, vm.state.goal.style)
        assertEquals(listOf(OnboardingStep.Goal), vm.state.setupFlow)
    }

    @Test
    fun oneNumber_savesExact_onTheSteppedAim() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atGoal(listOf(NumbersFeel.LoveThem), goalsAndEnergy = true, settings = settings)

        vm.takeAction(OnboardingAction.GoalStyleTapped(GoalChoice.OneNumber))
        vm.takeAction(OnboardingAction.GoalStepped(up = true))
        runCurrent()
        assertEquals(Display.Shown("2,150 kcal"), vm.state.goalView?.amount)
        assertEquals(listOf(OnboardingStep.Goal), vm.state.setupFlow, "one number needs no resting energy")

        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(TargetStyle.Exact, settings.settings.value.targets.style)
        assertEquals(2150, settings.settings.value.targets.energyKcal)
    }

    @Test
    fun aSetSurplus_asksForRestingEnergy_savesTheDetails_andLogsTheWeightOnce() = runUnitTest {
        val settings = FakeSettingsRepository()
        val weights = FakeWeightRepository()
        val vm = atGoal(listOf(NumbersFeel.LoveThem), goalsAndEnergy = true, settings = settings, weights = weights)

        vm.takeAction(OnboardingAction.GoalStyleTapped(GoalChoice.Surplus))
        runCurrent()
        assertEquals(Display.Hidden, vm.state.goalView?.amount, "a surplus follows the burn; no stepper here")
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(OnboardingStep.Body, vm.state.step)

        vm.takeAction(OnboardingAction.BirthYearTyped("1992x"))
        vm.takeAction(OnboardingAction.HeightTyped("168"))
        vm.takeAction(OnboardingAction.WeightTyped("64"))
        vm.takeAction(OnboardingAction.SexPicked(BodySex.Female))
        runCurrent()
        assertEquals("1992", vm.state.body.birthYear, "only digits reach the field")
        assertEquals(Display.Shown("1,360 kcal"), vm.state.bodyView?.resting)
        assertEquals(Display.Shown("1,630 kcal"), vm.state.bodyView?.burned, "mostly sitting until the person says otherwise")

        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        val saved = settings.settings.value
        assertEquals(1992, saved.birthYear)
        assertEquals(168.0, saved.heightCm)
        assertEquals(BodySex.Female, saved.bodySex)
        // The aim, 2,100, over an ordinary day's 1,630 (1,360 resting, 270 sitting): 470, floored to 50, and its own cap.
        assertEquals(TargetStyle.Surplus(kcal = 450, startingKcal = 450), saved.targets.style)
        assertEquals(ActivityLevel.Sedentary, saved.activityLevel)
        assertEquals(listOf(64.0), weights.added)

        // Back and on again saves again, but the weigh-in is not logged twice.
        vm.takeAction(OnboardingAction.Back)
        runCurrent()
        assertEquals(OnboardingStep.Body, vm.state.step)
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(listOf(64.0), weights.added)
    }

    @Test
    fun aSetDeficit_neverAsksForMoreThanTheAimAlreadyImplies() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atGoal(listOf(NumbersFeel.LoveThem), goalsAndEnergy = true, settings = settings)
        vm.takeAction(OnboardingAction.GoalStyleTapped(GoalChoice.Deficit))
        vm.takeAction(OnboardingAction.ContinueTapped)
        listOf(
            OnboardingAction.BirthYearTyped("1992"),
            OnboardingAction.HeightTyped("168"),
            OnboardingAction.WeightTyped("64"),
            OnboardingAction.SexPicked(BodySex.Female),
            OnboardingAction.ContinueTapped,
        ).forEach(vm::takeAction)
        runCurrent()

        // An ordinary day (1,630) is under the aim (2,100): no deficit at all, and the floor holds.
        val targets = settings.settings.value.targets
        assertEquals(TargetStyle.Deficit(kcal = 0, startingKcal = 0), targets.style)
        assertEquals(Targets.Default.startingKcal, targets.floorKcal)
    }

    @Test
    fun theActivityLevel_isWhatADeficitStartsFrom_andIsSaved() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atGoal(listOf(NumbersFeel.LoveThem), goalsAndEnergy = true, settings = settings)
        vm.takeAction(OnboardingAction.GoalStyleTapped(GoalChoice.Deficit))
        vm.takeAction(OnboardingAction.ContinueTapped)
        listOf(
            OnboardingAction.BirthYearTyped("1992"),
            OnboardingAction.HeightTyped("168"),
            OnboardingAction.WeightTyped("64"),
            OnboardingAction.SexPicked(BodySex.Female),
            OnboardingAction.ActivityPicked(ActivityLevel.Active),
        ).forEach(vm::takeAction)
        runCurrent()
        // 1,360 resting and 990 of a very active day.
        assertEquals(Display.Shown("2,350 kcal"), vm.state.bodyView?.burned)

        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        val saved = settings.settings.value
        assertEquals(ActivityLevel.Active, saved.activityLevel)
        // 2,350 burned against the 2,100 aim: 250 under, and that is also as far as it can go.
        assertEquals(TargetStyle.Deficit(kcal = 250, startingKcal = 250), saved.targets.style)
        assertEquals(Targets.Default.startingKcal, saved.targets.floorKcal, "the goal still never goes under where it started")
    }

    @Test
    fun skippingRestingEnergy_keepsTheStyle_withNoDetailsAndNoWeighIn() = runUnitTest {
        val settings = FakeSettingsRepository()
        val weights = FakeWeightRepository()
        val vm = atGoal(listOf(NumbersFeel.LoveThem), goalsAndEnergy = true, settings = settings, weights = weights)
        vm.takeAction(OnboardingAction.GoalStyleTapped(GoalChoice.Deficit))
        vm.takeAction(OnboardingAction.ContinueTapped)
        vm.takeAction(OnboardingAction.SkipQuestionTapped)
        runCurrent()

        val saved = settings.settings.value
        assertIs<TargetStyle.Deficit>(saved.targets.style)
        assertNull(saved.birthYear)
        assertNull(saved.heightCm)
        assertTrue(weights.added.isEmpty())
    }

    @Test
    fun poundsAndInches_areSavedInKilogramsAndCentimetres() = runUnitTest {
        val settings = FakeSettingsRepository()
        val weights = FakeWeightRepository()
        val vm = atGoal(listOf(NumbersFeel.LoveThem), goalsAndEnergy = true, settings = settings, weights = weights)
        vm.takeAction(OnboardingAction.GoalStyleTapped(GoalChoice.Surplus))
        vm.takeAction(OnboardingAction.ContinueTapped)
        vm.takeAction(OnboardingAction.UnitPicked(WeightUnit.Lb))
        vm.takeAction(OnboardingAction.HeightTyped("66"))
        vm.takeAction(OnboardingAction.WeightTyped("141"))
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()

        val saved = settings.settings.value
        assertEquals(WeightUnit.Lb, saved.weightUnit)
        assertEquals(167.64, saved.heightCm!!, 0.001)
        assertEquals(63.957, weights.added.single(), 0.001)
    }

    @Test
    fun aTypoInTheDetails_isNotSaved() = runUnitTest {
        val settings = FakeSettingsRepository()
        val weights = FakeWeightRepository()
        val vm = atGoal(listOf(NumbersFeel.LoveThem), goalsAndEnergy = true, settings = settings, weights = weights)
        vm.takeAction(OnboardingAction.GoalStyleTapped(GoalChoice.Surplus))
        vm.takeAction(OnboardingAction.ContinueTapped)
        vm.takeAction(OnboardingAction.BirthYearTyped("2020"))
        vm.takeAction(OnboardingAction.HeightTyped("1680"))
        vm.takeAction(OnboardingAction.WeightTyped("6"))
        runCurrent()
        assertEquals(Display.Hidden, vm.state.bodyView?.resting)
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()

        assertNull(settings.settings.value.birthYear, "someone born in 2020 is not 13")
        assertNull(settings.settings.value.heightCm)
        assertTrue(weights.added.isEmpty())
    }

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

    @Test
    fun someoneWhoAgreedNumbersWereHard_neverSeesAGoal() = runUnitTest {
        // Full numbers is never on offer to them, and the goal step only comes with it.
        val vm = atRecommendation(listOf(NumbersFeel.LoveThem, StatementAnswer.Agree))

        assertEquals(PresetId.MacrosWithGrams, vm.state.chosen)
        assertFalse(vm.state.showsGoal)
        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()
        assertTrue(vm.state.isFinishing, "saved straight away, no setup step")
    }

    @Test
    fun setupActions_offTheirStep_doNothing() = runUnitTest {
        val vm = atRecommendation(listOf(NumbersFeel.LoveThem))
        val before = vm.state

        vm.takeAction(OnboardingAction.GoalStepped(up = true))
        vm.takeAction(OnboardingAction.WeightTyped("64"))
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
        val vm = atGoal(listOf(Why.EatingRegularly, NumbersFeel.LoveThem), goalsAndEnergy = true)
        vm.takeAction(OnboardingAction.GoalStyleTapped(GoalChoice.Deficit))
        listOf(
            OnboardingAction.ContinueTapped,
            OnboardingAction.BirthYearTyped("1992"),
            OnboardingAction.HeightTyped("168"),
            OnboardingAction.WeightTyped("64"),
            OnboardingAction.SexPicked(BodySex.Female),
            OnboardingAction.ContinueTapped,
            OnboardingAction.MealTimePicked(MealSlot.Breakfast, LocalTime(9, 30)),
            OnboardingAction.ContinueTapped,
        ).forEach(vm::takeAction)
        runCurrent()

        assertEquals(listOf("goal", "resting_energy", "meal_times"), events.steps().takeLast(3))
        val values = events.entries
            .filter { it.context.extras[EXTRA_APP_EVENT].toString().startsWith("onboarding.") }
            .flatMap { entry -> entry.context.extras.values.map { it.toString().lowercase() } }
        val setupWords = listOf("1992", "168", "64", "64.0", "female", "deficit", "9:30", "09:30") +
            GoalChoice.entries.map { it.name.lowercase() } - "range"
        val leaked = values.filter { it in setupWords }
        assertTrue(leaked.isEmpty(), "setup values reached telemetry: $leaked")
    }

    // ---------- scaffolding ----------

    private fun newVm(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        weights: FakeWeightRepository = FakeWeightRepository(),
        setupSteps: Boolean = true,
        goalsAndEnergy: Boolean = false,
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
        route = OnboardingRoute(),
        appleSignIn = true,
    )

    private fun TestScope.atRecommendation(
        answers: List<SetupOption>,
        setupSteps: Boolean = true,
        goalsAndEnergy: Boolean = false,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        weights: FakeWeightRepository = FakeWeightRepository(),
    ): OnboardingViewModel {
        val vm = newVm(settings, weights, setupSteps, goalsAndEnergy)
        SetupQuestion.entries.forEach { question ->
            answers.filter { it in question.options }.forEach { vm.takeAction(OnboardingAction.OptionTapped(it)) }
            vm.takeAction(OnboardingAction.ContinueTapped)
        }
        runCurrent()
        check(vm.state.step == OnboardingStep.Recommendation)
        return vm
    }

    private fun TestScope.atGoal(
        answers: List<SetupOption>,
        goalsAndEnergy: Boolean = false,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        weights: FakeWeightRepository = FakeWeightRepository(),
    ): OnboardingViewModel {
        val vm = atRecommendation(answers, goalsAndEnergy = goalsAndEnergy, settings = settings, weights = weights)
        vm.takeAction(OnboardingAction.StartTapped)
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
