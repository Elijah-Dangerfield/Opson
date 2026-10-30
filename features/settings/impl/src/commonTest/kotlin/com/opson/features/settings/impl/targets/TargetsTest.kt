package com.opson.features.settings.impl.targets

import com.opson.features.settings.impl.FakeBurnedRepository
import com.opson.features.settings.impl.FakeGoalRepository
import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.Toast
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.findSupportFlag
import com.opson.features.settings.impl.weightGoalsFlag
import com.opson.features.settings.impl.goalsAndEnergyFlag
import com.opson.features.settings.impl.integrations.FakeCalendar
import com.opson.features.settings.impl.integrations.FakeHealthRepository
import com.opson.features.settings.impl.integrations.healthFlag
import com.opson.libraries.diary.MacroMode
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.MAX_GOAL_KCAL
import com.opson.libraries.nutrition.MIN_GOAL_KCAL
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RangeEnd
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_targets_ceiling_toast
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
 * Targets: amounts only when calories are configured Show, each end stepping on its own between
 * the lowest and highest a goal goes, the deficit and by-day styles (every group open, days moved
 * by a tap), and ratio versus gram macros. The floor, deficit and group arithmetic themselves are
 * `TargetsTest` and `TargetStyleTest` in `:libraries:nutrition`; the low-goal note is
 * `TargetsSafetyTest`.
 */
class TargetsTest : CoroutineTest() {

    private fun build(
        numbers: NumbersSettings = NumbersSettings(PresetId.Full),
        targets: Targets = SettingsFixtures.rangeTargets,
        burned: Burned? = null,
    ) = TargetsStateBuilder.build(
        SettingsFixtures.settings(numbers, targets),
        SettingsFixtures.policy(numbers),
        burned = burned,
    )

    // --- builder ---

    @Test
    fun aRangeShowsEachEndWithItsSteps() {
        val range = assertNotNull(build().range)

        assertEquals(
            EndStepper(Display.Shown("1,900"), down = StepState.Open, up = StepState.Open, TypedField(TypedField.Kind.Low), "1900"),
            range.low,
            "under where it started is fine",
        )
        assertEquals(EndStepper(Display.Shown("2,300"), down = StepState.Open, up = StepState.Open, TypedField(TypedField.Kind.High), "2300"), range.high)
        assertFalse(range.lowLocked)
        assertTrue(range.band.start < range.band.endInclusive)
    }

    @Test
    fun aRangeAtTheLimits_locksThoseSteps() {
        val low = assertNotNull(build(targets = SettingsFixtures.rangeTargets.copy(energyKcal = 1000, minKcal = MIN_GOAL_KCAL, maxKcal = 1200)).range)
        assertEquals(StepState.Locked, low.low.down)
        assertTrue(low.lowLocked)

        val high = assertNotNull(build(targets = SettingsFixtures.rangeTargets.copy(energyKcal = 5800, minKcal = 5600, maxKcal = MAX_GOAL_KCAL)).range)
        assertEquals(StepState.Locked, high.high.up)
        assertFalse(high.lowLocked)
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
        listOf(NumbersSettings(PresetId.MacrosOnly), NumbersSettings.later(), NumbersSettings(PresetId.NoNumbers)).forEach { numbers ->
            listOf(SettingsFixtures.rangeTargets, SettingsFixtures.deficitTargets, SettingsFixtures.byDayTargets).forEach { targets ->
                val state = build(numbers, targets, burned = SettingsFixtures.burned)
                assertEquals(TargetsState(loaded = true, visible = false), state, "in $numbers")
            }
        }
    }

