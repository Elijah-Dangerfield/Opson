package com.opson.features.settings.impl.targets

import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.Toast
import com.opson.features.settings.impl.eventsOf
import com.opson.libraries.diary.MacroMode
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_targets_floor_toast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Targets: amounts only when calories are configured Show, the floor (never below where the
 * user started while `GOAL_FLOOR_KCAL` is unset), and ratio versus gram macros. The floor and
 * scaling arithmetic themselves are `TargetsTest` in `:libraries:nutrition`.
 */
class TargetsTest : CoroutineTest() {

    private fun build(numbers: NumbersSettings, macroMode: MacroMode = MacroMode.Ratios) =
        TargetsStateBuilder.build(SettingsFixtures.settings(numbers).copy(macroMode = macroMode), SettingsFixtures.policy(numbers))

    // --- builder ---

    @Test
    fun fullShowsTheRangeTheAimAndEachMacro() {
        val state = build(NumbersSettings(PresetId.Full))

        assertTrue(state.visible)
        assertEquals(Display.Shown("1,950 to 2,350 kcal"), state.range)
        assertEquals(Display.Shown("2,150 kcal"), state.aim)
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat), state.macros.map { it.metric })
        val protein = state.macros.first()
        assertEquals(Display.Shown("123 g"), protein.grams)
        assertEquals(Display.Shown("23%"), protein.share)
    }

    @Test
    fun softDelayedAndHiddenCaloriesGetTheBackgroundCardWithNoAmounts() {
        listOf(PresetId.Fuel, PresetId.Delayed, PresetId.Coast, PresetId.Blind, PresetId.Nutrient).forEach { preset ->
            val state = build(NumbersSettings(preset))
            assertFalse(state.visible, "in $preset")
            assertEquals(Display.Hidden, state.range, "in $preset")
            assertEquals(Display.Hidden, state.aim, "in $preset")
            assertTrue(state.macros.isEmpty(), "in $preset")
        }
    }

    @Test
    fun aMacroThatIsNotShowDropsItsRow() {
        val numbers = NumbersSettings(
            PresetId.Full,
            overrides = mapOf(Metric.Fat to Visibility.Hide, Metric.Carbs to Visibility.Soften),
        )

        assertEquals(listOf(Metric.Protein), build(numbers).macros.map { it.metric })
    }

    @Test
    fun lowerIsOpenAboveTheStartAndShutAtIt() {
        assertTrue(build(NumbersSettings()).canLower)
        val atStart = TargetsStateBuilder.build(
            SettingsFixtures.settings().copy(targets = Targets.Default),
            SettingsFixtures.policy(PresetId.Full),
        )
        assertFalse(atStart.canLower)
    }

    // --- view model ---

    private fun viewModel(repository: FakeSettingsRepository) =
        TargetsViewModel(repository, FakePolicySource(SettingsFixtures.policy(PresetId.Full)))

    @Test
    fun raisingThenLoweringBackToTheStartIsAllowedAndOneMoreIsRefused() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings().copy(targets = Targets.Default))
        val vm = viewModel(repository)
        val events = eventsOf(vm)

        vm.takeAction(TargetsAction.RaiseTapped)
        assertEquals(2150, repository.state.value.targets.energyKcal)
        vm.takeAction(TargetsAction.LowerTapped)
        assertEquals(2100, repository.state.value.targets.energyKcal)
        assertTrue(events.isEmpty())

        val writes = repository.writes
        vm.takeAction(TargetsAction.LowerTapped)

        assertEquals(2100, repository.state.value.targets.energyKcal)
        assertEquals(writes, repository.writes)
        assertEquals(listOf<TargetsEvent>(TargetsEvent.ShowToast(Toast(Res.string.settings_targets_floor_toast))), events)
    }

    @Test
    fun ratioModeScalesTheGrams() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings().copy(targets = Targets.Default))
        val vm = viewModel(repository)

        vm.takeAction(TargetsAction.RaiseTapped)

        assertEquals(123, repository.state.value.targets.proteinG)
    }

    @Test
    fun gramModeLeavesTheGrams() = runUnitTest {
        val repository = FakeSettingsRepository(
            SettingsFixtures.settings().copy(targets = Targets.Default, macroMode = MacroMode.Grams),
        )
        val vm = viewModel(repository)

        vm.takeAction(TargetsAction.RaiseTapped)

        assertEquals(2150, repository.state.value.targets.energyKcal)
        assertEquals(120, repository.state.value.targets.proteinG)
    }

    @Test
    fun pickingAMacroModeSavesIt() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository)

        vm.takeAction(TargetsAction.MacroModePicked(MacroMode.Grams))

        assertEquals(MacroMode.Grams, repository.state.value.macroMode)
        assertEquals(MacroMode.Grams, vm.state.macroMode)
    }

    @Test
    fun theBackgroundCardLinksToModesAndBackCloses() = runUnitTest {
        val vm = viewModel(FakeSettingsRepository())
        val events = eventsOf(vm)

        vm.takeAction(TargetsAction.ModesTapped)
        vm.takeAction(TargetsAction.BackTapped)

        assertEquals(listOf(TargetsEvent.OpenModes, TargetsEvent.Close), events)
    }
}
