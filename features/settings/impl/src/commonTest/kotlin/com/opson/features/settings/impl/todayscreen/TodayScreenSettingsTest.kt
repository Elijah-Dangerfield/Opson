package com.opson.features.settings.impl.todayscreen

import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.eventsOf
import com.opson.libraries.diary.MainNumberChoice
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SummaryCardPreset
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** More › Today screen (design sync 4, 13.6): the builder per branch and each action. */
class TodayScreenSettingsTest : CoroutineTest() {

    @Test
    fun theDefaults_inAndOut_followMyGoal_bothPages_everyRowOn() {
        val state = TodayScreenSettingsStateBuilder.build(SettingsFixtures.settings())
        assertTrue(state.cardArranged)
        assertEquals(SummaryCardPreset.InOut, state.presets.single { it.selected }.preset)
        assertEquals(MainNumberChoice.Follow, state.mainNumber)
        assertEquals(FollowStyle.Range, state.follows)
        assertTrue(state.macrosPage && state.burnedBreakdown && state.nutrientsLink && state.water && state.weight && state.closeDay)
        assertFalse(state.mealsCount)
    }

    @Test
    fun rings_needASingleTarget() {
        val range = TodayScreenSettingsStateBuilder.build(SettingsFixtures.settings().copy(todayCard = SummaryCardPreset.Rings))
        assertFalse(range.presets.single { it.preset == SummaryCardPreset.Rings }.enabled)
        assertEquals(SummaryCardPreset.InOut, range.presets.single { it.selected }.preset, "Rings with a range draws In and out")

        val one = TodayScreenSettingsStateBuilder.build(SettingsFixtures.settings(targets = SettingsFixtures.exactTargets).copy(todayCard = SummaryCardPreset.Rings))
        assertTrue(one.presets.single { it.preset == SummaryCardPreset.Rings }.enabled)
        assertEquals(SummaryCardPreset.Rings, one.presets.single { it.selected }.preset)
        assertEquals(FollowStyle.Exact, one.follows)
    }

    @Test
    fun outsideFullNumbers_theCardComesFromTheMode() {
        listOf(PresetId.MacrosWithGrams, PresetId.MacrosOnly, PresetId.NoNumbers).forEach { preset ->
            assertFalse(TodayScreenSettingsStateBuilder.build(SettingsFixtures.settings(NumbersSettings(preset = preset))).cardArranged, "$preset")
        }
    }

    // --- view model ---

    @Test
    fun presetPicked_isSaved_butRingsNotWithARange() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = TodayScreenSettingsViewModel(repository)

        vm.takeAction(TodayScreenSettingsAction.PresetPicked(SummaryCardPreset.Equation))
        assertEquals(SummaryCardPreset.Equation, repository.state.value.todayCard)
        vm.takeAction(TodayScreenSettingsAction.PresetPicked(SummaryCardPreset.Rings))
        assertEquals(SummaryCardPreset.Equation, repository.state.value.todayCard)
    }

    @Test
    fun eachSwitch_writesItsSetting() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = TodayScreenSettingsViewModel(repository)
        TodaySwitch.entries.forEach { vm.takeAction(TodayScreenSettingsAction.Toggled(it, on = it == TodaySwitch.MealsCount)) }

        val s = repository.state.value
        assertEquals(listOf(false, true, false, false, false, false), listOf(s.todayBurnedBreakdown, s.todayMealsCount, s.todayNutrientsLink, s.todayWater, s.todayWeight, s.todayCloseDay))
    }

    @Test
    fun mainNumberAndCardPages_openTheirSheets_andBackCloses() = runUnitTest {
        val vm = TodayScreenSettingsViewModel(FakeSettingsRepository())
        val events = eventsOf(vm)
        vm.takeAction(TodayScreenSettingsAction.MainNumberTapped)
        vm.takeAction(TodayScreenSettingsAction.CardPagesTapped)
        vm.takeAction(TodayScreenSettingsAction.BackTapped)
        assertEquals(
            listOf(
                TodayScreenSettingsEvent.OpenChoice(TodayScreenChoice.MainNumber),
                TodayScreenSettingsEvent.OpenChoice(TodayScreenChoice.CardPages),
                TodayScreenSettingsEvent.Close,
            ),
            events,
        )
    }

    @Test
    fun aMainNumberPicked_isWhatTheToggleResetsTo() = runUnitTest {
        val repository = FakeSettingsRepository(Settings(todayMainNumberByStyle = mapOf("range" to "balance")))
        val vm = TodayScreenChoiceViewModel(TodayScreenChoice.MainNumber, repository)
        val events = eventsOf(vm)

        vm.takeAction(TodayScreenChoiceAction.MainNumberPicked(MainNumberChoice.Eaten))

        assertEquals(MainNumberChoice.Eaten, repository.state.value.todayMainNumber)
        assertEquals(emptyMap(), repository.state.value.todayMainNumberByStyle)
        assertEquals(listOf<TodayScreenChoiceEvent>(TodayScreenChoiceEvent.Dismiss), events)
    }

    @Test
    fun cardPagesPicked_turnsTheMacrosPageOnOrOff() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = TodayScreenChoiceViewModel(TodayScreenChoice.CardPages, repository)
        vm.takeAction(TodayScreenChoiceAction.PagesPicked(macros = false))
        assertFalse(repository.state.value.todayMacrosPage)
        assertFalse(vm.state.macrosPage)
    }
}
