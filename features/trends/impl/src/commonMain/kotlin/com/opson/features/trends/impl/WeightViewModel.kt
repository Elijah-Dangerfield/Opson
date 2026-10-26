package com.opson.features.trends.impl

import androidx.lifecycle.viewModelScope
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.flowroutines.collectIn
import com.opson.libraries.nutrition.Units
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.ui.components.text.NumberInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
class WeightViewModel(
    private val weights: WeightRepository,
    settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : SEAViewModel<WeightState, WeightEvent, WeightAction>(initialStateArg = WeightState()) {

    // The last repository inputs, so a range pick or a keystroke can rebuild
    // the page without waiting for the database to emit again.
    private var latest: WeightAction.InputsArrived? = null

    private val today: LocalDate get() = clock.now().toLocalDateTime(timeZone).date

    init {
        val weighIns = policySource.policy
            .map { today }
            .distinctUntilChanged()
            .flatMapLatest { day ->
                weights.observeWeighIns(since = day.minus(HISTORY_DAYS, DateTimeUnit.DAY).atStartOfDayIn(timeZone))
            }
        combine(settingsRepository.settings, policySource.policy, weighIns) { settings, policy, weighIns ->
            WeightAction.InputsArrived(WeightInputs(today, settings, weighIns, timeZone), policy)
        }.collectIn(viewModelScope) { takeAction(it) }
    }

    override suspend fun handleAction(action: WeightAction) {
        when (action) {
            is WeightAction.InputsArrived -> {
                latest = action
                action.updateState { rebuild(it.ui()) ?: it }
            }
            is WeightAction.RangePicked -> action.updateState { rebuild(it.ui().copy(range = action.range)) ?: it.copy(range = action.range) }
            is WeightAction.WeightTyped -> {
                val input = NumberInput.sanitize(action.text, allowDecimal = true)
                action.updateState { rebuild(it.ui().copy(input = input, saved = false)) ?: it.copy(input = input, saved = null) }
            }
            WeightAction.SaveTapped -> save(action)
            WeightAction.BackTapped -> sendEvent(WeightEvent.Close)
        }
    }

    /** Ignores anything that is not a weight: empty, zero, or more than anyone weighs. */
    private suspend fun save(action: WeightAction) {
        val amount = state.input.toDoubleOrNull() ?: return
        val kg = when (state.unit) {
            WeightUnit.Kg -> amount
            WeightUnit.Lb -> Units.lbToKg(amount)
        }
        if (kg <= 0.0 || kg > MAX_KG) return
        // The amount is health data: it never goes in a log line, only the failure does.
        val written = Catching { weights.addWeighIn(kg) }.logOnFailure("Couldn't save a weigh-in").isSuccess
        if (!written) return
        action.updateState { rebuild(it.ui().copy(input = "", saved = true)) ?: it.copy(input = "") }
    }

    private fun rebuild(ui: WeightUi): WeightState? =
        latest?.let { WeightStateBuilder.build(it.inputs, ui, it.policy) }

    private fun WeightState.ui() = WeightUi(range = range, input = input, saved = saved != null)

    private companion object {
        /** Seeds the EMA before the 3-month window, and covers it. */
        const val HISTORY_DAYS = 120

        /** The repository's own ceiling. */
        const val MAX_KG = 700.0
    }
}

sealed interface WeightAction {
    data class InputsArrived(val inputs: WeightInputs, val policy: VisibilityPolicy) : WeightAction
    data class RangePicked(val range: WeightRange) : WeightAction
    data class WeightTyped(val text: String) : WeightAction
    data object SaveTapped : WeightAction
    data object BackTapped : WeightAction
}

sealed interface WeightEvent {
    data object Close : WeightEvent
}
