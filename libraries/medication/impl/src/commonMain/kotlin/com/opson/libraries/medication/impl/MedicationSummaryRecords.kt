package com.opson.libraries.medication.impl

import com.opson.libraries.config.AppConfigFlow
import com.opson.libraries.medication.MedicationEnabled
import com.opson.libraries.medication.MedicationRecord
import com.opson.libraries.medication.MedicationRecords
import com.opson.libraries.medication.MedicationRepository
import com.opson.libraries.medication.SymptomLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/** Medication for the provider summary: whatever was logged, tracking on or not, while the flag is on. Off, nothing is read. */
@OptIn(ExperimentalCoroutinesApi::class)
@ContributesBinding(AppScope::class)
@Inject
class MedicationSummaryRecords(
    private val repository: MedicationRepository,
    private val symptoms: SymptomLog,
    private val config: AppConfigFlow,
) : MedicationRecords {
    override fun observe(from: LocalDate, to: LocalDate): Flow<MedicationRecord?> =
        config.map(MedicationEnabled::isOn).distinctUntilChanged().flatMapLatest { on ->
            if (!on) {
                flowOf(null)
            } else {
                combine(repository.plan, repository.observeDoses(), symptoms.observeRange(from, to)) { plan, doses, sideEffects ->
                    plan.medication?.let { MedicationRecord(it, plan.tracking, doses.sortedBy { dose -> dose.takenAt }, sideEffects) }
                }
            }
        }
}
