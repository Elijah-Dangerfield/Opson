package com.opson.features.settings.impl.targets

import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.Toast
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.goalStylesFlag
import com.opson.features.settings.impl.integrations.FakeHealthRepository
import com.opson.features.settings.impl.integrations.healthFlag
import com.opson.libraries.diary.MacroMode
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.GroupShape
import com.opson.libraries.nutrition.MAX_SURPLUS_KCAL
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
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
 * One number and a set surplus on Targets (`release.goalStyles`): the picker, each style's card,
 * and a day group's shape. The arithmetic is `ExactAndSurplusTest` in `:libraries:nutrition`.
 */
class TargetsGoalStylesTest : CoroutineTest() {

    private fun build(
        targets: Targets,
        numbers: NumbersSettings = NumbersSettings(PresetId.Full),
        burn: Int? = null,
        editingGroup: Int? = null,
        goalStyles: Boolean = true,
    ) = TargetsStateBuilder.build(
        SettingsFixtures.settings(numbers, targets),
        SettingsFixtures.policy(numbers),
        burnKcal = burn,
        editingGroup = editingGroup,
        goalStyles = goalStyles,
    )

    // --- builder: one number ---

    @Test
    fun oneNumber_showsTheGoalWithItsSteps_andNoRangeCard() {
        val state = build(SettingsFixtures.exactTargets)

        assertEquals(StyleKind.Exact, state.style)
        assertEquals(EndStepper(Display.Shown("2,200"), down = StepState.Open, up = StepState.Open), state.number)
        assertNull(state.range)
        assertNull(state.surplus)
    }

    @Test
    fun oneNumber_atTheFloor_locksItsDownStep() {
        val atFloor = SettingsFixtures.rangeTargets.copy(startingKcal = 2100).useExact()
        assertEquals(StepState.Locked, build(atFloor).number?.down)
    }

    // --- builder: surplus ---

    @Test
    fun aSurplus_showsTheAmount_andTheGoalItComesTo_fromTheBurn() {
        val surplus = assertNotNull(build(SettingsFixtures.surplusTargets, burn = SettingsFixtures.BURN_KCAL).surplus)

        assertEquals(Display.Shown("300"), surplus.amount)
        assertEquals(Display.Shown("2,172 kcal"), surplus.burn)
        assertEquals(Display.Shown("2,470 kcal"), surplus.goal)
        assertFalse(surplus.fromTarget)
        assertEquals(StepState.Open, surplus.down)
        assertEquals(StepState.Open, surplus.up)
    }

    @Test
    fun aSurplus_withNoBurn_goesOnTopOfTheUsualTarget_andSaysSo() {
        val surplus = assertNotNull(build(SettingsFixtures.surplusTargets).surplus)

        assertNull(surplus.burn)
        assertEquals(Display.Shown("2,400 kcal"), surplus.goal)
        assertTrue(surplus.fromTarget)
    }

    @Test
    fun aSurplus_isClosedAtZero_andLockedAtTheCap() {
        val zero = assertNotNull(build(SettingsFixtures.rangeTargets.useSurplus(burnKcal = null)).surplus)
        assertEquals(StepState.Closed, zero.down)
        val cap = assertNotNull(build(SettingsFixtures.rangeTargets.copy(style = TargetStyle.Surplus(MAX_SURPLUS_KCAL))).surplus)
        assertEquals(StepState.Locked, cap.up)
    }

    @Test
    fun aHiddenWatchEnergyHidesTheBurn_butNotTheSurplusGoal() {
        val numbers = NumbersSettings(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Hide))
        val surplus = assertNotNull(build(SettingsFixtures.surplusTargets, numbers, burn = SettingsFixtures.BURN_KCAL).surplus)

