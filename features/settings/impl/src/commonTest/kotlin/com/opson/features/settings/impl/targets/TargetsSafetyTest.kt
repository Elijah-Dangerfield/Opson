package com.opson.features.settings.impl.targets

import com.opson.features.settings.impl.FakeBurnedRepository
import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.findSupportFlag
import com.opson.features.settings.impl.weightGoalsFlag
import com.opson.features.settings.impl.goalsAndEnergyFlag
import com.opson.features.settings.impl.integrations.FakeCalendar
import com.opson.features.settings.impl.integrations.FakeHealthRepository
import com.opson.features.settings.impl.integrations.healthFlag
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.DayGroup
import com.opson.libraries.nutrition.GroupShape
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RangeEnd
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The note under a low goal or a fast deficit on Targets: when it shows (the least a day's goal
 * asks for, by the person's sex; a set deficit's size), that it never shows without calories, that
 * it never stops a step, and that its Find support link follows `release.findSupport`.
 *
 * Not covered here: the lines themselves, which are `GoalSafetyTest` in `:libraries:nutrition`,
 * and how the note looks, which is the `TargetsLowGoalPreview` screenshot.
 */
class TargetsSafetyTest : CoroutineTest() {

    private fun build(
        targets: Targets,
        sex: BodySex? = null,
        numbers: NumbersSettings = NumbersSettings(PresetId.Full),
        burned: Burned? = null,
    ) = TargetsStateBuilder.build(
        SettingsFixtures.settings(numbers, targets).copy(bodySex = sex),
        SettingsFixtures.policy(numbers),
        burned = burned,
        goalsAndEnergy = true,
    )

    private fun range(min: Int, max: Int) = SettingsFixtures.rangeTargets.copy(energyKcal = (min + max) / 2, minKcal = min, maxKcal = max)

    @Test
    fun anOrdinaryGoal_hasNoNote() {
        assertNull(build(SettingsFixtures.rangeTargets).safety)
    }

    @Test
    fun aRange_isJudgedByItsLowEnd_againstTheLineForTheirSex() {
        val range = range(1300, 1700)
        assertNull(build(range, BodySex.Female).safety)
        assertEquals(SafetyNote.LowGoal, build(range, BodySex.Male).safety)
        assertEquals(SafetyNote.LowGoal, build(range, sex = null).safety, "halfway, 1,350, when not said")
        assertEquals(SafetyNote.LowGoal, build(range(1100, 1500), BodySex.Female).safety)
    }

    @Test
    fun oneNumber_isJudgedByTheNumber() {
        assertEquals(SafetyNote.LowGoal, build(range(1050, 1250).useExact(), BodySex.Female).safety)
        assertNull(build(range(1250, 1450).useExact(), BodySex.Female).safety)
    }

    @Test
    fun differentByDay_isJudgedByItsLowestDay_butNotAnEmptyGroup() {
        val week = SettingsFixtures.rangeTargets.copy(
            style = TargetStyle.ByDay(
                listOf(
                    DayGroup(DayOfWeek.entries.toSet() - DayOfWeek.SUNDAY, 1900, 2300),
                    DayGroup(setOf(DayOfWeek.SUNDAY), 1000, 1400),
                ),
            ),
        )
        assertEquals(SafetyNote.LowGoal, build(week, BodySex.Female).safety)

        val emptyLow = SettingsFixtures.rangeTargets.copy(
            style = TargetStyle.ByDay(listOf(DayGroup(DayOfWeek.entries.toSet(), 1900, 2300), DayGroup(emptySet(), 1000, 1400))),
        )
        assertNull(build(emptyLow, BodySex.Female).safety, "no day reads an empty group")
    }

    @Test
    fun aDeficitPastAHealthyPace_getsThePaceNote() {
        assertEquals(SafetyNote.FastDeficit, build(SettingsFixtures.fastDeficitTargets, burned = SettingsFixtures.activeBurned).safety)
        assertNull(build(SettingsFixtures.deficitTargets, burned = SettingsFixtures.activeBurned).safety)

        val fastDay = SettingsFixtures.byDayTargets.copy(
            style = TargetStyle.ByDay(
                listOf(
                    DayGroup(DayOfWeek.entries.toSet() - DayOfWeek.MONDAY, 1900, 2300),
                    DayGroup(setOf(DayOfWeek.MONDAY), 1900, 2300, GroupShape.Deficit(1100, 1100)),
                ),
            ),
        )
        assertEquals(SafetyNote.FastDeficit, build(fastDay, burned = SettingsFixtures.activeBurned).safety)
    }

