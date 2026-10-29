package com.opson.libraries.medication.impl

import com.opson.libraries.config.AppConfigFlow
import com.opson.libraries.medication.DoseDays
import com.opson.libraries.medication.DoseDraft
import com.opson.libraries.medication.DoseVia
import com.opson.libraries.medication.MedicationEnabled
import com.opson.libraries.medication.MedicationRepository
import com.opson.libraries.medication.SiteRotation
import com.opson.libraries.reminders.DoseNudge
import com.opson.libraries.reminders.DoseReminders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.time.Instant

/**
 * The medication plan's dose days for the reminder planner. A day a dose covers is still listed,
 * marked covered, so a nudge already on the screen is taken off it.
 */
@ContributesBinding(AppScope::class)
@Inject
class MedicationDoseReminders(
    private val repository: MedicationRepository,
    private val config: AppConfigFlow,
) : DoseReminders {

    override fun upcoming(from: LocalDate, days: Int): Flow<List<DoseNudge>> =
        combine(config.map(MedicationEnabled::isOn).distinctUntilChanged(), repository.plan, repository.observeDoses()) { on, plan, doses ->
            val medication = plan.medication
            if (!on || !plan.tracking || medication == null || !medication.reminder) {
                emptyList()
            } else {
                DoseDays.upcoming(medication, doses, from, days).map { due ->
                    DoseNudge(medication.id, due.date, due.time, covered = due.covered, quickLog = medication.currentDose != null)
                }
            }
        }.distinctUntilChanged()

    /** Writes nothing when the day is already covered, so a second tap can't log a second dose. */
    override suspend fun alreadyDid(medicationId: String, date: LocalDate, at: Instant) {
        val plan = repository.currentPlan()
        val medication = plan.medication?.takeIf { it.id == medicationId } ?: return
        val amount = medication.currentDose ?: return
        val doses = repository.observeDoses().first()
        if (DoseDays.covered(medication, date, doses)) return
        val site = if (medication.injected) SiteRotation.next(plan.sites, doses) else null
        repository.logDose(DoseDraft(medication.id, amount, medication.doseUnit, at, site), DoseVia.AlreadyDid)
    }
}
