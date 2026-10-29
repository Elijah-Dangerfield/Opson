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
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.GroupShape
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RestingEnergy
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_targets_deficit_cap_toast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A deficit without a health app, the everyday activity that stands in for one, and day groups of
 * every goal type. The arithmetic is `PerDayGoalsTest` and `BurnedTest` in `:libraries:nutrition`.
 */
class TargetsBurnAndDaysTest : CoroutineTest() {

    private fun build(
        targets: Targets,
        burned: Burned? = null,
        goalsAndEnergy: Boolean = true,
        healthSource: HealthSource? = null,
        choosingActivity: Boolean = false,
    ) = TargetsStateBuilder.build(
        SettingsFixtures.settings(NumbersSettings(PresetId.Full), targets),
        SettingsFixtures.policy(PresetId.Full),
        burned = burned,
        goalsAndEnergy = goalsAndEnergy,
        healthSource = healthSource,
        choosingActivity = choosingActivity,
    )

    private fun burnedAt(total: Int) = Burned(RestingEnergy(total, RestingFrom.Calculated))

    // --- builder: a deficit without a health app ---

    @Test
    fun aDeficit_withNoBurnedYet_asksForTheDetails_insteadOfATarget() {
        val deficit = build(SettingsFixtures.deficitTargets).deficit

        assertEquals(true, deficit?.needsDetails)
        assertNull(deficit?.target)
        assertNull(deficit?.burned)
    }

    @Test
    fun aDeficit_withoutAHealthApp_isMeasuredAgainstTheEstimate() {
        val deficit = build(SettingsFixtures.deficitTargets, burned = SettingsFixtures.estimatedBurned).deficit

        assertEquals(false, deficit?.needsDetails)
        assertEquals(Display.Shown("1,910 kcal"), deficit?.target, "2,210 burned less 300")
        assertEquals(Display.Shown("2,210 kcal"), deficit?.burned)
        assertEquals(true, deficit?.estimated)
        assertEquals(false, build(SettingsFixtures.deficitTargets, burned = SettingsFixtures.burned).deficit?.estimated, "a health app's reading")
    }

    @Test
    fun aDeficitThatStartedAtZero_canStartFromTodaysBurn_onlyWhenThatGivesMore() {
        // 2,210 burned against a 2,100 aim implies 110, floored to 100.
        assertEquals(true, build(SettingsFixtures.newDeficitTargets, burned = SettingsFixtures.estimatedBurned).deficit?.canStartFromBurn)
        assertEquals(false, build(SettingsFixtures.newDeficitTargets, burned = burnedAt(2000)).deficit?.canStartFromBurn, "the aim is already above the burn")
        assertEquals(false, build(SettingsFixtures.newDeficitTargets).deficit?.canStartFromBurn, "nothing known yet")
        assertEquals(false, build(SettingsFixtures.deficitTargets, burned = SettingsFixtures.burned).deficit?.canStartFromBurn, "it started at 300")
    }

    @Test
    fun theEverydayActivity_isPickedHere_onlyWithoutAHealthApp() {
        val without = build(SettingsFixtures.deficitTargets, burned = SettingsFixtures.estimatedBurned, choosingActivity = true).burned
        assertEquals(EverydayActivity.entries, without?.levels)
        assertEquals(EverydayActivity.MostlySitting, without?.everydayActivity)

        val with = build(SettingsFixtures.deficitTargets, burned = SettingsFixtures.burned, healthSource = HealthSource.HealthConnect, choosingActivity = true).burned
        assertEquals(emptyList(), with?.levels)
    }

    // --- builder: day groups of every type ---

    @Test
    fun aDayGroup_canBeADeficit_withItsOwnStepper() {
        val state = build(SettingsFixtures.everyShapeByDayTargets, burned = SettingsFixtures.burned)
        val group = state.groups[2]

        assertEquals(ShapeKind.Deficit, group.shape)
        assertEquals(listOf(ShapeKind.Range, ShapeKind.Exact, ShapeKind.Deficit, ShapeKind.Surplus), group.shapes)
        assertEquals(EndStepper(Display.Shown("200"), down = StepState.Open, up = StepState.Open), group.deficit)
        assertEquals(Display.Shown("1,980 kcal"), group.summary, "2,180 burned less 200")
        assertFalse(group.waitingOnBurn)
        assertEquals(listOf(ShapeKind.Range, ShapeKind.Exact, ShapeKind.Deficit, ShapeKind.Surplus), state.groups.map { it.shape })
    }

    @Test
    fun aDeficitDay_withNoBurnedYet_readsAsItsRange_andSaysWhy() {
        val group = build(SettingsFixtures.everyShapeByDayTargets).groups[2]

        assertTrue(group.waitingOnBurn)
        assertEquals(Display.Shown("1,900 to 2,300 kcal"), group.summary)
    }

    @Test
    fun withoutGoalsAndEnergy_aDayGroupOffersNoShapes() {
        assertEquals(emptyList(), build(SettingsFixtures.everyShapeByDayTargets, goalsAndEnergy = false).groups[2].shapes)
    }

