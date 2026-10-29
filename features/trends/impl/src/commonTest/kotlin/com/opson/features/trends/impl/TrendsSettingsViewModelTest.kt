package com.opson.features.trends.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TrendsCards
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.nutrition.Metric
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

    private fun vm(
        preset: PresetId = PresetId.Full,
        healthOn: Boolean = false,
        health: FakeHealthRepository = FakeHealthRepository(),
        goalsOn: Boolean = false,
    ) =
        TrendsSettingsViewModel(settings, FakePolicySource(TrendsFixtures.policy(preset)), health, healthFlag(healthOn), goalsAndEnergyFlag(goalsOn))

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
        val blind = TrendsSettingsStateBuilder.build(Settings(), TrendsFixtures.policy(PresetId.NoNumbers), healthConnected = true)
        assertTrue(blind.toggles.none { it.card == TrendsCard.Expended })
    }

    @Test
    fun toggles_filteredByWhatTheModeCanShow() {
        fun cards(preset: PresetId, overrides: Map<Metric, Visibility> = emptyMap()) =
            TrendsSettingsStateBuilder.build(Settings(), TrendsFixtures.policy(preset, overrides)).toggles.map { it.card }
        assertEquals(
            TrendsCard.entries - TrendsCard.Expended - TrendsCard.Running,
            cards(PresetId.Full),
            "Expended waits for a connected watch, the running total for Burned",
        )
        assertEquals(listOf(TrendsCard.Calendar, TrendsCard.Recap, TrendsCard.Journal), cards(PresetId.NoNumbers), "weight is hidden, so its card never shows")
        assertEquals(listOf(TrendsCard.Calendar, TrendsCard.Recap, TrendsCard.Nutrients, TrendsCard.Weight, TrendsCard.Journal), cards(PresetId.MacrosOnly))
        assertEquals(
            listOf(TrendsCard.Calendar, TrendsCard.Recap, TrendsCard.Nutrients, TrendsCard.Weight, TrendsCard.Journal),
            cards(PresetId.MacrosWithGrams),
            "no calorie chart",
        )
        assertEquals(
            listOf(TrendsCard.Intake, TrendsCard.Calendar, TrendsCard.Recap, TrendsCard.Nutrients, TrendsCard.Weight, TrendsCard.Journal),
            cards(PresetId.Full, Presets.laterLevels),
        )
    }

    @Test
    fun runningTotal_offeredWithBurned_whereCaloriesAndEnergyOutShow_andNotAfterNumbersHaveBeenHard() = runUnitTest {
        assertTrue(vm(goalsOn = false).state.toggles.none { it.card == TrendsCard.Running })
        val vm = vm(goalsOn = true)
        assertEquals(true, vm.state.toggles.single { it.card == TrendsCard.Running }.on)
        vm.takeAction(TrendsSettingsAction.CardToggled(TrendsCard.Running))
        assertEquals(TrendsCards(runningTotal = false), settings.settings.value.trendsCards)

        val hard = Settings(onboarding = com.opson.libraries.diary.OnboardingAnswers(hard = true))
        assertTrue(TrendsSettingsStateBuilder.build(hard, TrendsFixtures.policy(), goalsAndEnergy = true).toggles.none { it.card == TrendsCard.Running })
        val energyHidden = TrendsFixtures.policy(overrides = mapOf(Metric.EnergyOut to Visibility.Hide))
        assertTrue(TrendsSettingsStateBuilder.build(Settings(), energyHidden, goalsAndEnergy = true).toggles.none { it.card == TrendsCard.Running })
    }

    @Test
    fun calendarToggled_writesItsCard() = runUnitTest {
        val vm = vm()
        vm.takeAction(TrendsSettingsAction.CardToggled(TrendsCard.Calendar))
        assertEquals(TrendsCards(calendar = false), settings.settings.value.trendsCards)
    }

    @Test
    fun journalToggled_turnsHungerAndNotesOff() = runUnitTest {
        val vm = vm()
        vm.takeAction(TrendsSettingsAction.CardToggled(TrendsCard.Journal))
        assertEquals(TrendsCards(journal = false), settings.settings.value.trendsCards)
        assertEquals(false, vm.state.toggles.single { it.card == TrendsCard.Journal }.on)
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
