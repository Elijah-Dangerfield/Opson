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
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RangeEnd
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import kotlinx.datetime.DayOfWeek
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_targets_deficit_cap_toast
import opson.libraries.resources.generated.resources.settings_targets_floor_toast
import opson.libraries.resources.generated.resources.settings_targets_gap_toast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Targets: amounts only when calories are configured Show, each end stepping on its own under
 * the floor, the deficit and by-day styles, and ratio versus gram macros. The floor, deficit
 * and group arithmetic themselves are `TargetsTest` and `TargetStyleTest` in `:libraries:nutrition`.
 */
class TargetsTest : CoroutineTest() {

    private fun build(
        numbers: NumbersSettings = NumbersSettings(PresetId.Full),
        targets: Targets = SettingsFixtures.rangeTargets,
        burned: Burned? = null,
        editingGroup: Int? = null,
    ) = TargetsStateBuilder.build(
        SettingsFixtures.settings(numbers, targets),
        SettingsFixtures.policy(numbers),
        burned = burned,
        editingGroup = editingGroup,
    )

    // --- builder ---

    @Test
    fun aRangeShowsEachEndWithItsSteps() {
        val range = assertNotNull(build().range)

        assertEquals(EndStepper(Display.Shown("1,900"), down = StepState.Locked, up = StepState.Open), range.low)
        assertEquals(EndStepper(Display.Shown("2,300"), down = StepState.Open, up = StepState.Open), range.high)
        assertTrue(range.lowLocked)
        assertTrue(range.band.start < range.band.endInclusive)
    }

