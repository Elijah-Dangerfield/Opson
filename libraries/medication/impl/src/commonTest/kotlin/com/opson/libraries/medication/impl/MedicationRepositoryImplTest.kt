package com.opson.libraries.medication.impl

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogId
import com.opson.libraries.core.logging.LogTree
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.medication.DoseDraft
import com.opson.libraries.medication.DoseSchedule
import com.opson.libraries.medication.DoseUnit
import com.opson.libraries.medication.DoseVia
import com.opson.libraries.medication.InjectionSite
import com.opson.libraries.medication.Medication
import com.opson.libraries.medication.MedicationDrug
import com.opson.libraries.medication.MedicationForm
import com.opson.libraries.medication.MedicationProduct
import com.opson.libraries.medication.Severity
import com.opson.libraries.medication.Symptom
import com.opson.libraries.medication.SymptomDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MedicationRepositoryImplTest : CoroutineTest() {

    private val today = LocalDate(2026, 10, 29)
    private val clock = FixedClock(Instant.parse("2026-10-29T20:00:00Z"))
    private val flag = MutableStateFlow(true)
    private val doses = FakeDoseDao()
    private val symptoms = FakeSymptomDao()
    private val logs = EveryLine()

    private val compounded = Medication(
        id = "med",
        product = MedicationProduct.CompoundedTirzepatide,
        brand = "Clinic tirz",
        doseUnit = DoseUnit.Units,
        currentDose = 40.0,
        schedule = DoseSchedule.Weekly(DayOfWeek.THURSDAY, LocalTime(19, 0)),
        reminder = true,
        addedOn = LocalDate(2026, 10, 1),
    )

    // New York, so 1 AM UTC on the 30th is still the 29th there.
    private fun repository(zone: TimeZone = TimeZone.UTC) =
        MedicationRepositoryImpl(doses, symptoms, FakeCacheFactory(), FixedCalendar(today, zone), clock, medicationConfig(flag))

    @BeforeTest
    fun plant() = KLog.plant(logs)

    @AfterTest
    fun uproot() = KLog.uproot(logs)

    @Test
    fun aDoseCopiesTheMedicationSoHistoryOutlivesThePlan() = runUnitTest {
        val repository = repository(TimeZone.of("America/New_York"))
        repository.updatePlan { it.withMedication(compounded).copy(on = true) }
        val takenAt = Instant.parse("2026-10-30T01:00:00Z")
        val dose = assertNotNull(repository.logDose(DoseDraft("med", 40.0, DoseUnit.Units, takenAt, InjectionSite.ThighLeft, " sore "), DoseVia.Medication))

        assertEquals(LocalDate(2026, 10, 29), dose.day, "the local day, not UTC's")
        assertEquals(MedicationDrug.Tirzepatide, dose.drug)
        assertEquals("Clinic tirz", dose.brand)
        assertEquals("sore", dose.note)
        repository.updatePlan { it.withMedication(compounded.copy(product = MedicationProduct.Zepbound, brand = null)) }
        assertEquals(MedicationProduct.CompoundedTirzepatide, repository.dose(dose.id)?.product)
    }

    @Test
    fun aDoseOfAMedicationNotInThePlanIsNotWritten() = runUnitTest {
        val repository = repository()
        assertNull(repository.logDose(DoseDraft("nobody", 1.0, DoseUnit.Mg, clock.now(), null), DoseVia.Medication))
        assertTrue(doses.rows.value.isEmpty())
    }

    @Test
    fun aPillHasNoSite() = runUnitTest {
        val repository = repository()
        val pill = compounded.copy(product = MedicationProduct.Rybelsus, form = MedicationForm.Pill)
        repository.updatePlan { it.withMedication(pill).copy(on = true) }
        val dose = repository.logDose(DoseDraft("med", 7.0, DoseUnit.Mg, clock.now(), InjectionSite.ArmLeft), DoseVia.Medication)
        assertNull(dose?.site)
    }

    @Test
    fun deleteIsATombstoneThatUndoBringsBack() = runUnitTest {
        val repository = repository()
        repository.updatePlan { it.withMedication(compounded).copy(on = true) }
        val dose = assertNotNull(repository.logDose(DoseDraft("med", 40.0, DoseUnit.Units, clock.now(), null), DoseVia.Medication))
        val stamped = doses.rows.value.getValue(dose.id).clientUpdatedAtEpochMs

        assertTrue(repository.deleteDose(dose.id))
        assertEquals(emptyList(), repository.observeDoses().first())
        val tombstone = doses.rows.value.getValue(dose.id)
        assertTrue(tombstone.deleted, "kept for undo and a later sync")
        assertTrue(tombstone.clientUpdatedAtEpochMs > stamped, "each change stamps later than the last")

        assertTrue(repository.restoreDose(dose.id))
        assertEquals(listOf(dose.id), repository.observeDoses().first().map { it.id })
        assertFalse(repository.restoreDose(dose.id), "nothing to restore twice")
    }

    @Test
    fun sideEffectsRoundTripByDayAndAnEmptyDayDropsOut() = runUnitTest {
        val repository = repository()
        repository.set(today, mapOf(Symptom.Nausea to Severity.Mild, Symptom.StomachPain to Severity.Severe))
        assertEquals(mapOf(Symptom.Nausea to Severity.Mild, Symptom.StomachPain to Severity.Severe), repository.observeDay(today).first())

        repository.set(today, emptyMap())
        assertEquals(emptyMap(), repository.observeDay(today).first())
        assertEquals(emptyList<SymptomDay>(), repository.observeRange(today, today).first())
    }

    @Test
    fun aSideEffectThisBuildDoesNotKnowIsDropped() {
        assertEquals(mapOf(Symptom.Fatigue to Severity.Moderate), decodeSymptoms("""{"fatigue":"moderate","hiccups":"mild","nausea":"awful"}"""))
        assertEquals(emptyMap(), decodeSymptoms("not json"))
    }

    @Test
    fun theCheckInAsksOnlyWhileTheFlagAndTrackingAreOn() = runUnitTest {
        val repository = repository()
        assertFalse(repository.asking.first(), "nothing set up")
        repository.updatePlan { it.withMedication(compounded).copy(on = true) }
        assertTrue(repository.asking.first())
        flag.value = false
        assertFalse(repository.asking.first())
    }

    @Test
    fun aUserChangeWipesThePlan() = runUnitTest {
        val repository = repository()
        repository.updatePlan { it.withMedication(compounded).copy(on = true) }
        repository.clear("previous")
        assertNull(repository.currentPlan().medication)
    }

    /**
     * The spec's leak test: no dose, drug, brand, site, note or side effect in any telemetry event
     * or log line, whatever happens.
     */
    @Test
    fun nothingAboutTheMedicationReachesALogLine() = runUnitTest {
        val repository = repository()
        repository.updatePlan { it.withMedication(compounded).copy(on = true) }
        val dose = assertNotNull(
            repository.logDose(DoseDraft("med", 42.5, DoseUnit.Units, clock.now(), InjectionSite.ThighLeft, "left a bruise"), DoseVia.Reminder),
        )
        repository.updateDose(dose.id, DoseDraft("med", 37.5, DoseUnit.Units, clock.now(), InjectionSite.ArmRight, "fine"))
        repository.deleteDose(dose.id)
        repository.set(today, mapOf(Symptom.Vomiting to Severity.Severe))
        repository.updatePlan { it.copy(on = false) }
        MedicationDoseReminders(repository, medicationConfig(flag)).alreadyDid("med", today, clock.now())

        val text = logs.all()
        assertTrue(logs.events().contains("medication.dose_logged"), "the events did fire: $text")
        listOf("42.5", "37.5", "40", "tirz", "Clinic", "thigh", "arm_right", "bruise", "fine", "vomiting", "severe", "units").forEach { secret ->
            assertFalse(text.contains(secret, ignoreCase = true), "\"$secret\" reached a log line: $text")
        }
    }

    /** Every log entry, message and extras, as one lowercase string. */
    private class EveryLine : LogTree() {
        private val entries = mutableListOf<LogEntry>()

        override fun log(entry: LogEntry): LogId? {
            entries += entry
            return null
        }

        fun all(): String = entries.joinToString("\n") { "${it.message} ${it.context.extras}" }

        fun events(): List<String> = entries.mapNotNull { it.context.extras["app_event"]?.toString() }
    }
}
