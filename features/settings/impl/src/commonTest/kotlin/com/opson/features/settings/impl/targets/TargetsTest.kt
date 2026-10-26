package com.opson.features.settings.impl.targets

import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.Toast
import com.opson.features.settings.impl.eventsOf
import com.opson.libraries.diary.MacroMode
import com.opson.libraries.flowroutines.testing.CoroutineTest
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
        burn: Int? = null,
        editingGroup: Int? = null,
    ) = TargetsStateBuilder.build(
        SettingsFixtures.settings(numbers, targets),
        SettingsFixtures.policy(numbers),
        burnKcal = burn,
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
        listOf(PresetId.Fuel, PresetId.Delayed, PresetId.Coast, PresetId.Blind, PresetId.Nutrient).forEach { preset ->
            listOf(SettingsFixtures.rangeTargets, SettingsFixtures.deficitTargets, SettingsFixtures.byDayTargets).forEach { targets ->
                val state = build(NumbersSettings(preset), targets, burn = SettingsFixtures.BURN_KCAL, editingGroup = 1)
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
    fun aDeficitShowsTheAmount_andTheGoalOnlyOnceTheBurnIsKnown() {
        val known = assertNotNull(build(targets = SettingsFixtures.deficitTargets, burn = SettingsFixtures.BURN_KCAL).deficit)
        assertEquals(Display.Shown("300"), known.amount)
        assertEquals(Display.Shown("2,172 kcal"), known.burn)
        assertEquals(Display.Shown("1,870 kcal"), known.goal)
        assertTrue(known.canLower)
        assertFalse(known.canRaise, "the deficit can't grow past where it started")

        val unknown = assertNotNull(build(targets = SettingsFixtures.deficitTargets).deficit)
        assertNull(unknown.burn)
        assertNull(unknown.goal)
    }

    @Test
    fun aHiddenWatchEnergyHidesTheBurnButNotTheGoal() {
        val numbers = NumbersSettings(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Hide))
        val deficit = assertNotNull(build(numbers, SettingsFixtures.deficitTargets, burn = SettingsFixtures.BURN_KCAL).deficit)

        assertEquals(Display.Hidden, deficit.burn)
        assertEquals(Display.Shown("1,870 kcal"), deficit.goal)
    }

    @Test
    fun byDayShowsEachGroup_andTheWeek() {
        val state = build(targets = SettingsFixtures.byDayTargets)

        assertEquals(listOf(GroupLabel.OtherDays, GroupLabel.Numbered(2)), state.groups.map { it.label })
        assertEquals(Display.Shown("2,300 to 2,700 kcal"), state.groups[1].range)
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
        TargetsViewModel(repository, FakePolicySource(SettingsFixtures.policy(preset)))

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

    @Test
    fun theDeficitStopsWhereItStarted() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings(targets = SettingsFixtures.deficitTargets))
        val vm = viewModel(repository)
        val events = eventsOf(vm)

        vm.takeAction(TargetsAction.DeficitStepped(up = true))
        assertEquals(300, (repository.targets().style as TargetStyle.Deficit).kcal)
        assertEquals(listOf<TargetsEvent>(TargetsEvent.ShowToast(Toast(Res.string.settings_targets_deficit_cap_toast))), events)

        vm.takeAction(TargetsAction.DeficitStepped(up = false))
        assertEquals(250, (repository.targets().style as TargetStyle.Deficit).kcal)
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

        vm.takeAction(TargetsAction.GoalStyleTapped)
        vm.takeAction(TargetsAction.ModesTapped)
        vm.takeAction(TargetsAction.BackTapped)

        assertEquals(listOf(TargetsEvent.OpenGoalStyle, TargetsEvent.OpenModes, TargetsEvent.Close), events)
    }

    // --- goal style sheet ---

    @Test
    fun aSetDeficitIsOfferedOnlyInFullNumbersAndSprint() {
        PresetId.entries.forEach { preset ->
            val options = GoalStyleStateBuilder.build(SettingsFixtures.settings(NumbersSettings(preset))).options
            assertEquals(
                preset == PresetId.Full || preset == PresetId.Sprint,
                StyleKind.Deficit in options,
                "in $preset",
            )
        }
    }

    @Test
    fun pickingAStyleWaitsForDone() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = GoalStyleViewModel(repository)
        val events = eventsOf(vm)

        vm.takeAction(GoalStyleAction.StylePicked(StyleKind.ByDay))
        assertEquals(TargetStyle.Range, repository.targets().style)

        vm.takeAction(GoalStyleAction.DoneTapped)
        assertTrue(repository.targets().style is TargetStyle.ByDay)
        assertEquals(listOf<GoalStyleEvent>(GoalStyleEvent.Dismiss), events)
    }

    @Test
    fun aDeficitCannotBePickedWhereItIsNotOffered() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings(NumbersSettings(PresetId.Fuel)))
        val vm = GoalStyleViewModel(repository)

        vm.takeAction(GoalStyleAction.StylePicked(StyleKind.Deficit))
        vm.takeAction(GoalStyleAction.DoneTapped)

        assertEquals(TargetStyle.Range, repository.targets().style)
    }

    @Test
    fun doneWithNoChangeWritesNothing() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings(targets = SettingsFixtures.byDayTargets))
        val vm = GoalStyleViewModel(repository)
        val writes = repository.writes

        vm.takeAction(GoalStyleAction.DoneTapped)

        assertEquals(writes, repository.writes)
        assertEquals(SettingsFixtures.byDayTargets, repository.targets())
    }
}
