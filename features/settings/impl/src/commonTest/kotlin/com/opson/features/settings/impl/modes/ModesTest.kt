package com.opson.features.settings.impl.modes

import com.opson.features.settings.impl.FakeNumbersChanger
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.Toast
import com.opson.features.settings.impl.eventsOf
import com.opson.libraries.diary.NumbersOutcome
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.nutrition.Visibility
import kotlinx.datetime.TimeZone
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_saved
import opson.libraries.resources.generated.resources.settings_saved_fewer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Modes and numbers: what the builder shows (levels, never values) and how each action reaches
 * [com.opson.libraries.diary.NumbersChanger]. Whether a change pauses is the changer's call
 * (`SettingsNumbersChangerTest`); here the fake answers and the test checks what the page does
 * with the answer.
 */
class ModesTest : CoroutineTest() {

    // --- builder ---

    @Test
    fun presetsAreListedInOrderWithTheCurrentOneSelected() {
        val state = ModesStateBuilder.build(NumbersSettings(PresetId.Blind), TimeZone.UTC)

        assertEquals(Presets.all.map { it.id }, state.presets.map { it.id })
        assertEquals(listOf(PresetId.Blind), state.presets.filter { it.selected }.map { it.id })
    }

    @Test
    fun anAdjustedPresetSelectsNoRow() {
        val state = ModesStateBuilder.build(SettingsFixtures.adjusted, TimeZone.UTC)

        assertTrue(state.presets.none { it.selected })
    }

    @Test
    fun theGridShowsConfiguredLevelsDelayIncluded() {
        val state = ModesStateBuilder.build(NumbersSettings(PresetId.Coast), TimeZone.UTC)

        assertEquals(Metric.entries, state.matrix.map { it.metric })
        assertEquals(Visibility.Delay, state.matrix.single { it.metric == Metric.Calories }.level)
        assertEquals(Visibility.Hide, state.matrix.single { it.metric == Metric.Protein }.level)
    }

    @Test
    fun theNoteSaysSetupUntilTheUserChangesSomething() {
        val setup = ModesStateBuilder.build(NumbersSettings(PresetId.Blind), TimeZone.UTC)
        val changed = ModesStateBuilder.build(SettingsFixtures.adjusted, TimeZone.UTC)

        assertEquals(ChangedNote.DuringSetup, setup.changedNote)
        assertEquals(ChangedNote.LastChanged("Oct 25, 9:41 PM"), changed.changedNote)
        assertTrue(changed.pauseOn)
    }

    // --- view model ---

    private fun viewModel(
        numbers: NumbersSettings = NumbersSettings(PresetId.Full),
        changer: FakeNumbersChanger = FakeNumbersChanger(),
    ) = ModesViewModel(FakeSettingsRepository(SettingsFixtures.settings(numbers)), changer)

    @Test
    fun settingsArriveAsState() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings(NumbersSettings(PresetId.Full)))
        val vm = ModesViewModel(repository, FakeNumbersChanger())

        repository.state.value = SettingsFixtures.settings(NumbersSettings(PresetId.Mindful, pauseBeforeMore = true))

        assertEquals(PresetId.Mindful, vm.state.presets.single { it.selected }.id)
        assertTrue(vm.state.pauseOn)
    }

    @Test
    fun pickingAPresetThatShowsFewerSaysSo() = runUnitTest {
        val changer = FakeNumbersChanger { NumbersOutcome.Applied(fewer = true) }
        val vm = viewModel(changer = changer)
        val events = eventsOf(vm)

        vm.takeAction(ModesAction.PresetTapped(PresetId.Blind))

        assertEquals(listOf<NumbersChange>(NumbersChange.PickPreset(PresetId.Blind)), changer.requested)
        assertEquals(listOf<ModesEvent>(ModesEvent.ShowToast(Toast(Res.string.settings_saved_fewer))), events)
    }

    @Test
    fun anEqualOrRaisingChangeThatAppliesJustSaysSaved() = runUnitTest {
        val vm = viewModel(NumbersSettings(PresetId.Blind))
        val events = eventsOf(vm)

        vm.takeAction(ModesAction.PresetTapped(PresetId.Full))

        assertEquals(listOf<ModesEvent>(ModesEvent.ShowToast(Toast(Res.string.settings_saved))), events)
    }

    @Test
    fun aChangeThatNeedsThePauseOpensTheSheetWithThatChange() = runUnitTest {
        val vm = viewModel(NumbersSettings(PresetId.Blind), FakeNumbersChanger { NumbersOutcome.NeedsPause })
        val events = eventsOf(vm)

        vm.takeAction(ModesAction.LevelTapped(Metric.Weight, Visibility.Show))

        val change = NumbersChange.Override(Metric.Weight, Visibility.Show)
        assertEquals(listOf<ModesEvent>(ModesEvent.OpenPause(change)), events)
    }

    @Test
    fun tappingTheSelectedPresetDoesNothing() = runUnitTest {
        val changer = FakeNumbersChanger()
        val vm = viewModel(NumbersSettings(PresetId.Full), changer)

        vm.takeAction(ModesAction.PresetTapped(PresetId.Full))

        assertTrue(changer.requested.isEmpty())
    }

    @Test
    fun tappingTheCurrentPresetWithOverridesClearsThem() = runUnitTest {
        val changer = FakeNumbersChanger()
        val vm = viewModel(SettingsFixtures.adjusted, changer)

        vm.takeAction(ModesAction.PresetTapped(PresetId.Full))

        assertEquals(listOf<NumbersChange>(NumbersChange.PickPreset(PresetId.Full)), changer.requested)
    }

    @Test
    fun tappingTheCurrentLevelDoesNothing() = runUnitTest {
        val changer = FakeNumbersChanger()
        val vm = viewModel(NumbersSettings(PresetId.Full), changer)

        vm.takeAction(ModesAction.LevelTapped(Metric.Calories, Visibility.Show))

        assertTrue(changer.requested.isEmpty())
    }

    @Test
    fun turningThePauseOnIsInstant() = runUnitTest {
        val changer = FakeNumbersChanger()
        val vm = viewModel(NumbersSettings(PresetId.Full, pauseBeforeMore = false), changer)
        val events = eventsOf(vm)

        vm.takeAction(ModesAction.PauseToggled)

        assertEquals(1, changer.pauseTurnedOn)
        assertTrue(changer.requested.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun turningThePauseOffGoesThroughThePause() = runUnitTest {
        val changer = FakeNumbersChanger { NumbersOutcome.NeedsPause }
        val vm = viewModel(NumbersSettings(PresetId.Blind, pauseBeforeMore = true), changer)
        val events = eventsOf(vm)

        vm.takeAction(ModesAction.PauseToggled)

        assertEquals(listOf<NumbersChange>(NumbersChange.TurnOffPause), changer.requested)
        assertEquals(listOf<ModesEvent>(ModesEvent.OpenPause(NumbersChange.TurnOffPause)), events)
    }

    @Test
    fun targetsAndBackNavigate() = runUnitTest {
        val vm = viewModel()
        val events = eventsOf(vm)

        vm.takeAction(ModesAction.TargetsTapped)
        vm.takeAction(ModesAction.BackTapped)

        assertEquals(listOf(ModesEvent.OpenTargets, ModesEvent.Close), events)
    }
}
