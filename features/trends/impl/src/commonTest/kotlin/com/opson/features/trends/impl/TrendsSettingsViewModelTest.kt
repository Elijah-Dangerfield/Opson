package com.opson.features.trends.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TrendsCards
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Trends settings sheet: which switches each mode offers, and that each
 * action writes its own setting and nothing else. None of these touch
 * visibility, so none goes through the pause.
 */
class TrendsSettingsViewModelTest : CoroutineTest() {

    private val settings = FakeSettingsRepository()

    private fun vm(preset: PresetId = PresetId.Full, healthOn: Boolean = false, health: FakeHealthRepository = FakeHealthRepository()) =
        TrendsSettingsViewModel(settings, FakePolicySource(TrendsFixtures.policy(preset)), health, healthFlag(healthOn))

    @Test
    fun expended_offeredOnlyWithTheFlagOnAndAWatchConnected_andItsSwitchWritesItsCard() = runUnitTest {
        assertTrue(vm(healthOn = false).state.toggles.none { it.card == TrendsCard.Expended })
        assertTrue(vm(healthOn = true, health = FakeHealthRepository(initial = HealthStatus.NotConnected)).state.toggles.none { it.card == TrendsCard.Expended })

        val vm = vm(healthOn = true)
        assertEquals(true, vm.state.toggles.single { it.card == TrendsCard.Expended }.on)
        vm.takeAction(TrendsSettingsAction.CardToggled(TrendsCard.Expended))
        assertEquals(TrendsCards(expended = false), settings.settings.value.trendsCards)
    }

    @Test
    fun expended_notOfferedWhereEnergyOutIsHidden() {
        val blind = TrendsSettingsStateBuilder.build(Settings(), TrendsFixtures.policy(PresetId.Blind), healthConnected = true)
        assertTrue(blind.toggles.none { it.card == TrendsCard.Expended })
    }

    @Test
    fun toggles_filteredByWhatTheModeCanShow() {
        fun cards(preset: PresetId) = TrendsSettingsStateBuilder.build(Settings(), TrendsFixtures.policy(preset)).toggles.map { it.card }
        assertEquals(TrendsCard.entries - TrendsCard.Expended, cards(PresetId.Full), "Expended waits for a connected watch")
        assertEquals(listOf(TrendsCard.Recap), cards(PresetId.Blind), "weight is hidden, so its card never shows")
        assertEquals(listOf(TrendsCard.Recap, TrendsCard.Nutrients, TrendsCard.Weight), cards(PresetId.Fuel))
        assertEquals(listOf(TrendsCard.Intake, TrendsCard.Recap, TrendsCard.Weight), cards(PresetId.Coast))
    }

    @Test
    fun inputsArrive_stateFollowsSettings() = runUnitTest {
        val vm = vm()
        settings.settings.value = Settings(trendsCards = TrendsCards(recap = false), weekStart = DayOfWeek.SUNDAY)
        assertEquals(DayOfWeek.SUNDAY, vm.state.weekStart)
        assertEquals(false, vm.state.toggles.single { it.card == TrendsCard.Recap }.on)
    }

    @Test
    fun weekStartPicked_writesTheStartDay_onlyTheOfferedOnes() = runUnitTest {
        val vm = vm()
        vm.takeAction(TrendsSettingsAction.WeekStartPicked(DayOfWeek.SATURDAY))
        assertEquals(DayOfWeek.SATURDAY, settings.settings.value.weekStart)
        vm.takeAction(TrendsSettingsAction.WeekStartPicked(DayOfWeek.WEDNESDAY))
        assertEquals(DayOfWeek.SATURDAY, settings.settings.value.weekStart)
    }

    @Test
    fun cardToggled_flipsOnlyThatCard() = runUnitTest {
        val vm = vm()
        vm.takeAction(TrendsSettingsAction.CardToggled(TrendsCard.Nutrients))
        assertEquals(TrendsCards(nutrients = false), settings.settings.value.trendsCards)
        assertEquals(false, vm.state.toggles.single { it.card == TrendsCard.Nutrients }.on)
        vm.takeAction(TrendsSettingsAction.CardToggled(TrendsCard.Nutrients))
        assertEquals(TrendsCards(), settings.settings.value.trendsCards)
    }

    @Test
    fun closeTapped_dismisses() = runUnitTest {
        val vm = vm()
        val events = mutableListOf<TrendsSettingsEvent>()
        backgroundScope.launch { vm.eventFlow.collect { events += it } }
        vm.takeAction(TrendsSettingsAction.CloseTapped)
        assertEquals(listOf<TrendsSettingsEvent>(TrendsSettingsEvent.Dismiss), events)
        assertEquals(0, settings.updates)
    }
}
