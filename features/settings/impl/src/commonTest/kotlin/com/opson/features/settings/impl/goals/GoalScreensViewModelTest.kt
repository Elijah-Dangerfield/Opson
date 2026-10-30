package com.opson.features.settings.impl.goals

import com.opson.features.settings.GoalEditRoute
import com.opson.features.settings.GoalsRoute
import com.opson.features.settings.impl.FakeGoalRepository
import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.findSupportFlag
import com.opson.features.settings.impl.integrations.FakeCalendar
import com.opson.features.settings.impl.weightGoalsFlag
import com.opson.libraries.diary.GoalBook
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.goals.GoalFixtures
import com.opson.libraries.nutrition.BaselineChange
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.GoalKind
import com.opson.libraries.nutrition.GoalRecalculated
import com.opson.libraries.nutrition.GoalStatus
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The goal editor and the goals page: what they read, what they write, where they go. */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalScreensViewModelTest : CoroutineTest() {

    private val base = SettingsFixtures.settings().copy(heightCm = 180.0, birthYear = 1996, bodySex = BodySex.Male)

    @Test
    fun aNewGoal_isPicked_set_andSaved_thenRevealed() = runUnitTest {
        val repository = FakeSettingsRepository(base)
        val vm = editor(repository, GoalEditRoute())
        runCurrent()
        assertEquals(null, vm.state.view?.kind)
        assertTrue(vm.state.view!!.kinds.isNotEmpty())

        vm.takeAction(GoalEditAction.Edit(GoalEdit.KindPicked(GoalKind.Weight)))
        vm.takeAction(GoalEditAction.Edit(GoalEdit.TargetTyped("80")))
        vm.takeAction(GoalEditAction.SaveTapped)
        runCurrent()

        val goal = assertNotNull(GoalBook.driving(repository.state.value))
        assertIs<GoalType.Weight>(goal.type)
        assertEquals(goal.targetsKcal, repository.state.value.targets.energyKcal, "the goal wrote its target")
        val saved = assertNotNull(vm.state.saved)
        assertNotNull(saved.chart, "the reveal draws where it leads")
        assertIs<Display.Shown>(saved.target)
    }

    @Test
    fun back_fromAPickedType_goesBackToTheList_thenCloses() = runUnitTest {
        val vm = editor(FakeSettingsRepository(base), GoalEditRoute())
        val events = eventsOf(vm)
        runCurrent()
        vm.takeAction(GoalEditAction.Edit(GoalEdit.KindPicked(GoalKind.Meals)))
        vm.takeAction(GoalEditAction.BackTapped)
        runCurrent()
        assertNull(vm.state.view?.kind)
        assertEquals(emptyList(), events)
        vm.takeAction(GoalEditAction.BackTapped)
        runCurrent()
        assertEquals(listOf<GoalEditEvent>(GoalEditEvent.Close), events)
    }

    @Test
    fun aRouteKind_startsOnThatType_andTheFlagOffCloses() = runUnitTest {
        val vm = editor(FakeSettingsRepository(base), GoalEditRoute(kind = GoalKind.Steps.key))
        runCurrent()
        assertEquals(GoalKind.Steps, vm.state.view?.kind)

        val off = editor(FakeSettingsRepository(base), GoalEditRoute(), flag = false)
        val events = eventsOf(off)
        runCurrent()
        assertEquals(listOf<GoalEditEvent>(GoalEditEvent.Close), events)
    }

    @Test
    fun theGoalsPage_listsTheCalorieGoalFirst_withItsTargetChartAndHistory() = runUnitTest {
        val answered = GoalFixtures.lose.recorded(GoalRecalculated(GoalFixtures.monday, setOf(BaselineChange.Weight), 1980, 1930, accepted = true))
        val settings = GoalFixtures.settings(answered, GoalFixtures.meals, base = base)
        val vm = goals(FakeSettingsRepository(settings))
        runCurrent()
        val cards = vm.state.goals
        assertEquals(listOf(GoalKind.Weight, GoalKind.Meals), cards.map { it.kind })
        val weight = cards.first()
        assertTrue(weight.setsTarget)
        assertIs<Display.Shown>(weight.target)
        assertNotNull(weight.chart)
        assertEquals(listOf(BaselineChange.Weight), weight.history.single().reasons)
        assertNull(cards[1].chart)
        assertTrue(vm.state.canAdd)
    }

    @Test
    fun pausingAndPickingUpAGoal() = runUnitTest {
        val repository = FakeSettingsRepository(GoalFixtures.settings(GoalFixtures.lose, GoalFixtures.steps, base = base))
        val vm = goals(repository)
        runCurrent()
        vm.takeAction(GoalsAction.PauseTapped(GoalFixtures.steps.id))
        vm.takeAction(GoalsAction.PauseTapped(GoalFixtures.lose.id))
        runCurrent()
        assertEquals(listOf(GoalStatus.Paused, GoalStatus.Paused), GoalBook.goals(repository.state.value).map { it.status })
        assertNull(GoalBook.driving(repository.state.value))

        vm.takeAction(GoalsAction.ResumeTapped(GoalFixtures.lose.id))
        runCurrent()
        assertEquals(GoalFixtures.lose.id, GoalBook.driving(repository.state.value)?.id, "picked up, it sets the target again")
        assertEquals(1930, repository.state.value.targets.energyKcal)
    }

    @Test
    fun theGoalsPage_opensTheEditor() = runUnitTest {
        val vm = goals(FakeSettingsRepository(GoalFixtures.settings(base = base)))
        val events = eventsOf(vm)
        runCurrent()
        vm.takeAction(GoalsAction.EditTapped(GoalFixtures.lose.id))
        vm.takeAction(GoalsAction.AddTapped)
        runCurrent()
        assertEquals(listOf<GoalsEvent>(GoalsEvent.OpenEdit(GoalFixtures.lose.id), GoalsEvent.OpenEdit(null)), events)
    }

    private fun editor(repository: FakeSettingsRepository, route: GoalEditRoute, flag: Boolean = true) = GoalEditViewModel(
        route,
        repository,
        FakePolicySource(SettingsFixtures.policy(PresetId.Full)),
        FakeGoalRepository(weightKg = GoalFixtures.WEIGHT_KG),
        FakeCalendar(GoalFixtures.today),
        weightGoalsFlag(on = flag),
        findSupportFlag(),
    )

    private fun goals(repository: FakeSettingsRepository) = GoalsViewModel(
        GoalsRoute(),
        repository,
        FakePolicySource(SettingsFixtures.policy(PresetId.Full)),
        FakeGoalRepository(weightKg = GoalFixtures.WEIGHT_KG),
        FakeCalendar(GoalFixtures.today),
        weightGoalsFlag(on = true),
        findSupportFlag(),
    )
}
