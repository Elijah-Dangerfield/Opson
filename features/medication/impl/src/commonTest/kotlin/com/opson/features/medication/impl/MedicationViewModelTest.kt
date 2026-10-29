package com.opson.features.medication.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.medication.DoseSchedule
import com.opson.libraries.medication.DoseUnit
import com.opson.libraries.medication.InjectionSite
import com.opson.libraries.medication.MedicationPlan
import com.opson.libraries.medication.MedicationProduct
import com.opson.libraries.medication.Severity
import com.opson.libraries.medication.Symptom
import com.opson.libraries.reminders.NotificationPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MedicationViewModelTest : CoroutineTest() {

    private val today = MedicationFixtures.today
    private val repository = FakeMedicationRepository(MedicationFixtures.plan(), MedicationFixtures.history)
    private var permissions = FakePermissions()

    private fun vm(released: Boolean = true, reminders: Boolean = true) = MedicationViewModel(
        repository = repository,
        symptoms = repository,
        calendar = FixedCalendar(today),
        permissions = permissions,
        enabled = medicationFlag(released),
        remindersEnabled = remindersFlag(reminders),
    )

    private val MedicationViewModel.tracking get() = assertIs<MedicationPage.Tracking>(state.page)

    private fun CoroutineScope.events(vm: MedicationViewModel) = mutableListOf<MedicationEvent>().also { list ->
        launch { vm.eventFlow.collect { list += it } }
    }

    @Test
    fun withTheFlagOffThePageIsEmpty() = runUnitTest {
        assertEquals(MedicationPage.Hidden, vm(released = false).state.page)
    }

    @Test
    fun setUpPicksAMedicationAndTurnsTrackingOn() = runUnitTest {
        repository.planFlow.value = MedicationPlan()
        val vm = vm()
        assertEquals(MedicationPage.Intro(picking = false), vm.state.page)

        vm.takeAction(MedicationAction.SetUpTapped)
        assertEquals(MedicationPage.Intro(picking = true), vm.state.page)

        vm.takeAction(MedicationAction.ProductPicked(MedicationProduct.Zepbound))
        val plan = repository.planFlow.value
        val medication = checkNotNull(plan.medication)
        assertTrue(plan.on)
        assertEquals(MedicationProduct.Zepbound, medication.product)
        assertEquals(DoseSchedule.Weekly(DayOfWeek.THURSDAY, DoseSchedule.DEFAULT_TIME), medication.schedule, "weekly, on today's weekday")
        assertEquals(today, medication.addedOn)
        assertIs<MedicationPage.Tracking>(vm.state.page)
    }

    @Test
    fun aTypedDoseIsKeptOnceItIsADose() = runUnitTest {
        val vm = vm()
        vm.takeAction(MedicationAction.DoseTyped("1.7"))
        assertEquals(1.7, repository.planFlow.value.medication?.currentDose)

        vm.takeAction(MedicationAction.DoseTyped("2."))
        assertEquals("2.", vm.tracking.setup.doseText)
        assertEquals(2.0, repository.planFlow.value.medication?.currentDose, "2. reads as 2")

        vm.takeAction(MedicationAction.DoseTyped("500"))
        assertTrue(vm.tracking.setup.doseInvalid)
        assertEquals(2.0, repository.planFlow.value.medication?.currentDose, "past 100 keeps the last dose")

        vm.takeAction(MedicationAction.DoseTyped(""))
        assertNull(repository.planFlow.value.medication?.currentDose)
    }

    @Test
    fun anotherDrugClearsTheCurrentDose_theSameDrugKeepsIt() = runUnitTest {
        val vm = vm()
        vm.takeAction(MedicationAction.ProductPicked(MedicationProduct.Ozempic))
        assertEquals(1.0, repository.planFlow.value.medication?.currentDose, "both semaglutide")

        vm.takeAction(MedicationAction.ProductPicked(MedicationProduct.Saxenda))
        val medication = checkNotNull(repository.planFlow.value.medication)
        assertNull(medication.currentDose)
        assertIs<DoseSchedule.Daily>(medication.schedule, "Saxenda's usual schedule")
        assertEquals(DoseUnit.Mg, medication.doseUnit)
    }

    @Test
    fun theReminderAsksForPermissionWhenTurnedOn_andSaysSoWhenDenied() = runUnitTest {
        repository.updatePlan { it.withMedication(MedicationFixtures.wegovy.copy(reminder = false)) }
        permissions = FakePermissions(answer = NotificationPermission.Denied)
        val vm = vm()

        vm.takeAction(MedicationAction.ReminderToggled)
        assertEquals(1, permissions.requests)
        assertEquals(true, repository.planFlow.value.medication?.reminder, "the choice is kept")
        assertEquals(ReminderRow(on = true, denied = true), vm.tracking.setup.reminder)

        vm.takeAction(MedicationAction.ReminderToggled)
        assertEquals(1, permissions.requests, "turning it off asks nothing")
    }

    @Test
    fun noReminderRowWithoutReminders_orForAMedicationTakenWhenNeeded() = runUnitTest {
        assertNull(vm(reminders = false).tracking.setup.reminder)
        val vm = vm()
        vm.takeAction(MedicationAction.ScheduleKindPicked(ScheduleKind.AsNeeded))
        assertNull(vm.tracking.setup.reminder)
        assertEquals(DoseSchedule.AsNeeded, repository.planFlow.value.medication?.schedule)
    }

    @Test
    fun theLastSiteCannotBeTurnedOff() = runUnitTest {
        repository.updatePlan { it.copy(sites = setOf(InjectionSite.ThighLeft)) }
        val vm = vm()
        vm.takeAction(MedicationAction.SiteToggled(InjectionSite.ThighLeft))
        assertEquals(setOf(InjectionSite.ThighLeft), repository.planFlow.value.sites)
        vm.takeAction(MedicationAction.SiteToggled(InjectionSite.ArmLeft))
        assertEquals(setOf(InjectionSite.ThighLeft, InjectionSite.ArmLeft), repository.planFlow.value.sites)
    }

    @Test
    fun sideEffectsStartMild_takeASeverity_andSevereVomitingShowsTheCareLine() = runUnitTest {
        val vm = vm()
        vm.takeAction(MedicationAction.SymptomTapped(Symptom.Vomiting))
        assertEquals(mapOf(Symptom.Vomiting to Severity.Mild), repository.symptoms.value[today])
        assertEquals(false, vm.tracking.feeling.needCare)

        vm.takeAction(MedicationAction.SeverityPicked(Symptom.Vomiting, Severity.Severe))
        assertTrue(vm.tracking.feeling.needCare)

        vm.takeAction(MedicationAction.SymptomRemoved(Symptom.Vomiting))
        assertEquals(emptyMap(), repository.symptoms.value[today])
    }

    @Test
    fun stoppingKeepsTheHistory_andTurningBackOnRestoresTheRest() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        vm.takeAction(MedicationAction.StopTapped)

        val off = assertIs<MedicationPage.Off>(vm.state.page)
        assertEquals(MedicationFixtures.history.map { it.id }, off.history.map { it.id })
        assertEquals(listOf<MedicationEvent>(MedicationEvent.Stopped), events)

        vm.takeAction(MedicationAction.TurnOnTapped)
        assertEquals(MedicationProduct.Wegovy, vm.tracking.setup.product)
    }

    @Test
    fun aHistoryRowOpensThatDose() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        vm.takeAction(MedicationAction.DoseTapped("dose-7"))
        vm.takeAction(MedicationAction.LogDoseTapped)
        assertEquals(listOf<MedicationEvent>(MedicationEvent.OpenDose("dose-7"), MedicationEvent.OpenDose(null)), events)
    }
}
