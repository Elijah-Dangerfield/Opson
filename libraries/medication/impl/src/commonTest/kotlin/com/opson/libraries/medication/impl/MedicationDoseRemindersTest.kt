package com.opson.libraries.medication.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.medication.DoseDraft
import com.opson.libraries.medication.DoseSchedule
import com.opson.libraries.medication.DoseUnit
import com.opson.libraries.medication.DoseVia
import com.opson.libraries.medication.InjectionSite
import com.opson.libraries.medication.Medication
import com.opson.libraries.medication.MedicationDose
import com.opson.libraries.medication.MedicationPlan
import com.opson.libraries.medication.MedicationProduct
import com.opson.libraries.medication.MedicationRepository
import com.opson.libraries.reminders.DoseNudge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MedicationDoseRemindersTest : CoroutineTest() {

    private val monday = LocalDate(2026, 10, 26)
    private val thursday = LocalDate(2026, 10, 29)
    private val clock = FixedClock(Instant.parse("2026-10-26T08:00:00Z"))
    private val flag = MutableStateFlow(true)
    private val repository = MedicationRepositoryImpl(
        FakeDoseDao(),
        FakeSymptomDao(),
        FakeCacheFactory(),
        FixedCalendar(monday, TimeZone.UTC),
        clock,
        medicationConfig(flag),
    )
    private val reminders = MedicationDoseReminders(repository, medicationConfig(flag))

    private val wegovy = Medication(
        id = "med",
        product = MedicationProduct.Wegovy,
        currentDose = 1.7,
        schedule = DoseSchedule.Weekly(DayOfWeek.THURSDAY, LocalTime(19, 0)),
        reminder = true,
        addedOn = LocalDate(2026, 10, 1),
    )

    @Test
    fun theDoseDaysAheadGetANudgeAtTheScheduleTime() = runUnitTest {
        repository.updatePlan { it.withMedication(wegovy).copy(on = true) }
        assertEquals(listOf(DoseNudge("med", thursday, LocalTime(19, 0))), reminders.upcoming(monday, days = 7).first())
    }

    @Test
    fun nothingIsPlannedWhileTheFlagTrackingOrTheSwitchIsOff() = runUnitTest {
        repository.updatePlan { it.withMedication(wegovy.copy(reminder = false)).copy(on = true) }
        assertEquals(emptyList(), reminders.upcoming(monday, 7).first())
        repository.updatePlan { it.withMedication(wegovy).copy(on = false) }
        assertEquals(emptyList(), reminders.upcoming(monday, 7).first())
        repository.updatePlan { it.copy(on = true) }
        flag.value = false
        assertEquals(emptyList(), reminders.upcoming(monday, 7).first())
    }

    @Test
    fun withTheFlagOff_thePlanAndDosesAreNeverRead() = runUnitTest {
        repository.updatePlan { it.withMedication(wegovy).copy(on = true) }
        flag.value = false
        val counting = CountingRepository(repository)
        val reminders = MedicationDoseReminders(counting, medicationConfig(flag))

        assertEquals(emptyList(), reminders.upcoming(monday, 7).first())
        assertEquals(0, counting.reads, "nothing about medication runs at app start while the flag is off")

        flag.value = true
        assertEquals(listOf(DoseNudge("med", thursday, LocalTime(19, 0))), reminders.upcoming(monday, 7).first())
        assertEquals(2, counting.reads, "on, it reads the plan and the doses")
    }

    /** Counts every read of the plan or the doses. */
    private class CountingRepository(private val real: MedicationRepository) : MedicationRepository by real {
        var reads = 0

        override val plan: Flow<MedicationPlan> get() = real.plan.also { reads++ }

        override fun observeDoses(): Flow<List<MedicationDose>> = real.observeDoses().also { reads++ }
    }

    @Test
    fun aLoggedDoseCoversItsDaySoTheNudgeIsDropped() = runUnitTest {
        repository.updatePlan { it.withMedication(wegovy).copy(on = true) }
        repository.logDose(DoseDraft("med", 1.7, DoseUnit.Mg, Instant.parse("2026-10-22T19:00:00Z"), InjectionSite.ThighRight), DoseVia.Medication)
        repository.logDose(DoseDraft("med", 1.7, DoseUnit.Mg, Instant.parse("2026-10-28T19:00:00Z"), InjectionSite.ThighLeft), DoseVia.Medication)
        assertEquals(listOf(DoseNudge("med", thursday, LocalTime(19, 0), covered = true)), reminders.upcoming(monday, 7).first())
    }

    @Test
    fun withNoCurrentDoseAlreadyDidIsNotOffered() = runUnitTest {
        repository.updatePlan { it.withMedication(wegovy.copy(currentDose = null)).copy(on = true) }
        assertEquals(false, reminders.upcoming(monday, 7).first().single().quickLog)
    }

    @Test
    fun alreadyDidLogsTheCurrentDoseOnTheSuggestedSiteOnce() = runUnitTest {
        repository.updatePlan { it.withMedication(wegovy).copy(on = true) }
        val at = Instant.parse("2026-10-29T19:00:00Z")
        reminders.alreadyDid("med", thursday, at)
        reminders.alreadyDid("med", thursday, at)

        val logged = repository.observeDoses().first()
        assertEquals(1, logged.size, "a second tap writes nothing")
        assertEquals(1.7, logged.single().dose)
        assertEquals(at, logged.single().takenAt)
        assertEquals(InjectionSite.AbdomenUpperLeft, logged.single().site)
    }
}
