package com.opson.features.settings.impl.targets

import com.opson.features.settings.impl.FakeBurnedRepository
import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.Toast
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.goalsAndEnergyFlag
import com.opson.features.settings.impl.integrations.FakeCalendar
import com.opson.features.settings.impl.integrations.FakeHealthRepository
import com.opson.features.settings.impl.integrations.healthFlag
import com.opson.libraries.diary.MacroMode
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.MacroPreset
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.macroFit
import com.opson.libraries.nutrition.macroPercent
import com.opson.libraries.nutrition.withPreset
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_targets_macros_goal_floor_toast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The macro editor: grams or percents of the calorie goal, presets, typed amounts, and the two
 * ways to make macros and calories agree. The math is `MacroGoalsTest` in `:libraries:nutrition`.
 */
class TargetsMacroEditorTest : CoroutineTest() {

    private fun settings(targets: Targets = SettingsFixtures.rangeTargets, mode: MacroMode = MacroMode.Ratios, preset: PresetId = PresetId.Full) =
        SettingsFixtures.settings(NumbersSettings(preset), targets).copy(macroMode = mode)

    private fun editor(settings: Settings, drafts: TargetsDrafts = TargetsDrafts()): MacroEditor? = TargetsStateBuilder.build(
        settings,
        SettingsFixtures.policy(settings.numbers.preset),
        editingMacros = true,
        goalsAndEnergy = true,
        drafts = drafts,
    ).macroEditor

    // --- builder ---

    @Test
    fun closed_hasNoEditor() {
        assertNull(TargetsStateBuilder.build(settings(), SettingsFixtures.policy(PresetId.Full)).macroEditor)
    }

    @Test
    fun inPercents_eachRowIsItsShareOfTheGoal_withItsGramsBeside() {
        val editor = assertNotNull(editor(settings()))

        assertTrue(editor.inPercent)
        // 120 g, 250 g and 70 g of a 2,100 goal.
        assertEquals(listOf("23", "48", "30"), editor.rows.map { it.text })
        assertEquals(Display.Shown("120 g"), editor.rows.first().grams)
        assertNull(editor.fit, "2,110 kcal is 2,100 give or take rounding")
    }

    @Test
    fun thePresets_markTheOneThatMatches_andCustomOnlyWhenNoneDoes() {
        val custom = assertNotNull(editor(settings())).presets
        assertEquals(listOf<MacroPreset?>(MacroPreset.Balanced, MacroPreset.HigherProtein, MacroPreset.LowerCarb, null), custom.map { it.preset })
        assertEquals(listOf(false, false, false, true), custom.map { it.selected })

        val balanced = assertNotNull(editor(settings(SettingsFixtures.rangeTargets.withPreset(MacroPreset.Balanced, 2100)))).presets
        assertEquals(listOf(true, false, false), balanced.map { it.selected })
    }

    @Test
    fun inGrams_macrosThatAddUpToMore_sayByHowMuch_andOfferBothFixes() {
        val editor = assertNotNull(editor(settings(SettingsFixtures.gramsOverTargets, MacroMode.Grams)))

        assertFalse(editor.inPercent)
        assertEquals(listOf("150", "260", "80"), editor.rows.map { it.text })
        assertEquals(Display.Shown("29%"), editor.rows.first().percent)
        assertEquals(
            MacroFitLine(
                macroKcal = Display.Shown("2,360 kcal"),
                goal = Display.Shown("2,100 kcal"),
                gap = Display.Shown("260"),
                over = true,
                goalKind = MacroGoalKind.RangeMiddle,
                canUseAsGoal = true,
            ),
            editor.fit,
        )
    }

    @Test
    fun underADeficit_theMacrosAreMeasuredAgainstTodaysGoal_andCanNotSetIt() {
        val fit = assertNotNull(editor(settings(SettingsFixtures.deficitTargets.copy(proteinG = 150, carbsG = 260, fatG = 80), MacroMode.Grams))).fit
        assertEquals(MacroGoalKind.Today, fit?.goalKind)
        assertEquals(false, fit?.canUseAsGoal)
    }

    @Test
    fun withCaloriesHidden_theEditorIsGramsAlone() {
        val state = TargetsStateBuilder.build(settings(preset = PresetId.MacrosWithGrams), SettingsFixtures.policy(PresetId.MacrosWithGrams), editingMacros = true)
        val editor = assertNotNull(state.macroEditor)

        assertFalse(state.visible)
        assertFalse(editor.percents)
        assertFalse(editor.inPercent, "even with percents saved as the mode")
        assertEquals(emptyList(), editor.presets)
        assertNull(editor.fit)
        assertEquals(listOf("120", "250", "70"), editor.rows.map { it.text })
        assertTrue(editor.rows.all { it.percent == Display.Hidden })
    }

    @Test
    fun withNoMacroShowingAsAnAmount_thereIsNothingToEdit() {
        val state = TargetsStateBuilder.build(settings(preset = PresetId.MacrosOnly), SettingsFixtures.policy(PresetId.MacrosOnly), editingMacros = true)
        assertNull(state.macroEditor)
    }

    @Test
    fun aDraft_staysInItsField() {
        val editor = assertNotNull(editor(settings(mode = MacroMode.Grams), TargetsDrafts(macros = mapOf(Metric.Carbs to ""))))
        assertEquals(listOf("120", "", "70"), editor.rows.map { it.text })
    }

    // --- view model ---

    private fun viewModel(repository: FakeSettingsRepository, preset: PresetId = PresetId.Full) = TargetsViewModel(
        repository,
        FakePolicySource(SettingsFixtures.policy(preset)),
        FakeHealthRepository(),
        healthFlag(false),
        goalsAndEnergyFlag(on = true),
        FakeBurnedRepository(),
        FakeCalendar(),
    ).also { it.takeAction(TargetsAction.MacroCardTapped) }

