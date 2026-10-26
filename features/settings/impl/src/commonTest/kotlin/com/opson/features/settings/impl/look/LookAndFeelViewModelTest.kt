package com.opson.features.settings.impl.look

import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.eventsOf
import com.opson.libraries.diary.PaletteChoice
import com.opson.libraries.diary.TextSize
import com.opson.libraries.diary.ThemeMode
import com.opson.libraries.diary.Tone
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Look and feel: each action writes its one field and the page reflects it. That the app root
 * re-themes from these is `UserThemeTest` in `:apps:compose`.
 */
class LookAndFeelViewModelTest : CoroutineTest() {

    @Test
    fun eachPickWritesItsField() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = LookAndFeelViewModel(repository)

        vm.takeAction(LookAndFeelAction.PalettePicked(PaletteChoice.Clay))
        vm.takeAction(LookAndFeelAction.ThemePicked(ThemeMode.Dark))
        vm.takeAction(LookAndFeelAction.TextSizePicked(TextSize.Large))
        vm.takeAction(LookAndFeelAction.WeightUnitPicked(WeightUnit.Lb))
        vm.takeAction(LookAndFeelAction.TonePicked(Tone.Warm))
        vm.takeAction(LookAndFeelAction.HungerQuestionsToggled)

        val expected = LookAndFeelState(
            palette = PaletteChoice.Clay,
            theme = ThemeMode.Dark,
            textSize = TextSize.Large,
            weightUnit = WeightUnit.Lb,
            tone = Tone.Warm,
            askTags = false,
        )
        assertEquals(expected, LookAndFeelStateBuilder.build(repository.state.value))
        assertEquals(expected, vm.state)
    }

    @Test
    fun theHungerQuestionsSwitchFlipsBack() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = LookAndFeelViewModel(repository)

        vm.takeAction(LookAndFeelAction.HungerQuestionsToggled)
        assertFalse(repository.state.value.askTags)
        vm.takeAction(LookAndFeelAction.HungerQuestionsToggled)

        assertEquals(true, repository.state.value.askTags)
    }

    @Test
    fun backCloses() = runUnitTest {
        val vm = LookAndFeelViewModel(FakeSettingsRepository())
        val events = eventsOf(vm)

        vm.takeAction(LookAndFeelAction.BackTapped)

        assertEquals(listOf<LookAndFeelEvent>(LookAndFeelEvent.Close), events)
    }
}