        assertEquals(Display.Hidden, surplus.burn)
        assertEquals(Display.Shown("2,470 kcal"), surplus.goal)
    }

    @Test
    fun noModeWithoutShownCaloriesSeesEitherStyle() {
        listOf(PresetId.Fuel, PresetId.Delayed, PresetId.Coast, PresetId.Blind, PresetId.Nutrient).forEach { preset ->
            listOf(SettingsFixtures.exactTargets, SettingsFixtures.surplusTargets, SettingsFixtures.shapedByDayTargets).forEach { targets ->
                val state = build(targets, NumbersSettings(preset), burn = SettingsFixtures.BURN_KCAL, editingGroup = 1)
                assertEquals(TargetsState(visible = false), state, "in $preset")
            }
        }
    }

    // --- builder: day group shapes ---

    @Test
    fun eachDayGroupSaysItsGoal_inItsShape() {
        val groups = build(SettingsFixtures.shapedByDayTargets).groups

        assertEquals(listOf(ShapeKind.Range, ShapeKind.Exact, ShapeKind.Surplus), groups.map { it.shape })
        assertEquals(Display.Shown("1,900 to 2,300 kcal"), groups[0].summary)
        assertEquals(Display.Shown("2,500 kcal"), groups[1].summary)
        assertEquals(Display.Shown("2,400 kcal"), groups[2].summary, "300 over the group's 2,100 until a burn is known")
        assertTrue(groups.all { it.shapes.isEmpty() }, "shapes only while editing")
    }

    @Test
    fun editingAGroup_opensTheStepperForItsShape_andTheShapes() {
        val exact = build(SettingsFixtures.shapedByDayTargets, editingGroup = 1).groups[1]
        assertEquals(ShapeKind.entries, exact.shapes)
        assertEquals(Display.Shown("2,500"), exact.number?.value)
        assertNull(exact.low)
        assertNull(exact.surplus)

        val surplus = build(SettingsFixtures.shapedByDayTargets, editingGroup = 2).groups[2]
        assertEquals(Display.Shown("300"), surplus.surplus?.value)
        assertNull(surplus.number)
    }

    @Test
    fun withTheFlagOff_aGroupOffersNoShapes_butKeepsTheOneItHas() {
        val group = build(SettingsFixtures.shapedByDayTargets, editingGroup = 1, goalStyles = false).groups[1]

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

    // --- goal style sheet ---

    @Test
    fun oneNumberAndASurplus_areOfferedWithTheFlag_inEveryModeThatShowsTargets() {
        PresetId.entries.forEach { preset ->
            val on = GoalStyleStateBuilder.build(SettingsFixtures.settings(NumbersSettings(preset)), goalStyles = true).options
            assertTrue(StyleKind.Exact in on && StyleKind.Surplus in on, "in $preset")
            val off = GoalStyleStateBuilder.build(SettingsFixtures.settings(NumbersSettings(preset))).options
            assertFalse(StyleKind.Exact in off || StyleKind.Surplus in off, "in $preset")
        }
    }

    @Test
    fun withTheFlagOff_aSavedStyleIsStillShown() {
        val state = GoalStyleStateBuilder.build(SettingsFixtures.settings(targets = SettingsFixtures.surplusTargets))
        assertEquals(StyleKind.Surplus, state.selected)
        assertTrue(StyleKind.Surplus in state.options)
        assertFalse(StyleKind.Exact in state.options)
    }

    @Test
    fun pickingOneNumber_keepsTheAim() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = GoalStyleViewModel(repository, goalStylesFlag(on = true))

        vm.takeAction(GoalStyleAction.StylePicked(StyleKind.Exact))
        vm.takeAction(GoalStyleAction.DoneTapped)

        assertEquals(TargetStyle.Exact, repository.targets().style)
        assertEquals(SettingsFixtures.rangeTargets.energyKcal, repository.targets().energyKcal)
    }

    @Test
    fun pickingASurplus_startsItAtZero() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = GoalStyleViewModel(repository, goalStylesFlag(on = true))

        vm.takeAction(GoalStyleAction.StylePicked(StyleKind.Surplus))
        vm.takeAction(GoalStyleAction.DoneTapped)

        assertEquals(TargetStyle.Surplus(0), repository.targets().style)
    }

    @Test
    fun withTheFlagOff_neitherCanBePicked() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = GoalStyleViewModel(repository, goalStylesFlag(on = false))

        vm.takeAction(GoalStyleAction.StylePicked(StyleKind.Surplus))
        vm.takeAction(GoalStyleAction.DoneTapped)

        assertEquals(TargetStyle.Range, repository.targets().style)
    }

    // --- view model ---

    private fun viewModel(repository: FakeSettingsRepository) = TargetsViewModel(
        repository,
        FakePolicySource(SettingsFixtures.policy(PresetId.Full)),
        FakeHealthRepository(),
        healthFlag(false),
        goalStylesFlag(on = true),
    )

    private fun FakeSettingsRepository.targets(): Targets = state.value.targets

    private fun repositoryWith(targets: Targets) = FakeSettingsRepository(SettingsFixtures.settings(targets = targets))

    @Test
    fun numberStepped_movesTheGoalFifty_andScalesRatioMacros() = runUnitTest {
        val repository = repositoryWith(SettingsFixtures.exactTargets)
        val vm = viewModel(repository)

        vm.takeAction(TargetsAction.NumberStepped(up = true))

        assertEquals(2250, repository.targets().energyKcal)
        assertEquals(Display.Shown("2,250"), vm.state.number?.value)
        assertTrue(repository.targets().proteinG > SettingsFixtures.exactTargets.proteinG)

        val grams = FakeSettingsRepository(SettingsFixtures.settings(targets = SettingsFixtures.exactTargets).copy(macroMode = MacroMode.Grams))
        viewModel(grams).takeAction(TargetsAction.NumberStepped(up = true))
        assertEquals(SettingsFixtures.exactTargets.proteinG, grams.targets().proteinG)
    }

    @Test
    fun numberStepped_pastTheFloor_writesNothingAndSaysWhy() = runUnitTest {
        val repository = repositoryWith(SettingsFixtures.rangeTargets.copy(startingKcal = 2100).useExact())
        val vm = viewModel(repository)
        val events = eventsOf(vm)
        val writes = repository.writes

        vm.takeAction(TargetsAction.NumberStepped(up = false))

        assertEquals(writes, repository.writes)
        assertEquals(listOf<TargetsEvent>(TargetsEvent.ShowToast(Toast(Res.string.settings_targets_floor_toast))), events)
    }

    @Test
    fun surplusStepped_addsAndTakesFifty() = runUnitTest {
        val repository = repositoryWith(SettingsFixtures.surplusTargets)
        val vm = viewModel(repository)

        vm.takeAction(TargetsAction.SurplusStepped(up = true))
        assertEquals(TargetStyle.Surplus(350), repository.targets().style)
        vm.takeAction(TargetsAction.SurplusStepped(up = false))
        vm.takeAction(TargetsAction.SurplusStepped(up = false))
        assertEquals(TargetStyle.Surplus(250), repository.targets().style)
        assertEquals(Display.Shown("250"), vm.state.surplus?.amount)
    }

    @Test
    fun surplusStepped_atTheCap_writesNothingAndSaysWhy_atZero_saysNothing() = runUnitTest {
        val capped = repositoryWith(SettingsFixtures.rangeTargets.copy(style = TargetStyle.Surplus(MAX_SURPLUS_KCAL)))
        val vm = viewModel(capped)
        val events = eventsOf(vm)
        val writes = capped.writes

        vm.takeAction(TargetsAction.SurplusStepped(up = true))

        assertEquals(writes, capped.writes)
        assertEquals(listOf<TargetsEvent>(TargetsEvent.ShowToast(Toast(Res.string.settings_targets_surplus_cap))), events)

        val zero = repositoryWith(SettingsFixtures.rangeTargets.useSurplus(burnKcal = null))
        val atZero = viewModel(zero)
        val quiet = eventsOf(atZero)
        atZero.takeAction(TargetsAction.SurplusStepped(up = false))
        assertEquals(emptyList(), quiet)
    }

    @Test
    fun groupShapePicked_givesTheGroupThatShape() = runUnitTest {
        val repository = repositoryWith(SettingsFixtures.byDayTargets)
        val vm = viewModel(repository)
        fun groups() = (repository.targets().style as TargetStyle.ByDay).groups

        vm.takeAction(TargetsAction.EditGroupTapped(1))
        vm.takeAction(TargetsAction.GroupShapePicked(1, ShapeKind.Exact))
        assertEquals(GroupShape.Exact, groups()[1].shape)
        assertEquals(ShapeKind.Exact, vm.state.groups[1].shape)
        assertNotNull(vm.state.groups[1].number, "the group stays open on its new stepper")

        vm.takeAction(TargetsAction.GroupShapePicked(1, ShapeKind.Surplus))
        assertEquals(GroupShape.Surplus(0), groups()[1].shape)
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

        vm.takeAction(TargetsAction.SurplusStepped(up = true, group = 2))
        assertEquals(GroupShape.Surplus(350), groups()[2].shape)
    }
}
