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
import com.opson.libraries.progressphotos.ProgressPhotoRepository
import com.opson.libraries.progressphotos.ProgressPhotosEnabled
import com.opson.libraries.ui.components.text.NumberInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    photosEnabled: ProgressPhotosEnabled,
    progressPhotos: ProgressPhotoRepository,
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
        // Read once, as the page opens. Off, the photo index is never touched.
        val photosReleased = photosEnabled.value
        val lastPhoto = if (photosReleased) progressPhotos.observeLastTaken() else flowOf(null)
        combine(settingsRepository.settings, policySource.policy, weighIns, lastPhoto) { settings, policy, weighIns, last ->
            WeightAction.InputsArrived(WeightInputs(today, settings, weighIns, timeZone, photosReleased, last), policy)
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
                action.updateState {
                    rebuild(it.ui().copy(input = input, saved = false, failed = false, notAWeight = false))
                        ?: it.copy(input = input, saved = null, saveFailed = false, notAWeight = false)
                }
            }
            WeightAction.SaveTapped -> save(action)
            WeightAction.BackTapped -> sendEvent(WeightEvent.Close)
            WeightAction.ProgressPhotosTapped -> if (state.photos != null) sendEvent(WeightEvent.OpenProgressPhotos)
        }
    }

    /** An empty field does nothing; zero, or more than anyone weighs, is refused with a line saying so. */
    private suspend fun save(action: WeightAction) {
        if (state.input.isEmpty()) return
        val kg = state.input.toDoubleOrNull()?.let { amount ->
            when (state.unit) {
                WeightUnit.Kg -> amount
                WeightUnit.Lb -> Units.lbToKg(amount)
            }
        }
        if (kg == null || kg <= 0.0 || kg > MAX_KG) {
            action.updateState { rebuild(it.ui().copy(saved = false, notAWeight = true)) ?: it.copy(saved = null, notAWeight = true) }
            return
        }
        // The amount is health data: it never goes in a log line, only the failure does.
        val written = Catching { weights.addWeighIn(kg) }.logOnFailure("Couldn't save a weigh-in").isSuccess
        if (!written) {
            action.updateState { rebuild(it.ui().copy(saved = false, failed = true)) ?: it.copy(saved = null, saveFailed = true) }
            return
        }
        action.updateState { rebuild(it.ui().copy(input = "", saved = true)) ?: it.copy(input = "") }
    }

    private fun rebuild(ui: WeightUi): WeightState? =
        latest?.let { WeightStateBuilder.build(it.inputs, ui, it.policy) }

    private fun WeightState.ui() = WeightUi(range = range, input = input, saved = saved != null, failed = saveFailed, notAWeight = notAWeight)

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
    data object ProgressPhotosTapped : WeightAction
}

sealed interface WeightEvent {
    data object Close : WeightEvent
    data object OpenProgressPhotos : WeightEvent
}
