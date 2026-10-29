package com.opson.features.medication.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.medication.DoseDraft
import com.opson.libraries.medication.DoseVia
import com.opson.libraries.medication.MedicationDose
import com.opson.libraries.medication.MedicationEnabled
import com.opson.libraries.medication.MedicationPlan
import com.opson.libraries.medication.MedicationRepository
import com.opson.libraries.medication.Severity
import com.opson.libraries.medication.Symptom
import com.opson.libraries.medication.SymptomDay
import com.opson.libraries.medication.SymptomLog
import com.opson.libraries.reminders.NotificationPermission
import com.opson.libraries.reminders.NotificationPermissions
import com.opson.libraries.reminders.RemindersEnabled
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** The plan and doses in memory; doses newest first, as the real one gives them. */
class FakeMedicationRepository(plan: MedicationPlan = MedicationPlan(), doses: List<MedicationDose> = emptyList()) : MedicationRepository, SymptomLog {
    val planFlow = MutableStateFlow(plan)
    val doses = MutableStateFlow(doses)
    val symptoms = MutableStateFlow<Map<LocalDate, Map<Symptom, Severity>>>(emptyMap())
    val logged = mutableListOf<Pair<DoseDraft, DoseVia>>()
    private var next = 0

    override val plan: Flow<MedicationPlan> get() = planFlow

    override suspend fun currentPlan(): MedicationPlan = planFlow.value

    override suspend fun updatePlan(change: (MedicationPlan) -> MedicationPlan): MedicationPlan = change(planFlow.value).also { planFlow.value = it }

    override fun observeDoses(): Flow<List<MedicationDose>> = doses

    override fun observeDoses(from: LocalDate, to: LocalDate): Flow<List<MedicationDose>> =
        doses.map { all -> all.filter { it.day in from..to }.sortedBy { it.takenAt } }

    override suspend fun dose(id: String): MedicationDose? = doses.value.firstOrNull { it.id == id }

    override suspend fun logDose(draft: DoseDraft, via: DoseVia): MedicationDose? {
        val medication = planFlow.value.medications.firstOrNull { it.id == draft.medicationId } ?: return null
        logged += draft to via
        val dose = MedicationDose(
            id = "new-${next++}",
            medicationId = medication.id,
            product = medication.product,
            drug = medication.drug,
            brand = medication.brand,
            dose = draft.dose,
            unit = draft.unit,
            takenAt = draft.takenAt,
            day = draft.takenAt.toLocalDateTime(TimeZone.UTC).date,
            site = draft.site,
            note = draft.note,
        )
        doses.value = (listOf(dose) + doses.value).sortedByDescending { it.takenAt }
        return dose
    }

    override suspend fun updateDose(id: String, draft: DoseDraft): Boolean {
        val existing = dose(id) ?: return false
        doses.value = doses.value.map {
            if (it.id == id) existing.copy(dose = draft.dose, takenAt = draft.takenAt, site = draft.site, note = draft.note) else it
        }
        return true
    }

    override suspend fun deleteDose(id: String): Boolean {
        val before = doses.value.size
        doses.value = doses.value.filterNot { it.id == id }
        return doses.value.size < before
    }

    override suspend fun restoreDose(id: String): Boolean = false

    override val asking: Flow<Boolean> get() = planFlow.map { it.tracking }

    override fun observeDay(day: LocalDate): Flow<Map<Symptom, Severity>> = symptoms.map { it[day].orEmpty() }

    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<SymptomDay>> =
        symptoms.map { all -> all.filterKeys { it in from..to }.map { (day, felt) -> SymptomDay(day, felt) } }

    override suspend fun set(day: LocalDate, symptoms: Map<Symptom, Severity>) {
        this.symptoms.value = this.symptoms.value + (day to symptoms)
    }
}

class FakePermissions(var status: NotificationPermission = NotificationPermission.NotAsked, private val answer: NotificationPermission = NotificationPermission.Granted) : NotificationPermissions {
    var requests = 0

    override suspend fun status(): NotificationPermission = status

    override suspend fun request(): NotificationPermission {
        requests++
        status = answer
        return answer
    }

    override fun openSettings() = Unit
}

class FixedCalendar(private val date: LocalDate) : LocalCalendar {
    override fun timeZone(): TimeZone = TimeZone.UTC
    override fun today(): LocalDate = date
}

class FixedClock(private val at: Instant) : Clock {
    override fun now(): Instant = at
}

private fun config(vararg flags: Pair<String, Boolean>) = object : AppConfigMap() {
    override val map: Map<String, *> = mapOf("release" to flags.toMap())
}

fun medicationFlag(on: Boolean) = MedicationEnabled(config("medication" to on))

fun remindersFlag(on: Boolean) = RemindersEnabled(config("reminders" to on))
