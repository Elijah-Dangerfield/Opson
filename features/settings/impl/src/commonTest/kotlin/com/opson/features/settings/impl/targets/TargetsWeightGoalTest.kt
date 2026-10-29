package com.opson.features.settings.impl.targets

import com.opson.features.settings.impl.FakeBurnedRepository
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
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.BodyStage
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.WeightGoal
import com.opson.libraries.nutrition.WeightGoals
import com.opson.libraries.nutrition.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent

/**
 * Targets and the weight goal: the row says the goal sets the target, or that a target moved by
 * hand is the person's own now, with a way back; no goal is an offer. Never where weight is not on
 * show, after numbers have been hard, while pregnant, or with the flag off.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TargetsWeightGoalTest : CoroutineTest() {

    private val goal = WeightGoal(GoalDirection.Lose, targetKg = 75.0, kgPerWeek = 0.5, startKg = 80.0)

    private fun settings(byHand: Boolean = false, goal: WeightGoal = this.goal): Settings {
        val base = SettingsFixtures.settings(NumbersSettings(PresetId.Full)).copy(weightUnit = WeightUnit.Kg, everydayActivity = EverydayActivity.MostlySitting)
        if (!goal.isSet) return base
        val (stamped, targets) = WeightGoals.apply(goal, base.targets, aimKcal = 1950, scaleMacros = false)
        return base.copy(weightGoal = stamped, targets = if (byHand) targets.raise() else targets)
    }

    private fun row(settings: Settings, preset: PresetId = PresetId.Full, flag: Boolean = true) = TargetsStateBuilder.build(
        settings,
        SettingsFixtures.policy(preset),
        burned = SettingsFixtures.burned,
        goalsAndEnergy = true,
        weightGoals = flag,
    ).weightGoal

    @Test
    fun aGoalThatSetsTheTarget_saysSo_withItsPace() {
        assertEquals(WeightGoalRow.Driving(GoalDirection.Lose, pace = "0.5 kg"), row(settings()))
    }

    @Test
    fun aTargetMovedByHand_isTheirs_andOffersTheGoalBack() {
        assertEquals(WeightGoalRow.NotDriving(canUseGoal = true), row(settings(byHand = true)))
    }

    @Test
    fun noGoal_orJustTracking_isAnOffer() {
        assertEquals(WeightGoalRow.None, row(settings(goal = WeightGoal.None)))
        assertEquals(WeightGoalRow.None, row(settings(goal = WeightGoal.None).copy(weightGoal = WeightGoal(GoalDirection.JustTrack))))
    }

    @Test
    fun noRow_whereAWeightGoalIsNotOffered() {
        assertNull(row(settings(), flag = false), "flag off")
        assertNull(row(settings(), preset = PresetId.MacrosOnly), "weight is a trend there")
        assertNull(row(settings().copy(onboarding = OnboardingAnswers(hard = true))))
        assertNull(row(settings().copy(bodyStage = BodyStage.Pregnant)))
        assertIs<WeightGoalRow.Driving>(row(settings(), preset = PresetId.MacrosWithGrams), "weight shows there, calories don't")
    }

    @Test
    fun useMyGoal_setsTheTargetFromATypicalDay_andTheGoalDrivesItAgain() = runUnitTest {
        val repository = FakeSettingsRepository(settings(byHand = true))
        val vm = viewModel(repository)
        runCurrent()

        vm.takeAction(TargetsAction.UseGoalTapped)
        runCurrent()

        val saved = repository.state.value
        // 1,610 resting plus mostly sitting's 320, less 0.5 kg a week's 550.
        assertEquals(1610 + 320 - 550, saved.targets.energyKcal)
        assertTrue(saved.weightGoal.drives(saved.targets))
        assertIs<WeightGoalRow.Driving>(vm.state.weightGoal)
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

    private fun viewModel(repository: FakeSettingsRepository) = TargetsViewModel(
        repository,
        FakePolicySource(SettingsFixtures.policy(PresetId.Full)),
        FakeHealthRepository(),
        healthFlag(false),
        goalsAndEnergyFlag(true),
        FakeBurnedRepository(),
        FakeCalendar(),
        findSupportFlag(),
        weightGoalsFlag(on = true),
    )
}
