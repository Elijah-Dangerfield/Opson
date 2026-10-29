package com.opson.libraries.medication.impl

import com.opson.libraries.config.AppConfigFlow
import com.opson.libraries.medication.DoseMarks
import com.opson.libraries.medication.DoseMarksSource
import com.opson.libraries.medication.MedicationAccess
import com.opson.libraries.medication.MedicationEnabled
import com.opson.libraries.medication.MedicationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/** The weight chart's dose ticks, only while medication is released and tracked. Off, nothing is read. */
@OptIn(ExperimentalCoroutinesApi::class)
@ContributesBinding(AppScope::class)
@Inject
class MedicationDoseMarks(
    private val repository: MedicationRepository,
    private val config: AppConfigFlow,
) : DoseMarksSource {
    override val marks: Flow<List<DoseMarks.Mark>>
        get() = config.map(MedicationEnabled::isOn).distinctUntilChanged().flatMapLatest { on ->
            if (!on) {
                flowOf(emptyList())
            } else {
                combine(repository.plan, repository.observeDoses()) { plan, doses ->
                    if (MedicationAccess.of(on, plan) == MedicationAccess.Tracking) DoseMarks.of(plan.medication, doses) else emptyList()
                }
            }
        }.distinctUntilChanged()
}
