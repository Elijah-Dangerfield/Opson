package com.opson.features.trends.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Units
import com.opson.libraries.nutrition.WeightUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The weight view model: saving converts and validates, typing is sanitised,
 * and the range is the page's own. There is no view picker: what shows is set
 * in Modes and numbers. What each view
 * shows is `WeightStateBuilderTest`.
 */
class WeightViewModelTest : CoroutineTest() {

    private val weights = FakeWeightRepository()

    private fun vm(settings: FakeSettingsRepository = FakeSettingsRepository(), preset: PresetId = PresetId.Full) =
        WeightViewModel(
            weights = weights,
            settingsRepository = settings,
            policySource = FakePolicySource(TrendsFixtures.policy(preset)),
            clock = TrendsFixtures.clock,
            timeZone = TrendsFixtures.timeZone,
        )

    private fun CoroutineScope.events(vm: WeightViewModel) = mutableListOf<WeightEvent>().also { list ->
        launch { vm.eventFlow.collect { list += it } }
    }

    @Test
    fun inputsArrive_buildTheNumbersPage() = runUnitTest {
        val vm = vm()
        assertEquals(false, vm.state.loading)
        assertNotNull(vm.state.chart)
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
    fun backTapped_closes() = runUnitTest {
        val vm = vm()
        val events = backgroundScope.events(vm)
        vm.takeAction(WeightAction.BackTapped)
        assertEquals(listOf<WeightEvent>(WeightEvent.Close), events)
    }
}
