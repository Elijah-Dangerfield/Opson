package com.opson.features.settings.impl.targets

import com.opson.features.settings.impl.FakeBurnedRepository
import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.goalsAndEnergyFlag
import com.opson.features.settings.impl.integrations.FakeCalendar
import com.opson.features.settings.impl.integrations.FakeHealthRepository
import com.opson.features.settings.impl.integrations.healthFlag
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Targets' Exercise adjustment row (v3 `targets.png`, 0.2): when it shows, what it says, where it goes. */
class TargetsExerciseTest : CoroutineTest() {

    private val some = SettingsFixtures.settings().copy(exerciseRange = ExerciseAdjustment.Some)

    @Test
    fun connected_showsTheAnswer() {
        val state = TargetsStateBuilder.build(some, SettingsFixtures.policy(PresetId.Full), healthConnected = true)
        assertEquals(ExerciseAdjustment.Some, state.exercise)
    }

    @Test
    fun unanswered_saysNo() {
        val state = TargetsStateBuilder.build(SettingsFixtures.settings(), SettingsFixtures.policy(PresetId.Full), healthConnected = true)
        assertEquals(ExerciseAdjustment.None, state.exercise)
    }

    @Test
    fun notConnected_hasNoRow() {
        assertNull(TargetsStateBuilder.build(some, SettingsFixtures.policy(PresetId.Full)).exercise)
    }

    @Test
    fun numbersHaveBeenHard_hasNoRow() {
        val hard = some.copy(onboarding = OnboardingAnswers(hard = true))
        assertNull(TargetsStateBuilder.build(hard, SettingsFixtures.policy(PresetId.Full), healthConnected = true).exercise)
    }

    @Test
    fun targetsOutOfView_hasNoRow() {
        listOf(PresetId.NoNumbers, PresetId.NutrientsFirst, PresetId.Later).forEach { preset ->
            val settings = some.copy(numbers = NumbersSettings(preset = preset))
            assertNull(TargetsStateBuilder.build(settings, SettingsFixtures.policy(preset), healthConnected = true).exercise, "$preset")
        }
    }

    private fun vm(health: FakeHealthRepository, flagOn: Boolean) =
        TargetsViewModel(
            FakeSettingsRepository(some),
            FakePolicySource(SettingsFixtures.policy(PresetId.Full)),
            health,
            healthFlag(flagOn),
            goalsAndEnergyFlag(),
            FakeBurnedRepository(),
            FakeCalendar(),
        )

    @Test
    fun flagOn_andConnected_showsTheRow() = runUnitTest {
        val health = FakeHealthRepository(status = HealthStatus.Connected)
        val vm = vm(health, flagOn = true)
        assertEquals(ExerciseAdjustment.Some, vm.state.exercise)

        health.statusFlow.value = HealthStatus.NotConnected
        assertNull(vm.state.exercise)
    }

    @Test
    fun flagOff_neverShowsTheRow_evenConnected() = runUnitTest {
        val vm = vm(FakeHealthRepository(status = HealthStatus.Connected), flagOn = false)
        assertTrue(vm.state.visible)
        assertNull(vm.state.exercise)
    }

    @Test
    fun exerciseAdjustmentTapped_opensWatchAndHealthApps() = runUnitTest {
        val vm = vm(FakeHealthRepository(status = HealthStatus.Connected), flagOn = true)
        val events = eventsOf(vm)
        vm.takeAction(TargetsAction.ExerciseAdjustmentTapped)
        assertEquals(listOf<TargetsEvent>(TargetsEvent.OpenIntegrations), events)
    }
}
