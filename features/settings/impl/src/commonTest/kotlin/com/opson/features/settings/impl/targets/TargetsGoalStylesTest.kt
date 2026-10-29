package com.opson.features.settings.impl.targets

import com.opson.features.settings.impl.FakeBurnedRepository
import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.Toast
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.findSupportFlag
import com.opson.features.settings.impl.goalsAndEnergyFlag
import com.opson.features.settings.impl.integrations.FakeCalendar
import com.opson.features.settings.impl.integrations.FakeHealthRepository
import com.opson.features.settings.impl.integrations.healthFlag
import com.opson.libraries.diary.MacroMode
import com.opson.libraries.diary.SummaryCardPreset
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.health.HealthSource
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.MIN_GOAL_KCAL
import com.opson.libraries.nutrition.GroupShape
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_targets_floor_toast
import opson.libraries.resources.generated.resources.settings_targets_surplus_cap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One number, a set surplus and Burned on Targets (`release.goalsAndEnergy`, design sync 4): the
 * list, each style's controls, a day group's shape, and where Burned comes from. The arithmetic
 * is `ExactAndSurplusTest` and `BurnedTest` in `:libraries:nutrition`.
 */
class TargetsGoalStylesTest : CoroutineTest() {

    private fun build(
        targets: Targets,
        numbers: NumbersSettings = NumbersSettings(PresetId.Full),
        burned: Burned? = null,
        goalsAndEnergy: Boolean = true,
    ) = TargetsStateBuilder.build(
        SettingsFixtures.settings(numbers, targets),
        SettingsFixtures.policy(numbers),
        burned = burned,
        goalsAndEnergy = goalsAndEnergy,
        healthSource = HealthSource.HealthConnect,
    )

    // --- builder: one number ---

    @Test
    fun oneNumber_showsTheGoalWithItsSteps_andNoRangeControls() {
        val state = build(SettingsFixtures.exactTargets)

        assertEquals(StyleKind.Exact, state.style)
        assertEquals(EndStepper(Display.Shown("2,200"), down = StepState.Open, up = StepState.Open), state.number?.stepper)
        assertEquals("2200", state.number?.text)
        assertNull(state.range)
        assertNull(state.surplus)
    }

    @Test
    fun oneNumber_atTheFloor_locksItsDownStep() {
        val atFloor = SettingsFixtures.rangeTargets.withEnergy(MIN_GOAL_KCAL).useExact()
        assertEquals(StepState.Locked, build(atFloor).number?.stepper?.down)
        assertEquals(StepState.Open, build(SettingsFixtures.rangeTargets.useExact()).number?.stepper?.down, "under where it started is fine")
    }

    @Test
    fun oneNumber_typed_saysTooLowOnlyOnceNoDigitCanHelp() {
        fun typed(text: String, done: Boolean = false) = TargetsStateBuilder.build(
            SettingsFixtures.settings(NumbersSettings(PresetId.Full), SettingsFixtures.exactTargets),
            SettingsFixtures.policy(PresetId.Full),
            drafts = TargetsDrafts(number = text, numberDone = done),
        ).number!!
        assertFalse(typed("15").tooLow, "on the way to 1500")
        assertFalse(typed("500").tooLow, "on the way to 5000")
        assertTrue(typed("500", done = true).tooLow)
        assertTrue(typed("700").tooLow, "7000 would be over the maximum, so no digit can help")
        assertTrue(typed("7000").tooHigh)
        assertFalse(typed("1500", done = true).tooLow)
    }

    // --- builder: surplus ---

    @Test
    fun aSurplus_showsTheAmount_andTodaysTargetAgainstBurned() {
        val surplus = assertNotNull(build(SettingsFixtures.surplusTargets, burned = SettingsFixtures.burned).surplus)

        assertEquals(Display.Shown("300"), surplus.amount)
        // 2,180 burned plus 300.
        assertEquals(Display.Shown("2,480 kcal"), surplus.target)
        assertFalse(surplus.fromAim)
        assertEquals(StepState.Open, surplus.down)
        assertEquals(StepState.Locked, surplus.up, "no further than where it started until a clinician sets a limit")
    }

    @Test
    fun aSurplus_withNoBurned_goesOnTopOfTheUsualTarget_andSaysSo() {
        val surplus = assertNotNull(build(SettingsFixtures.surplusTargets).surplus)

        assertEquals(Display.Shown("2,400 kcal"), surplus.target)
        assertTrue(surplus.fromAim)
    }

