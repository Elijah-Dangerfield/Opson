package com.opson.features.onboarding.impl

import com.opson.features.onboarding.OnboardingRoute
import com.opson.libraries.core.logging.EXTRA_APP_EVENT
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogId
import com.opson.libraries.core.logging.LogTree
import com.opson.libraries.diary.CelebrationsRelease
import com.opson.libraries.diary.GoalBook
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.diary.PlanCommitment
import com.opson.libraries.entitlements.BetaRelease
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.BodyStage
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.GoalAveraging
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GoalKind
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.SexChoice
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Units
import com.opson.libraries.opson.AppData
import com.opson.libraries.nutrition.WeightGoal
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.ui.components.text.DateParts
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
 * The setup steps after the starting mode (`release.onboardingSetup`, docs/plans/onboarding-v4.md):
 *
 *  - Off, Start saves straight away, whatever the mode.
 *  - On: meal times where they shape Today, then "Want to set a goal?". "Not now, just let me log"
 *    saves and goes to Today, with nothing about a body asked or saved.
 *  - A weight goal walks the profile, activity, goal, pace and overview; it sets the targets from a
 *    typical day, records the goal, logs the weight once, then the streak and the commitment.
 *  - Other kinds ask only their own number; the streak sets celebrations.
 *  - "Numbers have been hard": showing up and regular meals only, no weight, nothing about a body.
 *  - Pregnant or breastfeeding: no goal step, and the stage is saved.
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
    fun theGoalQuestion_comesFirst_andNotNow_savesWithNothingAboutABody() = runUnitTest {
        KLog.plant(events)
        val settings = FakeSettingsRepository()
        val weights = FakeWeightRepository()
        val vm = atRecommendation(listOf(NumbersFeel.LoveThem), settings = settings, weights = weights)
        assertEquals(listOf<OnboardingStep>(OnboardingStep.Plan(PlanStep.Ask)), vm.state.setupFlow)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }

        vm.takeAction(OnboardingAction.StartTapped)
        runCurrent()
        assertEquals(OnboardingStep.Plan(PlanStep.Ask), vm.state.step)
        plan(vm, PlanAction.WantsGoal(false))
        runCurrent()

        assertTrue(OnboardingEvent.NavigateToToday in received)
        val saved = settings.settings.value
        assertTrue(GoalBook.goals(saved).isEmpty())
        assertEquals(GoalDirection.JustTrack, saved.weightGoal.direction, "answered: Today won't offer a goal flow")
        assertNull(saved.heightCm)
        assertNull(saved.bodySex)
        assertEquals(Targets.Default, saved.targets)
        assertTrue(weights.added.isEmpty())
        assertNull(saved.planCommitment, "no goal, so no commitment")
        assertTrue(events.steps().none { it == "profile" || it == "streak" || it == "commitment" })
        assertEquals(listOf(false), events.booleans("onboarding.goal_wanted", "wanted"))
    }

    @Test
    fun aGoalToLose_setsTheTargets_drivesThem_logsTheWeightOnce_thenTheStreakAndTheCommitment() = runUnitTest {
        KLog.plant(events)
        val settings = FakeSettingsRepository()
        val weights = FakeWeightRepository()
        val vm = atProfile(listOf(NumbersFeel.LoveThem), settings = settings, weights = weights, region = "US")
        assertFalse(vm.state.planView!!.profile.birthdayShown, "asked at the age question, not again here")
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
        assertEquals("250 kcal", (rate.dailyKcal as Display.Shown).text)
        assertEquals("March 14, 2027", rate.forecast)

        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        val overview = vm.state.planView!!.overview
        assertEquals(OverviewKind.Goal, overview.kind)
        assertEquals("2,200 kcal", (overview.energy as Display.Shown).text)
        assertEquals(-1, overview.differenceSign)

        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(OnboardingStep.Plan(PlanStep.Streak), vm.state.step)
        plan(vm, PlanAction.StreakPicked(keep = true))
        runCurrent()
        assertEquals(OnboardingStep.Plan(PlanStep.Commit), vm.state.step)
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
        assertTrue(saved.celebrationsEnabled)
        assertEquals(listOf(Units.lbToKg(180.0)), weights.added)
        assertEquals(PlanCommitment(FixedCalendar.today(), committed = true), saved.planCommitment)
        assertTrue(events.commitments().single(), "the commitment is one boolean")
        assertEquals(listOf(true), events.booleans("onboarding.streak", "kept"))
    }

    @Test
    fun aGoalWithItsOwnNumber_asksNoBody_andNoStreakSetsCelebrationsOff() = runUnitTest {
        val settings = FakeSettingsRepository()
        val weights = FakeWeightRepository()
        val vm = atKind(listOf(NumbersFeel.LoveThem), settings = settings, weights = weights)
        plan(vm, PlanAction.GoalPicked(GoalKind.Meals))
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(OnboardingStep.Plan(PlanStep.MealCount), vm.state.step)
        plan(vm, PlanAction.MealsStepped(more = true))
        vm.takeAction(OnboardingAction.ContinueTapped)
        plan(vm, PlanAction.StreakPicked(keep = false))
        vm.takeAction(OnboardingAction.CommitTapped(committed = false))
        runCurrent()

        val saved = settings.settings.value
        assertEquals(GoalType.Meals(4), GoalBook.goals(saved).single().type)
        assertFalse(saved.celebrationsEnabled)
        assertNull(saved.heightCm)
        assertTrue(weights.added.isEmpty())
        assertEquals(PlanCommitment(FixedCalendar.today(), committed = false), saved.planCommitment)
    }

    @Test
    fun withCelebrationsOff_aGoalGoesStraightToTheCommitment() = runUnitTest {
        val vm = atKind(listOf(NumbersFeel.LoveThem), celebrations = false)
        plan(vm, PlanAction.GoalPicked(GoalKind.TrackEveryDay))
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(OnboardingStep.Plan(PlanStep.Commit), vm.state.step)
    }

    @Test
    fun numbersHaveBeenHard_areOfferedTheGentleGoals_andNothingAboutABody() = runUnitTest {
        val settings = FakeSettingsRepository()
        val weights = FakeWeightRepository()
        val vm = atKind(listOf(NumbersFeel.LoveThem, StatementAnswer.Agree), settings = settings, weights = weights)
        assertEquals(PresetId.MacrosWithGrams, vm.state.chosen)
        assertEquals(listOf(GoalKind.TrackEveryDay, GoalKind.Meals), vm.state.planView!!.kind.picks)

        plan(vm, PlanAction.GoalPicked(GoalKind.Weight))
        runCurrent()
        assertNull(vm.state.plan.pick, "a weight goal isn't offered")
        plan(vm, PlanAction.GoalPicked(GoalKind.TrackEveryDay))
        vm.takeAction(OnboardingAction.ContinueTapped)
        plan(vm, PlanAction.StreakPicked(keep = true))
        vm.takeAction(OnboardingAction.CommitTapped(committed = true))
        runCurrent()

        val saved = settings.settings.value
        assertEquals(GoalType.TrackEveryDay, GoalBook.goals(saved).single().type)
        assertEquals(Targets.Default, saved.targets)
        assertTrue(weights.added.isEmpty())
        assertNull(saved.bodySex)
    }

    @Test
    fun noNumbers_hasMealTimesFirst_thenTheGoalQuestion() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atRecommendation(listOf(NumbersFeel.RatherNot), settings = settings)
        assertEquals(PresetId.NoNumbers, vm.state.chosen)
        assertEquals(listOf(OnboardingStep.Meals, OnboardingStep.Plan(PlanStep.Ask)), vm.state.setupFlow)

        vm.takeAction(OnboardingAction.StartTapped)
        vm.takeAction(OnboardingAction.MealTimePicked(MealSlot.Breakfast, LocalTime(9, 30)))
        vm.takeAction(OnboardingAction.ContinueTapped)
        plan(vm, PlanAction.WantsGoal(false))
        runCurrent()

        val saved = settings.settings.value
        assertEquals(MealTimes(breakfast = LocalTime(9, 30)), saved.mealTimes)
        assertEquals(Targets.Default, saved.targets)
    }

    @Test
    fun anEnergyBalance_walksTheProfile_andSetsTheTargetFromIt() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = atKind(listOf(NumbersFeel.LoveThem), settings = settings, region = "US")
        plan(vm, PlanAction.GoalPicked(GoalKind.EnergyBalance))
        vm.takeAction(OnboardingAction.ContinueTapped)
        runCurrent()
        assertEquals(OnboardingStep.Plan(PlanStep.Profile), vm.state.step)
        typeImperialBody(vm)
        repeat(2) { vm.takeAction(OnboardingAction.ContinueTapped) }
        runCurrent()
        assertEquals(OnboardingStep.Plan(PlanStep.Balance), vm.state.step)
        val balance = vm.state.planView!!.balance!!
        assertEquals("250 kcal", (balance.amount as Display.Shown).text)
        assertEquals("250", balance.text)
        assertTrue(balance.weeklyOffered)
        assertFalse(balance.weekly, "each day, until a weekly average is picked")
        plan(vm, PlanAction.AveragingPicked(GoalAveraging.Weekly))
        plan(vm, PlanAction.BalanceTyped("350"))
        plan(vm, PlanAction.BalanceStepped(more = false))
        runCurrent()
        assertTrue(vm.state.planView!!.balance!!.weekly)
        assertEquals("300", vm.state.planView!!.balance!!.text, "a step after typing shows the amount")
        repeat(2) { vm.takeAction(OnboardingAction.ContinueTapped) }
        plan(vm, PlanAction.StreakPicked(keep = true))
        vm.takeAction(OnboardingAction.CommitTapped(committed = true))
        runCurrent()

        val goal = GoalBook.driving(settings.settings.value)!!
        assertEquals(GoalType.EnergyBalance(-300), goal.type)
        assertEquals(GoalAveraging.Weekly, goal.averaging)
        assertEquals(goal.targetsKcal, settings.settings.value.targets.energyKcal)
    }

    @Test
    fun changingToAKindWithoutABody_dropsTheWeightTypedForTheOther() = runUnitTest {
        val settings = FakeSettingsRepository()
        val weights = FakeWeightRepository()
        val vm = atProfile(listOf(NumbersFeel.LoveThem), settings = settings, weights = weights, region = "US")
        typeImperialBody(vm)
        vm.takeAction(OnboardingAction.Back)
        plan(vm, PlanAction.GoalPicked(GoalKind.Burn))
        vm.takeAction(OnboardingAction.ContinueTapped)
        vm.takeAction(OnboardingAction.ContinueTapped)
        plan(vm, PlanAction.StreakPicked(keep = true))
        vm.takeAction(OnboardingAction.CommitTapped(committed = true))
        runCurrent()

        assertTrue(weights.added.isEmpty(), "no weigh-in from a profile the goal doesn't use")
        assertNull(settings.settings.value.heightCm)
        assertIs<GoalType.Burn>(GoalBook.goals(settings.settings.value).single().type)
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
        assertEquals(OnboardingStep.Plan(PlanStep.Streak), vm.state.step)
        plan(vm, PlanAction.StreakPicked(keep = true))
        vm.takeAction(OnboardingAction.CommitTapped(committed = true))
        runCurrent()

        val saved = settings.settings.value
        assertEquals(BodySex.Female, saved.bodySex)
        assertEquals(BodyStage.Pregnant, saved.bodyStage)
        assertTrue(GoalBook.goals(saved).isEmpty())
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
        repeat(5) { vm.takeAction(OnboardingAction.Back) }
        runCurrent()
        assertEquals(OnboardingStep.Recommendation, vm.state.step)
    }

    @Test
    fun planChanges_offThePlanSteps_doNothing() = runUnitTest {
        val vm = atRecommendation(listOf(NumbersFeel.LoveThem))
        val before = vm.state
        plan(vm, PlanAction.WeightTyped("64"))
        plan(vm, PlanAction.WantsGoal(true))
        plan(vm, PlanAction.DirectionPicked(GoalDirection.Lose))
        vm.takeAction(OnboardingAction.MealTimePicked(MealSlot.Lunch, LocalTime(14, 0)))
        vm.takeAction(OnboardingAction.CommitTapped(committed = true))
        runCurrent()
        assertEquals(before, vm.state)
    }

    @Test
    fun theStreakAnswer_onlyCountsOnItsOwnStep() = runUnitTest {
        val vm = atKind(listOf(NumbersFeel.LoveThem))
        plan(vm, PlanAction.StreakPicked(keep = false))
        runCurrent()
        assertEquals(OnboardingStep.Plan(PlanStep.Kind), vm.state.step)
        assertNull(vm.state.plan.streak)
    }

    @Test
    fun theBetaCard_followsItsFlag() = runUnitTest {
        assertTrue(newVm(beta = true).state.betaNotice)
        assertFalse(newVm().state.betaNotice)
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
            OnboardingAction.PlanChanged(PlanAction.StreakPicked(keep = true)),
            OnboardingAction.CommitTapped(committed = true),
        ).forEach(vm::takeAction)
        runCurrent()

        assertEquals(
            listOf("goal_ask", "goal_kind", "profile", "activity", "weight_goal", "goal_rate", "goal_overview", "streak", "commitment"),
            events.steps().takeLast(9),
        )
        val values = events.entries
            .filter { it.context.extras[EXTRA_APP_EVENT].toString().startsWith("onboarding.") }
            .flatMap { entry -> entry.context.extras.values.map { it.toString().lowercase() } }
        val setupWords = listOf("1990", "180", "170", "male", "lose", "ft_in", "lb", "0.5", "2200", "2027-03-14", "weight", "track_every_day")
        val leaked = values.filter { it in setupWords }
        assertTrue(leaked.isEmpty(), "setup values reached telemetry: $leaked")
    }

    // ---------- scaffolding ----------

    private fun plan(vm: OnboardingViewModel, change: PlanAction) = vm.takeAction(OnboardingAction.PlanChanged(change))

    private fun typeImperialBody(vm: OnboardingViewModel) {
        val body = OnboardingFixtures.ImperialBody
        plan(vm, PlanAction.SexPicked(body.sex!!))
        plan(vm, PlanAction.BirthdayTyped(DateParts.of(body.birthDate)))
        plan(vm, PlanAction.HeightUnitPicked(HeightUnit.FtIn))
        plan(vm, PlanAction.HeightFeetTyped(body.height.feet))
        plan(vm, PlanAction.HeightInchesTyped(body.height.inches))
        plan(vm, PlanAction.WeightUnitPicked(WeightUnit.Lb))
        plan(vm, PlanAction.WeightTyped(body.weight.text))
    }

    private fun newVm(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        weights: FakeWeightRepository = FakeWeightRepository(),
        setupSteps: Boolean = true,
        region: String? = "GB",
        health: FakeHealthRepository = FakeHealthRepository(),
        healthOn: Boolean = false,
        celebrations: Boolean = true,
        beta: Boolean = false,
    ) = OnboardingViewModel(
        // Signed in to a new account that answered the age question, so onboarding opens at the first question.
        appCache = FakeAppCache(initial = AppData(pendingBirthDate = OnboardingFixtures.ImperialBody.birthDate.toString())),
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
        celebrations = CelebrationsRelease { celebrations },
        beta = BetaRelease { beta },
    )

    private fun TestScope.atRecommendation(
        answers: List<SetupOption>,
        setupSteps: Boolean = true,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        weights: FakeWeightRepository = FakeWeightRepository(),
        region: String? = "GB",
        health: FakeHealthRepository = FakeHealthRepository(),
        healthOn: Boolean = false,
        celebrations: Boolean = true,
    ): OnboardingViewModel {
        val vm = newVm(settings, weights, setupSteps, region, health, healthOn, celebrations)
        SetupQuestion.entries.forEach { question ->
            answers.filter { it in question.options }.forEach { vm.takeAction(OnboardingAction.OptionTapped(it)) }
            vm.takeAction(OnboardingAction.ContinueTapped)
        }
        runCurrent()
        check(vm.state.step == OnboardingStep.Recommendation)
        return vm
    }

    /** "What kind of goal?", after yes to setting one. */
    private fun TestScope.atKind(
        answers: List<SetupOption>,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        weights: FakeWeightRepository = FakeWeightRepository(),
        region: String? = "GB",
        health: FakeHealthRepository = FakeHealthRepository(),
        healthOn: Boolean = false,
        celebrations: Boolean = true,
    ): OnboardingViewModel {
        val vm = atRecommendation(answers, settings = settings, weights = weights, region = region, health = health, healthOn = healthOn, celebrations = celebrations)
        vm.takeAction(OnboardingAction.StartTapped)
        plan(vm, PlanAction.WantsGoal(true))
        runCurrent()
        check(vm.state.step == OnboardingStep.Plan(PlanStep.Kind)) { "expected the kind step, was ${vm.state.step}" }
        return vm
    }

    /** The profile step of a weight goal. */
    private fun TestScope.atProfile(
        answers: List<SetupOption>,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        weights: FakeWeightRepository = FakeWeightRepository(),
        region: String? = "GB",
        health: FakeHealthRepository = FakeHealthRepository(),
        healthOn: Boolean = false,
    ): OnboardingViewModel {
        val vm = atKind(answers, settings = settings, weights = weights, region = region, health = health, healthOn = healthOn)
        plan(vm, PlanAction.GoalPicked(GoalKind.Weight))
        vm.takeAction(OnboardingAction.ContinueTapped)
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

        fun commitments(): List<Boolean> = booleans("onboarding.commitment", "committed")

        fun booleans(event: String, key: String): List<Boolean> =
            entries.filter { it.context.extras[EXTRA_APP_EVENT] == event }.map { it.context.extras[key] as Boolean }
    }
}
