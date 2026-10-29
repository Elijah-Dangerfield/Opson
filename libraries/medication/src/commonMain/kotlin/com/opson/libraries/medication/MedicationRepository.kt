package com.opson.libraries.medication

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate

/**
 * The medication plan and the doses logged against it. On this phone only for now: doses are a
 * Room table shaped for a later `medication_dose` sync entity (client uuid ids, a
 * `clientUpdatedAt` stamp and a tombstone), but nothing is queued for the server, which has no
 * table for them yet. Never logged: no dose, drug, site or note reaches telemetry or a log line.
 */
interface MedicationRepository {
    val plan: Flow<MedicationPlan>

    suspend fun currentPlan(): MedicationPlan

    /** Read-modify-write of the plan; stamps [MedicationPlan.updatedAtEpochMs]. */
    suspend fun updatePlan(change: (MedicationPlan) -> MedicationPlan): MedicationPlan

    /** Every dose, newest first. A weekly shot is 52 rows a year, so this stays small. */
    fun observeDoses(): Flow<List<MedicationDose>>

    /** Doses whose day is in [from]..[to], oldest first. */
    fun observeDoses(from: LocalDate, to: LocalDate): Flow<List<MedicationDose>>

    suspend fun dose(id: String): MedicationDose?

    /** Writes a new dose of the plan's medication [DoseDraft.medicationId]; null when it is not in the plan. */
    suspend fun logDose(draft: DoseDraft, via: DoseVia): MedicationDose?

    /** False when there is no such dose. */
    suspend fun updateDose(id: String, draft: DoseDraft): Boolean

    /** A tombstone, so Undo and a later sync both have the row. */
    suspend fun deleteDose(id: String): Boolean

    /** Undo for [deleteDose]. */
    suspend fun restoreDose(id: String): Boolean
}

/**
 * Side effects by day, kept apart from Close the day's check-in for now. This is the read API a
 * check-in can use to ask "Any side effects today?" next to hunger and energy: [asking] says
 * whether to ask, and [set] writes what was answered, so the Medication page and a check-in edit
 * the same record.
 */
interface SymptomLog {
    /** Whether side effects are worth asking about: `release.medication` on and tracking on. */
    val asking: Flow<Boolean>

    fun observeDay(day: LocalDate): Flow<Map<Symptom, Severity>>

    /** Days in [from]..[to] with at least one side effect, oldest first. */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<SymptomDay>>

    /** Replaces the day's side effects; empty clears them. */
    suspend fun set(day: LocalDate, symptoms: Map<Symptom, Severity>)
}

/**
 * The weight chart's dose ticks ([DoseMarks]), while `release.medication` and tracking are on;
 * empty otherwise. Trends decides whether the chart, and so the ticks, shows at all.
 */
interface DoseMarksSource {
    val marks: Flow<List<DoseMarks.Mark>>

    /** For tests that are not about doses. */
    object None : DoseMarksSource {
        override val marks: Flow<List<DoseMarks.Mark>> = flowOf(emptyList())
    }
}

/**
 * What the provider summary can say about medication: the medication, every dose (so a change of
 * dose at the start of a range is still a change), and the side effects in the range.
 */
data class MedicationRecord(
    val medication: Medication,
    val tracking: Boolean,
    /** Oldest first. */
    val doses: List<MedicationDose>,
    val sideEffects: List<SymptomDay>,
)

/** The provider summary's medication section. Null while `release.medication` is off or nothing was ever set up. */
interface MedicationRecords {
    fun observe(from: LocalDate, to: LocalDate): Flow<MedicationRecord?>

    /** For tests that are not about medication. */
    object None : MedicationRecords {
        override fun observe(from: LocalDate, to: LocalDate): Flow<MedicationRecord?> = flowOf(null)
    }
}