    @Test
    fun aSurplus_isClosedAtZero() {
        val zero = assertNotNull(build(SettingsFixtures.rangeTargets.useSurplus(burnKcal = null)).surplus)
        assertEquals(StepState.Closed, zero.down)
        assertEquals(StepState.Locked, zero.up)
    }

    @Test
    fun noModeWithoutShownCaloriesSeesEitherStyle() {
        listOf(NumbersSettings(PresetId.MacrosOnly), NumbersSettings.later(), NumbersSettings(PresetId.NoNumbers)).forEach { numbers ->
            listOf(SettingsFixtures.exactTargets, SettingsFixtures.surplusTargets, SettingsFixtures.shapedByDayTargets).forEach { targets ->
                val state = build(targets, numbers, burned = SettingsFixtures.burned)
                assertEquals(TargetsState(loaded = true, visible = false), state, "in $numbers")
            }
        }
    }

    // --- builder: Burned comes from ---

    @Test
    fun burnedComesFrom_restingAsItIsNow_andTheActivitySource() {
        val burned = assertNotNull(build(SettingsFixtures.rangeTargets, burned = SettingsFixtures.burned).burned)

        assertEquals(Display.Shown("1,610"), burned.resting)
        assertEquals(RestingFrom.Calculated, burned.restingFrom)
        assertEquals(HealthSource.HealthConnect, burned.activity)

        val unknown = assertNotNull(build(SettingsFixtures.rangeTargets).burned)
        assertNull(unknown.restingFrom)
        assertEquals(Display.Hidden, unknown.resting)
        assertNull(build(SettingsFixtures.rangeTargets, burned = SettingsFixtures.burned, goalsAndEnergy = false).burned)
    }

