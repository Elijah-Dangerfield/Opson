package com.opson.features.settings.impl.targets

import com.opson.features.settings.impl.FakeBurnedRepository
import com.opson.features.settings.impl.FakeGoalRepository
import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.findSupportFlag
import com.opson.features.settings.impl.goalsAndEnergyFlag
import com.opson.features.settings.impl.integrations.FakeCalendar
import com.opson.features.settings.impl.integrations.FakeHealthRepository
import com.opson.features.settings.impl.integrations.healthFlag
import com.opson.features.settings.impl.weightGoalsFlag
import com.opson.libraries.diary.GoalBook
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.goals.GoalFixtures
import com.opson.libraries.nutrition.BodyStage
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Goal
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GoalStatus
import com.opson.libraries.nutrition.GoalTargets
import com.opson.libraries.nutrition.PaceTier
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RangeEnd
import com.opson.libraries.nutrition.Recalculation
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent

/**
 * Targets and a goal (owner feedback, 2026-10-30): a goal's target is locked and changing it asks
 * first; an own target that points against the goal asks again; "Use my goal" lands on the same
 * target every time; a change to what the target is worked out from is offered, never applied
 * quietly. No goal card where a goal isn't offered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TargetsWeightGoalTest : CoroutineTest() {

    private val base = SettingsFixtures.settings().copy(weightUnit = WeightUnit.Kg, heightCm = 180.0, birthYear = 1996)

    private fun settings(goal: Goal = GoalFixtures.lose): Settings = GoalFixtures.settings(goal, base = base)

    private fun card(settings: Settings, preset: PresetId = PresetId.Full, flag: Boolean = true) = TargetsStateBuilder.build(
        settings,
        SettingsFixtures.policy(preset),
        burned = SettingsFixtures.burned,
        goalsAndEnergy = true,
        weightGoals = flag,
        today = GoalFixtures.today,
        weightKg = GoalFixtures.WEIGHT_KG,
    )

    @Test
    fun aGoalThatSetsTheTarget_isLocked_withItsBandAndPace() {
        val state = card(settings())
        val goal = assertIs<GoalCard.Setting>(state.goal)
        assertTrue(state.locked)
        assertEquals(GoalDirection.Lose, goal.direction)
        assertEquals("0.5 kg", goal.paceText)
        assertTrue((goal.target as Display.Shown).text.contains("1,930"))
        assertTrue((goal.low as Display.Shown).text.contains("1,830"))
        assertTrue((goal.high as Display.Shown).text.contains("2,030"))
        assertEquals(-1, goal.differenceSign)
        assertEquals(PaceTier.Steady, goal.pace?.tier)
        assertNull(state.safety, "the goal's own note says what matters")
    }

    @Test
    fun anOwnTarget_isTheirs_andOffersTheGoalBack() {
        val state = card(GoalBook.override(settings()))
        assertEquals(GoalCard.Own(com.opson.libraries.nutrition.GoalKind.Weight, GoalDirection.Lose), state.goal)
        assertFalse(state.locked)
    }

    @Test
    fun aGoalWithoutATypicalDayYet_saysItIsWaiting_andAPausedOneSaysSo() {
        val waiting = GoalBook.withGoals(base, listOf(GoalFixtures.lose.copy(baseline = null)))
        assertIs<GoalCard.Waiting>(card(waiting).goal)
        assertIs<GoalCard.Paused>(card(GoalBook.pause(settings(), Goal.WEIGHT_ID)).goal)
    }

    @Test
    fun noGoal_orJustTracking_isAnOffer() {
        assertEquals(GoalCard.None, card(base).goal)
        assertEquals(GoalCard.None, card(GoalBook.without(settings(), Goal.WEIGHT_ID)).goal)
    }

    @Test
    fun noCard_whereAGoalIsNotOffered() {
        assertNull(card(settings(), flag = false).goal, "flag off")
        assertFalse(card(settings(), flag = false).locked, "and nothing is locked")
        assertNull(card(settings(), preset = PresetId.MacrosOnly).goal, "weight is a trend there")
        assertNull(card(settings().copy(onboarding = OnboardingAnswers(hard = true))).goal)
        assertNull(card(settings().copy(bodyStage = BodyStage.Pregnant)).goal)
        val words = assertIs<GoalCard.Setting>(card(settings(), preset = PresetId.MacrosWithGrams).goal, "weight shows there, calories don't")
        assertEquals(Display.Hidden, words.target)
    }

    @Test
    fun useMyGoal_landsOnTheSameTarget_everyTime() = runUnitTest {
        val repository = FakeSettingsRepository(GoalBook.override(settings()))
        val vm = viewModel(repository)
        runCurrent()
        val targets = (1..5).map { round ->
            // Moved by hand in between, a different way each time.
            repeat(round) { vm.takeAction(TargetsAction.StepTapped(RangeEnd.Low, up = round % 2 == 0)) }
            vm.takeAction(TargetsAction.StepTapped(RangeEnd.High, up = true))
            runCurrent()
            vm.takeAction(TargetsAction.UseGoalTapped)
            runCurrent()
            repository.state.value.targets.let { Triple(it.energyKcal, it.minKcal, it.maxKcal) }
        }
        assertEquals(List(5) { Triple(1930, 1830, 2030) }, targets)
        assertTrue(GoalBook.driving(repository.state.value) != null)
        assertIs<GoalCard.Setting>(vm.state.goal)
    }

    @Test
    fun aLockedTarget_asksBeforeAnythingMovesIt() = runUnitTest {
        val repository = FakeSettingsRepository(settings())
        val vm = viewModel(repository)
        runCurrent()
        val before = repository.state.value

        vm.takeAction(TargetsAction.MacrosAsGoalTapped)
        runCurrent()
        assertEquals(TargetsDialog.ChangeTarget, vm.state.dialog)
        assertEquals(before, repository.state.value, "nothing written yet")

        vm.takeAction(TargetsAction.DialogDismissed)
        runCurrent()
        assertNull(vm.state.dialog)
        assertEquals(before, repository.state.value)

        vm.takeAction(TargetsAction.ChangeTargetTapped)
        vm.takeAction(TargetsAction.OwnTargetChosen)
        runCurrent()
        val goal = GoalBook.goals(repository.state.value).single()
        assertFalse(goal.setsTarget, "the goal is kept and no longer sets the target")
        assertEquals(GoalStatus.Active, goal.status)
        assertIs<GoalCard.Own>(vm.state.goal)
    }

    @Test
    fun editMyGoal_opensTheGoalFlow() = runUnitTest {
        val vm = viewModel(FakeSettingsRepository(settings()))
        val events = eventsOf(vm)
        runCurrent()
        vm.takeAction(TargetsAction.ChangeTargetTapped)
        vm.takeAction(TargetsAction.EditGoalChosen)
        runCurrent()
        assertEquals(listOf<TargetsEvent>(TargetsEvent.OpenGoalSetup), events)
        assertNull(vm.state.dialog)
    }

    @Test
    fun anOwnTargetAgainstTheGoal_asksAgain_andPausesTheGoalOnlyIfConfirmed() = runUnitTest {
        val repository = FakeSettingsRepository(GoalBook.override(settings()))
        val vm = viewModel(repository)
        runCurrent()

        // A surplus starts at the burn: no longer a loss.
        vm.takeAction(TargetsAction.StylePicked(StyleKind.Surplus))
        runCurrent()
        assertEquals(TargetsDialog.AgainstGoal(GoalDirection.Lose), vm.state.dialog)
        assertEquals(TargetStyle.Range, repository.state.value.targets.style)

        vm.takeAction(TargetsAction.DialogDismissed)
        runCurrent()
        assertEquals(TargetStyle.Range, repository.state.value.targets.style, "keeping the goal keeps the target")

        vm.takeAction(TargetsAction.StylePicked(StyleKind.Surplus))
        vm.takeAction(TargetsAction.PauseGoalChosen)
        runCurrent()
        assertIs<TargetStyle.Surplus>(repository.state.value.targets.style)
        assertEquals(GoalStatus.Paused, GoalBook.goals(repository.state.value).single().status)
    }

    @Test
    fun anEditTheGoalAgreesWith_goesStraightThrough() = runUnitTest {
        val repository = FakeSettingsRepository(GoalBook.override(settings()))
        val vm = viewModel(repository)
        runCurrent()
        vm.takeAction(TargetsAction.StepTapped(RangeEnd.High, up = true))
        runCurrent()
        assertNull(vm.state.dialog)
        assertEquals(2080, repository.state.value.targets.maxKcal)
    }

    @Test
    fun aRecalculation_isOffered_andUsedOrKeptOnlyByTheirChoice() = runUnitTest {
        val offer = recalculation()
        val repository = FakeSettingsRepository(settings())
        val vm = viewModel(repository, FakeGoalRepository(recalculation = offer))
        runCurrent()
        val dialog = assertIs<TargetsDialog.Recalculated>(vm.state.dialog)
        assertTrue((dialog.old as Display.Shown).text.contains("1,930"))
        assertTrue((dialog.new as Display.Shown).text.contains("2,150"))
        assertEquals(1930, repository.state.value.targets.energyKcal, "nothing changes on its own")

        vm.takeAction(TargetsAction.RecalculationAccepted)
        runCurrent()
        assertEquals(2150, repository.state.value.targets.energyKcal)
        assertEquals(offer.newBaseline, GoalBook.driving(repository.state.value)?.baseline)
        assertNull(vm.state.dialog)
    }

    @Test
    fun keepingTheTarget_remembersTheInputs() = runUnitTest {
        val offer = recalculation()
        val repository = FakeSettingsRepository(settings())
        val vm = viewModel(repository, FakeGoalRepository(recalculation = offer))
        runCurrent()
        vm.takeAction(TargetsAction.RecalculationKept)
        runCurrent()
        assertEquals(1930, repository.state.value.targets.energyKcal)
        assertEquals(offer.newBaseline.inputs, GoalBook.driving(repository.state.value)?.declined)
        assertNull(vm.state.dialog)
    }

    @Test
    fun closingARecalculation_waitsForTheNextVisit() = runUnitTest {
        val vm = viewModel(FakeSettingsRepository(settings()), FakeGoalRepository(recalculation = recalculation()))
        runCurrent()
        vm.takeAction(TargetsAction.DialogDismissed)
        runCurrent()
        assertNull(vm.state.dialog)
    }

    @Test
    fun thePaceNotesInfo_opensWithTheLimit() = runUnitTest {
        val vm = viewModel(FakeSettingsRepository(settings()), FakeGoalRepository(weightKg = GoalFixtures.WEIGHT_KG))
        runCurrent()
        vm.takeAction(TargetsAction.PaceInfoTapped)
        runCurrent()
        val info = assertIs<TargetsDialog.PaceInfo>(vm.state.dialog)
        assertTrue(info.canWorkOut)
        assertTrue(info.estimated, "no body fat on Profile")
    }

    @Test
    fun changeGoal_opensTheGoalFlow() = runUnitTest {
        val vm = viewModel(FakeSettingsRepository(settings()))
        val events = eventsOf(vm)
        runCurrent()
        vm.takeAction(TargetsAction.ChangeGoalTapped)
        runCurrent()
        assertEquals(listOf<TargetsEvent>(TargetsEvent.OpenGoalSetup), events)
    }

    /** Connecting a health app, for the goal these settings hold. */
    private fun recalculation(): Recalculation {
        val offer = GoalFixtures.connectedHealth
        assertEquals(1930, GoalTargets.of(GoalBook.driving(settings())!!)?.aimKcal)
        return offer
    }

    private fun viewModel(repository: FakeSettingsRepository, goals: FakeGoalRepository = FakeGoalRepository()) = TargetsViewModel(
        repository,
        FakePolicySource(SettingsFixtures.policy(PresetId.Full)),
        FakeHealthRepository(),
        healthFlag(false),
        goalsAndEnergyFlag(true),
        FakeBurnedRepository(),
        FakeCalendar(),
        findSupportFlag(),
        weightGoalsFlag(on = true),
        goals,
    )
}
