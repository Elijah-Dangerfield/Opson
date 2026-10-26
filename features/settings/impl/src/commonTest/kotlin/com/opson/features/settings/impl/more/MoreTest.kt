package com.opson.features.settings.impl.more

import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.eventsOf
import com.opson.libraries.diary.FastingRelease
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.FastingPlan
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
        val vm = MoreViewModel(settings, FastingRelease { false })

        settings.state.value = settings.state.value.copy(waterGlasses = 10)

        assertEquals(MoreRow.Water(glasses = 10), vm.state.groups[1].single())
    }

    @Test
    fun eachRowOpensItsPage_andPlanOpensNothing() = runUnitTest {
        val vm = MoreViewModel(FakeSettingsRepository(), FastingRelease { false })
        val events = eventsOf(vm)

        vm.state.groups.flatten().forEach { vm.takeAction(MoreAction.RowTapped(it)) }

        assertEquals(
            listOf(MoreEvent.OpenModes, MoreEvent.OpenTargets, MoreEvent.OpenLookAndFeel, MoreEvent.OpenWater, MoreEvent.OpenSupport),
            events,
        )
    }

    // --- Fasting (0.2) ---

    private fun withFasting(preset: PresetId, hard: Boolean = false, released: Boolean = true, on: Boolean = false) =
        MoreStateBuilder.build(
            SettingsFixtures.settings(NumbersSettings(preset)).copy(fasting = on, onboarding = OnboardingAnswers(hard = hard)),
            fastingReleased = released,
        )

    @Test
    fun fastingSitsAfterWaterOnlyInFullAndSprint() {
        PresetId.entries.forEach { preset ->
            val extras = withFasting(preset).groups[1]
            val expected = if (preset == PresetId.Full || preset == PresetId.Sprint) {
                listOf(MoreRow.Water(glasses = 8), MoreRow.Fasting(on = false, plan = FastingPlan.FourteenTen))
            } else {
                listOf(MoreRow.Water(glasses = 8))
            }
            assertEquals(expected, extras, "in $preset")
        }
    }

    @Test
    fun fastingNeverShowsAfterTheStatementInAnyMode() {
        PresetId.entries.forEach { preset ->
            assertEquals(listOf(MoreRow.Water(glasses = 8)), withFasting(preset, hard = true, on = true).groups[1], "in $preset")
        }
    }

    @Test
    fun fastingNeverShowsWithTheFlagOff() {
        assertEquals(listOf(MoreRow.Water(glasses = 8)), withFasting(PresetId.Full, released = false).groups[1])
        assertEquals(listOf(MoreRow.Water(glasses = 8)), build().groups[1], "0.1 callers pass no flag")
    }

    @Test
    fun theFastingRowSaysOnWithThePlan() {
        assertEquals(MoreRow.Fasting(on = true, plan = FastingPlan.FourteenTen), withFasting(PresetId.Sprint, on = true).groups[1][1])
    }

    @Test
    fun theFastingRowOpensFasting() = runUnitTest {
        val vm = MoreViewModel(FakeSettingsRepository(SettingsFixtures.settings()), FastingRelease { true })
        val events = eventsOf(vm)

        vm.takeAction(MoreAction.RowTapped(vm.state.groups[1][1]))

        assertEquals(listOf(MoreEvent.OpenFasting), events)
    }
}
