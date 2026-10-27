package com.opson.features.settings.impl.energy

import com.opson.features.settings.impl.FakeBurnedRepository
import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.integrations.FakeCalendar
import com.opson.features.settings.impl.integrations.FakeDiary
import com.opson.features.settings.impl.integrations.FakeHealthRepository
import com.opson.features.settings.impl.integrations.healthFlag
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Energy today (design sync 4, 13.2c), over its fixture day: 1,245 eaten, seven foods, one
 * weighed and one from a label, against 2,180 burned.
 */
class EnergyTodayTest : CoroutineTest() {

    private fun build(preset: PresetId = PresetId.Full, overrides: Map<Metric, Visibility> = emptyMap(), burned: Boolean = true, style: TargetStyle? = null) =
        EnergyTodayStateBuilder.build(
            EnergyTodayFixtures.inputs(
                SettingsFixtures.settings().let { s -> style?.let { s.copy(targets = s.targets.copy(style = it)) } ?: s },
                burned,
            ),
            SettingsFixtures.policy(NumbersSettings(preset = preset, overrides = overrides)),
        )

    @Test
    fun eaten_withItsGiveOrTake() {
        val eaten = assertNotNull(build().eaten)
        assertEquals("1,245", (eaten.amount as Display.Shown).text)
        // Estimates 35, 280, 95, 220, 75 at 25%, the label at 10% and the weighed oats at 5%: √9,686 is 98.
        assertEquals(Display.Shown("100"), eaten.plusMinus)
        assertEquals(Display.Shown("1,145 to 1,345"), eaten.likely)
        assertEquals(5, eaten.estimates)
        assertEquals(7, eaten.foods)
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat), eaten.parts.map { it.metric })
    }

    @Test
    fun burned_withWhereEachPartComesFrom() {
        val burned = assertNotNull(build().burned)
        assertTrue(burned.known)
        assertEquals(Display.Shown("2,180"), burned.total)
        assertEquals(Display.Shown("1,610"), burned.resting)
        assertEquals(RestingFrom.Calculated, burned.restingFrom)
        assertEquals(Display.Shown("390"), burned.movement)
        assertEquals(Display.Shown("180"), burned.workouts)
        assertTrue(burned.restingShare > burned.activeShare)
    }

    @Test
    fun theDeficitSoFar_andTheGapToTheRange() {
        val state = build()
        assertEquals(Balance(surplus = false, amount = Display.Shown("935")), state.balance)
        assertEquals(ToGoal(single = false, metric = Metric.Calories, amount = Display.Shown("655")), state.toGoal)
    }

    @Test
    fun oneNumber_isAGoal_notARange() {
        assertEquals(true, build(style = TargetStyle.Exact).toGoal?.single)
    }

    @Test
    fun noBurned_promptsForDetails_andHasNoDeficit() {
        val state = build(burned = false)
        assertFalse(assertNotNull(state.burned).known)
        assertNull(state.balance)
    }

    @Test
    fun hiddenWatchEnergy_dropsBurnedAndTheDeficit_keepsEaten() {
        val state = build(overrides = mapOf(Metric.EnergyOut to Visibility.Hide))
        assertNull(state.burned)
        assertNull(state.balance)
        assertNotNull(state.eaten)
    }

    @Test
    fun hiddenCalories_showNoEnergyAtAll() {
        listOf(PresetId.Blind, PresetId.Fuel, PresetId.Nutrient).forEach { preset ->
            val state = build(preset)
            assertNull(state.eaten, "$preset")
            assertNull(state.balance, "$preset")
            assertNull(state.toGoal, "$preset")
        }
    }

    // --- view model ---

    private fun viewModel() = EnergyTodayViewModel(
        FakeDiary(listOf(EnergyTodayFixtures.day)),
        FakeSettingsRepository(),
        FakeBurnedRepository(),
        FakePolicySource(SettingsFixtures.policy(PresetId.Full)),
        FakeHealthRepository(),
        healthFlag(false),
        FakeCalendar(EnergyTodayFixtures.today),
    )

    @Test
    fun theDayArrives() = runUnitTest {
        val vm = viewModel()
        assertTrue(vm.state.loaded)
        assertEquals("1,245", (vm.state.eaten?.amount as? Display.Shown)?.text)
    }

    @Test
    fun nutrientsRestingAndBack_navigate() = runUnitTest {
        val vm = viewModel()
        val events = eventsOf(vm)
        vm.takeAction(EnergyTodayAction.NutrientsTapped)
        vm.takeAction(EnergyTodayAction.RestingEnergyTapped)
        vm.takeAction(EnergyTodayAction.BackTapped)
        assertEquals(
            listOf(EnergyTodayEvent.OpenNutrients(EnergyTodayFixtures.today), EnergyTodayEvent.OpenRestingEnergy, EnergyTodayEvent.Close),
            events,
        )
    }
}