    @Test
    fun macrosWithGrams_hidesTheCalorieGoal_butKeepsTheGramTargets() {
        val state = build(NumbersSettings(PresetId.MacrosWithGrams), SettingsFixtures.rangeTargets, burned = SettingsFixtures.burned)
        assertEquals(false, state.visible)
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat), state.macros.map { it.metric })
        assertEquals(Display.Shown("120 g"), state.macros.first().grams)
        assertTrue(state.styles.isEmpty() && state.range == null && state.burned == null, "no calorie goal anywhere")
    }

    @Test
    fun theVitaminsAndMineralsRow_isWhereverMicrosShow_caloriesOrNot() {
        assertTrue(build().micros)
        assertTrue(TargetsStateBuilder.build(SettingsFixtures.settings(), SettingsFixtures.policy(PresetId.MacrosWithGrams)).micros)
        assertFalse(TargetsStateBuilder.build(SettingsFixtures.settings(), SettingsFixtures.policy(PresetId.MacrosOnly)).micros)
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
    }

    @Test
    fun everyGroupIsOpen_andEveryGroupButTheFirstCanBeRemoved() {
        val groups = build(targets = SettingsFixtures.byDayTargets).groups

        assertEquals(listOf("1,900", "2,300"), groups.map { (it.low?.value as Display.Shown).text })
        assertEquals(listOf(false, true), groups.map { it.removable })
    }

    @Test
    fun theFirstGroupsOwnDays_areShownNotTapped_andEveryOtherDayIs() {
        val groups = build(targets = SettingsFixtures.byDayTargets).groups

        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
            groups[0].days.filter { it.tappable }.map { it.day },
            "in Other days, only the days another group has can be tapped, to bring them back",
        )
        assertTrue(groups[1].days.all { it.tappable })
    }

    @Test
    fun theOnlyGroup_hasNoDaysToPick() {
        val only = SettingsFixtures.rangeTargets.useByDay()
        val group = build(targets = only).groups.single()

        assertEquals(GroupLabel.EveryDay, group.label)
        assertEquals(emptyList(), group.days)
        assertFalse(group.removable)
    }

    @Test
    fun aNewGroup_isEmpty_andSaysHowToFillIt() {
        val groups = build(targets = SettingsFixtures.newByDayTargets).groups

        assertEquals(listOf(false, true), groups.map { it.empty })
        assertTrue(groups[1].days.none { it.inGroup })
        assertEquals(List(7) { 0.5f }, build(targets = SettingsFixtures.newByDayTargets).week.map { it.position }, "an empty group is drawn nowhere")
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
        findSupportFlag(),
        weightGoalsFlag(),
        FakeGoalRepository(),
    )

    private fun FakeSettingsRepository.targets(): Targets = state.value.targets

    @Test
    fun untilBurnedArrives_thePageIsLoading_notTheBackgroundCard() = runUnitTest {
        val burned = FakeBurnedRepository()
        val waiting = object : BurnedRepository by burned {
            override fun burned(day: LocalDate): Flow<Burned?> = flow { awaitCancellation() }
        }
        val vm = TargetsViewModel(
            FakeSettingsRepository(),
            FakePolicySource(SettingsFixtures.policy(PresetId.Full)),
            FakeHealthRepository(),
            healthFlag(false),
            goalsAndEnergyFlag(true),
            waiting,
            FakeCalendar(),
            findSupportFlag(),
            weightGoalsFlag(),
            FakeGoalRepository(),
        )

        assertFalse(vm.state.loaded)
        assertTrue(viewModel(FakeSettingsRepository()).state.loaded, "once everything has arrived")
    }

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
    fun aLowEndUnderWhereItStarted_isWritten() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository)
        val events = eventsOf(vm)

        repeat(4) { vm.takeAction(TargetsAction.StepTapped(RangeEnd.Low, up = false)) }

        assertEquals(1700 to 2300, repository.targets().minKcal to repository.targets().maxKcal)
        assertEquals(emptyList(), events)
    }

    @Test
    fun aStepPastTheFloorWritesNothingAndSaysWhy() = runUnitTest {
        val atFloor = SettingsFixtures.rangeTargets.copy(energyKcal = 1000, minKcal = MIN_GOAL_KCAL, maxKcal = 1200)
        val repository = FakeSettingsRepository(SettingsFixtures.settings(targets = atFloor))
        val vm = viewModel(repository)
        val events = eventsOf(vm)
        val writes = repository.writes

        vm.takeAction(TargetsAction.StepTapped(RangeEnd.Low, up = false))

        assertEquals(writes, repository.writes)
        assertEquals(listOf<TargetsEvent>(TargetsEvent.ShowToast(Toast(Res.string.settings_targets_floor_toast))), events)
    }

    @Test
    fun aStepPastTheCeilingWritesNothingAndSaysWhy() = runUnitTest {
        val atCeiling = SettingsFixtures.rangeTargets.copy(energyKcal = 5800, minKcal = 5600, maxKcal = MAX_GOAL_KCAL)
        val repository = FakeSettingsRepository(SettingsFixtures.settings(targets = atCeiling))
        val vm = viewModel(repository)
        val events = eventsOf(vm)
        val writes = repository.writes

        vm.takeAction(TargetsAction.StepTapped(RangeEnd.High, up = true))

        assertEquals(writes, repository.writes)
        assertEquals(listOf<TargetsEvent>(TargetsEvent.ShowToast(Toast(Res.string.settings_targets_ceiling_toast))), events)
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
        vm.takeAction(TargetsAction.DayTapped(group = 1, day = DayOfWeek.FRIDAY))
        assertTrue(DayOfWeek.FRIDAY in groups()[0].days, "tapped again, it goes back to Other days")

        vm.takeAction(TargetsAction.AddGroupTapped)
        assertEquals(3, groups().size)
        assertTrue(vm.state.groups[2].empty)
        vm.takeAction(TargetsAction.DayTapped(group = 2, day = DayOfWeek.MONDAY))
        assertEquals(setOf(DayOfWeek.MONDAY), groups()[2].days, "a new group takes its days straight away")
        assertTrue(DayOfWeek.MONDAY !in groups()[1].days)

        vm.takeAction(TargetsAction.StepTapped(RangeEnd.High, up = true, group = 2))
        assertEquals(2350, groups()[2].maxKcal)

        vm.takeAction(TargetsAction.RemoveGroupTapped(2))
        assertEquals(2, groups().size)
        assertTrue(DayOfWeek.MONDAY in groups()[0].days, "its days go back to Other days")
    }

    @Test
    fun theFirstGroup_cantBeRemoved_andItsOwnDaysDontMove() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings(targets = SettingsFixtures.byDayTargets))
        val vm = viewModel(repository)
        val before = repository.targets()

        vm.takeAction(TargetsAction.RemoveGroupTapped(0))
        vm.takeAction(TargetsAction.DayTapped(group = 0, day = DayOfWeek.FRIDAY))

        assertEquals(before, repository.targets())
    }

    @Test
    fun pickingDifferentByDay_startsWithAnEmptyGroupReadyForItsDays() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository)

        vm.takeAction(TargetsAction.StylePicked(StyleKind.ByDay))

        val groups = (repository.targets().style as TargetStyle.ByDay).groups
        assertEquals(listOf(DayOfWeek.entries.toSet(), emptySet()), groups.map { it.days })
        assertEquals(listOf(GroupLabel.OtherDays, GroupLabel.Numbered(2)), vm.state.groups.map { it.label })
        assertTrue(vm.state.groups[1].days.all { it.tappable })
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
        vm.takeAction(TargetsAction.ConnectHealthTapped)
        vm.takeAction(TargetsAction.ModesTapped)
        vm.takeAction(TargetsAction.BackTapped)

        assertEquals(
            listOf(TargetsEvent.OpenRestingEnergy, TargetsEvent.OpenIntegrations, TargetsEvent.OpenModes, TargetsEvent.Close),
            events,
        )
    }

    // --- goal style list ---

    @Test
    fun aSetDeficitIsOfferedOnlyInFullNumbers_withGoalsAndEnergy() {
        PresetId.entries.forEach { preset ->
            val options = TargetsStateBuilder.offered(StyleKind.Range, preset, goalsAndEnergy = true)
            assertEquals(preset == PresetId.Full, StyleKind.Deficit in options, "in $preset")
        }
        // Without the flag nothing reads Burned, so a deficit would only ever be the range.
        assertFalse(StyleKind.Deficit in TargetsStateBuilder.offered(StyleKind.Range, PresetId.Full, goalsAndEnergy = false))
        assertTrue(StyleKind.Deficit in TargetsStateBuilder.offered(StyleKind.Deficit, PresetId.Full, goalsAndEnergy = false), "a saved one still shows")
    }

    @Test
    fun theListMarksTheSavedStyle_andTheRangeRowSaysItsRange() {
        val styles = build(targets = SettingsFixtures.byDayTargets).styles

        assertEquals(listOf(StyleKind.Range, StyleKind.ByDay), styles.map { it.kind }, "release.goalsAndEnergy off")
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
        val repository = FakeSettingsRepository(SettingsFixtures.settings(NumbersSettings(PresetId.MacrosOnly)))
        val vm = viewModel(repository, PresetId.MacrosOnly)
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