    @Test
    fun aLowGoal_comesBeforeAFastPace() {
        // 2,180 burned less 1,200 would be 980; a worked-out goal stays at 1,200, under a man's line.
        val both = SettingsFixtures.fastDeficitTargets
        assertEquals(SafetyNote.LowGoal, build(both, BodySex.Male, burned = SettingsFixtures.burned).safety)
    }

    @Test
    fun noModeWithoutShownCalories_hasTheNote() {
        listOf(PresetId.NoNumbers, PresetId.MacrosOnly, PresetId.MacrosWithGrams).forEach { preset ->
            val state = build(range(1000, 1400), BodySex.Female, numbers = NumbersSettings(preset))
            assertNull(state.safety, "in $preset")
            assertFalse(state.visible)
        }
    }

    // --- view model ---

    private fun viewModel(repository: FakeSettingsRepository, findSupport: Boolean = true) = TargetsViewModel(
        repository,
        FakePolicySource(SettingsFixtures.policy(PresetId.Full)),
        FakeHealthRepository(),
        healthFlag(false),
        goalsAndEnergyFlag(true),
        FakeBurnedRepository(),
        FakeCalendar(),
        findSupportFlag(findSupport),
        weightGoalsFlag(),
    )

    @Test
    fun aLowGoal_isNeverRefused_theNoteJustAppears() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings(targets = range(1250, 1650)).copy(bodySex = BodySex.Female))
        val vm = viewModel(repository)
        val events = eventsOf(vm)
        assertNull(vm.state.safety)

        repeat(2) { vm.takeAction(TargetsAction.StepTapped(RangeEnd.Low, up = false)) }

        assertEquals(1150, repository.state.value.targets.minKcal)
        assertEquals(SafetyNote.LowGoal, vm.state.safety)
        assertEquals(emptyList(), events, "no toast, no block")
    }

    @Test
    fun theNotesLink_opensFindSupport_onlyWithTheFlag() = runUnitTest {
        val low = SettingsFixtures.settings(targets = range(1000, 1400))
        val on = viewModel(FakeSettingsRepository(low))
        val onEvents = eventsOf(on)
        assertTrue(on.state.supportLink)
        on.takeAction(TargetsAction.FindSupportTapped)
        assertEquals(listOf<TargetsEvent>(TargetsEvent.OpenFindSupport), onEvents)

        val off = viewModel(FakeSettingsRepository(low), findSupport = false)
        val offEvents = eventsOf(off)
        assertEquals(SafetyNote.LowGoal, off.state.safety, "the note itself shows either way")
        assertFalse(off.state.supportLink)
        off.takeAction(TargetsAction.FindSupportTapped)
        assertEquals(emptyList(), offEvents)
    }

    @Test
    fun aTypedNumberUnderTheMinimum_saysSoOnDone_andIsNotWritten() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings(targets = SettingsFixtures.exactTargets))
        val vm = viewModel(repository)

        vm.takeAction(TargetsAction.NumberTyped("500"))
        assertFalse(vm.state.number!!.tooLow, "500 may yet be 5000")
        vm.takeAction(TargetsAction.NumberDone)
        assertTrue(vm.state.number!!.tooLow)
        assertEquals("500", vm.state.number!!.text)
        assertEquals(2200, repository.state.value.targets.energyKcal)

        vm.takeAction(TargetsAction.NumberTyped("1300"))
        assertFalse(vm.state.number!!.tooLow)
        assertEquals(1300, repository.state.value.targets.energyKcal, "a goal under where it started is written")
    }

    @Test
    fun aTypedNumberNearTheBounds_isWrittenAsTyped() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings(targets = SettingsFixtures.exactTargets))
        val vm = viewModel(repository)

        for (kcal in listOf(900, 800, 6000)) {
            vm.takeAction(TargetsAction.NumberTyped(kcal.toString()))
            assertEquals(kcal, repository.state.value.targets.energyKcal, "$kcal is saved as $kcal, not moved by the range kept behind it")
        }
    }
}
