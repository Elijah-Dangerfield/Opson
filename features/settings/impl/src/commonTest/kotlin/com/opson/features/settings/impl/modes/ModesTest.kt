package com.opson.features.settings.impl.modes

import com.opson.features.settings.impl.FakeNumbersChanger
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.Toast
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.photosFlag
import com.opson.libraries.diary.NumbersOutcome
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.nutrition.RevealOn
import com.opson.libraries.nutrition.Visibility
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_saved
import opson.libraries.resources.generated.resources.settings_saved_fewer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
        val state = ModesStateBuilder.build(NumbersSettings(PresetId.NoNumbers), TimeZone.UTC)

        assertEquals(Presets.all.map { it.id }, state.presets.map { it.id })
        assertEquals(listOf(PresetId.NoNumbers), state.presets.filter { it.selected }.map { it.id })
    }

    @Test
    fun aChangedPresetStaysSelectedWithItsChangeCount() {
        val state = ModesStateBuilder.build(SettingsFixtures.nutrientsFirstWithACarbsChange, TimeZone.UTC)

        assertEquals(listOf(PresetRow(PresetId.MacrosOnly, selected = true, changes = 1)), state.presets.filter { it.selected })
        assertTrue(state.presets.filterNot { it.selected }.all { it.changes == 0 })
    }

    @Test
    fun anOverrideEqualToThePresetIsNotAChange() {
        val fuelProtein = Presets[PresetId.MacrosOnly].level(Metric.Protein)
        val state = ModesStateBuilder.build(NumbersSettings(PresetId.MacrosOnly, overrides = mapOf(Metric.Protein to fuelProtein)), TimeZone.UTC)

        assertEquals(0, state.presets.single { it.selected }.changes)
        assertTrue(state.matrix.all { it.changedFrom == null })
    }

    @Test
    fun aChangedRowSaysWhatItWas() {
        val state = ModesStateBuilder.build(SettingsFixtures.nutrientsFirstWithACarbsChange, TimeZone.UTC)

        assertEquals(
            MatrixRow(Metric.Carbs, Visibility.Show, changedFrom = Presets[PresetId.MacrosOnly].level(Metric.Carbs)),
            state.matrix.single { it.metric == Metric.Carbs },
        )
        assertEquals(listOf(Metric.Carbs), state.matrix.filter { it.changedFrom != null }.map { it.metric })
    }

    @Test
    fun theMatrixIsTheOnlyPlaceForWeightAndVitamins() {
        val metrics = ModesStateBuilder.build(NumbersSettings(), TimeZone.UTC).matrix.map { it.metric }

        assertTrue(Metric.Weight in metrics && Metric.Micros in metrics)
    }

    @Test
    fun theRevealTimeIsShownAsAClockTime_onlyWhileSomethingIsLater() {
        val nine = ModesStateBuilder.build(NumbersSettings.later(), TimeZone.UTC)
        val custom = ModesStateBuilder.build(NumbersSettings.later().copy(revealAt = LocalTime(19, 30)), TimeZone.UTC)

        assertEquals("9:00 PM", nine.reveal?.timeText)
        assertEquals("7:30 PM", custom.reveal?.timeText)
        assertNull(ModesStateBuilder.build(NumbersSettings(), TimeZone.UTC).reveal, "nothing is Later in All numbers")
        val oneLater = NumbersSettings(overrides = mapOf(Metric.Weight to Visibility.Delay))
        assertEquals(false, ModesStateBuilder.build(oneLater, TimeZone.UTC).reveal?.weekly)
    }

    @Test
    fun weeklyReveal_isShownOnTheRevealRow() {
        val weekly = ModesStateBuilder.build(NumbersSettings.later().copy(revealOn = RevealOn.Week), TimeZone.UTC)

        assertEquals(true, weekly.reveal?.weekly)
    }

    @Test
    fun endAfter_offByDefault_andShowsItsTimerWhenOn() {
        assertEquals(false, ModesStateBuilder.build(NumbersSettings(), TimeZone.UTC).endAfter.on)

        val state = ModesStateBuilder.build(SettingsFixtures.fullForTwoWeeks, TimeZone.UTC, editingEndAfter = true)
        assertEquals(SettingsFixtures.fullForTwoWeeks.endAfter, state.endAfter.timing)
        assertEquals("Nov 8", state.endAfter.endsOnText)
        assertTrue(state.endAfter.editing)
        assertEquals(listOf(PresetId.MacrosWithGrams, PresetId.MacrosOnly, PresetId.NoNumbers), state.endAfter.thenChoices, "never the current one")
    }

    @Test
    fun theGridShowsConfiguredLevelsDelayIncluded() {
        val state = ModesStateBuilder.build(NumbersSettings.later(), TimeZone.UTC)

        assertEquals(Metric.entries, state.matrix.map { it.metric })
        assertEquals(Visibility.Delay, state.matrix.single { it.metric == Metric.Calories }.level)
        assertEquals(Visibility.Hide, state.matrix.single { it.metric == Metric.Micros }.level)
    }

    @Test
    fun theNoteSaysSetupUntilTheUserChangesSomething() {
        val setup = ModesStateBuilder.build(NumbersSettings(PresetId.NoNumbers), TimeZone.UTC)
        val changed = ModesStateBuilder.build(SettingsFixtures.adjusted, TimeZone.UTC)

        assertEquals(ChangedNote.DuringSetup, setup.changedNote)
        assertEquals(ChangedNote.LastChanged("Oct 25, 9:41 PM"), changed.changedNote)
        assertTrue(changed.pauseOn)
    }

    @Test
    fun thePhotosRow_onlyWithItsFlag_followingThePresetUntilChanged() {
        assertNull(ModesStateBuilder.build(NumbersSettings(PresetId.MacrosOnly), TimeZone.UTC).photos)

        assertEquals(PhotosRow(shown = false), ModesStateBuilder.build(NumbersSettings(PresetId.MacrosOnly), TimeZone.UTC, photosReleased = true).photos)
        assertEquals(PhotosRow(shown = true), ModesStateBuilder.build(NumbersSettings(PresetId.Full), TimeZone.UTC, photosReleased = true).photos)
    }

    @Test
    fun photosShownInNutrientsFirst_isAChangeFromHide_andCounts() {
        val fuel = SettingsFixtures.nutrientsFirstWithACarbsChange.copy(progressPhotos = true)

        val state = ModesStateBuilder.build(fuel, TimeZone.UTC, photosReleased = true)
        assertEquals(PhotosRow(shown = true, changedFrom = false), state.photos)
        assertEquals(2, state.presets.single { it.selected }.changes)

        val flagOff = ModesStateBuilder.build(fuel, TimeZone.UTC)
        assertEquals(1, flagOff.presets.single { it.selected }.changes, "a row that isn't drawn isn't counted")
    }

    // --- view model ---

    @Test
    fun photosTapped_goesThroughTheChanger_andOpensThePauseWhenItSaysSo() = runUnitTest {
        val changer = FakeNumbersChanger { NumbersOutcome.NeedsPause }
        val vm = viewModel(NumbersSettings(PresetId.MacrosOnly, pauseBeforeMore = true), changer, photos = true)
        val events = eventsOf(vm)

        vm.takeAction(ModesAction.PhotosTapped(shown = false))
        assertTrue(changer.requested.isEmpty(), "already hidden in Nutrients first")

        vm.takeAction(ModesAction.PhotosTapped(shown = true))
        val change = NumbersChange.SetProgressPhotos(shown = true)
        assertEquals(listOf<NumbersChange>(change), changer.requested)
        assertEquals(listOf<ModesEvent>(ModesEvent.OpenPause(change)), events)
    }

    @Test
    fun photosFlagOff_noRow_andATapDoesNothing() = runUnitTest {
        val changer = FakeNumbersChanger()
        val vm = viewModel(NumbersSettings(PresetId.MacrosOnly), changer, photos = false)

        assertNull(vm.state.photos)
        vm.takeAction(ModesAction.PhotosTapped(shown = true))
        assertTrue(changer.requested.isEmpty())
    }

    private fun viewModel(
        numbers: NumbersSettings = NumbersSettings(PresetId.Full),
        changer: FakeNumbersChanger = FakeNumbersChanger(),
        photos: Boolean = false,
    ) = ModesViewModel(FakeSettingsRepository(SettingsFixtures.settings(numbers)), changer, photosFlag(photos))

    @Test
    fun settingsArriveAsState() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings(NumbersSettings(PresetId.Full)))
        val vm = ModesViewModel(repository, FakeNumbersChanger(), photosFlag())

        repository.state.value = SettingsFixtures.settings(NumbersSettings(PresetId.NoNumbers, pauseBeforeMore = true))

        assertEquals(PresetId.NoNumbers, vm.state.presets.single { it.selected }.id)
        assertTrue(vm.state.pauseOn)
    }

    @Test
    fun pickingAPresetThatShowsFewerSaysSo() = runUnitTest {
        val changer = FakeNumbersChanger { NumbersOutcome.Applied(fewer = true) }
        val vm = viewModel(changer = changer)
        val events = eventsOf(vm)

        vm.takeAction(ModesAction.PresetTapped(PresetId.NoNumbers))

        assertEquals(listOf<NumbersChange>(NumbersChange.PickPreset(PresetId.NoNumbers)), changer.requested)
        assertEquals(listOf<ModesEvent>(ModesEvent.ShowToast(Toast(Res.string.settings_saved_fewer))), events)
    }

    @Test
    fun anEqualOrRaisingChangeThatAppliesJustSaysSaved() = runUnitTest {
        val vm = viewModel(NumbersSettings(PresetId.NoNumbers))
        val events = eventsOf(vm)

        vm.takeAction(ModesAction.PresetTapped(PresetId.Full))

        assertEquals(listOf<ModesEvent>(ModesEvent.ShowToast(Toast(Res.string.settings_saved))), events)
    }

    @Test
    fun aChangeThatNeedsThePauseOpensTheSheetWithThatChange() = runUnitTest {
        val vm = viewModel(NumbersSettings(PresetId.NoNumbers), FakeNumbersChanger { NumbersOutcome.NeedsPause })
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
    fun tappingTheCurrentPresetWithChangesClearsThem() = runUnitTest {
        val changer = FakeNumbersChanger()
        val vm = viewModel(SettingsFixtures.nutrientsFirstWithACarbsChange, changer)

        vm.takeAction(ModesAction.PresetTapped(PresetId.MacrosOnly))

        assertEquals(listOf<NumbersChange>(NumbersChange.PickPreset(PresetId.MacrosOnly)), changer.requested)
    }

    @Test
    fun endAfterOn_startsTwoWeeksThenNoNumbers_andOpensItsChoices() = runUnitTest {
        val changer = FakeNumbersChanger()
        val repository = FakeSettingsRepository(SettingsFixtures.settings(NumbersSettings(PresetId.Full)))
        val vm = ModesViewModel(repository, changer, photosFlag())

        vm.takeAction(ModesAction.EndAfterToggled)
        assertEquals(listOf<Pair<Int, PresetId>?>(2 to PresetId.NoNumbers), changer.timers)

        // The changer wrote it; the page opens the length and next-mode choices for it.
        repository.state.value = SettingsFixtures.settings(SettingsFixtures.fullForTwoWeeks)
        assertTrue(vm.state.endAfter.editing)
    }

    @Test
    fun endAfterOn_fromNoNumbers_offersMacrosOnly() = runUnitTest {
        val changer = FakeNumbersChanger()
        val vm = viewModel(NumbersSettings(PresetId.NoNumbers), changer)

        vm.takeAction(ModesAction.EndAfterToggled)

        assertEquals(listOf<Pair<Int, PresetId>?>(2 to PresetId.MacrosOnly), changer.timers)
    }

    @Test
    fun endAfterOff_clearsIt_andNeverPauses() = runUnitTest {
        val changer = FakeNumbersChanger { NumbersOutcome.NeedsPause }
        val vm = viewModel(SettingsFixtures.fullForTwoWeeks, changer)
        val events = eventsOf(vm)

        vm.takeAction(ModesAction.EndAfterToggled)

        assertEquals(listOf<Pair<Int, PresetId>?>(null), changer.timers)
        assertTrue(changer.requested.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun endAfterChoices_changeTheLengthOrTheNextMode() = runUnitTest {
        val changer = FakeNumbersChanger()
        val vm = viewModel(SettingsFixtures.fullForTwoWeeks, changer)

        vm.takeAction(ModesAction.EndAfterWeeksPicked(4))
        vm.takeAction(ModesAction.EndAfterThenPicked(PresetId.MacrosOnly))
        vm.takeAction(ModesAction.EndAfterWeeksPicked(2))

        assertEquals(listOf<Pair<Int, PresetId>?>(4 to PresetId.NoNumbers, 2 to PresetId.MacrosOnly), changer.timers, "picking what it already is does nothing")
    }

    @Test
    fun eachNumber_opensItsPage() = runUnitTest {
        val vm = viewModel()
        val events = eventsOf(vm)

        vm.takeAction(ModesAction.EachNumberTapped)

        assertEquals(listOf<ModesEvent>(ModesEvent.OpenEachNumber), events)
    }

    @Test
    fun revealingEachDayInsteadOfWeekly_goesThroughTheChanger() = runUnitTest {
        val changer = FakeNumbersChanger { NumbersOutcome.NeedsPause }
        val vm = viewModel(NumbersSettings.later().copy(revealOn = RevealOn.Week, pauseBeforeMore = true), changer)
        val events = eventsOf(vm)

        vm.takeAction(ModesAction.RevealWeeklyToggled)

        val change = NumbersChange.SetRevealWeekly(weekly = false)
        assertEquals(listOf<NumbersChange>(change), changer.requested)
        assertEquals(listOf<ModesEvent>(ModesEvent.OpenPause(change)), events)
    }

    @Test
    fun theRevealTimePickerOpensAndCloses() = runUnitTest {
        val vm = viewModel(NumbersSettings.later())

        vm.takeAction(ModesAction.RevealTimeTapped)
        assertTrue(vm.state.pickingRevealTime)

        vm.takeAction(ModesAction.RevealTimeDismissed)
        assertEquals(false, vm.state.pickingRevealTime)
    }

    @Test
    fun pickingARevealTimeGoesThroughTheChanger() = runUnitTest {
        val changer = FakeNumbersChanger { NumbersOutcome.NeedsPause }
        val vm = viewModel(NumbersSettings.later().copy(pauseBeforeMore = true), changer)
        val events = eventsOf(vm)

        vm.takeAction(ModesAction.RevealTimeTapped)
        vm.takeAction(ModesAction.RevealTimePicked(LocalTime(19, 0)))

        val change = NumbersChange.SetRevealAt(LocalTime(19, 0))
        assertEquals(listOf<NumbersChange>(change), changer.requested)
        assertEquals(listOf<ModesEvent>(ModesEvent.OpenPause(change)), events)
        assertEquals(false, vm.state.pickingRevealTime)
    }

    @Test
    fun pickingTheSameRevealTimeDoesNothing() = runUnitTest {
        val changer = FakeNumbersChanger()
        val vm = viewModel(NumbersSettings.later(), changer)

        vm.takeAction(ModesAction.RevealTimePicked(NumbersSettings.DEFAULT_REVEAL_AT))

        assertTrue(changer.requested.isEmpty())
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
        val vm = viewModel(NumbersSettings(PresetId.NoNumbers, pauseBeforeMore = true), changer)
        val events = eventsOf(vm)

        vm.takeAction(ModesAction.PauseToggled)

        assertEquals(listOf<NumbersChange>(NumbersChange.TurnOffPause), changer.requested)
        assertEquals(listOf<ModesEvent>(ModesEvent.OpenPause(NumbersChange.TurnOffPause)), events)
    }

    @Test
    fun backCloses() = runUnitTest {
        val vm = viewModel()
        val events = eventsOf(vm)

        vm.takeAction(ModesAction.BackTapped)

        assertEquals(listOf<ModesEvent>(ModesEvent.Close), events)
    }

    // --- weight: Numbers, Trend only, Hidden (moved here from the Weight screen) ---

    @Test
    fun weightsTrendOnlyIsTheSoftColumn_andAChangeIsMarked() {
        val fuel = ModesStateBuilder.build(NumbersSettings(PresetId.MacrosOnly), TimeZone.UTC)
        assertEquals(MatrixRow(Metric.Weight, Visibility.Soften), fuel.matrix.single { it.metric == Metric.Weight })

        val hidden = NumbersSettings(PresetId.MacrosOnly, overrides = mapOf(Metric.Weight to Visibility.Hide))
        val state = ModesStateBuilder.build(hidden, TimeZone.UTC)
        assertEquals(MatrixRow(Metric.Weight, Visibility.Hide, changedFrom = Visibility.Soften), state.matrix.single { it.metric == Metric.Weight })
        assertEquals(1, state.presets.single { it.selected }.changes)
    }

    @Test
    fun weightBackToNumbersWaitsForThePause_hidingItDoesNot() {
        val trendOnlyPaused = NumbersSettings(PresetId.MacrosOnly, pauseBeforeMore = true)

        assertTrue(NumbersChange.Override(Metric.Weight, Visibility.Show).needsPause(trendOnlyPaused))
        assertEquals(false, NumbersChange.Override(Metric.Weight, Visibility.Hide).needsPause(trendOnlyPaused))
        assertEquals(false, NumbersChange.Override(Metric.Weight, Visibility.Show).needsPause(trendOnlyPaused.copy(pauseBeforeMore = false)))
    }

    @Test
    fun hidingWeightAppliesAndSaysFewer() = runUnitTest {
        val changer = FakeNumbersChanger { NumbersOutcome.Applied(fewer = true) }
        val vm = viewModel(NumbersSettings(PresetId.MacrosOnly, pauseBeforeMore = true), changer)
        val events = eventsOf(vm)

        vm.takeAction(ModesAction.LevelTapped(Metric.Weight, Visibility.Hide))

        assertEquals(listOf<NumbersChange>(NumbersChange.Override(Metric.Weight, Visibility.Hide)), changer.requested)
        assertEquals(listOf<ModesEvent>(ModesEvent.ShowToast(Toast(Res.string.settings_saved_fewer))), events)
    }

    @Test
    fun weightAlreadyAtTrendOnlyIsANoOp() = runUnitTest {
        val changer = FakeNumbersChanger()
        val vm = viewModel(NumbersSettings(PresetId.MacrosOnly), changer)

        vm.takeAction(ModesAction.LevelTapped(Metric.Weight, Visibility.Soften))

        assertTrue(changer.requested.isEmpty())
    }
}
