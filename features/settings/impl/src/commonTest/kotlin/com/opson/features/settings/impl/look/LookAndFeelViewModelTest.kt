package com.opson.features.settings.impl.look

import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.libraries.diary.TextSize
import com.opson.libraries.diary.ThemeMode
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Display's Appearance half: each action writes its one field and the page reflects it. That the
 * app root re-themes from these is `UserThemeTest` in `:apps:compose`.
 */
class LookAndFeelViewModelTest : CoroutineTest() {

    @Test
    fun eachPickWritesItsField() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = LookAndFeelViewModel(repository)

        vm.takeAction(LookAndFeelAction.ThemePicked(ThemeMode.Dark))
        vm.takeAction(LookAndFeelAction.TextSizePicked(TextSize.Large))
        vm.takeAction(LookAndFeelAction.WeightUnitPicked(WeightUnit.Lb))

        val expected = LookAndFeelState(theme = ThemeMode.Dark, textSize = TextSize.Large, weightUnit = WeightUnit.Lb)
        assertEquals(expected, LookAndFeelStateBuilder.build(repository.state.value))
        assertEquals(expected, vm.state)
    }

    @Test
    fun aChangeMadeElsewhereShowsHere() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = LookAndFeelViewModel(repository)

        repository.state.value = repository.state.value.copy(weightUnit = WeightUnit.Lb)

        assertEquals(WeightUnit.Lb, vm.state.weightUnit)
    }
}
