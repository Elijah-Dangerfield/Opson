package com.opson.features.trends.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Units
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.WeightUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The weight view model: saving converts and validates, typing is sanitised,
 * the view picker goes through the numbers rule (a raise with the pause on
 * waits for the pause sheet), and the range is the page's own. What each view
 * shows is `WeightStateBuilderTest`.
 */
class WeightViewModelTest : CoroutineTest() {

    private val weights = FakeWeightRepository()

    private fun vm(settings: FakeSettingsRepository = FakeSettingsRepository(), preset: PresetId = PresetId.Full) =
        WeightViewModel(
            weights = weights,
            settingsRepository = settings,
            policySource = FakePolicySource(TrendsFixtures.policy(preset)),
            numbers = FakeNumbersChanger(settings),
            clock = TrendsFixtures.clock,
            timeZone = TrendsFixtures.timeZone,
        )

    private fun CoroutineScope.events(vm: WeightViewModel) = mutableListOf<WeightEvent>().also { list ->
        launch { vm.eventFlow.collect { list += it } }
    }

    @Test
    fun inputsArrive_numbersView() = runUnitTest {
        val vm = vm()
        assertEquals(false, vm.state.loading)
        assertEquals(WeightView.Numbers, vm.state.selected)
    }

    @Test
    fun weightTyped_keepsDigitsAndOnePoint_clearsSaved() = runUnitTest {
        val vm = vm()
        vm.takeAction(WeightAction.WeightTyped("7x2,4.5"))
        assertEquals("72.45", vm.state.input)
    }

    @Test
    fun saveTapped_kilograms_savesAndClears() = runUnitTest {
        val vm = vm()
        vm.takeAction(WeightAction.WeightTyped("72.4"))
        vm.takeAction(WeightAction.SaveTapped)
        assertEquals(listOf(72.4), weights.added)
        assertEquals("", vm.state.input)
        assertEquals(SavedNote.Plain, vm.state.saved)
    }

    @Test
    fun saveTapped_pounds_convertsToKilograms() = runUnitTest {
        val vm = vm(FakeSettingsRepository(Settings(weightUnit = WeightUnit.Lb)))
        vm.takeAction(WeightAction.WeightTyped("160"))
        vm.takeAction(WeightAction.SaveTapped)
        assertEquals(Units.lbToKg(160.0), weights.added.single())
    }

    @Test
    fun saveTapped_hidden_staysOutOfView() = runUnitTest {
        val vm = vm(preset = PresetId.Blind)
        assertEquals(true, vm.state.inputMasked)
        vm.takeAction(WeightAction.WeightTyped("72.4"))
        vm.takeAction(WeightAction.SaveTapped)
        assertEquals(SavedNote.OutOfView, vm.state.saved)
    }

    @Test
    fun saveTapped_invalid_isIgnored() = runUnitTest {
        val vm = vm()
        vm.takeAction(WeightAction.SaveTapped)
        vm.takeAction(WeightAction.WeightTyped("0"))
        vm.takeAction(WeightAction.SaveTapped)
        vm.takeAction(WeightAction.WeightTyped("7000"))
        vm.takeAction(WeightAction.SaveTapped)
        assertTrue(weights.added.isEmpty())
        assertEquals("7000", vm.state.input)
        assertEquals(null, vm.state.saved)
    }

    @Test
    fun rangePicked_slicesTheChart() = runUnitTest {
        val vm = vm()
        vm.takeAction(WeightAction.RangePicked(WeightRange.W1))
        assertEquals(WeightRange.W1, vm.state.range)
        assertEquals(7, vm.state.chart?.dots?.size)
    }

    @Test
    fun viewPicked_lowering_appliesAtOnce() = runUnitTest {
        val settings = FakeSettingsRepository(Settings(numbers = NumbersSettings(pauseBeforeMore = true)))
        val vm = vm(settings)
        val events = backgroundScope.events(vm)
        vm.takeAction(WeightAction.ViewPicked(WeightView.Hidden))
        assertEquals(Visibility.Hide, settings.settings.value.numbers.overrides[Metric.Weight])
        assertEquals(emptyList(), events)
    }

    @Test
    fun viewPicked_raisingWithThePause_opensThePauseAndWritesNothing() = runUnitTest {
        val blindPaused = Settings(numbers = NumbersSettings(preset = PresetId.Blind, pauseBeforeMore = true))
        val settings = FakeSettingsRepository(blindPaused)
        val vm = vm(settings, PresetId.Blind)
        val events = backgroundScope.events(vm)
        vm.takeAction(WeightAction.ViewPicked(WeightView.Numbers))
        assertEquals(listOf<WeightEvent>(WeightEvent.OpenPause(NumbersChange.Override(Metric.Weight, Visibility.Show))), events)
        assertEquals(blindPaused, settings.settings.value)
    }

    @Test
    fun viewPicked_raisingWithoutThePause_applies() = runUnitTest {
        val settings = FakeSettingsRepository(Settings(numbers = NumbersSettings(preset = PresetId.Blind)))
        val vm = vm(settings, PresetId.Blind)
        vm.takeAction(WeightAction.ViewPicked(WeightView.TrendOnly))
        assertEquals(Visibility.Soften, settings.settings.value.numbers.overrides[Metric.Weight])
    }

    @Test
    fun viewPicked_theSelectedOne_doesNothing() = runUnitTest {
        val settings = FakeSettingsRepository()
        val vm = vm(settings)
        vm.takeAction(WeightAction.ViewPicked(WeightView.Numbers))
        assertEquals(0, settings.updates)
    }

    @Test
    fun backTapped_closes() = runUnitTest {
        val vm = vm()
        val events = backgroundScope.events(vm)
        vm.takeAction(WeightAction.BackTapped)
        assertEquals(listOf<WeightEvent>(WeightEvent.Close), events)
    }
}
