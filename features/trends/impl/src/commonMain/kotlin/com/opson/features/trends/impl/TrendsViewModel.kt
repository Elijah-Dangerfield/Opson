package com.opson.features.trends.impl

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.flowroutines.collectIn
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
@Inject
class TrendsViewModel(
    diary: DiaryRepository,
    weights: WeightRepository,
    private val settingsRepository: SettingsRepository,
    private val policySource: VisibilityPolicySource,
    private val clock: Clock,
    private val health: HealthRepository,
    healthFlag: HealthReleaseFlag,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : SEAViewModel<TrendsState, TrendsEvent, TrendsAction>(initialStateArg = TrendsState()) {

    private val today: LocalDate get() = clock.now().toLocalDateTime(timeZone).date

    init {
        // The policy source re-emits at midnight, so the date is read again on
        // each emission and the range moves with the day.
        val span = combine(settingsRepository.settings, policySource.policy) { settings, _ ->
            val today = today
            Span(TrendsCalendar.firstNeeded(today, settings), today)
        }.distinctUntilChanged()

        val records = span.flatMapLatest { span ->
            val weighInsSince = span.today.minus(WEIGH_IN_DAYS, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
            combine(
                diary.observeDays(span.from, span.today),
                weights.observeWeighIns(since = weighInsSince),
            ) { days, weighIns -> Records(span.today, days, weighIns) }
        }

        val expended = if (healthFlag()) expended() else flowOf(null)

        combine(settingsRepository.settings, policySource.policy, records, expended) { settings, policy, records, expended ->
            TrendsAction.InputsArrived(
                TrendsInputs(records.today, settings, records.days, records.weighIns, timeZone, expended),
                policy,
            )
        }.collectIn(viewModelScope) { takeAction(it) }
    }

    /**
     * The watch's energy over the picked range, up to yesterday: today is still in progress. Read
     * again when the range or the day moves. Starts at `null`, so the page draws without waiting
     * on the health store and the Expended card joins it once the read lands.
     */
    private fun expended(): Flow<Map<LocalDate, EnergyExpended>?> =
        combine(settingsRepository.settings, policySource.policy) { settings, _ ->
            val today = today
            TrendsCalendar.span(today, settings.weekStart, settings.trendsRange).start to today.minus(1, DateTimeUnit.DAY)
        }
            .distinctUntilChanged()
            .mapLatest { (from, to) -> health.energyBetween(from, to, timeZone) }
            .onStart { emit(null) }

    override suspend fun handleAction(action: TrendsAction) {
        when (action) {
            is TrendsAction.InputsArrived -> action.updateState { TrendsStateBuilder.build(action.inputs, action.policy) }
            is TrendsAction.RangePicked -> {
                // Not a visibility change: the range only picks which days the cards cover.
                if (action.range != state.range) settingsRepository.update { it.copy(trendsRange = action.range) }
            }
            TrendsAction.SettingsTapped -> sendEvent(TrendsEvent.OpenTrendsSettings)
            TrendsAction.PatternsTapped -> sendEvent(TrendsEvent.OpenPatterns)
            TrendsAction.NutrientsCardTapped -> sendEvent(TrendsEvent.OpenNutrients(today.toString()))
            TrendsAction.WeightCardTapped -> sendEvent(TrendsEvent.OpenWeight)
        }
    }

    private data class Span(val from: LocalDate, val today: LocalDate)

    private class Records(val today: LocalDate, val days: List<Day>, val weighIns: List<WeighIn>)

    private companion object {
        /** Enough history to seed the EMA before the 3 months the card can cover. */
        const val WEIGH_IN_DAYS = 120
    }
}

sealed interface TrendsAction {
    data class InputsArrived(val inputs: TrendsInputs, val policy: VisibilityPolicy) : TrendsAction
    data class RangePicked(val range: TrendsRange) : TrendsAction
    data object SettingsTapped : TrendsAction
    data object PatternsTapped : TrendsAction
    data object NutrientsCardTapped : TrendsAction
    data object WeightCardTapped : TrendsAction
}

sealed interface TrendsEvent {
    data object OpenTrendsSettings : TrendsEvent
    data object OpenPatterns : TrendsEvent

    /** Nutrients detail on Week average, with "Trends" as the back label. */
    data class OpenNutrients(val dayIso: String) : TrendsEvent
    data object OpenWeight : TrendsEvent
}
