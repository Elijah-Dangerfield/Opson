package com.opson.features.today.impl

import com.opson.features.today.impl.checkin.CheckInAction
import com.opson.features.today.impl.checkin.CheckInEvent
import com.opson.features.today.impl.checkin.CheckInViewModel
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.Energy
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.medication.Severity
import com.opson.libraries.medication.Symptom
import com.opson.libraries.medication.SymptomDay
import com.opson.libraries.medication.SymptomLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The check-in's Side effects row (`release.medication`), from `SymptomLog`. */
class CheckInSideEffectsTest : CoroutineTest() {

    private val today = TodayFixtures.today
    private val diary = FakeDiaryRepository(listOf(TodayFixtures.day(dinner = true)))
    private val symptoms = FakeSymptoms()

    private fun vm(day: LocalDate? = null) = CheckInViewModel(
        requestedDay = day,
        diary = diary,
        checkIns = diary,
        calendar = FakeCalendar(),
        clock = FakeClock(LocalDateTime(today, LocalTime(21, 0)).toInstant(TimeZone.UTC)),
        symptoms = symptoms,
    )

    @Test
    fun noRowUnlessMedicationIsTracked() = runUnitTest {
        symptoms.asking.value = false
        assertNull(vm().state.sideEffects)
    }

    @Test
    fun theRowListsTodaysSideEffects_andFollowsTheLog() = runUnitTest {
        val vm = vm()
        assertEquals(emptyList(), vm.state.sideEffects)
        symptoms.today.value = mapOf(Symptom.Fatigue to Severity.Mild, Symptom.Nausea to Severity.Severe)
        assertEquals(listOf(Symptom.Nausea, Symptom.Fatigue), vm.state.sideEffects)
    }

    @Test
    fun anotherDaysCheckInHasNoRow() = runUnitTest {
        assertNull(vm(today.minus(DatePeriod(days = 1))).state.sideEffects)
    }

    @Test
    fun tappingItSavesThePicksThenOpensTheMedicationPage() = runUnitTest {
        val events = mutableListOf<CheckInEvent>()
        val vm = vm()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        vm.takeAction(CheckInAction.EnergyTapped(Energy.Good))
        vm.takeAction(CheckInAction.SideEffectsTapped)

        assertEquals(listOf<Pair<LocalDate, CheckIn?>>(today to CheckIn(energy = Energy.Good)), diary.checkIns)
        assertEquals(CheckInEvent.OpenSideEffects, events.last())
    }

    private class FakeSymptoms : SymptomLog {
        override val asking = MutableStateFlow(true)
        val today = MutableStateFlow<Map<Symptom, Severity>>(emptyMap())
        override fun observeDay(day: LocalDate): Flow<Map<Symptom, Severity>> = today.map { it }
        override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<SymptomDay>> = flowOf(emptyList())
        override suspend fun set(day: LocalDate, symptoms: Map<Symptom, Severity>) {
            today.value = symptoms
        }
    }
}
