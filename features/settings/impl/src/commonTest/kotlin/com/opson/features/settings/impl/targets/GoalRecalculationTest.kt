package com.opson.features.settings.impl.targets

import com.opson.features.settings.GoalRecalculationRoute
import com.opson.features.settings.impl.FakeGoalRepository
import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.integrations.FakeCalendar
import com.opson.features.settings.impl.weightGoalsFlag
import com.opson.libraries.diary.GoalBook
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.goals.GoalFixtures
import com.opson.libraries.nutrition.BaselineChange
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The recalculation over Today: offered by its prompt, answered in its own dialog, and gone once answered. */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalRecalculationTest : CoroutineTest() {

    private val settings = GoalFixtures.settings(base = SettingsFixtures.settings())

    @Test
    fun theDialog_saysWhatChanged_andAcceptingWritesTheNewTarget() = runUnitTest {
        val repository = FakeSettingsRepository(settings)
        val vm = viewModel(repository, FakeGoalRepository(recalculation = GoalFixtures.connectedHealth))
        val events = eventsOf(vm)
        runCurrent()
        assertEquals(listOf(BaselineChange.RestingSource, BaselineChange.ActivitySource), vm.state.dialog?.reasons)

        vm.takeAction(GoalRecalculationAction.Accepted)
        runCurrent()
        assertEquals(2150, repository.state.value.targets.energyKcal)
        // The goal screen lists it: that day, from what to what, and that it was taken.
        val entry = GoalBook.driving(repository.state.value)?.history?.single()
        assertEquals(GoalFixtures.today, entry?.date)
        assertEquals(1930 to 2150, entry?.oldAimKcal to entry?.newAimKcal)
        assertEquals(true, entry?.accepted)
        assertEquals(listOf<GoalRecalculationEvent>(GoalRecalculationEvent.Close), events)
    }

    @Test
    fun keeping_leavesTheTarget_andClosingWritesNothing() = runUnitTest {
        val repository = FakeSettingsRepository(settings)
        val kept = viewModel(repository, FakeGoalRepository(recalculation = GoalFixtures.connectedHealth))
        runCurrent()
        kept.takeAction(GoalRecalculationAction.Kept)
        runCurrent()
        assertEquals(1930, repository.state.value.targets.energyKcal)
        assertEquals(GoalFixtures.connectedHealth.newBaseline.inputs, GoalBook.driving(repository.state.value)?.declined)

        val closed = FakeSettingsRepository(settings)
        val vm = viewModel(closed, FakeGoalRepository(recalculation = GoalFixtures.connectedHealth))
        runCurrent()
        vm.takeAction(GoalRecalculationAction.Dismissed)
        runCurrent()
        assertEquals(settings, closed.state.value)
    }

    @Test
    fun answeredElsewhere_closesAtOnce() = runUnitTest {
        val vm = viewModel(FakeSettingsRepository(settings), FakeGoalRepository(recalculation = null))
        val events = eventsOf(vm)
        runCurrent()
        assertNull(vm.state.dialog)
        assertEquals(listOf<GoalRecalculationEvent>(GoalRecalculationEvent.Close), events)
    }

    @Test
    fun thePrompt_offersOnlyAPendingRecalculation_withTheFlagOn() = runUnitTest {
        val pending = GoalRecalculationPrompt(weightGoalsFlag(on = true), FakeGoalRepository(recalculation = GoalFixtures.connectedHealth))
        assertTrue(pending.isEligible())
        assertIs<GoalRecalculationRoute>(pending.screen())
        assertFalse(GoalRecalculationPrompt(weightGoalsFlag(on = false), FakeGoalRepository(recalculation = GoalFixtures.connectedHealth)).isEligible())
        assertFalse(GoalRecalculationPrompt(weightGoalsFlag(on = true), FakeGoalRepository()).isEligible())
    }

    private fun viewModel(repository: FakeSettingsRepository, goals: FakeGoalRepository) =
        GoalRecalculationViewModel(repository, goals, FakePolicySource(SettingsFixtures.policy(PresetId.Full)), FakeCalendar(GoalFixtures.today))
}