    private fun FakeSettingsRepository.targets(): Targets = state.value.targets

    @Test
    fun aPreset_setsTheGramsForTheGoal() = runUnitTest {
        val repository = FakeSettingsRepository(settings())
        viewModel(repository).takeAction(TargetsAction.MacroPresetPicked(MacroPreset.HigherProtein))

        assertEquals(MacroPreset.HigherProtein.proteinPercent, repository.targets().macroPercent(Metric.Protein, 2100))
        assertTrue(repository.targets().macroFit(2100).fits)
    }

    @Test
    fun aStep_isFivePercentInPercents_andFiveGramsInGrams() = runUnitTest {
        val percents = FakeSettingsRepository(settings())
        viewModel(percents).takeAction(TargetsAction.MacroStepped(Metric.Protein, up = true))
        assertEquals(25, percents.targets().macroPercent(Metric.Protein, 2100))

        val grams = FakeSettingsRepository(settings(mode = MacroMode.Grams))
        viewModel(grams).takeAction(TargetsAction.MacroStepped(Metric.Protein, up = true))
        assertEquals(125, grams.targets().proteinG)
    }

    @Test
    fun typing_writesEachSensibleValue_andKeepsWhatIsTyped() = runUnitTest {
        val repository = FakeSettingsRepository(settings(mode = MacroMode.Grams))
        val vm = viewModel(repository)

        vm.takeAction(TargetsAction.MacroTyped(Metric.Fat, ""))
        assertEquals(70, repository.targets().fatG, "an empty field writes nothing")
        assertEquals("", vm.state.macroEditor?.rows?.get(2)?.text)

        vm.takeAction(TargetsAction.MacroTyped(Metric.Fat, "6g5"))
        assertEquals(65, repository.targets().fatG)
        assertEquals("65", vm.state.macroEditor?.rows?.get(2)?.text)

        vm.takeAction(TargetsAction.MacroStepped(Metric.Fat, up = true))
        assertEquals("70", vm.state.macroEditor?.rows?.get(2)?.text, "a step shows what is saved again")
    }

    @Test
    fun typingAPercent_over100_writesNothing() = runUnitTest {
        val repository = FakeSettingsRepository(settings())
        val vm = viewModel(repository)

        vm.takeAction(TargetsAction.MacroTyped(Metric.Carbs, "101"))
        assertEquals(250, repository.targets().carbsG)
        vm.takeAction(TargetsAction.MacroTyped(Metric.Carbs, "40"))
        assertEquals(40, repository.targets().macroPercent(Metric.Carbs, 2100))
    }

    @Test
    fun fittingTheMacros_keepsTheGoal_andMakesThemAddUp() = runUnitTest {
        val repository = FakeSettingsRepository(settings(SettingsFixtures.gramsOverTargets, MacroMode.Grams))
        val vm = viewModel(repository)

        vm.takeAction(TargetsAction.MacrosFitTapped)

        assertEquals(2100, repository.targets().energyKcal)
        assertTrue(repository.targets().macroFit(2100).fits)
        assertNull(vm.state.macroEditor?.fit)
    }

    @Test
    fun makingTheMacrosTheGoal_movesTheRangeWithIt() = runUnitTest {
        val repository = FakeSettingsRepository(settings(SettingsFixtures.gramsOverTargets, MacroMode.Grams))
        val vm = viewModel(repository)

        vm.takeAction(TargetsAction.MacrosAsGoalTapped)

        assertEquals(2360, repository.targets().energyKcal)
        assertEquals(2160 to 2560, repository.targets().minKcal to repository.targets().maxKcal)
        assertNull(vm.state.macroEditor?.fit)
    }

    @Test
    fun makingTheMacrosTheGoal_underWhereItStarted_writesNothing_andSaysWhy() = runUnitTest {
        val under = SettingsFixtures.rangeTargets.copy(carbsG = 150)
        val repository = FakeSettingsRepository(settings(under, MacroMode.Grams))
        val vm = viewModel(repository)
        val events = eventsOf(vm)

        vm.takeAction(TargetsAction.MacrosAsGoalTapped)

        assertEquals(under, repository.targets())
        assertEquals(listOf(TargetsEvent.ShowToast(Toast(Res.string.settings_targets_macros_goal_floor_toast))), events)
    }

    @Test
    fun withCaloriesHidden_aStepIsGrams_whateverModeIsSaved() = runUnitTest {
        val repository = FakeSettingsRepository(settings(preset = PresetId.MacrosWithGrams))
        viewModel(repository, PresetId.MacrosWithGrams).takeAction(TargetsAction.MacroStepped(Metric.Carbs, up = false))

        assertEquals(245, repository.targets().carbsG)
    }

    // --- one number, typed ---

    @Test
    fun aTypedGoal_writesOnceItIsAtOrAboveWhereItStarted() = runUnitTest {
        val repository = FakeSettingsRepository(settings(SettingsFixtures.exactTargets))
        val vm = viewModel(repository)

        vm.takeAction(TargetsAction.NumberTyped("2"))
        assertEquals(2200, repository.targets().energyKcal)
        assertEquals("2", vm.state.number?.text)
        assertFalse(vm.state.number?.tooLow == true, "still being typed")

        vm.takeAction(TargetsAction.NumberTyped("1900"))
        assertEquals(2200, repository.targets().energyKcal, "under 2,050, where this goal started")
        assertEquals(true, vm.state.number?.tooLow)

        vm.takeAction(TargetsAction.NumberTyped("2300"))
        assertEquals(2300, repository.targets().energyKcal)
        assertEquals(2100 to 2500, repository.targets().minKcal to repository.targets().maxKcal)
    }
}
