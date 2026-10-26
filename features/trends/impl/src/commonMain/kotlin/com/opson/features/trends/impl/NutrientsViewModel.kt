package com.opson.features.trends.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.trends.NutrientsRange
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.flowroutines.collectIn
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
@Inject
class NutrientsViewModel(
    @Assisted private val day: LocalDate,
    @Assisted initialRange: NutrientsRange,
    @Assisted private val fromTrends: Boolean,
    private val diary: DiaryRepository,
    private val settingsRepository: SettingsRepository,
    private val policySource: VisibilityPolicySource,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : SEAViewModel<NutrientsState, NutrientsEvent, NutrientsAction>(
    initialStateArg = NutrientsState(range = initialRange),
) {

    private var subscription: Job? = null

    private val today: LocalDate get() = clock.now().toLocalDateTime(timeZone).date

    init {
        subscribe(initialRange)
    }

    override suspend fun handleAction(action: NutrientsAction) {
        when (action) {
            is NutrientsAction.InputsArrived -> action.updateState { current ->
                // An emission from the range just left can still be queued; it must not switch the page back.
                if (action.inputs.range != current.range) current else NutrientsStateBuilder.build(action.inputs, action.policy)
            }
            is NutrientsAction.RangePicked -> {
                if (action.range == state.range) return
                action.updateState { it.copy(range = action.range) }
                subscribe(action.range)
            }
            // Showing more only ever happens in Modes and numbers, behind the pause (design v3).
            NutrientsAction.ModesTapped -> sendEvent(NutrientsEvent.OpenModes)
            NutrientsAction.BackTapped -> sendEvent(NutrientsEvent.Close)
        }
    }

    /** Day follows the one day; Week average follows the Trends week, which moves if the week start changes. */
    private fun subscribe(range: NutrientsRange) {
        subscription?.cancel()
        val days: Flow<List<Day>> = when (range) {
            NutrientsRange.Day -> diary.observeDay(day).map { listOf(it) }
            NutrientsRange.WeekAverage -> settingsRepository.settings
                .map { TrendsCalendar.weekWindow(today, it.weekStart) }
                .distinctUntilChanged()
                .flatMapLatest { window -> diary.observeDays(window.start, window.end) }
        }
        subscription = combine(settingsRepository.settings, policySource.policy, days) { settings, policy, days ->
            NutrientsAction.InputsArrived(NutrientsInputs(today, day, range, fromTrends, settings, days), policy)
        }.collectIn(viewModelScope) { takeAction(it) }
    }
}

sealed interface NutrientsAction {
    data class InputsArrived(val inputs: NutrientsInputs, val policy: VisibilityPolicy) : NutrientsAction
    data class RangePicked(val range: NutrientsRange) : NutrientsAction
    data object ModesTapped : NutrientsAction
    data object BackTapped : NutrientsAction
}

sealed interface NutrientsEvent {
    data object Close : NutrientsEvent

    /** Modes and numbers, where vitamins and minerals are turned on. */
    data object OpenModes : NutrientsEvent
}
