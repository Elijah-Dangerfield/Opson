package com.opson.features.onboarding.impl

import com.opson.features.onboarding.GoalSetupRoute
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.PlanCommitment
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.BodyStage
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.nutrition.WeightGoal
import com.opson.libraries.nutrition.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.LocalDate

/**
 * The goal flow from Today: it starts from what the account has, writes nothing until the last
 * step, sets the goal and the targets it means, logs a weigh-in only for a changed weight, and
 * closes. "Just track" leaves an existing account's targets alone. And the card that offers it
 * shows only where a weight goal makes sense.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalSetupViewModelTest : CoroutineTest() {

    private val account = Settings(
        bodySex = BodySex.Male,
        heightCm = 181.6,
        heightUnit = HeightUnit.Cm,
        weightUnit = WeightUnit.Kg,
        everydayActivity = EverydayActivity.SomeWalking,
        numbers = NumbersSettings(preset = PresetId.Full),
    ).withBirthDate(LocalDate(1990, 7, 2))

    private val latest = WeighIn(id = "w0", measuredAt = Instant.parse("2026-10-20T08:00:00Z"), kg = 81.6)

    @Test
    fun itOpens_onWhatTheAccountHas() = runUnitTest {
        val vm = newVm()
        runCurrent()
        val body = vm.state.answers.body
        assertEquals(SexChoice.Male, body.sex)
        assertEquals(LocalDate(1990, 7, 2), body.birthDate)
        assertEquals("81.6", body.weight.text)
        assertEquals(EverydayActivity.SomeWalking, body.activity)
        assertEquals(PlanStep.Profile, vm.state.step)
    }

    @Test
    fun aGoal_isSavedOnlyAtTheEnd_withTheTargets_andAnUnchangedWeightLogsNothing() = runUnitTest {
        val settings = FakeSettingsRepository(account)
        val weights = FakeWeightRepository(existing = listOf(latest))
        val vm = newVm(settings, weights)
        val closed = closes(vm)
        runCurrent()

        repeat(2) { vm.takeAction(GoalSetupAction.ContinueTapped) }
        vm.takeAction(GoalSetupAction.Plan(PlanAction.DirectionPicked(GoalDirection.Lose)))
        vm.takeAction(GoalSetupAction.Plan(PlanAction.TargetTyped("76")))
        repeat(3) { vm.takeAction(GoalSetupAction.ContinueTapped) }
        runCurrent()
        assertEquals(PlanStep.Commit, vm.state.step)
        assertEquals(account, settings.settings.value, "nothing written before the last step")

        vm.takeAction(GoalSetupAction.CommitTapped(committed = true))
        runCurrent()

        val saved = settings.settings.value
        assertEquals(GoalDirection.Lose, saved.weightGoal.direction)
        assertTrue(saved.weightGoal.drives(saved.targets))
        assertEquals(PlanCommitment(FixedCalendar.today(), committed = true), saved.planCommitment)
        assertTrue(weights.added.isEmpty(), "the weight wasn't changed")
        assertEquals(1, closed.size)
    }

    @Test
    fun aChangedWeight_isLogged() = runUnitTest {
        val weights = FakeWeightRepository(existing = listOf(latest))
        val vm = newVm(weights = weights)
        runCurrent()
        vm.takeAction(GoalSetupAction.Plan(PlanAction.WeightTyped("80.2")))
        vm.takeAction(GoalSetupAction.ContinueTapped)
        vm.takeAction(GoalSetupAction.ContinueTapped)
        vm.takeAction(GoalSetupAction.SkipTapped)
        vm.takeAction(GoalSetupAction.ContinueTapped)
        vm.takeAction(GoalSetupAction.CommitTapped(committed = false))
        runCurrent()
        assertEquals(listOf(80.2), weights.added)
    }

    @Test
    fun justTracking_leavesTheAccountsTargetsAsTheyAre() = runUnitTest {
        val mine = Targets.Default.raise().raise()
        val settings = FakeSettingsRepository(account.copy(targets = mine))
        val vm = newVm(settings)
        runCurrent()
        repeat(2) { vm.takeAction(GoalSetupAction.ContinueTapped) }
        vm.takeAction(GoalSetupAction.Plan(PlanAction.DirectionPicked(GoalDirection.JustTrack)))
        repeat(2) { vm.takeAction(GoalSetupAction.ContinueTapped) }
        vm.takeAction(GoalSetupAction.CommitTapped(committed = true))
        runCurrent()

        assertEquals(mine, settings.settings.value.targets)
        assertEquals(GoalDirection.JustTrack, settings.settings.value.weightGoal.direction)
    }

    @Test
    fun backOnTheFirstStep_closes_andSavesNothing() = runUnitTest {
        val settings = FakeSettingsRepository(account)
        val vm = newVm(settings)
        val closed = closes(vm)
        runCurrent()
        vm.takeAction(GoalSetupAction.BackTapped)
        runCurrent()
        assertEquals(1, closed.size)
        assertEquals(account, settings.settings.value)
    }

    @Test
    fun theCard_showsOnlyWhereAWeightGoalMakesSense() {
        val full = OnboardingFixtures.policy(PresetId.Full)
        assertTrue(GoalSetupPrompt.worthOffering(Settings(), full))
        assertFalse(GoalSetupPrompt.worthOffering(Settings(weightGoal = WeightGoal(GoalDirection.JustTrack)), full), "answered")
        assertFalse(GoalSetupPrompt.worthOffering(Settings(onboarding = OnboardingAnswers(hard = true)), full))
        assertFalse(GoalSetupPrompt.worthOffering(Settings(bodyStage = BodyStage.Pregnant), full))
        assertFalse(GoalSetupPrompt.worthOffering(Settings(), OnboardingFixtures.policy(PresetId.MacrosOnly)), "weight is a trend there")
        assertTrue(GoalSetupPrompt.worthOffering(Settings(), OnboardingFixtures.policy(PresetId.MacrosWithGrams)))
    }

    private fun TestScope.closes(vm: GoalSetupViewModel): MutableList<GoalSetupEvent> {
        val received = mutableListOf<GoalSetupEvent>()
        backgroundScope.launch { vm.eventFlow.collect { if (it == GoalSetupEvent.Close) received += it } }
        return received
    }

    private fun newVm(
        settings: FakeSettingsRepository = FakeSettingsRepository(account),
        weights: FakeWeightRepository = FakeWeightRepository(existing = listOf(latest)),
    ) = GoalSetupViewModel(
        route = GoalSetupRoute(),
        settingsRepository = settings,
        weightRepository = weights,
        policySource = object : VisibilityPolicySource {
            override val policy: StateFlow<VisibilityPolicy> = MutableStateFlow(OnboardingFixtures.policy(PresetId.Full))
        },
        calendar = FixedCalendar,
        region = { "GB" },
        health = FakeHealthRepository(),
        healthFlag = healthFlag(false),
    )
}
