package com.opson.features.settings.impl.more

import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.eventsOf
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The More list (design v3): three cards, the changes count on the Modes row, the water goal,
 * and where each row goes. No row carries an amount, so nothing here reads the policy. Not
 * covered: the routes the events become (the entry point).
 */
class MoreTest : CoroutineTest() {

    private fun build(numbers: NumbersSettings = NumbersSettings()) = MoreStateBuilder.build(SettingsFixtures.settings(numbers))

    @Test
    fun theRowsAreThreeCardsInOrder() {
        assertEquals(
            listOf(
                listOf(MoreRow.Modes(PresetId.Full, changes = 0), MoreRow.Targets, MoreRow.LookAndFeel),
                listOf(MoreRow.Water(glasses = 8)),
                listOf(MoreRow.Support, MoreRow.Plan),
            ),
            build().groups,
        )
    }

    @Test
    fun theModesRowCountsTheChanges() {
        assertEquals(MoreRow.Modes(PresetId.Fuel, changes = 1), build(SettingsFixtures.fuelWithACarbsChange).groups[0][0])
        assertEquals(MoreRow.Modes(PresetId.Full, changes = 2), build(SettingsFixtures.adjusted).groups[0][0])
    }

    @Test
    fun theListIsTheSameInEveryMode() {
        val rows = build().groups.flatten().map { it::class }
        PresetId.entries.forEach { preset ->
            assertEquals(rows, build(NumbersSettings(preset)).groups.flatten().map { it::class }, "in $preset")
        }
    }

    @Test
    fun settingsArriveAsState() = runUnitTest {
        val settings = FakeSettingsRepository(SettingsFixtures.settings())
        val vm = MoreViewModel(settings)

        settings.state.value = settings.state.value.copy(waterGlasses = 10)

        assertEquals(MoreRow.Water(glasses = 10), vm.state.groups[1].single())
    }

    @Test
    fun eachRowOpensItsPage_andPlanOpensNothing() = runUnitTest {
        val vm = MoreViewModel(FakeSettingsRepository())
        val events = eventsOf(vm)

        vm.state.groups.flatten().forEach { vm.takeAction(MoreAction.RowTapped(it)) }

        assertEquals(
            listOf(MoreEvent.OpenModes, MoreEvent.OpenTargets, MoreEvent.OpenLookAndFeel, MoreEvent.OpenWater, MoreEvent.OpenSupport),
            events,
        )
    }
}
