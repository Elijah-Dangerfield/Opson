package com.opson.features.medication.impl.today

import com.opson.features.medication.impl.FakeMedicationRepository
import com.opson.features.medication.impl.FixedCalendar
import com.opson.features.medication.impl.MedicationFixtures
import com.opson.features.medication.impl.medicationFlag
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.medication.InjectionSite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MedicationCardTest : CoroutineTest() {

    private val today = MedicationFixtures.today
    private val zone = MedicationFixtures.zone

    @Test
    fun noCardWithTheFlagOff_trackingOff_orShowOnTodayOff() {
        val history = MedicationFixtures.history
        assertNull(MedicationCardBuilder.build(released = false, MedicationFixtures.plan(), history, today, zone))
        assertNull(MedicationCardBuilder.build(released = true, MedicationFixtures.plan(on = false), history, today, zone))
        assertNull(MedicationCardBuilder.build(released = true, MedicationFixtures.plan(todayCard = false), history, today, zone))
        assertNull(MedicationCardBuilder.build(released = true, MedicationFixtures.plan(medication = null), history, today, zone))
    }

    @Test
    fun eachStateSaysWhatTheSpecSays() {
        val plan = MedicationFixtures.plan()
        val history = MedicationFixtures.history
        assertEquals(MedicationCard.Due(InjectionSite.AbdomenLowerLeft), MedicationCardBuilder.build(true, plan, history, today, zone))

        val tonight = MedicationFixtures.dose(0, 1.0, InjectionSite.ThighRight)
        assertEquals(
            MedicationCard.Logged(tonight.id, LocalTime(19, 40), InjectionSite.ThighRight),
            MedicationCardBuilder.build(true, plan, listOf(tonight) + history, today, zone),
        )

        val monday = today.plus(DatePeriod(days = 4))
        assertEquals(
            MedicationCard.Between(last = today, next = today.plus(DatePeriod(days = 7)), today = monday),
            MedicationCardBuilder.build(true, plan, listOf(tonight) + history, monday, zone),
        )

        val saturday = today.plus(DatePeriod(days = 2))
        assertEquals(MedicationCard.NotLogged(today), MedicationCardBuilder.build(true, plan, history, saturday, zone))

        val pills = MedicationFixtures.plan(MedicationFixtures.rybelsus)
        assertEquals(MedicationCard.DailyDue, MedicationCardBuilder.build(true, pills, emptyList(), today, zone))
    }

    @Test
    fun theCardShowsOnlyOnToday_andSkippedPutsThePromptAwayWritingNoDose() = runUnitTest {
        val repository = FakeMedicationRepository(MedicationFixtures.plan(), MedicationFixtures.history)
        val saturday = today.plus(DatePeriod(days = 2))
        val vm = MedicationCardViewModel(repository, FixedCalendar(saturday), medicationFlag(true))

        vm.takeAction(MedicationCardAction.DayShown(today))
        assertNull(vm.state.card, "Today showing another day")

        vm.takeAction(MedicationCardAction.DayShown(saturday))
        assertEquals(MedicationCard.NotLogged(today), vm.state.card)

        vm.takeAction(MedicationCardAction.SkippedTapped)
        assertEquals(today, repository.planFlow.value.skippedDue)
        assertEquals(MedicationFixtures.history, repository.doses.value, "no dose written, nothing counted")
        assertEquals(MedicationCard.Between(last = MedicationFixtures.history.first().day, next = today.plus(DatePeriod(days = 7)), today = saturday), vm.state.card)
    }

    @Test
    fun aLoggedCardOpensItsDose_andTheOthersOpenThePageOrTheSheet() = runUnitTest {
        val tonight = MedicationFixtures.dose(0, 1.0, InjectionSite.ThighRight)
        val repository = FakeMedicationRepository(MedicationFixtures.plan(), listOf(tonight))
        val vm = MedicationCardViewModel(repository, FixedCalendar(today), medicationFlag(true))
        val events = events(vm)
        vm.takeAction(MedicationCardAction.DayShown(today))

        vm.takeAction(MedicationCardAction.CardTapped)
        vm.takeAction(MedicationCardAction.LogTapped)
        assertEquals(listOf<MedicationCardEvent>(MedicationCardEvent.OpenSheet(tonight.id), MedicationCardEvent.OpenSheet(null)), events)
    }

    private fun CoroutineScope.events(vm: MedicationCardViewModel) = mutableListOf<MedicationCardEvent>().also { list ->
        launch { vm.eventFlow.collect { list += it } }
    }
}
