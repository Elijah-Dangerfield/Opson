package com.opson.features.settings.impl.water

import com.opson.features.settings.impl.FakeNumbersChanger
import com.opson.features.settings.impl.FakeNotificationPermissions
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.Toast
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.remindersFlag
import com.opson.libraries.diary.NumbersOutcome
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.WaterUnit
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_saved_fewer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Water settings: what "Show on Today" asks the changer for in each mode, the plain writes for
 * goal, glass size and unit, and the relabelled sizes. The pause decision is the changer's.
 */
class WaterSettingsTest : CoroutineTest() {

    // --- builder ---

    @Test
    fun theGoalReadsInTheUsersUnit() {
        val state = WaterSettingsStateBuilder.build(SettingsFixtures.settings(), backToMore = true)

        assertEquals(8, state.glasses)
        assertEquals("2,000 ml", state.goalAmount)
        assertEquals(listOf(200, 250, 330, 500), state.sizes.map { it.ml })
        assertEquals("250 ml", state.sizes.single { it.ml == 250 }.label)
        assertTrue(state.backToMore)
    }

    @Test
    fun switchingUnitsRelabelsTheSizes() {
        val ml = WaterSettingsStateBuilder.build(SettingsFixtures.settings(), backToMore = false)
        val flOz = WaterSettingsStateBuilder.build(SettingsFixtures.settings().copy(waterUnit = WaterUnit.FlOz), false)

        assertEquals(ml.sizes.map { it.ml }, flOz.sizes.map { it.ml })
        flOz.sizes.forEach { assertTrue(it.label.endsWith("fl oz"), it.label) }
        assertNotEquals(ml.goalAmount, flOz.goalAmount)
    }

    @Test
    fun showOnTodayFollowsTheConfiguredWaterLevel() {
        fun shows(numbers: NumbersSettings) =
            WaterSettingsStateBuilder.build(SettingsFixtures.settings(numbers), backToMore = false).showOnToday

        assertTrue(shows(NumbersSettings(PresetId.Full)))
        assertTrue(shows(NumbersSettings(PresetId.Blind)))
        assertFalse(shows(NumbersSettings(PresetId.Blind, overrides = mapOf(Metric.Water to Visibility.Hide))))
    }

    @Test
    fun turningWaterOffHidesItInAnyMode() {
        listOf(PresetId.Full, PresetId.Blind, PresetId.Sprint).forEach { preset ->
            assertEquals(
                NumbersChange.Override(Metric.Water, Visibility.Hide),
                WaterSettingsStateBuilder.toggleChange(NumbersSettings(preset)),
            )
        }
    }

    @Test
    fun turningWaterBackOnReturnsToTheModesOwnLevel() {
        listOf(PresetId.Full, PresetId.Blind).forEach { preset ->
            val hidden = NumbersSettings(preset, overrides = mapOf(Metric.Water to Visibility.Hide))
            assertEquals(NumbersChange.ClearOverride(Metric.Water), WaterSettingsStateBuilder.toggleChange(hidden))
        }
    }

    // --- view model ---

    @Test
    fun theSwitchGoesThroughTheChanger() = runUnitTest {
        val changer = FakeNumbersChanger { NumbersOutcome.Applied(fewer = true) }
        val vm = WaterSettingsViewModel(false, FakeSettingsRepository(SettingsFixtures.settings()), changer, remindersFlag(), FakeNotificationPermissions())
        val events = eventsOf(vm)

        vm.takeAction(WaterSettingsAction.ShowOnTodayToggled)

        assertEquals(listOf<NumbersChange>(NumbersChange.Override(Metric.Water, Visibility.Hide)), changer.requested)
        assertEquals(listOf<WaterSettingsEvent>(WaterSettingsEvent.ShowToast(Toast(Res.string.settings_saved_fewer))), events)
    }

    @Test
    fun showingWaterWithThePauseOnOpensThePause() = runUnitTest {
        val hidden = NumbersSettings(PresetId.Blind, overrides = mapOf(Metric.Water to Visibility.Hide), pauseBeforeMore = true)
        val vm = WaterSettingsViewModel(
            false,
            FakeSettingsRepository(SettingsFixtures.settings(hidden)),
            FakeNumbersChanger { NumbersOutcome.NeedsPause },
            remindersFlag(),
            FakeNotificationPermissions(),
        )
        val events = eventsOf(vm)

        vm.takeAction(WaterSettingsAction.ShowOnTodayToggled)

        assertEquals(listOf<WaterSettingsEvent>(WaterSettingsEvent.OpenPause(NumbersChange.ClearOverride(Metric.Water))), events)
    }

    @Test
    fun goalSizeAndUnitAreSavedWithoutThePause() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings())
        val changer = FakeNumbersChanger()
        val vm = WaterSettingsViewModel(true, repository, changer, remindersFlag(), FakeNotificationPermissions())

        vm.takeAction(WaterSettingsAction.GoalPicked(10))
        vm.takeAction(WaterSettingsAction.GlassSizePicked(330))
        vm.takeAction(WaterSettingsAction.UnitPicked(WaterUnit.FlOz))

        val settings = repository.state.value
        assertEquals(10, settings.waterGlasses)
        assertEquals(330, settings.glassMl)
        assertEquals(WaterUnit.FlOz, settings.waterUnit)
        assertTrue(changer.requested.isEmpty())
        assertEquals(10, vm.state.glasses)
        assertTrue(vm.state.backToMore)
    }

    @Test
    fun backCloses() = runUnitTest {
        val vm = WaterSettingsViewModel(false, FakeSettingsRepository(), FakeNumbersChanger(), remindersFlag(), FakeNotificationPermissions())
        val events = eventsOf(vm)

        vm.takeAction(WaterSettingsAction.BackTapped)

        assertEquals(listOf<WaterSettingsEvent>(WaterSettingsEvent.Close), events)
    }

    @Test
    fun theRemindersRowShowsOnlyWithTheFlag() {
        val settings = SettingsFixtures.settings().copy(waterReminders = true)
        assertEquals(null, WaterSettingsStateBuilder.build(settings, backToMore = true).reminders)
        assertEquals(true, WaterSettingsStateBuilder.build(settings, backToMore = true, remindersOn = true).reminders)
    }

    @Test
    fun turningWaterRemindersOnAsksForNotifications_once_andOffNeverAsks() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings())
        val permissions = FakeNotificationPermissions()
        val vm = WaterSettingsViewModel(true, repository, FakeNumbersChanger(), remindersFlag(on = true), permissions)

        vm.takeAction(WaterSettingsAction.RemindersToggled)
        assertEquals(true, repository.state.value.waterReminders)
        assertEquals(1, permissions.prompts)
        assertEquals(true, vm.state.reminders)

        vm.takeAction(WaterSettingsAction.RemindersToggled)
        vm.takeAction(WaterSettingsAction.RemindersToggled)
        assertEquals(true, repository.state.value.waterReminders)
        assertEquals(1, permissions.prompts)
    }
}
