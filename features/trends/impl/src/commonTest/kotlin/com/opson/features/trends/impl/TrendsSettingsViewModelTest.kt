package com.opson.features.trends.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TrendsCards
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Trends settings sheet: which switches each mode offers, and that each
 * action writes its own setting and nothing else. None of these touch
 * visibility, so none goes through the pause.
 */
class TrendsSettingsViewModelTest : CoroutineTest() {

    private val settings = FakeSettingsRepository()

    private fun vm(preset: PresetId = PresetId.Full) =
        TrendsSettingsViewModel(settings, FakePolicySource(TrendsFixtures.policy(preset)))

    @Test
    fun toggles_filteredByWhatTheModeCanShow() {
        fun cards(preset: PresetId) = TrendsSettingsStateBuilder.build(Settings(), TrendsFixtures.policy(preset)).toggles.map { it.card }
        assertEquals(TrendsCard.entries, cards(PresetId.Full))
        assertEquals(listOf(TrendsCard.Recap, TrendsCard.Weight), cards(PresetId.Blind))
        assertEquals(listOf(TrendsCard.Recap, TrendsCard.Nutrients, TrendsCard.Weight), cards(PresetId.Fuel))
        assertEquals(listOf(TrendsCard.Intake, TrendsCard.Recap, TrendsCard.Weight), cards(PresetId.Coast))
    }

    @Test
    fun inputsArrive_stateFollowsSettings() = runUnitTest {
        val vm = vm()
        settings.settings.value = Settings(trendsRange = TrendsRange.FourWeeks, weekStart = DayOfWeek.SUNDAY)
        assertEquals(TrendsRange.FourWeeks, vm.state.range)
        assertEquals(DayOfWeek.SUNDAY, vm.state.weekStart)
    }

    @Test
    fun rangePicked_writesTheRange() = runUnitTest {
        val vm = vm()
        vm.takeAction(TrendsSettingsAction.RangePicked(TrendsRange.ThreeMonths))
        assertEquals(TrendsRange.ThreeMonths, settings.settings.value.trendsRange)
        assertEquals(Settings().copy(trendsRange = TrendsRange.ThreeMonths), settings.settings.value)
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
