package com.opson.features.settings.impl.more

import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.eventsOf
import com.opson.libraries.diary.MacroMode
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The More list: which rows it has, the Targets subtitle per level, the adjusted label, and
 * where each row goes. Not covered: the routes the events become (the entry point).
 */
class MoreTest : CoroutineTest() {

    private fun build(numbers: NumbersSettings) =
        MoreStateBuilder.build(SettingsFixtures.settings(numbers), SettingsFixtures.policy(numbers))

    private fun MoreState.targets() = rows.filterIsInstance<MoreRow.Targets>().single()

    @Test
    fun fullShowsTheEnergyTarget() {
        assertEquals(Display.Shown("2,150 kcal"), build(NumbersSettings(PresetId.Full)).targets().energy)
    }

    @Test
    fun delayedBlindAndSoftCaloriesSayOutOfView() {
        listOf(PresetId.Delayed, PresetId.Blind, PresetId.Fuel, PresetId.Coast).forEach { preset ->
            assertEquals(Display.Hidden, build(NumbersSettings(preset)).targets().energy, "in $preset")
        }
    }

    @Test
    fun hidingCaloriesByOverrideHidesTheTarget() {
        val numbers = NumbersSettings(PresetId.Full, overrides = mapOf(Metric.Calories to Visibility.Hide))
        assertEquals(Display.Hidden, build(numbers).targets().energy)
    }

    @Test
    fun theModesRowSaysAdjustedWhenANumberWasChanged() {
        val adjusted = build(SettingsFixtures.adjusted).rows.first()
        val plain = build(NumbersSettings(PresetId.Blind)).rows.first()

        assertEquals(MoreRow.Modes(PresetId.Full, adjusted = true), adjusted)
        assertEquals(MoreRow.Modes(PresetId.Blind, adjusted = false), plain)
    }

    @Test
    fun theRowsAreThe01SetInOrder() {
        val rows = build(NumbersSettings()).rows

        assertEquals(
            listOf(MoreRow.Modes::class, MoreRow.Targets::class, MoreRow.Water::class, MoreRow.LookAndFeel::class, MoreRow.Support::class),
            rows.map { it::class },
        )
    }

    @Test
    fun inputsArriveFromSettingsAndThePolicy() = runUnitTest {
        val settings = FakeSettingsRepository(SettingsFixtures.settings())
        val policy = FakePolicySource(SettingsFixtures.policy(PresetId.Full))
        val vm = MoreViewModel(settings, policy)

        assertEquals(Display.Shown("2,150 kcal"), vm.state.targets().energy)

        settings.state.value = settings.state.value.copy(macroMode = MacroMode.Grams)
        policy.policy.value = SettingsFixtures.policy(PresetId.Blind)

        assertEquals(MoreRow.Targets(Display.Hidden, MacroMode.Grams), vm.state.targets())
    }

    @Test
    fun eachRowOpensItsPage() = runUnitTest {
        val vm = MoreViewModel(FakeSettingsRepository(), FakePolicySource(SettingsFixtures.policy(PresetId.Full)))
        val events = eventsOf(vm)

        vm.state.rows.forEach { vm.takeAction(MoreAction.RowTapped(it)) }

        assertEquals(
            listOf(MoreEvent.OpenModes, MoreEvent.OpenTargets, MoreEvent.OpenWater, MoreEvent.OpenLookAndFeel, MoreEvent.OpenSupport),
            events,
        )
    }
}