    // --- view model ---

    private fun viewModel(
        repository: FakeSettingsRepository,
        burned: FakeBurnedRepository = FakeBurnedRepository(),
        flag: Boolean = true,
        health: FakeHealthRepository = FakeHealthRepository(),
        healthOn: Boolean = false,
    ) = TargetsViewModel(
        repository,
        FakePolicySource(SettingsFixtures.policy(PresetId.Full)),
        health,
        healthFlag(healthOn),
        goalsAndEnergyFlag(on = flag),
        burned,
        FakeCalendar(),
        findSupportFlag(),
    )

    private fun FakeSettingsRepository.targets(): Targets = state.value.targets

    @Test
    fun aDeficit_canBePickedWithNothingKnown_andStartsFromTheBurnOnceThereIsOne() = runUnitTest {
        val repository = FakeSettingsRepository()
        val burned = FakeBurnedRepository(burned = null)
        val vm = viewModel(repository, burned)

        vm.takeAction(TargetsAction.StylePicked(StyleKind.Deficit))
        assertEquals(TargetStyle.Deficit(0, 0), repository.targets().style)
        assertEquals(true, vm.state.deficit?.needsDetails)

        burned.burnedFlow.value = SettingsFixtures.estimatedBurned
        assertEquals(true, vm.state.deficit?.canStartFromBurn)
        vm.takeAction(TargetsAction.StartFromBurnTapped)

        assertEquals(TargetStyle.Deficit(100, 100), repository.targets().style)
        assertEquals(SettingsFixtures.rangeTargets.minKcal to SettingsFixtures.rangeTargets.maxKcal, repository.targets().minKcal to repository.targets().maxKcal)
    }

    @Test
    fun startingFromTheBurn_neverRestartsADeficitThatHadOne() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings(targets = SettingsFixtures.deficitTargets))
        val vm = viewModel(repository, FakeBurnedRepository(burnedAt(3000)))

        vm.takeAction(TargetsAction.StartFromBurnTapped)

        assertEquals(TargetStyle.Deficit(300, 300), repository.targets().style)
    }

    @Test
    fun theActivityRow_withoutAHealthApp_opensTheLevels_andAPickWritesIt() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository)
        val events = eventsOf(vm)

        vm.takeAction(TargetsAction.ActivityTapped)
        assertEquals(EverydayActivity.entries, vm.state.burned?.levels)

        vm.takeAction(TargetsAction.EverydayActivityPicked(EverydayActivity.OnMyFeet))
        assertEquals(EverydayActivity.OnMyFeet, repository.state.value.everydayActivity)
        assertEquals(emptyList(), vm.state.burned?.levels)
        assertEquals(emptyList(), events)
    }

    @Test
    fun theActivityRow_withAHealthApp_opensIntegrations() = runUnitTest {
        val vm = viewModel(FakeSettingsRepository(), health = FakeHealthRepository(status = HealthStatus.Connected), healthOn = true)
        val events = eventsOf(vm)

        vm.takeAction(TargetsAction.ActivityTapped)

        assertEquals(listOf(TargetsEvent.OpenIntegrations), events)
    }

    @Test
    fun aDeficitDay_stepsOnItsOwn_upToWhereItStarted() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings(targets = SettingsFixtures.everyShapeByDayTargets))
        val vm = viewModel(repository)
        val events = eventsOf(vm)
        fun shape() = (repository.targets().style as TargetStyle.ByDay).groups[2].shape

        vm.takeAction(TargetsAction.DeficitStepped(up = false, group = 2))
        assertEquals(GroupShape.Deficit(150, 300), shape())
        repeat(4) { vm.takeAction(TargetsAction.DeficitStepped(up = true, group = 2)) }

        assertEquals(GroupShape.Deficit(300, 300), shape())
        assertEquals(listOf(TargetsEvent.ShowToast(Toast(Res.string.settings_targets_deficit_cap_toast))), events)
    }

    @Test
    fun pickingADeficitForADay_startsFromWhatItsMiddleImplies() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings(targets = SettingsFixtures.byDayTargets))
        val vm = viewModel(repository, FakeBurnedRepository(burnedAt(2900)))

        vm.takeAction(TargetsAction.GroupShapePicked(group = 1, shape = ShapeKind.Deficit))

        // Monday to Wednesday's middle is 2,500: 400 under a 2,900 burn.
        assertEquals(GroupShape.Deficit(400, 400), (repository.targets().style as TargetStyle.ByDay).groups[1].shape)
    }

    @Test
    fun withoutGoalsAndEnergy_aDayCanNotBecomeADeficit() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings(targets = SettingsFixtures.byDayTargets))
        val vm = viewModel(repository, FakeBurnedRepository(burnedAt(2900)), flag = false)

        vm.takeAction(TargetsAction.GroupShapePicked(group = 1, shape = ShapeKind.Deficit))

        assertEquals(GroupShape.Range, (repository.targets().style as TargetStyle.ByDay).groups[1].shape)
    }
}
