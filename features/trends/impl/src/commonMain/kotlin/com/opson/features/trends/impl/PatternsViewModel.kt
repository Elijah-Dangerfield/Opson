package com.opson.features.trends.impl

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.CheckInEnabled
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.NewFoods
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.flowroutines.collectIn
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
@Inject
class PatternsViewModel(
    diary: DiaryRepository,
    settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    private val clock: Clock,
    checkInEnabled: CheckInEnabled,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : SEAViewModel<PatternsState, PatternsEvent, PatternsAction>(initialStateArg = PatternsState()) {

    private val today: LocalDate get() = clock.now().toLocalDateTime(timeZone).date

    init {
        // The policy source re-emits at midnight, so the four weeks move with the day.
        val days = policySource.policy
            .map { today }
            .distinctUntilChanged()
            .flatMapLatest { day ->
                val from = day.minus(PatternsStateBuilder.DAYS - 1 + NewFoods.LOOKBACK_DAYS, DateTimeUnit.DAY)
                diary.observeDays(from, day).map { day to it }
            }
        // Read once when the page opens, like every release flag.
        val checkInReleased = checkInEnabled()
        combine(settingsRepository.settings, policySource.policy, days) { settings, policy, (day, list) ->
            PatternsAction.InputsArrived(PatternsInputs(day, settings, list, timeZone, checkInOn = checkInReleased), policy)
        }.collectIn(viewModelScope) { takeAction(it) }
    }

    override suspend fun handleAction(action: PatternsAction) {
        when (action) {
            is PatternsAction.InputsArrived -> action.updateState { PatternsStateBuilder.build(action.inputs, action.policy) }
            PatternsAction.BackTapped -> sendEvent(PatternsEvent.Close)
        }
    }
}

sealed interface PatternsAction {
    data class InputsArrived(val inputs: PatternsInputs, val policy: VisibilityPolicy) : PatternsAction
    data object BackTapped : PatternsAction
}

sealed interface PatternsEvent {
    data object Close : PatternsEvent
}