    @Test
    fun aHiddenWatchEnergy_hidesRestingEnergy() {
        val numbers = NumbersSettings(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Hide))
        assertEquals(Display.Hidden, build(SettingsFixtures.rangeTargets, numbers, burned = SettingsFixtures.burned).burned?.resting)
    }

    // --- builder: day group shapes ---

    @Test
    fun eachDayGroupSaysItsGoal_inItsShape() {
        val groups = build(SettingsFixtures.shapedByDayTargets).groups

        assertEquals(listOf(ShapeKind.Range, ShapeKind.Exact, ShapeKind.Surplus), groups.map { it.shape })
        assertEquals(Display.Shown("1,900 to 2,300 kcal"), groups[0].summary)
        assertEquals(Display.Shown("2,500 kcal"), groups[1].summary)
        assertEquals(Display.Shown("2,400 kcal"), groups[2].summary, "300 over the group's 2,100 until Burned is known")
        assertEquals(Display.Shown("2,480 kcal"), build(SettingsFixtures.shapedByDayTargets, burned = SettingsFixtures.burned).groups[2].summary)
        assertTrue(groups.all { it.shapes == ShapeKind.entries }, "every group open on its shapes")
    }

    @Test
    fun eachGroup_showsTheStepperForItsShape_andTheShapes() {
        val exact = build(SettingsFixtures.shapedByDayTargets).groups[1]
        assertEquals(ShapeKind.entries, exact.shapes)
        assertEquals(Display.Shown("2,500"), exact.number?.value)
        assertNull(exact.low)
        assertNull(exact.surplus)

        val surplus = build(SettingsFixtures.shapedByDayTargets).groups[2]
        assertEquals(Display.Shown("300"), surplus.surplus?.value)
        assertNull(surplus.number)
    }

    @Test
    fun withTheFlagOff_aGroupOffersNoShapes_butKeepsTheOneItHas() {
        val group = build(SettingsFixtures.shapedByDayTargets, goalsAndEnergy = false).groups[1]

        assertEquals(emptyList(), group.shapes)
        assertEquals(ShapeKind.Exact, group.shape)
        assertNotNull(group.number)
    }

    @Test
    fun theWeekPlacesEachGroupAtItsGoal() {
        val week = build(SettingsFixtures.shapedByDayTargets).week
        // Range 2,100, one number 2,500, surplus 2,400: lowest at 0, highest at 1.
        assertEquals(listOf(1f, 1f, 1f, 0f, 0f, 0.75f, 0.75f), week.map { it.position })
    }

    // --- the list ---

    @Test
    fun oneNumberAndASurplus_areInTheListWithTheFlag_aSurplusInFullNumbersOnly() {
        PresetId.entries.forEach { preset ->
            val on = TargetsStateBuilder.offered(StyleKind.Range, preset, goalsAndEnergy = true)
            assertTrue(StyleKind.Exact in on, "in $preset")
            assertEquals(TargetStyle.surplusAllowed(preset), StyleKind.Surplus in on, "in $preset")
            val off = TargetsStateBuilder.offered(StyleKind.Range, preset, goalsAndEnergy = false)
            assertFalse(StyleKind.Exact in off || StyleKind.Surplus in off, "in $preset")
        }
        assertEquals(
            listOf(StyleKind.Range, StyleKind.Exact, StyleKind.Deficit, StyleKind.Surplus, StyleKind.ByDay),
            TargetsStateBuilder.offered(StyleKind.Range, PresetId.Full, goalsAndEnergy = true),
        )
    }

    @Test
    fun withTheFlagOff_aSavedStyleIsStillShown() {
        val state = build(SettingsFixtures.surplusTargets, goalsAndEnergy = false)
        assertEquals(StyleKind.Surplus, state.style)
        assertTrue(state.styles.any { it.kind == StyleKind.Surplus && it.selected })
        assertFalse(state.styles.any { it.kind == StyleKind.Exact })
    }

    // --- view model ---

    private fun viewModel(repository: FakeSettingsRepository, burned: FakeBurnedRepository = FakeBurnedRepository(), flag: Boolean = true) =
        TargetsViewModel(
            repository,
            FakePolicySource(SettingsFixtures.policy(PresetId.Full)),
            FakeHealthRepository(),
            healthFlag(false),
            goalsAndEnergyFlag(on = flag),
            burned,
            FakeCalendar(),
            findSupportFlag(),
        )

    private fun FakeSettingsRepository.targets(): Targets = state.value.targets

    private fun repositoryWith(targets: Targets) = FakeSettingsRepository(SettingsFixtures.settings(targets = targets))

    @Test
    fun pickingOneNumber_keepsTheAim() = runUnitTest {
        val repository = FakeSettingsRepository()
        viewModel(repository).takeAction(TargetsAction.StylePicked(StyleKind.Exact))

        assertEquals(TargetStyle.Exact, repository.targets().style)
        assertEquals(SettingsFixtures.rangeTargets.energyKcal, repository.targets().energyKcal)
    }

    @Test
    fun pickingASurplusOrDeficit_startsFromWhatTheAimImpliesAgainstBurned() = runUnitTest {
        // A 2,100 aim against 1,780 burned is 320 over, floored to 300.
        val resting = SettingsFixtures.burned.resting.copy(kcal = 1780)
        val burned = FakeBurnedRepository(Burned(resting, activityCounted = false))
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository, burned)

        vm.takeAction(TargetsAction.StylePicked(StyleKind.Surplus))
        assertEquals(TargetStyle.Surplus(300, 300), repository.targets().style)
        assertEquals(Display.Shown("2,080 kcal"), vm.state.surplus?.target)

        burned.burnedFlow.value = Burned(resting.copy(kcal = 2420), activityCounted = false)
        vm.takeAction(TargetsAction.StylePicked(StyleKind.Deficit))
        assertEquals(TargetStyle.Deficit(300, 300), repository.targets().style)
    }

    @Test
    fun withTheFlagOff_neitherCanBePicked_andBurnedIsNotRead() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository, flag = false)

        vm.takeAction(TargetsAction.StylePicked(StyleKind.Surplus))
        vm.takeAction(TargetsAction.StylePicked(StyleKind.Exact))

        assertEquals(TargetStyle.Range, repository.targets().style)
        assertNull(vm.state.burned)
    }

    @Test
    fun numberStepped_movesTheGoalFifty_andScalesRatioMacros() = runUnitTest {
        val repository = repositoryWith(SettingsFixtures.exactTargets)
        val vm = viewModel(repository)

        vm.takeAction(TargetsAction.NumberStepped(up = true))

        assertEquals(2250, repository.targets().energyKcal)
        assertEquals(Display.Shown("2,250"), vm.state.number?.stepper?.value)
        assertTrue(repository.targets().proteinG > SettingsFixtures.exactTargets.proteinG)

        val grams = FakeSettingsRepository(SettingsFixtures.settings(targets = SettingsFixtures.exactTargets).copy(macroMode = MacroMode.Grams))
        viewModel(grams).takeAction(TargetsAction.NumberStepped(up = true))
        assertEquals(SettingsFixtures.exactTargets.proteinG, grams.targets().proteinG)
    }

    @Test
    fun numberStepped_pastTheFloor_writesNothingAndSaysWhy() = runUnitTest {
        val repository = repositoryWith(SettingsFixtures.rangeTargets.withEnergy(MIN_GOAL_KCAL).useExact())
        val vm = viewModel(repository)
        val events = eventsOf(vm)
        val writes = repository.writes

        vm.takeAction(TargetsAction.NumberStepped(up = false))

        assertEquals(writes, repository.writes)
        assertEquals(listOf<TargetsEvent>(TargetsEvent.ShowToast(Toast(Res.string.settings_targets_floor_toast))), events)
    }

    @Test
    fun surplusStepped_takesFifty_andGoesBackNoFurtherThanItStarted() = runUnitTest {
        val repository = repositoryWith(SettingsFixtures.surplusTargets)
        val vm = viewModel(repository)
        val events = eventsOf(vm)

        vm.takeAction(TargetsAction.SurplusStepped(up = false))
        assertEquals(TargetStyle.Surplus(250, 300), repository.targets().style)
        assertEquals(Display.Shown("250"), vm.state.surplus?.amount)
        vm.takeAction(TargetsAction.SurplusStepped(up = true))
        assertEquals(TargetStyle.Surplus(300, 300), repository.targets().style)

        val writes = repository.writes
        vm.takeAction(TargetsAction.SurplusStepped(up = true))
        assertEquals(writes, repository.writes)
        assertEquals(listOf<TargetsEvent>(TargetsEvent.ShowToast(Toast(Res.string.settings_targets_surplus_cap))), events)
    }

    @Test
    fun surplusStepped_atZero_saysNothing() = runUnitTest {
        val vm = viewModel(repositoryWith(SettingsFixtures.rangeTargets.useSurplus(burnKcal = null)))
        val events = eventsOf(vm)
        vm.takeAction(TargetsAction.SurplusStepped(up = false))
        assertEquals(emptyList(), events)
    }

    @Test
    fun groupShapePicked_givesTheGroupThatShape() = runUnitTest {
        val repository = repositoryWith(SettingsFixtures.byDayTargets)
        val vm = viewModel(repository)
        fun groups() = (repository.targets().style as TargetStyle.ByDay).groups

        vm.takeAction(TargetsAction.GroupShapePicked(1, ShapeKind.Exact))
        assertEquals(GroupShape.Exact, groups()[1].shape)
        assertEquals(ShapeKind.Exact, vm.state.groups[1].shape)
        assertNotNull(vm.state.groups[1].number, "the group shows its new stepper")

        // The group's middle is 2,500; against 2,180 burned that is 320 over, floored to 300.
        vm.takeAction(TargetsAction.GroupShapePicked(1, ShapeKind.Surplus))
        assertEquals(GroupShape.Surplus(300, 300), groups()[1].shape)
        vm.takeAction(TargetsAction.GroupShapePicked(1, ShapeKind.Range))
        assertEquals(GroupShape.Range, groups()[1].shape)
        assertEquals(2300 to 2700, groups()[1].minKcal to groups()[1].maxKcal, "the range is where it was left")
    }

    @Test
    fun aGroupsNumberAndSurplus_step() = runUnitTest {
        val repository = repositoryWith(SettingsFixtures.shapedByDayTargets)
        val vm = viewModel(repository)
        fun groups() = (repository.targets().style as TargetStyle.ByDay).groups

        vm.takeAction(TargetsAction.NumberStepped(up = true, group = 1))
        assertEquals(2350 to 2750, groups()[1].minKcal to groups()[1].maxKcal)

        vm.takeAction(TargetsAction.SurplusStepped(up = false, group = 2))
        assertEquals(GroupShape.Surplus(250, 300), groups()[2].shape)
    }

    @Test
    fun pickingAStyle_resetsTodaysMainNumber_andRingsWithARangeGoesBack() = runUnitTest {
        val repository = FakeSettingsRepository(
            SettingsFixtures.settings(targets = SettingsFixtures.exactTargets).copy(
                todayCard = SummaryCardPreset.Rings,
                todayMainNumberByStyle = mapOf("range" to "balance", "exact" to "balance"),
            ),
        )
        val vm = viewModel(repository)

        vm.takeAction(TargetsAction.StylePicked(StyleKind.Range))

        assertEquals(mapOf("exact" to "balance"), repository.state.value.todayMainNumberByStyle, "the new style starts on its default")
        assertEquals(SummaryCardPreset.InOut, repository.state.value.todayCard)
    }
}
