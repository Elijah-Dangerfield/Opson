package com.opson.features.medication.impl.dose

import com.opson.features.medication.LogDoseRoute
import com.opson.features.medication.impl.FakeMedicationRepository
import com.opson.features.medication.impl.FixedCalendar
import com.opson.features.medication.impl.FixedClock
import com.opson.features.medication.impl.MedicationFixtures
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.medication.DoseUnit
import com.opson.libraries.medication.DoseVia
import com.opson.libraries.medication.InjectionSite
import com.opson.libraries.medication.MedicationDose
import com.opson.libraries.medication.MedicationPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LogDoseViewModelTest : CoroutineTest() {

    private val now = Instant.parse("2026-10-29T19:40:00Z")

    private fun vm(
        route: LogDoseRoute = LogDoseRoute(),
        plan: MedicationPlan = MedicationFixtures.plan(),
        doses: List<MedicationDose> = MedicationFixtures.history,
    ): Pair<LogDoseViewModel, FakeMedicationRepository> {
        val repository = FakeMedicationRepository(plan, doses)
        return LogDoseViewModel(route, repository, FixedCalendar(MedicationFixtures.today), FixedClock(now)) to repository
    }

    private fun CoroutineScope.events(vm: LogDoseViewModel) = mutableListOf<LogDoseEvent>().also { list ->
        launch { vm.eventFlow.collect { list += it } }
    }

    @Test
    fun aNewDoseStartsOnTheCurrentDoseNowAndTheSiteUsedLongestAgo() = runUnitTest {
        val (vm, _) = vm()
        val state = vm.state
        assertEquals("1", state.doseText)
        assertEquals(DoseDay.Today, state.day)
        assertEquals(LocalTime(19, 40), state.time)
        // Upper left and upper right, right and left thighs were used; the lower abdomen never was.
        assertEquals(InjectionSite.AbdomenLowerLeft, state.site)
        assertEquals(state.site, state.suggested)
    }

    @Test
    fun savingTheCurrentDoseLogsItAndCloses() = runUnitTest {
        val (vm, repository) = vm(LogDoseRoute(via = LogDoseRoute.VIA_TODAY_CARD))
        val events = events(vm)
        vm.takeAction(LogDoseAction.SaveTapped)

        val (draft, via) = repository.logged.single()
        assertEquals(1.0, draft.dose)
        assertEquals(now, draft.takenAt)
        assertEquals(InjectionSite.AbdomenLowerLeft, draft.site)
        assertEquals(DoseVia.TodayCard, via)
        assertEquals(listOf<LogDoseEvent>(LogDoseEvent.Dismiss), events)
    }

    @Test
    fun aNewAmountAsksOnceWhetherItIsTheCurrentDoseNow() = runUnitTest {
        val (vm, repository) = vm()
        vm.takeAction(LogDoseAction.ChipPicked(1.7))
        vm.takeAction(LogDoseAction.SaveTapped)
        assertEquals(DoseStep.AskCurrent(1.7, DoseUnit.Mg), vm.state.step)
        assertEquals(1, repository.logged.size, "the dose is written before the question")

        vm.takeAction(LogDoseAction.MakeCurrentTapped)
        assertEquals(1.7, repository.planFlow.value.medication?.currentDose)
    }

    @Test
    fun justThisOnceLeavesTheCurrentDose() = runUnitTest {
        val (vm, repository) = vm()
        vm.takeAction(LogDoseAction.DoseTyped("1.7"))
        vm.takeAction(LogDoseAction.SaveTapped)
        vm.takeAction(LogDoseAction.JustThisOnceTapped)
        assertEquals(1.0, repository.planFlow.value.medication?.currentDose)
    }

    @Test
    fun theFirstDoseWithNoCurrentDoseBecomesItWithoutAsking() = runUnitTest {
        val plan = MedicationFixtures.plan(MedicationFixtures.wegovy.copy(currentDose = null))
        val (vm, repository) = vm(plan = plan, doses = emptyList())
        assertEquals("", vm.state.doseText)
        assertFalse(vm.state.canSave)

        vm.takeAction(LogDoseAction.DoseTyped("0,25"))
        vm.takeAction(LogDoseAction.SaveTapped)
        assertEquals(0.25, repository.planFlow.value.medication?.currentDose)
        assertEquals(DoseStep.Form, vm.state.step)
    }

    @Test
    fun yesterdayAtAPickedTimeIsWhenItWasTaken() = runUnitTest {
        val (vm, repository) = vm()
        vm.takeAction(LogDoseAction.DayPicked(DoseDay.Yesterday))
        vm.takeAction(LogDoseAction.TimePicked(LocalTime(21, 15)))
        vm.takeAction(LogDoseAction.SaveTapped)
        assertEquals(Instant.parse("2026-10-28T21:15:00Z"), repository.logged.single().first.takenAt)
    }

    @Test
    fun notADoseIsSaidAndNothingIsWritten() = runUnitTest {
        val (vm, repository) = vm()
        vm.takeAction(LogDoseAction.DoseTyped("0"))
        vm.takeAction(LogDoseAction.SaveTapped)
        assertTrue(vm.state.notADose)
        assertTrue(repository.logged.isEmpty())
    }

    @Test
    fun aPillHasNoSite() = runUnitTest {
        val (vm, repository) = vm(plan = MedicationFixtures.plan(MedicationFixtures.rybelsus), doses = emptyList())
        assertNull(vm.state.sites)
        vm.takeAction(LogDoseAction.SiteTapped(InjectionSite.ThighLeft))
        vm.takeAction(LogDoseAction.SaveTapped)
        assertNull(repository.logged.single().first.site)
    }

    @Test
    fun editingLoadsTheDose_savesOverIt_andDeleteOffersUndo() = runUnitTest {
        val (vm, repository) = vm(LogDoseRoute(doseId = "dose-7"))
        val events = events(vm)
        assertTrue(vm.state.editing)
        assertEquals(InjectionSite.AbdomenUpperRight, vm.state.site)
        assertEquals(DoseDay.Other(MedicationFixtures.history.first().day), vm.state.day)

        vm.takeAction(LogDoseAction.SiteTapped(InjectionSite.ThighLeft))
        vm.takeAction(LogDoseAction.SaveTapped)
        assertEquals(InjectionSite.ThighLeft, repository.dose("dose-7")?.site)
        assertTrue(repository.logged.isEmpty(), "an edit is not a new dose")

        vm.takeAction(LogDoseAction.DeleteTapped)
        assertNull(repository.dose("dose-7"))
        assertEquals(listOf(LogDoseEvent.Dismiss, LogDoseEvent.Deleted("dose-7")), events.toList())
    }

    @Test
    fun aReminderTappedTheMorningAfterLogsAgainstYesterdaysDoseTime() = runUnitTest {
        val (vm, _) = vm(LogDoseRoute(dueIso = "2026-10-28", via = LogDoseRoute.VIA_REMINDER))
        assertEquals(DoseDay.Yesterday, vm.state.day)
        assertEquals(LocalTime(19, 0), vm.state.time)
    }
}
