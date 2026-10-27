package com.opson.features.settings.impl.energy

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import me.tatarka.inject.annotations.Inject

/** Energy today: today's diary, Burned and the last four weeks (for the ±), rebuilt as any of them change. */
@Inject
class EnergyTodayViewModel(
    diary: DiaryRepository,
    settingsRepository: SettingsRepository,
    burnedRepository: BurnedRepository,
    policySource: VisibilityPolicySource,
    health: HealthRepository,
    healthFlag: HealthReleaseFlag,
    calendar: LocalCalendar,
) : SEAViewModel<EnergyTodayState, EnergyTodayEvent, EnergyTodayAction>(initialStateArg = EnergyTodayState()) {

    private val today: LocalDate = calendar.today()

    init {
        val activity = if (healthFlag()) {
            health.status.map { status -> health.source.takeIf { status == HealthStatus.Connected } }
        } else {
            flowOf(null)
        }
        val history = diary.observeDays(today.minus(DatePeriod(days = HISTORY_DAYS)), today.minus(DatePeriod(days = 1)))
        val day = combine(diary.observeDay(today), settingsRepository.observe(), burnedRepository.burned(today), history, activity) {
                day, settings, burned, days, source ->
            EnergyTodayInputs(today, day, settings, burned, days, source)
        }
        viewModelScope.launch {
            combine(day, policySource.policy, EnergyTodayAction::InputsArrived).collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: EnergyTodayAction) {
        when (action) {
            is EnergyTodayAction.InputsArrived -> action.updateState { EnergyTodayStateBuilder.build(action.inputs, action.policy) }
            EnergyTodayAction.NutrientsTapped -> sendEvent(EnergyTodayEvent.OpenNutrients(today))
            EnergyTodayAction.RestingEnergyTapped -> sendEvent(EnergyTodayEvent.OpenRestingEnergy)
            EnergyTodayAction.BackTapped -> sendEvent(EnergyTodayEvent.Close)
        }
    }

    private companion object {
        const val HISTORY_DAYS = 28
    }
}

sealed interface EnergyTodayEvent {
    data class OpenNutrients(val day: LocalDate) : EnergyTodayEvent
    data object OpenRestingEnergy : EnergyTodayEvent
    data object Close : EnergyTodayEvent
}

sealed interface EnergyTodayAction {
    data class InputsArrived(val inputs: EnergyTodayInputs, val policy: VisibilityPolicy) : EnergyTodayAction
    data object NutrientsTapped : EnergyTodayAction
    data object RestingEnergyTapped : EnergyTodayAction
    data object BackTapped : EnergyTodayAction
}
