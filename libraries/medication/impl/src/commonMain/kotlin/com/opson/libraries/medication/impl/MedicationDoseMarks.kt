package com.opson.libraries.medication.impl

import com.opson.libraries.config.AppConfigFlow
import com.opson.libraries.medication.DoseMarks
import com.opson.libraries.medication.DoseMarksSource
import com.opson.libraries.medication.MedicationAccess
import com.opson.libraries.medication.MedicationEnabled
import com.opson.libraries.medication.MedicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/** The weight chart's dose ticks, only while medication is released and tracked. */
@ContributesBinding(AppScope::class)
@Inject
class MedicationDoseMarks(
    private val repository: MedicationRepository,
    private val config: AppConfigFlow,
) : DoseMarksSource {
    override val marks: Flow<List<DoseMarks.Mark>>
        get() = combine(config.map(MedicationEnabled::isOn).distinctUntilChanged(), repository.plan, repository.observeDoses()) { on, plan, doses ->
            if (MedicationAccess.of(on, plan) == MedicationAccess.Tracking) DoseMarks.of(plan.medication, doses) else emptyList()
        }.distinctUntilChanged()
}
