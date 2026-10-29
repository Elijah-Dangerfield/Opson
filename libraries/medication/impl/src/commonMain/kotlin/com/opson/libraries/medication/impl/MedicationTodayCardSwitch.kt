package com.opson.libraries.medication.impl

import com.opson.libraries.config.AppConfigFlow
import com.opson.libraries.medication.MedicationAccess
import com.opson.libraries.medication.MedicationEnabled
import com.opson.libraries.medication.MedicationRepository
import com.opson.libraries.medication.MedicationTodaySwitch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/** The plan's "Show on Today", for More › Today screen, while medication is released and tracked. */
@ContributesBinding(AppScope::class)
@Inject
class MedicationTodayCardSwitch(
    private val repository: MedicationRepository,
    private val config: AppConfigFlow,
) : MedicationTodaySwitch {
    override val shown: Flow<Boolean?>
        get() = combine(config.map(MedicationEnabled::isOn).distinctUntilChanged(), repository.plan) { on, plan ->
            plan.todayCard.takeIf { MedicationAccess.of(on, plan) == MedicationAccess.Tracking }
        }.distinctUntilChanged()

    override suspend fun set(on: Boolean) {
        repository.updatePlan { it.copy(todayCard = on) }
    }
}