    @Test
    fun theMacroSplitShowsGramsAndAShareForEachVisibleMacro() {
        val state = build()

        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat), state.macros.map { it.metric })
        val protein = state.macros.first()
        assertEquals(Display.Shown("120 g"), protein.grams)
        assertEquals(Display.Shown("23%"), protein.share)
        assertEquals(480f, protein.weight)
    }

    @Test
    fun softDelayedAndHiddenCaloriesGetTheBackgroundCardWithNoAmounts() {
        listOf(PresetId.NutrientsFirst, PresetId.Later, PresetId.Later, PresetId.NoNumbers, PresetId.NutrientsFirst).forEach { preset ->
            listOf(SettingsFixtures.rangeTargets, SettingsFixtures.deficitTargets, SettingsFixtures.byDayTargets).forEach { targets ->
                val state = build(NumbersSettings(preset), targets, burned = SettingsFixtures.burned, editingGroup = 1)
                assertEquals(TargetsState(visible = false), state, "in $preset")
            }
        }
    }

    @Test
    fun aMacroThatIsNotShowDropsOut() {
        val numbers = NumbersSettings(
            PresetId.Full,
            overrides = mapOf(Metric.Fat to Visibility.Hide, Metric.Carbs to Visibility.Soften),
        )

        assertEquals(listOf(Metric.Protein), build(numbers).macros.map { it.metric })
    }

    @Test
    fun aDeficitShowsTheAmount_andTodaysTargetOnlyOnceBurnedIsKnown() {
        val known = assertNotNull(build(targets = SettingsFixtures.deficitTargets, burned = SettingsFixtures.burned).deficit)
        assertEquals(Display.Shown("300"), known.amount)
        // 2,180 burned less 300.
        assertEquals(Display.Shown("1,880 kcal"), known.target)
        assertEquals(StepState.Open, known.down)
        assertEquals(StepState.Locked, known.up, "the deficit can't grow past where it started")

        val unknown = assertNotNull(build(targets = SettingsFixtures.deficitTargets).deficit)
        assertNull(unknown.target)
    }

    @Test
    fun aDeficitPickedWithNoBurnIsLockedAtZero_notGreyedOut() {
        val deficit = assertNotNull(build(targets = SettingsFixtures.newDeficitTargets).deficit)

        assertEquals(Display.Shown("0"), deficit.amount)
        assertEquals(StepState.Closed, deficit.down)
        assertEquals(StepState.Locked, deficit.up)
    }

    @Test
    fun aDeficitUnderItsCapStepsBothWays() {
        val under = SettingsFixtures.deficitTargets.copy(style = TargetStyle.Deficit(kcal = 150, startingKcal = 300))
        val deficit = assertNotNull(build(targets = under).deficit)

        assertEquals(StepState.Open, deficit.down)
        assertEquals(StepState.Open, deficit.up)
    }

    @Test
    fun aHiddenWatchEnergyHidesRestingEnergy_butNotTodaysTarget() {
        val numbers = NumbersSettings(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Hide))
        val settings = SettingsFixtures.settings(numbers, SettingsFixtures.deficitTargets)
        val state = TargetsStateBuilder.build(settings, SettingsFixtures.policy(numbers), burned = SettingsFixtures.burned, goalsAndEnergy = true)

        assertEquals(Display.Hidden, state.burned?.resting)
        assertEquals(Display.Shown("1,880 kcal"), state.deficit?.target)
    }

    @Test
    fun byDayShowsEachGroup_andTheWeek() {
        val state = build(targets = SettingsFixtures.byDayTargets)

        assertEquals(listOf(GroupLabel.OtherDays, GroupLabel.Numbered(2)), state.groups.map { it.label })
        assertEquals(Display.Shown("2,300 to 2,700 kcal"), state.groups[1].summary)
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
            state.groups[1].days.filter { it.inGroup }.map { it.day },
        )
        assertEquals(listOf(1f, 1f, 1f, 0f, 0f, 0f, 0f), state.week.map { it.position })
        assertTrue(state.canAddGroup)
        assertNull(state.groups[1].low, "steppers only while editing")
    }

    @Test
    fun editingAGroupOpensItsSteppers() {
        val group = build(targets = SettingsFixtures.byDayTargets, editingGroup = 1).groups[1]

        assertEquals(Display.Shown("2,300"), group.low?.value)
        assertTrue(group.removable)
    }

    @Test
    fun theWeekFollowsTheUsersWeekStart() {
        val settings = SettingsFixtures.settings(targets = SettingsFixtures.byDayTargets).copy(weekStart = DayOfWeek.SUNDAY)
        val state = TargetsStateBuilder.build(settings, SettingsFixtures.policy(PresetId.Full))

        assertEquals(DayOfWeek.SUNDAY, state.week.first().day)
    }

    // --- view model ---

    private fun viewModel(repository: FakeSettingsRepository, preset: PresetId = PresetId.Full) =
        TargetsViewModel(
        repository,
        FakePolicySource(SettingsFixtures.policy(preset)),
        FakeHealthRepository(),
        healthFlag(false),
        goalsAndEnergyFlag(false),
        FakeBurnedRepository(),
        FakeCalendar(),
    )

    private fun FakeSettingsRepository.targets(): Targets = state.value.targets

    @Test
    fun eachEndStepsOnItsOwn() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository)

        vm.takeAction(TargetsAction.StepTapped(RangeEnd.High, up = true))
        assertEquals(1900 to 2350, repository.targets().minKcal to repository.targets().maxKcal)
        vm.takeAction(TargetsAction.StepTapped(RangeEnd.Low, up = true))
        assertEquals(1950 to 2350, repository.targets().minKcal to repository.targets().maxKcal)
    }

    @Test
    fun aStepPastTheFloorWritesNothingAndSaysWhy() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository)
        val events = eventsOf(vm)
        val writes = repository.writes

        vm.takeAction(TargetsAction.StepTapped(RangeEnd.Low, up = false))

        assertEquals(writes, repository.writes)
        assertEquals(listOf<TargetsEvent>(TargetsEvent.ShowToast(Toast(Res.string.settings_targets_floor_toast))), events)
    }

    @Test
    fun endsThatWouldTouchSayWhy() = runUnitTest {
        val narrow = SettingsFixtures.rangeTargets.copy(minKcal = 2250, startingMinKcal = 1900)
        val vm = viewModel(FakeSettingsRepository(SettingsFixtures.settings(targets = narrow)))
        val events = eventsOf(vm)

        vm.takeAction(TargetsAction.StepTapped(RangeEnd.Low, up = true))

        assertEquals(listOf<TargetsEvent>(TargetsEvent.ShowToast(Toast(Res.string.settings_targets_gap_toast))), events)
    }

    @Test
    fun ratioModeScalesTheGrams_gramModeLeavesThem() = runUnitTest {
        val ratio = FakeSettingsRepository()
        viewModel(ratio).takeAction(TargetsAction.StepTapped(RangeEnd.High, up = true))
        assertEquals(121, ratio.targets().proteinG)

        val grams = FakeSettingsRepository(SettingsFixtures.settings().copy(macroMode = MacroMode.Grams))
        viewModel(grams).takeAction(TargetsAction.StepTapped(RangeEnd.High, up = true))
        assertEquals(120, grams.targets().proteinG)
    }

    private fun FakeSettingsRepository.deficit(): Int = (targets().style as TargetStyle.Deficit).kcal

    private fun deficitRepository(kcal: Int, startingKcal: Int) = FakeSettingsRepository(
        SettingsFixtures.settings(targets = SettingsFixtures.deficitTargets.copy(style = TargetStyle.Deficit(kcal, startingKcal))),
    )

    @Test
    fun deficitStepDown_takesFiftyOff() = runUnitTest {
        val repository = deficitRepository(kcal = 300, startingKcal = 300)
        val vm = viewModel(repository)
        val events = eventsOf(vm)

        vm.takeAction(TargetsAction.DeficitStepped(up = false))

        assertEquals(250, repository.deficit())
        assertEquals(emptyList(), events)
    }

    @Test
    fun deficitStepUp_addsFiftyUnderTheCap() = runUnitTest {
        val repository = deficitRepository(kcal = 200, startingKcal = 300)
        val vm = viewModel(repository)
        val events = eventsOf(vm)

        vm.takeAction(TargetsAction.DeficitStepped(up = true))

        assertEquals(250, repository.deficit())
        assertEquals(emptyList(), events)
        assertEquals(StepState.Open, vm.state.deficit?.up)
    }

    @Test
    fun deficitStepUp_atTheCapWritesNothingAndSaysWhy() = runUnitTest {
        val repository = deficitRepository(kcal = 300, startingKcal = 300)
        val vm = viewModel(repository)
        val events = eventsOf(vm)
        val writes = repository.writes

        vm.takeAction(TargetsAction.DeficitStepped(up = true))

        assertEquals(300, repository.deficit())
        assertEquals(writes, repository.writes)
        assertEquals(listOf<TargetsEvent>(TargetsEvent.ShowToast(Toast(Res.string.settings_targets_deficit_cap_toast))), events)
    }

    @Test
    fun deficitStepDown_atZeroWritesNothingAndSaysNothing() = runUnitTest {
        val repository = deficitRepository(kcal = 0, startingKcal = 300)
        val vm = viewModel(repository)
        val events = eventsOf(vm)
        val writes = repository.writes

        vm.takeAction(TargetsAction.DeficitStepped(up = false))

        assertEquals(0, repository.deficit())
        assertEquals(writes, repository.writes)
        assertEquals(emptyList(), events)
    }

    @Test
    fun deficitStepsDownAndBackUpToWhereItStarted() = runUnitTest {
        val repository = deficitRepository(kcal = 300, startingKcal = 300)
        val vm = viewModel(repository)

        vm.takeAction(TargetsAction.DeficitStepped(up = false))
        vm.takeAction(TargetsAction.DeficitStepped(up = false))
        assertEquals(200, repository.deficit())
        assertEquals(StepState.Open, vm.state.deficit?.up)

        vm.takeAction(TargetsAction.DeficitStepped(up = true))
        vm.takeAction(TargetsAction.DeficitStepped(up = true))
        assertEquals(300, repository.deficit())
        assertEquals(StepState.Locked, vm.state.deficit?.up)
    }

    @Test
    fun daysMoveBetweenGroups_andGroupsComeAndGo() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings(targets = SettingsFixtures.byDayTargets))
        val vm = viewModel(repository)
        fun groups() = (repository.targets().style as TargetStyle.ByDay).groups

        vm.takeAction(TargetsAction.DayTapped(group = 1, day = DayOfWeek.FRIDAY))
        assertTrue(DayOfWeek.FRIDAY in groups()[1].days && DayOfWeek.FRIDAY !in groups()[0].days)

        vm.takeAction(TargetsAction.AddGroupTapped)
        assertEquals(3, groups().size)
        assertEquals(2, vm.state.editingGroup, "a new group opens for editing")

        vm.takeAction(TargetsAction.StepTapped(RangeEnd.High, up = true, group = 2))
        assertEquals(2350, groups()[2].maxKcal)

        vm.takeAction(TargetsAction.RemoveGroupTapped(2))
        assertEquals(2, groups().size)
        assertNull(vm.state.editingGroup)
    }

    @Test
    fun editingAGroupTogglesItsSteppers() = runUnitTest {
        val vm = viewModel(FakeSettingsRepository(SettingsFixtures.settings(targets = SettingsFixtures.byDayTargets)))

        vm.takeAction(TargetsAction.EditGroupTapped(1))
        assertNotNull(vm.state.groups[1].low)
        vm.takeAction(TargetsAction.EditGroupTapped(1))
        assertNull(vm.state.groups[1].low)
    }

    @Test
    fun theMacroCardOpensTheModeEditor_andAModeIsSaved() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository)

        vm.takeAction(TargetsAction.MacroCardTapped)
        assertTrue(vm.state.editingMacros)
        vm.takeAction(TargetsAction.MacroModePicked(MacroMode.Grams))

        assertEquals(MacroMode.Grams, repository.state.value.macroMode)
        assertEquals(MacroMode.Grams, vm.state.macroMode)
        assertTrue(vm.state.editingMacros, "the editor stays open while the user picks")
    }

    @Test
    fun theStyleCardModesLinkAndBackNavigate() = runUnitTest {
        val vm = viewModel(FakeSettingsRepository())
        val events = eventsOf(vm)

        vm.takeAction(TargetsAction.RestingEnergyTapped)
        vm.takeAction(TargetsAction.ActivityTapped)
        vm.takeAction(TargetsAction.ModesTapped)
        vm.takeAction(TargetsAction.BackTapped)

        assertEquals(
            listOf(TargetsEvent.OpenRestingEnergy, TargetsEvent.OpenIntegrations, TargetsEvent.OpenModes, TargetsEvent.Close),
            events,
        )
    }

    // --- goal style list ---

    @Test
    fun aSetDeficitIsOfferedOnlyInFullNumbersAndSprint() {
        PresetId.entries.forEach { preset ->
            val options = TargetsStateBuilder.offered(StyleKind.Range, preset, goalsAndEnergy = false)
            assertEquals(
                preset == PresetId.Full || preset == PresetId.Full,
                StyleKind.Deficit in options,
                "in $preset",
            )
        }
    }

    @Test
    fun theListMarksTheSavedStyle_andTheRangeRowSaysItsRange() {
        val styles = build(targets = SettingsFixtures.byDayTargets).styles

        assertEquals(listOf(StyleKind.Range, StyleKind.Deficit, StyleKind.ByDay), styles.map { it.kind })
        assertEquals(StyleKind.ByDay, styles.single { it.selected }.kind)
        assertEquals(Display.Shown("1,900 to 2,300 kcal"), styles.first().range)
    }

    @Test
    fun pickingAStyle_writesItAtOnce() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository)

        vm.takeAction(TargetsAction.StylePicked(StyleKind.ByDay))

        assertTrue(repository.targets().style is TargetStyle.ByDay)
        assertEquals(StyleKind.ByDay, vm.state.style)
    }

    @Test
    fun aDeficitCannotBePickedWhereItIsNotOffered() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings(NumbersSettings(PresetId.NutrientsFirst)))
        val vm = viewModel(repository, PresetId.NutrientsFirst)
        val writes = repository.writes

        vm.takeAction(TargetsAction.StylePicked(StyleKind.Deficit))

        assertEquals(TargetStyle.Range, repository.targets().style)
        assertEquals(writes, repository.writes)
    }

    @Test
    fun pickingTheSavedStyle_writesNothing() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings(targets = SettingsFixtures.byDayTargets))
        val vm = viewModel(repository)
        val writes = repository.writes

        vm.takeAction(TargetsAction.StylePicked(StyleKind.ByDay))

        assertEquals(writes, repository.writes)
        assertEquals(SettingsFixtures.byDayTargets, repository.targets())
    }
}
