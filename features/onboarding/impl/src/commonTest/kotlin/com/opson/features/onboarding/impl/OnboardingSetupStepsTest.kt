package com.opson.features.onboarding.impl

import com.opson.features.onboarding.OnboardingRoute
import com.opson.libraries.core.logging.EXTRA_APP_EVENT
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogId
import com.opson.libraries.core.logging.LogTree
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.diary.PlanCommitment
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.BodyStage
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Units
import com.opson.libraries.nutrition.WeightGoal
import com.opson.libraries.nutrition.WeightUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * The setup steps after the starting mode (`release.onboardingSetup`, docs/plans/onboarding-v2.md):
 *
 *  - Off, Start saves straight away, whatever the mode.
 *  - On: meal times where they shape Today, then the goal flow: profile, activity, a weight goal
 *    and pace where the mode shows weight, the overview and the commitment.
 *  - "Numbers have been hard": no weight field, no goal, no pace, a gentle overview, and nothing
 *    about a goal saved.
 *  - A goal to lose sets the targets from a typical day, records the goal so it drives them, logs
 *    the weight once, and the commitment either way finishes.
 *  - Skip on the goal step means no goal; Continue waits for a usable target.
 *  - Pregnant or breastfeeding: no goal step, and the stage is saved.
 *  - The activity step connects the health app from its own tap.
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
    fun flagOff_startSavesStraightAway() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atRecommendation(listOf(NumbersFeel.LoveThem), setupSteps = false, settings = settings)
        assertTrue(vm.state.setupFlow.isEmpty())

        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()

        assertTrue(vm.state.isFinishing)
        assertEquals(Targets.Default, settings.settings.value.targets)
        assertEquals(WeightGoal.None, settings.settings.value.weightGoal)
        assertNull(settings.settings.value.planCommitment)
    }

    @Test
    fun allNumbers_walksTheGoalFlow_andAPaceJoinsOnceLosingIsPicked() = runUnitTest {
        val vm = atRecommendation(listOf(NumbersFeel.LoveThem))
        val base = listOf(PlanStep.Profile, PlanStep.Activity, PlanStep.Goal, PlanStep.Overview, PlanStep.Commit)
        assertEquals(base.map { OnboardingStep.Plan(it) }, vm.state.setupFlow)

        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()
        assertEquals(OnboardingStep.Plan(PlanStep.Profile), vm.state.step)
        vm.takeAction(OnboardingAction.ContinueTapped)
        vm.takeAction(OnboardingAction.ContinueTapped)
        plan(vm, PlanAction.DirectionPicked(GoalDirection.Lose))
        runCurrent()

        assertEquals(OnboardingStep.Plan(PlanStep.Goal), vm.state.step)
        assertTrue(OnboardingStep.Plan(PlanStep.Rate) in vm.state.setupFlow)
    }

    @Test
    fun numbersHaveBeenHard_getTheGentlePath_andNothingAboutAGoalIsSaved() = runUnitTest {
        val settings = FakeSettingsRepository()
        val weights = FakeWeightRepository()
        val vm = atRecommendation(listOf(NumbersFeel.LoveThem, StatementAnswer.Agree), settings = settings, weights = weights)
        assertEquals(PresetId.MacrosWithGrams, vm.state.chosen)
        val gentle = listOf(PlanStep.Profile, PlanStep.Activity, PlanStep.Overview, PlanStep.Commit)
        assertEquals(gentle.map { OnboardingStep.Plan(it) }, vm.state.setupFlow)

        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()
        assertFalse(vm.state.planView!!.profile.weightShown, "no weight field")
        plan(vm, PlanAction.SexPicked(SexChoice.Female))
        plan(vm, PlanAction.WeightTyped("64"))
        vm.takeAction(OnboardingAction.ContinueTapped)
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(OverviewKind.Gentle, vm.state.planView!!.overview.kind)
        vm.takeAction(OnboardingAction.ContinueTapped)
        vm.takeAction(OnboardingAction.CommitTapped(committed = true))
        runCurrent()

        val saved = settings.settings.value
        assertEquals(BodySex.Female, saved.bodySex)
        assertEquals(WeightGoal.None, saved.weightGoal)
        assertEquals(Targets.Default, saved.targets)
        assertTrue(weights.added.isEmpty(), "no weight was asked, so none is logged")
        assertEquals(PlanCommitment(FixedCalendar.today(), committed = true), saved.planCommitment)
    }

    @Test
    fun noNumbers_hasMealTimesFirst_andNoGoalStep() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atRecommendation(listOf(NumbersFeel.RatherNot), settings = settings)
        assertEquals(PresetId.NoNumbers, vm.state.chosen)
        val flow = listOf(OnboardingStep.Meals) +
            listOf(PlanStep.Profile, PlanStep.Activity, PlanStep.Overview, PlanStep.Commit).map { OnboardingStep.Plan(it) }
        assertEquals(flow, vm.state.setupFlow)

        vm.takeAction(OnboardingAction.StartTapped)
        vm.takeAction(OnboardingAction.MealTimePicked(MealSlot.Breakfast, LocalTime(9, 30)))
        repeat(4) { vm.takeAction(OnboardingAction.ContinueTapped) }
        vm.takeAction(OnboardingAction.CommitTapped(committed = false))
        runCurrent()

        val saved = settings.settings.value
        assertEquals(MealTimes(breakfast = LocalTime(9, 30)), saved.mealTimes)
        assertEquals(Targets.Default, saved.targets, "no goal step, so no goal written")
        assertEquals(PlanCommitment(FixedCalendar.today(), committed = false), saved.planCommitment)
    }

    @Test
    fun aGoalToLose_setsTheTargets_drivesThem_logsTheWeightOnce_andRecordsTheCommitment() = runUnitTest {
        KLog.plant(events)
        val settings = FakeSettingsRepository()
        val weights = FakeWeightRepository()
        val vm = atProfile(listOf(NumbersFeel.LoveThem), settings = settings, weights = weights, region = "US")
        typeImperialBody(vm)
        plan(vm, PlanAction.ActivityPicked(OnboardingFixtures.ImperialBody.activity))
        vm.takeAction(OnboardingAction.ContinueTapped)
        vm.takeAction(OnboardingAction.ContinueTapped)
        plan(vm, PlanAction.DirectionPicked(GoalDirection.Lose))
        plan(vm, PlanAction.TargetTyped("170"))
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()

        assertEquals(OnboardingStep.Plan(PlanStep.Rate), vm.state.step)
        val rate = vm.state.planView!!.rate
        assertEquals("0.5 lb", rate.rate, "half a pound a week to start")
        assertEquals("250 kcal", (rate.dailyKcal as com.opson.libraries.nutrition.Display.Shown).text)
        assertEquals("March 14, 2027", rate.forecast)

        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        val overview = vm.state.planView!!.overview
        assertEquals(OverviewKind.Goal, overview.kind)
        assertEquals("2,200 kcal", (overview.energy as com.opson.libraries.nutrition.Display.Shown).text)
        assertEquals(-1, overview.differenceSign)

        vm.takeAction(OnboardingAction.ContinueTapped)
        vm.takeAction(OnboardingAction.CommitTapped(committed = true))
        runCurrent()

        val saved = settings.settings.value
        val goal = saved.weightGoal
        assertEquals(GoalDirection.Lose, goal.direction)
        assertEquals(Units.lbToKg(170.0), goal.targetKg!!, 1e-9)
        assertEquals(Units.lbToKg(0.5), goal.kgPerWeek, 1e-9)
        assertEquals(Units.lbToKg(180.0), goal.startKg!!, 1e-9)
        assertEquals(FixedCalendar.today(), goal.startDate)
        assertEquals(2200, saved.targets.energyKcal)
        assertEquals(TargetStyle.Range, saved.targets.style)
        assertTrue(goal.drives(saved.targets))
        assertEquals(LocalDate(1990, 7, 2), saved.birthDate)
        assertEquals(1990, saved.birthYear)
        assertEquals(BodySex.Male, saved.bodySex)
        assertEquals(HeightUnit.FtIn, saved.heightUnit)
        assertEquals(WeightUnit.Lb, saved.weightUnit)
        assertEquals(listOf(Units.lbToKg(180.0)), weights.added)
        assertEquals(PlanCommitment(FixedCalendar.today(), committed = true), saved.planCommitment)
        assertTrue(events.commitments().single(), "the commitment is one boolean")
    }

    @Test
    fun notNow_finishesAllTheSame() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atProfile(listOf(NumbersFeel.LoveThem), settings = settings)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }
        repeat(2) { vm.takeAction(OnboardingAction.ContinueTapped) }
        vm.takeAction(OnboardingAction.SkipQuestionTapped)
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(OnboardingStep.Plan(PlanStep.Commit), vm.state.step)

        vm.takeAction(OnboardingAction.CommitTapped(committed = false))
        runCurrent()

        assertTrue(OnboardingEvent.NavigateToToday in received)
        assertEquals(false, settings.settings.value.planCommitment?.committed)
    }

    @Test
    fun skipOnTheGoal_meansNoGoal_andGoesPastThePace() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atProfile(listOf(NumbersFeel.LoveThem), settings = settings)
        repeat(2) { vm.takeAction(OnboardingAction.ContinueTapped) }
        plan(vm, PlanAction.DirectionPicked(GoalDirection.Gain))
        vm.takeAction(OnboardingAction.SkipQuestionTapped)
        runCurrent()

        assertEquals(OnboardingStep.Plan(PlanStep.Overview), vm.state.step)
        assertEquals(GoalDirection.None, vm.state.plan.goal.direction)
        vm.takeAction(OnboardingAction.ContinueTapped)
        vm.takeAction(OnboardingAction.CommitTapped(committed = true))
        runCurrent()
        assertEquals(WeightGoal.None, settings.settings.value.weightGoal)
    }

    @Test
    fun continueOnTheGoal_waitsForADirection_andAUsableTarget() = runUnitTest {
        val vm = atProfile(listOf(NumbersFeel.LoveThem), region = "US")
        typeImperialBody(vm)
        repeat(2) { vm.takeAction(OnboardingAction.ContinueTapped) }
        runCurrent()
        val goalStep = OnboardingStep.Plan(PlanStep.Goal)
        assertEquals(goalStep, vm.state.step)
        assertTrue(PlanStep.Goal in vm.state.planView!!.blocked, "no direction yet")

        plan(vm, PlanAction.DirectionPicked(GoalDirection.Lose))
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(goalStep, vm.state.step, "no target yet")
        assertNull(vm.state.planView!!.goal.hint, "a blank target says nothing")

        plan(vm, PlanAction.TargetTyped("190"))
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(goalStep, vm.state.step)
        assertEquals(TargetHint.AboveCurrent, vm.state.planView!!.goal.hint)

        plan(vm, PlanAction.TargetTyped("120"))
        runCurrent()
        assertTrue(vm.state.planView!!.goal.belowHealthy, "under a healthy range for 5'11.5\": a note, not a block")
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(OnboardingStep.Plan(PlanStep.Rate), vm.state.step)
    }

    @Test
    fun pregnant_skipsTheGoal_andSavesTheStage() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atProfile(listOf(NumbersFeel.LoveThem), settings = settings)
        plan(vm, PlanAction.SexPicked(SexChoice.Pregnant))
        runCurrent()
        assertFalse(OnboardingStep.Plan(PlanStep.Goal) in vm.state.setupFlow)

        repeat(3) { vm.takeAction(OnboardingAction.ContinueTapped) }
        runCurrent()
        assertEquals(OverviewKind.Stage, vm.state.planView!!.overview.kind)
        vm.takeAction(OnboardingAction.CommitTapped(committed = true))
        runCurrent()

        val saved = settings.settings.value
        assertEquals(BodySex.Female, saved.bodySex)
        assertEquals(BodyStage.Pregnant, saved.bodyStage)
        assertEquals(WeightGoal.None, saved.weightGoal)
    }

    @Test
    fun theActivityStep_connectsTheHealthApp_fromItsOwnTap() = runUnitTest {
        val health = FakeHealthRepository(initial = HealthStatus.NotConnected)
        val vm = atProfile(listOf(NumbersFeel.LoveThem), health = health, healthOn = true)
        assertIs<HealthOffer.CanConnect>(vm.state.health)
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(0, health.connects, "nothing is asked before the tap")

        plan(vm, PlanAction.ConnectHealthTapped)
        runCurrent()

        assertEquals(1, health.connects)
        assertIs<HealthOffer.Connected>(vm.state.health)
        assertTrue(vm.state.planView!!.activity.levels.isEmpty(), "nothing to pick once it reads activity")
    }

    @Test
    fun findSupport_inTheLowGoalNote_opensSupport() = runUnitTest {
        val vm = atProfile(listOf(NumbersFeel.LoveThem))
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }
        repeat(2) { vm.takeAction(OnboardingAction.ContinueTapped) }
        plan(vm, PlanAction.FindSupportTapped)
        runCurrent()
        assertEquals(listOf<OnboardingEvent>(OnboardingEvent.NavigateToSupport), received)
    }

    @Test
    fun back_walksTheFlow_toTheRecommendation() = runUnitTest {
        val vm = atProfile(listOf(NumbersFeel.LoveThem))
        repeat(2) { vm.takeAction(OnboardingAction.ContinueTapped) }
        runCurrent()
        assertEquals(OnboardingStep.Plan(PlanStep.Goal), vm.state.step)
        repeat(3) { vm.takeAction(OnboardingAction.Back) }
        runCurrent()
        assertEquals(OnboardingStep.Recommendation, vm.state.step)
    }

    @Test
    fun planChanges_offThePlanSteps_doNothing() = runUnitTest {
        val vm = atRecommendation(listOf(NumbersFeel.LoveThem))
        val before = vm.state
        plan(vm, PlanAction.WeightTyped("64"))
        plan(vm, PlanAction.DirectionPicked(GoalDirection.Lose))
        vm.takeAction(OnboardingAction.MealTimePicked(MealSlot.Lunch, LocalTime(14, 0)))
        vm.takeAction(OnboardingAction.CommitTapped(committed = true))
        runCurrent()
        assertEquals(before, vm.state)
    }

    @Test
    fun noEventAttribute_everCarriesASetupValue() = runUnitTest {
        KLog.plant(events)
        val vm = atProfile(listOf(Why.Performance, NumbersFeel.LoveThem), region = "US")
        typeImperialBody(vm)
        listOf(
            OnboardingAction.ContinueTapped,
            OnboardingAction.ContinueTapped,
            OnboardingAction.PlanChanged(PlanAction.DirectionPicked(GoalDirection.Lose)),
            OnboardingAction.PlanChanged(PlanAction.TargetTyped("170")),
            OnboardingAction.ContinueTapped,
            OnboardingAction.ContinueTapped,
            OnboardingAction.ContinueTapped,
            OnboardingAction.CommitTapped(committed = true),
        ).forEach(vm::takeAction)
        runCurrent()

        assertEquals(
            listOf("activity", "weight_goal", "goal_rate", "goal_overview", "commitment"),
            events.steps().takeLast(5),
        )
        val values = events.entries
            .filter { it.context.extras[EXTRA_APP_EVENT].toString().startsWith("onboarding.") }
            .flatMap { entry -> entry.context.extras.values.map { it.toString().lowercase() } }
        val setupWords = listOf("1990", "180", "170", "male", "lose", "ft_in", "lb", "0.5", "2200", "2027-03-14")
        val leaked = values.filter { it in setupWords }
        assertTrue(leaked.isEmpty(), "setup values reached telemetry: $leaked")
    }

    // ---------- scaffolding ----------

    private fun plan(vm: OnboardingViewModel, change: PlanAction) = vm.takeAction(OnboardingAction.PlanChanged(change))

    private fun typeImperialBody(vm: OnboardingViewModel) {
        val body = OnboardingFixtures.ImperialBody
        plan(vm, PlanAction.SexPicked(body.sex!!))
        plan(vm, PlanAction.BirthDatePicked(body.birthDate!!))
        plan(vm, PlanAction.HeightUnitPicked(HeightUnit.FtIn))
        plan(vm, PlanAction.HeightFeetTyped(body.height.feet))
        plan(vm, PlanAction.HeightInchesTyped(body.height.inches))
        plan(vm, PlanAction.WeightUnitPicked(WeightUnit.Lb))
        plan(vm, PlanAction.WeightTyped(body.weight.text))
    }

    private fun newVm(
        settings: FakeSettingsRepository,
        weights: FakeWeightRepository,
        setupSteps: Boolean,
        region: String?,
        health: FakeHealthRepository,
        healthOn: Boolean,
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
        health = health,
        healthFlag = healthFlag(healthOn),
        region = { region },
        route = OnboardingRoute(),
        appleSignIn = true,
    )

    private fun TestScope.atRecommendation(
        answers: List<SetupOption>,
        setupSteps: Boolean = true,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        weights: FakeWeightRepository = FakeWeightRepository(),
        region: String? = "GB",
        health: FakeHealthRepository = FakeHealthRepository(),
        healthOn: Boolean = false,
    ): OnboardingViewModel {
        val vm = newVm(settings, weights, setupSteps, region, health, healthOn)
        SetupQuestion.entries.forEach { question ->
            answers.filter { it in question.options }.forEach { vm.takeAction(OnboardingAction.OptionTapped(it)) }
            vm.takeAction(OnboardingAction.ContinueTapped)
        }
        runCurrent()
        check(vm.state.step == OnboardingStep.Recommendation)
        return vm
    }

    /** The profile step, the goal flow's first. */
    private fun TestScope.atProfile(
        answers: List<SetupOption>,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        weights: FakeWeightRepository = FakeWeightRepository(),
        region: String? = "GB",
        health: FakeHealthRepository = FakeHealthRepository(),
        healthOn: Boolean = false,
    ): OnboardingViewModel {
        val vm = atRecommendation(answers, settings = settings, weights = weights, region = region, health = health, healthOn = healthOn)
        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()
        check(vm.state.step == OnboardingStep.Plan(PlanStep.Profile)) { "expected the profile step, was ${vm.state.step}" }
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

        fun commitments(): List<Boolean> =
            entries.filter { it.context.extras[EXTRA_APP_EVENT] == "onboarding.commitment" }.map { it.context.extras["committed"] as Boolean }
    }
}
