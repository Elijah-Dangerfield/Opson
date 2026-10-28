package com.opson.features.trends.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.trends.NutrientsRange
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.flowroutines.collectIn
import com.opson.libraries.nutrition.Nutrient
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** The foods sheet: follows the day, or the Trends week, as Nutrients does, so a food logged meanwhile appears. */
@OptIn(ExperimentalCoroutinesApi::class)
@Inject
class NutrientFoodsViewModel(
    @Assisted private val day: LocalDate,
    @Assisted private val nutrient: Nutrient,
    @Assisted private val range: NutrientsRange,
    private val diary: DiaryRepository,
    private val settingsRepository: SettingsRepository,
    private val policySource: VisibilityPolicySource,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : SEAViewModel<NutrientFoodsState, NutrientFoodsEvent, NutrientFoodsAction>(initialStateArg = NutrientFoodsState()) {

    private val today: LocalDate get() = clock.now().toLocalDateTime(timeZone).date

    init {
        val days: Flow<List<Day>> = when (range) {
            NutrientsRange.Day -> diary.observeDay(day).map { listOf(it) }
            NutrientsRange.WeekAverage -> settingsRepository.settings
                .map { TrendsCalendar.weekWindow(today, it.weekStart) }
                .distinctUntilChanged()
                .flatMapLatest { window -> diary.observeDays(window.start, window.end) }
        }
        combine(settingsRepository.settings, policySource.policy, days) { settings, policy, days ->
            NutrientFoodsAction.InputsArrived(NutrientFoodsInputs(today, day, range, nutrient, settings, days), policy)
        }.collectIn(viewModelScope) { takeAction(it) }
    }

    override suspend fun handleAction(action: NutrientFoodsAction) {
        when (action) {
            is NutrientFoodsAction.InputsArrived -> action.updateState { NutrientFoodsStateBuilder.build(action.inputs, action.policy) }
            NutrientFoodsAction.CloseTapped -> sendEvent(NutrientFoodsEvent.Dismiss)
        }
    }
}

sealed interface NutrientFoodsAction {
    data class InputsArrived(val inputs: NutrientFoodsInputs, val policy: VisibilityPolicy) : NutrientFoodsAction
    data object CloseTapped : NutrientFoodsAction
}

sealed interface NutrientFoodsEvent {
    data object Dismiss : NutrientFoodsEvent
}
