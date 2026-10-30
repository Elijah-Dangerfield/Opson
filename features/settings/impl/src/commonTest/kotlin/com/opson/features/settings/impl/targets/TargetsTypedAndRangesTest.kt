package com.opson.features.settings.impl.targets

import com.opson.features.settings.impl.FakeBurnedRepository
import com.opson.features.settings.impl.FakeGoalRepository
import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.Toast
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.findSupportFlag
import com.opson.features.settings.impl.goalsAndEnergyFlag
import com.opson.features.settings.impl.integrations.FakeCalendar
import com.opson.features.settings.impl.integrations.FakeHealthRepository
import com.opson.features.settings.impl.integrations.healthFlag
import com.opson.features.settings.impl.weightGoalsFlag
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.goals.GoalFixtures
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.TargetStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_targets_deficit_cap_toast
import opson.libraries.resources.generated.resources.settings_targets_gap_toast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Targets typed rather than stepped, the macro split across the calorie range, and where the
 * person's own target leads (owner feedback, 2026-10-30).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TargetsTypedAndRangesTest : CoroutineTest() {

    private val low = TypedField(TypedField.Kind.Low)

    @Test
    fun typingARangeEnd_keepsTheDraft_andWritesOnceItIsAValue() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings())
        val vm = viewModel(repository)
        runCurrent()
        val writes = repository.writes

        vm.takeAction(TargetsAction.FieldTyped(low, "18"))
        runCurrent()
        assertEquals("18", vm.state.range?.low?.text, "a half-typed number stays on screen")
        assertEquals(writes, repository.writes, "and writes nothing")

        vm.takeAction(TargetsAction.FieldTyped(low, "1875"))
        runCurrent()
        assertEquals(1875 to 2300, repository.state.value.targets.minKcal to repository.state.value.targets.maxKcal)
        assertEquals("1875", vm.state.range?.low?.text)
    }

    @Test
    fun done_onANumberThatCantBeUsed_saysWhy_andGoesBackToTheSavedOne() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings())
        val vm = viewModel(repository)
        val events = eventsOf(vm)
        runCurrent()

        vm.takeAction(TargetsAction.FieldTyped(low, "2290"))
        vm.takeAction(TargetsAction.FieldDone(low))
        runCurrent()
        assertEquals(1900, repository.state.value.targets.minKcal)
        assertEquals("1900", vm.state.range?.low?.text)
        assertEquals(listOf<TargetsEvent>(TargetsEvent.ShowToast(Toast(Res.string.settings_targets_gap_toast))), events)
    }

    @Test
    fun aDeficit_isTyped_upToItsCap() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings(targets = SettingsFixtures.deficitTargets))
        val vm = viewModel(repository)
        val events = eventsOf(vm)
        runCurrent()
        val deficit = TypedField(TypedField.Kind.Deficit)

        vm.takeAction(TargetsAction.FieldTyped(deficit, "250"))
        runCurrent()
        assertEquals(250, (repository.state.value.targets.style as TargetStyle.Deficit).kcal)

        vm.takeAction(TargetsAction.FieldTyped(deficit, "450"))
        vm.takeAction(TargetsAction.FieldDone(deficit))
        runCurrent()
        assertEquals(250, (repository.state.value.targets.style as TargetStyle.Deficit).kcal, "past where it started")
        assertEquals(listOf<TargetsEvent>(TargetsEvent.ShowToast(Toast(Res.string.settings_targets_deficit_cap_toast))), events)
    }

    @Test
    fun theMacros_showGramsAtEachEndOfTheRange() {
        val state = TargetsStateBuilder.build(SettingsFixtures.settings(), SettingsFixtures.policy(PresetId.Full))
        val protein = state.macros.first { it.metric == Metric.Protein }
        // 120 g at 2,100: 109 g at 1,900 and 131 g at 2,300.
        assertEquals("109", (protein.low as Display.Shown).text.filter(Char::isDigit))
        assertEquals("131", (protein.high as Display.Shown).text.filter(Char::isDigit))
        assertTrue(state.macroRange)

        val deficit = TargetsStateBuilder.build(SettingsFixtures.settings(targets = SettingsFixtures.deficitTargets), SettingsFixtures.policy(PresetId.Full))
        assertTrue(deficit.macros.all { it.low is Display.Hidden }, "a deficit's target moves day to day")
    }

    @Test
    fun theOwnTarget_drawsWhereItLeads_withAWeighIn_andOnlyWhereWeightShows() {
        val settings = SettingsFixtures.settings(targets = SettingsFixtures.deficitTargets)
        fun build(preset: PresetId, inputs: com.opson.libraries.nutrition.BaselineInputs? = GoalFixtures.inputs) = TargetsStateBuilder.build(
            settings.copy(numbers = NumbersSettings(preset = preset)),
            SettingsFixtures.policy(preset),
            burned = SettingsFixtures.burned,
            goalsAndEnergy = true,
            weightGoals = true,
            today = GoalFixtures.today,
            inputs = inputs,
        )
        val chart = assertNotNull(build(PresetId.Full).projection)
        assertTrue(chart.shape.middle.last() < chart.shape.start, "300 under a typical day goes down")
        assertNull(build(PresetId.Full, inputs = GoalFixtures.inputs.copy(weightKg = null)).projection, "no weigh-in, no chart")
        assertNull(build(PresetId.NoNumbers).projection)
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
        FakeGoalRepository(),
    )
}
