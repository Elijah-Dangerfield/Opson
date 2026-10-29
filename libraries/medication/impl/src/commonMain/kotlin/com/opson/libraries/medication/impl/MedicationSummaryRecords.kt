package com.opson.libraries.medication.impl

import com.opson.libraries.config.AppConfigFlow
import com.opson.libraries.medication.MedicationEnabled
import com.opson.libraries.medication.MedicationRecord
import com.opson.libraries.medication.MedicationRecords
import com.opson.libraries.medication.MedicationRepository
import com.opson.libraries.medication.SymptomLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/** Medication for the provider summary: whatever was logged, tracking on or not, while the flag is on. */
@ContributesBinding(AppScope::class)
@Inject
class MedicationSummaryRecords(
    private val repository: MedicationRepository,
    private val symptoms: SymptomLog,
    private val config: AppConfigFlow,
) : MedicationRecords {
    override fun observe(from: LocalDate, to: LocalDate): Flow<MedicationRecord?> =
        combine(
            config.map(MedicationEnabled::isOn).distinctUntilChanged(),
            repository.plan,
            repository.observeDoses(),
            symptoms.observeRange(from, to),
        ) { on, plan, doses, sideEffects ->
            val medication = plan.medication
            if (!on || medication == null) null else MedicationRecord(medication, plan.tracking, doses.sortedBy { it.takenAt }, sideEffects)
        }
}
